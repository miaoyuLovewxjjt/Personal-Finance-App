package com.miaoyu03.pixelbook.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 应用签名信息（设置页展示 / PDF 页脚） */
object AppMeta {
    const val VERSION = "v1.0"                        // 与 git tag 保持一致
    const val GIT_URL = "https://github.com/miaoyuLovewxjjt/Personal-Finance-App"
}

/** 金额一律以「分」(Long) 存储，避免浮点误差 */
typealias Cents = Long

data class Ledger(
    val id: String,
    val name: String,
    val coverColor: Int,        // 封面配色索引（0..5，对应 LedgerCover.colors 取色）
    val createdAt: String = LocalDate.now().toString(),
    val syncedMonths: Set<String> = emptySet(),   // 已执行过「一键同步」的月份 "2026-09"
    val font: String = "pixel", // 账本专属字体（见 LedgerFonts：pixel/cute/kaiti/songti）
    val file: String = "",      // 账本单文件文件名（账本名_创建时间戳.json，外部存储用）
)

data class Tx(
    val id: String,
    val ledgerId: String,
    val date: LocalDate,        // 记账日期
    val time: String,           // "HH:mm"
    val dir: TxDir,             // 收入 / 支出
    val category: String,       // 分类名（见 Categories）
    val amount: Cents,          // 金额（分）
    val name: String,           // 具体名称
    val note: String,           // 备注
)

enum class TxDir { IN, OUT }

data class Deposit(
    val id: String,
    val ledgerId: String,
    val date: LocalDate,        // 入库时间
    val kind: DepositKind,      // 金钱类 / 非金钱类
    val name: String,           // 物品名称
    val note: String,           // 备注
    val value: Cents,           // 价值（分）
)

enum class DepositKind(val label: String) {
    MONEY("金钱类"), GOODS("非金钱类")
}

/** 兜底类别（固定，不可删除/编辑） */
const val CATEGORY_OTHERS = "其他"

/** 支出分类（图标/颜色/名称） */
object ExpenseCats {
    val list = listOf("餐饮", "交通", "购物", "娱乐", "居住", "医疗", CATEGORY_OTHERS)
}

/** 收入分类 */
object IncomeCats {
    val list = listOf("工资", "理财", "红包", CATEGORY_OTHERS)
}

/** 天气：按 天 记录（每个账本独立） */
enum class Weather(val label: String) {
    SUNNY("晴"), CLOUDY("多云"), RAIN("雨"), SNOW("雪")
}

object Fmt {
    private val num = java.text.DecimalFormat("#,##0.00")

    /** 分 → "1,234.00" */
    fun money(cents: Cents): String = num.format(cents / 100.0)

    /** 分 → "¥12,345.00" */
    fun yen(cents: Cents): String = "¥${money(cents)}"

    fun date(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

    fun dateYmd(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    fun weekday(d: LocalDate): String = when (d.dayOfWeek.value) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }

    fun dateFull(d: LocalDate): String = "${date(d)} ${weekday(d)}"

    /** 天 → "9月18日"；月 → "9月" */
    fun dayOfMonth(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日"
    fun monthOf(d: LocalDate): String = "${d.monthValue}月"

    /** "2026-09" 形式的月份键 */
    fun ymKey(d: LocalDate): String = "%04d-%02d".format(d.year, d.monthValue)
    fun yearKey(d: LocalDate): String = d.year.toString()

    /** 输入字符串 → 分；失败返回 null。容忍 "9,000"、"9000.5" 等写法 */
    fun parseCents(input: String): Cents? {
        val t = input.trim().replace(",", "").replace("¥", "").replace("￥", "")
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return null
        if (v < 0) return null
        return (v * 100).toLong()
    }
}