package com.miaoyu03.pixelbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============ 像素风调色板：低饱和暖色 ============ */
object Px {
    val CreamBg   = Color(0xFFF2E6C8)   // 奶油米色背景
    val Cream     = Color(0xFFFBF4E0)   // 奶油白（卡片）
    val CreamDark = Color(0xFFE8D9B4)   // 背景噪点深一档
    val Wood      = Color(0xFF8A5A33)   // 木棕
    val WoodDark  = Color(0xFF6E4526)   // 木棕深
    val Brown     = Color(0xFF4E3421)   // 深棕（描边/主文字）
    val BrownDark = Color(0xFF3A2718)   // 深棕（阴影最深）
    val Grass     = Color(0xFF7FAF4B)   // 草绿
    val GrassDark = Color(0xFF5E8A33)
    val Yellow    = Color(0xFFE8B84B)   // 暖黄
    val YellowDark= Color(0xFFC9972F)
    val Clay      = Color(0xFFD97B4A)   // 陶土橘
    val ClayDark  = Color(0xFFB85F35)
    val Sky       = Color(0xFF7FB5C8)   // 天蓝
    val SkyDark   = Color(0xFF5E93A8)
    val GrayText  = Color(0xFF9C8B72)   // 灰色小字
    val Red       = Color(0xFFC7504A)   // 医疗十字（低饱和砖红）

    /** 账本封面配色（5 套） */
    val Covers = listOf(
        Color(0xFF8B9D4E), // 草绿
        Color(0xFFD97B4A), // 陶土橘
        Color(0xFF7FB5C8), // 天蓝
        Color(0xFFE8B84B), // 暖黄
        Color(0xFFB4885C), // 浅木棕
    )
}

/* ============ 像素字体（Zpix 最像素 12px） ============ */
@Composable
fun pixelFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return FontFamily(Font(ctx.resources.getIdentifier("zpix", "font", ctx.packageName), FontWeight.Normal))
}

/** 全局字号等级：同等级内容同一字号（硬性规范） */
object PxType {
    val Title   = 22.sp   // 页面大标题
    val Heading = 16.sp   // 区块标题
    val Body    = 13.sp   // 正文/字段值
    val Small   = 11.sp   // 注释/辅助
    val Tiny    = 9.sp    // 极小的标签
}

/* ============ 奶油米色像素纹理（棋盘格小砖） ============ */
/**
 * 深浅两档米色小砖交错铺满 → 奶油像素纹理背景。
 * 纯 drawBehind 实现，无渐变无平滑。
 */
@Composable
fun Modifier.creamTexture(): Modifier {
    val c1 = Px.CreamBg.toArgb()
    val c2 = Color(0xFFEBDBB6).toArgb()   // 深一档的米色砖
    return this.drawBehind {
        val unit = 8.dp.toPx()
        val cols = (size.width / unit).toInt() + 1
        val rows = (size.height / unit).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = Color(if (((c + r) % 2) == 0) c1 else c2),
                    topLeft = Offset(c * unit, r * unit),
                    size = Size(unit, unit),
                )
            }
        }
    }
}