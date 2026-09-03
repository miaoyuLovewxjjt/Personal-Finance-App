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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.IncomeCats
import com.miaoyu03.pixelbook.data.Ledger
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.data.Weather
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
    onDeposits: () -> Unit,
    onMonth: (String) -> Unit,
    onYear: (Int) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }
    var addingDir by remember { mutableStateOf<TxDir?>(null) }
    var deletingTx by remember { mutableStateOf<Tx?>(null) }

    val ledger = remember(tick) { store.ledger(ledgerId) }
    val all = remember(tick) { store.txList(ledgerId) }

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
    val dayTxs = remember(tick, selectedDate) { store.txOfDay(ledgerId, selectedDate) }
    val inList = dayTxs.filter { it.dir == TxDir.IN }
    val outList = dayTxs.filter { it.dir == TxDir.OUT }
    val inSum = inList.sumOf { it.amount }
    val outSum = outList.sumOf { it.amount }
    val weather = remember(tick, selectedDate) { store.weather(ledgerId, selectedDate) }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(
            // 标题 = 账本名（居中、长名自动换行；新建/编辑时限制 30 字内）
            title = ledger?.name ?: "记账明细",
            onBack = onBack,
            trailing = {
                // 右起：财富明细 → 导出（最右）
                PixelIconButton(icon = "chest", size = 34.dp, onClick = onDeposits, desc = "存款明细")
                Spacer(Modifier.width(4.dp))
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
            // ---- 左侧导航：年→月→日 折叠展开；标题点击跳总结页，箭头点击展开子级。
            //      顶部折叠按钮可把整个导航收缩到左侧窄条 ----
            Column(
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
                // 顶部折叠 / 展开按钮（方框箭头，贴导航栏与明细交界线；右侧留距不压住分割竖线）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .padding(end = 6.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    PixelIconButton(
                        icon = if (navCollapsed) "collapseR" else "collapseL",
                        size = 30.dp,
                        bg = Px.Cream,
                        onClick = { navCollapsed = !navCollapsed },
                        desc = if (navCollapsed) "展开导航" else "折叠导航",
                    )
                }
                if (!navCollapsed) {
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
                            TxRow(t, onTap = { editingTx = t })
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
                            TxRow(t, onTap = { editingTx = t })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // 支出明细末尾「＋」（左下对齐）：新增当天支出明细
                    item {
                        AddDetailPlus(onClick = { addingDir = TxDir.OUT })
                        Spacer(Modifier.height(6.dp))
                    }
                    // 今日总结：明细列表最下方（总收入/总支出/结余 + 最大收入/花销 + 花销占比）
                    item {
                        TodaySummaryPanel(
                            inSum = inSum,
                            outSum = outSum,
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
}

private fun Weather.iconName(): String = when (this) {
    Weather.SUNNY -> "sun"; Weather.CLOUDY -> "cloud"; Weather.RAIN -> "rain"
    Weather.SNOW -> "snow"
}


/** 今日总结面板：明细列表最下方，含 总收入/总支出/今日结余、最大收入/花销、花销占比图 */
@Composable
private fun TodaySummaryPanel(
    inSum: Long,
    outSum: Long,
    inList: List<Tx>,
    outList: List<Tx>,
) {
    val balance = inSum - outSum
    val maxIn = inList.maxByOrNull { it.amount }
    val maxOut = outList.maxByOrNull { it.amount }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        PixelSectionTitle("今日总结", icon = "statChart")
        Spacer(Modifier.height(8.dp))

        // 总览卡：总收入 / 总支出 / 今日结余 各独占一行（文字在左，数字在右）
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

/** 总览行：文字在左，数字在右，独占一行（无底色） */
@Composable
private fun SummaryValue(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.weight(1f))
        PxText(value, size = 14.sp, color = color)
    }
}

/** 最大收入 / 最大花销行：左标签，右 名称 + 金额 */
@Composable
private fun MaxSummaryRow(label: String, tx: Tx?, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(label, size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.weight(1f))
        if (tx == null) {
            PxText("—", size = 12.sp, color = Px.GrayText)
        } else {
            PxText(
                tx.category + if (tx.name.isNotEmpty()) " · ${tx.name}" else "",
                size = 11.sp,
                color = Px.Brown,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            PxText(Fmt.yen(tx.amount), size = 12.sp, color = color)
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
        PixelIcon(if (expanded) "chevronD" else "chevronR", size = 15.dp)
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
        PixelIcon(icon, size = 16.dp)
        Spacer(Modifier.width(5.dp))
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
private fun TxRow(t: Tx, onTap: () -> Unit) {
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
                    if (t.dir == TxDir.IN) "+${Fmt.yen(t.amount)}" else "-${Fmt.yen(t.amount)}",
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
    // 全局类表（设置中可维护；含「其他」）
    val cats = if (isIn) store.incomeCats() else store.expenseCats()
    // 编辑历史记录时：类别不在类表中（如旧数据/已被删除）→ 归入「自定义」并预填输入框
    val catIsCustom = tx != null && tx.category !in cats
    var time by remember { mutableStateOf(tx?.time ?: "%02d:%02d".format(now.hour, now.minute)) }
    var cat by remember { mutableStateOf(if (catIsCustom) CUSTOM_CAT else (tx?.category ?: if (isIn) "工资" else "餐饮")) }
    var customCat by remember { mutableStateOf(if (catIsCustom) tx!!.category else "") }
    var amountStr by remember { mutableStateOf(tx?.let { Fmt.money(it.amount) } ?: "") }
    var name by remember { mutableStateOf(tx?.name ?: "") }
    var note by remember { mutableStateOf(tx?.note ?: "") }
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
                    // 新类别（不在类表中）自动加入全局类表，之后所有账本下拉都能直接选
                    if (finalCat !in cats) {
                        if (isIn) store.addIncomeCat(finalCat) else store.addExpenseCat(finalCat)
                    }
                    if (tx == null) {
                        store.addTx(
                            Tx(
                                id = "t${System.currentTimeMillis()}",
                                ledgerId = ledgerId, date = date, time = t,
                                dir = dir, category = finalCat, amount = v,
                                name = name.trim(), note = note.trim(),
                            )
                        )
                    } else {
                        store.updateTx(tx.copy(time = t, category = finalCat, amount = v, name = name.trim(), note = note.trim()))
                    }
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(104.dp),
            )
        },
    ) {
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
                value = customCat, onValueChange = onCustomCat,
                placeholder = "如：宠物、旅行", modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "收入金额（元）" else "花销金额（元）", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(value = amount, onValueChange = onAmount, placeholder = "如：43.00", numeric = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "具体收入名称" else "具体花销名称", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(value = name, onValueChange = onName, placeholder = "如：兰州拉面", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PxText(if (isIn) "收入备注" else "花销备注", size = 12.sp, color = Px.GrayText)
        Spacer(Modifier.height(4.dp))
        PixelTextField(value = note, onValueChange = onNote, placeholder = "可选", modifier = Modifier.fillMaxWidth())
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

    // 表单状态（收入 / 支出各一组）
    val inState = remember { EntryFormState(true) }
    val outState = remember { EntryFormState(false) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }

    val dayTxs = remember(tick, curDate) { store.txOfDay(ledgerId, curDate) }
    val inList = dayTxs.filter { it.dir == TxDir.IN }
    val outList = dayTxs.filter { it.dir == TxDir.OUT }
    val weather = remember(tick, curDate) { store.weather(ledgerId, curDate) ?: Weather.SUNNY }
    // 全局类表（设置中可维护）
    val inCats = remember(tick) { store.incomeCats() }
    val outCats = remember(tick) { store.expenseCats() }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    PixelIcon("calendar", size = 20.dp, desc = "选择日期")
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

            // 4. 收入录入区
            PixelSectionTitle("收入", icon = "income", color = Px.GrassDark)
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
            )
            Spacer(Modifier.height(16.dp))

            // 5. 支出录入区
            PixelSectionTitle("支出", icon = "expense", color = Px.WoodDark)
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
            )
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
                            if (t.dir == TxDir.IN) "+${Fmt.yen(t.amount)}" else "-${Fmt.yen(t.amount)}",
                            size = 12.sp,
                            color = if (t.dir == TxDir.IN) Px.GrassDark else Px.WoodDark,
                        )
                        Spacer(Modifier.width(6.dp))
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
    // 新类别自动加入全局类表（之后所有账本下拉可直接选）
    val cats = if (st.isIn) store.incomeCats() else store.expenseCats()
    if (finalCat !in cats) {
        if (st.isIn) store.addIncomeCat(finalCat) else store.addExpenseCat(finalCat)
    }
    store.addTx(
        Tx(
            id = "t${System.currentTimeMillis()}",
            ledgerId = ledgerId, date = date, time = timeOk,
            dir = if (st.isIn) TxDir.IN else TxDir.OUT,
            category = finalCat, amount = v, name = st.name.trim(), note = st.note.trim(),
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