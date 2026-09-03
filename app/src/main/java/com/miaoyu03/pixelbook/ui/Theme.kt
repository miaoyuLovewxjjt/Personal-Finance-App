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
import com.miaoyu03.pixelbook.R

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

    // 图表专用：月度/年度总结图
    val ChartIn   = Color(0xFF94D8C3)   // 收入（草绿）
    val ChartOut  = Color(0xFFF4B393)   // 花销（陶土橘）
    val ChartGold = Color(0xFFE4D48F)   // 暖黄备用
    val ChartSky  = Color(0xFF8BC8EA)   // 天蓝备用

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

/**
 * 账本可选字体：像素（默认）/ 可爱风 / 楷体 / 宋体。
 * font 字段值存于 Ledger.font，页面经 LocalLedgerFont 生效。
 */
object LedgerFonts {
    const val PIXEL = "pixel"     // 像素（Zpix，App 默认风格）
    const val CUTE = "cute"       // 可爱风：站酷快乐体
    const val KAITI = "kaiti"     // 楷体：霞鹜文楷
    const val SONG = "songti"     // 宋体：站酷小薇

    val list = listOf(PIXEL, CUTE, KAITI, SONG)

    fun label(f: String): String = when (f) {
        CUTE -> "可爱风"; KAITI -> "楷体"; SONG -> "宋体"; else -> "像素"
    }

    @Composable
    fun family(f: String): FontFamily = when (f) {
        CUTE -> FontFamily(Font(R.font.zcool_kuaile))
        KAITI -> FontFamily(Font(R.font.lxgw_wenkai))
        SONG -> FontFamily(Font(R.font.zcool_xiaowei))
        else -> pixelFontFamily()
    }
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