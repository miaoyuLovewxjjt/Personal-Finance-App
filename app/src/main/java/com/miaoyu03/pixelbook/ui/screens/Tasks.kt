package com.miaoyu03.pixelbook.ui.screens

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.MAX_TASK_LEN
import com.miaoyu03.pixelbook.data.MAX_TASK_NOTE_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.TaskItem
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelCalendarDialog
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import java.time.LocalDate
import java.time.YearMonth

/* ================================================================
 * 任务系统
 *  ① 今日任务区块（目录页，我的账本上方）：标题「今日任务列表」+
 *     顶部「我的任务」入口（任务卷轴图标，点按进入全部任务页）
 *  ② 全部任务页：左侧日期导航（年→月→日，参考记账明细），
 *     历史日期可新增/编辑/删除/打勾
 *  三要素：日期（默认所选日期）、任务名（≤15 字）、备注（≤60 字）
 * ================================================================ */

/* ---------------- ① 今日任务区块（目录页） ---------------- */

@Composable
fun TodayTasksSection(
    store: Store,
    accountId: String,
    refreshKey: Int,
    onChange: () -> Unit,
    onOpenAll: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    val today = LocalDate.now()
    val tasks = remember(accountId, refreshKey) { store.tasksOn(accountId, today) }
    val doneCount = tasks.count { it.done }

    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        bg = Px.Cream,
        contentPadding = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部：我的任务（卷轴图标装饰 + 文字点击 → 全部任务页）
            // 注：整行 Row 承载 clickable，避免窄文字热区被外层布局吞掉
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAll)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon("taskScroll", size = 22.dp)
                Spacer(Modifier.width(8.dp))
                PxText("我的任务", size = 15.sp, color = Px.Brown)
                Spacer(Modifier.weight(1f))
                PxText("全部任务 ▸", size = 11.sp, color = Px.GrayText)
            }
            Spacer(Modifier.height(6.dp))
            TaskDivider()
            Spacer(Modifier.height(6.dp))
            // 今日任务列表（无左侧图标；标题 + 小字备注 + 编辑/删除）
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxText("今日任务列表", size = 14.sp, color = Px.Brown)
                Spacer(Modifier.weight(1f))
                PxText("$doneCount / ${tasks.size}", size = 11.sp, color = Px.GrayText)
                Spacer(Modifier.width(8.dp))
                PixelIconButton(icon = "plus", size = 28.dp, bg = Px.Yellow,
                    onClick = { showAdd = true }, desc = "新增任务")
            }
            if (tasks.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                PxText("今天暂无任务，点右上「＋」新增", size = 12.sp, color = Px.GrayText)
            } else {
                Spacer(Modifier.height(4.dp))
                tasks.forEach { t ->
                    TaskRow(
                        task = t,
                        showDate = false,
                        onToggle = { store.toggleTask(accountId, t.id); onChange() },
                        onEdit = { editing = t },
                        onDelete = { deleting = t },
                    )
                }
            }
        }
    }

    if (showAdd) {
        TaskFormDialog(
            store = store,
            accountId = accountId,
            initial = null,
            defaultDate = today,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; onChange() },
        )
    }
    editing?.let { t ->
        TaskFormDialog(
            store = store,
            accountId = accountId,
            initial = t,
            defaultDate = today,
            onDismiss = { editing = null },
            onSaved = { editing = null; onChange() },
        )
    }
    deleting?.let { t ->
        PixelConfirm(
            title = "删除任务",
            message = "确定删除任务「${t.text}」吗？",
            confirmText = "删除",
            onConfirm = { store.deleteTask(accountId, t.id); deleting = null; onChange() },
            onDismiss = { deleting = null },
        )
    }
}

/* ---------------- ② 全部任务页（左日期导航 + 任务列表） ---------------- */

@Composable
fun TasksScreen(
    store: Store,
    accountId: String,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var navCollapsed by remember { mutableStateOf(false) }
    var expandedYears by remember { mutableStateOf(setOf<Int>()) }
    var expandedMonths by remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    var showCal by remember { mutableStateOf(false) }

    val all = remember(accountId, tick) { store.tasksOf(accountId) }
    val dates = remember(all) {
        (all.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() } + LocalDate.now())
            .distinct().sortedDescending()
    }
    val years = remember(dates) { dates.map { it.year }.distinct().sortedDescending() }
    val dayTasks = remember(all, selectedDate) {
        all.filter { it.date == selectedDate.toString() }
            .sortedWith(compareBy({ it.done }, { it.created }))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "我的任务", onBack = onBack)

        Row(modifier = Modifier.fillMaxSize()) {
            // ---- 左侧日期导航（年 → 月 → 日，参考记账明细） ----
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
                            size = Size(stroke, size.height),
                        )
                    },
            ) {
                if (!navCollapsed) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 日历按钮（选任意日期，可给历史日期加任务）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIconButton(
                                icon = "calendarCute", size = 30.dp, bg = Px.Cream,
                                onClick = { showCal = true }, desc = "选择日期",
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            years.forEach { y ->
                                val yExp = y in expandedYears
                                TaskNavRow("${y}年", 1, selectedDate.year == y, yExp) {
                                    expandedYears = if (yExp) expandedYears - y else expandedYears + y
                                }
                                if (yExp) {
                                    val months = (1..12).filter { m -> dates.any { it.year == y && it.monthValue == m } }
                                    months.forEach { m ->
                                        val ym = "%04d-%02d".format(y, m)
                                        val mExp = ym in expandedMonths
                                        TaskNavRow("${m}月", 2, YearMonth.from(selectedDate).let { it.year == y && it.monthValue == m }, mExp) {
                                            expandedMonths = if (mExp) expandedMonths - ym else expandedMonths + ym
                                        }
                                        if (mExp) {
                                            dates.filter { it.year == y && it.monthValue == m }.forEach { d ->
                                                TaskNavRow("${d.dayOfMonth}日", 3, d == selectedDate, null) {
                                                    selectedDate = d
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 底部固定「＋」（新增所选日期任务）
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelIconButton(icon = "plus", size = 40.dp, bg = Px.Grass,
                                onClick = { showAdd = true }, desc = "新增任务")
                        }
                    }
                }
                // 折叠三角（骑右缘竖线）
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = { navCollapsed = !navCollapsed },
                            )
                            .offset(x = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(if (navCollapsed) "triR" else "triL", size = 15.dp,
                            desc = if (navCollapsed) "展开导航" else "折叠导航")
                    }
                }
            }

            // ---- 右侧：所选日期的任务列表 ----
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PxText(Fmt.dateFull(selectedDate), size = 16.sp, modifier = Modifier.weight(1f))
                    PxText("${dayTasks.count { it.done }} / ${dayTasks.size}", size = 12.sp, color = Px.GrayText)
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (dayTasks.isEmpty()) {
                        item {
                            Spacer(Modifier.height(60.dp))
                            PxText(
                                if (selectedDate == LocalDate.now()) "今天还没有任务，点击左下「＋」新增"
                                else "这一天没有任务，点击左下「＋」补记",
                                size = 13.sp, color = Px.GrayText,
                                align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(dayTasks, key = { it.id }) { t ->
                        PixelPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            bg = Px.Cream,
                            contentPadding = 10.dp,
                        ) {
                            TaskRow(
                                task = t,
                                showDate = false,
                                onToggle = { store.toggleTask(accountId, t.id); tick++ },
                                onEdit = { editing = t },
                                onDelete = { deleting = t },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showCal) {
        PixelCalendarDialog(
            initial = selectedDate,
            onPick = { selectedDate = it; showCal = false },
            onDismiss = { showCal = false },
        )
    }
    if (showAdd) {
        TaskFormDialog(
            store = store,
            accountId = accountId,
            initial = null,
            defaultDate = selectedDate,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; tick++ },
        )
    }
    editing?.let { t ->
        TaskFormDialog(
            store = store,
            accountId = accountId,
            initial = t,
            defaultDate = selectedDate,
            onDismiss = { editing = null },
            onSaved = { editing = null; tick++ },
        )
    }
    deleting?.let { t ->
        PixelConfirm(
            title = "删除任务",
            message = "确定删除任务「${t.text}」吗？",
            confirmText = "删除",
            onConfirm = { store.deleteTask(accountId, t.id); deleting = null; tick++ },
            onDismiss = { deleting = null },
        )
    }
}

/** 任务导航行（三级缩进；expanded=null 不可折叠） */
@Composable
private fun TaskNavRow(
    title: String,
    level: Int,
    active: Boolean,
    expanded: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Px.Grass.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                horizontal = when (level) { 1 -> 10.dp; 2 -> 20.dp; else -> 30.dp },
                vertical = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(
            title,
            size = when (level) { 1 -> 14.sp; 2 -> 13.sp; else -> 12.sp },
            color = when { active -> Px.GrassDark; level == 1 -> Px.Brown; else -> Px.Wood },
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (expanded != null) {
            PixelIcon(if (expanded) "chevronR" else "chevronD", size = 12.dp)
        }
    }
}

/* ---------------- 任务行（今日区块与全部任务页共用） ---------------- */

@Composable
private fun TaskRow(
    task: TaskItem,
    showDate: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 对勾方框（完成打勾、草绿高亮）
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(if (task.done) Px.Grass.copy(alpha = 0.3f) else Px.CreamBg)
                .clickable(onClick = onToggle)
                .drawBehind {
                    drawRect(
                        if (task.done) Px.Grass else Px.Brown,
                        style = Stroke(if (task.done) 2.5.dp.toPx() else 2.dp.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) PixelIcon("check", size = 16.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            PxText(
                task.text,
                size = 13.sp,
                color = if (task.done) Px.GrayText else Px.Brown,
            )
            if (task.note.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                // 备注：较小浅色斜体，换行保持对齐（左对齐、固定行高）
                PxText(
                    task.note,
                    size = 11.sp,
                    color = Px.GrayText,
                    fontStyle = FontStyle.Italic,
                )
            }
            if (showDate) {
                Spacer(Modifier.height(2.dp))
                PxText(task.date, size = 10.sp, color = Px.GrayText)
            }
        }
        PixelIconButton(icon = "pencil", size = 26.dp, bg = Px.CreamDark, onClick = onEdit, desc = "编辑任务")
        Spacer(Modifier.width(4.dp))
        PixelIconButton(icon = "trash", size = 26.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除任务")
    }
}

/* ---------------- 任务表单（新增/编辑：日期 + 标题 + 备注） ---------------- */

@Composable
private fun TaskFormDialog(
    store: Store,
    accountId: String,
    initial: TaskItem?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember {
        mutableStateOf(
            initial?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() } ?: defaultDate
        )
    }
    var showCal by remember { mutableStateOf(false) }

    PixelDialog(
        title = if (initial == null) "新增任务" else "编辑任务",
        onDismiss = onDismiss,
        contentScrollable = true,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    if (text.trim().isEmpty()) { store.toast("请填写任务名"); return@PixelButton }
                    val base = initial
                    if (base == null) {
                        store.addTask(accountId, text, note, date)
                    } else {
                        store.updateTask(
                            accountId,
                            base.copy(text = text.trim(), note = note.trim(), date = date.toString()),
                        )
                    }
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("日期", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelPanel(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { showCal = true },
                bg = Px.Cream,
                depth = 2.dp,
                contentPadding = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PxText(Fmt.dateYmd(date), size = 13.sp)
                    Spacer(Modifier.weight(1f))
                    PixelIcon("calendar", size = 18.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            PxText("任务名（≤$MAX_TASK_LEN 字）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = text,
                onValueChange = { text = Fmt.clip(it, MAX_TASK_LEN) },
                placeholder = "如：交房租",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("备注（≤$MAX_TASK_NOTE_LEN 字）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = note,
                onValueChange = { note = Fmt.clip(it, MAX_TASK_NOTE_LEN) },
                placeholder = "可选，写点细节",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showCal) {
        PixelCalendarDialog(
            initial = date,
            onPick = { date = it; showCal = false },
            onDismiss = { showCal = false },
        )
    }
}

/** 任务区块内分隔线 */
@Composable
private fun TaskDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Px.CreamDark))
}
