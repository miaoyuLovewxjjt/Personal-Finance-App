package com.miaoyu03.pixelbook.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/**
 * 32x32 像素画图标库：以 16x16 字符画定义 → 2x 放大渲染为 32x32 ImageBitmap。
 *
 * 字符表（每张图独立调色板）：
 *  .  透明
 *  b  深棕描边    w  奶油白      c  奶油米      g  草绿    G  草绿深
 *  y  暖黄        Y  暖黄深      o  陶土橘      O  陶土橘深  s  天蓝
 *  S  天蓝深      n  木棕        N  木棕深      r  砖红    t  灰
 *  C  封面填充色（动态传入账本封面色）  D  封面深色
 */
object PixelIcons {

    data class Def(val name: String, val palette: Map<Char, Int>, val rows: List<String>)

    // —— 基础色板（每张图以此为底，可覆盖） ——
    private val B = Px.Brown.toArgb(); private val BD = Px.BrownDark.toArgb()
    private val W = Px.Cream.toArgb(); private val C = Px.CreamBg.toArgb()
    private val G = Px.Grass.toArgb(); private val GD = Px.GrassDark.toArgb()
    private val Y = Px.Yellow.toArgb(); private val YD = Px.YellowDark.toArgb()
    private val O = Px.Clay.toArgb(); private val OD = Px.ClayDark.toArgb()
    private val S = Px.Sky.toArgb(); private val SD = Px.SkyDark.toArgb()
    private val N = Px.Wood.toArgb(); private val ND = Px.WoodDark.toArgb()
    private val R = Px.Red.toArgb(); private val T = Px.GrayText.toArgb()

    private val basePalette: Map<Char, Int> = mapOf(
        'b' to B, 'B' to BD, 'w' to W, 'c' to C, 'g' to G, 'G' to GD,
        'y' to Y, 'Y' to YD, 'o' to O, 'O' to OD, 's' to S, 'S' to SD,
        'n' to N, 'N' to ND, 'r' to R, 't' to T,
    )

    /* ================= 账本 ================= */
    val ledger = Def("ledger", basePalette, listOf(
        "................",
        ".bbbbbbbbbbbbbb.",
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCwwwwwwwwwwCb.",
        ".bCwCCCCCCCCwCb.",
        ".bCwCCCCCCCCwCb.",
        ".bCwwwwwwwwwwCb.",
        ".bCCyyCCCCyyCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
    ))

    /* ================= 存款：宝箱（正面视角，中央金锁） ================= */
    val chest = Def("chest", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bYYYYYYYYYYYYb.",
        ".bbbbbbbbbbbbbb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNbbYYYYbbNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bbbbbbbbbbbbbb.",
        "................",
    ))

    /* ================= 金币（理财/总存款） ================= */
    val coin = Def("coin", basePalette, listOf(
        ".......bb.......",
        ".....bbYYbb.....",
        "....bYYYYYYb....",
        "...bYYbbbbYYb...",
        "...bYbYYYYbYb...",
        "..bYYbYYYYbYYb..",
        "..bYYbYbbYbYYb..",
        "..bYYbYYYYbYYb..",
        "..bYYbbbbbbYYb..",
        "...bYbbbbbbYb...",
        "...bYYYYYYYYb...",
        "....bYYYYYYb....",
        ".....bbYYbb.....",
        ".......bb.......",
        "................",
        "................",
    ))

    /* ================= 金币堆（金钱类/存款） ================= */
    val coinPile = Def("coinPile", basePalette, listOf(
        "................",
        "......bb........",
        ".....bYYb.......",
        "..b.bYYYYb......",
        ".bYbbYYYYb......",
        ".bYYYYYYYb..bb..",
        "..bYYYYYbb.bYYb.",
        "...bbbYbb.bYYYb.",
        ".....bYYYbYYYYb.",
        ".....bYYYYYYYYb.",
        "......bbbbbbYb..",
        "..............b.",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 加号（游戏机十字键：一体剪影无交叉线 + 左上高光右下阴影） ================= */
    val plus = Def("plus", basePalette, listOf(
        "................",
        "................",
        "................",
        "......wYYY......",
        "......wYYY......",
        "......wYYY......",
        "..wwYYYYYYYYYY..",
        "..wwYYYYYYYYYY..",
        "..YYYYYYYYYYDD..",
        "..YYYYYYYYYYDD..",
        "......YYYD......",
        "......YYYD......",
        "......YYYD......",
        "................",
        "................",
        "................",
    ))

    /* ================= 右箭头（账本卡片/折叠）居中三角 ================= */
    val chevronR = Def("chevronR", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......bb........",
        ".....bYYb.......",
        "....bYYYYb......",
        "...bYYYYYYb.....",
        "....bYYYYb......",
        ".....bYYb.......",
        "......bb........",
        "................",
        "................",
        "................",
    ))

    /* ================= 编辑（铅笔，对角居中） ================= */
    val pencil = Def("pencil", basePalette, listOf(
        "................",
        "..............b.",
        "............nNb.",
        "...........nNb..",
        "..........nNb...",
        ".........nNb....",
        "........nNb.....",
        ".......nNb......",
        "......nNb.......",
        ".....nNb........",
        "....nNb.........",
        "...nNb..........",
        "..nNb...........",
        "..bOOOb.........",
        ".bOOOOOb........",
        "..bbbbb.........",
    ))

    /* ================= 删除（垃圾桶） ================= */
    val trash = Def("trash", basePalette, listOf(
        "................",
        "....bbbbbb......",
        "....bNyyNbb.....",
        ".bbbNbbbbNbbbb..",
        ".bNNNNNNNNNNNb..",
        ".bNyNNNNNNyNNb..",
        ".bNyyNNNNyyNNb..",
        ".bNyNNNNNNyNNb..",
        ".bNyyNNNNyyNNb..",
        ".bNyNNNNNNyNNb..",
        ".bNyyNNNNyyNNb..",
        ".bNNNNNNNNNNNb..",
        ".bNNNNNNNNNNNb..",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
    ))

    /* ================= 餐饮：汉堡 ================= */
    val burger = Def("burger", basePalette, listOf(
        "................",
        "...bbbbbbbbbb...",
        "..bYYYYYYYYYYb..",
        ".bYYYYYYYYYYYYb.",
        ".bYbYbYYbYbYYb..",
        ".bYYYYYYYYYYYYb.",
        ".bbbbbbbbbbbbbb.",
        ".bGGGGGGGGGGGGb.",
        ".bbbbbbbbbbbbbb.",
        ".bOOOOOOOOOOOOb.",
        ".bObbObbbbObbOb.",
        ".bbbbbbbbbbbbbb.",
        ".bYYYYYYYYYYYYb.",
        ".bYYYYYYYYYYYYb.",
        "..bbbbbbbbbbbb..",
        "................",
    ))

    /* ================= 交通：小汽车 ================= */
    val car = Def("car", basePalette, listOf(
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        ".bbYYYYYYYYYYbb.",
        "bbYbbbbbbbbbbYbb",
        "bYYbSSSSSSSSbYYb",
        "bYYbSSSSSSSSbYYb",
        "bYYYYYYYYYYYYYYb",
        ".bYYYYYYYYYYYYb.",
        ".bbbbbbbbbbbbbb.",
        ".bSbbSSbbSSbbSb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 购物：购物袋 ================= */
    val bag = Def("bag", basePalette, listOf(
        "................",
        ".....bb..bb.....",
        "....bNNb.bNNb...", // hmm
        "...bNNNNNNNNNb..",
        "...bNNNNNNNNNb..",
        "..bOONNNNNNOOb..",
        "..bOOOOOOOOOOb..",
        "..bOObbbbbbOOb..",
        "..bOObbbbbbbOb..",
        "..bOObbbbbbbOb..",
        "..bOObbbbbbbOb..",
        "..bOOOOOOOOOOb..",
        "...bOOOOOOOOb...",
        "....bbbbbbbb....",
        "................",
        "................",
    ))

    /* ================= 娱乐：游戏手柄 ================= */
    val gamepad = Def("gamepad", basePalette, listOf(
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        ".bNnnnnnnnnnnNb.",
        ".bnbNbbbbbbNbnb.",
        ".bnbNbbbbbbNbnb.", // hmm handle placement
        ".bNNbbNbbNbbNNb.",
        ".bNNbbNbbNbbNNb.",
        ".bNNNbbbbbbNNNb.",
        ".bnnbbbnbbbbnnb.",
        ".bnbbbbbbbbbbnb.",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 居住：小房子 ================= */
    val house = Def("house", basePalette, listOf(
        "................",
        ".....bbbbbb.....",
        "...bbOOOOOObb...",
        "..bOOOOOOOOOOb..",
        ".bOOOOOOOOOOOOb.",
        ".bObbbbbbbbbbOb.",
        ".bOwbbbbbbbbwOb.",
        ".bOwbbbbbbbbwOb.", // hmm
        ".bObbwwwwwwbbOb.",
        ".bObbwbbbbwbbOb.",
        ".bObbwbbbbwbbOb.",
        ".bOOOOOOOOOOOOb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
        "................",
    ))

    /* ================= 工资：一叠钞票 ================= */
    val bills = Def("bills", basePalette, listOf(
        "................",
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        "..bwwwwwwwwwgb..",
        "..bwwggggggwgb..",
        "..bwwwwwwwwwgb..",
        "..bwggggggwwgb..",
        "..bwwwwwwwwwgb..",
        "..bwgwwwwgwwgb..",
        "..bwwwwwwwwwgb..",
        "..bggggggggggb..",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
        "................",
    ))

    /* ================= 医疗：小药箱 ================= */
    val medkit = Def("medkit", basePalette, listOf(
        "................",
        "................",
        "..bbbb..bbbb....",
        "..bwwwbbbbww....", // hmm
        ".bwwwwwwwwwwb...",
        ".bwwrrrrrrwwb...",
        ".bwwrrrrrrwwb...",
        ".bwwrrrwwwwb....", // horizontal cross
        ".bwwrrrrrwwb....",
        ".bwwrwwwwwwb....",
        ".bwwwwwwwwwb....",
        ".bwwwwwwwwwb....",
        "..bbbbbbbbb.....",
        "................",
        "................",
        "................",
    ))

    /* ================= 其他：省略号 ================= */
    val dots = Def("dots", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..bbb...bbb..bb.",
        "..btb...btb..btb",
        "..bbb...bbb..bbb",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 收入类：上箭头 + 金币 ================= */
    val income = Def("income", basePalette, listOf(
        "................",
        "......bb........",
        ".....bYYb.......",
        "....bYYYYb......",
        "...bYbbbbYb.....",
        "..bYbYYYYbYb....",
        "..bYbYYYYbYb....",
        "..bYbYbbYbYb....",
        "..bbYbYYbYbb....",
        "...bbbbbbbb.....",
        "......bb........",
        "................",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 支出类：下箭头（陶土橘） ================= */
    val expense = Def("expense", basePalette, listOf(
        "................",
        "......bb........",
        ".....bOOb.......",
        "....bOOOOb......",
        "...bObbbbOb.....",
        "..bObOOOObOb....",
        "..bObOOOObOb....",
        "..bObObbObOb....",
        "..bbObbObbOb....",
        "...bbbbbbbb.....",
        "......bb........",
        "................",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 非金钱类：礼盒（天蓝缎带） ================= */
    val gift = Def("gift", basePalette, listOf(
        "................",
        "....b.b..b.b....",
        "...bSb.bb.bSb...",
        "...bSSbbbbSSb...",
        "....bSSbbSSb....",
        ".bbbbSbbbbSbbbb.",
        ".bOOOSbOObbOOOb.", // hmm bow
        ".bOOOObbbbOOOOb.",
        ".bOObSSbSSbbOOb.",
        ".bOObSSSSSSbOOb.",
        ".bOObSSbSSbbOOb.",
        ".bOObSSbSSbbOOb.",
        ".bOOOOOOOOOOOOb.",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
    ))

    /* ================= 非金钱类(红包)：礼盒红金 ================= */
    val giftRed = Def("giftRed", basePalette, listOf(
        "................",
        "....b.b..b.b....",
        "...bYb.bb.bYb...",
        "...bYYbbbbYYb...",
        "....bYYbbYYb....",
        ".bbbbYbbbbYbbbb.",
        ".bOOOYbOObbOOOb.",
        ".bOOOObbbbOOOOb.",
        ".bOObYYbYYbbOOb.",
        ".bOObYYYYYYbOOb.",
        ".bOObYYbYYbbOOb.",
        ".bOObYYbYYbbOOb.",
        ".bOOOOOOOOOOOOb.",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
    ))

    /* ================= 日历（月/记账） ================= */
    val calendar = Def("calendar", basePalette, listOf(
        "................",
        "....bbbbbbbb....",
        "....byyyyyyb....",
        "..bbbbbbbbbbbb..",
        "..bwwwwwwwwwwwb.", // hmm width
        ".bwwwwwwwwwwwwb.",
        ".bwwbbbbbbbwwb..", // grid lines
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
        "................",
    ))

    /* ================= 日历（年，金带） ================= */
    val calendarGold = Def("calendarGold", basePalette, listOf(
        "................",
        "....bbbbbbbb....",
        "....byyyyyyb....",
        "..bbbbbbbbbbbb..",
        "..bwwwwwwwwwwb..",
        "..bwwbbbbbbwwb..",
        "..bwwwwwwwwwwb..",
        "..bwyywwyywwwb..",
        "..bwwwwwwwwwwb..",
        "..bwwwwwwwwwwb..",
        "..bwwwwwwwwwwb..",
        "..bwwwwwwwwwwb..",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
        "................",
    ))

    /* ================= 天气：晴 ================= */
    val sun = Def("sun", basePalette, listOf(
        "................",
        ".....b....b.....",
        ".....by..yb.....",
        "..b...byyb...b..",
        "...b.bYYYYb.b...",
        "....bYYYYYYb....",
        "..bbYYYYYYYYbb..",
        "..bYYYYYYYYYYb..",
        "..bYYYYYYYYYYb..",
        "..bbYYYYYYYYbb..",
        "....bYYYYYYb....",
        "...b.bYYYYb.b...",
        "..b...byyb...b..",
        ".....by..yb.....",
        ".....b....b.....",
        "................",
    ))

/* ================= 天气：多云（对称居中） ================= */
    val cloud = Def("cloud", basePalette, listOf(
        "................",
        "................",
        "................",
        ".......bb.......",
        ".....bbwwbb.....",
        "....bwwwwwwb....",
        "..bbwwwwwwwwbb..",
        ".bwwwwwwwwwwwwb.",
        "bwwwwwwwwwwwwwwb",
        "bwwwwwwwwwwwwwwb",
        "bwwwwwwwwwwwwwwb",
        ".bwwwwwwwwwwwwb.",
        "..bbbbbbbbbbbb..",
        "................",
        "................",
        "................",
    ))

    /* ================= 天气：雨（云居中 + 雨滴） ================= */
    val rain = Def("rain", basePalette, listOf(
        "................",
        "................",
        "................",
        ".......bb.......",
        ".....bbssbb.....",
        "....bssssssb....",
        "..bbssssssssbb..",
        ".bssssssssssssb.",
        "bssssssssssssssb",
        "bssssssssssssssb",
        ".bssssssssssssb.",
        "..bbbbbbbbbbbb..",
        "....b...b...b...",
        "...bS..bS..bS...",
        "....b...b...b...",
        "................",
    ))

    /* ================= 天气：雪（云居中 + 雪花） ================= */
    val snow = Def("snow", basePalette, listOf(
        "................",
        "................",
        "................",
        ".......bb.......",
        ".....bbwwbb.....",
        "....bwwwwwwb....",
        "..bbwwwwwwwwbb..",
        ".bwwwwwwwwwwwwb.",
        "bwwwwwwwwwwwwwwb",
        "bwwwwwwwwwwwwwwb",
        ".bwwwwwwwwwwwwb.",
        "..bbbbbbbbbbbb..",
        "...bSb..bSb..bSb",
        "....b....b....b.",
        "...bSb..bSb..bSb",
        "................",
    ))

    /* ================= 天气：大风（漩涡风纹 + 三条长短弯曲飘动风线） ================= */
    val windy = Def("windy", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "....ssss........",
        "...ss..s........",
        "..ss....s.......",
        "..ss....ssssss..",
        "...ss..s....s...",
        "....ssss...s....",
        ".........ssss...",
        "............s...",
        "................",
        "................",
        "................",
    ))

    /* ================= 总结：像素柱状图 ================= */
    val statChart = Def("statChart", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "......bYYb......",
        "......bYYb......",
        "......bYYb......",
        "......bYYb..bSSb",
        "......bYYb..bSSb",
        "......bYYb..bSSb",
        "......bYYb..bSSb",
        ".bGGb.bYYb..bSSb",
        ".bGGb.bYYb..bSSb",
        ".bbbb.bbbb..bbbb",
        "................",
        "................",
    ))

    /** 返回箭头（粗实心暖黄，尖朝左，深棕描边） */
    val back = Def("back", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "..bbbbbbbbbbb...",
        "..byyyyyyyyyb...",
        ".byyyyyyyyyyb...",
        ".byyyyyyyyyyb...",
        ".byyyyyyyyyyb...",
        ".byyyyyyyyyyb...",
        "..byyyyyyyyyb...",
        "..bbbbbbbbbbb...",
        "................",
        "................",
        "................",
        "................",
    ))

    /** 下拉箭头：居中菱形 */
    val chevronD = Def("chevronD", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......bb.......",
        "......bYYb......",
        ".....bYYYYb.....",
        "....bYYYYYYb....",
        ".....bYYYYb.....",
        "......bYYb......",
        ".......bb.......",
        "................",
        "................",
        "................",
    ))

    /* ============ 渲染 ============ */

    private val cache = mutableMapOf<String, ImageBitmap>()

    /** 取 32x32 图标位图；ledger 需传封面配色索引 */
    fun get(name: String): ImageBitmap {
        val def = defOf(name)
        return cache.getOrPut(name) { render(def, cover = null) }
    }

    /** 账本封面图标（封面颜色动态） */
    fun ledgerIcon(coverIdx: Int): ImageBitmap {
        val key = "ledger:$coverIdx"
        return cache.getOrPut(key) {
            render(ledger, cover = Px.Covers[coverIdx % Px.Covers.size])
        }
    }

    fun defOf(name: String): Def = when (name) {
        "ledger" -> ledger; "coin" -> coin; "coinPile" -> coinPile; "plus" -> plus
        "chevronR" -> chevronR; "chevronD" -> chevronD; "back" -> back
        "pencil" -> pencil; "trash" -> trash; "burger" -> burger; "car" -> car
        "bag" -> bag; "gamepad" -> gamepad; "house" -> house; "bills" -> bills
        "medkit" -> medkit; "dots" -> dots; "income" -> income; "expense" -> expense
        "gift" -> gift; "giftRed" -> giftRed; "calendar" -> calendar
        "calendarGold" -> calendarGold; "sun" -> sun; "cloud" -> cloud
        "rain" -> rain; "snow" -> snow; "chest" -> chest
        "windy" -> windy; "statChart" -> statChart
        else -> dots
    }

    /** 分类 → 图标名 */
    fun iconOfCategory(category: String): String = when (category) {
        "餐饮" -> "burger"; "交通" -> "car"; "购物" -> "bag"; "娱乐" -> "gamepad"
        "居住" -> "house"; "医疗" -> "medkit"; "工资" -> "bills"; "理财" -> "coin"
        "红包" -> "giftRed"; else -> "dots"
    }

    /** 分类 → 颜色（环形图/条目） */
    fun colorOfCategory(category: String): Color = when (category) {
        "餐饮" -> Px.Clay; "交通" -> Px.Sky; "购物" -> Px.Wood; "娱乐" -> Px.Yellow
        "居住" -> Px.Grass; "医疗" -> Px.Red; "工资" -> Px.Grass; "理财" -> Px.Yellow
        "红包" -> Px.Sky; else -> Px.GrayText
    }

    private fun render(def: Def, cover: Color?): ImageBitmap {
        val coverArgb = cover?.toArgb()
        val coverDarkArgb = cover?.let { c ->
            Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, 1f).toArgb()
        }
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (ry in 0 until 16) {
            val row = def.rows[ry]
            for (rx in 0 until 16) {
                val ch = row[rx]
                if (ch == '.') continue
                val color = when (ch) {
                    'C' -> coverArgb ?: Px.Covers[0].toArgb()
                    'D' -> coverDarkArgb ?: Px.Covers[0].toArgb()
                    else -> def.palette[ch] ?: 0
                }
                // 2x 放大
                bmp.setPixel(rx * 2, ry * 2, color)
                bmp.setPixel(rx * 2 + 1, ry * 2, color)
                bmp.setPixel(rx * 2, ry * 2 + 1, color)
                bmp.setPixel(rx * 2 + 1, ry * 2 + 1, color)
            }
        }
        return bmp.asImageBitmap()
    }
}