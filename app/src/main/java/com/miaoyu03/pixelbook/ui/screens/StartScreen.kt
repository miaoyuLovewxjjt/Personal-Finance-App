package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Account
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTag
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText

/* ================================================================
 * 新首页（四季记账 · 启动屏）
 * 游戏风农场背景 + 居中标题 + 卷轴选号 + 右下公告牌「开始记账」
 * 右上角齿轮 → 设置（含账号管理）
 * ================================================================ */

/**
 * 真正首页。onStart(id)：选好账号后按「开始记账」进入目录页。
 * 首次展示无账户时，自动弹出「新建账户」引导。
 */
@Composable
fun StartScreen(
    store: Store,
    onStart: (String) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }      // 卷轴下拉
    var showSettings by remember { mutableStateOf(false) }    // 右上设置
    var addingAccount by remember { mutableStateOf(false) }
    var firstRun by remember { mutableStateOf(store.accounts().isEmpty()) }

    // 最近账号列表（updated 倒序）
    val accounts = remember(tick) { store.accountsByRecent() }
    val curId = remember(tick, accounts) { store.currentAccountId() }
    val cur = accounts.firstOrNull { it.id == curId }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1) 游戏风背景（占满整屏、绘于最底层）
        FarmBackground()

        // 2) 主内容区（压在背景上）
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(46.dp))
            // 标题「四季记账」
            AppTitle()
            Spacer(Modifier.weight(1f))

            // 底部控件区：卷轴选号 + 公告牌
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 卷轴：显示当前账号；点击展开下拉
                if (accounts.isNotEmpty()) {
                    ScrollPicker(
                        account = cur,
                        onClick = { showPicker = true },
                    )
                }
                Spacer(Modifier.height(18.dp))
                // 右下公告牌「开始记账」
                BoardButton(enabled = cur != null) {
                    cur?.let { onStart(it.id) }
                }
            }
        }

        // 3) 右上角设置齿轮
        PixelIconButton(
            icon = "gear",
            size = 36.dp,
            onClick = { showSettings = true },
            desc = "设置",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp),
        )
    }

    // 账号下拉（从卷轴位置弹出）
    if (showPicker) {
        PixelDialog(title = "切换账号", onDismiss = { showPicker = false }, contentScrollable = true) {
            accounts.forEach { a ->
                val isCur = a.id == cur?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCur) Px.Grass.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            store.setCurrentAccountId(a.id)
                            showPicker = false
                            tick++
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon("idcard", size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    PxText(a.name, size = 14.sp, color = if (isCur) Px.GrassDark else Px.Brown, modifier = Modifier.weight(1f))
                    if (isCur) PixelTag("当前", bg = Px.Grass, textColor = Px.Cream)
                }
            }
            Spacer(Modifier.height(8.dp))
            PixelButton(
                text = "＋ 新建账户",
                onClick = { showPicker = false; addingAccount = true },
                bg = Px.Yellow, height = 40.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    // 新建账户
    if (addingAccount) {
        AccountNameDialog(
            title = "新建账户",
            initial = "",
            hint = "账户名（不可与已有账户同名）",
            onSave = { nm -> store.addAccount(nm) != null },
            onDismiss = { addingAccount = false },
            onSaved = { addingAccount = false; firstRun = false; tick++ },
            dupHint = { store.toast("账户名无效或已存在") },
        )
    }
    // 设置
    if (showSettings) {
        SettingsDialog(
            store = store,
            accountId = cur?.id ?: "",
            onDismiss = { showSettings = false },
            onStorageChanged = { tick++ },
        )
    }
    // 首次启动且无账户 → 引导新建
    if (firstRun) {
        PixelDialog(title = "欢迎使用四季记账", onDismiss = { firstRun = false }, footer = {
            PixelButton("取消", { firstRun = false }, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "新建账户",
                { firstRun = false; addingAccount = true },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(120.dp),
            )
        }) {
            PxText("还没有账户。先新建一个账户，账户下可建立多个账本，开始你的四季记账吧！", size = 13.sp)
        }
    }
}

/* ================================================================
 * 农场游戏风背景（自绘像素块）
 * 图层：天空(日光渐变) → 云 → 远山 → 草地 → 池塘 → 木屋 → 树/围栏/点缀
 * 所有形状用硬边色块，无渐变无圆角（星露谷质感）
 * ================================================================ */

// —— 星露谷风配色（比主题略艳一些，游戏感） ——
private val SkyHi    = Color(0xFF8CC8E8)   // 天顶蓝
private val SkyMid   = Color(0xFFA8D8EF)   // 中天蓝
private val SkyLo    = Color(0xFFE8F0D8)   // 地平线浅青白
private val SunC     = Color(0xFFFFE98A)   // 暖阳黄
private val SunEdge  = Color(0xFFF5CB5C)   // 阳边缘
private val CloudC   = Color(0xFFF7F5EE)   // 云白
private val CloudSh  = Color(0xFFD9E4E4)   // 云阴影
private val MtFar    = Color(0xFFB7C4B4)   // 远山淡灰绿
private val MtNear   = Color(0xFF8FA58B)   // 近山灰绿
private val GrassC   = Color(0xFF86B94B)   // 主草地
private val GrassD   = Color(0xFF6FA03C)   // 深草地
private val GrassH   = Color(0xFFA2CF62)   // 亮草地(日光)
private val WaterC   = Color(0xFF5FA8CF)   // 水
private val WaterD   = Color(0xFF4689B5)   // 水深
private val WaterHi  = Color(0xFF8ED0E8)   // 水光
private val SandC    = Color(0xFFE3D3A1)   // 土道/岸沙
private val WoodWall = Color(0xFFA9743F)   // 木墙
private val WoodWallD= Color(0xFF8A5A2E)   // 木墙深
private val RoofC    = Color(0xFFB2493F)   // 红屋顶
private val RoofD    = Color(0xFF8E352F)   // 红顶深
private val TrunkC   = Color(0xFF7A4E2D)   // 树干
private val LeafC    = Color(0xFF4E8E3A)   // 树冠
private val LeafD    = Color(0xFF3B6F2C)   // 树冠深
private val FenceC   = Color(0xFFC9A25C)   // 围栏浅木
private val FenceD   = Color(0xFF9E7A3C)   // 围栏深
private val PumpkC   = Color(0xFFE08A2E)   // 南瓜橙
private val PumpkD   = Color(0xFFB26A20)   // 南瓜深
private val HayC     = Color(0xFFE8C86A)   // 干草黄
private val StoneC   = Color(0xFF9AA0A6)   // 石头

@Composable
private fun FarmBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val skyH = h * 0.36f          // 天空
        val mtH = h * 0.08f           // 远山带
        val groundTop = skyH + mtH    // 草地起点

        // 1) 天空（三档块状：天顶→中→地平线）
        drawRect(SkyHi, size = Size(w, skyH * 0.62f))
        drawRect(SkyMid, topLeft = Offset(0f, skyH * 0.62f), size = Size(w, skyH * 0.38f))
        drawRect(SkyLo, topLeft = Offset(0f, skyH * 0.86f), size = Size(w, skyH * 0.14f))

        // 2) 太阳（右上，块状光晕+圆）
        val sx = w * 0.80f; val sy = skyH * 0.24f; val sr = w * 0.055f
        drawRect(SunEdge, topLeft = Offset(sx - sr - w * 0.012f, sy - sr - h * 0.006f), size = Size(sr * 2 + w * 0.024f, sr * 2 + h * 0.012f))
        drawBlockCircle(SunC, sx, sy, sr)

        // 3) 云（两朵白胖块）
        drawCloud(Offset(w * 0.16f, skyH * 0.30f), w * 0.20f)
        drawCloud(Offset(w * 0.50f, skyH * 0.16f), w * 0.15f)

        // 4) 远山两带
        drawMtBand(mtH, Offset(0f, skyH), w, MtFar, peak = 0.55f)
        drawMtBand(mtH * 0.8f, Offset(0f, skyH + mtH * 0.20f), w, MtNear, peak = 0.45f)

        // 5) 草地主底
        drawRect(GrassC, topLeft = Offset(0f, groundTop), size = Size(w, h - groundTop))
        // 草地纵向色块（呼应耕地方块）：几道宽条不同明度
        val blockW = w / 9f
        for (i in 0 until 9) {
            val x = i * blockW
            val c = when (i % 3) { 0 -> GrassD; 1 -> GrassH; else -> GrassC }
            drawRect(c, topLeft = Offset(x, groundTop + h * 0.02f), size = Size(blockW, h - groundTop))
        }

        // 6) 池塘（左下）
        drawPond(Offset(w * 0.02f, h * 0.68f), Size(w * 0.42f, h * 0.24f))

        // 7) 木屋（中部偏左上的地面线，避开池塘）
        drawHouse(Offset(w * 0.52f, groundTop + h * 0.06f), w * 0.36f)

        // 8) 元素点缀：树、围栏、南瓜、稻草人、石头、草花
        drawTree(Offset(w * 0.90f, groundTop + h * 0.10f), w * 0.10f)
        drawTree(Offset(w * 0.46f, groundTop + h * 0.16f), w * 0.08f)
        drawTree(Offset(w * 0.92f, groundTop + h * 0.30f), w * 0.07f, dark = true)
        drawFence(Offset(w * 0.60f, groundTop + h * 0.30f), w * 0.30f)
        drawScarecrow(Offset(w * 0.10f, groundTop + h * 0.22f), w * 0.06f)
        drawPumpkin(Offset(w * 0.24f, h * 0.60f), w * 0.045f)
        drawPumpkin(Offset(w * 0.30f, h * 0.66f), w * 0.04f)
        drawStones(Offset(w * 0.08f, groundTop + h * 0.24f), w * 0.05f)
        drawFlowers(Offset(w * 0.72f, groundTop + h * 0.20f), w * 0.05f)
        drawFlowers(Offset(w * 0.40f, h * 0.62f), w * 0.04f)
    }
}

/** 像素块圆（用圆角方形近似：中心矩形 + 四角），硬边 */
private fun DrawScope.drawBlockCircle(c: Color, cx: Float, cy: Float, r: Float) {
    val cell = r * 0.28f
    // 主体方形
    drawRect(c, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2))
    // 四角补齐成八角圆
    drawRect(c, topLeft = Offset(cx - r * 0.72f, cy - r * 1.28f), size = Size(r * 1.44f, r * 0.56f))
    drawRect(c, topLeft = Offset(cx - r * 0.72f, cy + r * 0.72f), size = Size(r * 1.44f, r * 0.56f))
    drawRect(c, topLeft = Offset(cx - r * 1.28f, cy - r * 0.72f), size = Size(r * 0.56f, r * 1.44f))
    drawRect(c, topLeft = Offset(cx + r * 0.72f, cy - r * 0.72f), size = Size(r * 0.56f, r * 1.44f))
}

private fun DrawScope.drawCloud(origin: Offset, w: Float) {
    val x = origin.x; val y = origin.y
    val u = w / 6f   // 云朵网格单元
    drawRect(CloudSh, topLeft = Offset(x, y + u * 2), size = Size(w, u * 1.6f))
    drawRect(CloudSh, topLeft = Offset(x + u * 1.4f, y + u * 1.2f), size = Size(u * 3.2f, u * 1.2f))
    drawRect(CloudC, topLeft = Offset(x, y), size = Size(w, u * 1.4f))
    drawRect(CloudC, topLeft = Offset(x + u * 0.8f, y - u * 0.9f), size = Size(u * 4.4f, u * 1.0f))
    drawRect(CloudC, topLeft = Offset(x + u * 1.6f, y - u * 1.5f), size = Size(u * 3.2f, u * 0.9f))
}

/** 锯齿山带：peak 控制锯齿深度比例 */
private fun DrawScope.drawMtBand(h: Float, top: Offset, w: Float, c: Color, peak: Float) {
    val seg = w / 5f
    for (i in 0 until 5) {
        val x = top.x + i * seg
        val hh = h * (if (i % 2 == 0) peak else 0.72f)
        drawRect(c, topLeft = Offset(x, top.y + h * 0.42f), size = Size(seg, h - h * 0.42f))
        // 峰顶矩形（居中一段更高）
        drawRect(c, topLeft = Offset(x + seg * 0.25f, top.y), size = Size(seg * 0.5f, h * 0.6f))
        drawRect(c, topLeft = Offset(x + seg * 0.35f, top.y - h * 0.15f), size = Size(seg * 0.3f, h * 0.3f))
    }
}

private fun DrawScope.drawPond(at: Offset, s: Size) {
    // 岸沙边
    drawRect(SandC, topLeft = Offset(at.x - s.width * 0.02f, at.y - s.height * 0.10f), size = Size(s.width * 1.04f, s.height * 1.20f))
    // 水主体（大圆角方块 = 池塘）
    val r = s.height * 0.35f
    drawBlockCircle(WaterC, at.x + s.width / 2, at.y + s.height / 2, r * 1.9f)
    drawBlockCircle(WaterD, at.x + s.width / 2, at.y + s.height / 2, r * 1.9f) // 被上面覆盖? 用分层色
    // 深水底与亮波光
    drawBlockCircle(WaterD, at.x + s.width / 2 - s.width * 0.10f, at.y + s.height / 2 + s.height * 0.02f, r * 1.2f)
    drawBlockCircle(WaterHi, at.x + s.width * 0.24f, at.y + s.height * 0.34f, r * 0.9f)
}

private fun DrawScope.drawHouse(ground: Offset, w: Float) {
    val u = w / 10f
    val wallH = u * 4.2f
    val x = ground.x; val yGround = ground.y
    // 烟囱(后)
    drawRect(Px.WoodDark, topLeft = Offset(x + u * 8.0f, yGround - wallH - u * 1.6f), size = Size(u * 0.8f, u * 2.4f))
    // 屋顶(宽出墙面)
    drawRect(RoofD, topLeft = Offset(x - u * 0.8f, yGround - wallH - u * 1.2f), size = Size(w + u * 1.6f, u * 1.3f))
    drawRect(RoofC, topLeft = Offset(x - u * 0.8f, yGround - wallH - u * 1.2f), size = Size(w + u * 1.6f, u * 0.7f))
    // 屋顶山墙三角（用两段矩形叠出斜顶）
    drawRect(RoofC, topLeft = Offset(x - u * 1.6f, yGround - wallH - u * 2.4f), size = Size(w + u * 3.2f, u * 0.8f))
    // 墙
    drawRect(WoodWall, topLeft = Offset(x, yGround - wallH), size = Size(w, wallH))
    // 窗
    drawRect(Px.Cream, topLeft = Offset(x + u * 1.2f, yGround - wallH + u * 0.9f), size = Size(u * 1.8f, u * 1.6f))
    drawRect(Px.Brown, topLeft = Offset(x + u * 1.2f + u * 0.8f, yGround - wallH + u * 0.9f), size = Size(u * 0.2f, u * 1.6f))
    drawRect(Px.Brown, topLeft = Offset(x + u * 1.2f, yGround - wallH + u * 1.6f), size = Size(u * 1.8f, u * 0.2f))
    // 门
    drawRect(WoodWallD, topLeft = Offset(x + w / 2 - u * 1.0f, yGround - wallH * 0.72f), size = Size(u * 2.0f, wallH * 0.72f))
    // 烟囱烟(两小方)
    drawRect(CloudC, topLeft = Offset(x + u * 7.6f, yGround - wallH - u * 2.8f), size = Size(u * 0.7f, u * 0.7f))
    drawRect(CloudC, topLeft = Offset(x + u * 7.2f, yGround - wallH - u * 3.6f), size = Size(u * 1.0f, u * 0.8f))
}

private fun DrawScope.drawTree(ground: Offset, w: Float, dark: Boolean = false) {
    val leaf = if (dark) LeafD else LeafC
    val leafD = if (dark) LeafC else LeafD
    val u = w / 5f
    val x = ground.x; val yGround = ground.y
    // 树干
    drawRect(TrunkC, topLeft = Offset(x + u * 1.9f, yGround - u * 3.2f), size = Size(u * 1.2f, u * 3.2f))
    // 树冠(两层圆)
    drawBlockCircle(leaf, x + u * 2.5f, yGround - u * 3.6f, u * 2.3f)
    drawBlockCircle(leafD, x + u * 2.5f, yGround - u * 4.6f, u * 1.8f)
    // 高光点
    drawRect(GrassH, topLeft = Offset(x + u * 1.1f, yGround - u * 4.8f), size = Size(u * 0.8f, u * 0.6f))
}

private fun DrawScope.drawFence(ground: Offset, w: Float) {
    val x = ground.x; val y = ground.y
    val u = w / 20f
    // 横木(上下两条)
    drawRect(FenceC, topLeft = Offset(x, y), size = Size(w, u * 0.6f))
    drawRect(FenceC, topLeft = Offset(x, y + u * 1.6f), size = Size(w, u * 0.6f))
    // 竖桩
    for (i in 0 until 5) {
        val px = x + i * w / 4f
        drawRect(FenceD, topLeft = Offset(px, y - u * 0.5f), size = Size(u * 0.8f, u * 3.2f))
    }
}

private fun DrawScope.drawScarecrow(ground: Offset, w: Float) {
    val x = ground.x; val yGround = ground.y
    val u = w / 4f
    // 杆
    drawRect(TrunkC, topLeft = Offset(x + u * 1.8f, yGround - u * 5f), size = Size(u * 0.5f, u * 5f))
    // 横杆(两臂)
    drawRect(TrunkC, topLeft = Offset(x + u * 0.2f, yGround - u * 4.2f), size = Size(u * 3.6f, u * 0.5f))
    // 衣服(方块)
    drawRect(Px.Clay, topLeft = Offset(x + u * 1.0f, yGround - u * 3.9f), size = Size(u * 2.2f, u * 2.0f))
    drawRect(Px.ClayDark, topLeft = Offset(x + u * 1.0f, yGround - u * 3.9f + u * 2.0f), size = Size(u * 2.2f, u * 1.6f))
    // 头(稻草黄)
    drawRect(HayC, topLeft = Offset(x + u * 1.2f, yGround - u * 4.9f), size = Size(u * 1.8f, u * 1.2f))
}

private fun DrawScope.drawPumpkin(at: Offset, w: Float) {
    val u = w / 3f
    drawRect(PumpkD, topLeft = Offset(at.x, at.y + u * 0.2f), size = Size(w, u * 1.4f))
    drawRect(PumpkC, topLeft = Offset(at.x, at.y), size = Size(w, u * 1.2f))
    // 蒂
    drawRect(Px.GrassDark, topLeft = Offset(at.x + w / 2 - u * 0.3f, at.y - u * 0.7f), size = Size(u * 0.6f, u * 0.8f))
    // 棱线
    drawRect(PumpkD, topLeft = Offset(at.x + w * 0.30f, at.y), size = Size(u * 0.3f, u * 1.2f))
    drawRect(PumpkD, topLeft = Offset(at.x + w * 0.65f, at.y), size = Size(u * 0.3f, u * 1.2f))
}

private fun DrawScope.drawStones(at: Offset, w: Float) {
    val u = w / 5f
    drawBlockCircle(StoneC, at.x + u * 1.5f, at.y + u, u * 1.2f)
    drawBlockCircle(Color(0xFF7D8389), at.x + u * 3.6f, at.y + u * 1.5f, u * 0.8f)
}

/** 小花丛：几色小像素点聚簇 */
private fun DrawScope.drawFlowers(at: Offset, w: Float) {
    val cols = listOf(Color(0xFFF49AC1), Color(0xFFFFE98A), Color(0xFFED6A5A), Color(0xFFC9E4DE))
    val u = w / 4f
    var i = 0
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            drawRect(cols[(i) % cols.size], topLeft = Offset(at.x + col * u * 0.8f, at.y + row * u * 0.8f), size = Size(u * 0.5f, u * 0.5f))
            i++
        }
    }
}

/* ================================================================
 * 顶部标题「四季记账」（游戏标题感：双行错落+图标）
 * ================================================================ */
@Composable
private fun AppTitle() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 四叶草/图标装饰排
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("chest", size = 26.dp)
            Spacer(Modifier.width(8.dp))
            PixelIcon("sun", size = 22.dp)
            Spacer(Modifier.width(8.dp))
            PixelIcon("calendarCute", size = 24.dp)
        }
        Spacer(Modifier.height(6.dp))
        PxText(
            "四季记账",
            size = 42.sp,
            color = Px.Cream,
            align = TextAlign.Center,
            modifier = Modifier
                .background(Px.WoodDark)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(4.dp))
        PxText("— 星露谷四季 · 像素手账 —", size = 11.sp, color = Px.Cream.copy(alpha = 0.85f), align = TextAlign.Center)
    }
}

/* ================================================================
 * 卷轴（账号选择）：牛皮纸卷轴形，点开切换
 * ================================================================ */
@Composable
private fun ScrollPicker(account: Account?, onClick: () -> Unit) {
    val name = account?.name ?: "未选择账号"
    // 卷轴整体：两端木轴 + 中部纸卷
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左轴
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(64.dp)
                .background(Px.WoodDark)
                .drawBehind {
                    drawRect(Px.Wood, topLeft = Offset(size.width * 0.25f, 0f), size = Size(size.width * 0.5f, size.height))
                },
        )
        // 纸卷主体（渐变牛皮纸）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(Px.Cream.copy(alpha = 0.0f))
                .drawBehind {
                    val edge = 4.dp.toPx()
                    // 卷纸：多层牛皮色
                    drawRect(Color(0xFFC9A25C), topLeft = Offset(0f, edge), size = Size(size.width, size.height - edge * 2))
                    drawRect(Color(0xFFE3D0A1), topLeft = Offset(edge, 0f), size = Size(size.width - edge * 2, size.height))
                    // 横折痕
                    drawRect(Color(0xFFB98F4A), topLeft = Offset(edge, size.height * 0.5f - 1.dp.toPx()), size = Size(size.width - edge * 2, 2.dp.toPx()))
                }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("idcard", size = 20.dp)
                Spacer(Modifier.width(8.dp))
                PxText(name, size = 16.sp, color = Px.Brown, modifier = Modifier.weight(1f))
                PxText("点按切换 ▾", size = 11.sp, color = Px.GrayText)
            }
        }
        // 右轴
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(64.dp)
                .background(Px.WoodDark)
                .drawBehind {
                    drawRect(Px.Wood, topLeft = Offset(size.width * 0.25f, 0f), size = Size(size.width * 0.5f, size.height))
                },
        )
    }
}

/* ================================================================
 * 公告牌按钮（右下）：木牌+两图钉+文字「开始记账」
 * ================================================================ */
@Composable
private fun BoardButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                // 木牌外框(深)
                drawRect(Px.BrownDark, size = Size(size.width, size.height))
                drawRect(Px.Brown, topLeft = Offset(0f, 4.dp.toPx()), size = Size(size.width, size.height - 4.dp.toPx()))
                // 板面(木)
                drawRect(if (enabled) Px.Wood else Px.GrayText, topLeft = Offset(4.dp.toPx(), 8.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 12.dp.toPx()))
                // 内描边
                drawRect(Px.WoodDark, topLeft = Offset(8.dp.toPx(), 12.dp.toPx()), size = Size(size.width - 16.dp.toPx(), size.height - 20.dp.toPx()), style = Stroke(2.dp.toPx()))
                // 四角图钉
                val pin = 5.dp.toPx()
                drawRect(Px.Clay, topLeft = Offset(6.dp.toPx(), 8.dp.toPx()), size = Size(pin, pin))
                drawRect(Px.Clay, topLeft = Offset(size.width - 6.dp.toPx() - pin, 8.dp.toPx()), size = Size(pin, pin))
                drawRect(Px.Clay, topLeft = Offset(6.dp.toPx(), size.height - 13.dp.toPx()), size = Size(pin, pin))
                drawRect(Px.Clay, topLeft = Offset(size.width - 6.dp.toPx() - pin, size.height - 13.dp.toPx()), size = Size(pin, pin))
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("ledger", size = 26.dp)
            Spacer(Modifier.width(10.dp))
            PxText("开始记账", size = 19.sp, color = Px.Cream)
            Spacer(Modifier.width(8.dp))
            PxText("▶", size = 15.sp, color = Px.Cream)
        }
    }
}
