package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    // 首启由 Store.ensureStartupAccount() 兜底：全新安装自动建立「默认账户」，此处无需引导

    // 最近账号列表（updated 倒序）
    val accounts = remember(tick) { store.accountsByRecent() }
    val curId = remember(tick, accounts) { store.currentAccountId() }
    val cur = accounts.firstOrNull { it.id == curId }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1) 游戏风背景（AI 生成的星露谷风图片，铺满整屏并居中裁剪）
        Image(
            painter = painterResource(com.miaoyu03.pixelbook.R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 轻微压暗，让标题/卷轴/按钮文字更清晰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x22000000)),
        )

        // 2) 主内容区（压在背景上）
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(46.dp))
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
            onSaved = { addingAccount = false; tick++ },
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
                    // 卷纸：多层牛皮色（无折痕线，干净）
                    drawRect(Color(0xFFC9A25C), topLeft = Offset(0f, edge), size = Size(size.width, size.height - edge * 2))
                    drawRect(Color(0xFFE3D0A1), topLeft = Offset(edge, 0f), size = Size(size.width - edge * 2, size.height))
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
