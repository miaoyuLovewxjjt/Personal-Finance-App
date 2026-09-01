package com.miaoyu03.pixelbook.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 本地数据层：SharedPreferences 存 JSON。
 * 每个账本独立 key：txs.<id> / dps.<id> / wx.<id>
 */
class Store(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("pixelbook_data", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LEDGERS = "ledgers"
        private const val KEY_SEEDED = "demo_seeded_v1"
    }

    /** 像素风格 toast（组件内用 store.toast 代替 Context） */
    fun toast(msg: String) {
        android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    /* ================= 账本 ================= */

    fun ledgers(): List<Ledger> {
        val arr = prefs.getString(KEY_LEDGERS, null) ?: return emptyList()
        return parseLedgers(arr)
    }

    fun ledger(id: String): Ledger? = ledgers().find { it.id == id }

    fun addLedger(name: String, coverIdx: Int): Ledger {
        val l = Ledger(id = newId(), name = name.trim(), coverColor = coverIdx)
        val list = ledgers().toMutableList().apply { add(l) }
        prefs.edit().putString(KEY_LEDGERS, ledgersToJson(list)).apply()
        return l
    }

    fun renameLedger(id: String, name: String) {
        val list = ledgers().map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }
        prefs.edit().putString(KEY_LEDGERS, ledgersToJson(list)).apply()
    }

    fun deleteLedger(id: String) {
        val list = ledgers().filterNot { it.id == id }
        prefs.edit()
            .putString(KEY_LEDGERS, ledgersToJson(list))
            .remove(txKey(id)).remove(dpKey(id)).remove(wxKey(id))
            .apply()
    }

    /* ================= 流水 ================= */

    fun txList(ledgerId: String): List<Tx> {
        val arr = prefs.getString(txKey(ledgerId), null) ?: return emptyList()
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
        val arr = prefs.getString(dpKey(ledgerId), null) ?: return emptyList()
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
        val map = prefs.getString(wxKey(ledgerId), null) ?: return null
        val name = JSONObject(map).optString(date.toString(), "")
        return Weather.entries.find { it.name == name }
    }

    fun setWeather(ledgerId: String, date: LocalDate, w: Weather) {
        val map = prefs.getString(wxKey(ledgerId), null)
        val obj = if (map.isNullOrEmpty()) JSONObject() else JSONObject(map)
        obj.put(date.toString(), w.name)
        prefs.edit().putString(wxKey(ledgerId), obj.toString()).apply()
    }

    /* ================= 一键同步标记 ================= */

    fun isSynced(ledgerId: String, ym: String): Boolean =
        ledger(ledgerId)?.syncedMonths?.contains(ym) == true

    fun markSynced(ledgerId: String, ym: String) {
        val list = ledgers().map {
            if (it.id == ledgerId) it.copy(syncedMonths = it.syncedMonths + ym) else it
        }
        prefs.edit().putString(KEY_LEDGERS, ledgersToJson(list)).apply()
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
            out.add(Ledger(o.getString("id"), o.getString("name"), o.optInt("cover", 0), syncedMonths = synced))
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
        prefs.edit().putString(txKey(ledgerId), arr.toString()).apply()
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
        prefs.edit().putString(dpKey(ledgerId), arr.toString()).apply()
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
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (ledgers().isNotEmpty()) { prefs.edit().putBoolean(KEY_SEEDED, true).apply(); return }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
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