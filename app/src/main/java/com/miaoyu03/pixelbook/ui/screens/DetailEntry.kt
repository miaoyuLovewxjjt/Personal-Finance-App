package com.miaoyu03.pixelbook.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Cents
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.IncomeCats
import com.miaoyu03.pixelbook.data.Ledger
import com.miaoyu03.pixelbook.data.MAX_CAT_LEN
import com.miaoyu03.pixelbook.data.MAX_NOTE_LEN
import com.miaoyu03.pixelbook.data.MAX_TX_NAME_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.data.Weather
import com.miaoyu03.pixelbook.data.fullLabel
import com.miaoyu03.pixelbook.data.label
import com.miaoyu03.pixelbook.export.PdfExporter
import com.miaoyu03.pixelbook.ui.DonutSeg
import com.miaoyu03.pixelbook.ui.LedgerFonts
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelCalendarDialog
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelDonut
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelOption
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelSectionTitle
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** 分类占比太多时合并：Top N + 「其他」（避免环形图碎成乱码细条） */
fun mergeDonut(
    byCat: Map<String, Long>,
    topN: Int = 5,
): List<Pair<String, Long>> {
    val sorted = byCat.entries.sortedByDescending { it.value }
    if (sorted.size <= topN) return sorted.map { it.key to it.value }
    val top = sorted.take(topN - 1).map { it.key to it.value }
    val otherSum = sorted.drop(topN - 1).sumOf { it.value }
    return top + ("其他" to otherSum)
}

/* ================================================================
 * 三、记账明细页（左侧 年→月→日 导航 + 右侧流水）
 * ================================================================ */

@Composable
fun DetailScreen(
    store: Store,
    ledgerId: String,
    onBack: () -> Unit,
    onAdd: (LocalDate) -> Unit,
    onMonth: (String) -> Unit,
    onYear: (Int) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }
    var addingDir by remember { mutableStateOf<TxDir?>(null) }
    var deletingTx by remember { mutableStateOf<Tx?>(null) }
    var showCal by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }

    val ledger = remember(tick) { store.ledger(ledgerId) }
    val all = remember(tick) { store.txList(ledgerId) }
    // 本账本资产账户映射（流水行显示入账资产名用）
    val assetLabelById: Map<String, String> = remember(tick, ledger?.accountId) {
        ledger?.accountId?.let { acc -> store.assetsOf(acc).associate { it.id to it.label() } } ?: emptyMap()
    }
    // 每日结余（收入-支出，分）：日历弹窗逐日小字展示用
    val dayBalances = remember(all) {
        all.groupBy { it.date }.mapValues { (_, v) ->
            v.sumOf { if (it.dir == TxDir.IN) it.amount else -it.amount }
        }
    }
    // 当前所选日期当天的花销预算（分，未设置为 null）
    val dailyBudget = remember(tick, selectedDate) { store.dailyBudget(ledgerId, selectedDate) }
    // 导出 PDF：SAF 让用户选择保存位置 → IO 线程生成
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val l = ledger
        if (uri == null || l == null) return@rememberLauncherForActivityResult
        val snapshot = l
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                appCtx.contentResolver.openOutputStream(uri)?.use { out ->
                    PdfExporter.export(appCtx, store, snapshot, out)
                } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (ok) store.toast("PDF 已导出") else store.toast("导出失败，请重试")
            }
        }
    }
    val years = remember(all, tick) {
        (all.map { it.date.year } + LocalDate.now().year).distinct().sortedDescending()
    }
    // 左导航折叠状态：默认全部展开（允许收缩）
    var navCollapsed by remember { mutableStateOf(false) }
    var expandedYears by remember(all) { mutableStateOf(years.toSet()) }
    var expandedMonths by remember(all) {
        mutableStateOf(all.map { Fmt.ymKey(it.date) }.toSet())
    }
    // 当日流水直接从全量列表派生（all 已按 tick 缓存读取，避免 txOfDay 再全量读一次账本文件 → 减少编辑/保存后的卡顿）
    val dayTxs = remember(all, selectedDate) { all.filter { it.date == selectedDate } }
    val inList = dayTxs.filter { it.dir == TxDir.IN }
    val outList = dayTxs.filter { it.dir == TxDir.OUT }
    val inSum = inList.sumOf { it.amount }
    val outSum = outList.sumOf { it.amount }
    val weather = remember(tick, selectedDate) { store.weather(ledgerId, selectedDate) }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(
            // 标题 = 账本名（居中、长名自动换行；新建/编辑时限制 30 字内）
            title = ledger?.name ?: "我的记账",
            onBack = onBack,
            trailing = {
                // 存款明细入口已移至「我的记账」账户主页，此处仅保留导出
                PixelIconButton(
                    icon = "export", size = 34.dp,
                    onClick = {
                        val safe = (ledger?.name ?: "账本")
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            .ifBlank { "账本" }
                        exportLauncher.launch("${safe}_收支报告")
                    },
                    desc = "导出PDF",
                )
            },
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ---- 左侧导航：日历（最上方居中）→ 年→月→日 折叠展开；
            //      折叠开关为骑在右侧分隔竖线上、垂直居中的精简小三角 ----
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (navCollapsed) 38.dp else 96.dp)
                    .background(Px.CreamBg)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRect(
                            Px.Brown,
                            topLeft = Offset(size.width - stroke, 0f),
                            size = Size(stroke, size.height)
                        )
                    },
            ) {
                if (!navCollapsed) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 日历图标：导航最上方居中；点击打开带「每日结余」的日历
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIconButton(
                                icon = "calendarCute",
                                size = 36.dp,
                                bg = Px.Cream,
                                onClick = { showCal = true },
                                desc = "日历",
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            years.forEach { y ->
                                val yearExpanded = y in expandedYears
                                NavYearRow(
                                    year = y,
                                    expanded = yearExpanded,
                                    selected = selectedDate.year == y,
                                    onTitleClick = { onYear(y) },
                                    onToggle = {
                                        expandedYears = if (yearExpanded) expandedYears - y else expandedYears + y
                                    },
                                )
                                if (yearExpanded) {
                                    val months = (1..12).map { YearMonth.of(y, it) }
                                        .filter { m -> all.any { YearMonth.from(it.date) == m } }
                                    months.forEach { m ->
                                        val key = Fmt.ymKey(m.atDay(1))
                                        val monthExpanded = key in expandedMonths
                                        NavMonthRow(
                                            month = m,
                                            expanded = monthExpanded,
                                            selected = YearMonth.from(selectedDate) == m,
                                            onTitleClick = { onMonth(key) },
                                            onToggle = {
                                                expandedMonths = if (monthExpanded) expandedMonths - key else expandedMonths + key
                                            },
                                        )
                                        if (monthExpanded) {
                                            val days = all.filter { YearMonth.from(it.date) == m }
                                                .map { it.date.dayOfMonth }.distinct().sorted()
                                            days.forEach { dayNum ->
                                                val d = m.atDay(dayNum)
                                                NavDayRow(
                                                    day = d,
                                                    selected = d == selectedDate,
                                                    onClick = { selectedDate = d },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 底部固定「+」按钮（草绿底，新增所选日期的那一天）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIconButton(
                                icon = "plus",
                                size = 40.dp,
                                bg = Px.Grass,
                                onClick = { onAdd(selectedDate) },
                                desc = "新增某天",
                            )
                        }
                    }
                }
                // 折叠开关：竖线中点一枚纯三角（无框、无涟漪）：展开态箭头向左，收起态箭头向右
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)                       // 隐形热区，便于点击
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,             // 点击不产生阴影/底色闪烁
                                onClick = { navCollapsed = !navCollapsed },
                            )
                            .offset(x = 14.dp),                // 让三角正好骑在右侧分隔竖线中点上
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(
                            if (navCollapsed) "triR" else "triL",
                            size = 15.dp,
                            desc = if (navCollapsed) "展开导航" else "折叠导航",
                        )
                    }
                }
            }

            // ---- 右侧流水 ----
            Column(modifier = Modifier.weight(1f)) {
                // 当天标题（日期纯显示；天气图标可直接点击修改天气 → 进入记一笔页）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PxText(Fmt.dateFull(selectedDate), size = 15.sp, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clickable { onAdd(selectedDate) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        PixelIcon((weather ?: Weather.SUNNY).iconName(), size = 26.dp, desc = "修改天气")
                    }
                }

                // 当日花销预算（在每日标题下方；点击可设置/修改/清除当天预算）
                BudgetBar(
                    budget = dailyBudget,
                    onClick = { editingBudget = true },
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // 收入区块
                    item {
                        TxSectionHeader(
                            title = "收入",
                            icon = "income",
                            color = Px.GrassDark,
                            sum = inSum,
                        )
                    }
                    if (inList.isEmpty()) {
                        item { EmptyNote("今天还没有收入，点击下方「＋」新增") }
                    } else {
                        items(inList, key = { it.id }) { t ->
                            TxRow(t, assetLabel = t.asset.takeIf { it.isNotEmpty() }?.let { assetLabelById[it] }, onTap = { editingTx = t })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // 收入明细末尾「＋」（左下对齐）：新增当天收入明细
                    item {
                        AddDetailPlus(onClick = { addingDir = TxDir.IN })
                        Spacer(Modifier.height(6.dp))
                    }
                    // 支出区块
                    item {
                        Spacer(Modifier.height(10.dp))
                        TxSectionHeader(
                            title = "支出",
                            icon = "expense",
                            color = Px.WoodDark,
                            sum = outSum,
                        )
                    }
                    if (outList.isEmpty()) {
                        item { EmptyNote("今天还没有支出，点击下方「＋」新增") }
                    } else {
                        items(outList, key = { it.id }) { t ->
                            TxRow(t, assetLabel = t.asset.takeIf { it.isNotEmpty() }?.let { assetLabelById[it] }, onTap = { editingTx = t })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // 支出明细末尾「＋」（左下对齐）：新增当天支出明细
                    item {
                        AddDetailPlus(onClick = { addingDir = TxDir.OUT })
                        Spacer(Modifier.height(6.dp))
                    }
                    // 今日总结：明细列表最下方（总收入/总支出/结余 + 花销预算 + 最大收入/花销）
                    item {
                        TodaySummaryPanel(
                            inSum = inSum,
                            outSum = outSum,
                            budget = dailyBudget,
                            inList = inList,
                            outList = outList,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }

    // 编辑 / 新增 / 删除流水
    editingTx?.let { t ->
        TxFormDialog(
            store = store,
            ledgerId = ledgerId,
            date = selectedDate,
            dir = t.dir,
            tx = t,
            onDismiss = { editingTx = null },
            onSaved = { editingTx = null; tick++ },
        )
    }
    addingDir?.let { dir ->
        TxFormDialog(
            store = store,
            ledgerId = ledgerId,
            date = selectedDate,
            dir = dir,
            tx = null,
            onDismiss = { addingDir = null },
            onSaved = { addingDir = null; tick++ },
        )
    }
    deletingTx?.let { t ->
        PixelConfirm(
            title = "删除记录",
            message = "确定删除「${t.name.ifEmpty { t.category }}」这条${if (t.dir == TxDir.IN) "收入" else "支出"}吗？",
            confirmText = "删除",
            onConfirm = { store.deleteTx(t.id, ledgerId); tick++ },
            onDismiss = { deletingTx = null },
        )
    }

    // 左侧日历按钮 → 打开带每日结余的日历（默认当前日期所在月，点某天切到那天）
    if (showCal) {
        PixelCalendarDialog(
            initial = selectedDate,
            dayBalances = dayBalances,
            onPick = { d -> selectedDate = d; showCal = false },
            onDismiss = { showCal = false },
        )
    }
    // 预算设置弹窗（当天）
    if (editingBudget) {
        BudgetDialog(
            store = store,
            ledgerId = ledgerId,
            date = selectedDate,
            initial = dailyBudget,
            onDismiss = { editingBudget = false },
            onSaved = { editingBudget = false; tick++ },
        )
    }
}

private fun Weather.iconName(): String = when (this) {
    Weather.SUNNY -> "sun"; Weather.CLOUDY -> "cloud"; Weather.RAIN -> "rain"
    Weather.SNOW -> "snow"
}


/** 今日总结面板：明细列表最下方，含 总收入/总支出/今日结余、今日花销预算/花销超出、最大收入/花销 */
@Composable
private fun TodaySummaryPanel(
    inSum: Long,
    outSum: Long,
    budget: Cents?,          // 当日预算（分）；null = 未设置
    inList: List<Tx>,
    outList: List<Tx>,
) {
    val balance = inSum - outSum
    val maxIn = inList.maxByOrNull { it.amount }
    val maxOut = outList.maxByOrNull { it.amount }
    val over = if (budget != null) outSum - budget else 0L

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        PixelSectionTitle("今日总结", icon = "statChart")
        Spacer(Modifier.height(8.dp))

        // 总览卡：总收入 / 总支出 / 今日结余 各独占一行（文字在左，数字在右）
        //        + 今日花销预算 / 花销超出（未设置显示「未设置」；未超出显示「未超支」）
        //        + 今日最大收入 / 今日最大花销
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            bg = Px.Cream,
            contentPadding = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryValue("总收入", Fmt.yen(inSum), Px.GrassDark)
                SummaryValue("总支出", Fmt.yen(outSum), Px.WoodDark)
                SummaryValue(
                    "今日结余", Fmt.yen(balance),
                    if (balance >= 0) Px.GrassDark else Px.ClayDark,
                )
                SummaryValue(
                    "今日花销预算",
                    if (budget == null) "未设置" else Fmt.yen(budget),
                    if (budget == null) Px.GrayText else Px.WoodDark,
                )
                SummaryValue(
                    "花销超出",
                    when {
                        budget == null -> "—"
                        over > 0 -> Fmt.yen(over)
                        else -> "未超支"
                    },
                    when {
                        budget == null -> Px.GrayText
                        over > 0 -> Px.ClayDark
                        else -> Px.GrassDark
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(Px.CreamDark),
                )
                MaxSummaryRow("最大收入：", maxIn, Px.GrassDark)
                MaxSummaryRow("最大花销：", maxOut, Px.WoodDark)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 总览行：文字在左，数字在右，独占一行；数字过长时仅在数字区域内自动换行（不挤压左侧文字） */
@Composable
private fun SummaryValue(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.width(10.dp))
        // 数值区占满剩余宽度：短则右对齐单行；过长则在此区域内换行，绝不挤压标签
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            PxText(
                value, size = 14.sp, color = color,
                align = TextAlign.End, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 最大收入 / 最大花销行：左标签，中 名称（仅文字区换行），右 金额（独立不被挤压） */
@Composable
private fun MaxSummaryRow(label: String, tx: Tx?, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.width(8.dp))
        if (tx == null) {
            PxText("—", size = 12.sp, color = Px.GrayText)
        } else {
            PxText(
                tx.category + if (tx.name.isNotEmpty()) " · ${tx.name}" else "",
                size = 11.sp,
                color = Px.Brown,
                modifier = Modifier.weight(1f, fill = false),   // 只占文字实际需要的宽度；过长在剩余宽度内换行
            )
            Spacer(Modifier.width(8.dp))
            PxText(Fmt.yen(tx.amount), size = 12.sp, color = color)
        }
    }
}

/** 当日预算栏（每日标题正下方）：显示今日花销预算，点击弹出设置 */
@Composable
private fun BudgetBar(budget: Cents?, onClick: () -> Unit) {
    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)   // 与下方「收入/支出」区块留出间距
            .clickable(onClick = onClick),
        bg = Px.Cream,
        depth = 2.dp,
        contentPadding = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("coin", size = 18.dp)
            Spacer(Modifier.width(6.dp))
            PxText("今日预算", size = 12.sp, color = Px.Wood)
            Spacer(Modifier.weight(1f))
            PxText(
                if (budget == null) "未设置" else Fmt.yen(budget),
                size = 13.sp,
                color = if (budget == null) Px.GrayText else Px.Brown,
            )
            Spacer(Modifier.width(4.dp))
            PixelIcon("pencil", size = 15.dp, desc = "设置预算")
        }
    }
}

/** 当日预算设置弹窗：按天生效；金额留空 = 清除当天预算 */
@Composable
private fun BudgetDialog(
    store: Store,
    ledgerId: String,
    date: LocalDate,
    initial: Cents?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.let { Fmt.money(it) } ?: "") }
    PixelDialog(
        title = "${Fmt.dayOfMonth(date)}花销预算",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(amount)
                    if (v == null && amount.isNotBlank()) { store.toast("请填写有效金额"); return@PixelButton }
                    if (store.setDailyBudget(ledgerId, date, v ?: 0L)) onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("预算金额（元）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = amount,
                onValueChange = { amount = Fmt.cleanAmountInput(it) },
                placeholder = "如：100",
                numeric = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PxText("按天设置，只作用于这一天；清空金额可删除当天预算。", size = 11.sp, color = Px.GrayText)
        }
    }
}


/** 明细区块末尾的「＋」：左下对齐，新增当天该类型明细（柔和黄底） */
@Composable
private fun AddDetailPlus(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        PixelIconButton(icon = "plus", size = 34.dp, bg = Px.Yellow, onClick = onClick, desc = "新增明细")
    }
}

/** 左侧导航：年份行（标题 + 小折叠箭头，单行） */
@Composable
private fun NavYearRow(
    year: Int,
    expanded: Boolean,
    selected: Boolean,
    onTitleClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Px.Grass.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable(onClick = onTitleClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText("${year}年", size = 13.sp, color = if (selected) Px.GrassDark else Px.Brown)
        Spacer(Modifier.width(8.dp))
        NavToggleArrow(expanded = expanded, onClick = onToggle)
    }
}

/** 左侧导航：月份行（标题 + 小折叠箭头） */
@Composable
private fun NavMonthRow(
    month: YearMonth,
    expanded: Boolean,
    selected: Boolean,
    onTitleClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Px.Grass.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable(onClick = onTitleClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText("${month.monthValue}月", size = 13.sp, color = if (selected) Px.GrassDark else Px.Wood)
        Spacer(Modifier.width(8.dp))
        NavToggleArrow(expanded = expanded, onClick = onToggle)
    }
}

/** 左侧导航：日期行（点击 → 右侧显示该日流水） */
@Composable
private fun NavDayRow(day: LocalDate, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Px.Grass.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText("${day.dayOfMonth}日", size = 13.sp, color = if (selected) Px.GrassDark else Px.Brown)
    }
}

/** 导航折叠箭头：与文字水平/垂直居中，尺寸略大于小字号 */
@Composable
private fun NavToggleArrow(expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(if (expanded) "chevronR" else "chevronD", size = 15.dp)
    }
}

@Composable
private fun TxSectionHeader(title: String, icon: String, color: Color, sum: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(icon, size = 26.dp)
        }
        Spacer(Modifier.width(7.dp))
        PxText(title, size = 14.sp, color = color)
        Spacer(Modifier.weight(1f))
        PxText(Fmt.yen(sum), size = 13.sp, color = color)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        PxText(text, size = 12.sp, color = Px.GrayText, align = TextAlign.Center)
    }
}

@Composable
private fun TxRow(t: Tx, assetLabel: String? = null, onTap: () -> Unit) {
    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onTap),
        bg = Px.Cream,
        contentPadding = 9.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(com.miaoyu03.pixelbook.ui.PixelIcons.iconOfCategory(t.category), size = 26.dp)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                PxText(t.category + if (t.name.isNotEmpty()) " · ${t.name}" else "", size = 13.sp)
                // 入账资产账户行（类别+子类别前10字；有角色直接显示角色）
                if (assetLabel?.isNotEmpty() == true) {
                    Spacer(Modifier.height(2.dp))
                    PxText(assetLabel, size = 10.sp, color = Px.Wood)
                }
                if (t.note.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    PxText(
                        t.note,
                        size = 10.sp,
                        color = Px.GrayText,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PxText(
                    Fmt.yen(t.amount),   // 区块已分收入/支出，金额不再带正负号
                    size = 13.sp,
                    color = if (t.dir == TxDir.IN) Px.GrassDark else Px.WoodDark,
                )
                Spacer(Modifier.height(2.dp))
                PxText(t.time, size = 10.sp, color = Px.GrayText)
            }
        }
    }
}

/* ================================================================
 * 记账（新增一笔 / 编辑一笔）表单弹窗：Detail 与 Entry 共用
 * tx == null 表示新增，否则编辑
 * ================================================================ */

@Composable
fun TxFormDialog(
    store: Store,
    ledgerId: String,
    date: LocalDate,
    dir: TxDir,
    tx: Tx?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val isIn = dir == TxDir.IN
    val now = java.time.LocalTime.now()
    // 账户维度：类别表 + 资产账户
    val accountId = remember(tx) { store.ledger(ledgerId)?.accountId ?: "" }
    val cats = remember(accountId) { if (isIn) store.incomeCats(accountId) else store.expenseCats(accountId) }
    val assetOptions: List<Pair<String, String>> = remember(accountId) {
        if (accountId.isEmpty()) emptyList()
        else store.assetsOf(accountId).map { it.id to it.fullLabel() }
    }
    // 编辑历史记录时：类别不在类表中（如旧数据/已被删除）→ 归入「自定义」并预填输入框
    val catIsCustom = tx != null && tx.category !in cats
    var time by remember { mutableStateOf(tx?.time ?: "%02d:%02d".format(now.hour, now.minute)) }
    var cat by remember { mutableStateOf(if (catIsCustom) CUSTOM_CAT else (tx?.category ?: if (isIn) "工资" else "餐饮")) }
    var customCat by remember { mutableStateOf(if (catIsCustom) tx!!.category else "") }
    var amountStr by remember { mutableStateOf(tx?.let { Fmt.money(it.amount) } ?: "") }
    var name by remember { mutableStateOf(tx?.name ?: "") }
    var note by remember { mutableStateOf(tx?.note ?: "") }
    var assetId by remember { mutableStateOf(tx?.asset ?: "") }
    var showDelete by remember { mutableStateOf(false) }

    PixelDialog(
        title = "${if (tx == null) "新增" else "编辑"}${if (isIn) "收入" else "支出"}",
        onDismiss = onDismiss,
        footer = {
            if (tx != null) {
                PixelButton(
                    "删除",
                    { showDelete = true },
                    bg = Px.Red, height = 40.dp,
                    modifier = Modifier.width(104.dp),
                )
            }
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(amountStr)
                    if (v == null) { store.toast("请填写有效金额"); return@PixelButton }
                    val t = time.trim()
                    if (!Regex("^\\d{1,2}:\\d{2}$").matches(t)) { store.toast("时间格式：HH:mm"); return@PixelButton }
                    // 自定义类别：取输入框内容作为类别名
                    val finalCat = if (cat == CUSTOM_CAT) {
                        val c = customCat.trim()
                        if (c.isEmpty()) { store.toast("请输入自定义类别"); return@PixelButton }
                        c
                    } else cat
                    // 新类别（不在类表中）自动加入该类账户的类别表，之后所有账本下拉都能直接选
                    if (finalCat !in cats && accountId.isNotEmpty()) {
                        if (isIn) store.addIncomeCat(accountId, finalCat) else store.addExpenseCat(accountId, finalCat)
                    }
                    if (tx == null) {
                        store.addTx(
                            Tx(
                                id = "t${System.currentTimeMillis()}",
                                ledgerId = ledgerId, date = date, time = t,
                                dir = dir, category = finalCat, amount = v,
                                name = name.trim(), note = note.trim(), asset = assetId,
                            )
                        )
                    } else {
                        store.updateTx(tx.copy(time = t, category = finalCat, amount = v, name = name.trim(), note = note.trim(), asset = assetId))
                    }
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(104.dp),
            )
        },
        contentScrollable = true,
    ) {
        // 表单体由 PixelDialog 提供弹性滚动：软键盘弹出后仍能滚动查看/点选下方字段（含资产账户），边打字边看输入
        TxFormFields(
            isIn = isIn,
            time = time,
            onTime = { time = it },
            cat = cat,
            onCat = { cat = it },
            customCat = customCat,
            onCustomCat = { customCat = it },
            amount = amountStr,
            onAmount = { amountStr = it },
            name = name,
            onName = { name = it },
            note = note,
            onNote = { note = it },
            cats = cats,
            assetOptions = assetOptions,
            assetSel = assetId,
            onAsset = { assetId = it },
        )
    }

    if (showDelete) {
        PixelConfirm(
            title = "删除记录",
            message = "确定删除这条${if (isIn) "收入" else "支出"}吗？",
            confirmText = "删除",
            onConfirm = { store.deleteTx(tx!!.id, ledgerId); onSaved() },
            onDismiss = { showDelete = false },
        )
    }
}

/** 「自定义」类别选项名（下拉与判断共用） */
const val CUSTOM_CAT = "自定义"

/** 收入/支出 表单字段（编辑弹窗 & 记一笔页共用） */
@Composable
fun TxFormFields(
    isIn: Boolean,
    time: String,
    onTime: (String) -> Unit,
    cat: String,
    onCat: (String) -> Unit,
    customCat: String,
    onCustomCat: (String) -> Unit,
    amount: String,
    onAmount: (String) -> Unit,
    name: String,
    onName: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    cats: List<String> = emptyList(),   // 类表（含预设与已新增类别；空 = 用内置预设）
    assetOptions: List<Pair<String, String>> = emptyList(),   // (id, 显示名) 资产账户列表；空 = 不显示
    assetSel: String = "",               // 当前选中的资产账户 id
    onAsset: (String) -> Unit = {},
) {
    val allCats = cats.ifEmpty {
        if (isIn) IncomeCats.list else com.miaoyu03.pixelbook.data.ExpenseCats.list
    }
    // 选项顺序：类表 → 「自定义」（新输入）
    val options = allCats.map { PixelOption(it, com.miaoyu03.pixelbook.ui.PixelIcons.iconOfCategory(it)) } +
        listOf(PixelOption(CUSTOM_CAT, "pencil"))
    Column {
        PxText(if (isIn) "收入时间" else "花销时间", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(
            value = time, onValueChange = onTime,
            placeholder = "如：12:30", modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "收入类型" else "花销类型", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelDropdown(
            label = if (isIn) "收入类型" else "花销类型",
            options = options,
            selected = cat,
            onSelect = onCat,
            modifier = Modifier.fillMaxWidth(),
            width = 130.dp,
        )
        // 选「自定义」：多出一个类别输入框（已用过的自定义类别直接出现在下拉里，无需再输）
        if (cat == CUSTOM_CAT) {
            Spacer(Modifier.height(8.dp))
            PxText("自定义类别", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = customCat,
                onValueChange = { onCustomCat(Fmt.clip(it, MAX_CAT_LEN)) },   // 最长 10 汉字
                placeholder = "如：宠物、旅行", modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "收入金额（元）" else "花销金额（元）", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(
            value = amount,
            onValueChange = { onAmount(Fmt.cleanAmountInput(it)) },   // 最多 9 位整数 + 2 位小数
            placeholder = "如：43.00", numeric = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "具体收入名称" else "具体花销名称", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(
            value = name,
            onValueChange = { onName(Fmt.clip(it, MAX_TX_NAME_LEN)) },   // 最长 25 汉字
            placeholder = "如：兰州拉面", modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "收入备注" else "花销备注", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(
            value = note,
            onValueChange = { onNote(Fmt.clip(it, MAX_NOTE_LEN)) },   // 最长 30 汉字
            placeholder = "可选", modifier = Modifier.fillMaxWidth(),
        )
        // 资产账户：选择该笔收入/支出 到账/支付 的资产账户（银行/支付宝/微信…）
        if (assetOptions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PxText(if (isIn) "到账资产账户" else "支付资产账户", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelDropdown(
                label = if (isIn) "到账资产账户" else "支付资产账户",
                options = listOf(PixelOption("不使用", "dots")) +
                    assetOptions.map { (id, label) -> PixelOption(label, "bankCard") },
                selected = if (assetSel.isEmpty()) "不使用" else (assetOptions.firstOrNull { it.first == assetSel }?.second ?: "不使用"),
                onSelect = { s ->
                    if (s == "不使用") onAsset("")
                    else assetOptions.firstOrNull { it.second == s }?.let { onAsset(it.first) }
                },
                modifier = Modifier.fillMaxWidth(),
                width = 150.dp,
            )
        }
    }
}

/* ================================================================
 * 四、记一笔页（新增一笔）
 * ================================================================ */

@Composable
fun EntryScreen(
    store: Store,
    ledgerId: String,
    date: LocalDate,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var curDate by remember { mutableStateOf(date) }
    var showCal by remember { mutableStateOf(false) }
    // 收支切换：默认「支出」（新增开销是最高频操作）；两个方向的表单状态各自保留
    var isInTab by remember { mutableStateOf(false) }

    // 表单状态（收入 / 支出各一组）
    val inState = remember { EntryFormState(true) }
    val outState = remember { EntryFormState(false) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }
    // 切换日期时重置两组表单内容（防止把上一日的金额/名称/备注误记到新日期）
    LaunchedEffect(curDate) {
        inState.resetForNewDate()
        outState.resetForNewDate()
    }

    val dayTxs = remember(tick, curDate) { store.txOfDay(ledgerId, curDate) }
    val inList = dayTxs.filter { it.dir == TxDir.IN }
    val outList = dayTxs.filter { it.dir == TxDir.OUT }
    val weather = remember(tick, curDate) { store.weather(ledgerId, curDate) ?: Weather.SUNNY }
    // 账户维度：类别表 + 资产账户（设置中可维护）
    val accountId = remember { store.ledger(ledgerId)?.accountId ?: "" }
    val inCats = remember(tick, accountId) { store.incomeCats(accountId) }
    val outCats = remember(tick, accountId) { store.expenseCats(accountId) }
    val assetOptions: List<Pair<String, String>> = remember(tick, accountId) {
        if (accountId.isEmpty()) emptyList() else store.assetsOf(accountId).map { it.id to it.fullLabel() }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        PixelHeader(title = "记一笔", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            // 1. 日期（可点击切换：新增任意一天）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PxText(Fmt.dateFull(curDate), size = 16.sp, align = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clickable { showCal = true }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon("calendar", size = 26.dp, desc = "选择日期")
                }
            }
            Spacer(Modifier.height(10.dp))
            // 2. 天气选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon("chevronR", size = 14.dp)
                Weather.entries.forEach { w ->
                    WeatherIconButton(
                        w = w,
                        selected = weather == w,
                        onClick = {
                            store.setWeather(ledgerId, curDate, w)
                            tick++
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                PxText("天气", size = 12.sp, color = Px.GrayText)
            }
            Spacer(Modifier.height(14.dp))

            // 3. 收支切换（默认支出；收入/支出状态各自保留，保存时都提交）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryTabButton("收入", icon = "income", active = isInTab, onClick = { isInTab = true }, modifier = Modifier.weight(1f))
                EntryTabButton("支出", icon = "expense", active = !isInTab, onClick = { isInTab = false }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // 4. 录入区（收支二选一展示）
            if (isInTab) {
                PxText("收入录入", size = 12.sp, color = Px.GrayText)
                Spacer(Modifier.height(8.dp))
                TxFormFields(
                    isIn = true,
                    time = inState.time, onTime = { inState.time = it },
                    cat = inState.cat, onCat = { inState.cat = it },
                    customCat = inState.customCat, onCustomCat = { inState.customCat = it },
                    amount = inState.amount, onAmount = { inState.amount = it },
                    name = inState.name, onName = { inState.name = it },
                    note = inState.note, onNote = { inState.note = it },
                    cats = inCats,
                    assetOptions = assetOptions,
                    assetSel = inState.asset,
                    onAsset = { inState.asset = it },
                )
            } else {
                PxText("支出录入", size = 12.sp, color = Px.GrayText)
                Spacer(Modifier.height(8.dp))
                TxFormFields(
                    isIn = false,
                    time = outState.time, onTime = { outState.time = it },
                    cat = outState.cat, onCat = { outState.cat = it },
                    customCat = outState.customCat, onCustomCat = { outState.customCat = it },
                    amount = outState.amount, onAmount = { outState.amount = it },
                    name = outState.name, onName = { outState.name = it },
                    note = outState.note, onNote = { outState.note = it },
                    cats = outCats,
                    assetOptions = assetOptions,
                    assetSel = outState.asset,
                    onAsset = { outState.asset = it },
                )
            }
            Spacer(Modifier.height(16.dp))

            // 6. 今日已记（可编辑/删除）
            PixelSectionTitle("今日已记", icon = "dots")
            Spacer(Modifier.height(8.dp))
            if (dayTxs.isEmpty()) {
                PxText("今天还没有记录", size = 12.sp, color = Px.GrayText)
            } else {
                dayTxs.sortedByDescending { it.time }.forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { editingTx = t },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelIcon(com.miaoyu03.pixelbook.ui.PixelIcons.iconOfCategory(t.category), size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            PxText(t.category + if (t.name.isNotEmpty()) " · ${t.name}" else "", size = 12.sp)
                            PxText(t.time, size = 10.sp, color = Px.GrayText)
                        }
                        PxText(
                            Fmt.yen(t.amount),   // 收支分页展示，金额不带正负号
                            size = 12.sp,
                            color = if (t.dir == TxDir.IN) Px.GrassDark else Px.WoodDark,
                        )
                        Spacer(Modifier.width(6.dp))
                        // 编辑（铅笔）在删除（垃圾桶）左侧
                        PixelIconButton(
                            icon = "pencil", size = 26.dp, bg = Px.CreamDark,
                            onClick = { editingTx = t },
                            desc = "编辑",
                        )
                        Spacer(Modifier.width(4.dp))
                        PixelIconButton(
                            icon = "trash", size = 26.dp, bg = Px.CreamDark,
                            onClick = {
                                store.deleteTx(t.id, ledgerId)
                                tick++
                            },
                            desc = "删除",
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 7. 底部操作栏 取消 / 保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelButton(text = "取消", onClick = onBack, bg = Px.Wood, modifier = Modifier.weight(1f))
            PixelButton(
                text = "保存",
                onClick = {
                    val inOk = saveEntry(store, ledgerId, curDate, inState)
                    val outOk = saveEntry(store, ledgerId, curDate, outState)
                    if (inState.amount.isBlank() && outState.amount.isBlank()) {
                        store.toast("请填写收入或支出金额")
                    } else if (inOk && outOk) {
                        onSaved()
                    }
                },
                bg = Px.Clay, modifier = Modifier.weight(1f),
            )
        }
    }

    if (showCal) {
        PixelCalendarDialog(
            initial = curDate,
            onPick = { curDate = it; showCal = false },
            onDismiss = { showCal = false },
        )
    }

    editingTx?.let { t ->
        TxFormDialog(
            store = store,
            ledgerId = ledgerId,
            date = curDate,
            dir = t.dir,
            tx = t,
            onDismiss = { editingTx = null },
            onSaved = { editingTx = null; tick++ },
        )
    }
}

/** 提交一笔（金额为空则跳过该区块）；返回是否提交成功（自定义类别为空时返回 false） */
private fun saveEntry(store: Store, ledgerId: String, date: LocalDate, st: EntryFormState): Boolean {
    val v = Fmt.parseCents(st.amount)
    if (v == null || st.amount.isBlank()) return true
    val t = st.time.trim()
    val timeOk = if (Regex("^\\d{1,2}:\\d{2}$").matches(t)) t else "12:00"
    val finalCat = if (st.cat == CUSTOM_CAT) {
        val c = st.customCat.trim()
        if (c.isEmpty()) { store.toast("请输入自定义类别"); return false }
        c
    } else st.cat
    // 新类别自动加入账户类别表（之后所有账本下拉可直接选）
    val accountId = store.ledger(ledgerId)?.accountId ?: ""
    if (accountId.isNotEmpty()) {
        val cats = if (st.isIn) store.incomeCats(accountId) else store.expenseCats(accountId)
        if (finalCat !in cats) {
            if (st.isIn) store.addIncomeCat(accountId, finalCat) else store.addExpenseCat(accountId, finalCat)
        }
    }
    store.addTx(
        Tx(
            id = "t${System.currentTimeMillis()}",
            ledgerId = ledgerId, date = date, time = timeOk,
            dir = if (st.isIn) TxDir.IN else TxDir.OUT,
            category = finalCat, amount = v, name = st.name.trim(), note = st.note.trim(),
            asset = st.asset,
        )
    )
    return true
}

/** 表单状态容器（默认时间为当前手机时间） */
class EntryFormState(val isIn: Boolean) {
    private val now = java.time.LocalTime.now()
    var time by mutableStateOf("%02d:%02d".format(now.hour, now.minute))
    var cat by mutableStateOf(if (isIn) "工资" else "餐饮")
    var customCat by mutableStateOf("")
    var amount by mutableStateOf("")
    var name by mutableStateOf("")
    var note by mutableStateOf("")
    var asset by mutableStateOf("")

    /** 切换到另一天时重置内容字段：时间刷新为当前时间、清空金额/名称/备注（类别/资产保留常用选择） */
    fun resetForNewDate() {
        val n = java.time.LocalTime.now()
        time = "%02d:%02d".format(n.hour, n.minute)
        amount = ""
        name = ""
        note = ""
    }
}

@Composable
private fun WeatherIconButton(w: Weather, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(if (selected) Px.Yellow.copy(alpha = 0.5f) else Px.Cream)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = if (selected) 3.dp.toPx() else 2.dp.toPx()
                drawRect(
                    if (selected) Px.Clay else Px.Brown,
                    style = Stroke(width = stroke)
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(w.iconName(), size = 28.dp)
    }
}

/** 记一笔页收支切换按钮（选中高亮描边） */
@Composable
private fun EntryTabButton(
    label: String,
    icon: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(if (active) Px.Grass.copy(alpha = 0.28f) else Px.Cream)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = if (active) 3.dp.toPx() else 2.dp.toPx()
                drawRect(
                    if (active) Px.Grass else Px.Brown,
                    style = Stroke(width = stroke)
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(icon, size = 26.dp)
            }
            Spacer(Modifier.width(8.dp))
            PxText(label, size = 14.sp, color = if (active) Px.GrassDark else Px.Brown)
        }
    }
}
