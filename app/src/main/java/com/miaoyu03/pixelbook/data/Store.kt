package com.miaoyu03.pixelbook.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

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
    private var io: LedgerIO = loadIo()

    companion object {
        private const val CFG_NAME = "pixelbook_cfg"
        private const val KEY_LEDGERS = "ledgers"
        private const val KEY_CATS_IN = "cats.in"      // 收入类别表
        private const val KEY_CATS_OUT = "cats.out"    // 花销类别表
        private const val KEY_SEEDED = "demo_seeded_v1"
        private const val KEY_STORAGE_TREE = "storage_tree"
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

    /**
     * 切换数据存储（null = 恢复应用内部存储）。
     * 先读取全部数据再写入新后端，全部成功才切换；失败返回 false，原存储不受影响。
     */
    fun switchStorage(treeUri: Uri?): Boolean {
        val newIo: LedgerIO = if (treeUri == null) {
            PrefsLedgerIO(appContext)
        } else {
            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?.takeIf { it.exists() && it.canWrite() } ?: return false
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {}
            SafLedgerIO(appContext, root)
        }
        // 迁移：枚举全部数据 key → 读旧写新（任一失败即放弃，保留原存储）
        try {
            io.keys().mapNotNull { k -> io.read(k)?.let { k to it } }
                .forEach { (k, v) -> newIo.write(k, v) }
        } catch (e: Exception) {
            return false
        }
        io = newIo
        cfg.edit().let { e ->
            if (treeUri == null) e.remove(KEY_STORAGE_TREE)
            else e.putString(KEY_STORAGE_TREE, treeUri.toString())
        }.apply()
        return true
    }

    private fun loadIo(): LedgerIO {
        val uri = cfg.getString(KEY_STORAGE_TREE, null)
        if (uri != null) {
            val root = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))
            if (root != null && root.exists() && root.canWrite()) {
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
        io.write(KEY_LEDGERS, ledgersToJson(list))
        return l
    }

    fun renameLedger(id: String, name: String) {
        val list = ledgers().map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        io.write(KEY_LEDGERS, ledgersToJson(list))
    }

    /** 编辑账本：名称 / 字体 / 封面颜色 */
    fun updateLedger(id: String, name: String, font: String, coverColor: Int) {
        val list = ledgers().map {
            if (it.id == id) it.copy(name = name.trim(), font = font, coverColor = coverColor) else it
        }
        io.write(KEY_LEDGERS, ledgersToJson(list))
    }

    fun deleteLedger(id: String) {
        val list = ledgers().filterNot { it.id == id }
        io.write(KEY_LEDGERS, ledgersToJson(list))
        io.remove(txKey(id)); io.remove(dpKey(id)); io.remove(wxKey(id))
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
        io.write(key, JSONArray(list).toString())
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
        io.write(wxKey(ledgerId), obj.toString())
    }

    /* ================= 一键同步标记 ================= */

    fun isSynced(ledgerId: String, ym: String): Boolean =
        ledger(ledgerId)?.syncedMonths?.contains(ym) == true

    fun markSynced(ledgerId: String, ym: String) {
        val list = ledgers().map {
            if (it.id == ledgerId) it.copy(syncedMonths = it.syncedMonths + ym) else it
        }
        io.write(KEY_LEDGERS, ledgersToJson(list))
    }

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
        io.write(txKey(ledgerId), arr.toString())
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
        io.write(dpKey(ledgerId), arr.toString())
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

    /* ================= 内置演示账本 ================= */

    fun seedDemoIfEmpty() {
        if (cfg.getBoolean(KEY_SEEDED, false)) return
        if (ledgers().isNotEmpty()) { cfg.edit().putBoolean(KEY_SEEDED, true).apply(); return }
        cfg.edit().putBoolean(KEY_SEEDED, true).apply()
        seedDemo(now = LocalDate.now())
    }

    private fun seedDemo(now: LocalDate) {
        val ledger = addLedger("日常账本", 0)
        val lid = ledger.id

        // 确定性伪随机（固定种子，保证每次演示内容一致）
        var seed = 20260827L
        fun rand(m: Int): Int {
            seed = (seed * 48271) % 2147483647
            return (((seed % m) + m) % m).toInt()
        }

        val expensePool = mapOf(
            "餐饮" to listOf("兰州拉面", "食堂午饭", "奶茶", "火锅", "早餐包子", "外卖"),
            "交通" to listOf("地铁", "公交", "打车", "加油"),
            "购物" to listOf("超市采购", "新衣服", "日用品", "文具"),
            "娱乐" to listOf("电影票", "游戏充值", "KTV"),
            "居住" to listOf("房租", "水电费"),
            "医疗" to listOf("感冒药", "挂号费"),
            "其他" to listOf("快递", "维修"),
        )
        val expCost = mapOf(
            "餐饮" to listOf(2200L, 1500L, 1600L, 8600L, 800L, 2400L),
            "交通" to listOf(400L, 200L, 2500L, 30000L),
            "购物" to listOf(9800L, 15800L, 4500L, 3000L),
            "娱乐" to listOf(4000L, 3000L, 6000L),
            "居住" to listOf(350000L, 14500L),
            "医疗" to listOf(2800L, 5000L),
            "其他" to listOf(1200L, 6800L),
        )

        fun monthOf(monthOffset: Int): java.time.YearMonth {
            return java.time.YearMonth.from(now).plusMonths(monthOffset.toLong())
        }

        fun addExpense(mo: java.time.YearMonth, day: Int, cat: String, time: String, name: String, amt: Cents) {
            addTx(Tx(newId(), lid, mo.atDay(day), time, TxDir.OUT, cat, amt, name, ""))
        }

        fun addIncome(mo: java.time.YearMonth, day: Int, cat: String, time: String, name: String, amt: Cents) {
            addTx(Tx(newId(), lid, mo.atDay(day), time, TxDir.IN, cat, amt, name, ""))
        }

        // —— 当前月：完整流水 ——
        val cur = monthOf(0)
        val curDays = if (now.dayOfMonth >= 3) now.dayOfMonth else 30
        repeat(8) { i ->
            val day = 1 + ((i * 3) % curDays)
            val cat = expensePool.keys.elementAt(i % 7)
            addExpense(cur, day, cat, "%02d:%02d".format(8 + i % 10, rand(60)),
                expensePool[cat]!![rand(expensePool[cat]!!.size)], expCost[cat]!![rand(expCost[cat]!!.size)])
        }
        // 固定几笔：房租月初、工资
        addExpense(cur, 1, "居住", "09:05", "房租", 350000)
        addExpense(cur, minOf(now.dayOfMonth, 28), "交通", "08:40", "地铁", 400)
        // 今天：一笔工资收入 + 多笔支出（类别丰富，验证占比/列表高数据量显示）
        addIncome(cur, now.dayOfMonth, "工资", "09:00", "9月工资", 800000)
        addIncome(cur, now.dayOfMonth, "理财", "11:20", "基金分红", 50000)
        addExpense(cur, now.dayOfMonth, "餐饮", "08:15", "早餐包子", 600)
        addExpense(cur, now.dayOfMonth, "交通", "08:40", "地铁", 400)
        addExpense(cur, now.dayOfMonth, "餐饮", "12:10", "兰州拉面", 2200)
        addExpense(cur, now.dayOfMonth, "交通", "13:05", "公交", 200)
        addExpense(cur, now.dayOfMonth, "购物", "15:30", "超市采购", 9800)
        addExpense(cur, now.dayOfMonth, "娱乐", "18:00", "电影票", 4000)
        addExpense(cur, now.dayOfMonth, "餐饮", "18:40", "奶茶", 1600)
        addExpense(cur, now.dayOfMonth, "居住", "19:10", "水电费", 14500)
        addExpense(cur, now.dayOfMonth, "医疗", "19:45", "感冒药", 2800)
        addExpense(cur, now.dayOfMonth, "其他", "20:20", "快递", 1200)
        addExpense(cur, now.dayOfMonth, "购物", "21:00", "文具", 3000)
        // 随机补充本月其他日期支出（让"支出最多日"有区分度）
        addExpense(cur, 18, "餐饮", "12:30", "火锅", 8600)
        addExpense(cur, 18, "娱乐", "20:00", "电影票", 4000)
        addExpense(cur, 5, "购物", "15:10", "新衣服", 15800)
        addExpense(cur, 12, "医疗", "10:00", "感冒药", 2800)
        addExpense(cur, 25, "其他", "11:45", "快递", 1200)
        addIncome(cur, 15, "工资", "09:00", "月中津贴", 200000)

        // —— 上一月 ——
        val prev = monthOf(-1)
        addExpense(prev, 1, "居住", "09:05", "房租", 350000)
        addExpense(prev, 3, "餐饮", "12:20", "食堂午饭", 1500)
        addExpense(prev, 7, "交通", "08:30", "地铁", 400)
        addExpense(prev, 10, "购物", "16:00", "日用品", 4500)
        addExpense(prev, 14, "娱乐", "19:30", "游戏充值", 3000)
        addExpense(prev, 20, "餐饮", "18:40", "奶茶", 1600)
        addExpense(prev, 24, "医疗", "09:15", "挂号费", 5000)
        addExpense(prev, 27, "其他", "14:00", "维修", 6800)
        addIncome(prev, 15, "工资", "09:00", "7月工资", 800000)

        // —— 下一月：少量（开头几天） ——
        val next = monthOf(1)
        if (now.dayOfMonth <= 10) {
            addExpense(next, 1, "居住", "09:05", "房租", 350000)
        }
        addIncome(next, 10, "工资", "09:00", "预支工资", 800000)

        // —— 存款 ——
        addDep(Deposit(newId(), lid, now.minusDays(5), DepositKind.MONEY, "现金", "", 950000))
        addDep(Deposit(newId(), lid, now.minusMonths(1).minusDays(3), DepositKind.MONEY, "黄金", "100g", 200000))
        addDep(Deposit(newId(), lid, now.minusDays(20), DepositKind.GOODS, "纪念币", "收藏", 50000))
        addDep(Deposit(newId(), lid, now.minusMonths(2), DepositKind.GOODS, "图书", "精装版", 20000))

        // 天气：今天设为晴
        setWeather(lid, now, Weather.SUNNY)
    }
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

    private fun file(key: String): DocumentFile? = root.findFile(fileName(key))

    override fun read(key: String): String? {
        val f = file(key) ?: return null
        return cr.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() }
    }

    override fun write(key: String, value: String) {
        var f = file(key)
        if (f == null || !f.exists()) {
            f = root.createFile("application/json", fileName(key))
                ?: throw IOException("无法在存储目录创建文件：$key")
        }
        val os = cr.openOutputStream(f.uri, "wt")
            ?: throw IOException("无法写入存储目录：$key")
        os.use { it.write(value.toByteArray(Charsets.UTF_8)) }
    }

    override fun remove(key: String) {
        file(key)?.delete()
    }

    override fun keys(): Set<String> = root.listFiles().mapNotNull { f ->
        val n = f.name ?: return@mapNotNull null
        if (n.startsWith(FILE_PREFIX) && n.endsWith(".json"))
            n.removePrefix(FILE_PREFIX).removeSuffix(".json") else null
    }.toSet()

    private fun fileName(key: String) = "$FILE_PREFIX$key.json"
}