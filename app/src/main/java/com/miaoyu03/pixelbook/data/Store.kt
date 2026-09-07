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
 * 存储结构（目录可选：应用内部 filesDir / SAF 目录）：
 *   <root>/
 *     accounts.json                    账户索引（全局一份）
 *     <账户文件夹>/                    账户名（自动跟随改名；账户名唯一）
 *       ledgers.json                   该账户账本索引
 *       cats.json                      该账户收支类别（in/out）
 *       <账本名>_<创建时间戳>.json      每账本一个数据包（txs/dps/wx/bdg/ledger 元信息）
 *       <账户名>_资产信息.json          该账户全部资产账户（银行/支付宝/微信…）
 *
 * 旧版本（无账户，数据散在根目录/SharedPreferences）首次启动自动迁移：
 * 建立「默认账户」文件夹并搬入全部旧账本。
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

    init {
        io = loadIo()
        // 首次启动：建立账户体系并迁移旧数据（幂等：有 accounts.json 即跳过）
        ensureAccounts()
        // 账户公用存款迁移：把各账本数据包里的旧存款汇入账户公用存款（幂等）
        migrateDepsToAccount()
    }

    companion object {
        private const val CFG_NAME = "pixelbook_cfg"
        private const val KEY_STORAGE_TREE = "storage_tree"
        private const val KEY_LAST_SWITCH = "last_switch_result"
        private const val KEY_PREV_STORAGE_TREE = "prev_storage_tree"
        private const val KEY_STORAGE_HISTORY = "storage_history"
        private const val KEY_CUR_ACCOUNT = "current_account"

        // 相对文件/文件夹名（<root> 之下）
        private const val ACCOUNTS_JSON = "accounts.json"
        private const val LEDGERS_JSON = "ledgers.json"
        private const val CATS_JSON = "cats.json"
        private const val ASSETS_SUB = "资产信息"
        private const val KEY_BDG = "bdg"

        // 旧版根目录文件名（迁移读取用）
        private const val LEGACY_LEDGERS = "ledgers"
        private const val LEGACY_CATS = "cats"
        private const val LEGACY_PREFIX = "pixelbook_"

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
            val root = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))
            if (root != null) SafLedgerIO(appContext, root) else FileLedgerIO(File(appContext.filesDir, "ledgers").apply { mkdirs() })
        }
    }

    /* ================= 账户体系与旧数据迁移 ================= */

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
                Account(it.getString("id"), it.getString("name"), it.optString("created", ""))
            }.getOrNull()
        }

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
    }

    /** 某账本所属账户 */
    fun ledgerAccount(ledgerId: String): Account? =
        ledger(ledgerId)?.accountId?.let { account(it) }

    /** 首次启动/账户体系缺失时：建「默认账户」并迁移旧数据 */
    private fun ensureAccounts() {
        if (readAccountsRaw().isNotEmpty()) return
        // 若目录里已经有账户文件夹结构（accounts.json 被外部清掉）则兜底恢复索引
        val folderNames = io.listDirs()
        val known = accounts().map { accountFolder(it.name) }
        val restorable = folderNames.firstOrNull { it !in known }
        runCatching {
            val defId = "a${System.currentTimeMillis()}"
            val defName = DEFAULT_ACCOUNT_NAME
            val folder = accountFolder(defName)
            if (restorable != null && restorable.isNotBlank()) {
                // 兜底：把遗留文件夹原样注册为「默认账户」
                val def = Account(defId, defName)
                registerAccountMeta(def, restorable)
                setCurrentAccountId(defId)
                return
            }
            io.createDir(folder)
            // 1) 账本索引：旧数据在内部 SharedPreferences 或 目录根文件
            val legacyRaw: String? = if (io is FileLedgerIO) {
                legacyPrefs.getString(LEGACY_LEDGERS, null)
            } else {
                // SAF 旧版根文件名为 pixelbook_ledgers.json（readNamedRaw 直接按名读，避免 read() 二次加前缀）
                io.readNamedRaw("${LEGACY_PREFIX}${LEGACY_LEDGERS}")
            }
            val legacyLedgers: List<Ledger> = legacyRaw?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) } ?: emptyList()

            val migrated = legacyLedgers.map { it.copy(accountId = defId, file = it.file.ifBlank { bundleFileName(it.name, it.id) }) }

            // 2) 账本数据包：逐本从旧位置搬入新文件夹
            for (l in migrated) {
                val legacyBody = if (io is FileLedgerIO) {
                    legacyPrefs.getString("ledger.${l.id}", null)
                } else {
                    io.readNamedRaw("${LEGACY_PREFIX}ledger.${l.id}")
                        ?: io.readNamedRaw(l.file.ifBlank { bundleFileName(l.name, l.id) })
                }
                if (legacyBody != null) {
                    safeIo { io.writeNamedRaw("${folder}/${l.file}", legacyBody) }
                }
            }
            // 3) 类别表随账户走（旧版只有一张全局表；SAF 旧文件名 pixelbook_cats.json）
            val catsRaw = if (io is FileLedgerIO) legacyPrefs.getString(LEGACY_CATS, null) else io.readNamedRaw("${LEGACY_PREFIX}${LEGACY_CATS}")
            safeIo { io.writeNamedRaw("$folder/$CATS_JSON", catsRaw ?: catsJson(IncomeCats.list, ExpenseCats.list)) }

            // 4) 索引写入新结构 + 账户注册 + 指向默认账户
            safeIo { io.writeNamedRaw("$folder/$LEDGERS_JSON", ledgersToJson(migrated)) }
            val def = Account(defId, defName)
            registerAccountMeta(def, folder)
            if (migrated.isNotEmpty()) {
                runCatching { toast("已从旧版导入 ${migrated.size} 个账本到「${defName}」") }
            }

            // 5) 清理旧位置（内部：SharedPreferences；目录：旧根文件），保留备份不删也可，此处删除避免二次误读
            if (io is FileLedgerIO) {
                runCatching { legacyPrefs.edit().remove(LEGACY_LEDGERS).remove(LEGACY_CATS).commit() }
                migrated.forEach { l -> runCatching { legacyPrefs.edit().remove("ledger.${l.id}").commit() } }
            } else {
                migrated.forEach { l ->
                    runCatching { io.removeRaw("${LEGACY_PREFIX}ledger.${l.id}") }
                    runCatching { io.removeRaw(l.file.ifBlank { bundleFileName(l.name, l.id) }) }
                }
                runCatching { io.removeRaw(LEGACY_PREFIX + LEGACY_LEDGERS) }
                runCatching { io.removeRaw(LEGACY_PREFIX + LEGACY_CATS) }
            }
            setCurrentAccountId(defId)
        }.onFailure { e ->
            android.util.Log.e("PixelStore", "ensureAccounts failed", e)
        }
    }

    private fun registerAccountMeta(a: Account, folder: String) {
        val list = readAccountsRaw().toMutableList()
        list.add(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("created", a.createdAt); put("folder", folder)
        })
        safeIo { io.write(ACCOUNTS_JSON, JSONArray(list).toString()) }
    }

    /**
     * 账户公用存款迁移（幂等）：逐账户把各账本数据包里仍残留的旧存款汇入
     * 「<账户名>_存款明细.json」公用列表（备注标注来源账本），随后清空账本包内的 dps。
     * 因每次启动都会检查账本包是否还有 dps，天然幂等且不会漏。
     */
    private fun migrateDepsToAccount() {
        android.util.Log.d("PixelStore", "migrateDeps start accounts=${accounts().size}")
        for (acc in accounts()) {
            val depF = depFile(acc.id)
            if (depF.isEmpty()) continue
            android.util.Log.d("PixelStore", "migrate acc=${acc.name} depF=$depF exists=${io.readNamedRaw(depF) != null}")
            var changed = false
            val merged = accountDepList(acc.id).toMutableList()
            for (l in ledgersOf(acc.id)) {
                val bundle = readBundle(l.id) ?: continue
                val oldDeps = parseDeps(bundle.optJSONArray("dps")?.toString())
                android.util.Log.d("PixelStore", "migrate ledger=${l.name} oldDeps=${oldDeps.size}")
                if (oldDeps.isEmpty()) continue
                oldDeps.forEach { d ->
                    val srcNote = if (d.note.isNotEmpty()) d.note else ""
                    merged.add(
                        d.copy(
                            ledgerId = l.id,
                            note = if (srcNote.contains("来自账本")) srcNote else if (srcNote.isEmpty()) "来自账本「${l.name}」" else "$srcNote · 来自账本「${l.name}」",
                        )
                    )
                }
                runCatching { bundle.remove("dps") }
                runCatching { writeBundle(l, bundle) }
                changed = true
            }
            if (changed) {
                android.util.Log.d("PixelStore", "migrate writing ${merged.size} deps")
                writeAccountDeps(acc.id, merged)
            }
        }
        android.util.Log.d("PixelStore", "migrateDeps done")
    }

    /* ================= 账户 CRUD（含文件夹自动同步） ================= */

    /** 新建账户：同名不允许；返回 null 表示失败 */
    fun addAccount(name: String): Account? {
        val nm = name.trim()
        if (nm.isEmpty() || accounts().any { it.name == nm }) return null
        val id = "a${newId()}"
        val folder = accountFolder(nm)
        io.createDir(folder)
        safeIo {
            io.writeNamedRaw("$folder/$LEDGERS_JSON", "[]")
            io.writeNamedRaw("$folder/$CATS_JSON", catsJson(IncomeCats.list, ExpenseCats.list))
        }
        registerAccountMeta(Account(id, nm), folder)
        return Account(id, nm)
    }

    /** 改名：同步重命名账户文件夹；同名/空名返回 false */
    fun renameAccount(id: String, newName: String): Boolean {
        val old = account(id) ?: return false
        val nm = newName.trim()
        if (nm.isEmpty() || accounts().any { it.name == nm }) return false
        val oldFolder = accountFolder(old.name)
        val newFolder = accountFolder(nm)
        io.renameDir(oldFolder, newFolder)
        val list = readAccountsRaw().toMutableList()
        val arr = JSONArray()
        list.forEach {
            if (it.optString("id") == id) {
                arr.put(JSONObject().apply {
                    put("id", id); put("name", nm); put("created", it.optString("created", "")); put("folder", newFolder)
                })
            } else arr.put(it)
        }
        safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
        return true
    }

    /** 删除账户（连同文件夹与全部账本数据）；id 不存在返回 false */
    fun deleteAccount(id: String): Boolean {
        val old = account(id) ?: return false
        io.deleteDir(accountFolder(old.name))
        val arr = JSONArray()
        readAccountsRaw().forEach { if (it.optString("id") != id) arr.put(it) }
        safeIo { io.write(ACCOUNTS_JSON, arr.toString()) }
        if (cfg.getString(KEY_CUR_ACCOUNT, null) == id) cfg.edit().remove(KEY_CUR_ACCOUNT).apply()
        return true
    }

    /* ================= 账本（按账户文件夹存放） ================= */

    /** 账户文件夹相对路径（由账户 meta 的 folder 或名称计算） */
    private fun folderOfAccount(accountId: String): String? {
        val a = account(accountId) ?: return null
        return readAccountsRaw().firstOrNull { it.optString("id") == accountId }
            ?.optString("folder", "")
            ?.takeIf { it.isNotBlank() }
            ?: accountFolder(a.name)
    }

    /** 某账户下的账本列表 */
    fun ledgersOf(accountId: String): List<Ledger> {
        val folder = folderOfAccount(accountId) ?: return emptyList()
        val raw = io.readNamedRaw("$folder/$LEDGERS_JSON") ?: return emptyList()
        return runCatching { parseLedgers(raw).map { it.copy(accountId = accountId) } }.getOrDefault(emptyList())
    }

    fun ledgers(): List<Ledger> = accounts().flatMap { ledgersOf(it.id) }

    fun ledger(id: String): Ledger? = ledgers().find { it.id == id }

    /** 新建账本（accountId 账户下，最多 60 本）；超限返回 null */
    fun addLedger(accountId: String, name: String, coverIdx: Int): Ledger? {
        val folder = folderOfAccount(accountId) ?: return null
        if (ledgersOf(accountId).size >= MAX_LEDGER_PER_ACCOUNT) return null
        val id = newId()
        val l = Ledger(id = id, name = name.trim(), coverColor = coverIdx, file = bundleFileName(name.trim(), id), accountId = accountId)
        val list = ledgersOf(accountId).toMutableList().apply { add(l) }
        safeIo { io.writeNamedRaw("$folder/$LEDGERS_JSON", ledgersToJson(list)) }
        // 初始化空数据包（含账本信息）
        safeBundleSave(l, emptyBundleLike(l))
        return l
    }

    /** 编辑账本（改名/字体/封面色）；改名自动同步账本单文件名 */
    fun updateLedger(id: String, name: String, font: String, coverColor: Int) {
        val old = ledger(id) ?: return
        val accId = old.accountId
        val folder = folderOfAccount(accId) ?: return
        val newName = name.trim()
        val list = ledgersOf(accId).map {
            if (it.id == id) it.copy(name = newName, font = font, coverColor = coverColor, file = bundleFileName(newName, it.id)) else it
        }
        safeIo { io.writeNamedRaw("$folder/$LEDGERS_JSON", ledgersToJson(list)) }
        if (newName != old.name) {
            val oldFile = old.file.ifBlank { bundleFileName(old.name, old.id) }
            val newFile = bundleFileName(newName, id)
            // 数据包内 name/字体/封面色同步；文件改名
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
        }
    }

    fun deleteLedger(id: String) {
        val old = ledger(id) ?: return
        val accId = old.accountId
        val folder = folderOfAccount(accId) ?: return
        val list = ledgersOf(accId).filterNot { it.id == id }
        safeIo { io.writeNamedRaw("$folder/$LEDGERS_JSON", ledgersToJson(list)) }
        val f = old.file.ifBlank { bundleFileName(old.name, old.id) }
        safeIo { io.removeRaw("$folder/$f") }
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

    /* ================= 资产账户（按账户文件夹存 <账户名>_资产信息.json） ================= */

    private fun assetsFile(accountId: String): String {
        val a = account(accountId) ?: return ""
        return "${accountFolder(a.name)}/${sanitizeFileName(a.name)}_资产信息.json"
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
        return "$folder/${l.file.ifBlank { bundleFileName(l.name, l.id) }}"
    }

    private fun bundleFileName(name: String, id: String): String =
        "${sanitizeFileName(name)}_${createStampMs(id)}.json"

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

    fun txList(ledgerId: String): List<Tx> =
        parseTxs(readBundle(ledgerId)?.optJSONArray("txs")?.toString())

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
    }

    private fun saveTxs(ledgerId: String, list: List<Tx>) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        obj.put("txs", JSONArray().apply { list.forEach { put(txToJson(it)) } })
        writeBundle(l, obj)
    }

    /* ================= 存款（账户级公用：一本账，不分账本；旧账本存款已自动汇入） ================= */

    /** 账户公用存款文件名（置于账户文件夹内）：<账户名>_存款明细.json */
    private fun depFile(accountId: String): String {
        val a = account(accountId) ?: return ""
        return "${accountFolder(a.name)}/${sanitizeFileName(a.name)}_存款明细.json"
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
        val accId = old.accountId
        val folder = folderOfAccount(accId) ?: return
        val list = ledgersOf(accId).map {
            if (it.id == ledgerId) {
                val set = it.syncedMonths.toMutableSet()
                if (mark) set.add(ym) else set.remove(ym)
                it.copy(syncedMonths = set)
            } else it
        }
        safeIo { io.writeNamedRaw("$folder/$LEDGERS_JSON", ledgersToJson(list)) }
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
     * 切换存储目录：整棵账户树（accounts.json + 各账户文件夹）复制到目标后端。
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
            // 整树拷贝（含 accounts.json、全部账户文件夹内文件）
            io.listAllRaw().forEach { rel ->
                val content = io.readNamedRaw(rel) ?: return@forEach
                newIo.writeNamedRaw(rel, content)
            }
            // 生效
            cfg.edit().putString(KEY_STORAGE_TREE, treeUri?.toString()).apply()
            io = newIo
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

    private fun ledgersToJson(list: List<Ledger>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("cover", it.coverColor)
                put("font", it.font)
                put("synced", JSONArray(it.syncedMonths.toList()))
                put("file", it.file)
                if (it.accountId.isNotEmpty()) put("acc", it.accountId)
            })
        }
        return arr.toString()
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
    fun read(key: String): String?                       // 顶层虚拟 key（accounts.json 之类经映射）
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
}

/** 应用内部存储后端：filesDir/ledgers 下真实文件夹与文件 */
private class FileLedgerIO(private val root: File) : LedgerIO {

    private fun fileOf(rel: String): File {
        val f = File(root, rel)
        // 防目录穿越：强制约束在 root 下
        return if (f.canonicalPath.startsWith(root.canonicalPath)) f else File(root, "bad")
    }

    override fun read(key: String): String? = readNamedRaw(legacyRootKey(key))
    override fun write(key: String, value: String) = writeNamedRaw(legacyRootKey(key), value)
    override fun remove(key: String) = removeRaw(legacyRootKey(key))

    /** 顶层 key → 根级 pixelbook_<key>.json（保留旧命名习惯便于目录浏览） */
    private fun legacyRootKey(key: String): String = LEGACY_PREFIX_F + key

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

    private companion object { const val LEGACY_PREFIX_F = "pixelbook_" }
}

/** SAF 目录后端：相对路径按 / 逐段解析（自动建目录/递归删除） */
private class SafLedgerIO(private val context: Context, private val root: DocumentFile) : LedgerIO {

    private fun legacyRootKey(key: String): String = "pixelbook_$key"

    override fun read(key: String): String? = readNamedRaw(legacyRootKey(key))
    override fun write(key: String, value: String) = writeNamedRaw(legacyRootKey(key), value)
    override fun remove(key: String) = removeRaw(legacyRootKey(key))

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
}
