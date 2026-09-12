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
 * 首页场景图包含标题、房屋、菜地和四层木牌。
 * 热区使用原图坐标，统一跟随居中裁剪变换，避免换屏幕后点击位置漂移。
 * 横屏使用完整场景适配，保证房屋、菜地和木牌仍可见。
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
    // Reference scene: the finer composition from the original homepage sketch.
    // The current interactive labels are painted over the four existing planks below.
    val background = ImageBitmap.imageResource(R.drawable.home_bg)
    val boardTexture = ImageBitmap.imageResource(R.drawable.home_details)
    val pixelFont = pixelFontFamily()
    val signInk = Color(0xFF503019)
    val accountPaper = Color(0xFFF2D9A5)
    val accountBorder = Color(0xFF704321)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF86B9CA)).clipToBounds()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        // Fill the viewport with the reference scene. Since its aspect ratio is close to
        // the target phone canvas, this keeps the house, field, lake and signpost balanced.
        val fit = minOf(viewportW / background.width, viewportH / background.height)
        val crop = max(viewportW / background.width, viewportH / background.height)
        val scale = if (viewportW > viewportH) fit else crop
        val sceneW = background.width * scale
        val sceneH = background.height * scale
        val originX = (viewportW - sceneW) / 2f
        val originY = (viewportH - sceneH) / 2f

        // A single Canvas avoids layout constraint clamping of an oversized cropped image.
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = "四季记账，山间农场，房屋、菜地和路旁的祝福木牌"
            },
        ) {
            drawImage(
                image = background,
                dstOffset = IntOffset(originX.roundToInt(), originY.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(sceneW.roundToInt(), sceneH.roundToInt()),
                filterQuality = FilterQuality.None,
            )
            // Cover only the lettering on the reference planks with the existing blank
            // plank texture, preserving the reference scene everywhere else.
            fun blankBoard(
                sourceLeft: Int,
                sourceTop: Int,
                sourceWidth: Int,
                sourceHeight: Int,
                targetLeft: Int,
                targetTop: Int,
                targetWidth: Int,
                targetHeight: Int,
            ) {
                drawImage(
                    image = boardTexture,
                    srcOffset = IntOffset(sourceLeft, sourceTop),
                    srcSize = IntSize(sourceWidth, sourceHeight),
                    dstOffset = IntOffset((originX + targetLeft * scale).roundToInt(), (originY + targetTop * scale).roundToInt()),
                    dstSize = IntSize((targetWidth * scale).roundToInt(), (targetHeight * scale).roundToInt()),
                    filterQuality = FilterQuality.None,
                )
                // The blank-board source is brighter than the reference sketch. A warm
                // translucent glaze brings it back to the muted chestnut of home_bg while
                // keeping its pixel wood grain and hardware visible.
                drawRect(
                    color = Color(0xFF6B2B17).copy(alpha = 0.26f),
                    topLeft = Offset(
                        (originX + targetLeft * scale).roundToInt().toFloat(),
                        (originY + targetTop * scale).roundToInt().toFloat(),
                    ),
                    size = Size(
                        (targetWidth * scale).roundToInt().toFloat(),
                        (targetHeight * scale).roundToInt().toFloat(),
                    ),
                )
            }
            blankBoard(589, 977, 270, 83, 696, 1298, 148, 63)
            blankBoard(560, 1066, 317, 79, 699, 1359, 146, 65)
            blankBoard(560, 1150, 317, 84, 699, 1422, 146, 66)
            blankBoard(622, 1240, 199, 83, 716, 1487, 100, 64)
        }

        fun region(left: Float, top: Float, width: Float, height: Float): Modifier =
            Modifier
                .offset { IntOffset((originX + left * scale).roundToInt(), (originY + top * scale).roundToInt()) }
                .size(with(density) { (width * scale).toDp() }, with(density) { (height * scale).toDp() })

        // Actual source bitmap: 851 × 1847. Keep these coordinates with home_bg.png.
        SceneHotspot(
            // The reference scene uses the mailbox as the account entry point.
            modifier = region(354f, 1250f, 105f, 151f),
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
        Box(region(700f, 1301f, 140f, 56f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxText("start", size = with(density) { (20f * scale).toSp() }, color = signInk,
                    font = pixelFont, maxLines = 1, align = TextAlign.Center)
                Spacer(Modifier.width(with(density) { (4f * scale).toDp() }))
                SignSymbol(heartPixels, Color(0xFFD9473F), Modifier.size(with(density) { (20f * scale).toDp() }))
            }
        }
        Box(region(718f, 1489f, 96f, 59f), contentAlignment = Alignment.Center) {
            SignSymbol(gearPixels, signInk, Modifier.size(with(density) { (30f * scale).toDp() }))
        }
        SceneHotspot(
            modifier = region(688f, 1291f, 164f, 76f),
            description = "start，开始记账",
            enabled = cur != null,
            onClick = { cur?.let { onStart(it.id) } },
        )
        SceneHotspot(
            modifier = region(706f, 1480f, 118f, 78f),
            description = "设置",
            onClick = { showSettings = true },
        )

        // Live text uses the same pixel font as the app; the reference wood grain remains visible.
        blessings.forEachIndexed { index, text ->
            Box(
                region(701f, if (index == 0) 1362f else 1425f, 142f, 58f),
                contentAlignment = Alignment.Center,
            ) {
                PxText(text, size = with(density) { ((if (text.length <= 6) 18f else 15f) * scale).toSp() },
                    color = signInk, font = pixelFont,
                    maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            SceneHotspot(
                modifier = region(688f, if (index == 0) 1352f else 1415f, 164f, 78f),
                description = "编辑祝福：$text",
                onClick = { blessingDraft = text; editingBlessing = index },
            )
        }

        // The account box is revealed by the mailbox; its own click opens the account list.
        if (showAccountBox) Box(region(276f, 1190f, 216f, 54f)) {
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
                onValueChange = { blessingDraft = it.replace("\n", "").replace("\r", "").take(7) },
                placeholder = "写下喜欢的话", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PxText("最多 7 个字 · ${blessingDraft.length}/7", size = 11.sp, color = Px.GrayText)
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

private val heartPixels = listOf(
    ".##.##.",
    "#######",
    "#######",
    ".#####.",
    "..###..",
    "...#...",
)

private val gearPixels = listOf(
    ".....##.....##......",
    ".....##.....##......",
    "..################..",
    ".##################.",
    "####################",
    "######......######..",
    "#####........#####..",
    "####....####....####",
    "####....####....####",
    "#####........#####..",
    "######......######..",
    "####################",
    ".##################.",
    "..################..",
    ".....##.....##......",
    ".....##.....##......",
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
