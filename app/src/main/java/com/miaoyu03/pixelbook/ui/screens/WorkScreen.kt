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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Cents
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.MAX_COMMUTE_LEN
import com.miaoyu03.pixelbook.data.MAX_OCCUPATION_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.WorkProfile
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/* ================================================================
 * 职业（个人收入形象卡）：
 *  - 月薪 / 工作日（可多选）/ 上下班时间 / 通勤路线（可在页面内编辑）
 *  - 本月工资（按已出勤工作日计提）：月薪 ÷ 本月工作日数 × 本月已出勤天数
 *  - 已打工天数（本月出勤）
 *  - 距离今天下班还有多久（仅工作日；非工作日/已下班显示提示）
 * 入口：角色卡「职业 · xxx」行点击跳转。
 * ================================================================ */

/** HH:mm 解析（失败返回兜底 09:00 / 18:00） */
private fun parseHm(s: String, fallback: LocalTime): LocalTime =
    runCatching { LocalTime.parse(s.trim().padStart(5, '0')) }.getOrDefault(fallback)

/** 本月（截至今天）出勤的工作日天数：按 workDays 匹配星期，且日期 ≤ 今天 */
private fun workedDaysThisMonth(p: WorkProfile, today: LocalDate): Int {
    val ym = YearMonth.from(today)
    var n = 0
    for (d in 1..today.dayOfMonth) {
        if (ym.atDay(d).dayOfWeek.value in p.workDays) n++
    }
    return n
}

/** 本月工作日总数（整月） */
private fun totalWorkDaysThisMonth(p: WorkProfile, today: LocalDate): Int {
    val ym = YearMonth.from(today)
    var n = 0
    for (d in 1..ym.lengthOfMonth()) {
        if (ym.atDay(d).dayOfWeek.value in p.workDays) n++
    }
    return n
}

@Composable
fun WorkScreen(
    store: Store,
    accountId: String,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // 每 30 秒刷一次「距下班倒计时」（页面可见期间）
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }

    val profile = remember(tick, accountId) { store.workProfile(accountId) }
    val today = now.toLocalDate()
    val isWorkDay = today.dayOfWeek.value in profile.workDays
    val workedDays = remember(profile, today) { workedDaysThisMonth(profile, today) }
    val monthWorkDays = remember(profile, today) { totalWorkDaysThisMonth(profile, today) }
    val monthIncome = if (monthWorkDays > 0) profile.monthlySalary * workedDays / monthWorkDays else 0L

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "职业", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // 个人收入形象卡：职业名 + 月薪 + 工作量 + 倒计时
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 14.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon("briefcase", size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            PxText(
                                profile.occupation.ifEmpty { "未设置职业" },
                                size = 17.sp, color = Px.Brown,
                            )
                            Spacer(Modifier.height(2.dp))
                            PxText(
                                if (profile.monthlySalary > 0) "月薪 ${Fmt.yen(profile.monthlySalary)}" else "月薪未设置",
                                size = 12.sp, color = Px.GrayText,
                            )
                        }
                        PixelButton(
                            text = "设置",
                            onClick = { showEdit = true },
                            bg = Px.Wood, height = 34.dp, textSize = 12.sp,
                            modifier = Modifier.width(76.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))

                    // 本月工资（大字）
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        PxText("本月工资", size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(2.dp))
                        PxText(Fmt.yen(monthIncome), size = 21.sp, color = Px.Brown, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(2.dp))
                        PxText(
                            "已出勤 $workedDays / $monthWorkDays 天（按工作日计提）",
                            size = 11.sp, color = Px.GrayText,
                            align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))

                    // 已打工天数 / 距下班倒计时
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoCell(
                            label = "本月已打工",
                            value = "$workedDays 天",
                            modifier = Modifier.weight(1f),
                        )
                        InfoCell(
                            label = "距离下班",
                            value = offWorkText(now, profile, isWorkDay),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 工作信息卡：上班/下班/通勤路线
            PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                bg = Px.Cream,
                contentPadding = 12.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxText("上班时间", size = 12.sp, color = Px.GrayText)
                        Spacer(Modifier.weight(1f))
                        PxText(profile.workStart, size = 14.sp, color = Px.Brown)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxText("下班时间", size = 12.sp, color = Px.GrayText)
                        Spacer(Modifier.weight(1f))
                        PxText(profile.workEnd, size = 14.sp, color = Px.Brown)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxText("工作日", size = 12.sp, color = Px.GrayText)
                        Spacer(Modifier.weight(1f))
                        PxText(workDaysText(profile.workDays), size = 14.sp, color = Px.Brown)
                    }
                    if (profile.commute.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            PxText("通勤路线", size = 12.sp, color = Px.GrayText)
                            Spacer(Modifier.weight(1f))
                            PxText(
                                profile.commute, size = 14.sp, color = Px.Brown,
                                align = TextAlign.End, modifier = Modifier.weight(2f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showEdit) {
        WorkEditDialog(
            store = store,
            accountId = accountId,
            initial = profile,
            onDismiss = { showEdit = false },
            onSaved = { showEdit = false; tick++ },
        )
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Px.CreamDark))
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PxText(label, size = 11.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(3.dp))
        PxText(value, size = 15.sp, color = Px.Brown, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** 距离下班文案：非工作日 / 未上班 / 已下班 / 剩余 x 小时 y 分钟 */
private fun offWorkText(now: LocalDateTime, p: WorkProfile, isWorkDay: Boolean): String {
    if (!isWorkDay) return "今天休息"
    val start = parseHm(p.workStart, LocalTime.of(9, 0))
    val end = parseHm(p.workEnd, LocalTime.of(18, 0))
    val t = now.toLocalTime()
    return when {
        t < start -> "还没上班"
        t >= end -> "已下班"
        else -> {
            val mins = Duration.between(t, end).toMinutes().coerceAtLeast(0)
            val h = mins / 60; val m = mins % 60
            if (h > 0) "$h 小时 $m 分" else "$m 分钟"
        }
    }
}

private fun workDaysText(days: List<Int>): String {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    if (days.isEmpty()) return "未设置"
    if (days.sorted() == listOf(1, 2, 3, 4, 5)) return "周一 ~ 周五"
    if (days.sorted() == listOf(1, 2, 3, 4, 5, 6, 7)) return "每天"
    return days.sorted().joinToString("、") { "周${names[it - 1]}" }
}

/* ================================================================
 * 职业设置弹窗：职业名 / 月薪 / 工作日（多选）/ 上下班时间 / 通勤路线
 * ================================================================ */

@Composable
private fun WorkEditDialog(
    store: Store,
    accountId: String,
    initial: WorkProfile,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var occupation by remember { mutableStateOf(initial.occupation) }
    var salaryStr by remember { mutableStateOf(if (initial.monthlySalary > 0) Fmt.money(initial.monthlySalary) else "") }
    var days by remember { mutableStateOf(initial.workDays.toSet()) }
    var start by remember { mutableStateOf(initial.workStart) }
    var end by remember { mutableStateOf(initial.workEnd) }
    var commute by remember { mutableStateOf(initial.commute) }

    PixelDialog(
        title = "职业设置",
        onDismiss = onDismiss,
        contentScrollable = true,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(salaryStr)
                    if (salaryStr.isNotBlank() && v == null) { store.toast("请填写有效月薪"); return@PixelButton }
                    if (!Regex("^\\d{1,2}:\\d{2}$").matches(start.trim())) { store.toast("上班时间格式：HH:mm"); return@PixelButton }
                    if (!Regex("^\\d{1,2}:\\d{2}$").matches(end.trim())) { store.toast("下班时间格式：HH:mm"); return@PixelButton }
                    val ok = store.setWorkProfile(
                        accountId,
                        WorkProfile(
                            occupation = occupation.trim(),
                            monthlySalary = v ?: 0L,
                            workDays = days.sorted(),
                            workStart = start.trim(),
                            workEnd = end.trim(),
                            commute = commute.trim(),
                        ),
                    )
                    if (ok) onSaved() else store.toast("保存失败，请重试")
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("职业", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = occupation,
                onValueChange = { occupation = Fmt.clip(it, MAX_OCCUPATION_LEN) },
                placeholder = "如：程序员、教师",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("月薪（元）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = salaryStr,
                onValueChange = { salaryStr = Fmt.cleanAmountInput(it) },
                placeholder = "如：12000",
                numeric = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("工作日（可多选）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    val on = d in days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(if (on) Px.Grass.copy(alpha = 0.28f) else Px.CreamBg)
                            .clickable { days = if (on) days - d else days + d }
                            .drawBehind {
                                drawRect(
                                    if (on) Px.Grass else Px.Brown,
                                    style = Stroke(if (on) 3.dp.toPx() else 2.dp.toPx()),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PxText(
                            listOf("一", "二", "三", "四", "五", "六", "日")[d - 1],
                            size = 13.sp,
                            color = if (on) Px.GrassDark else Px.Brown,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    PxText("上班时间", size = 12.sp, color = Px.GrayText)
                    Spacer(Modifier.height(4.dp))
                    PixelTextField(
                        value = start,
                        onValueChange = { start = it.take(5) },
                        placeholder = "09:00",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    PxText("下班时间", size = 12.sp, color = Px.GrayText)
                    Spacer(Modifier.height(4.dp))
                    PixelTextField(
                        value = end,
                        onValueChange = { end = it.take(5) },
                        placeholder = "18:00",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PxText("通勤路线", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = commute,
                onValueChange = { commute = Fmt.clip(it, MAX_COMMUTE_LEN) },
                placeholder = "如：地铁 2 号线 → 换乘 4 号线 → 步行 800m",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PxText(
                "本月工资按「月薪 ÷ 本月工作日数 × 已出勤天数」自动计提",
                size = 11.sp, color = Px.GrayText,
            )
        }
    }
}
