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
 *
 * 存储结构（v2 单文件账本）：
 * - 每个账本 = 一个 JSON 包：账本信息 + 全部收支(txs) + 存款(dps) + 天气(wx)
 *   - 外部 SAF 目录：单文件 `{账本名}_{创建时间戳}.json`（毫秒精度），文件名写在 ledgers 索引的 file 字段
 *   - 内部 SharedPreferences：同内容的 key `ledger.<id>`
 * - 类别（收入/花销）：全局一个 key `cats`（{in:[...], out:[...]}）
 * - 账本索引：`ledgers`（每本一行：id/名称/封面/字体/归档标记/文件名）
 * - 兼容：旧版三文件（pixelbook_txs.<id>.json / dps / wx 与 cats.in / cats.out）
 *   在启动或切换时自动整理为单文件结构（读取侧也兜底兼容）。
 */
private const val KEY_LEDGERS = "ledgers"
private const val KEY_CATS = "cats"                // {in:[...], out:[...]}
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
        // 启动时自动整理：旧版三文件 / 旧类别 key → 单文件结构，并补齐索引
        organizeIfNeeded()
        android.util.Log.d("PdfExp", "after organize: ledgers=${ledgers().size}")
    }

    companion object {
        private const val CFG_NAME = "pixelbook_cfg"
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

    /* ================= 账本单文件 ================= */

    /** 账本单文件名：账本名 + 创建时间戳（毫秒，来自账本 id 前缀） */
    private fun bundleFileName(name: String, id: String): String =
        "${sanitizeFileName(name)}_${createStampMs(id)}.json"

    /** 从账本 id 前缀（epoch 毫秒）解析创建时间戳 yyyyMMdd_HHmmssSSS；解析失败用当前时间兜底 */
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

    /** 账本索引里记录的文件名；没有则按当前名称计算兜底 */
    private fun fileOf(id: String): String =
        ledger(id)?.file?.takeIf { it.isNotBlank() }
            ?: bundleFileName(ledger(id)?.name ?: "账本", id)

    private fun bundleKey(id: String) = "ledger.$id"

    /** 组装账本 JSON 包字符串 */
    private fun bundleJson(l: Ledger, txs: List<Tx>, dps: List<Deposit>, wx: JSONObject): String =
        JSONObject().apply {
            put("app", "pixelbook"); put("type", "ledger"); put("version", 2)
            put("ledger", JSONObject().apply {
                put("id", l.id); put("name", l.name); put("cover", l.coverColor); put("font", l.font)
                put("synced", JSONArray(l.syncedMonths.toList()))
            })
            put("wx", wx)
            put("txs", JSONArray().apply { txs.forEach { put(txToJson(it)) } })
            put("dps", JSONArray().apply { dps.forEach { put(depToJson(it)) } })
        }.toString()

    /** 读取某账本的数据包（当前存储）：索引文件名优先，其次内部 key，最后按 id 扫描 */
    private fun readBundle(id: String): JSONObject? {
        val f = fileOf(id)
        var raw: String? = null
        if (io is SafLedgerIO) raw = (io as SafLedgerIO).readNamed(f)
        if (raw == null) raw = io.read(bundleKey(id))
        if (raw == null && io is SafLedgerIO) {
            (io as SafLedgerIO).findLedgerFileById(id)?.let { ff -> raw = (io as SafLedgerIO).readNamed(ff) }
        }
        return raw?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    /** 写某账本数据包到指定后端（saf 按 file 名写；prefs 按 key 写） */
    private fun saveBundleTo(target: LedgerIO, l: Ledger, file: String?, obj: JSONObject) {
        val text = obj.toString()
        if (target is SafLedgerIO) {
            target.writeNamed(file?.takeIf { it.isNotBlank() } ?: bundleFileName(l.name, l.id), text)
        } else {
            target.write(bundleKey(l.id), text)
        }
    }

    private fun removeBundleAt(target: LedgerIO, l: Ledger, file: String?) {
        if (target is SafLedgerIO) {
            file?.takeIf { it.isNotBlank() }?.let { target.removeNamed(it) }
        } else {
            target.remove(bundleKey(l.id))
        }
    }

    /** 读指定后端某账本数据包（切换/载入用；file 优先，其次按 id 扫描） */
    private fun readBundleFrom(target: LedgerIO, l: Ledger, file: String?): JSONObject? {
        if (target is SafLedgerIO) {
            file?.takeIf { it.isNotBlank() }?.let { ff ->
                target.readNamed(ff)?.let { raw -> return runCatching { JSONObject(raw) }.getOrNull() }
            }
            target.findLedgerFileById(l.id)?.let { ff ->
                target.readNamed(ff)?.let { raw -> return runCatching { JSONObject(raw) }.getOrNull() }
            }
            return null
        }
        return target.read(bundleKey(l.id))?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun emptyBundleLike(l: Ledger) =
        JSONObject(bundleJson(l, emptyList(), emptyList(), JSONObject()))

    /* ================= 存储目录（设置） ================= */

    /** 当前存储位置描述（设置页展示用） */
    fun storageDirDescription(): String {
        val uri = cfg.getString(KEY_STORAGE_TREE, null) ?: return "应用内部存储（默认）"
        val name = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name
            ?.takeIf { it.isNotBlank() } ?: "所选目录"
        return "外部目录：$name"
    }

    /** 账本存储信息：位置描述 + 数据占用字节数（账本单文件） */
    fun ledgerStorageInfo(id: String): Pair<String, Long> {
        val bytes = readBundle(id)?.toString()?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
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
     * 完整迁移：1) 当前每本账本的单文件复制（备份）到目标 + 类别表同步；
     * 2) 载入目标存储中的账本（新单文件 / 旧备份 / 老三文件自动整理）；
     * 3) 合并索引（同 id 时名称/封面等以当前为准，最新数据同步过去）。
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
            val targetName = if (newIo is SafLedgerIO) "新目录" else "内部存储"

            // ===== 1) 备份：当前每本账本的单文件（本身就是全量数据）复制/覆盖到目标 =====
            val curLedgers = ledgers()
            var copied = 0
            for (l in curLedgers) {
                readBundle(l.id)?.let { obj ->
                    val targetFile = targetFileOf(newIo, l)
                    saveBundleTo(newIo, l, targetFile, obj)
                    copied++
                }
            }
            writeCatsTo(newIo)
            android.util.Log.d("PdfExp", "copied $copied ledger bundles -> $targetName")

            // ===== 2) 载入/整理：目标存储里的账本（新单文件 / 备份 / 老三文件自动整理） =====
            val targetLedgers = scanLedgers(newIo)

            // ===== 3) 合并索引：目标优先保留顺序；同 id 的名称/封面等以当前为准 =====
            val append = curLedgers.filterNot { l -> targetLedgers.any { it.id == l.id } }
            val merged = targetLedgers.map { tl ->
                curLedgers.find { it.id == tl.id }?.copy(file = tl.file.takeIf { f -> f.isNotBlank() } ?: tl.file) ?: tl
            } + append
            var result: String
            if (merged.isNotEmpty()) {
                newIo.write(KEY_LEDGERS, ledgersToJson(merged))
                android.util.Log.d("PdfExp", "merged: ${merged.size} ledgers -> $targetName")
                result = if (append.isEmpty() && targetLedgers.isNotEmpty())
                    "ok:已载入${targetName}的 ${targetLedgers.size} 本账本（当前数据已同步）"
                else
                    "ok:目标与当前合并，共 ${merged.size} 本账本"
            } else {
                result = "ok:已切换到$targetName（无数据）"
            }
            if (lastOrganizeFailed > 0) {
                result += "；有 $lastOrganizeFailed 本旧账本整理未成功（可能在文件管理器中手动处理）"
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

    /** 目标存储里该账本已有的文件名（载入扫描得到）；没有则用当前文件名 */
    private fun targetFileOf(target: LedgerIO, l: Ledger): String? {
        if (target is SafLedgerIO) {
            target.findLedgerFileById(l.id)?.let { return it }
        }
        return fileOf(l.id)
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

    /* ================= 兼容整理（旧版三文件 / 旧类别 key → 单文件结构） ================= */

    /** 最近一次整理失败的账本数（设置页/切换结果提示用） */
    @Volatile private var lastOrganizeFailed = 0

    /** 启动时检测并整理：旧 cats.in/out → cats；老 txs/dps/wx → 账本单文件；补齐索引 */
    private fun organizeIfNeeded() {
        // 1) 类别迁移（老 key 存在时合并进新 key 并删除）
        if (io.read("cats.in") != null || io.read("cats.out") != null) {
            writeCatsTo(io)
        }
        // 2) 账本扫描整理（自动把老三文件合并成单文件）
        lastOrganizeFailed = 0
        val scanned = scanLedgers(io)
        if (scanned.isNotEmpty()) {
            // 索引 = 扫描结果 ∪ 旧索引中未被扫描到的条目（防个别文件漏检导致丢账本）
            val oldById = readLedgersRaw(io).associateBy { it.optString("id") }
            val merged = scanned.toMutableList()
            oldById.values.forEach { o ->
                if (merged.none { it.id == o.optString("id") }) {
                    runCatching {
                        merged.add(
                            Ledger(
                                o.getString("id"), o.getString("name"),
                                o.optInt("cover", 0), font = o.optString("font", "pixel"),
                                syncedMonths = runCatching {
                                    val sa = o.optJSONArray("synced") ?: JSONArray()
                                    (0 until sa.length()).map { sa.getString(it) }.toMutableSet()
                                }.getOrDefault(mutableSetOf()),
                                file = o.optString("file", ""),
                            )
                        )
                    }
                }
            }
            io.write(KEY_LEDGERS, ledgersToJson(merged))
            android.util.Log.d("PdfExp", "organized ${scanned.size} ledgers (fail=$lastOrganizeFailed)")
        } else if (lastOrganizeFailed > 0) {
            android.util.Log.w("PdfExp", "organize failed: $lastOrganizeFailed")
        }
    }

    /**
     * 扫描指定存储中的全部账本（返回带 file 的 Ledger 列表）：
     * - 新单文件（type=ledger / 旧备份 type=backup）→ 直接识别；
     * - 老版三文件（pixelbook_txs.<id>.json / dps / wx，或 prefs 的 txs.<id> 等）→
     *   合并成单文件并删除老三件（整理）；
     * - 以文件为真相（不依赖索引）。
     */
    private fun scanLedgers(target: LedgerIO): List<Ledger> {
        val results = mutableListOf<Ledger>()
        val bundleFiles = mutableListOf<Pair<String, JSONObject>>()
        val legacy = LinkedHashMap<String, MutableMap<String, String?>>()

        if (target is SafLedgerIO) {
            target.listJsonFiles().forEach { (name, text) ->
                // 老版三文件优先按文件名识别（内容可能是数组，不能先解析成 JSONObject）
                if (name.startsWith("pixelbook_txs.") || name.startsWith("pixelbook_dps.") || name.startsWith("pixelbook_wx.")) {
                    val kind = name.removePrefix("pixelbook_").substringBefore(".")
                    val id = name.removePrefix("pixelbook_").substringAfter(".").removeSuffix(".json")
                    legacy.getOrPut(id) { mutableMapOf() }[kind] = text
                    return@forEach
                }
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@forEach
                val ap = obj.optString("app", "")
                val tp = obj.optString("type", "")
                if (ap == "pixelbook" && (tp == "ledger" || tp == "backup") && obj.has("ledger")) {
                    bundleFiles.add(name to obj)
                }
            }
        } else {
            target.keys().forEach { k ->
                val text = target.read(k) ?: return@forEach
                if (k.startsWith("txs.") || k.startsWith("dps.") || k.startsWith("wx.")) {
                    val kind = k.substringBefore(".")
                    val id = k.substringAfter(".")
                    legacy.getOrPut(id) { mutableMapOf() }[kind] = text
                }
            }
        }

        // 老格式整理：合并成单文件（写成功后删除老三件）；
        // 若同 id 已有单文件/备份（真实名在 bundleFiles 里）→ 老三件只是历史副本，直接清理不重复生成
        if (legacy.isNotEmpty()) {
            val bundleIds = bundleFiles.mapNotNull { (_, o) -> o.optJSONObject("ledger")?.optString("id", "") }.toSet()
            val index = readLedgersRaw(target)
            legacy.forEach { (id, parts) ->
                if (id in bundleIds) {
                    parts.forEach { (kind, _) ->
                        if (target is SafLedgerIO) target.removeNamed("$FILE_PREFIX$kind.$id.json")
                        else target.remove("$kind.$id")
                    }
                    return@forEach
                }
                val meta = index.find { it.optString("id") == id }
                val name = meta?.optString("name") ?: "恢复账本${id.takeLast(4)}"
                val cover = meta?.optInt("cover", 0) ?: 0
                val font = meta?.optString("font", "pixel") ?: "pixel"
                val syncedSet = mutableSetOf<String>()
                runCatching {
                    val sa = meta?.optJSONArray("synced") ?: JSONArray()
                    for (j in 0 until sa.length()) syncedSet.add(sa.getString(j))
                }
                val l = Ledger(id, name, cover, font = font, syncedMonths = syncedSet,
                    file = bundleFileName(name, id))
                val obj = JSONObject().apply {
                    put("app", "pixelbook"); put("type", "ledger"); put("version", 2)
                    put("ledger", JSONObject().apply {
                        put("id", l.id); put("name", l.name); put("cover", l.coverColor); put("font", l.font)
                        put("synced", JSONArray(l.syncedMonths.toList()))
                    })
                    put("wx", runCatching { JSONObject(parts["wx"] ?: "{}") }.getOrDefault(JSONObject()))
                    put("txs", runCatching { JSONArray(parts["txs"] ?: "[]") }.getOrDefault(JSONArray()))
                    put("dps", runCatching { JSONArray(parts["dps"] ?: "[]") }.getOrDefault(JSONArray()))
                }
                runCatching { saveBundleTo(target, l, l.file, obj) }
                    .onFailure { e ->
                        lastOrganizeFailed++
                        android.util.Log.w("PdfExp", "organize write fail: $id -> ${e.message}")
                    }
                    .onSuccess {
                        parts.forEach { (kind, _) ->
                            if (target is SafLedgerIO) target.removeNamed("$FILE_PREFIX$kind.$id.json")
                            else target.remove("$kind.$id")
                        }
                        results.add(l)
                    }
            }
            android.util.Log.d("PdfExp", "organized legacy: ${results.size}")
        }

        // 单文件账本 / 旧备份文件
        bundleFiles.forEach { (name, obj) ->
            runCatching {
                val lo = obj.getJSONObject("ledger")
                val synced = mutableSetOf<String>()
                runCatching {
                    val sa = lo.optJSONArray("synced") ?: JSONArray()
                    for (j in 0 until sa.length()) synced.add(sa.getString(j))
                }
                results.add(
                    Ledger(
                        id = lo.getString("id"), name = lo.getString("name"),
                        coverColor = lo.optInt("cover", 0), font = lo.optString("font", "pixel"),
                        syncedMonths = synced, file = name,
                    )
                )
            }
        }
        // 合并去重：同 id 多来源时优先"非占位名"（备份/单文件里的真实名 > 老三件整理的占位名）
        val placeholder = fun(id: String) = "恢复账本${id.takeLast(4)}"
        return results.groupBy { it.id }.values.map { items ->
            items.firstOrNull { it.name != placeholder(it.id) } ?: items.first()
        }.filter { it.id.isNotBlank() }
    }

    /** 读取存储里的账本索引原始 JSON 对象列表（可能为空） */
    private fun readLedgersRaw(target: LedgerIO): List<JSONObject> =
        target.read(KEY_LEDGERS)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }.getOrDefault(emptyList())
        } ?: emptyList()

    /* ================= 账本 ================= */

    fun ledgers(): List<Ledger> {
        val arr = io.read(KEY_LEDGERS) ?: return emptyList()
        return parseLedgers(arr)
    }

    fun ledger(id: String): Ledger? = ledgers().find { it.id == id }

    fun addLedger(name: String, coverIdx: Int): Ledger {
        val id = newId()
        val l = Ledger(id = id, name = name.trim(), coverColor = coverIdx, file = bundleFileName(name.trim(), id))
        val list = ledgers().toMutableList().apply { add(l) }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        // 初始化空数据包（含账本信息）
        safeBundleSave(l, emptyBundleLike(l))
        return l
    }

    fun renameLedger(id: String, name: String) {
        val old = ledger(id) ?: return
        val newName = name.trim()
        val list = ledgers().map {
            if (it.id == id) it.copy(name = newName, file = bundleFileName(newName, it.id)) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        // 旧文件搬移到新文件名（数据包内 name 同步；prefs 后端 key 不变无动作）
        if (io is SafLedgerIO && newName != old.name) {
            readBundle(id)?.let { obj ->
                runCatching { obj.getJSONObject("ledger").put("name", newName) }
                val newFile = bundleFileName(newName, id)
                runCatching { (io as SafLedgerIO).writeNamed(newFile, obj.toString()) }
                    .onSuccess {
                        old.file.takeIf { f -> f.isNotBlank() && f != newFile }
                            ?.let { runCatching { (io as SafLedgerIO).removeNamed(it) } }
                    }
            }
        }
    }

    /** 编辑账本：名称 / 字体 / 封面颜色 */
    fun updateLedger(id: String, name: String, font: String, coverColor: Int) {
        val old = ledger(id) ?: return
        val newName = name.trim()
        val list = ledgers().map {
            if (it.id == id) it.copy(name = newName, font = font, coverColor = coverColor, file = bundleFileName(newName, it.id)) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        if (io is SafLedgerIO && newName != old.name) {
            readBundle(id)?.let { obj ->
                runCatching {
                    obj.getJSONObject("ledger").apply {
                        put("name", newName); put("font", font); put("cover", coverColor)
                    }
                }
                val newFile = bundleFileName(newName, id)
                runCatching { (io as SafLedgerIO).writeNamed(newFile, obj.toString()) }
                    .onSuccess {
                        old.file.takeIf { f -> f.isNotBlank() && f != newFile }
                            ?.let { runCatching { (io as SafLedgerIO).removeNamed(it) } }
                    }
            }
        }
    }

    fun deleteLedger(id: String) {
        val old = ledger(id) ?: return
        val list = ledgers().filterNot { it.id == id }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
        removeBundleAt(io, old, old.file)
    }

    /* ================= 类别维护（收入/花销：全局一张表，可增删改） ================= */

    private fun readCatsObj(): JSONObject? {
        io.read(KEY_CATS)?.let { raw -> return runCatching { JSONObject(raw) }.getOrNull() }
        // 旧版 cats.in / cats.out 迁移
        if (io.read("cats.in") != null || io.read("cats.out") != null) {
            writeCatsTo(io)
            return io.read(KEY_CATS)?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
        return null
    }

    private fun readCatsArr(obj: JSONObject?, tag: String, defaults: List<String>): List<String> {
        obj?.optJSONArray(tag)?.let { arr ->
            return (0 until arr.length()).map { arr.getString(it) }
        } ?: return defaults
    }

    private fun catsJson(income: List<String>, expense: List<String>): String =
        JSONObject().apply {
            put("in", JSONArray(income))
            put("out", JSONArray(expense))
        }.toString()

    /** 把当前类别表写入目标存储（含老 key 迁移清理） */
    private fun writeCatsTo(target: LedgerIO) {
        val obj = readCatsObj() ?: JSONObject().apply {
            put("in", JSONArray(IncomeCats.list)); put("out", JSONArray(ExpenseCats.list))
        }
        target.write(KEY_CATS, obj.toString())
        runCatching { target.remove("cats.in") }
        runCatching { target.remove("cats.out") }
    }

    /** 收入类别列表（默认：工资/理财/红包/其他） */
    fun incomeCats(): List<String> = readCatsArr(readCatsObj(), "in", IncomeCats.list)

    /** 花销类别列表（默认：餐饮/交通/购物/娱乐/居住/医疗/其他） */
    fun expenseCats(): List<String> = readCatsArr(readCatsObj(), "out", ExpenseCats.list)

    /** 新增类别；重名/空名返回 false */
    fun addIncomeCat(name: String): Boolean = addCat("in", IncomeCats.list, name)
    fun addExpenseCat(name: String): Boolean = addCat("out", ExpenseCats.list, name)

    /** 编辑类别：全量同步所有被引用记录；「其他」与重名/空名不可编辑 */
    fun renameIncomeCat(old: String, new: String): Boolean = renameCat("in", IncomeCats.list, TxDir.IN, old, new)
    fun renameExpenseCat(old: String, new: String): Boolean = renameCat("out", ExpenseCats.list, TxDir.OUT, old, new)

    /** 删除类别：被引用记录全量回退为「其他」；「其他」不可删除 */
    fun deleteIncomeCat(name: String): Boolean = deleteCat("in", IncomeCats.list, TxDir.IN, name)
    fun deleteExpenseCat(name: String): Boolean = deleteCat("out", ExpenseCats.list, TxDir.OUT, name)

    private fun readCatsFor(tag: String, defaults: List<String>): List<String> =
        readCatsArr(readCatsObj(), tag, defaults)

    private fun writeCatsTag(tag: String, list: List<String>) {
        val income = if (tag == "in") list else readCatsFor("in", IncomeCats.list)
        val expense = if (tag == "out") list else readCatsFor("out", ExpenseCats.list)
        safeWrite(KEY_CATS, catsJson(income, expense))
    }

    private fun addCat(tag: String, defaults: List<String>, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val cur = readCatsFor(tag, defaults)
        if (n in cur) return false
        writeCatsTag(tag, cur + n)
        return true
    }

    private fun renameCat(tag: String, defaults: List<String>, dir: TxDir, old: String, new: String): Boolean {
        val n = new.trim()
        if (old == CATEGORY_OTHERS) return false          // 「其他」固定，不可改
        val cats = readCatsFor(tag, defaults)
        if (old !in cats) return false
        if (n.isEmpty() || n == old || n in cats) return false   // 空名 / 未变 / 重名
        writeCatsTag(tag, cats.map { if (it == old) n else it })
        applyCatRename(dir, old, n)
        return true
    }

    private fun deleteCat(tag: String, defaults: List<String>, dir: TxDir, name: String): Boolean {
        if (name == CATEGORY_OTHERS) return false          // 「其他」固定，不可删
        val cats = readCatsFor(tag, defaults)
        if (name !in cats) return false
        writeCatsTag(tag, cats.filterNot { it == name })
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

    fun txList(ledgerId: String): List<Tx> = parseTxs(readBundle(ledgerId)?.optJSONArray("txs")?.toString())

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

    /** 读写账本数据包内 txs 段 */
    private fun saveTxs(ledgerId: String, list: List<Tx>) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        obj.put("txs", JSONArray().apply { list.forEach { put(txToJson(it)) } })
        safeBundleSave(l, obj)
    }

    /* ================= 存款 ================= */

    fun depList(ledgerId: String): List<Deposit> = parseDeps(readBundle(ledgerId)?.optJSONArray("dps")?.toString())

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

    private fun saveDeps(ledgerId: String, list: List<Deposit>) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        obj.put("dps", JSONArray().apply { list.forEach { put(depToJson(it)) } })
        safeBundleSave(l, obj)
    }

    /* ================= 天气（按天） ================= */

    fun weather(ledgerId: String, date: LocalDate): Weather? {
        val obj = readBundle(ledgerId) ?: return null
        val name = obj.optJSONObject("wx")?.optString(date.toString(), "") ?: ""
        return Weather.entries.find { it.name == name }
    }

    fun setWeather(ledgerId: String, date: LocalDate, w: Weather) {
        val l = ledger(ledgerId) ?: return
        val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
        val wx = obj.optJSONObject("wx") ?: JSONObject().also { obj.put("wx", it) }
        wx.put(date.toString(), w.name)
        safeBundleSave(l, obj)
    }

    /* ================= 一键同步标记（归档） ================= */

    fun isSynced(ledgerId: String, ym: String): Boolean =
        ledger(ledgerId)?.syncedMonths?.contains(ym) == true

    fun markSynced(ledgerId: String, ym: String) {
        val list = ledgers().map {
            if (it.id == ledgerId) it.copy(syncedMonths = it.syncedMonths + ym) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
    }

    /** 清除某月的归档标记（重置归档用） */
    fun unmarkSynced(ledgerId: String, ym: String) {
        val list = ledgers().map {
            if (it.id == ledgerId) it.copy(syncedMonths = it.syncedMonths - ym) else it
        }
        safeWrite(KEY_LEDGERS, ledgersToJson(list))
    }

    /** 定位某月「一键同步」生成的攒钱存款记录（name=攒钱 且 备注以 "yyyy.M 月收入已归档" 开头） */
    fun archivedDepFor(ledgerId: String, ym: String): Deposit? {
        val y = runCatching { java.time.YearMonth.parse(ym) }.getOrNull() ?: return null
        val prefix = "${y.year}.${y.monthValue} 月收入已归档"
        return depList(ledgerId).firstOrNull { it.name == "攒钱" && it.note.startsWith(prefix) }
    }

    /**
     * 归档结余为「攒钱」存款（幂等）：
     * 1) 若该月已有归档记录 → 先删旧的再写入（标记丢失/重复点击都不会造成存款翻倍）；
     * 2) 存款写入成功才返回 true（失败不打归档标记，避免"显示已归档但存款没变"）。
     */
    fun archiveMonth(ledgerId: String, ym: String, value: Cents): Boolean {
        val l = ledger(ledgerId) ?: return false
        val old = archivedDepFor(ledgerId, ym)
        val list = depList(ledgerId).filterNot { old != null && it.id == old.id } + Deposit(
            id = "d${System.currentTimeMillis()}", ledgerId = ledgerId,
            // 归档归属该月月末（跨月查看时记录落在正确的月份）
            date = runCatching { java.time.YearMonth.parse(ym).atEndOfMonth() }.getOrDefault(LocalDate.now()),
            kind = DepositKind.MONEY,
            name = "攒钱",
            note = runCatching {
                val y = java.time.YearMonth.parse(ym)
                "${y.year}.${y.monthValue} 月收入已归档 ${moneyYuan(value)} 元"
            }.getOrDefault(""),
            value = value,
        )
        return try {
            val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
            obj.put("dps", JSONArray().apply { list.forEach { put(depToJson(it)) } })
            saveBundleTo(io, l, fileOf(l.id), obj)
            true
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "archive write failed: $ledgerId/$ym -> ${e.message}", e)
            false
        }
    }

    /** 重置本月归档：删除该月攒钱存款记录 + 清除归档标记；删除失败返回 false */
    fun resetArchive(ledgerId: String, ym: String): Boolean {
        val l = ledger(ledgerId) ?: return false
        val old = archivedDepFor(ledgerId, ym)
        var ok = true
        if (old != null) {
            ok = try {
                val obj = readBundle(ledgerId) ?: emptyBundleLike(l)
                obj.put("dps", JSONArray().apply {
                    depList(ledgerId).filterNot { it.id == old.id }.forEach { put(depToJson(it)) }
                })
                saveBundleTo(io, l, fileOf(l.id), obj)
                true
            } catch (e: Exception) {
                android.util.Log.e("PdfExp", "reset write failed -> ${e.message}", e)
                false
            }
        }
        if (ok) unmarkSynced(ledgerId, ym)
        return ok
    }

    /** 分 → 元 显示字符串（归档备注用，与 Fmt.yen 一致的数字写法） */
    private fun moneyYuan(c: Cents): String {
        val v = if (c < 0) -c else c
        return if (v % 100 == 0L) "${v / 100}" else "%d.%02d".format(v / 100, v % 100)
    }

    /* ================= 写入兜底 ================= */

    /** 写入封装：失败时记日志 + toast 提示，不抛异常（避免 UI 崩溃）；switchStorage/整理内部流程仍直接调 io.write 以感知失败 */
    private fun safeWrite(key: String, value: String) {
        try {
            io.write(key, value)
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "write failed: $key -> ${e.message}", e)
            lastWriteError = "${e.message ?: e.javaClass.simpleName}"
            runCatching { toast("保存失败：${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** 账本单文件写入兜底（写失败记日志 + toast + 记录 lastWriteError） */
    private fun safeBundleSave(l: Ledger, obj: JSONObject) {
        try {
            saveBundleTo(io, l, fileOf(l.id), obj)
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "bundle save failed: ${l.id} -> ${e.message}", e)
            lastWriteError = "${e.message ?: e.javaClass.simpleName}"
            runCatching { toast("保存失败：${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** 最近一次写入错误信息（无错误返回 null），设置页展示用 */
    fun lastWriteError(): String? = lastWriteError

    /* ================= 序列化 ================= */

    private fun txToJson(tx: Tx): JSONObject = JSONObject().apply {
        put("id", tx.id); put("ld", tx.ledgerId); put("date", tx.date.toString()); put("time", tx.time)
        put("dir", tx.dir.name); put("cat", tx.category)
        put("amt", tx.amount); put("name", tx.name); put("note", tx.note)
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
                )
            )
        }
        return out
    }

    /** 兼容读取：bundle 内 txs 数组解析 */
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
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /** 兼容读取：bundle 内 dps 数组解析 */
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

    /** 文件名清洗：非法字符与空白替换为下划线，截断防超长（名称 30 字 + 戳 22 字符仍安全） */
    private fun sanitizeFileName(name: String): String {
        val clean = name.trim()
            .replace(Regex("""[\\/:*?"<>|\s]"""), "_")
            .take(30)
        return clean.ifEmpty { "账本" }
    }
}

/* ================= Key-Value 存储后端 ================= */

interface LedgerIO {
    fun read(key: String): String?
    fun write(key: String, value: String)   // 失败抛异常（迁移可感知）
    fun remove(key: String)
    fun keys(): Set<String>
}

/** 默认后端：应用内部 SharedPreferences（账本数据单 key 存整个数据包） */
private class PrefsLedgerIO(context: Context) : LedgerIO {
    private val prefs = context.getSharedPreferences("pixelbook_data", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> =
        prefs.all.keys.filter {
            it == "ledgers" || it == "cats" || it == "cats.in" || it == "cats.out" ||
                it.startsWith("ledger.") || it.startsWith("txs.") || it.startsWith("dps.") || it.startsWith("wx.")
        }.toSet()
}

/** 外部 SAF 目录后端：索引/类别等 key 存 pixelbook_<key>.json；账本数据存 {账本名}_{创建时间戳}.json 单文件 */
private class SafLedgerIO(context: Context, private val root: DocumentFile) : LedgerIO {
    private val cr = context.contentResolver

    /** 目录树 URI（供记录"上次存储"用） */
    val uri: Uri get() = root.uri

    private fun directUriOf(fileName: String): Uri? = runCatching {
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(root.uri)
        // 只编码文件名片段；整段 Uri.encode 会把 "/" 变成 %2F，再经
        // buildDocumentUriUsingTree 二次编码成 %252F，文档 id 对不上 → 永远读不到
        val child = treeDocId + "/" + Uri.encode(fileName)
        android.provider.DocumentsContract.buildDocumentUriUsingTree(root.uri, child)
    }.getOrNull()

    private fun readUri(uri: Uri): String? = runCatching {
        cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    private fun fileNamed(fileName: String): DocumentFile? = runCatching {
        val byName = root.findFile(fileName)
        if (byName != null && byName.exists()) return byName
        // 变体容错：前缀匹配（如微信重复保存的副本）
        root.listFiles().firstOrNull {
            val n = it.name
            n != null && n.startsWith(fileName) && it.exists()
        }
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "find $fileName failed -> ${e.message}")
    }.getOrNull()

    /** 按文件名读取（枚举命中优先，直连 URI 兜底） */
    fun readNamed(fileName: String): String? {
        fileNamed(fileName)?.let { f ->
            val v = readUri(f.uri)
            if (v != null) return v
            android.util.Log.w("PdfExp", "readNamed stream null: $fileName")
        }
        val d = directUriOf(fileName)
        return if (d != null) readUri(d) else null
    }

    /** 按文件名写入：同名覆盖（不产生 (1) 副本）；找不到文件时才新建 */
    fun writeNamed(fileName: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        // 1) 权威枚举命中 → 覆盖
        val existing = fileNamed(fileName)
        if (existing != null && existing.exists()) {
            val ok = runCatching {
                cr.openOutputStream(existing.uri, "wt")?.use { it.write(bytes) }
                true
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "named overwrite fail: $fileName -> ${e.message}")
            }.getOrDefault(false)
            if (ok) return
        }
        // 2) 直连覆盖（枚举失效时的备用；文件不存在时 FileNotFoundException → 继续往下）
        val d = directUriOf(fileName)
        if (d != null) {
            val ok = runCatching {
                cr.openOutputStream(d, "wt")?.use { it.write(bytes) }
                true
            }.onFailure { e ->
                android.util.Log.w("PdfExp", "named direct fail: $fileName -> ${e.message}")
            }.getOrDefault(false)
            if (ok) return
        }
        // 3) 新建
        val f = root.createFile("application/json", fileName)
            ?: throw IOException("无法在存储目录创建文件：$fileName")
        val os = cr.openOutputStream(f.uri, "wt")
            ?: throw IOException("无法写入文件：$fileName")
        os.use { it.write(bytes) }
    }

    /** 按文件名删除 */
    fun removeNamed(fileName: String) {
        runCatching { fileNamed(fileName)?.delete() }
    }

    /** 枚举目录中所有 json 文件内容（账本单文件/旧备份/老格式/索引），供载入扫描 */
    fun listJsonFiles(): List<Pair<String, String>> = runCatching {
        root.listFiles().mapNotNull { f ->
            val n = f.name ?: return@mapNotNull null
            if (!n.endsWith(".json")) return@mapNotNull null
            val text = readUri(f.uri) ?: return@mapNotNull null
            n to text
        }
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "listJsonFiles failed -> ${e.message}")
    }.getOrDefault(emptyList())

    /** 按账本 id 查找单文件（在扫描结果里匹配内容 ledger.id；含旧备份） */
    fun findLedgerFileById(id: String): String? =
        listJsonFiles().firstOrNull { (_, text) ->
            runCatching {
                val o = JSONObject(text)
                o.optString("app") == "pixelbook" &&
                    (o.optString("type") == "ledger" || o.optString("type") == "backup") &&
                    o.optJSONObject("ledger")?.optString("id") == id
            }.getOrDefault(false)
        }?.first

    override fun read(key: String): String? = readNamed("$FILE_PREFIX$key.json")

    override fun write(key: String, value: String) = writeNamed("$FILE_PREFIX$key.json", value)

    override fun remove(key: String) = removeNamed("$FILE_PREFIX$key.json")

    override fun keys(): Set<String> = runCatching {
        root.listFiles().mapNotNull { f ->
            val n = f.name ?: return@mapNotNull null
            if (n.startsWith(FILE_PREFIX) && n.endsWith(".json"))
                n.removePrefix(FILE_PREFIX).removeSuffix(".json") else null
        }.toSet()
    }.onFailure { e ->
        android.util.Log.w("PdfExp", "keys() failed -> ${e.message}")
    }.getOrDefault(emptySet())
}