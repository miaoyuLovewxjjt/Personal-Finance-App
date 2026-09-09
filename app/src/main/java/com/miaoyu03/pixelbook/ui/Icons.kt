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

    /* ================= 账本（厚书/魔法书：封面 + 书签带 + 页口，无格纹） ================= */
    val ledger = Def("ledger", basePalette, listOf(
        "................",
        "..bbbbbbbbbbbb..",
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCyyCCCCCCyyCb.",   // 书名条(暖黄)
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bCbbCCCCCCbbCb.",   // 装饰
        ".bCCCCCCCCCCCCb.",
        ".bCCCCCCCCCCCCb.",
        ".bNNNNNNNNNNNNb.",   // 底部书脊厚边
        "..bbbbbbbbbbbb..",
        "..yYYYYYYYYYy...",   // 书签带
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

    /* ================= 金币（星露谷风：饱满实心金圆币，左上高光，深描边） ================= */
    val coin = Def("coin", basePalette, listOf(
        ".......bb.......",
        ".....bYYYYb.....",
        "....bYYYYYYb....",
        "...bYyYYYYYYb...",
        "..bYyYYYYYYYYb..",
        "..bYYYYYYYYYYb..",
        "..bYYYYYYYYYYb..",
        "..bYYYYYYYYYYb..",
        "..bYyYYYYYYYYb..",
        "..bYYYYYYYYYYb..",
        "...bYYYYYYYYb...",
        "....bYYYYYYb....",
        ".....bYYYYb.....",
        ".......bb.......",
        "................",
        "................",
    ))

    /* ================= 金币堆/一小摞金币（两枚圆币叠放，错位有层次，左上高光，星露谷道具风） ================= */
    val coinPile = Def("coinPile", basePalette, listOf(
        "................",
        "................",
        ".....bbbbbb.....",
        "....bYYYYYYb....",
        "...bYyYYYYYb....",
        "...bYYYYYYYYb...",
        "...bYYYYYYYYb...",
        "....bYYYYYYb....",
        "..bYYYYYYYYYYb..",
        ".bYyYYYYYYYYYb..",
        ".bYYYYYYYYYYYYb.",
        ".bYYYYYYYYYYYYb.",
        "..bbbbbbbbbbbb..",
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

    /* ================= 展开箭头（朝右开口）：标准实心三角 尖朝右（▶）。左边缘竖直笔直、向右收尖、上下对称。 ================= */
    val chevronR = Def("chevronR", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "...YY...........",
        "...YYYY.........",
        "...YYYYYY.......",
        "...YYYYYYYY.....",
        "...YYYYYY.......",
        "...YYYY.........",
        "...YY...........",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 左箭头（朝左开口）：标准实心三角 尖朝左（◀）。右边缘竖直笔直、向左收尖、上下对称。 ================= */
    val chevronL = Def("chevronL", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "..........YY....",
        "........YYYY....",
        "......YYYYYY....",
        "....YYYYYYYY....",
        "......YYYYYY....",
        "........YYYY....",
        "..........YY....",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 微信（绿色对话气泡，两只白眼睛） ================= */
    val chat = Def("chat", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "..bbbbbbbbbbb...",
        ".bGGGGGGGGGGb...",
        ".bGbbbbbbbGGb...",
        ".bGbwwbwwbGGb...",
        ".bGwwbwwbwGGb...",
        ".bGGGGGGGGGGb...",
        ".bGGGGGGGbbb....",
        "..bbbbbbbbb.....",
        "................",
        "................",
        "................",
    ))

    /* ================= 支付宝（天蓝盾牌 + 白芯，可爱风） ================= */
    val alipay = Def("alipay", basePalette, listOf(
        "................",
        "................",
        "................",
        ".....bSSSSb.....",
        "....bSSSSSSb....",
        "...bSSSSSSSSb...",
        "..bSSSbbbbSSSb..",
        "..bSSbwwwwbSSb..",
        "..bSSbwwwwbSSb..",
        "..bSSbwwwwbSSb..",
        "...bSSSSSSSSb...",
        "....bSSSSSSb....",
        ".....bSSSSb.....",
        "......bbbb......",
        "................",
        "................",
    ))

    /* ================= 账户（身份证卡片：木棕描边卡身 + 左上人像 + 右侧姓名/编号条） ================= */
    val idcard = Def("idcard", basePalette, listOf(
        "................",
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        ".bNNNNNNNNNNNNb.",
        ".bNwwwwwwwwwwNb.",
        ".bNwyywbbwwwwNb.",   // 人像头
        ".bNwyywbbwyywNb.",   // 人像脸+肩
        ".bNwwwbbwwwwwNb.",
        ".bNwbbbwwwwwwNb.",
        ".bNwwwwwwwwwwNb.",
        ".bNwYYYYYYYwwNb.",   // 姓名条
        ".bNwwwwwwwwwwNb.",
        ".bNNNNNNNNNNNNb.",
        ".bbbbbbbbbbbbbb.",
        "................",
    ))

    /* ================= 资产账户（钱袋子：束口袋身 + 收口 + 币标） ================= */
    val bankCard = Def("bankCard", basePalette, listOf(
        "................",
        "......bbbb......",
        ".....bYYYYb.....",
        ".....byyyyb.....",   // 束口带
        "....bNNNNNNb....",
        "...bNNNNNNNNb...",
        "..bNNNNNNNNNNb..",
        "..bNNbNNNNbNNb..",   // 袋身
        "..bNNbYYYYbNNb..",
        "..bNNNNNNNNNNb..",
        "..bNNNNNNNNNNb..",
        "...bNNNNNNNNb...",
        "....bNNNNNNb....",
        ".....bbbbbb.....",
        "................",
        "................",
    ))

    /* ================= 记账明细图标（账单/票据：标题条 + 三行明细横线 + 底部锯齿） ================= */
    val book = Def("book", basePalette, listOf(
        "................",
        "................",
        "..bbbbbbbbbbbb..",
        "..byyyyyyyyyyb..",   // 标题条(暖黄)
        "..bwwwwwwwwwwb..",
        "..bwbbbwbbbwwb..",   // 明细行
        "..bwwwwwwwwwwb..",
        "..bwbbbwbbbwwb..",
        "..bwwwwwwwwwwb..",
        "..bwbbbwbbbwwb..",
        "..bwwwwwwwwwwb..",
        "..bbbbbbbbbbbb..",
        "....bbb..bbb....",   // 锯齿底
        "...bbb....bbb...",
        "................",
        "................",
    ))

    /* ================= 折叠导航：纯三角（左）——无框，展开态点击收起 ================= */
    val triL = Def("triL", basePalette, listOf(
        "................",
        "................",
        "................",
        "......bbb.......",
        ".....byyyb......",
        "....byyyyyb.....",
        "...byyyyyyyb....",
        "..byyyyyyyyyb...",
        "...byyyyyyyb....",
        "....byyyyyb.....",
        ".....byyyb......",
        "......bbb.......",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 折叠导航：纯三角（右）——无框，收起态点击展开 ================= */
    val triR = Def("triR", basePalette, listOf(
        "................",
        "................",
        "................",
        ".......bbb......",
        "......byyyb.....",
        ".....byyyyyb....",
        "....byyyyyyyb...",
        "...byyyyyyyyyb..",
        "....byyyyyyyb...",
        ".....byyyyyb....",
        "......byyyb.....",
        ".......bbb......",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 折叠导航：方框 + 实心左箭头（收起） ================= */
    val collapseL = Def("collapseL", basePalette, listOf(
        "................",
        ".bbbbbbbbbbbbbb.",
        ".bwwwwwwwwwwwwb.",
        ".bwyywwwwwwwwwb.",
        ".bwYYYYwwwwwwwb.",
        ".bwYYYYYYYYwwwb.",
        ".bYYYYYYYYYYYYb.",
        ".bYYYYYYYYYYYYb.",
        ".bwYYYYYYYYwwwb.",
        ".bwYYYYwwwwwwwb.",
        ".bwyywwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
    ))

    /* ================= 折叠导航：方框 + 实心右箭头（展开） ================= */
    val collapseR = Def("collapseR", basePalette, listOf(
        "................",
        ".bbbbbbbbbbbbbb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwyywb.",
        ".bwwwwwwwYYYYwb.",
        ".bwwwYYYYYYYYwb.",
        ".bYYYYYYYYYYYYb.",
        ".bYYYYYYYYYYYYb.",
        ".bwwwYYYYYYYYwb.",
        ".bwwwwwwwYYYYwb.",
        ".bwwwwwwwwwyywb.",
        ".bwwwwwwwwwwwwb.",
        ".bwwwwwwwwwwwwb.",
        ".bbbbbbbbbbbbbb.",
        "................",
        "................",
    ))

    /* ================= 编辑（铅笔：粗笔身 + 木质 + 笔尖） ================= */
    val pencil = Def("pencil", basePalette, listOf(
        "................",
        "................",
        ".............bb.",
        "............bNNb",
        "...........bNNNb",
        "..........bNNNNb",
        ".........bNNNNb.",
        "........bYYYYb..",
        ".......bYYYYb...",
        "......bYYYYb....",
        ".....bYYYYb.....",
        "....bYYYYb......",
        "...bNNNNb.......",
        "..bNNNNb........",
        "..bbbb..........",
        "................",
    ))

    /* ================= 删除（垃圾桶：铁皮垃圾箱正视——宽盖 + 开口 + 微梯形桶身 + 竖纹） ================= */
    private val trashPalette = basePalette + mapOf(
        'r' to 0xFF8B3A2E.toInt(),   // 暗红棕(桶身)
        'R' to 0xFF5F2A20.toInt(),   // 更暗(盖/开口/竖纹/底)
    )
    val trash = Def("trash", trashPalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "..bbbbbbbbbbbb..",   // 盖(深棕描边)
        "..bRRRRRRRRRRb..",   // 盖面(深红)
        "..brrrrrrrrrrb..",   // 盖沿
        "...bRrRrRrRrb...",   // 桶身开口(微收)
        "...brRrrRrrRb...",
        "...brRrrRrrRb...",
        "...brRrrRrrRb...",
        "...bRrrrrrrRb...",
        "....bRRRRRRb....",   // 底收
        "....bbbbbbbb....",
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

    /* ================= 日历（明细导航，棕色可爱·直面图）：挂环顶条 + 木框 + 奶油纸页 + 3×3 日期点阵 ================= */
    val calendarCute = Def("calendarCute", basePalette, listOf(
        "................",
        "....bbbbbbbb....",
        "....byyyyyyb....",
        "..bbbbbbbbbbbb..",
        "..bnnnnnnnnnnb..",
        ".bnnwwwwwwwwNNb.",
        ".bnnwbwwbwwbNNb.",
        ".bnnwwwwwwwwNNb.",
        ".bnnwbwwbwwbNNb.",
        ".bnnwwwwwwwwNNb.",
        ".bnnwbwwbwwbNNb.",
        ".bnnwwwwwwwwNNb.",
        ".bnnnnnnnnnnnnb.",
        ".bbbbbbbbbbbbbb.",
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

    /** 返回箭头：标准「←」——实心尖三角 + 自三角中部向右伸出的细水平杆 */
    val back = Def("back", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        ".....bbb........",
        "....byyyb.......",
        "...byyyybbbbbb..",
        "...byyyybbbbbb..",
        "....byyyb.......",
        ".....bbb........",
        "................",
        "................",
        "................",
        "................",
        "................",
    ))

    /** 下拉/折叠收起箭头：标准倒三角 尖朝下（▼）。顶边水平、左右对称、向下收尖；尺寸与 chevronR 一致。 */
    val chevronD = Def("chevronD", basePalette, listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "....YYYYYYY.....",
        "....YYYYYYY.....",
        ".....YYYYY......",
        ".....YYYYY......",
        "......YYY.......",
        "......YYY.......",
        ".......Y........",
        "................",
        "................",
        "................",
        "................",
    ))

    /* ================= 设置：齿轮（8 齿木棕 + 中心方孔黄芯） ================= */
    val gear = Def("gear", basePalette, listOf(
        "................",
        ".....bbNNbb.....",
        "...bbNNNNNNbb...",
        "..bNNNNNNNNNNb..",
        "..bNNNNNNNNNNb..",
        ".bNNNNNNNNNNNNb.",
        ".bNNNbbbbbbNNNb.",
        "bNNNNbYYYYbNNNNb",
        ".bNNNbbbbbbNNNb.",
        ".bNNNNNNNNNNNNb.",
        "..bNNNNNNNNNNb..",
        "..bNNNNNNNNNNb..",
        "...bbNNNNNNbb...",
        ".....bbNNbb.....",
        "................",
        "................",
    ))

    /* ================= 导出：像素打印机（中部纸张伸出 + 机身 + 出纸口 + 托盘，居中） ================= */
    val export = Def("export", basePalette, listOf(
        "................",
        "................",
        "....bbbbbbbb....",
        "...bwwwwwwwwb...",
        "...bwwwwwwwwb...",
        "...bbbbbbbbbb...",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNYYYYNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNNNNNNNNNNNNb.",
        ".bNbbNNNNNNbbNb.",
        ".bbbbbbbbbbbbbb.",
        ".bbbbbbbbbbbbbb.",
        "................",
    ))

    /* ================= 角色头像：短发男子（深棕短发 + 草绿上衣，星露谷风大头像） ================= */
    val avatarMan = Def("avatarMan", basePalette, listOf(
        "................",
        "................",
        "....bbbbbbb.....",
        "...bnnnnnnnb....",
        "..bnnnnnnnnnb...",
        "..bnnwwwwwwwnb..",   // 短发帘 + 额头
        "..bwwwwwwwwwwb..",   // 脸
        "..bwwbwwwwbwwb..",   // 眼睛
        "..bwwwwwwwwwwb..",
        "..bwwwwwwwwwwb..",
        "..bwwwwwwwwwwb..",
        "...bwwwwwwwwb...",   // 下巴收
        "....bwwwwwwb....",
        "....bbbbbbbb....",   // 颈肩
        ".bbggggggggggbb.",   // 上衣（草绿）
        "..bbbbbbbbbbbb..",
    ))

            /* ================= 角色头像：长发女子（深棕长发垂肩 + 陶土橘上衣） ================= */
    val avatarWoman = Def("avatarWoman", basePalette, listOf(
"................",
"................",
"....bbbbbbb.....",
"...bnnnnnnnb....",
".bnnnnnnnnnnnnb.",
".bnnnnnnnnnnnnb.",
".bnnwwwwwwwwnnb.",
".bwwwwwwwwwwwwb.",
".bwwbbwwwwbbwwb.",
".bwwwwwwwwwwwwb.",
".bnwwwwwwwwwwnb.",
".bnnnnnnnnnnnnb.",
".bnnoooooooonnb.",
".bnnoooooooonnb.",
".bnnnnnnnnnnnnb.",
"..bbbbbbbbbbbb.."
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
        "chevronR" -> chevronR; "chevronL" -> chevronL; "chevronD" -> chevronD; "back" -> back
        "collapseL" -> collapseL; "collapseR" -> collapseR
        "triL" -> triL; "triR" -> triR
        "bankCard" -> bankCard; "book" -> book; "chat" -> chat; "alipay" -> alipay
        "idcard" -> idcard
        "pencil" -> pencil; "trash" -> trash; "burger" -> burger; "car" -> car
        "bag" -> bag; "gamepad" -> gamepad; "house" -> house; "bills" -> bills
        "medkit" -> medkit; "dots" -> dots; "income" -> income; "expense" -> expense
        "gift" -> gift; "giftRed" -> giftRed; "calendar" -> calendar
        "calendarGold" -> calendarGold; "calendarCute" -> calendarCute; "sun" -> sun; "cloud" -> cloud
        "rain" -> rain; "snow" -> snow; "chest" -> chest
        "windy" -> windy; "statChart" -> statChart
        "gear" -> gear; "export" -> export
        "avatarMan" -> avatarMan; "avatarWoman" -> avatarWoman
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