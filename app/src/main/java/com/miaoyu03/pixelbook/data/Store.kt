package com.miaoyu03.pixelbook.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Store：数据层。
 *
 * 存储规范（目录可选：应用内部 filesDir / SAF 目录）——根目录下只有账户文件夹 + my_account.json：
 *   <root>/
 *     my_account.json                 账户索引（全局一份；账户名/创建时间/占用字节等）
 *     <账户文件夹>/                   账户名（自动跟随改名；账户名唯一）
 *       my_choice.json                该账户自定义类别（收支通用，账户内一份）
 *       my_saving.json                我的存款（账户公用一本；空文件也保留，内容为 []）
 *       my_payment.json               我的钱包资产账户（空文件也保留，内容为 []）
 *       my_ledger_<账本名>_<时间戳>.json   每账本一个数据包（txs/wx/bdg/ledger 元信息）
 *
 * 账本列表不依赖索引文件：启动/读取时扫描账户目录内全部 my_ledger_*.json，
 * 从每个数据包头部读出账本元信息（名称/封面/字体/归档月/文件名），按时间戳倒序。
 *
 * 兼容整理（normalize）只在两个时机执行，不每次启动扫描：
 *   1) 首次初始化（目录里还没有 my_account.json）——把旧版数据搬进「默认账户」；
 *   2) 设置里切换存储目录后，对目标目录执行一遍。
 * 旧数据形态（SharedPreferences pixelbook_data / SAF 根文件 pixelbook_*.json /
 *   备份文件 {账本名}_{时间戳}.json / 账户化过渡期 accounts.json+ledgers.json 等）
 * 都会被识别、搬移、改名成新规范；整理采用「先搬再删、读不到不删」避免丢账本。
 */
class Store(context: Context) {

    private val appContext = context.applicationContext

    /** 配置（存储目录选择、当前账户等）与业务数据分离 */
    private val cfg = appContext.getSharedPreferences(CFG_NAME, Context.MODE_PRIVATE)

    /** 旧版内部存储使用的 SharedPreferences（仅迁移用） */
    private val legacyPrefs = appContext.getSharedPreferences("pixelbook_data", Context.MODE_PRIVATE)

    /** 当前数据存储后端 */
    private var io: LedgerIO

    /** 最近一次写入错误（设置页展示，toast 错过也能查） */
    @Volatile private var lastWriteError: String? = null

    /**
     * 账本元信息缓存（去索引后的扫描结果）。
     * accountId -> (文件名 -> 账本元信息)。仅存每个数据包头部解析出的元信息；
     * 文件列表变化时按文件名 diff，只解析新增文件。读目录名本身成本很低，
     * 避免每次 ledgersOf 全量解析大账本包。
     */
    private val ledgerMetaCache = HashMap<String, MutableMap<String, Ledger>>()

    /**
     * 账本流水缓存 ledgerId -> List<Tx>：避免 DetailScreen/EntryScreen 每次重组（tick++ 等）
     * 都全量读账本 JSON 并解析全部流水（SAF 目录上明显卡顿）。
     * 任何写流水路径（addTx/updateTx/deleteTx/saveTxs）都会失效对应条目。
     */
    private val txCache = HashMap<String, List<Tx>>()

    init {
        io = loadIo()
        // 兼容整理：只在「尚无 my_account.json（首次初始化/升级后首启）」与「切换存储目录后」执行
        if (io.read(ACCOUNTS_JSON) == null) {
            val summary = normalize(io)
            if (summary.migrated > 0) {
                runCatching { toast("已整理旧数据：${summary.migrated} 个账本 → ${summary.accounts} 个账户") }
            }
        }
    }

    companion object {
        private const val CFG_NAME = "pixelbook_cfg"
        private const val KEY_STORAGE_TREE = "storage_tree"
        private const val KEY_LAST_SWITCH = "last_switch_result"
        private const val KEY_PREV_STORAGE_TREE = "prev_storage_tree"
        private const val KEY_STORAGE_HISTORY = "storage_history"
        private const val KEY_CUR_ACCOUNT = "current_account"

        // 相对文件/文件夹名（<root> 之下；新规范名）
        private const val ACCOUNTS_JSON = "my_account.json"
        private const val CATS_JSON = "my_choice.json"
        private const val SAVING_JSON = "my_saving.json"
        private const val PAYMENT_JSON = "my_payment.json"
        private const val LEDGER_FILE_PREFIX = "my_ledger_"
        private const val LEDGER_FILE_SUFFIX = ".json"
        // 旧版/过渡期文件名（normalize 识别、迁移用）
        private const val OLD_ACCOUNTS_JSON = "accounts.json"
        private const val OLD_LEDGERS_JSON = "ledgers.json"
        private const val OLD_CATS_JSON = "cats.json"
        private const val OLD_ASSETS_SUB = "资产信息"
        private const val OLD_DEP_SUB = "存款明细"
        private const val KEY_BDG = "bdg"

        // 旧版根目录文件名（迁移读取用）
        private const val LEGACY_LEDGERS = "ledgers"
        private const val LEGACY_CATS = "cats"
        private const val LEGACY_PREFIX = "pixelbook_"
        private const val LEGACY_KEY_PREFIX = "ledger."
        private const val LEGACY_PREFS_NAME = "pixelbook_data"
        private const val LEGACY_TX_P = "txs."
        private const val LEGACY_DP_P = "dps."
        private const val LEGACY_WX_P = "wx."

        const val MAX_LEDGER_NAME = 30   // 账本名称字符上限
    }

    /** 像素风格 toast（组件内用 store.toast 代替 Context） */
    fun toast(msg: String) {
        android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    /* ================= 存储后端 ================= */

    private fun loadIo(): LedgerIO {
        val uri = cfg.getString(KEY_STORAGE_TREE, null)
        return if (uri == null) {
            FileLedgerIO(File(appContext.filesDir, "ledgers").apply { mkdirs() })
        } else {
            val tree = Uri.parse(uri)
            val root = DocumentFile.fromTreeUri(appContext, tree)
            // 权限探活：SAF 目录若因卸载/系统清理导致授权失效，fromTreeUri 仍返回对象但读取会
            // SecurityException。这里做一次轻量探测，失效则回落内部存储（外部数据不会被删，
            // 提示用户重新授权后再切换回外部目录）。
            if (root != null) {
                val alive = runCatching {
                    root.canRead() && root.exists()
                }.getOrDefault(false)
                if (alive) return SafLedgerIO(appContext, root)
                // 授权失效：清掉失效指向，回落内部存储，避免 UI 误显示"没有账户/账本"
                cfg.edit().remove(KEY_STORAGE_TREE).apply()
                // 提示写进"上次切换"结果（设置页可见），toast 可能因 init 时机太早不显示
                cfg.edit().putString(KEY_LAST_SWITCH, "外部目录权限已失效，已回到内部存储；请重新设置选择目录以恢复数据（原外部数据未被删除）").apply()
                runCatching {
                    toast("外部存储目录权限已失效，已回到内部存储；请重新设置选择目录以恢复数据")
                }
                return FileLedgerIO(File(appContext.filesDir, "ledgers").apply { mkdirs() })
            }
            FileLedgerIO(File(appContext.filesDir, "ledgers").apply { mkdirs() })
        }
    }

    /* ================= 账户体系 ================= */

    /** 账户文件夹名（= 账户名清洗后；账户名唯一故文件夹唯一） */
    private fun accountFolder(name: String): String = sanitizeFileName(name)

    /** 读账户索引原始 JSON */
    private fun readAccountsRaw(): List<JSONObject> =
        io.read(ACCOUNTS_JSON)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }.getOrDefault(emptyList())
        } ?: emptyList()

    fun accounts(): List<Account> =
        readAccountsRaw().mapNotNull {
            runCatching {
                Account(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    createdAt = it.optString("created", ""),
                    updatedAt = it.optLong("updated", 0L),
                )
            }.getOrNull()
        }

    /**
     * 首页账号列表：按最近编辑时间倒序（最新修改最上）；时间相同（含旧数据无 updated）
     * 再按名称升序。新建/切换/改名/删除其他账户都会刷新时间戳，保证"最近用过的最靠前"。
     */
    fun accountsByRecent(): List<Account> = accounts().sortedWith(
        compareByDescending<Account> { it.updatedAt }
            .thenBy { it.name }
    )

    fun account(id: String): Account? = accounts().find { it.id == id }

    /** 当前选中账户（首页展示）；无则取第一个 */
    fun currentAccountId(): String? {
        val cur = cfg.getString(KEY_CUR_ACCOUNT, null)
        val list = accounts()
        if (list.isEmpty()) return null
        return if (list.any { it.id == cur }) cur else list.first().id
    }

    fun setCurrentAccountId(id: String) {
        cfg.edit().putString(KEY_CUR_ACCOUNT, id).apply()
        touchAccountUpdated(id)   // 切换即"最近编辑"，首页排序把该账号置顶
    }

    /** 把某账户的 updated 字段刷新为当前时间（首页按"最近修改"排序依据） */
    fun touchAccountUpdated(id: String) {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        var changed = false
        readAccountsRaw().forEach { o ->
            if (o.optString("id") == id && o.optLong("updated", 0L) < now) {
                arr.put(JSONObject().apply {
                    put("id", o.optString("id")); put("name", o.optString("name"))
                    put("created", o.optString("created", "")); put("updated", now)
                    put("folder", o.optString("folder", "")); put("size", o.optLong("size", 0L))
                })
                changed = true
            } else arr.put(o)
        }
        if (changed) safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
    }

    /** 某账本所属账户 */
    fun ledgerAccount(ledgerId: String): Account? =
        ledger(ledgerId)?.accountId?.let { account(it) }

    /* ================= 兼容整理（normalize，幂等） ================= */

    /**
     * 对 [target] 后端执行一次「布局规范化」：
     *   1) 根目录出现旧账户过渡文件 accounts.json + <账户文件夹>/ledgers.json 等
     *      → 改名/就地归一为新文件名（my_account.json / my_choice.json / my_saving.json / my_payment.json / my_ledger_*）。
     *   2) 根目录出现旧式数据（SharedPreferences 由调用侧传入、SAF 根文件 pixelbook_*.json、
     *      {账本名}_{时间戳}.json 备份、老三文件 txs.<id>/dps.<id>/wx.<id>）且无账户
     *      → 建立「默认账户」文件夹并搬入，逐账本保证不丢。
     *   3) 每个账户文件夹补齐四个规范文件（空则以 [] 占位）。
     * 幂等：以 my_account.json 是否存在为界；某一步失败不影响已完成的迁移。
     */
    private fun normalize(target: LedgerIO): NormalizeSummary {
        val summary = NormalizeSummary()
        runCatching {
            // ---- 0) 读旧账户索引（过渡期 accounts.json，可能直接就是新布局的旧名） ----
            // 过渡期各后端把顶层 key 加 pixelbook_ 前缀落盘 → pixelbook_accounts.json；
            // 也兼容某些中间态直接写 accounts.json。
            val oldAcctsRaw = target.readNamedRaw("${LEGACY_PREFIX}${OLD_ACCOUNTS_JSON}")
                ?: target.readNamedRaw(OLD_ACCOUNTS_JSON)
            val oldAccts: List<JSONObject> = oldAcctsRaw
                ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }
                ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
                ?: emptyList()

            // ---- 1) 账户化过渡期数据（accounts.json 已存在）→ 就地改名归一 ----
            if (oldAccts.isNotEmpty()) {
                summary.accounts += renameTransitionalLayout(target, oldAccts)
            }

            // 若归一后已有 my_account.json，说明不是「旧版裸数据」，直接结束
            if (target.read(ACCOUNTS_JSON) != null) return@runCatching

            // ---- 2) 纯旧版数据 → 建默认账户搬入 ----
            migrateLegacyIntoDefault(target, summary)
        }
        // 无论上面做了什么，确保每个账户文件夹都有四个规范文件（缺则占位补空）
        runCatching { ensureStandardFiles(target) }
        // 数据整理/迁移后刷新账户占用空间
        runCatching {
            readAccountsRaw().forEach { it.optString("id").takeIf { id -> id.isNotEmpty() }?.let { id -> refreshAccountSize(id) } }
        }
        return summary
    }

    private class NormalizeSummary {
        var accounts = 0
        var migrated = 0
    }

    /** 过渡期布局（accounts.json + 各账户文件夹内 ledgers.json/cats.json/…）→ 新文件名。返回处理后账户数 */
    private fun renameTransitionalLayout(target: LedgerIO, oldAccts: List<JSONObject>): Int {
        var accCount = 0
        // 1) accounts.json → my_account.json（原样，兼容旧字段 + 追加 folder 缺省）
        val arr = JSONArray()
        for (o in oldAccts) {
            val id = o.optString("id"); val name = o.optString("name"); if (id.isEmpty() || name.isEmpty()) continue
            val folder = o.optString("folder", "").ifBlank { accountFolder(name) }
            val size = o.optLong("size", 0L)
            arr.put(JSONObject().apply {
                put("id", id); put("name", name); put("created", o.optString("created", ""))
                put("folder", folder); if (size > 0) put("size", size)
            })
            accCount++
        }
        if (accCount > 0) {
            target.write(ACCOUNTS_JSON, arr.toString())
            target.removeRaw("${LEGACY_PREFIX}${OLD_ACCOUNTS_JSON}")
            target.removeRaw(OLD_ACCOUNTS_JSON)
        }
        // 2) 各账户文件夹内文件改名
        for (o in oldAccts) {
            val folder = o.optString("folder", "").ifBlank { accountFolder(o.optString("name")) }
            val dirFiles = target.listDirRaw(folder)
            // ledgers.json → 逐账本数据包改名 my_ledger_*
            dirFiles.firstOrNull { it == OLD_LEDGERS_JSON }?.let { idxFile ->
                target.readNamedRaw("$folder/$idxFile")?.let { raw ->
                    val list = runCatching { parseLedgers(raw) }.getOrDefault(emptyList())
                    for (l in list) {
                        val oldName = l.file.ifBlank { ledgerFileName(l) }
                        val newName = ledgerFileName(l)
                        // 先改名数据包（不存在新名时才搬），再删索引项；读不到包则跳过（不删索引，防丢）
                        if (newName != oldName && target.readNamedRaw("$folder/$newName") == null) {
                            if (target.readNamedRaw("$folder/$oldName") != null) {
                                target.renameRaw("$folder/$oldName", "$folder/$newName")
                            }
                        }
                    }
                }
                target.removeRaw("$folder/$idxFile")
            }
            // 保留已知扩展名的账本备份文件（老名 {账本名}_{时间戳}.json）→ my_ledger_*（无索引也认得）
            dirFiles.forEach { fn ->
                if (fn.endsWith(LEDGER_FILE_SUFFIX) && !fn.startsWith(LEDGER_FILE_PREFIX) &&
                    fn != OLD_CATS_JSON && fn != OLD_LEDGERS_JSON && !fn.contains("_${OLD_ASSETS_SUB}") && !fn.contains("_${OLD_DEP_SUB}")
                ) {
                    renameLedgerFileToStandard(target, folder, fn)
                }
            }
            // cats.json → my_choice.json
            if (dirFiles.contains(OLD_CATS_JSON) && !dirFiles.contains(CATS_JSON)) {
                target.readNamedRaw("$folder/${OLD_CATS_JSON}")?.let { raw ->
                    target.writeNamedRaw("$folder/$CATS_JSON", raw)
                }
                target.removeRaw("$folder/${OLD_CATS_JSON}")
            }
            // <账户名>_资产信息.json → my_payment.json（账户名可能已改，按文件夹名与已知后缀识别）
            dirFiles.firstOrNull { it.endsWith("_${OLD_ASSETS_SUB}$LEDGER_FILE_SUFFIX") }?.let { oldF ->
                if (!dirFiles.contains(PAYMENT_JSON)) {
                    target.readNamedRaw("$folder/$oldF")?.let { raw -> target.writeNamedRaw("$folder/$PAYMENT_JSON", raw) }
                }
                target.removeRaw("$folder/$oldF")
            }
            // <账户名>_存款明细.json → my_saving.json
            dirFiles.firstOrNull { it.endsWith("_${OLD_DEP_SUB}$LEDGER_FILE_SUFFIX") }?.let { oldF ->
                if (!dirFiles.contains(SAVING_JSON)) {
                    target.readNamedRaw("$folder/$oldF")?.let { raw -> target.writeNamedRaw("$folder/$SAVING_JSON", raw) }
                }
                target.removeRaw("$folder/$oldF")
            }
            // 兜底：账本数据包内残留 dps → 汇入 my_saving（幂等）
            runCatching { foldBundledDepsToSaving(target, folder) }
        }
        return accCount
    }

    /**
     * 把任意非规范 json 账本文件改名为 my_ledger_<真实名>_<时间戳>.json。
     * 真实名/时间戳从文件内容头部的 ledger 声明读取（无声明则以文件名推导）。
     */
    private fun renameLedgerFileToStandard(target: LedgerIO, folder: String, oldName: String) {
        val raw = target.readNamedRaw("$folder/$oldName") ?: return
        val meta = parseBundleMeta(raw) ?: return
        val newName = ledgerFileName(meta.ledger)
        if (newName == oldName || target.readNamedRaw("$folder/$newName") != null) return
        target.writeNamedRaw("$folder/$newName", raw)
        target.removeRaw("$folder/$oldName")
    }

    /** 旧版裸数据（无账户）→ 默认账户 */
    private fun migrateLegacyIntoDefault(target: LedgerIO, summary: NormalizeSummary) {
        val defId = "a${System.currentTimeMillis()}"
        val folder = accountFolder(DEFAULT_ACCOUNT_NAME)
        // 旧账本数据源：
        //   a) SharedPreferences（内部存储后端）：ledgers 索引 + ledger.<id> 包 + txs./dps./wx. 老三文件
        //   b) SAF 目录根：pixelbook_ledgers.json / pixelbook_cats.json / pixelbook_ledger.<id> / {账本名}_{时间戳}.json
        val legacyLedgers = mutableListOf<Pair<Ledger, String?>>()  // (账本, 数据包JSON原文)
        val rootJsonSources = mutableListOf<String>()               // SAF 根目录旧账本源文件（待清理）
        var catsRaw: String? = null

        if (target is FileLedgerIO) {
            // 内部存储：旧数据在 pixelbook_data SharedPreferences
            val rawLedgers = legacyPrefs.getString(LEGACY_LEDGERS, null)
            val idxList = rawLedgers?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) } ?: emptyList()
            for (l in idxList) {
                val body = legacyPrefs.getString("${LEGACY_KEY_PREFIX}${l.id}", null)
                // 只有 body 非空才登记；body==null 的账本可能只有老三件，由下面循环补全（防占位导致老三件跳过而丢账本）
                if (body != null) legacyLedgers.add(l to body)
            }
            catsRaw = legacyPrefs.getString(LEGACY_CATS, null)
            // 老三文件（更老版本：txs.<id> / dps.<id> / wx.<id>）——没有 ledger.<id> 时用老三件组装
            val allKeys = legacyPrefs.all.keys
            val legacyTxIds = allKeys.filter { it.startsWith(LEGACY_TX_P) }.map { it.removePrefix(LEGACY_TX_P) }
            for (txId in legacyTxIds) {
                if (legacyLedgers.any { it.first.id == txId }) continue
                val l = idxList.find { it.id == txId }
                if (l == null) continue
                val body = buildLegacyBundle(l,
                    legacyPrefs.getString("${LEGACY_TX_P}$txId", null),
                    legacyPrefs.getString("${LEGACY_DP_P}$txId", null),
                    legacyPrefs.getString("${LEGACY_WX_P}$txId", null),
                )
                legacyLedgers.add(l to body)
            }
        } else {
            // SAF 目录：读根级旧文件。物理名带 .json 后缀（pixelbook_ledgers.json），
            // 历史 bug：旧迁移代码读 pixelbook_ledgers（无后缀）→ 永远读空 → 账本丢失。这里全变体兜底。
            val rawLedgers = readVariants(target, "${LEGACY_PREFIX}${LEGACY_LEDGERS}")
                ?: target.readNamedRaw(OLD_LEDGERS_JSON)
            val idxList = rawLedgers?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) } ?: emptyList()
            for (l in idxList) {
                // 按优先级读账本包：pixelbook_ledger.<id>(老三件之后的单包key) → pixelbook_ledger.<id>.json →
                // 索引 file 指向的根 json（v2 外部目录 {账本名}_{时间戳}.json）
                val prefixed = readVariants(target, "${LEGACY_PREFIX}${LEGACY_KEY_PREFIX}${l.id}")
                    ?: readVariants(target, "${LEGACY_PREFIX}ledger.${l.id}")
                val body = prefixed
                    ?: runCatching { target.readNamedRaw(l.file) }.getOrNull()
                // body==null 的账本可能只有老三件，由下面老三件循环补全（防占位导致跳过而丢账本）
                if (body != null) {
                    legacyLedgers.add(l to body)
                    // 账本包若来自索引 file 指向的根 json（非 pixelbook_ 前缀文件）→ 登记待清理，防根目录残留
                    if (prefixed == null && l.file.isNotBlank() && !l.file.startsWith(LEGACY_PREFIX)) {
                        rootJsonSources.add(l.file)
                    }
                }
            }
            catsRaw = readVariants(target, "${LEGACY_PREFIX}${LEGACY_CATS}")
                ?: readVariants(target, "${LEGACY_PREFIX}cats")
            // 老三文件在 SAF 根目录也兜底（老版本 pixelbook_txs.<id>.json 等）
            target.listDirRaw("").filter { it.startsWith("${LEGACY_PREFIX}${LEGACY_TX_P}") }.forEach { f ->
                val txId = f.removePrefix("${LEGACY_PREFIX}${LEGACY_TX_P}").removeSuffix(".json")
                if (legacyLedgers.any { it.first.id == txId }) return@forEach
                val l = idxList.find { it.id == txId } ?: return@forEach
                val body = buildLegacyBundle(l,
                    target.readNamedRaw(f),
                    readVariants(target, "${LEGACY_PREFIX}${LEGACY_DP_P}$txId"),
                    readVariants(target, "${LEGACY_PREFIX}${LEGACY_WX_P}$txId"),
                )
                if (body != null) {
                    legacyLedgers.add(l to body)
                    rootJsonSources.add(f)
                }
            }
            // 备份文件 {账本名}_{时间戳}.json（pixelbook_ 前缀之外的 json）：
            //   无论有无索引都当作账本包扫描（去重：已在 legacyLedgers 的 id 跳过），
            //   覆盖「索引在但包漏读」「只有备份文件无索引」两种情况，保证账本不丢。
            target.listDirRaw("").filter { it.endsWith(".json") && !it.startsWith(LEGACY_PREFIX) }
                .forEach { fn ->
                    val raw = target.readNamedRaw(fn) ?: return@forEach
                    val meta = parseBundleMeta(raw) ?: return@forEach
                    if (legacyLedgers.any { it.first.id == meta.ledger.id }) return@forEach
                    legacyLedgers.add(meta.ledger to raw)
                    rootJsonSources.add(fn)
                }
        }

        // 没有任何可迁移数据（无账本、无旧类别、根目录也无文件）→ 首次全新安装，不建默认账户，
        // 由首页引导用户新建账户。否则必须建默认账户并把旧数据搬入。
        val rootHasFiles = target.listDirRaw("").isNotEmpty()
        if (legacyLedgers.isEmpty() && catsRaw == null && !rootHasFiles) {
            return
        }
        target.createDir(folder)
        // 类别：旧 cats → my_choice；没有则写默认内置类别
        val cats = catsRaw ?: catsJson(IncomeCats.list, ExpenseCats.list)
        target.writeNamedRaw("$folder/$CATS_JSON", cats)
        // 存款/钱包占位空文件
        target.writeNamedRaw("$folder/$SAVING_JSON", "[]")
        target.writeNamedRaw("$folder/$PAYMENT_JSON", "[]")
        // 逐账本搬入：文件已规范名（沿用索引里的 file 或推导）
        var ok = 0
        for ((l, body) in legacyLedgers) {
            val full = body ?: continue
            val meta = runCatching { JSONObject(full) }.getOrNull()
            val realL = if (meta != null) (parseBundleMeta(full)?.ledger ?: l) else l
            val accLedger = realL.copy(accountId = defId)
            val fileName = ledgerFileName(accLedger)
            val path = "$folder/$fileName"
            if (target.readNamedRaw(path) != null) continue   // 已存在同名则跳过（幂等）
            target.writeNamedRaw(path, full)
            ok++
        }
        // 注册账户（含 folder 与大小占位）
        val meta = JSONObject().apply {
            put("id", defId); put("name", DEFAULT_ACCOUNT_NAME)
            put("created", LocalDate.now().toString()); put("folder", folder); put("size", 0L)
        }
        val arr = JSONArray().apply { put(meta) }
        target.write(ACCOUNTS_JSON, arr.toString())
        summary.accounts = 1
        summary.migrated = ok

        // 数据包内残留旧存款（按账本存）→ 汇入 my_saving.json
        runCatching { foldBundledDepsToSaving(target, folder) }

        // 清理旧源（内部 SharedPreferences / SAF 根旧文件）——读到并搬走后才删
        if (target is FileLedgerIO) {
            runCatching {
                legacyPrefs.edit()
                    .remove(LEGACY_LEDGERS).remove(LEGACY_CATS).commit()
                legacyLedgers.forEach { (l, _) ->
                    legacyPrefs.edit().remove("${LEGACY_KEY_PREFIX}${l.id}")
                        .remove("${LEGACY_TX_P}${l.id}").remove("${LEGACY_DP_P}${l.id}").remove("${LEGACY_WX_P}${l.id}").commit()
                }
            }
        } else {
            runCatching {
                target.removeRaw("${LEGACY_PREFIX}${LEGACY_LEDGERS}")
                target.removeRaw("${LEGACY_PREFIX}${LEGACY_CATS}")
                legacyLedgers.forEach { (l, _) ->
                    target.removeRaw("${LEGACY_PREFIX}${LEGACY_KEY_PREFIX}${l.id}")
                    target.removeRaw("${LEGACY_PREFIX}ledger.${l.id}")
                }
                // 仅清理「确认是账本包且已搬走」的根 json（无前缀备份文件同样校验内容为账本包后才删）
                target.listDirRaw("").filter {
                    it.endsWith(".json") && !it.startsWith(LEDGER_FILE_PREFIX) &&
                        (it.startsWith(LEGACY_PREFIX) || it in rootJsonSources)
                }.forEach { target.removeRaw(it) }
            }
        }
    }

    /** 老三文件（txs/dps/wx 分离）→ 单包 JSON */
    private fun buildLegacyBundle(l: Ledger, txsRaw: String?, dpsRaw: String?, wxRaw: String?): String? {
        if (txsRaw == null && dpsRaw == null && wxRaw == null) return null
        return JSONObject().apply {
            put("app", "pixelbook"); put("type", "ledger"); put("version", 2)
            put("ledger", JSONObject().apply {
                put("id", l.id); put("name", l.name); put("cover", l.coverColor); put("font", l.font)
                put("synced", JSONArray(l.syncedMonths.toList()))
            })
            put("wx", runCatching { JSONObject(wxRaw ?: "{}") }.getOrDefault(JSONObject()))
            put("txs", runCatching { JSONArray(txsRaw ?: "[]") }.getOrDefault(JSONArray()))
            put("dps", runCatching { JSONArray(dpsRaw ?: "[]") }.getOrDefault(JSONArray()))
        }.toString()
    }

    /** 每个账户文件夹补齐规范文件（缺则空占位） */
    private fun ensureStandardFiles(target: LedgerIO) {
        val accs = target.read(ACCOUNTS_JSON)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    o.optString("id").takeIf { it.isNotEmpty() }?.let { id ->
                        id to o.optString("folder", "").ifBlank { accountFolder(o.optString("name")) }
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        for ((_, folder) in accs) {
            val existing = target.listDirRaw(folder)
            if (!existing.contains(CATS_JSON)) target.writeNamedRaw("$folder/$CATS_JSON", catsJson(IncomeCats.list, ExpenseCats.list))
            if (!existing.contains(SAVING_JSON)) target.writeNamedRaw("$folder/$SAVING_JSON", "[]")
            if (!existing.contains(PAYMENT_JSON)) target.writeNamedRaw("$folder/$PAYMENT_JSON", "[]")
        }
    }

    /**
     * 把某账户文件夹内 my_ledger_*.json 数据包里残留的旧 dps（按账本存的存款）
     * 汇入 my_saving.json（标注来源账本），并从数据包中移除 dps 键。
     * 语义与旧 migrateDepsToAccount 一致；normalize 两分支均调用，保证不丢存款。
     */
    private fun foldBundledDepsToSaving(target: LedgerIO, folder: String) {
        val savingPath = "$folder/$SAVING_JSON"
        var saving = target.readNamedRaw(savingPath)
            ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }
            ?: JSONArray()
        var changedSaving = false
        val ledgerFiles = target.listDirRaw(folder).filter {
            it.startsWith(LEDGER_FILE_PREFIX) && it.endsWith(LEDGER_FILE_SUFFIX)
        }
        for (fn in ledgerFiles) {
            val path = "$folder/$fn"
            val raw = target.readNamedRaw(path) ?: continue
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val dpsArr = obj.optJSONArray("dps") ?: continue
            if (dpsArr.length() == 0) continue
            // 账本名（来源标注）
            val lname = obj.optJSONObject("ledger")?.optString("name", "") ?: ""
            val depList = parseDeps(dpsArr.toString())
            var added = 0
            for (d in depList) {
                // 避免与已汇入的重复（同名同值同日期视为已存在）
                val dup = (0 until saving.length()).any { i ->
                    val o = saving.getJSONObject(i)
                    o.optString("name") == d.name && o.optLong("value") == d.value && o.optString("date") == d.date.toString()
                }
                if (dup) continue
                val note = if (d.note.isNotEmpty() && !d.note.contains("来自账本")) "${d.note} · 来自账本「$lname」"
                else if (d.note.isEmpty()) "来自账本「$lname」" else d.note
                saving.put(JSONObject().apply {
                    put("id", d.id); put("ld", d.ledgerId); put("date", d.date.toString())
                    put("kind", d.kind.name); put("name", d.name); put("note", note); put("value", d.value)
                })
                added++
            }
            if (added > 0) {
                obj.remove("dps")
                target.writeNamedRaw(path, obj.toString())
                changedSaving = true
            }
        }
        if (changedSaving) target.writeNamedRaw(savingPath, saving.toString())
    }

    /** 按多种物理命名读文件（兼容带/不带 .json 后缀的旧文件） */
    private fun readVariants(target: LedgerIO, base: String): String? {
        if (base.endsWith(LEDGER_FILE_SUFFIX)) return target.readNamedRaw(base) ?: target.readNamedRaw(base.removeSuffix(LEDGER_FILE_SUFFIX))
        return target.readNamedRaw(base) ?: target.readNamedRaw("$base$LEDGER_FILE_SUFFIX")
    }

    /** 数据包头部元信息解析：不解析 txs/dps 大数组，只取 ledger 声明 */
    private fun parseBundleMeta(raw: String): BundleMeta? = runCatching {
        val o = JSONObject(raw)
        if (o.optString("type") != "ledger" && !o.has("ledger")) return null
        val lo = o.getJSONObject("ledger")
        val synced = mutableSetOf<String>()
        val sa = lo.optJSONArray("synced")
        if (sa != null) for (j in 0 until sa.length()) synced.add(sa.getString(j))
        val id = lo.getString("id")
        BundleMeta(
            Ledger(
                id = id,
                name = lo.getString("name"),
                coverColor = lo.optInt("cover", 0),
                font = lo.optString("font", "pixel"),
                syncedMonths = synced,
                file = ledgerFileName(Ledger(id, lo.optString("name", ""), lo.optInt("cover", 0))),
                accountId = "",
            ),
            o,
        )
    }.getOrNull()

    private class BundleMeta(val ledger: Ledger, val obj: JSONObject)

    /** 新规范账本文件名：my_ledger_<清洗名>_<时间戳>.json（时间戳取自账本 id） */
    private fun ledgerFileName(l: Ledger): String =
        "${LEDGER_FILE_PREFIX}${sanitizeFileName(l.name)}_${createStampMs(l.id)}$LEDGER_FILE_SUFFIX"

    /* ================= 账户 CRUD（含文件夹自动同步） ================= */

    /** 新建账户：同名不允许；返回 null 表示失败 */
    fun addAccount(name: String): Account? {
        val nm = name.trim()
        if (nm.isEmpty() || accounts().any { it.name == nm }) return null
        val id = "a${newId()}"
        val folder = accountFolder(nm)
        io.createDir(folder)
        // 规范文件：类别（内置默认）+ 存款/钱包空占位（空也是 [] 文件）
        safeIo {
            io.writeNamedRaw("$folder/$CATS_JSON", catsJson(IncomeCats.list, ExpenseCats.list))
            io.writeNamedRaw("$folder/$SAVING_JSON", "[]")
            io.writeNamedRaw("$folder/$PAYMENT_JSON", "[]")
        }
        appendAccountMeta(id, nm, folder)
        return Account(id, nm)
    }

    /** 追加账户到 my_account.json（含 folder；size 按需重算占位 0） */
    private fun appendAccountMeta(id: String, name: String, folder: String) {
        val arr = readAccountsRaw().toMutableList()
        arr.add(JSONObject().apply {
            put("id", id); put("name", name); put("created", LocalDate.now().toString()); put("folder", folder); put("size", 0L)
        })
        safeIo { io.write(ACCOUNTS_JSON, JSONArray(arr).toString()) }
    }

    /**
     * 重算账户数据占用（账户文件夹内全部文件字节和）并写回 my_account.json 的 size 字段。
     * 数据写盘后调用，供文件管理器/设置页展示“账本占用存储空间”。
     */
    fun refreshAccountSize(accountId: String) {
        val folder = folderOfAccount(accountId) ?: return
        val size = runCatching { io.dirSizeBytes(folder) }.getOrDefault(0L)
        val list = readAccountsRaw().toMutableList()
        val arr = JSONArray()
        list.forEach {
            if (it.optString("id") == accountId) {
                arr.put(JSONObject().apply {
                    put("id", it.optString("id")); put("name", it.optString("name"))
                    put("created", it.optString("created")); put("folder", it.optString("folder"))
                    put("size", size)
                })
            } else arr.put(it)
        }
        safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
    }

    /** 账户占用空间（字节）——直接从 my_account.json 读取 */
    fun accountSizeBytes(accountId: String): Long =
        readAccountsRaw().firstOrNull { it.optString("id") == accountId }?.optLong("size", 0L) ?: 0L

    /** 改名：同步重命名账户文件夹；同名/空名返回 false */
    fun renameAccount(id: String, newName: String): Boolean {
        val old = account(id) ?: return false
        val nm = newName.trim()
        if (nm.isEmpty() || accounts().any { it.name == nm }) return false
        val oldFolder = accountFolder(old.name)
        val newFolder = accountFolder(nm)
        // 账户文件夹存在才改名（防止异常状态下重命名空名目录）
        if (io.listDirRaw(oldFolder).isNotEmpty() || io.readNamedRaw("$oldFolder/$CATS_JSON") != null ||
            io.readNamedRaw("$oldFolder/$SAVING_JSON") != null || io.readNamedRaw("$oldFolder/$PAYMENT_JSON") != null
        ) {
            runCatching { io.renameDir(oldFolder, newFolder) }
        }
        val list = readAccountsRaw().toMutableList()
        val arr = JSONArray()
        list.forEach {
            if (it.optString("id") == id) {
                arr.put(JSONObject().apply {
                    put("id", id); put("name", nm); put("created", it.optString("created", ""))
                    put("folder", newFolder); put("size", it.optLong("size", 0L))
                })
            } else arr.put(it)
        }
        safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
        return true
    }

    /** 删除账户（连同文件夹与全部账本数据）；id 不存在返回 false */
    fun deleteAccount(id: String): Boolean {
        val old = account(id) ?: return false
        // 先收集该账户下账本 id（用于清流水缓存），再删文件夹
        val doomed = ledgersOf(id).map { it.id }
        io.deleteDir(accountFolder(old.name))
        ledgerMetaCache.remove(id)
        doomed.forEach { txCache.remove(it) }
        val arr = JSONArray()
        readAccountsRaw().forEach { if (it.optString("id") != id) arr.put(it) }
        safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
        if (cfg.getString(KEY_CUR_ACCOUNT, null) == id) cfg.edit().remove(KEY_CUR_ACCOUNT).apply()
        return true
    }

    /* ================= 账本（扫描账户文件夹内 my_ledger_*.json，无索引文件） ================= */

    /** 账户文件夹相对路径（由账户 meta 的 folder 或名称计算） */
    private fun folderOfAccount(accountId: String): String? {
        val a = account(accountId) ?: return null
        return readAccountsRaw().firstOrNull { it.optString("id") == accountId }
            ?.optString("folder", "")
            ?.takeIf { it.isNotBlank() }
            ?: accountFolder(a.name)
    }

    /** 某账户下账本（扫描 my_ledger_*.json；只解析新增文件的头部，按创建时间戳倒序） */
    fun ledgersOf(accountId: String): List<Ledger> {
        val folder = folderOfAccount(accountId) ?: return emptyList()
        val cache = ledgerMetaCache.getOrPut(accountId) { mutableMapOf() }
        val files = io.listDirRaw(folder)
            .filter { it.startsWith(LEDGER_FILE_PREFIX) && it.endsWith(LEDGER_FILE_SUFFIX) }
            .toSet()
        // 移除已删除文件的缓存
        cache.keys.retainAll(files)
        // 解析新增文件（只读头部元信息）
        for (fn in files) {
            if (fn in cache) continue
            val raw = io.readNamedRaw("$folder/$fn") ?: continue
            val meta = parseBundleMeta(raw)?.ledger ?: continue
            cache[fn] = meta.copy(accountId = accountId, file = fn)
        }
        return cache.values.sortedByDescending { idStampMs(it.id) }
    }

    fun ledgers(): List<Ledger> = accounts().flatMap { ledgersOf(it.id) }

    fun ledger(id: String): Ledger? = ledgers().find { it.id == id }

    /** 账本 id → 创建毫秒（时间戳排序/文件名用） */
    private fun idStampMs(id: String): Long = id.substringBefore("_").toLongOrNull() ?: 0L

    /** 新建账本（accountId 账户下，最多 60 本）；超限返回 null */
    fun addLedger(accountId: String, name: String, coverIdx: Int): Ledger? {
        val folder = folderOfAccount(accountId) ?: return null
        if (ledgersOf(accountId).size >= MAX_LEDGER_PER_ACCOUNT) return null
        val id = newId()
        val l = Ledger(id = id, name = name.trim(), coverColor = coverIdx,
            file = ledgerFileName(Ledger(id, name.trim(), coverIdx)), accountId = accountId)
        // 初始化空数据包（含账本信息）；成功后文件即存在，缓存直接登记（省一次重扫）
        safeBundleSave(l, emptyBundleLike(l))
        if (io.readNamedRaw("$folder/${l.file}") != null) {
            ledgerMetaCache.getOrPut(accountId) { mutableMapOf() }[l.file] = l
        }
        refreshAccountSize(accountId)
        return l
    }

    /** 编辑账本（改名/字体/封面色）；改名自动同步账本单文件名 */
    fun updateLedger(id: String, name: String, font: String, coverColor: Int) {
        val old = ledger(id) ?: return
        val accId = old.accountId
        val folder = folderOfAccount(accId) ?: return
        val newName = name.trim()
        val updated = old.copy(name = newName, font = font, coverColor = coverColor)
        if (newName != old.name) {
            val oldFile = old.file.ifBlank { ledgerFileName(old) }
            val newFile = ledgerFileName(updated)
            // 数据包内 name/字体/封面色同步；文件改名（新名不存在才搬，防覆盖）
            if (newFile != oldFile && io.readNamedRaw("$folder/$newFile") == null) {
                readBundle(id)?.let { obj ->
                    runCatching {
                        obj.getJSONObject("ledger").apply {
                            put("name", newName); put("font", font); put("cover", coverColor)
                        }
                    }
                    safeIo {
                        io.renameRaw("$folder/$oldFile", "$folder/$newFile")
                        io.writeNamedRaw("$folder/$newFile", obj.toString())
                    }
                }
            } else if (newFile != oldFile) {
                // 目标名已存在：退化为覆盖内容到新名、删旧名（内容以本次编辑为准）
                readBundle(id)?.let { obj ->
                    runCatching {
                        obj.getJSONObject("ledger").apply {
                            put("name", newName); put("font", font); put("cover", coverColor)
                        }
                    }
                    safeIo {
                        io.writeNamedRaw("$folder/$newFile", obj.toString())
                        io.removeRaw("$folder/$oldFile")
                    }
                }
            }
            // 更新缓存
            val cache = ledgerMetaCache.getOrPut(accId) { mutableMapOf() }
            cache.remove(oldFile)
            cache[newFile] = updated.copy(file = newFile)
        } else {
            // 仅字体/封面色变化：内容同步 + 缓存更新
            readBundle(id)?.let { obj ->
                runCatching {
                    obj.getJSONObject("ledger").apply { put("font", font); put("cover", coverColor) }
                }
                safeIo { io.writeNamedRaw("$folder/${old.file}", obj.toString()) }
            }
            val cache = ledgerMetaCache.getOrPut(accId) { mutableMapOf() }
            cache[old.file] = updated.copy(file = old.file)
        }
        refreshAccountSize(accId)
    }

    fun deleteLedger(id: String) {
        val old = ledger(id) ?: return
        val accId = old.accountId
        val folder = folderOfAccount(accId) ?: return
        val f = old.file.ifBlank { ledgerFileName(old) }
        safeIo { io.removeRaw("$folder/$f") }
        ledgerMetaCache[accId]?.remove(f)
        txCache.remove(id)
        refreshAccountSize(accId)
    }

    /* ================= 类别（按账户） ================= */

    private fun readCatsObj(accountId: String): JSONObject? {
        val folder = folderOfAccount(accountId) ?: return null
        val raw = io.readNamedRaw("$folder/$CATS_JSON") ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun readCatsArr(obj: JSONObject?, tag: String, defaults: List<String>): List<String> {
        obj?.optJSONArray(tag)?.let { arr ->
            return (0 until arr.length()).map { arr.getString(it) }
        } ?: return defaults
    }

    private fun catsJson(income: List<String>, expense: List<String>): String =
        JSONObject().apply {
            put("in", JSONArray(income)); put("out", JSONArray(expense))
        }.toString()

    private fun writeCats(accountId: String, obj: JSONObject) {
        val folder = folderOfAccount(accountId) ?: return
        safeIo { io.writeNamedRaw("$folder/$CATS_JSON", obj.toString()) }
    }

    fun incomeCats(accountId: String): List<String> = readCatsArr(readCatsObj(accountId), "in", IncomeCats.list)
    fun expenseCats(accountId: String): List<String> = readCatsArr(readCatsObj(accountId), "out", ExpenseCats.list)

    private fun readCatsFor(accountId: String, tag: String, defaults: List<String>): List<String> {
        val o = readCatsObj(accountId)
        val list = readCatsArr(o, tag, defaults)
        // 新装账户未写文件时补一个默认文件
        if (o == null) writeCats(accountId, JSONObject().apply {
            put("in", JSONArray(readCatsArr(null, "in", IncomeCats.list)))
            put("out", JSONArray(readCatsArr(null, "out", ExpenseCats.list)))
        })
        return list
    }

    fun addIncomeCat(accountId: String, name: String): Boolean = addCat(accountId, "in", IncomeCats.list, name)
    fun addExpenseCat(accountId: String, name: String): Boolean = addCat(accountId, "out", ExpenseCats.list, name)
    fun renameIncomeCat(accountId: String, old: String, new: String): Boolean = renameCat(accountId, "in", IncomeCats.list, TxDir.IN, old, new)
    fun renameExpenseCat(accountId: String, old: String, new: String): Boolean = renameCat(accountId, "out", ExpenseCats.list, TxDir.OUT, old, new)
    fun deleteIncomeCat(accountId: String, name: String): Boolean = deleteCat(accountId, "in", IncomeCats.list, TxDir.IN, name)
    fun deleteExpenseCat(accountId: String, name: String): Boolean = deleteCat(accountId, "out", ExpenseCats.list, TxDir.OUT, name)

    private fun addCat(accountId: String, tag: String, defaults: List<String>, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n == CATEGORY_OTHERS) return false
        val cur = readCatsFor(accountId, tag, defaults)
        if (n in cur) return false
        writeCats(accountId, JSONObject().apply {
            put("in", JSONArray(if (tag == "in") cur + n else readCatsFor(accountId, "in", IncomeCats.list)))
            put("out", JSONArray(if (tag == "out") cur + n else readCatsFor(accountId, "out", ExpenseCats.list)))
        })
        return true
    }

    private fun renameCat(accountId: String, tag: String, defaults: List<String>, dir: TxDir, old: String, new: String): Boolean {
        val n = new.trim()
        val cur = readCatsFor(accountId, tag, defaults)
        if (n.isEmpty() || old == CATEGORY_OTHERS || n == CATEGORY_OTHERS || old !in cur || n in cur) return false
        val renamed = cur.map { if (it == old) n else it }
        writeCats(accountId, JSONObject().apply {
            put("in", JSONArray(if (tag == "in") renamed else readCatsFor(accountId, "in", IncomeCats.list)))
            put("out", JSONArray(if (tag == "out") renamed else readCatsFor(accountId, "out", ExpenseCats.list)))
        })
        applyCatRename(accountId, dir, old, n)
        return true
    }

    private fun deleteCat(accountId: String, tag: String, defaults: List<String>, dir: TxDir, name: String): Boolean {
        val cur = readCatsFor(accountId, tag, defaults)
        if (name == CATEGORY_OTHERS || name !in cur) return false
        val removed = cur.filterNot { it == name }
        writeCats(accountId, JSONObject().apply {
            put("in", JSONArray(if (tag == "in") removed else readCatsFor(accountId, "in", IncomeCats.list)))
            put("out", JSONArray(if (tag == "out") removed else readCatsFor(accountId, "out", ExpenseCats.list)))
        })
        applyCatRename(accountId, dir, name, CATEGORY_OTHERS)
        return true
    }

    /** 类别改名/删除后，该账户所有账本流水同步 */
    private fun applyCatRename(accountId: String, dir: TxDir, old: String, new: String) {
        for (l in ledgersOf(accountId)) {
            val list = txList(l.id).map {
                if (it.dir == dir && it.category == old) it.copy(category = new) else it
            }
            if (list != txList(l.id)) saveTxs(l.id, list)
        }
    }

    /* ================= 资产账户（账户文件夹内 my_payment.json） ================= */

    private fun assetsFile(accountId: String): String {
        val folder = folderOfAccount(accountId) ?: return ""
        return "$folder/$PAYMENT_JSON"
    }

    fun assetsOf(accountId: String): List<AssetAccount> {
        val f = assetsFile(accountId)
        if (f.isEmpty()) return emptyList()
        val raw = io.readNamedRaw(f) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    AssetAccount(
                        id = o.getString("id"),
                        category = o.optString("cat", ""),
                        sub = o.optString("sub", ""),
                        role = o.optString("role", ""),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAssets(accountId: String, list: List<AssetAccount>) {
        val f = assetsFile(accountId)
        if (f.isEmpty()) return
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("cat", it.category); put("sub", it.sub); put("role", it.role)
            })
        }
        safeIo { io.writeNamedRaw(f, arr.toString()) }
    }

    /** 新增/更新资产账户（角色不可重复）；成功返回 true */
    fun saveAsset(accountId: String, asset: AssetAccount): Boolean {
        val list = assetsOf(accountId).toMutableList()
        if (asset.role.isNotEmpty() && list.any { it.id != asset.id && it.role == asset.role }) return false
        val idx = list.indexOfFirst { it.id == asset.id }
        if (idx >= 0) list[idx] = asset else list.add(asset)
        writeAssets(accountId, list)
        return true
    }

    fun deleteAsset(accountId: String, assetId: String) {
        writeAssets(accountId, assetsOf(accountId).filterNot { it.id == assetId })
    }

    /** 资产账户当前余额（自动统计该账户所有账本中关联流水的净额，分） */
    fun assetBalance(accountId: String, assetId: String): Cents {
        var sum = 0L
        for (l in ledgersOf(accountId)) {
            for (t in txList(l.id)) {
                if (t.asset == assetId) sum += if (t.dir == TxDir.IN) t.amount else -t.amount
            }
        }
        return sum
    }

    /* ================= 账本单文件数据包 ================= */

    private fun bundleFilePath(l: Ledger): String? {
        val folder = folderOfAccount(l.accountId) ?: return null
        return "$folder/${l.file.ifBlank { ledgerFileName(l) }}"
    }

    private fun createStampMs(id: String): String {
        val ms = id.substringBefore("_").toLongOrNull()
        if (ms != null) {
            val fromId = runCatching {
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").format(
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
                )
            }.getOrNull()
            if (fromId != null) return fromId
        }
        return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").format(LocalDateTime.now())
    }

    /** 组装账本 JSON 包字符串 */
    private fun bundleJson(l: Ledger, txs: List<Tx>, dps: List<Deposit>, wx: JSONObject, bdg: JSONObject): String =
        JSONObject().apply {
            put("app", "pixelbook"); put("type", "ledger"); put("version", 2)
            put("ledger", JSONObject().apply {
                put("id", l.id); put("name", l.name); put("cover", l.coverColor); put("font", l.font)
                put("synced", JSONArray(l.syncedMonths.toList()))
            })
            put("wx", wx)
            if (bdg.length() > 0) put(KEY_BDG, bdg)
            put("txs", JSONArray().apply { txs.forEach { put(txToJson(it)) } })
            put("dps", JSONArray().apply { dps.forEach { put(depToJson(it)) } })
        }.toString()

    private fun readBundle(id: String): JSONObject? {
        val l = ledger(id) ?: return null
        val p = bundleFilePath(l) ?: return null
        val raw = io.readNamedRaw(p) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun emptyBundleLike(l: Ledger) =
        JSONObject(bundleJson(l, emptyList(), emptyList(), JSONObject(), JSONObject()))

    /** 写账本数据包（重建整个包：txs/dps/wx/bdg 合并写入） */
    private fun writeBundle(l: Ledger, obj: JSONObject) {
        val p = bundleFilePath(l) ?: return
        safeIo { io.writeNamedRaw(p, obj.toString()) }
    }

    private fun safeBundleSave(l: Ledger, obj: JSONObject) {
        try {
            val p = bundleFilePath(l) ?: return
            io.writeNamedRaw(p, obj.toString())
        } catch (e: Exception) {
            android.util.Log.e("PixelStore", "bundle save failed: ${l.id} -> ${e.message}", e)
            lastWriteError = "${e.message ?: e.javaClass.simpleName}"
            runCatching { toast("保存失败：${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun safeIo(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.e("PixelStore", "io failed -> ${e.message}", e)
            lastWriteError = "${e.message ?: e.javaClass.simpleName}"
            runCatching { toast("保存失败：${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** 最近一次写入错误信息（无错误返回 null），设置页展示用 */
    fun lastWriteError(): String? = lastWriteError

    /* ================= 流水 ================= */

    fun txList(ledgerId: String): List<Tx> = txCache.getOrPut(ledgerId) {
        parseTxs(readBundle(ledgerId)?.optJSONArray("txs")?.toString())
    }

    fun txOfDay(ledgerId: String, date: LocalDate): List<Tx> =
        txList(ledgerId).filter { it.date == date }

    fun addTx(tx: Tx) {
        val list = txList(tx.ledgerId).toMutableList().apply { add(tx) }
        saveTxs(tx.ledgerId, list)
    }

    fun updateTx(tx: Tx) {
        val list = txList(tx.ledgerId).map { if (it.id == tx.id) tx else it }
        saveTxs(tx.ledgerId, list)
    }

    fun deleteTx(id: String, ledgerId: String) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        val list = parseTxs(obj.optJSONArray("txs")?.toString()).filterNot { it.id == id }
        obj.put("txs", JSONArray().apply { list.forEach { put(txToJson(it)) } })
        writeBundle(l, obj)
        txCache.remove(ledgerId)
    }

    private fun saveTxs(ledgerId: String, list: List<Tx>) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        obj.put("txs", JSONArray().apply { list.forEach { put(txToJson(it)) } })
        writeBundle(l, obj)
        // 写后更新缓存（比失效后下次重读更快）
        txCache[ledgerId] = list
    }

    /* ================= 存款（账户级公用：账户文件夹内 my_saving.json） ================= */

    /** 账户公用存款文件：my_saving.json（账户文件夹内，空也是 [] 文件） */
    private fun depFile(accountId: String): String {
        val folder = folderOfAccount(accountId) ?: return ""
        return "$folder/$SAVING_JSON"
    }

    /** 某账户的公用存款列表 */
    fun accountDepList(accountId: String): List<Deposit> {
        val f = depFile(accountId)
        if (f.isEmpty()) return emptyList()
        val raw = io.readNamedRaw(f) ?: return emptyList()
        return runCatching { parseDeps(raw) }.getOrDefault(emptyList())
    }

    /** 账户公用存款总额（金钱类与非金钱类价值之和） */
    fun accountTotalDeposits(accountId: String): Cents =
        accountDepList(accountId).sumOf { it.value }

    private fun writeAccountDeps(accountId: String, list: List<Deposit>) {
        val f = depFile(accountId)
        if (f.isEmpty()) return
        val arr = JSONArray()
        list.forEach { arr.put(depToJson(it)) }
        safeIo { io.writeNamedRaw(f, arr.toString()) }
    }

    /** 新增一笔公用存款（ledgerId 记来源账本；直接新增可留空） */
    fun addAccountDep(accountId: String, d: Deposit) {
        writeAccountDeps(accountId, accountDepList(accountId).toMutableList().apply { add(d) })
    }

    fun updateAccountDep(accountId: String, d: Deposit) {
        writeAccountDeps(accountId, accountDepList(accountId).map { if (it.id == d.id) d else it })
    }

    fun deleteAccountDep(accountId: String, depId: String) {
        writeAccountDeps(accountId, accountDepList(accountId).filterNot { it.id == depId })
    }

    /* ================= 天气 / 每日预算（存数据包） ================= */

    fun weather(ledgerId: String, date: LocalDate): Weather? {
        val obj = readBundle(ledgerId) ?: return null
        val name = obj.optJSONObject("wx")?.optString(date.toString(), "") ?: ""
        return Weather.entries.firstOrNull { it.name == name }
    }

    fun setWeather(ledgerId: String, date: LocalDate, w: Weather) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        val wx = obj.optJSONObject("wx") ?: JSONObject().also { obj.put("wx", it) }
        wx.put(date.toString(), w.name)
        writeBundle(l, obj)
    }

    fun dailyBudget(ledgerId: String, date: LocalDate): Cents? {
        val obj = readBundle(ledgerId) ?: return null
        val bdg = obj.optJSONObject(KEY_BDG) ?: return null
        val k = date.toString()
        return if (bdg.has(k)) bdg.optLong(k, 0) else null
    }

    fun setDailyBudget(ledgerId: String, date: LocalDate, cents: Cents): Boolean {
        val l = ledger(ledgerId) ?: return false
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        val bdg = obj.optJSONObject(KEY_BDG) ?: JSONObject().also { obj.put(KEY_BDG, it) }
        val k = date.toString()
        if (cents <= 0) bdg.remove(k) else bdg.put(k, cents)
        if (bdg.length() == 0) obj.remove(KEY_BDG)
        writeBundle(l, obj)
        return true
    }

    /* ================= 归档（一键同步） ================= */

    fun isSynced(ledgerId: String, ym: String): Boolean =
        ledger(ledgerId)?.syncedMonths?.contains(ym) ?: false

    private fun writeSynced(ledgerId: String, ym: String, mark: Boolean) {
        val old = ledger(ledgerId) ?: return
        val set = old.syncedMonths.toMutableSet()
        if (mark) set.add(ym) else set.remove(ym)
        val updated = old.copy(syncedMonths = set)
        // syncedMonths 存于数据包头部 ledger 声明（文件本身就是账本元信息，无需索引文件）
        readBundle(ledgerId)?.let { obj ->
            runCatching {
                obj.getJSONObject("ledger").put("synced", JSONArray(set.toList()))
            }
            safeIo { writeBundle(updated, obj) }
        }
        // 更新缓存
        val cache = ledgerMetaCache[old.accountId]
        cache?.let { it[updated.file] = updated }
    }

    fun markSynced(ledgerId: String, ym: String) = writeSynced(ledgerId, ym, true)
    fun unmarkSynced(ledgerId: String, ym: String) = writeSynced(ledgerId, ym, false)

    /** 归档条目标题前缀（含账本名，便于识别来源） */
    private fun archiveName(ledgerName: String, ym: String): String {
        val y = runCatching { YearMonth.parse(ym) }.getOrNull()
        return "${y?.year ?: ym.take(4)}.${y?.monthValue ?: ym.takeLast(2)} 月收入已归档·$ledgerName"
    }

    /** 在账户公用存款中查找某账本某月的归档记录 */
    fun archivedDepFor(ledgerId: String, ym: String): Deposit? {
        val l = ledger(ledgerId) ?: return null
        val accId = l.accountId
        val prefix = archiveName(l.name, ym)
        return accountDepList(accId).firstOrNull { it.name == prefix || it.name.startsWith("${prefix.substringBefore('·')}") }
    }

    /** 一键同步：把某账本某月结余存入账户公用存款（标来源账本名） */
    fun archiveMonth(ledgerId: String, ym: String, value: Cents): Boolean {
        val l = ledger(ledgerId) ?: return false
        val accId = l.accountId
        val old = archivedDepFor(ledgerId, ym)
        val y = runCatching { YearMonth.parse(ym) }.getOrNull() ?: return false
        val name = archiveName(l.name, ym)
        val list = accountDepList(accId).filterNot { old != null && it.id == old.id } + Deposit(
            id = old?.id ?: "d${System.currentTimeMillis()}_${(1000..9999).random()}",
            ledgerId = ledgerId, date = old?.date ?: y.atEndOfMonth(),
            kind = DepositKind.MONEY, name = name, note = "", value = value,
        )
        writeAccountDeps(accId, list)
        return true
    }

    fun resetArchive(ledgerId: String, ym: String): Boolean {
        val l = ledger(ledgerId) ?: return false
        val accId = l.accountId
        val old = archivedDepFor(ledgerId, ym)
        if (old == null) { unmarkSynced(ledgerId, ym); return true }
        writeAccountDeps(accId, accountDepList(accId).filterNot { it.id == old.id })
        unmarkSynced(ledgerId, ym)
        return true
    }

    /* ================= 存储目录（设置） ================= */

    /** 当前存储位置描述（设置页展示用） */
    fun storageDirDescription(): String {
        val uri = cfg.getString(KEY_STORAGE_TREE, null) ?: return "应用内部存储（默认）"
        val name = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name
            ?.takeIf { it.isNotBlank() } ?: "所选目录"
        return "外部目录：$name"
    }

    /** 存储绝对路径：内部存储为文件目录；外部目录解析真实路径 */
    fun storagePath(): String {
        val uri = cfg.getString(KEY_STORAGE_TREE, null)
        return if (uri == null) {
            File(appContext.filesDir, "ledgers").absolutePath
        } else {
            treeUriToPath(uri) ?: runCatching {
                DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name?.let { "/storage/emulated/0/$it" }
            }.getOrNull() ?: uri
        }
    }

    private fun treeUriToPath(uri: String): String? = runCatching {
        val tree = Uri.parse(uri)
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val volume = docId.substringBefore(":", "")
        val rest = docId.substringAfter(":", "")
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        if (rest.isBlank()) base else "$base/$rest"
    }.getOrNull()

    /**
     * 切换存储目录：整棵账户树（my_account.json + 各账户文件夹）复制到目标后端，
     * 然后对目标目录执行一次 normalize（若目标仍是旧布局则就地整理为新规范）。
     * treeUri == null 表示恢复应用内部存储。
     */
    fun switchStorage(treeUri: Uri?): String {
        val newIo: LedgerIO = if (treeUri == null) {
            FileLedgerIO(File(appContext.filesDir, "ledgers").apply { mkdirs() })
        } else {
            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?: return "切换失败：目录不可用"
            SafLedgerIO(appContext, root)
        }
        return runCatching {
            // 整树拷贝（含 my_account.json、全部账户文件夹内文件）
            io.listAllRaw().forEach { rel ->
                val content = io.readNamedRaw(rel) ?: return@forEach
                newIo.writeNamedRaw(rel, content)
            }
            // 生效
            cfg.edit().putString(KEY_STORAGE_TREE, treeUri?.toString()).apply()
            io = newIo
            // 目标目录若是旧布局（外部目录里有 pixelbook_* / accounts.json / 旧备份 json 等），就地整理
            if (io.read(ACCOUNTS_JSON) == null) {
                val summary = normalize(io)
                if (summary.migrated > 0) {
                    runCatching { toast("已整理目标目录旧数据：${summary.migrated} 个账本 → ${summary.accounts} 个账户") }
                }
            }
            ledgerMetaCache.clear()
            txCache.clear()
            // 记录历史与上次结果
            val prev = cfg.getString(KEY_STORAGE_TREE, null)
            prev?.let {
                val set = cfg.getStringSet(KEY_STORAGE_HISTORY, emptySet()).orEmpty().toMutableSet()
                set.add("$it|${runCatching { treeUriToPath(it) }.getOrNull() ?: it}")
                cfg.edit().putStringSet(KEY_STORAGE_HISTORY, set).apply()
            }
            cfg.edit().putString(KEY_LAST_SWITCH, "ok:已迁移 ${ledgers().size} 个账本").apply()
            "ok"
        }.getOrElse { e ->
            android.util.Log.e("PixelStore", "switch failed", e)
            val msg = "切换失败：${e.message ?: e.javaClass.simpleName}"
            cfg.edit().putString(KEY_LAST_SWITCH, msg).apply()
            msg
        }
    }

    fun lastSwitchResult(): String? = cfg.getString(KEY_LAST_SWITCH, null)
    fun prevStorageDescription(): String? = cfg.getString(KEY_PREV_STORAGE_TREE, null)?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name }.getOrNull()
    }
    fun prevStoragePath(): String? = cfg.getString(KEY_PREV_STORAGE_TREE, null)?.let { treeUriToPath(it) }
    fun storageHistory(): List<Triple<String, String, String>> =
        cfg.getStringSet(KEY_STORAGE_HISTORY, emptySet()).orEmpty()
            .mapNotNull { s ->
                val name = s.substringAfter("|")
                val uri = s.substringBefore("|")
                Triple(uri, runCatching { DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name }.getOrNull() ?: "外部目录", name)
            }

    /* ================= 序列化 ================= */

    private fun txToJson(tx: Tx): JSONObject = JSONObject().apply {
        put("id", tx.id); put("ld", tx.ledgerId); put("date", tx.date.toString()); put("time", tx.time)
        put("dir", tx.dir.name); put("cat", tx.category)
        put("amt", tx.amount); put("name", tx.name); put("note", tx.note)
        if (tx.asset.isNotEmpty()) put("ast", tx.asset)
    }

    private fun depToJson(d: Deposit): JSONObject = JSONObject().apply {
        put("id", d.id); put("ld", d.ledgerId); put("date", d.date.toString()); put("kind", d.kind.name)
        put("name", d.name); put("note", d.note); put("value", d.value)
    }

    private fun parseLedgers(s: String): List<Ledger> {
        val out = mutableListOf<Ledger>()
        val arr = JSONArray(s)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val synced = mutableSetOf<String>()
            val sa = o.optJSONArray("synced") ?: continue
            for (j in 0 until sa.length()) synced.add(sa.getString(j))
            out.add(
                Ledger(
                    o.getString("id"), o.getString("name"),
                    o.optInt("cover", 0),
                    font = o.optString("font", "pixel"),
                    syncedMonths = synced,
                    file = o.optString("file", ""),
                    accountId = o.optString("acc", ""),
                )
            )
        }
        return out
    }

    private fun parseTxs(s: String?): List<Tx> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val out = mutableListOf<Tx>()
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Tx(
                        id = o.getString("id"), ledgerId = o.getString("ld"),
                        date = LocalDate.parse(o.getString("date")), time = o.optString("time", "00:00"),
                        dir = if (o.optString("dir", "OUT") == "IN") TxDir.IN else TxDir.OUT,
                        category = o.optString("cat", "其他"),
                        amount = o.optLong("amt", 0), name = o.optString("name", ""),
                        note = o.optString("note", ""),
                        asset = o.optString("ast", ""),
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun parseDeps(s: String?): List<Deposit> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val out = mutableListOf<Deposit>()
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Deposit(
                        id = o.getString("id"), ledgerId = o.getString("ld"),
                        date = LocalDate.parse(o.getString("date")),
                        kind = if (o.optString("kind", "MONEY") == "MONEY") DepositKind.MONEY else DepositKind.GOODS,
                        name = o.optString("name", ""), note = o.optString("note", ""),
                        value = o.optLong("value", 0),
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun newId(): String = "${System.currentTimeMillis()}_${(1000..9999).random()}"

    /** 文件名/文件夹名清洗：非法字符与空白替换为下划线（账户文件夹名即由此而来） */
    private fun sanitizeFileName(name: String): String {
        val clean = name.trim()
            .replace(Regex("""[\\/:*?"<>|\s]"""), "_")
            .take(30)
        return clean.ifEmpty { "未命名" }
    }
}

/* ================= 文件后端 ================= */

/** 相对路径 = root 下的相对文件路径（含 / 分隔的子目录） */
interface LedgerIO {
    fun read(key: String): String?                       // 顶层虚拟 key（相对根的文件名）
    fun write(key: String, value: String)
    fun remove(key: String)
    fun createDir(rel: String)
    fun renameDir(oldRel: String, newRel: String)
    fun deleteDir(rel: String)
    fun listDirs(): List<String>
    fun readNamedRaw(relPath: String): String?
    fun writeNamedRaw(relPath: String, content: String)
    fun removeRaw(relPath: String)
    fun renameRaw(oldRel: String, newRel: String)
    fun listAllRaw(): List<String>
    fun listDirRaw(relDir: String): List<String>          // 某目录下条目名（含文件/子目录；空串=根）
    fun dirSizeBytes(relDir: String): Long                // 目录内全部文件字节和（递归；空串=根）
}

/** 应用内部存储后端：filesDir/ledgers 下真实文件夹与文件 */
private class FileLedgerIO(private val root: File) : LedgerIO {

    private fun fileOf(rel: String): File {
        val f = File(root, rel)
        // 防目录穿越：强制约束在 root 下
        return if (f.canonicalPath.startsWith(root.canonicalPath)) f else File(root, "bad")
    }

    override fun read(key: String): String? = readNamedRaw(key)
    override fun write(key: String, value: String) = writeNamedRaw(key, value)
    override fun remove(key: String) = removeRaw(key)

    override fun createDir(rel: String) { fileOf(rel).mkdirs() }
    override fun renameDir(oldRel: String, newRel: String) {
        runCatching { fileOf(oldRel).renameTo(fileOf(newRel)) }
    }
    override fun deleteDir(rel: String) { fileOf(rel).deleteRecursively() }
    override fun listDirs(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    override fun readNamedRaw(relPath: String): String? =
        runCatching { fileOf(relPath).takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()

    override fun writeNamedRaw(relPath: String, content: String) {
        val f = fileOf(relPath)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    override fun removeRaw(relPath: String) { runCatching { fileOf(relPath).delete() } }
    override fun renameRaw(oldRel: String, newRel: String) {
        runCatching { fileOf(oldRel).renameTo(fileOf(newRel)) }
    }
    override fun listAllRaw(): List<String> =
        root.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).path.replace('\\', '/') }.toList()

    override fun listDirRaw(relDir: String): List<String> {
        val d = if (relDir.isEmpty()) root else fileOf(relDir)
        return d.listFiles()?.map { it.name }?.filterNotNull() ?: emptyList()
    }

    override fun dirSizeBytes(relDir: String): Long {
        val d = if (relDir.isEmpty()) root else fileOf(relDir)
        if (!d.isDirectory) return 0L
        return d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/** SAF 目录后端：相对路径按 / 逐段解析（自动建目录/递归删除） */
private class SafLedgerIO(private val context: Context, private val root: DocumentFile) : LedgerIO {

    override fun read(key: String): String? = readNamedRaw(key)
    override fun write(key: String, value: String) = writeNamedRaw(key, value)
    override fun remove(key: String) = removeRaw(key)

    private fun docAt(relPath: String): DocumentFile? {
        val parts = relPath.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = root
        for (p in parts) {
            cur = cur.findFile(p) ?: return null
            if (!cur.exists()) return null
        }
        return cur
    }

    override fun createDir(rel: String) {
        val parts = rel.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = root
        for (p in parts) {
            cur = cur.findFile(p) ?: cur.createDirectory(p) ?: return
        }
    }

    override fun renameDir(oldRel: String, newRel: String) {
        val src = docAt(oldRel)
        if (src != null && src.isDirectory) {
            val ok = runCatching { src.renameTo(newRel.split('/').last()) }.getOrDefault(false)
            // 部分 Provider 不支持 renameTo：退化重建
            if (!ok) {
                copyDirRecursive(src, newRel)
                deleteDir(oldRel)
            }
        }
    }

    private fun copyDirRecursive(src: DocumentFile, dstRel: String) {
        createDir(dstRel)
        val parts = dstRel.split('/').filter { it.isNotBlank() }
        var cur = root
        for (p in parts) cur = cur.findFile(p) ?: return
        src.listFiles().forEach { f ->
            val childRel = if (dstRel.isEmpty()) (f.name ?: "") else "$dstRel/${f.name}"
            if (f.isDirectory) {
                copyDirRecursive(f, childRel)
            } else {
                runCatching {
                    context.contentResolver.openInputStream(f.uri)?.use { ins ->
                        val content = ins.bufferedReader().use { it.readText() }
                        val nf = cur.createFile("application/json", f.name ?: "") ?: return@use
                        context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                            os.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
    }

    override fun deleteDir(rel: String) {
        docAt(rel)?.takeIf { it.isDirectory }?.let { d ->
            d.listFiles().forEach { it.delete() }
            d.delete()
        }
    }

    override fun listDirs(): List<String> =
        root.listFiles().filter { it.isDirectory }.mapNotNull { it.name }

    override fun readNamedRaw(relPath: String): String? = runCatching {
        val parts = relPath.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = root
        for ((i, p) in parts.withIndex()) {
            val isLast = i == parts.lastIndex
            val nxt = cur.findFile(p) ?: return null
            if (isLast) {
                if (!nxt.isFile) return null
                return context.contentResolver.openInputStream(nxt.uri)?.bufferedReader()?.use { it.readText() }
            }
            cur = nxt
        }
        null
    }.getOrNull()

    override fun writeNamedRaw(relPath: String, content: String) {
        val parts = relPath.split('/').filter { it.isNotBlank() }
        var cur: DocumentFile = root
        for ((i, p) in parts.withIndex()) {
            val isLast = i == parts.lastIndex
            val nxt = cur.findFile(p)
            if (isLast) {
                val target = if (nxt != null && nxt.exists()) nxt else cur.createFile("application/json", p)
                if (target == null) throw IllegalStateException("无法创建文件 $relPath")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入 $relPath")
                return
            }
            cur = nxt ?: cur.createDirectory(p) ?: throw IllegalStateException("无法创建目录 $p")
        }
    }

    override fun removeRaw(relPath: String) {
        runCatching { docAt(relPath)?.delete() }
    }

    override fun renameRaw(oldRel: String, newRel: String) {
        val src = docAt(oldRel)
        if (src != null && src.isFile) {
            val ok = runCatching { src.renameTo(newRel.split('/').last()) }.getOrDefault(false)
            if (!ok) {
                readNamedRaw(oldRel)?.let { content -> writeNamedRaw(newRel, content); removeRaw(oldRel) }
            }
        }
    }

    override fun listAllRaw(): List<String> {
        val out = mutableListOf<String>()
        fun walk(d: DocumentFile, prefix: String) {
            d.listFiles().forEach { f ->
                val rel = if (prefix.isEmpty()) f.name ?: "" else "$prefix/${f.name}"
                if (f.isDirectory) walk(f, rel) else if (rel.isNotBlank()) out.add(rel)
            }
        }
        walk(root, "")
        return out
    }

    override fun listDirRaw(relDir: String): List<String> {
        val d = if (relDir.isEmpty()) root else docAt(relDir)
        return d?.listFiles()?.mapNotNull { it.name } ?: emptyList()
    }

    override fun dirSizeBytes(relDir: String): Long {
        val d = if (relDir.isEmpty()) root else docAt(relDir) ?: return 0L
        var sum = 0L
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) walk(f) else runCatching {
                    val len = f.length()
                    if (len >= 0) sum += len
                }
            }
        }
        walk(d)
        return sum
    }
}
