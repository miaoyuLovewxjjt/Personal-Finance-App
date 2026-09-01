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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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

/* ---------------- 图标 ---------------- */

@Composable
fun PixelIcon(name: String, size: Dp = 32.dp, desc: String? = null) {
    val bmp = remember(name) { PixelIcons.get(name) }
    Image(
        bitmap = bmp,
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
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontFamily = pixFont(),
        fontSize = size,
        fontStyle = fontStyle,
        lineHeight = size * 1.45f,
        textAlign = align,
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
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = pixFont(), fontSize = 14.sp, color = Px.Brown),
            cursorBrush = SolidColor(Px.Brown),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) PxText(placeholder, size = 13.sp, color = Px.GrayText)
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
                PxText(sel?.name ?: "请选择", size = 13.sp)
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
                    PxText(opt.name, size = 14.sp, color = opt.color)
                    Spacer(Modifier.weight(1f))
                    if (isSel) PixelIcon("chevronR", size = 16.dp)
                }
            }
        }
    }
}

/* ---------------- 对话框 ---------------- */

@Composable
fun PixelDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景 scrim：只在面板外点击时关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x883A2718))
                    .clickable(onClick = onDismiss),
            )
            // 面板：pointerInput 吞掉自身区域点击，防止冒泡到 scrim 误关
            PixelPanel(
                modifier = modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = 356.dp)
                    .then(Modifier.pointerInput(Unit) { detectTapGestures { } }),
                bg = Px.Cream,
                contentPadding = 14.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { PxText(title, size = 15.sp) }
                    Spacer(Modifier.height(10.dp))
                    Column { content() }
                    if (footer != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { footer() }
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

@Composable
fun PixelCalendarDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var ym by remember { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember { mutableStateOf(initial) }
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
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
                            PxText("$v", size = 13.sp, color = if (isSel) Px.GrassDark else if (isToday) Px.ClayDark else Px.Brown)
                        }
                    }
                }
            }
        }
    }
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

/* ---------------- 页面头部 ---------------- */

@Composable
fun PixelHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(Px.Wood)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧占位（返回按钮或等宽占位，保证标题严格居中）
            if (onBack != null) {
                PixelIconButton(icon = "back", size = 38.dp, onClick = onBack, desc = "返回")
            } else {
                Spacer(Modifier.width(38.dp))
            }
            PxText(title, size = 17.sp, color = Px.Cream, align = TextAlign.Center, modifier = Modifier.weight(1f))
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