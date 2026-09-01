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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.ui.DonutSeg
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDonut
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
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
    )
}

private fun maxDay(list: List<Tx>, dir: TxDir): LocalDate? =
    list.filter { it.dir == dir }
        .groupBy { it.date }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .maxByOrNull { it.value }?.key

/** 分类占比（支出侧） */
private fun categoryRatio(txs: List<Tx>): List<Pair<String, Long>> {
    val out = txs.filter { it.dir == TxDir.OUT }
    val byCat = out.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
    return byCat.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

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
    var selSeg by remember { mutableStateOf(-1) }

    val all = remember(tick) { store.txList(ledgerId) }
    val ymObj = YearMonth.parse(ym)
    val stats = remember(all, ym) { statsOf(all, ym) }
    val cats = remember(all, ym) { categoryRatio(all.filter { Fmt.ymKey(it.date) == ym }) }
    val catTotal = cats.sumOf { it.second }.coerceAtLeast(1)
    val segs = cats.map { (c, v) -> DonutSeg(c, com.miaoyu03.pixelbook.ui.PixelIcons.colorOfCategory(c), v.toFloat() / catTotal) }
    val synced = remember(tick) { store.isSynced(ledgerId, ym) }

    // 可选月份：有数据的月份（降序）
    val months = remember(all) {
        val set = all.map { Fmt.ymKey(it.date) }.toMutableSet().apply { add(ym); add(Fmt.ymKey(LocalDate.now())) }
        set.sortedDescending()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "月度总结", onBack = onBack)

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
                    selSeg = -1
                },
            )
            Spacer(Modifier.height(14.dp))

            // 一键同步
            PixelButton(
                text = if (synced) "本月已归档 ✓" else "一键同步",
                icon = "coinPile",
                onClick = {
                    val balance = stats.income - stats.expense
                    if (balance <= 0) {
                        store.toast("本月结余不足，没有可归档的攒钱")
                        return@PixelButton
                    }
                    store.addDep(
                        com.miaoyu03.pixelbook.data.Deposit(
                            id = "d${System.currentTimeMillis()}",
                            ledgerId = ledgerId,
                            date = LocalDate.now(),
                            kind = com.miaoyu03.pixelbook.data.DepositKind.MONEY,
                            name = "攒钱",
                            note = "${ymObj.year}.${ymObj.monthValue} 月收入已归档 ${moneyInt(balance)} 元",
                            value = balance,
                        )
                    )
                    store.markSynced(ledgerId, ym)
                    tick++; selSeg = -1
                    store.toast("已把结余 ${moneyInt(balance)} 元归档到存款明细")
                },
                enabled = !synced,
                bg = Px.Grass,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            // 月度总览 2x2
            StatGrid(
                cells = listOf(
                    StatCell("总支出", Fmt.yen(stats.expense), "expense"),
                    StatCell("总收入", Fmt.yen(stats.income), "income"),
                    StatCell("支出最多日", stats.maxExpenseDay?.let { Fmt.dayOfMonth(it) } ?: "—", "calendar"),
                    StatCell("收入最多日", stats.maxIncomeDay?.let { Fmt.dayOfMonth(it) } ?: "—", "calendarGold"),
                ),
            )
            Spacer(Modifier.height(14.dp))

            // 各类花销占比
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 14.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PixelSectionTitle("各类花销占比", icon = "burger")
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PixelDonut(
                            segments = segs,
                            canvasSize = 150.dp,
                            onSegment = { selSeg = it },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 点击占比后显示金额
                    if (selSeg in segs.indices) {
                        val s = segs[selSeg]
                        val amt = cats.getOrNull(selSeg)?.second ?: 0
                        PxText(
                            "${s.name} 共 ${Fmt.yen(amt)}",
                            size = 13.sp,
                            color = Px.WoodDark,
                            align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PxText("点击环形图查看各类具体金额", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    // 分类图例
                    segs.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelIcon(com.miaoyu03.pixelbook.ui.PixelIcons.iconOfCategory(s.name), size = 16.dp)
                            Spacer(Modifier.width(5.dp))
                            PxText(s.name, size = 12.sp)
                            Spacer(Modifier.weight(1f))
                            PxText("%d%%".format((s.ratio * 100).toInt()), size = 12.sp, color = Px.GrayText)
                        }
                    }
                }
            }
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
    var selSeg by remember { mutableStateOf(-1) }
    // 月份导航折叠状态（默认收缩）
    var monthsOpen by remember { mutableStateOf(false) }

    val all = remember(tick) { store.txList(ledgerId) }
    val stats = remember(all, year) { statsOfYear(all, year) }
    val cats = remember(all, year) { categoryRatio(all.filter { it.date.year == year }) }
    val catTotal = cats.sumOf { it.second }.coerceAtLeast(1)
    val segs = cats.map { (c, v) -> DonutSeg(c, com.miaoyu03.pixelbook.ui.PixelIcons.colorOfCategory(c), v.toFloat() / catTotal) }

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
                onSelect = { s -> year = s.removeSuffix("年").toInt(); selSeg = -1 },
            )
            Spacer(Modifier.height(14.dp))

            // 年度总览 2x2
            val expMonth = stats.maxExpenseMonth?.let { YearMonth.parse(it).monthValue }
            val incMonth = stats.maxIncomeMonth?.let { YearMonth.parse(it).monthValue }
            StatGrid(
                cells = listOf(
                    StatCell("总支出", Fmt.yen(stats.expense), "expense"),
                    StatCell("总收入", Fmt.yen(stats.income), "income"),
                    StatCell(
                        "支出最多月",
                        expMonth?.let { "${it}月" } ?: "—",
                        "calendar",
                        clickable = stats.maxExpenseMonth != null,
                        jumpYm = stats.maxExpenseMonth,
                    ),
                    StatCell(
                        "收入最多月",
                        incMonth?.let { "${it}月" } ?: "—",
                        "calendarGold",
                        clickable = stats.maxIncomeMonth != null,
                        jumpYm = stats.maxIncomeMonth,
                    ),
                ),
                onJumpMonth = onMonth,
            )
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

            // 全年占比
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 14.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PixelSectionTitle("全年各类花销占比", icon = "burger")
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PixelDonut(
                            segments = segs,
                            canvasSize = 150.dp,
                            onSegment = { selSeg = it },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (selSeg in segs.indices) {
                        val s = segs[selSeg]
                        val amt = cats.getOrNull(selSeg)?.second ?: 0
                        PxText(
                            "${s.name} 共 ${Fmt.yen(amt)}",
                            size = 13.sp,
                            color = Px.WoodDark,
                            align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PxText("点击环形图查看各类具体金额", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    segs.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelIcon(com.miaoyu03.pixelbook.ui.PixelIcons.iconOfCategory(s.name), size = 16.dp)
                            Spacer(Modifier.width(5.dp))
                            PxText(s.name, size = 12.sp)
                            Spacer(Modifier.weight(1f))
                            PxText("%d%%".format((s.ratio * 100).toInt()), size = 12.sp, color = Px.GrayText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PxText("数据每日自动同步生成，不可编辑", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
        }
    }
}

/* ---------------- 统计格子 ---------------- */

private data class StatCell(
    val label: String,
    val value: String,
    val icon: String,
    val clickable: Boolean = false,
    val jumpYm: String? = null,
)

@Composable
private fun StatGrid(cells: List<StatCell>, onJumpMonth: ((String) -> Unit)? = null) {
    PixelPanel(
        modifier = Modifier.fillMaxWidth(),
        bg = Px.Cream,
        contentPadding = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            cells.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { c ->
                        StatCellBox(
                            c = c,
                            onJump = { c.jumpYm?.let { ym -> onJumpMonth?.invoke(ym) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun StatCellBox(c: StatCell, onJump: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (c.clickable) Modifier.clickable(onClick = onJump) else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(c.icon, size = 16.dp)
                Spacer(Modifier.width(5.dp))
                PxText(c.label, size = 12.sp, color = Px.GrayText)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(if (c.clickable) Px.Grass.copy(alpha = 0.35f) else Px.CreamBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                PxText(c.value, size = 14.sp, color = if (c.clickable) Px.GrassDark else Px.Brown, align = TextAlign.Center)
            }
        }
    }
}