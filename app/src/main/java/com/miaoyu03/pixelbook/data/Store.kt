package com.miaoyu03.pixelbook.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 本地数据层：Key-Value JSON 存储。
 * 每个账本独立 key：txs.<id> / dps.<id> / wx.<id>
 * 默认存应用内部 SharedPreferences；可在设置中切换到外部 SAF 目录（每个 key 一个 JSON 文件）。
 */
/** 数据 key 前缀与默认文件名前缀（同文件后端类共用） */
private const val KEY_LEDGERS = "ledgers"
private const val FILE_PREFIX = "pixelbook_"

class Store(context: Context) {

    private val appContext = context.applicationContext
    /** 配置（存储目录选择、演示数据标记）与应用数据分离 */
    private val cfg = appContext.getSharedPreferences(CFG_NAME, Context.MODE_PRIVATE)
    /** 当前数据存储后端 */
    private var io: LedgerIO
    /** 最近一次写入错误（设置页展示，toast 错过也能查） */
    @Volatile private var lastWriteError: String? = null

    init {
        io = loadIo()
        android.util.Log.d("PdfExp", "init: io=${io.javaClass.simpleName} ledgers=${ledgers().size}")
        // 启动时自动恢复：外部目录里有流水/存款文件但缺账本定义（如 ledgers 未拷贝）
        // 时自动补建账本条目（授权在持久化后枚举才能可靠工作，重启后自愈）
        autoRestore()
        android.util.Log.d("PdfExp", "after autoRestore: ledgers=${ledgers().size}")
    }

    /** 目标为外部目录且缺账本定义时：目录里存在流水/存款数据 → 自动补建账本 */
    private fun autoRestore() {
        val cur = io
        if (cur is SafLedgerIO) {
            try {
                if (cur.read(KEY_LEDGERS) == null) rebuildLedgers(cur)
            } catch (_: Exception) {
            }
        }
    }

    /** 解析目标存储的账本列表：备份文件（账本名_时间戳.json）逐个恢复 + 标准数据（ledgers/rebuild）合并 */
    private fun resolveTargetLedgers(target: LedgerIO): List<Ledger> {
        // 1) 备份文件恢复：每份备份 = 一本账本（内容自带账本声明，多文件多账本一一对应）
        val fromBackups = mutableListOf<Ledger>()
        if (target is SafLedgerIO) {
            target.listBackups().forEach { (_, text) ->
                runCatching {
                    val o = JSONObject(text)
                    if (o.optString("app") != "pixelbook" || o.optString("type") != "backup") return@forEach
                    val lo = o.getJSONObject("ledger")
                    val synced = mutableSetOf<String>()
                    runCatching {
                        val sa = lo.optJSONArray("synced") ?: JSONArray()
                        for (j in 0 until sa.length()) synced.add(sa.getString(j))
                    }
                    val l = Ledger(
                        id = lo.getString("id"), name = lo.getString("name"),
                        coverColor = lo.optInt("cover", 0), font = lo.optString("font", "pixel"),
                        syncedMonths = synced,
                    )
                    // 把备份里的流水/存款/天气固化回标准 key 文件，该账本成为目录正式数据
                    val d = o.optJSONObject("data")
                    d?.optString("txs")?.takeIf { it.isNotBlank() }?.let { target.write(txKey(l.id), it) }
                    d?.optString("dps")?.takeIf { it.isNotBlank() }?.let { target.write(dpKey(l.id), it) }
                    d?.optString("wx")?.takeIf { it.isNotBlank() }?.let { target.write(wxKey(l.id), it) }
                    fromBackups.add(l)
                }
            }
            if (fromBackups.isNotEmpty()) android.util.Log.d("PdfExp", "backups restored: ${fromBackups.size}")
        }
        // 2) 标准数据：ledgers.json 优先；缺失但有 txs/dps 文件 → 按内容 ld 自动补建
        var list = target.read(KEY_LEDGERS)
            ?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) }
        if (list == null) {
            rebuildLedgers(target)
            list = target.read(KEY_LEDGERS)
                ?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) } ?: emptyList()
        }
        // 3) 合并：同 id 时以备份里的真实名称/封面/字体为准（rebuild 的"恢复账本xx"只是占位名）；
        //    备份里有而账本表没有的 → 追加进账本表；发生纠正/补充 → 回写账本表文件
        val byId = fromBackups.associateBy { it.id }
        val corrected = list.map { byId[it.id] ?: it }
        val extra = fromBackups.filterNot { fb -> corrected.any { it.id == fb.id } }
        val final = corrected + extra
        if (final != list) {
            target.write(KEY_LEDGERS, ledgersToJson(final))
            list = final
        }
        return list
    }

    /** 从账本 id 前缀（epoch 毫秒）解析创建时间戳 yyyyMMdd_HHmmss；解析失败用当前时间兜底 */
    private fun createStamp(id: String): String {
        val ms = id.substringBefore("_").toLongOrNull()
        if (ms != null) {
            val fromId = runCatching {
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
                )
            }.getOrNull()
            if (fromId != null) return fromId
        }
        return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
    }

    /** 备份/文件名清洗：非法字符与空白替换为下划线，截断防超长 */
    private fun sanitizeFileName(name: String): String {
        val clean = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
            .take(30)
        return clean.ifEmpty { "账本" }
    }

    /** 从 txs/dps 文件的账本 id 自动补建 ledgers 条目（id 以文件内容里的 ld 为准，文件名带变体也不影响） */
    private fun rebuildLedgers(target: LedgerIO) {
        val ids = mutableSetOf<String>()
        target.keys().forEach { k ->
            if (k.startsWith("txs.") || k.startsWith("dps.")) {
                val text = target.read(k) ?: return@forEach
                runCatching {
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val ld = arr.getJSONObject(i).optString("ld", "")
                        if (ld.isNotEmpty()) ids.add(ld)
                    }
                }
            }
        }
        if (ids.isNotEmpty()) {
            val arr = JSONArray()
            ids.forEach { id ->
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("name", "恢复账本${id.takeLast(4)}")
                    put("cover", 0); put("font", "pixel"); put("synced", JSONArray())
                })
            }
            target.write(KEY_LEDGERS, arr.toString())
        }
    }

    companion object {
        private const val CFG_NAME = "pixelbook_cfg"
        private const val KEY_LEDGERS = "ledgers"
        private const val KEY_CATS_IN = "cats.in"      // 收入类别表
        private const val KEY_CATS_OUT = "cats.out"    // 花销类别表
        private const val KEY_SEEDED = "demo_seeded_v1"
        private const val KEY_STORAGE_TREE = "storage_tree"
        private const val KEY_LAST_SWITCH = "last_switch_result"
        private const val KEY_PREV_STORAGE_TREE = "prev_storage_tree"
        private const val KEY_STORAGE_HISTORY = "storage_history"
        const val MAX_LEDGER_NAME = 30   // 账本名称字符上限
    }

    /** 像素风格 toast（组件内用 store.toast 代替 Context） */
    fun toast(msg: String) {
        android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    /* ================= 存储目录（设置） ================= */

    /** 当前存储位置描述（设置页展示用） */
    fun storageDirDescription(): String {
        val uri = cfg.getString(KEY_STORAGE_TREE, null) ?: return "应用内部存储（默认）"
        val name = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name
            ?.takeIf { it.isNotBlank() } ?: "所选目录"
        return "外部目录：$name"
    }

    /** 账本存储信息：位置描述 + 数据占用字节数（流水/存款/天气 JSON） */
    fun ledgerStorageInfo(id: String): Pair<String, Long> {
        val bytes = listOf(txKey(id), dpKey(id), wxKey(id)).sumOf { k ->
            io.read(k)?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
        }
        return storagePath() to bytes
    }

    /** 存储绝对路径：内部存储为应用数据目录；外部目录解析为 /storage/... 真实路径 */
    fun storagePath(): String {
        val uri = cfg.getString(KEY_STORAGE_TREE, null) ?: return appContext.dataDir.absolutePath
        return treeUriToPath(uri) ?: runCatching {
            DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name?.let { "/storage/emulated/0/$it" }
        }.getOrNull() ?: uri
    }

    /** SAF 树 URI → 绝对路径（如 primary:Documents → /storage/emulated/0/Documents） */
    private fun treeUriToPath(uri: String): String? = runCatching {
        val tree = Uri.parse(uri)
        val docId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val volume = docId.substringBefore(":", "")
        val rest = docId.substringAfter(":", "")
        val base = if (volume == "primary") {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        "$base/$rest".removeSuffix("/")
    }.getOrNull()

    /**
     * 切换数据存储（null = 恢复应用内部存储）。
     * 目标目录为空 → 迁移当前全部数据过去；目标目录已有数据（换机/重装后指向
     * 旧目录）→ 直接采用目标数据（恢复），不回写覆盖。
     * 失败返回 false，原存储不受影响。
     */
    /**
     * 切换数据存储（null = 恢复应用内部存储）。
     * 返回结果描述：成功时 = "ok:载入N个账本 / ok:已迁移 / ok:恢复N个账本"；
     * 失败时 = 错误说明（"存储目录不可用"、"写入失败"等）。
     */
    fun switchStorage(treeUri: Uri?): String {
        val newIo: LedgerIO = if (treeUri == null) {
            PrefsLedgerIO(appContext)
        } else {
            // 只要求目录存在；部分 ROM/文件管理器 provider 的 canWrite() 会误报 false，
            // 若真写不了，后面的写入步骤会给出具体失败原因
            val root = runCatching {
                DocumentFile.fromTreeUri(appContext, treeUri)?.takeIf { it.exists() }
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "switch target check failed: ${e.message}")
            }.getOrNull() ?: return "无法访问所选目录"
            android.util.Log.d(
                "PdfExp",
                "switch target=$treeUri canWrite=${runCatching { root.canWrite() }.getOrDefault(false)}"
            )
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                android.util.Log.w("PdfExp", "takePersistable failed", e)
            }
            SafLedgerIO(appContext, root)
        }
        try {
            android.util.Log.d("PdfExp", "targetHasLedgers=${newIo.read(KEY_LEDGERS) != null} keys=${newIo.keys().size}")

            // ===== 1) 备份：当前每本账本 → 目标 {账本名}_{时间戳}.json（仅外部目录） =====
            val curLedgers = io.read(KEY_LEDGERS)
                ?.let { runCatching { parseLedgers(it) }.getOrDefault(emptyList()) } ?: emptyList()
            var backedUp = 0
            if (newIo is SafLedgerIO && curLedgers.isNotEmpty()) {
                curLedgers.forEach { l ->
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                    val created = createStamp(l.id)
                    val bundle = JSONObject().apply {
                        put("app", "pixelbook"); put("type", "backup")
                        put("time", stamp); put("created", created)
                        put("ledger", JSONObject().apply {
                            put("id", l.id); put("name", l.name); put("cover", l.coverColor); put("font", l.font)
                            put("synced", JSONArray(l.syncedMonths.toList()))
                        })
                        put("data", JSONObject().apply {
                            io.read(txKey(l.id))?.let { put("txs", it) }
                            io.read(dpKey(l.id))?.let { put("dps", it) }
                            io.read(wxKey(l.id))?.let { put("wx", it) }
                        })
                    }
                    // 备份文件 = 账本名 + 账本创建时间戳（同一本账随时备份同名，覆盖更新不产生副本）
                    newIo.writeBackup("${sanitizeFileName(l.name)}_$created.json", bundle.toString())
                    backedUp++
                }
                android.util.Log.d("PdfExp", "backup $backedUp ledgers -> target")
            }
            val backupNote = if (backedUp > 0) "已备份 $backedUp 本账本；" else ""

            // ===== 2) 载入/合并：目标账本 + 当前账本（同 id 去重，目标优先） =====
            val targetName = if (newIo is SafLedgerIO) "新目录" else "内部存储"
            val targetLedgers = resolveTargetLedgers(newIo)
            val moving = curLedgers.filterNot { l -> targetLedgers.any { it.id == l.id } }
            val merged = targetLedgers + moving
            var result: String
            if (moving.isNotEmpty()) {
                // 当前账本（目标里没有的）→ 追加进目标账本表 + 数据文件写入目标
                newIo.write(KEY_LEDGERS, ledgersToJson(merged))
                moving.forEach { l ->
                    io.read(txKey(l.id))?.let { newIo.write(txKey(l.id), it) }
                    io.read(dpKey(l.id))?.let { newIo.write(dpKey(l.id), it) }
                    io.read(wxKey(l.id))?.let { newIo.write(wxKey(l.id), it) }
                }
                android.util.Log.d("PdfExp", "merged: target ${targetLedgers.size} + moving ${moving.size} = ${merged.size}")
                result = if (targetLedgers.isEmpty())
                    "ok:${backupNote}数据已迁移到$targetName（共 ${merged.size} 本账本）"
                else
                    "ok:${backupNote}目标与当前合并，共 ${merged.size} 本账本"
            } else {
                result = if (targetLedgers.isEmpty())
                    "ok:已切换到$targetName（无数据）"
                else
                    "ok:${backupNote}已载入${targetName}的 ${targetLedgers.size} 本账本"
            }
            // 记录这次切换前的旧外部存储目录（历史目录列表 + 上次存储），旧为内部存储则无
            val prevUri = runCatching { (io as? SafLedgerIO)?.uri?.toString() }.getOrNull()
            io = newIo
            val editor = cfg.edit()
            if (treeUri == null) editor.remove(KEY_STORAGE_TREE)
            else editor.putString(KEY_STORAGE_TREE, treeUri.toString())
            editor.putString(KEY_LAST_SWITCH, result)
            if (prevUri != null) editor.putString(KEY_PREV_STORAGE_TREE, prevUri)
            else editor.remove(KEY_PREV_STORAGE_TREE)
            // 历史目录（去重）
            if (prevUri != null) {
                val hist = cfg.getStringSet(KEY_STORAGE_HISTORY, emptySet()).orEmpty().toMutableSet()
                hist.add(prevUri)
                editor.putStringSet(KEY_STORAGE_HISTORY, hist)
            }
            editor.apply()
            return result
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "switch failed", e)
            val msg = "切换失败：${e.message ?: e.javaClass.simpleName}"
            cfg.edit().putString(KEY_LAST_SWITCH, msg).apply()
            return msg
        }
    }

    /** 上次切换结果（设置页展示，失败时便于直接回报给开发） */
    fun lastSwitchResult(): String? = cfg.getString(KEY_LAST_SWITCH, null)

    /** 上次使用的外部存储目录名（设置页展示，供用户自行去文件管理器删除） */
    fun prevStorageDescription(): String? = cfg.getString(KEY_PREV_STORAGE_TREE, null)?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name }.getOrNull() ?: uri
    }

    /** 上次使用的外部存储绝对路径（如 /storage/emulated/0/xxx） */
    fun prevStoragePath(): String? = cfg.getString(KEY_PREV_STORAGE_TREE, null)?.let {
        treeUriToPath(it) ?: it
    }

    /** 历史使用过的外部目录列表：(uri, 目录名, 绝对路径)，供用户自行前往查看/删除数据 */
    fun storageHistory(): List<Triple<String, String, String>> =
        cfg.getStringSet(KEY_STORAGE_HISTORY, emptySet()).orEmpty()
            .mapNotNull { uri ->
                val name = runCatching { DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: uri
                uri to (name to (treeUriToPath(uri) ?: uri))
            }
            .map { Triple(it.first, it.second.first, it.second.second) }
            .sortedBy { it.second }

    /** 存储自检：向当前存储写一个探针文件并读回再删除，验证可写可读 */
    fun storageSelfTest(): String {
        val cur = io
        return try {
            cur.write("selftest", "pixelbook ok")
            val back = cur.read("selftest")
            cur.remove("selftest")
            if (back == "pixelbook ok") "自检通过：目录可读可写"
            else "写入成功但读回内容异常（$back）"
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "selftest failed", e)
            "自检失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun loadIo(): LedgerIO {
        val uri = cfg.getString(KEY_STORAGE_TREE, null)
        if (uri != null) {
            val granted = try {
                appContext.contentResolver.persistedUriPermissions.any { it.uri == Uri.parse(uri) }
            } catch (_: Exception) { false }
            android.util.Log.d("PdfExp", "loadIo tree=$uri granted=$granted")
            val root = runCatching {
                DocumentFile.fromTreeUri(appContext, Uri.parse(uri))
                    ?.takeIf { it.exists() && it.canWrite() }
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "loadIo tree unusable: ${e.message}", e)
            }.getOrNull()
            if (root != null) {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        Uri.parse(uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {}
                return SafLedgerIO(appContext, root)
            }
        }
        return PrefsLedgerIO(appContext)
    }

    /* ================= 账本 ================= */

    fun ledgers(): List<Ledger> {
        val arr = io.read(KEY_LEDGERS) ?: return emptyList()
        return parseLedgers(arr)
    }

    fun ledger(id: String): Ledger? = ledgers().find { it.id == id }

    fun addLedger(name: String, coverIdx: Int): Ledger {
        val l = Ledger(id = newId(), name = name.trim(), coverColor = coverIdx)
        val list = ledgers().toMutableList().apply { add(l) }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        return l
    }

    fun renameLedger(id: String, name: String) {
        val list = ledgers().map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
    }

    /** 编辑账本：名称 / 字体 / 封面颜色 */
    fun updateLedger(id: String, name: String, font: String, coverColor: Int) {
        val list = ledgers().map {
            if (it.id == id) it.copy(name = name.trim(), font = font, coverColor = coverColor) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
    }

    fun deleteLedger(id: String) {
        val list = ledgers().filterNot { it.id == id }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        runCatching { io.remove(txKey(id)) }; runCatching { io.remove(dpKey(id)) }; runCatching { io.remove(wxKey(id)) }
    }

    /* ================= 类别维护（收入/花销：全局各一张表，可增删改） ================= */

    /** 收入类别列表（默认：工资/理财/红包/其他） */
    fun incomeCats(): List<String> = readCats(KEY_CATS_IN, IncomeCats.list)

    /** 花销类别列表（默认：餐饮/交通/购物/娱乐/居住/医疗/其他） */
    fun expenseCats(): List<String> = readCats(KEY_CATS_OUT, ExpenseCats.list)

    /** 新增类别；重名/空名返回 false */
    fun addIncomeCat(name: String): Boolean = addCat(KEY_CATS_IN, IncomeCats.list, name)
    fun addExpenseCat(name: String): Boolean = addCat(KEY_CATS_OUT, ExpenseCats.list, name)

    /** 编辑类别：全量同步所有被引用记录；「其他」与重名/空名不可编辑 */
    fun renameIncomeCat(old: String, new: String): Boolean = renameCat(KEY_CATS_IN, IncomeCats.list, TxDir.IN, old, new)
    fun renameExpenseCat(old: String, new: String): Boolean = renameCat(KEY_CATS_OUT, ExpenseCats.list, TxDir.OUT, old, new)

    /** 删除类别：被引用记录全量回退为「其他」；「其他」不可删除 */
    fun deleteIncomeCat(name: String): Boolean = deleteCat(KEY_CATS_IN, IncomeCats.list, TxDir.IN, name)
    fun deleteExpenseCat(name: String): Boolean = deleteCat(KEY_CATS_OUT, ExpenseCats.list, TxDir.OUT, name)

    private fun readCats(key: String, defaults: List<String>): List<String> {
        val s = io.read(key) ?: return defaults
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(defaults)
    }

    private fun writeCats(key: String, list: List<String>) {
        safeWrite(key, JSONArray(list).toString())
    }

    private fun addCat(key: String, defaults: List<String>, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val cur = readCats(key, defaults)   // key 不存在时以默认类表为基底，避免覆盖
        if (n in cur) return false
        writeCats(key, cur + n)
        return true
    }

    private fun renameCat(key: String, defaults: List<String>, dir: TxDir, old: String, new: String): Boolean {
        val n = new.trim()
        if (old == CATEGORY_OTHERS) return false          // 「其他」固定，不可改
        val cats = readCats(key, defaults)
        if (old !in cats) return false
        if (n.isEmpty() || n == old || n in cats) return false   // 空名 / 未变 / 重名
        writeCats(key, cats.map { if (it == old) n else it })
        applyCatRename(dir, old, n)
        return true
    }

    private fun deleteCat(key: String, defaults: List<String>, dir: TxDir, name: String): Boolean {
        if (name == CATEGORY_OTHERS) return false          // 「其他」固定，不可删
        val cats = readCats(key, defaults)
        if (name !in cats) return false
        writeCats(key, cats.filterNot { it == name })
        applyCatRename(dir, name, CATEGORY_OTHERS)
        return true
    }

    /** 全量同步：把 dir 方向所有账本中 old 类别的记录改为 new */
    private fun applyCatRename(dir: TxDir, old: String, new: String) {
        ledgers().forEach { l ->
            val list = txList(l.id)
            if (list.any { it.dir == dir && it.category == old }) {
                saveTxs(l.id, list.map { if (it.dir == dir && it.category == old) it.copy(category = new) else it })
            }
        }
    }

    /* ================= 流水 ================= */

    fun txList(ledgerId: String): List<Tx> {
        val arr = io.read(txKey(ledgerId)) ?: return emptyList()
        return parseTxs(arr)
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
        saveTxs(ledgerId, txList(ledgerId).filterNot { it.id == id })
    }

    /* ================= 存款 ================= */

    fun depList(ledgerId: String): List<Deposit> {
        val arr = io.read(dpKey(ledgerId)) ?: return emptyList()
        return parseDeps(arr)
    }

    /** 总存款（金钱类 + 非金钱类价值合计） */
    fun totalDeposits(ledgerId: String): Cents = depList(ledgerId).sumOf { it.value }

    fun addDep(d: Deposit) {
        val list = depList(d.ledgerId).toMutableList().apply { add(d) }
        saveDeps(d.ledgerId, list)
    }

    fun updateDep(d: Deposit) {
        val list = depList(d.ledgerId).map { if (it.id == d.id) d else it }
        saveDeps(d.ledgerId, list)
    }

    fun deleteDep(id: String, ledgerId: String) {
        saveDeps(ledgerId, depList(ledgerId).filterNot { it.id == id })
    }

    /* ================= 天气（按天） ================= */

    fun weather(ledgerId: String, date: LocalDate): Weather? {
        val map = io.read(wxKey(ledgerId)) ?: return null
        val name = JSONObject(map).optString(date.toString(), "")
        return Weather.entries.find { it.name == name }
    }

    fun setWeather(ledgerId: String, date: LocalDate, w: Weather) {
        val map = io.read(wxKey(ledgerId))
        val obj = if (map.isNullOrEmpty()) JSONObject() else JSONObject(map)
        obj.put(date.toString(), w.name)
        safeWrite(wxKey(ledgerId), obj.toString())
    }

    /* ================= 一键同步标记 ================= */

    fun isSynced(ledgerId: String, ym: String): Boolean =
        ledger(ledgerId)?.syncedMonths?.contains(ym) == true

    fun markSynced(ledgerId: String, ym: String) {
        val list = ledgers().map {
            if (it.id == ledgerId) it.copy(syncedMonths = it.syncedMonths + ym) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
    }

    /* ================= 写入兜底 ================= */

    /** 写入封装：失败时记日志 + toast 提示，不抛异常（避免 UI 崩溃）；switchStorage/rebuild 内部流程仍直接调 io.write 以感知失败 */
    private fun safeWrite(key: String, value: String) {
        try {
            io.write(key, value)
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "write failed: $key -> ${e.message}", e)
            lastWriteError = "${e.message ?: e.javaClass.simpleName}"
            runCatching { toast("保存失败：${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** 最近一次写入错误信息（无错误返回 null），设置页展示用 */
    fun lastWriteError(): String? = lastWriteError

    /* ================= 序列化 ================= */

    private fun txKey(id: String) = "txs.$id"
    private fun dpKey(id: String) = "dps.$id"
    private fun wxKey(id: String) = "wx.$id"

    private fun ledgersToJson(list: List<Ledger>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("cover", it.coverColor)
                put("font", it.font)
                put("synced", JSONArray(it.syncedMonths.toList()))
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
                    o.getString("id"), o.getString("name"), o.optInt("cover", 0),
                    font = o.optString("font", "pixel"), syncedMonths = synced,
                )
            )
        }
        return out
    }

    private fun saveTxs(ledgerId: String, list: List<Tx>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("ld", ledgerId); put("date", it.date.toString()); put("time", it.time)
                put("dir", it.dir.name); put("cat", it.category)
                put("amt", it.amount); put("name", it.name); put("note", it.note)
            })
        }
        safeWrite(txKey(ledgerId), arr.toString())
    }

    private fun parseTxs(s: String): List<Tx> {
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
                )
            )
        }
        return out
    }

    private fun saveDeps(ledgerId: String, list: List<Deposit>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("ld", ledgerId); put("date", it.date.toString()); put("kind", it.kind.name)
                put("name", it.name); put("note", it.note); put("value", it.value)
            })
        }
        safeWrite(dpKey(ledgerId), arr.toString())
    }

    private fun parseDeps(s: String): List<Deposit> {
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
        return out
    }

    private fun newId(): String = "${System.currentTimeMillis()}_${(1000..9999).random()}"

    /* ================= 内置演示账本（已移除：不默认建账本，空就是空） ================= */
}

/* ================= Key-Value 存储后端 ================= */

interface LedgerIO {
    fun read(key: String): String?
    fun write(key: String, value: String)   // 失败抛异常（迁移可感知）
    fun remove(key: String)
    fun keys(): Set<String>
}

/** 默认后端：应用内部 SharedPreferences（JSON 字符串） */
private class PrefsLedgerIO(context: Context) : LedgerIO {
    private val prefs = context.getSharedPreferences("pixelbook_data", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> =
        prefs.all.keys.filter { it == KEY_LEDGERS || it.startsWith("txs.") || it.startsWith("dps.") || it.startsWith("wx.") }
            .toSet()
}

/** 外部 SAF 目录后端：每个 key 存为 pixelbook_<key>.json */
private class SafLedgerIO(context: Context, private val root: DocumentFile) : LedgerIO {
    private val cr = context.contentResolver

    /** 目录树 URI（供记录"上次存储"用） */
    val uri: Uri get() = root.uri

    /**
     * 直接构造子文档 URI，绕过 DocumentFile 的枚举/查找缓存。
     * 新授权目录或部分设备上 findFile/listFiles 可能短暂返回空，
     * 导致"目录已有数据却判定为空"的误判；直连 URI 不依赖枚举，最可靠。
     */
    private fun directUri(key: String): Uri? = directUriOf(fileName(key))

    private fun directUriOf(fileName: String): Uri? = runCatching {
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(root.uri)
        // 只编码文件名片段；整段 Uri.encode 会把 "/" 变成 %2F，再经
        // buildDocumentUriUsingTree 二次编码成 %252F，文档 id 对不上 → 永远读不到
        val child = treeDocId + "/" + Uri.encode(fileName)
        android.provider.DocumentsContract.buildDocumentUriUsingTree(root.uri, child)
    }.getOrNull()

    /** 枚举非 pixelbook_ 前缀的 json（即 {账本名}_{时间戳}.json 备份文件），返回 (文件名, 内容) */
    fun listBackups(): List<Pair<String, String>> = runCatching {
        root.listFiles().mapNotNull { f ->
            val n = f.name ?: return@mapNotNull null
            if (n.startsWith(FILE_PREFIX) || !n.endsWith(".json")) return@mapNotNull null
            val text = readUri(f.uri) ?: return@mapNotNull null
            n to text
        }
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "listBackups failed -> ${e.message}")
    }.getOrDefault(emptyList())

    /** 备份文件写入：同名覆盖（不产生 (1) 副本）；找不到文件时才新建 */
    fun writeBackup(fileName: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        // 1) 权威枚举命中 → 用其 URI 覆盖（中文文件名直连 URI 在部分 provider 上不匹配，枚举最可靠）
        val existing = runCatching { root.findFile(fileName) }.getOrNull()
        if (existing != null && existing.exists()) {
            val ok = runCatching {
                cr.openOutputStream(existing.uri, "wt")?.use { it.write(bytes) }
                true
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "backup findFile-overwrite fail: $fileName -> ${e.message}")
            }.getOrDefault(false)
            if (ok) return
        }
        // 2) 直连覆盖（枚举失效时的备用；文件不存在时抛 FileNotFoundException → null）
        val d = directUriOf(fileName)
        if (d != null) {
            val ok = runCatching {
                cr.openOutputStream(d, "wt")?.use { it.write(bytes) }
                true
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "backup direct fail: $fileName -> ${e.message}")
            }.getOrDefault(false)
            if (ok) return
        }
        // 3) 新建（同名已存在且枚举失效时 provider 可能自动改名 (1)，数据不丢即可）
        val f = root.createFile("application/json", fileName)
            ?: throw IOException("无法在存储目录创建备份文件：$fileName")
        val os = cr.openOutputStream(f.uri, "wt")
            ?: throw IOException("无法写入备份文件：$fileName")
        os.use { it.write(bytes) }
    }

    private fun readUri(uri: Uri): String? = runCatching {
        cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    private fun file(key: String): DocumentFile? = runCatching {
        val byName = root.findFile(fileName(key))
        if (byName != null && byName.exists()) return byName
        // 变体容错：精确名失败时按前缀匹配（如 pixelbook_ledgers (1).json / 微信重复保存的副本）
        val prefix = "$FILE_PREFIX$key."
        root.listFiles().firstOrNull {
            val n = it.name
            n != null && n.startsWith(prefix) && n.endsWith(".json") && it.exists()
        } ?: root.listFiles().firstOrNull {
            val n = it.name
            n != null && n.startsWith(FILE_PREFIX + key) && n.endsWith(".json") && it.exists()
        }
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "file() failed: $key -> ${e.message}")
    }.getOrNull()

    override fun read(key: String): String? {
        val f = file(key)
        if (f != null && f.exists()) {
            val v = readUri(f.uri)
            if (v != null) return v
            android.util.Log.w("PdfExp", "read stream null: $key")
        }
        // 枚举失败/文件新建后未生效时直连读（文件不存在返回 null 属正常，不打日志）
        val d = directUri(key)
            ?: run { android.util.Log.w("PdfExp", "read directUri build failed: $key"); return null }
        return readUri(d)
    }

    override fun write(key: String, value: String) {
        // 优先直连写入：文件已存在 → 覆盖原文件（绝不产生 (1) 副本）；
        // 不存在 → 落到 DocumentFile 创建
        val d = directUri(key)
        if (d != null) {
            val ok = runCatching {
                cr.openOutputStream(d, "wt")?.use { it.write(value.toByteArray(Charsets.UTF_8)) }
                true
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "direct write fail: $key -> ${e.message}")
            }.getOrDefault(false)
            if (ok) return
        }
        // file() 返回 null 是正常情况（文件还不存在，走创建），只有抛异常才说明权限/IO 故障
        var f = try {
            file(key)
        } catch (e: Exception) {
            throw IOException("无法访问存储目录（权限可能已失效）：$key", e)
        }
        if (f == null || !f.exists()) {
            f = runCatching { root.createFile("application/json", fileName(key)) }
                .onFailure { e -> android.util.Log.w("PdfExp", "createFile fail: $key -> ${e.message}") }
                .getOrNull()
                ?: throw IOException("无法在存储目录创建文件：$key")
        }
        val os = cr.openOutputStream(f.uri, "wt")
            ?: throw IOException("无法写入存储目录：$key")
        os.use { it.write(value.toByteArray(Charsets.UTF_8)) }
    }

    override fun remove(key: String) {
        file(key)?.delete()
    }

    override fun keys(): Set<String> = runCatching {
        root.listFiles().mapNotNull { f ->
            val n = f.name ?: return@mapNotNull null
            if (n.startsWith(FILE_PREFIX) && n.endsWith(".json"))
                n.removePrefix(FILE_PREFIX).removeSuffix(".json") else null
        }.toSet()
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "keys() failed -> ${e.message}")
    }.getOrDefault(emptySet())

    private fun fileName(key: String) = "$FILE_PREFIX$key.json"
}