package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.R
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PxText
import com.miaoyu03.pixelbook.ui.pixelFontFamily
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 首页场景图包含标题、记事 NPC 和四层木牌。
 * 热区使用原图坐标，统一跟随居中裁剪变换，避免换屏幕后点击位置漂移。
 * 横屏使用完整场景适配，保证 NPC 和两块操作木牌仍可见。
 */
@Composable
fun StartScreen(store: Store, onStart: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var showAccountBox by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var addingAccount by remember { mutableStateOf(false) }
    var editingBlessing by rememberSaveable { mutableIntStateOf(-1) }
    var blessingDraft by rememberSaveable { mutableStateOf("") }
    var blessings by remember { mutableStateOf(store.homeBlessings()) }
    val accounts = remember(tick) { store.accountsByRecent() }
    val curId = remember(tick) { store.currentAccountId() }
    val cur = accounts.firstOrNull { it.id == curId } ?: accounts.firstOrNull()
    val background = ImageBitmap.imageResource(R.drawable.home_farm)
    val details = ImageBitmap.imageResource(R.drawable.home_details)
    val pixelFont = pixelFontFamily()
    val signInk = Color(0xFF503019)
    val accountPaper = Color(0xFFF2D9A5)
    val accountBorder = Color(0xFF704321)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF86B9CA)).clipToBounds()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        // Tall portrait devices may crop sky/edge foliage, but never the interactive area.
        // For wide screens use fit, with the same transform for both image and controls.
        val fit = minOf(viewportW / background.width, viewportH / background.height)
        val crop = max(viewportW / background.width, viewportH / background.height)
        val scale = if (viewportW > viewportH) fit else minOf(crop, viewportW / (background.width * 0.86f))
        val sceneW = background.width * scale
        val sceneH = background.height * scale
        val originX = (viewportW - sceneW) / 2f
        val originY = (viewportH - sceneH) / 2f

        // A single Canvas avoids layout constraint clamping of an oversized cropped image.
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = "四季记账，山间农场，房门前坐着记事的人，路旁是祝福木牌"
            },
        ) {
            drawImage(
                image = background,
                dstOffset = IntOffset(originX.roundToInt(), originY.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(sceneW.roundToInt(), sceneH.roundToInt()),
                filterQuality = FilterQuality.None,
            )
            // Only these art regions are replaced. The original landscape and its transform stay intact.
            fun detail(left: Int, top: Int, width: Int, height: Int) {
                val sx = details.width.toFloat() / background.width
                val sy = details.height.toFloat() / background.height
                drawImage(
                    image = details,
                    srcOffset = IntOffset((left * sx).roundToInt(), (top * sy).roundToInt()),
                    srcSize = IntSize((width * sx).roundToInt(), (height * sy).roundToInt()),
                    dstOffset = IntOffset((originX + left * scale).roundToInt(), (originY + top * scale).roundToInt()),
                    dstSize = IntSize((width * scale).roundToInt(), (height * scale).roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }
            detail(140, 185, 680, 260) // Reference-style title, leaves and flower.
            detail(758, 935, 76, 47) // Bird perched on the upper board.
            detail(589, 977, 270, 83)
            detail(560, 1066, 317, 79)
            detail(560, 1150, 317, 84)
            detail(622, 1240, 199, 83)
        }

        fun region(left: Float, top: Float, width: Float, height: Float): Modifier =
            Modifier
                .offset { IntOffset((originX + left * scale).roundToInt(), (originY + top * scale).roundToInt()) }
                .size(with(density) { (width * scale).toDp() }, with(density) { (height * scale).toDp() })

        // Actual source bitmap: 948 × 1659. Keep these coordinates with home_farm.png.
        SceneHotspot(
            modifier = region(174f, 955f, 174f, 244f),
            description = "选择账号，当前：${cur?.name ?: "未选择账号"}",
            onClick = {
                if (showAccountBox) {
                    showPicker = false
                    showAccountBox = false
                } else {
                    showAccountBox = true
                }
            },
        )
        Box(region(628f, 985f, 193f, 61f), contentAlignment = Alignment.Center) {
            PxText("start", size = with(density) { (39f * scale).toSp() }, color = signInk,
                font = pixelFont, maxLines = 1, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
        Box(region(624f, 1241f, 197f, 80f), contentAlignment = Alignment.Center) {
            SignSymbol(gearPixels, signInk, Modifier.size(with(density) { (49f * scale).toDp() }))
        }
        SceneHotspot(
            modifier = region(592f, 977f, 264f, 80f),
            description = "start，开始记账",
            enabled = cur != null,
            onClick = { cur?.let { onStart(it.id) } },
        )
        SceneHotspot(
            modifier = region(624f, 1241f, 197f, 80f),
            description = "设置",
            onClick = { showSettings = true },
        )

        // Live text uses the same font as the app; the painted board edges remain visible.
        blessings.forEachIndexed { index, text ->
            Box(
                region(583f, if (index == 0) 1074f else 1161f, 274f, 63f),
                contentAlignment = Alignment.Center,
            ) {
                PxText(text, size = with(density) { ((if (text.length <= 6) 35f else 30f) * scale).toSp() },
                    color = signInk, font = pixelFont,
                    maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            SceneHotspot(
                modifier = region(563f, if (index == 0) 1067f else 1152f, 314f, 80f),
                description = "编辑祝福：$text",
                onClick = { blessingDraft = text; editingBlessing = index },
            )
        }

        // The account box is revealed by the NPC; its own click opens the account list.
        if (showAccountBox) Box(region(340f, 1160f, 216f, 54f)) {
            PixelPanel(
                modifier = Modifier.fillMaxSize()
                    .clickable(role = Role.Button, onClickLabel = "展开账号列表") { showPicker = !showPicker }
                    .semantics { contentDescription = "当前账号：${cur?.name ?: "默认账户"}" },
                bg = accountPaper, borderColor = accountBorder, depth = 2.dp, contentPadding = 1.dp,
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    PxText(cur?.name ?: "默认账户", size = 11.sp, maxLines = 1, font = pixelFont,
                        overflow = TextOverflow.Ellipsis, align = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                    PxText("▼", size = 10.sp, color = accountBorder, font = pixelFont, maxLines = 1)
                }
            }
            DropdownMenu(expanded = showPicker, onDismissRequest = {
                showPicker = false
                showAccountBox = false
            },
                shape = RectangleShape, containerColor = Color.Transparent,
                tonalElevation = 0.dp, shadowElevation = 0.dp,
                modifier = Modifier.width(with(density) { (216f * scale).toDp() })
                    .heightIn(max = 260.dp)) {
                PixelPanel(
                    modifier = Modifier.fillMaxWidth(),
                    bg = accountPaper,
                    borderColor = accountBorder,
                    depth = 3.dp,
                    contentPadding = 0.dp,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        accounts.forEach { account ->
                            val selected = account.id == cur?.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (selected) Px.Grass.copy(alpha = .28f) else Color.Transparent)
                                    .clickable(role = Role.Button) {
                                        store.setCurrentAccountId(account.id)
                                        tick++
                                        showPicker = false
                                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PxText(account.name, size = 11.sp, font = pixelFont, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.fillMaxWidth().height(2.dp).background(accountBorder))
                        Box(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            showPicker = false
                            addingAccount = true
                        }.padding(horizontal = 8.dp, vertical = 12.dp)) {
                            PxText("新建账号", size = 11.sp, font = pixelFont, color = accountBorder)
                        }
                    }
                }
            }
        }
    }

    if (editingBlessing >= 0) {
        PixelDialog(title = "写下祝福", onDismiss = { editingBlessing = -1 }, footer = {
            PixelButton("取消", { editingBlessing = -1 }, bg = Px.Wood, modifier = Modifier.weight(1f))
            PixelButton("保存", {
                val text = blessingDraft.trim()
                if (text.isEmpty()) store.toast("请写下一句祝福")
                else {
                    store.setHomeBlessing(editingBlessing, text)
                    blessings = store.homeBlessings()
                    editingBlessing = -1
                }
            }, bg = Px.Clay, modifier = Modifier.weight(1f))
        }) {
            PixelTextField(value = blessingDraft,
                onValueChange = { blessingDraft = it.replace("\n", "").replace("\r", "").take(8) },
                placeholder = "写下喜欢的话", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PxText("最多 8 个字 · ${blessingDraft.length}/8", size = 11.sp, color = Px.GrayText)
        }
    }
    if (addingAccount) {
        AccountNameDialog(
            title = "新建账号",
            initial = "",
            hint = "账号名（不可与已有账号同名）",
            onSave = { name ->
                val created = store.addAccount(name)
                if (created != null) store.setCurrentAccountId(created.id)
                created != null
            },
            onDismiss = { addingAccount = false },
            onSaved = { addingAccount = false; tick++ },
            dupHint = { store.toast("账号名无效或已存在") },
        )
    }
    if (showSettings) {
        SettingsDialog(
            store = store,
            accountId = cur?.id ?: "",
            onDismiss = { showSettings = false; tick++ },
            onStorageChanged = { tick++ },
        )
    }
}

private val gearPixels = listOf(
    "......####......", "......####......", "..##..####..##..", "..############..",
    "...##########...", "...##########...", "######....######", "######....######",
    "######....######", "######....######", "...##########...", "...##########...",
    "..############..", "..##..####..##..", "......####......", "......####......",
)

/** Integer-grid silhouettes share the board lettering's dark coffee ink. */
@Composable
private fun SignSymbol(pixels: List<String>, ink: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val cell = minOf(size.width / pixels[0].length, size.height / pixels.size)
        val x = (size.width - cell * pixels[0].length) / 2
        val y = (size.height - cell * pixels.size) / 2
        pixels.forEachIndexed { row, line ->
            line.forEachIndexed { column, char ->
                if (char == '#') {
                    // Share integer edges so adjacent pixels have no antialiased hairline gaps.
                    val left = (x + column * cell).roundToInt().toFloat()
                    val top = (y + row * cell).roundToInt().toFloat()
                    val right = (x + (column + 1) * cell).roundToInt().toFloat()
                    val bottom = (y + (row + 1) * cell).roundToInt().toFloat()
                    drawRect(ink, Offset(left, top), Size(right - left, bottom - top))
                }
            }
        }
    }
}

/** Illustrated controls keep their painted appearance and give a subtle pressed highlight. */
@Composable
private fun SceneHotspot(
    modifier: Modifier,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.semantics { contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            )
            .background(if (pressed) Color.White.copy(alpha = 0.16f) else Color.Transparent),
    )
}
