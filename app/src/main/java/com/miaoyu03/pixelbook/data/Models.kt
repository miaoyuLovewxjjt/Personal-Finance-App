package com.miaoyu03.pixelbook.data

import com.miaoyu03.pixelbook.BuildConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 应用签名信息（设置页展示 / PDF 页脚） */
object AppMeta {
    /** 版本号取自 build.gradle.kts 的 versionName（编译期写入 BuildConfig），无需手工同步 */
    val VERSION: String = "v" + BuildConfig.VERSION_NAME
    const val GIT_URL = "https://github.com/miaoyuLovewxjjt/Personal-Finance-App"
}

/** 金额一律以「分」(Long) 存储，避免浮点误差 */
typealias Cents = Long

/** 账户（一个账户下可有多个账本；每个账户 = 存储目录下一个同名文件夹） */
data class Account(
    val id: String,
    val name: String,
    val createdAt: String = LocalDate.now().toString(),
    val updatedAt: Long = 0L,   // 最近编辑/切换时间（epoch millis；0 = 旧数据无记录，按创建时间兜底）
    val birthday: String = "",  // 生日（自由文本，如 "1998-06-15"，角色卡展示；空 = 未设置）
    val note: String = "",      // 备注（角色卡展示，≤60 字）
    val avatar: Int = 1,        // 角色形象：0 = 短发男子，1 = 长发女子；新账户默认为女子
)

/** 资产账户（银行卡/支付宝/微信…，属于某个账户；最新余额由流水自动计算，不可手改） */
data class AssetAccount(
    val id: String,               // 唯一 id
    val category: String,         // 类别（如 建设银行/余额宝），≤10 字，用户自定义
    val sub: String,              // 子类别 = 卡号/账号/户名（如 6222****），≤20 字
    val role: String = "",        // 角色（如 工资卡），≤10 字，账户内不可重复
)

/** 账本在 UI 上的资产显示名：有角色直接显示角色；否则 类别+子类别 截断前 10 字 */
fun AssetAccount.label(): String =
    if (role.isNotEmpty()) role else Fmt.clip(category + sub, 10)

/** 下拉/详情用的完整名：角色(category · sub) 或 category · sub */
fun AssetAccount.fullLabel(): String {
    val catSub = category + if (sub.isNotEmpty()) " · $sub" else ""
    return if (role.isNotEmpty()) "$role（$catSub）" else catSub
}

data class Ledger(
    val id: String,
    val name: String,
    val coverColor: Int,        // 封面配色索引（0..5，对应 LedgerCover.colors 取色）
    val createdAt: String = LocalDate.now().toString(),
    val syncedMonths: Set<String> = emptySet(),   // 已执行过「一键同步」的月份 "2026-09"
    val font: String = "pixel", // 账本专属字体（见 LedgerFonts：pixel/cute/kaiti/songti）
    val file: String = "",      // 账本单文件文件名（账本名_创建时间戳.json，存于账户文件夹内）
    val accountId: String = "", // 所属账户 id（旧数据迁移后回填默认账户）
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
    val asset: String = "",     // 关联资产账户 id（"" = 未指定）
)

enum class TxDir { IN, OUT }

data class Deposit(
    val id: String,
    val ledgerId: String,
    val date: LocalDate,        // 入库时间
    val kind: DepositKind,      // 金钱类 / 非金钱类（旧版字段；现金=金钱类，其余=非金钱类）
    val name: String,           // 物品名称
    val note: String,           // 备注
    val value: Cents,           // 价值（分）
    val category: String = "",  // 资产类别（现金/黄金/股票/基金/珠宝首饰/其他/自定义；空=按 kind 推导）
)

enum class DepositKind(val label: String) {
    MONEY("金钱类"), GOODS("非金钱类")
}

/** 资产默认类别（旧数据兼容：金钱类→现金，非金钱类→其他） */
object DepositCats {
    const val CASH = "现金"
    const val GOLD = "黄金"
    const val STOCK = "股票"
    const val FUND = "基金"
    const val JEWELRY = "珠宝首饰"
    val list = listOf(CASH, GOLD, STOCK, FUND, JEWELRY, CATEGORY_OTHERS)
}

/** 我的物品（账户级清单：记录持有物及其持有成本；日均价格 = 买入价格 ÷ 已用天数，运行时算） */
data class Item(
    val id: String,
    val name: String,             // 物品名称
    val note: String = "",        // 备注
    val buyDate: LocalDate,       // 买入时间（用户填写）
    val createdAt: String = LocalDate.now().toString(),   // 录入时间（自动）
    val price: Cents = 0,         // 买入价格（分）
)

/**
 * 职业档案（账户级）：个人收入形象卡的数据源。
 * 工作日 workDays：1=周一 … 7=周日（默认周一~周五）。
 */
data class WorkProfile(
    val occupation: String = "",           // 职业名（角色卡展示、点按进本页）
    val monthlySalary: Cents = 0,          // 月薪（分）
    val workDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val workStart: String = "09:00",       // 上班时间 HH:mm
    val workEnd: String = "18:00",         // 下班时间 HH:mm
    val commute: String = "",              // 通勤路线
)

/** 任务条目（账户级；date = 任务归属日期，今天/历史都可增删改） */
data class TaskItem(
    val id: String,
    val text: String,                          // 任务标题（≤15 字）
    val note: String = "",                     // 备注（≤60 字，小字浅色展示）
    val done: Boolean = false,
    val date: String = LocalDate.now().toString(),   // 任务日期 yyyy-MM-dd
    val created: String = LocalDate.now().toString(),
)

/** 兜底类别（固定，不可删除/编辑） */
const val CATEGORY_OTHERS = "其他"

/* ============ 输入长度硬性限制（全 App 统一） ============ */
const val MAX_AMOUNT_INT = 9                 // 金额：整数最多 9 位
const val MAX_AMOUNT_FRAC = 2                // 金额：小数最多 2 位
const val MAX_NOTE_LEN = 30                  // 备注：最多 30 个汉字（按字符数）
const val MAX_TX_NAME_LEN = 25               // 收入/支出 名称：最多 25 个汉字
const val MAX_CAT_LEN = 10                   // 自定义类别：最多 10 个汉字
const val MAX_ROLE_LEN = 10                  // 资产账户角色：最多 10 个汉字，不可重复
const val MAX_ASSET_SUB_LEN = 20             // 资产账户子类别（卡号/账号）：最多 20 字
const val MAX_ACCOUNT_NAME_LEN = 30          // 账户名：最多 30 字
const val MAX_ACCOUNT_NOTE_LEN = 60          // 账户备注：最多 60 字（角色卡展示）
const val MAX_ITEM_NAME_LEN = 25             // 物品名称：最多 25 字
const val MAX_OCCUPATION_LEN = 10            // 职业名：最多 10 字
const val MAX_COMMUTE_LEN = 30               // 通勤路线：最多 30 字
const val MAX_TASK_LEN = 15                  // 任务标题：最多 15 字
const val MAX_TASK_NOTE_LEN = 60             // 任务备注：最多 60 字
const val MAX_LEDGER_PER_ACCOUNT = 60        // 每个账户下最多账本数

/** 旧数据迁移时创建的默认账户名 */
const val DEFAULT_ACCOUNT_NAME = "默认账户"

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
    private val intFmt = java.text.DecimalFormat("#,##0")

    /** 整数部分上限 10^MAX_AMOUNT_INT - 1（9 位 → 999999999） */
    private val MAX_INT_PART: Long =
        (1..MAX_AMOUNT_INT).fold(1L) { acc, _ -> acc * 10 } - 1

    /** 分 → "1,234.00"（整数分位拆分，不经过 Double，避免浮点误差） */
    fun money(cents: Cents): String {
        val neg = cents < 0
        val abs = if (neg) -cents else cents
        val intPart = intFmt.format(abs / 100)
        val frac = (abs % 100).toString().padStart(2, '0')
        return "${if (neg) "-" else ""}$intPart.$frac"
    }

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

    /**
     * 输入字符串 → 分；失败返回 null。容忍 "9,000"、"9000.5"、"¥100" 等写法。
     *
     * 全程整数运算：早期版本走 Double（`(v * 100).toLong()`），
     * 因二进制无法精确表示 0.29 这类小数，`0.29 * 100 = 28.999…` 截断成 28 分，
     * 导致录入金额静默少 1 分。这里按字符解析整数/小数部分，结果精确。
     */
    fun parseCents(input: String): Cents? {
        val t = input.trim().replace(",", "").replace("¥", "").replace("￥", "").trim()
        if (t.isEmpty()) return null
        var intPart = 0L
        var fracPart = 0L
        var fracDigits = 0
        var dotSeen = false
        var digits = 0
        for (ch in t) {
            when {
                ch.isDigit() -> {
                    val d = ch - '0'
                    if (dotSeen) {
                        if (fracDigits >= MAX_AMOUNT_FRAC) return null  // 小数超 2 位
                        fracPart = fracPart * 10 + d
                        fracDigits++
                    } else {
                        if (intPart > MAX_INT_PART / 10) return null     // 整数超 9 位
                        intPart = intPart * 10 + d
                    }
                    digits++
                }
                ch == '.' -> {
                    if (dotSeen) return null                             // 多个小数点
                    dotSeen = true
                }
                else -> return null                                      // 负号/字母/空白等
            }
        }
        if (digits == 0) return null
        while (fracDigits < MAX_AMOUNT_FRAC) {                           // "0.5" → 50 分
            fracPart *= 10
            fracDigits++
        }
        return intPart * 100 + fracPart
    }

    /** 按「字符数」截断（中文 1 字 = 1，emoji 按码点计，不劈断代理对） */
    fun clip(s: String, max: Int): String {
        if (max <= 0 || s.codePointCount(0, s.length) <= max) return s
        val end = runCatching { s.offsetByCodePoints(0, max) }.getOrElse { s.length }
        return s.substring(0, end)
    }

    /**
     * 金额输入实时清洗：只保留数字与第一个小数点；
     * 整数部分最多 9 位、小数部分最多 2 位（超出部分自动丢弃）。
     */
    fun cleanAmountInput(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder()
        var dotSeen = false
        var intDigits = 0
        var fracDigits = 0
        for (ch in raw) {
            when {
                ch.isDigit() -> {
                    if (dotSeen) {
                        if (fracDigits < MAX_AMOUNT_FRAC) { sb.append(ch); fracDigits++ }
                    } else if (intDigits < MAX_AMOUNT_INT) { sb.append(ch); intDigits++ }
                }
                ch == '.' -> if (!dotSeen) { sb.append('.'); dotSeen = true }
            }
        }
        return sb.toString()
    }
}
