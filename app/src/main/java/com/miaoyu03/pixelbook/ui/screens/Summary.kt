package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.CATEGORY_OTHERS
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.ui.DonutSeg
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelBarChart
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDonut
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIcons
import com.miaoyu03.pixelbook.ui.PixelOption
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelSectionTitle
import com.miaoyu03.pixelbook.ui.PxText
import java.time.LocalDate
import java.time.YearMonth

/* ================================================================
 * 统计辅助（月度 / 年度）
 * ================================================================ */

private data class PeriodStats(
    val income: Long,
    val expense: Long,
    val maxExpenseDay: LocalDate?,
    val maxIncomeDay: LocalDate?,
    val maxExpenseMonth: String?,
    val maxIncomeMonth: String?,
    val maxExpense: Tx?,   // 期间最大单笔支出（含明细）
    val maxIncome: Tx?,    // 期间最大单笔收入（含明细）
)

private fun statsOf(txs: List<Tx>, ym: String): PeriodStats {
    val list = txs.filter { Fmt.ymKey(it.date) == ym }
    val income = list.filter { it.dir == TxDir.IN }.sumOf { it.amount }
    val expense = list.filter { it.dir == TxDir.OUT }.sumOf { it.amount }
    return PeriodStats(
        income = income,
        expense = expense,
        maxExpenseDay = maxDay(list, TxDir.OUT),
        maxIncomeDay = maxDay(list, TxDir.IN),
        maxExpenseMonth = null,
        maxIncomeMonth = null,
        maxExpense = list.filter { it.dir == TxDir.OUT }.maxByOrNull { it.amount },
        maxIncome = list.filter { it.dir == TxDir.IN }.maxByOrNull { it.amount },
    )
}

private fun statsOfYear(txs: List<Tx>, year: Int): PeriodStats {
    val list = txs.filter { it.date.year == year }
    val income = list.filter { it.dir == TxDir.IN }.sumOf { it.amount }
    val expense = list.filter { it.dir == TxDir.OUT }.sumOf { it.amount }
    val outByMonth = list.filter { it.dir == TxDir.OUT }.groupBy { Fmt.ymKey(it.date) }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
    val inByMonth = list.filter { it.dir == TxDir.IN }.groupBy { Fmt.ymKey(it.date) }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
    return PeriodStats(
        income = income,
        expense = expense,
        maxExpenseDay = null,
        maxIncomeDay = null,
        maxExpenseMonth = outByMonth.maxByOrNull { it.value }?.key,
        maxIncomeMonth = inByMonth.maxByOrNull { it.value }?.key,
        maxExpense = list.filter { it.dir == TxDir.OUT }.maxByOrNull { it.amount },
        maxIncome = list.filter { it.dir == TxDir.IN }.maxByOrNull { it.amount },
    )
}

private fun maxDay(list: List<Tx>, dir: TxDir): LocalDate? =
    list.filter { it.dir == dir }
        .groupBy { it.date }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .maxByOrNull { it.value }?.key

/** 金额整数字符串（整元显示无小数） */
private fun moneyInt(cents: Long): String {
    val yuan = cents / 100.0
    return if (cents % 100 == 0L) yuan.toLong().toString() else Fmt.money(cents)
}

/* ================================================================
 * 五、月度总结页
 * ================================================================ */

@Composable
fun MonthScreen(
    store: Store,
    ledgerId: String,
    ym: String,
    onBack: () -> Unit,
) {
    var ym by remember { mutableStateOf(ym) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val all = remember(tick) { store.txList(ledgerId) }
    val ymObj = YearMonth.parse(ym)
    val stats = remember(all, ym) { statsOf(all, ym) }
    val synced = remember(tick) { store.isSynced(ledgerId, ym) }
    // 本月收入 / 花销按分类汇总（图表数据源，降序）
    val inByCat = remember(all, ym) {
        all.filter { it.dir == TxDir.IN && Fmt.ymKey(it.date) == ym }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val outByCat = remember(all, ym) {
        all.filter { it.dir == TxDir.OUT && Fmt.ymKey(it.date) == ym }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // 可选月份：有数据的月份（降序）
    val months = remember(all) {
        val set = all.map { Fmt.ymKey(it.date) }.toMutableSet().apply { add(ym); add(Fmt.ymKey(LocalDate.now())) }
        set.sortedDescending()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "月度总结", onBack = onBack)

        if (confirmReset) {
            PixelConfirm(
                title = "重置本月归档",
                message = "将删除本月已归档的「攒钱」存款记录并清除归档标记，之后可以重新一键同步。",
                confirmText = "重置",
                onConfirm = {
                    if (store.resetArchive(ledgerId, ym)) {
                        tick++
                        store.toast("已重置本月归档，可重新一键同步")
                    } else {
                        store.toast("重置失败：存款删除未成功，请检查存储后重试")
                    }
                    confirmReset = false
                },
                onDismiss = { confirmReset = false },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            // 月份下拉（默认收缩）
            PixelDropdown(
                label = "月份",
                options = months.map { PixelOption("${YearMonth.parse(it).year}年${YearMonth.parse(it).monthValue}月") },
                selected = "${ymObj.year}年${ymObj.monthValue}月",
                onSelect = { s ->
                    ym = s.toYmY()
                },
            )
            Spacer(Modifier.height(14.dp))

            // 一键同步 / 已归档（可重置）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = if (synced) "本月已归档 ✓" else "一键同步",
                    icon = "coinPile",
                    onClick = {
                        val balance = stats.income - stats.expense
                        if (balance <= 0) {
                            store.toast("本月结余不足，没有可归档的攒钱")
                            return@PixelButton
                        }
                        if (store.archiveMonth(ledgerId, ym, balance)) {
                            store.markSynced(ledgerId, ym)
                            tick++
                            store.toast("已把结余 ${moneyInt(balance)} 元归档到存款明细")
                        } else {
                            store.toast("归档失败：存款写入未成功，请检查存储后重试")
                        }
                    },
                    enabled = !synced,
                    bg = Px.Grass,
                    modifier = Modifier.weight(1f),
                )
                if (synced) {
                    PixelButton(
                        text = "重置",
                        icon = "trash",
                        onClick = { confirmReset = true },
                        bg = Px.Clay,
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // 月度总览：总支出/总收入/总结余 横排（文字左、数字右、无底色）
            //         支出最多日/收入最多日 保留底色；本月最大收入/花销 横排带明细
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 12.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatLine("总支出", Fmt.yen(stats.expense), Px.WoodDark)
                    StatLine("总收入", Fmt.yen(stats.income), Px.GrassDark)
                    StatLine(
                        "总结余",
                        Fmt.yen(stats.income - stats.expense),
                        if (stats.income - stats.expense >= 0) Px.GrassDark else Px.ClayDark,
                    )
                    PanelDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox(
                            "支出最多日", stats.maxExpenseDay?.let { Fmt.dayOfMonth(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            "收入最多日", stats.maxIncomeDay?.let { Fmt.dayOfMonth(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PanelDivider()
                    MaxTxLine("本月最大收入：", stats.maxIncome, Px.GrassDark)
                    MaxTxLine("本月最大花销：", stats.maxExpense, Px.WoodDark)
                }
            }
            Spacer(Modifier.height(14.dp))

            // 本月收入 / 花销占比环形图（点击扇形/图例查看金额与占比）
            CategoryDonutCard("本月收入占比", "总收入", Fmt.yen(stats.income), Px.GrassDark, inByCat)
            Spacer(Modifier.height(14.dp))
            CategoryDonutCard("本月花销占比", "总花销", Fmt.yen(stats.expense), Px.WoodDark, outByCat)
            Spacer(Modifier.height(16.dp))
            PxText("数据每日自动同步生成，不可编辑", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** "2026年9月" → "2026-09"（月度页下拉选择用） */
private fun String.toYmY(): String {
    // 完整形式 "2026年9月"
    val yS = substringBefore("年")
    val mS = substringAfter("年").removeSuffix("月")
    return "%04d-%02d".format(yS.toInt(), mS.toInt())
}

/* ================================================================
 * 六、年度总结页
 * ================================================================ */

@Composable
fun YearScreen(
    store: Store,
    ledgerId: String,
    initialYear: Int,
    onBack: () -> Unit,
    onMonth: (String) -> Unit,
) {
    var year by remember { mutableStateOf(initialYear) }
    var tick by remember { mutableIntStateOf(0) }
    // 月份导航折叠状态（默认收缩）
    var monthsOpen by remember { mutableStateOf(false) }

    val all = remember(tick) { store.txList(ledgerId) }
    val stats = remember(all, year) { statsOfYear(all, year) }
    // 本年收入 / 花销按分类汇总（图表数据源，降序）
    val inByCatY = remember(all, year) {
        all.filter { it.dir == TxDir.IN && it.date.year == year }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val outByCatY = remember(all, year) {
        all.filter { it.dir == TxDir.OUT && it.date.year == year }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    val years = remember(all) {
        (all.map { it.date.year } + LocalDate.now().year).distinct().sortedDescending()
    }
    // 该年有数据的月份（用于月份导航高亮）
    val dataMonths = remember(all, year) {
        all.filter { it.date.year == year }.map { it.date.monthValue }.toSet()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "年度总结", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PixelDropdown(
                label = "年份",
                options = years.map { PixelOption("${it}年") },
                selected = "${year}年",
                onSelect = { s -> year = s.removeSuffix("年").toInt() },
            )
            Spacer(Modifier.height(14.dp))

            // 年度总览：总支出/总收入/总结余 横排（文字左、数字右、无底色）
            //         支出最多月/收入最多月 保留底色（可跳转）；最大收入/花销 横排带明细
            val expMonth = stats.maxExpenseMonth?.let { YearMonth.parse(it).monthValue }
            val incMonth = stats.maxIncomeMonth?.let { YearMonth.parse(it).monthValue }
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 12.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatLine("总支出", Fmt.yen(stats.expense), Px.WoodDark)
                    StatLine("总收入", Fmt.yen(stats.income), Px.GrassDark)
                    StatLine(
                        "总结余",
                        Fmt.yen(stats.income - stats.expense),
                        if (stats.income - stats.expense >= 0) Px.GrassDark else Px.ClayDark,
                    )
                    PanelDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox(
                            "支出最多月", expMonth?.let { "${it}月" } ?: "—",
                            clickable = stats.maxExpenseMonth != null,
                            onClick = { stats.maxExpenseMonth?.let(onMonth) },
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            "收入最多月", incMonth?.let { "${it}月" } ?: "—",
                            clickable = stats.maxIncomeMonth != null,
                            onClick = { stats.maxIncomeMonth?.let(onMonth) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PanelDivider()
                    MaxTxLine("本年度最大收入：", stats.maxIncome, Px.GrassDark)
                    MaxTxLine("本年度最大花销：", stats.maxExpense, Px.WoodDark)
                }
            }
            Spacer(Modifier.height(14.dp))

            // 本年收入 / 花销占比环形图 + 按月柱状图（点击扇形/柱/图例查看金额）
            CategoryDonutCard("本年收入占比", "总收入", Fmt.yen(stats.income), Px.GrassDark, inByCatY)
            Spacer(Modifier.height(14.dp))
            CategoryDonutCard("本年花销占比", "总花销", Fmt.yen(stats.expense), Px.WoodDark, outByCatY)
            Spacer(Modifier.height(14.dp))
            MonthBarCard(all = all, year = year)
            Spacer(Modifier.height(14.dp))

            // 月份导航卡：点击折叠箭头展开 12 个月，点某月 → 该月月度总结
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 12.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { monthsOpen = !monthsOpen }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelIcon("calendar", size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        PxText("${year}年 月份", size = 15.sp, color = Px.Brown)
                        Spacer(Modifier.weight(1f))
                        PxText("点击进入各月总结", size = 11.sp, color = Px.GrayText)
                        Spacer(Modifier.width(6.dp))
                        PixelIcon(if (monthsOpen) "chevronD" else "chevronR", size = 12.dp)
                    }
                    if (monthsOpen) {
                        Spacer(Modifier.height(10.dp))
                        (1..12).chunked(4).forEach { rowMonths ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowMonths.forEach { m ->
                                    val key = "%04d-%02d".format(year, m)
                                    val hasData = m in dataMonths
                                    val isCurMonth = m == LocalDate.now().monthValue && year == LocalDate.now().year
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .padding(horizontal = 3.dp, vertical = 3.dp)
                                            .background(if (hasData) Px.Grass.copy(alpha = 0.35f) else Px.CreamBg)
                                            .drawBehind {
                                                val stroke = 2.dp.toPx()
                                                drawRect(
                                                    if (isCurMonth) Px.Clay else Px.Brown,
                                                    topLeft = Offset(stroke / 2, stroke / 2),
                                                    size = Size(size.width - stroke, size.height - stroke),
                                                    style = Stroke(width = stroke)
                                                )
                                            }
                                            .clickable { onMonth(key) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PxText(
                                            "${m}月",
                                            size = 12.sp,
                                            color = if (hasData) Px.GrassDark else if (isCurMonth) Px.ClayDark else Px.GrayText,
                                        )
                                    }
                                }
                                if (rowMonths.size < 4) {
                                    repeat(4 - rowMonths.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PxText("数据每日自动同步生成，不可编辑", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
    }
}

/* ---------------- 统计组件：横排行 / 底色块 / 最大明细行 ---------------- */

/** 面板内分隔线 */
@Composable
private fun PanelDivider() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .background(Px.CreamDark),
    )
    Spacer(Modifier.height(8.dp))
}

/** 横排行：文字在左，数字在右，独占一行（无底色） */
@Composable
private fun StatLine(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.weight(1f))
        PxText(value, size = 14.sp, color = color)
    }
}

/** 底色标识块：最多日 / 最多月（可跳转时整块可点） */
@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    clickable: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier.then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (clickable) Px.Grass.copy(alpha = 0.35f) else Px.CreamBg)
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PxText(label, size = 11.sp, color = Px.GrayText, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PxText(value, size = 14.sp, color = if (clickable) Px.GrassDark else Px.Brown, align = TextAlign.Center)
        }
    }
}

/** 最大收入/花销行：文字在左，右侧 日期 + 分类·名称 + 金额（无正负号） */
@Composable
private fun MaxTxLine(label: String, tx: Tx?, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.weight(1f))
        if (tx == null) {
            PxText("—", size = 13.sp, color = Px.GrayText)
        } else {
            // 日期（如 9.2）+ 分类 · 名称
            PxText(
                "${tx.date.monthValue}.${tx.date.dayOfMonth}  ${tx.category}" +
                    if (tx.name.isNotEmpty()) " · ${tx.name}" else "",
                size = 11.sp,
                color = Px.Brown,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            PxText(Fmt.yen(tx.amount), size = 13.sp, color = color)
        }
    }
}
/* ---------------- 图表卡：环形占比图 / 月度双柱图 ---------------- */

/**
 * 环形占比图卡：中心显示总金额大字；点击扇形 → 显示「分类 + 金额 + 占比」；
 * 点击图例行 → 显隐该分类。小分类自动合并为「其他」避免环过碎。
 */
@Composable
private fun CategoryDonutCard(
    title: String,
    centerLabel: String,
    centerValue: String,
    centerColor: Color,
    data: List<Pair<String, Long>>,   // 分类 → 金额（分），降序
) {
    var selCat by remember { mutableStateOf<String?>(null) }
    var hidden by remember { mutableStateOf(setOf<String>()) }

    val merged = mergeSmall(data)
    val shown = merged.filter { it.first !in hidden }
    val total = shown.sumOf { it.second }.coerceAtLeast(1)
    val segs = shown.map { (c, v) ->
        DonutSeg(c, PixelIcons.colorOfCategory(c), v.toFloat() / total)
    }
    val sel = shown.firstOrNull { it.first == selCat }

    PixelPanel(
        modifier = Modifier.fillMaxWidth(),
        bg = Px.Cream,
        contentPadding = 14.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PixelSectionTitle(title, icon = "statChart")
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PixelDonut(
                    segments = segs,
                    canvasSize = 150.dp,
                    onSegment = { idx -> selCat = shown.getOrNull(idx)?.first },
                )
                // 中心大字：标签 + 总金额
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PxText(centerLabel, size = 11.sp, color = Px.GrayText)
                    Spacer(Modifier.height(2.dp))
                    PxText(centerValue, size = 15.sp, color = centerColor)
                }
            }
            Spacer(Modifier.height(6.dp))
            // 点击反馈：分类 + 金额 + 占比
            PxText(
                if (sel != null) {
                    "「${sel.first}」 ${Fmt.yen(sel.second)} · ${pctOf(sel.second, total)}%"
                } else {
                    "点击环形图或图例查看具体金额"
                },
                size = 12.sp,
                color = Px.WoodDark,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // 图例：点击显隐对应分类（含已隐藏项，置灰）
            merged.forEach { (c, v) ->
                val isHidden = c in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hidden = if (c in hidden) hidden - c else hidden + c
                            if (selCat == c) selCat = null
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(PixelIcons.iconOfCategory(c), size = 15.dp)
                    Spacer(Modifier.width(5.dp))
                    PxText(c, size = 12.sp, color = if (isHidden) Px.GrayText else Px.Brown)
                    Spacer(Modifier.weight(1f))
                    // 隐藏的分类当前不可见，占比显示 0%
                    PxText(if (isHidden) "0%" else "${pctOf(v, total)}%", size = 12.sp, color = Px.GrayText)
                }
            }
        }
    }
}

/** 小分类合并：Top(topN-1) + 「其他」，其余归入「其他」；总量为 0 时原样返回 */
private fun mergeSmall(data: List<Pair<String, Long>>, topN: Int = 5): List<Pair<String, Long>> {
    if (data.size <= topN || data.sumOf { it.second } <= 0) return data
    val top = data.take(topN - 1)
    val otherSum = data.drop(topN - 1).sumOf { it.second }
    return top + (CATEGORY_OTHERS to otherSum)
}

private fun pctOf(v: Long, total: Long): Int =
    if (total > 0) (v.toFloat() / total * 100 + 0.5f).toInt() else 0

/**
 * 按月收入/花销双柱图卡：12 个月两根柱（草绿收入 / 陶土橘花销）；
 * 点击柱 → 显示该月金额与占本年比例；图例点击切换收入/花销显隐。
 */
@Composable
private fun MonthBarCard(all: List<Tx>, year: Int) {
    var showIn by remember { mutableStateOf(true) }
    var showOut by remember { mutableStateOf(true) }
    var sel by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }   // (月份 1..12, isIncome)

    val yearList = all.filter { it.date.year == year }
    val monthsIn = (1..12).map { m ->
        yearList.filter { it.date.monthValue == m && it.dir == TxDir.IN }.sumOf { it.amount }
    }
    val monthsOut = (1..12).map { m ->
        yearList.filter { it.date.monthValue == m && it.dir == TxDir.OUT }.sumOf { it.amount }
    }
    val yearIn = monthsIn.sum()
    val yearOut = monthsOut.sum()

    PixelPanel(
        modifier = Modifier.fillMaxWidth(),
        bg = Px.Cream,
        contentPadding = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PixelSectionTitle("按月收入 / 花销", icon = "statChart")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendChip("收入", Px.ChartIn, showIn) { showIn = !showIn }
                Spacer(Modifier.width(12.dp))
                LegendChip("花销", Px.ChartOut, showOut) { showOut = !showOut }
                Spacer(Modifier.weight(1f))
                PxText("${year}年", size = 11.sp, color = Px.GrayText)
            }
            Spacer(Modifier.height(8.dp))
            PixelBarChart(
                income = monthsIn,
                expense = monthsOut,
                showIncome = showIn,
                showExpense = showOut,
                onBar = { m, isIn -> sel = m to isIn },
            )
            Spacer(Modifier.height(6.dp))
            PxText(
                sel?.let { (m, isIn) ->
                    val v = (if (isIn) monthsIn else monthsOut)[m - 1]
                    val yTotal = if (isIn) yearIn else yearOut
                    val tag = if (isIn) "收入" else "花销"
                    val yTag = if (isIn) "本年收入" else "本年花销"
                    "${m}月$tag ${Fmt.yen(v)} · 占$yTag ${pctOf(v, yTotal)}%"
                } ?: "点击柱形查看该月收入 / 花销",
                size = 12.sp,
                color = Px.WoodDark,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 图例小色块：点击切换显隐 */
@Composable
private fun LegendChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(if (active) color else Px.CreamDark)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRect(
                        Px.Brown,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                },
        )
        Spacer(Modifier.width(4.dp))
        PxText(label, size = 11.sp, color = if (active) Px.Brown else Px.GrayText)
    }
}
