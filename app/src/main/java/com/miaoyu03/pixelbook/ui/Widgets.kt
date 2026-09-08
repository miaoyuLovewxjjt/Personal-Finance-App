package com.miaoyu03.pixelbook.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miaoyu03.pixelbook.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* ================================================================
 * 像素风全局组件库
 * 硬性规范：2~3dp 深棕硬边框、阶梯硬阴影、无圆角、无渐变、
 *           Zpix 像素字体、字号严格分级、图标 32x32 马赛克。
 * ================================================================ */

/* ---------------- 像素字体 ---------------- */

@Composable
fun pixFont() = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(
        LocalContext.current.resources.getIdentifier("zpix", "font", LocalContext.current.packageName)
    )
)

/**
 * 账本专属字体（CompositionLocal）：
 * 进入某个账本的页面时由 MainActivity 提供其 Ledger.font 对应的字体，
 * PxText 默认读取它，未提供（主页/弹窗）时回退像素 Zpix。
 */
val LocalLedgerFont = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

/* ---------------- 图标 ---------------- */

/**
 * 手绘图标映射（用户手绘 → drawable 资源）。命中的图标直接用位图绘制（按 dp 缩放），
 * 未命中的回退到 16x16 像素字符画 PixelIcons。
 */
val handDrawnIcons: Map<String, Int> = mapOf(
    "trash" to R.drawable.ic_px_trash,
    "back" to R.drawable.ic_px_back,
    "gear" to R.drawable.ic_px_gear,
    "calendarCute" to R.drawable.ic_px_calendar,
    "chest" to R.drawable.ic_px_chest,
    "bankCard" to R.drawable.ic_px_wallet,
    "idcard" to R.drawable.ic_px_account,
    "ledger" to R.drawable.ic_px_ledger,
)

@Composable
fun PixelIcon(name: String, size: Dp = 32.dp, desc: String? = null) {
    val handRes = handDrawnIcons[name]
    if (handRes != null) {
        // 手绘位图：解码为 ImageBitmap 后按像素硬边渲染（FilterQuality.None），保持马赛克感
        val ctx = LocalContext.current
        val bmp = remember(handRes) {
            val opt = android.graphics.BitmapFactory.Options().apply { inScaled = false }
            android.graphics.BitmapFactory.decodeResource(ctx.resources, handRes, opt)?.asImageBitmap()
        }
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = desc,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(size)
                    .then(if (desc != null) Modifier.semantics { this.contentDescription = desc } else Modifier),
            )
            return
        }
    }
    val bmp2 = remember(name) { PixelIcons.get(name) }
    Image(
        bitmap = bmp2,
        contentDescription = desc,
        filterQuality = FilterQuality.None,
        modifier = Modifier
            .size(size)
            .then(if (desc != null) Modifier.semantics { this.contentDescription = desc } else Modifier),
    )
}

/* ---------------- 阶梯硬阴影面板 ---------------- */

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    bg: Color = Px.Cream,
    borderColor: Color = Px.Brown,
    shadow: Boolean = true,
    depth: Dp = 3.dp,
    contentPadding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val borderC = borderColor
    val bgC = bg
    val d = depth
    Box(
        modifier = modifier
            .drawBehind {
                val dw = size.width; val dh = size.height
                val dpv = d.toPx()
                if (shadow) {
                    drawRect(Px.BrownDark, size = Size(dw, dh))
                    drawRect(Px.Brown, topLeft = Offset(0f, dpv), size = Size(dw, dh - dpv))
                    drawRect(Px.WoodDark, topLeft = Offset(dpv, dpv), size = Size(dw - dpv, dh - dpv))
                    drawRect(bgC, topLeft = Offset(dpv * 2, dpv * 2), size = Size(dw - dpv * 2, dh - dpv * 2))
                } else {
                    drawRect(bgC, size = Size(dw, dh))
                }
                val stroke = 2.dp.toPx()
                val inset = if (shadow) dpv * 2 else 0f
                drawRect(
                    borderC,
                    topLeft = Offset(inset, inset),
                    size = Size(dw - inset * 2, dh - inset * 2),
                    style = Stroke(width = stroke)
                )
            }
            .padding(if (shadow) d * 2 + contentPadding else contentPadding)
    ) { content() }
}

/* ---------------- 按钮 ---------------- */

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Px.Grass,
    textColor: Color = Px.Brown,
    height: Dp = 46.dp,
    textSize: TextUnit = 14.sp,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val h = height
    Box(
        modifier = modifier
            .height(h)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .drawBehind {
                val dw = size.width; val dh = size.height
                val dpv = if (pressed) 1.2f.dp.toPx() else 3.dp.toPx()
                drawRect(Px.BrownDark, size = Size(dw, dh))
                drawRect(Px.Brown, topLeft = Offset(0f, dpv), size = Size(dw, dh - dpv))
                drawRect(Px.WoodDark, topLeft = Offset(dpv, dpv), size = Size(dw - dpv, dh - dpv))
                drawRect(
                    if (enabled) bg else Px.GrayText,
                    topLeft = Offset(dpv * 2, dpv * 2),
                    size = Size(dw - dpv * 2, dh - dpv * 2)
                )
                drawRect(
                    Px.Brown,
                    topLeft = Offset(dpv * 2, dpv * 2),
                    size = Size(dw - dpv * 4, dh - dpv * 4),
                    style = Stroke(width = 2.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                PixelIcon(icon, size = h * 0.45f)
                Spacer(Modifier.width(6.dp))
            }
            PxText(text, size = textSize, color = textColor)
        }
    }
}

@Composable
fun PixelIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    bg: Color = Px.Cream,
    desc: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val btnSize = size
    Box(
        modifier = modifier
            .size(btnSize)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .drawBehind {
                val sp = this.size.width
                val dpv = if (pressed) 1.2f.dp.toPx() else 2.5f.dp.toPx()
                drawRect(Px.BrownDark, size = Size(sp, sp))
                drawRect(bg, topLeft = Offset(0f, dpv), size = Size(sp, sp - dpv))
                drawRect(bg, topLeft = Offset(dpv, dpv), size = Size(sp - dpv, sp - dpv))
                drawRect(
                    Px.Brown,
                    topLeft = Offset(dpv, dpv),
                    size = Size(sp - dpv * 2, sp - dpv * 2),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            .then(if (desc != null) Modifier.semantics { this.contentDescription = desc } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon, size = btnSize * 0.6f)
    }
}

/* ---------------- 文字 ---------------- */

@Composable
fun PxText(
    text: String,
    size: TextUnit = 13.sp,
    color: Color = Px.Brown,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    fontStyle: androidx.compose.ui.text.font.FontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
    font: androidx.compose.ui.text.font.FontFamily? = null,   // 显式指定字体（如字体预览）；默认账本字体/像素
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontFamily = font ?: (LocalLedgerFont.current ?: pixFont()),
        fontSize = size,
        fontStyle = fontStyle,
        lineHeight = size * 1.45f,
        textAlign = align,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

/* ---------------- 输入框 ---------------- */

@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    height: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(Px.Cream)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRect(
                    Px.Brown,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
            },
    ) {
        // 占满整个输入框区域：点击框内任意空白处都可聚焦/移动光标
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = pixFont(), fontSize = 14.sp, color = Px.Brown),
            cursorBrush = SolidColor(Px.Brown),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) PxText(placeholder, size = 13.sp, color = Px.GrayText)
                    inner()
                }
            },
        )
    }
}

/* ---------------- 多行输入框（备注/便签等换行文本） ---------------- */

@Composable
fun PixelMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = 100.dp,
    maxLines: Int = 6,
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .background(Px.Cream)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRect(
                    Px.Brown,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
            },
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            maxLines = maxLines,
            textStyle = TextStyle(
                fontFamily = pixFont(), fontSize = 14.sp, color = Px.Brown,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(Px.Brown),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        PxText(placeholder, size = 13.sp, color = Px.GrayText)
                    }
                    inner()
                }
            },
        )
    }
}

/* ---------------- 下拉选择 ---------------- */

data class PixelOption(val name: String, val icon: String? = null, val color: Color = Px.Brown)

@Composable
fun PixelDropdown(
    label: String,
    options: List<PixelOption>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
) {
    var open by remember { mutableStateOf(false) }
    val sel = options.find { it.name == selected }
    PixelPanel(
        modifier = modifier
            .width(width)
            .height(44.dp)
            .clickable { open = true },
        bg = Px.Cream,
        depth = 2.dp,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sel?.icon != null) { PixelIcon(sel.icon, size = 22.dp); Spacer(Modifier.width(6.dp)) }
                PxText(sel?.name ?: "请选择", size = dropdownTextSize(sel?.name?.length ?: 0), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            PixelIcon("chevronD", size = 12.dp)
        }
    }
    if (open) {
        PixelDialog(title = label, onDismiss = { open = false }) {
            options.forEach { opt ->
                val isSel = opt.name == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSel) Px.Grass.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { onSelect(opt.name); open = false }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (opt.icon != null) { PixelIcon(opt.icon, size = 26.dp); Spacer(Modifier.width(8.dp)) }
                    PxText(opt.name, size = dropdownTextSize(opt.name.length), color = opt.color, modifier = Modifier.weight(1f))
                    if (isSel) PixelIcon("chevronR", size = 16.dp)
                }
            }
        }
    }
}

/** 下拉选项文字自适应：越长字号越小，保证超长（如资产 10+20 字）全量显示不截断 */
private fun dropdownTextSize(charLen: Int): TextUnit = when {
    charLen > 18 -> 10.sp
    charLen > 12 -> 11.sp
    charLen > 8 -> 12.sp
    else -> 14.sp
}

/* ---------------- 对话框 ---------------- */

/**
 * 像素风对话框。面板限高不超出屏幕可视区（含软键盘避让）。
 * @param contentScrollable 内容超高时是否让 content 区域滚动、footer 固定。
 *        表单弹窗（内容多 + 需键盘输入）应传 true，并在 content 中不要再包 verticalScroll。
 */
@Composable
fun PixelDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
    contentScrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 整体避让软键盘：键盘弹出时对话框收缩到键盘上方的可视区，不遮挡输入框
        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            // 背景 scrim：只在面板外点击时关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x883A2718))
                    .clickable(onClick = onDismiss),
            )
            // 面板：置于 scrim 之上；面板区域内点击不会冒泡到 scrim（Compose 命中顶层），无需额外吞手势，
            // 否则会拦截内部滚动（表单/长列表滑动失效）
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val availH = maxHeight - 20.dp
                PixelPanel(
                    modifier = modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .widthIn(max = 356.dp)
                        .heightIn(max = availH),
                    bg = Px.Cream,
                    contentPadding = 14.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) { PxText(title, size = 15.sp) }
                        Spacer(Modifier.height(10.dp))
                        if (contentScrollable) {
                            // 内容区弹性可滚（超高时滚动），footer 固定在最底部始终可见
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                            ) { content() }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) { content() }
                        }
                        if (footer != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { footer() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelConfirm(
    title: String,
    message: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PixelDialog(title = title, onDismiss = onDismiss, footer = {
        PixelButton(text = "取消", onClick = onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
        PixelButton(text = confirmText, onClick = { onConfirm(); onDismiss() }, bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp))
    }) {
        PxText(message, size = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/* ---------------- 像素日历弹窗 ---------------- */

/**
 * 日历弹窗：默认选择日期用（无副标题）；
 * 传入 dayBalances（日期 → 当日结余(分)）时，每天下方显示当日结余小字，
 * 正数草绿带「+」、负数陶土深带「-」、恰好为 0 显示灰色 0。
 */
@Composable
fun PixelCalendarDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    dayBalances: Map<LocalDate, Long>? = null,
) {
    var ym by remember { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember { mutableStateOf(initial) }
    val withBalance = dayBalances != null
    // 带结余时格子更高：金额大一些，超长金额在格子内居中换行（最多两行），不凌乱
    val cellH = if (withBalance) 66.dp else 44.dp
    PixelDialog(title = "选择日期", onDismiss = onDismiss) {
        // 月份切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PixelIconButton(icon = "back", size = 34.dp, onClick = { ym = ym.minusMonths(1) }, desc = "上一月")
            PxText("${ym.year}年${ym.monthValue}月", size = 15.sp)
            PixelIconButton(icon = "chevronR", size = 34.dp, onClick = { ym = ym.plusMonths(1) }, desc = "下一月")
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PxText(w, size = 12.sp, color = Px.Wood) }
            }
        }
        Spacer(Modifier.height(2.dp))
        val firstDow = ym.atDay(1).dayOfWeek.value % 7
        val days = ym.lengthOfMonth()
        val cells = MutableList(firstDow) { 0 } + (1..days).toList()
        var idx = 0
        while (idx < cells.size) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) {
                    val v = if (idx < cells.size) cells[idx] else 0
                    idx++
                    val d = if (v > 0) ym.atDay(v) else null
                    val isSel = d == selected
                    val isToday = d == LocalDate.now()
                    val balance = if (d != null) dayBalances?.get(d) else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellH)
                            .then(if (d != null) Modifier.clickable { selected = d; onPick(d) } else Modifier)
                            .background(if (isSel) Px.Grass.copy(alpha = 0.45f) else Color.Transparent)
                            .drawBehind {
                                if (isToday && !isSel) {
                                    val stroke = 2.dp.toPx()
                                    drawRect(
                                        Px.Clay,
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke)
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // 日历数字固定用像素字体（账本字体在小字号下会发虚）
                                PxText(
                                    "$v",
                                    size = 13.sp,
                                    color = if (isSel) Px.GrassDark else if (isToday) Px.ClayDark else Px.Brown,
                                    font = pixFont(),
                                )
                                if (balance != null) {
                                    Spacer(Modifier.height(2.dp))
                                    val (txt, col) = balanceSub(balance)
                                    PxText(
                                        txt,
                                        size = 12.sp,
                                        color = col,
                                        align = TextAlign.Center,
                                        maxLines = 2,
                                        font = pixFont(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日历结余小字：正数带「+」草绿 / 负数带「-」陶土深 / 0 灰。
 * 直接显示实际金额（千分位、小数尾 0 去除）；过宽时在格子内居中换行（最多两行），不缩写。
 */
private fun balanceSub(b: Long): Pair<String, Color> {
    val col = if (b > 0) Px.GrassDark else if (b < 0) Px.ClayDark else Px.GrayText
    if (b == 0L) return "0" to col
    val sign = if (b > 0) "+" else "-"
    val money = com.miaoyu03.pixelbook.data.Fmt.money(kotlin.math.abs(b))
    val txt = sign + if (money.contains('.')) money.trimEnd('0').trimEnd('.') else money
    return txt to col
}

/* ---------------- 像素环形占比图 ---------------- */

data class DonutSeg(val name: String, val color: Color, val ratio: Float)

/**
 * 像素环形图：先在 64x64 离屏位图上绘制，再 FilterQuality.None 放大，
 * 得到硬边马赛克环。点击按下角度映射到分段索引。
 */
@Composable
fun PixelDonut(
    segments: List<DonutSeg>,
    modifier: Modifier = Modifier,
    canvasSize: Dp = 148.dp,
    onSegment: ((Int) -> Unit)? = null,
) {
    val bmp = remember(segments) { renderDonut(segments) }
    val segs = segments
    val pxSize = with(LocalDensity.current) { canvasSize.toPx() }
    Canvas(
        modifier = modifier
            .size(canvasSize)
            .then(
                if (onSegment != null)
                    Modifier.pointerInput(segs) {
                        detectTapGestures { offset -> onSegment(hitSegment(offset, segs, pxSize)) }
                    }
                else Modifier
            ),
    ) {
        if (bmp != null) {
            drawImage(
                image = bmp,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** 64x64 位图上画环形：底色环 → 分段 → 深棕分割线 → 挖空内圆 */
private fun renderDonut(segs: List<DonutSeg>): ImageBitmap? {
    if (segs.isEmpty()) return null
    val n = 64
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = n / 2f; val cy = n / 2f
    val outer = 29f; val inner = 19f
    val p = Paint()

    // 底色环（占比和不足 360° 时露出米色）
    p.color = Px.CreamBg.toArgb()
    c.drawArc(RectF(cx - outer, cy - outer, cx + outer, cy + outer), 0f, 360f, true, p)

    var a0 = -90f
    for (s in segs) {
        if (s.ratio <= 0f) continue
        val sweep = s.ratio * 360f
        val gap = if (segs.size > 1) 1.2f else 0f
        p.color = s.color.toArgb()
        if (sweep - gap > 0.2f) {
            c.drawArc(RectF(cx - outer, cy - outer, cx + outer, cy + outer), a0 + gap / 2f, sweep - gap, true, p)
        }
        a0 += sweep
    }

    // 深棕分割线
    p.color = Px.BrownDark.toArgb()
    p.strokeWidth = 1.5f
    var a = -90f
    for (s in segs) {
        val rad = Math.toRadians(a.toDouble())
        c.drawLine(
            cx + inner * cos(rad).toFloat(), cy + inner * sin(rad).toFloat(),
            cx + outer * cos(rad).toFloat(), cy + outer * sin(rad).toFloat(), p
        )
        a += s.ratio * 360f
    }

    // 挖空内圆（透明）
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    c.drawCircle(cx, cy, inner, p)
    p.xfermode = null

    return bmp.asImageBitmap()
}

/** 点击坐标 → 分段索引（-1 = 未命中环） */
private fun hitSegment(offset: Offset, segs: List<DonutSeg>, pxSize: Float): Int {
    val cx = pxSize / 2f; val cy = pxSize / 2f
    val dx = offset.x - cx; val dy = offset.y - cy
    val r = sqrt(dx * dx + dy * dy)
    if (r < pxSize * 0.26f || r > pxSize * 0.39f) return -1
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (deg < 0) deg += 360f
    var cum = 0f
    for (i in segs.indices) {
        cum += segs[i].ratio * 360f
        if (deg <= cum) return i
    }
    return segs.lastIndex
}

/* ---------------- 像素月度双柱图（收入/花销） ---------------- */

/**
 * 12 个月 × 收入/花销 双柱图。
 * 每月两根柱并排（左草绿收入 / 右陶土橘花销），点击柱 → onBar(month 1..12, isIncome)。
 * 下方自动渲染 1..12 月刻度标签。
 */
@Composable
fun PixelBarChart(
    income: List<Long>,      // 12 个月收入（分），长度不足补 0
    expense: List<Long>,     // 12 个月花销（分）
    showIncome: Boolean = true,
    showExpense: Boolean = true,
    onBar: (month: Int, isIncome: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val in12 = income + List(12 - income.size) { 0L }
    val out12 = expense + List(12 - expense.size) { 0L }
    val maxV = (in12 + out12).maxOrNull()?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(in12, out12, showIncome, showExpense) {
                    detectTapGestures { o ->
                        val w = size.width
                        val mw = w / 12f
                        val m = (o.x / mw).toInt().coerceIn(0, 11)
                        val c1 = m * mw + mw * 0.28f   // 收入柱中心
                        val c2 = m * mw + mw * 0.72f   // 花销柱中心
                        // 命中带略宽于柱宽（柱宽 0.24mw），点柱附近即可命中
                        val halfW = mw * 0.2f
                        when {
                            showIncome && o.x >= c1 - halfW && o.x <= c1 + halfW -> onBar(m + 1, true)
                            showExpense && o.x >= c2 - halfW && o.x <= c2 + halfW -> onBar(m + 1, false)
                        }
                    }
                },
        ) {
            val baseline = size.height - 1.5.dp.toPx()
            val plotTop = 2.dp.toPx()
            val plotH = (size.height - 2.dp.toPx() - 8.dp.toPx()).coerceAtLeast(1f)
            val mw = size.width / 12f

            // 月份分隔线（淡米色）
            for (m in 1..11) {
                drawLine(Px.CreamDark, Offset(m * mw, plotTop), Offset(m * mw, baseline), 1.dp.toPx())
            }
            // 基线（深棕）
            drawLine(Px.Brown, Offset(0f, baseline), Offset(size.width, baseline), 2.dp.toPx())

            // 双柱：每月收入柱在左（草绿）、花销柱在右（陶土橘）
            val barW = (mw * 0.24f).coerceAtMost(16.dp.toPx())
            fun drawBars(list: List<Long>, color: Color, isLeft: Boolean) {
                list.forEachIndexed { i, v ->
                    if (v <= 0) return@forEachIndexed
                    val cx = i * mw + if (isLeft) mw * 0.28f else mw * 0.72f
                    val h = (v.toFloat() / maxV) * plotH
                    val top = baseline - h
                    drawRect(color, topLeft = Offset(cx - barW / 2f, top), size = Size(barW, h))
                    drawRect(
                        Px.Brown,
                        topLeft = Offset(cx - barW / 2f, top),
                        size = Size(barW, h),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
            drawBars(in12, Px.ChartIn, true)
            drawBars(out12, Px.ChartOut, false)
        }
        // 月份刻度
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(12) { i ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PxText("${i + 1}", size = 9.sp, color = Px.GrayText)
                }
            }
        }
    }
}

/* ---------------- 页面头部 ---------------- */

@Composable
fun PixelHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    // 高度自适应：标题较长时自动换行（最多 3 行），短标题保持 56dp 基准。
    // 注意：外层 Box 必须 wrap 内容高度（Row heightIn(min=56.dp)），
    // 若改用 fillMaxSize 的 Row 会把头部撑满整个屏幕。
    Box(modifier = Modifier.fillMaxWidth().background(Px.Wood)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(Px.WoodDark, size = Size(size.width, size.height / 2))
                    drawRect(Px.Wood, topLeft = Offset(0f, size.height / 2), size = Size(size.width, size.height / 2))
                    val stroke = 2.dp.toPx()
                    drawRect(
                        Px.BrownDark,
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke)
                    )
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧占位（返回按钮或等宽占位，保证标题严格居中）
            if (onBack != null) {
                PixelIconButton(icon = "back", size = 38.dp, onClick = onBack, desc = "返回")
            } else {
                Spacer(Modifier.width(38.dp))
            }
            // 标题：居中、与两侧按钮保持间距、长文本换行
            PxText(
                title,
                size = 17.sp,
                color = Px.Cream,
                align = TextAlign.Center,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            // 右侧占位（trailing 或等宽占位）
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            } else {
                Spacer(Modifier.width(38.dp))
            }
        }
    }
}

/* ---------------- 区块标题 ---------------- */

@Composable
fun PixelSectionTitle(
    text: String,
    icon: String? = null,
    color: Color = Px.Brown,
    extra: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { PixelIcon(icon, size = 20.dp); Spacer(Modifier.width(6.dp)) }
        PxText(text, size = 15.sp, color = color)
        if (extra != null) { Spacer(Modifier.weight(1f)); extra() }
    }
}

/* ---------------- 小标签块 ---------------- */

@Composable
fun PixelTag(text: String, bg: Color, textColor: Color = Px.Cream) {
    Box(
        modifier = Modifier
            .background(bg)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRect(Px.Brown, style = Stroke(width = stroke))
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        PxText(text, size = 11.sp, color = textColor)
    }
}

/* ---------------- Toast ---------------- */

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/* ---------------- 两段式显示开关（显示/隐藏 金额等敏感信息，存款明细同款） ---------------- */

@Composable
fun PixelSegSwitch(
    showLabel: String = "显示",
    hideLabel: String = "隐藏",
    hidden: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Px.Cream)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRect(
                    Px.Brown,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
            }
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelSegSwitchSeg(label = showLabel, selected = !hidden, onClick = { if (hidden) onToggle() })
        PixelSegSwitchSeg(label = hideLabel, selected = hidden, onClick = { if (!hidden) onToggle() })
    }
}

@Composable
private fun PixelSegSwitchSeg(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) Px.Grass.copy(alpha = 0.35f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        PxText(label, size = 11.sp, color = if (selected) Px.GrassDark else Px.GrayText)
    }
}