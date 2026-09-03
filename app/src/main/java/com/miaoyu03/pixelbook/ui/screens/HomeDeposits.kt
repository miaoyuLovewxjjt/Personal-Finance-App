package com.miaoyu03.pixelbook.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.AppMeta
import com.miaoyu03.pixelbook.data.Deposit
import com.miaoyu03.pixelbook.data.DepositKind
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelCalendarDialog
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelIcons
import com.miaoyu03.pixelbook.ui.PixelOption
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTag
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import com.miaoyu03.pixelbook.ui.pixFont
import java.time.LocalDate

/* ================================================================
 * 一、主页（账本管理）
 * ================================================================ */

@Composable
fun HomeScreen(
    store: Store,
    onOpenLedger: (String) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Ledger?>(null) }
    var editing by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Ledger?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val ledgers = remember(tick) { store.ledgers() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(18.dp)) }
            item {
                PxText("我的账本", size = 24.sp, align = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                PxText("共 ${ledgers.size} 个账本", size = 12.sp, color = Px.GrayText, align = TextAlign.Center)
            }
            item { Spacer(Modifier.height(18.dp)) }
            items(ledgers, key = { it.id }) { ledger ->
                LedgerCard(
                    ledger = ledger,
                    totalDeposits = store.totalDeposits(ledger.id),
                    onClick = { onOpenLedger(ledger.id) },
                    onEdit = { editing = ledger },
                    onDelete = { deleting = ledger },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (ledgers.isEmpty()) {
                item {
                    Spacer(Modifier.height(40.dp))
                    PxText("还没有账本，点击下方按钮新建", size = 13.sp, color = Px.GrayText)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                PixelButton(
                    text = "＋ 新建账本",
                    onClick = { showNew = true },
                    bg = Px.Yellow,
                    modifier = Modifier.width(220.dp),
                )
            }
            item { Spacer(Modifier.height(28.dp)) }
        }

        // 右上角：设置按钮（数据存储目录）
        PixelIconButton(
            icon = "gear",
            size = 34.dp,
            onClick = { showSettings = true },
            desc = "设置",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 14.dp),
        )
    }

    if (showNew) {
        NewLedgerDialog(
            onDismiss = { showNew = false },
            onCreated = { id ->
                showNew = false
                tick++
                onOpenLedger(id)   // 新建后进入该账本
            },
            store = store,
        )
    }
    deleting?.let { ledger ->
        PixelConfirm(
            title = "删除账本",
            message = "删除后，账本「${ledger.name}」的全部流水、存款、天气记录将一并删除，且无法恢复。确定删除吗？",
            confirmText = "删除",
            onConfirm = { store.deleteLedger(ledger.id); tick++ },
            onDismiss = { deleting = null },
        )
    }
    editing?.let { ledger ->
        EditLedgerDialog(
            store = store,
            ledger = ledger,
            onDismiss = { editing = null },
            onSaved = { editing = null; tick++ },
        )
    }
    if (showSettings) {
        SettingsDialog(
            store = store,
            onDismiss = { showSettings = false },
            onStorageChanged = { showSettings = false; tick++ },
        )
    }
}

@Composable
private fun LedgerCard(
    ledger: com.miaoyu03.pixelbook.data.Ledger,
    totalDeposits: com.miaoyu03.pixelbook.data.Cents,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
        bg = Px.Cream,
        contentPadding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 封面图标：随账本所选封面色（编辑颜色后主页同步变化）
            Image(
                bitmap = PixelIcons.ledgerIcon(ledger.coverColor),
                contentDescription = "账本",
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                PxText(ledger.name, size = 15.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                PxText("总存款 ${Fmt.yen(totalDeposits)}", size = 12.sp, color = Px.Wood)
            }
            // 右侧：删除（缩小）+ 编辑（手绘铅笔，最右）
            PixelIconButton(icon = "trash", size = 26.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除账本")
            Spacer(Modifier.width(4.dp))
            PixelIconButton(icon = "pencil", size = 26.dp, bg = Px.CreamDark, onClick = onEdit, desc = "编辑账本")
        }
    }
}

/** 新建账本弹窗：名称 + 封面色选择 */
@Composable
private fun NewLedgerDialog(
    store: Store,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cover by remember { mutableIntStateOf(0) }

    PixelDialog(
        title = "新建账本",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "创建",
                {
                    val nm = name.trim()
                    if (nm.isEmpty()) { store.toast("请输入账本名称"); return@PixelButton }
                    if (nm.length > Store.MAX_LEDGER_NAME) {
                        store.toast("账本名称不能超过${Store.MAX_LEDGER_NAME}字")
                        return@PixelButton
                    }
                    onCreated(store.addLedger(nm, cover).id)
                },
                bg = Px.Grass, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("账本名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(value = name, onValueChange = { name = it }, placeholder = "如：日常账本", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PxText("封面颜色", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Px.Covers.forEachIndexed { i, c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(c)
                            .clickable { cover = i }
                            .drawBehind {
                                if (i == cover) {
                                    val stroke = 3.dp.toPx()
                                    drawRect(
                                        Px.Brown,
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke)
                                    )
                                }
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == cover) PixelIcon("plus", size = 14.dp)
                    }
                }
            }
        }
    }
}

/* ================================================================
 * 二、存款明细页
 * ================================================================ */

@Composable
fun DepositsScreen(
    store: Store,
    ledgerId: String,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Deposit?>(null) }
    var deleting by remember { mutableStateOf<Deposit?>(null) }
    // 金额隐私开关：默认关闭（显示金额）；打开后本页所有具体金额隐藏为 ¥****
    var hideAmount by remember { mutableStateOf(false) }

    val deposits = remember(tick) { store.depList(ledgerId) }
    val total = remember(tick) { store.totalDeposits(ledgerId) }
    val money = deposits.filter { it.kind == DepositKind.MONEY }
    val goods = deposits.filter { it.kind == DepositKind.GOODS }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(
            title = "存款明细",
            onBack = onBack,
            trailing = {
                AmountSwitch(hide = hideAmount, onToggle = { hideAmount = !hideAmount })
            },
        )

        // 总存款卡
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            bg = Px.Cream,
            contentPadding = 12.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PixelIcon("coinPile", size = 30.dp)
                Spacer(Modifier.width(8.dp))
                PxText(
                    if (hideAmount) "总存款 ¥****" else "总存款 ${Fmt.yen(total)}",
                    size = 16.sp,
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            DepositGroup(
                title = "金钱类",
                icon = "coinPile",
                list = money,
                hideAmount = hideAmount,
                onEdit = { editing = it },
                onDelete = { deleting = it },
            )
            DepositGroup(
                title = "非金钱类",
                icon = "gift",
                list = goods,
                hideAmount = hideAmount,
                onEdit = { editing = it },
                onDelete = { deleting = it },
            )
            item { Spacer(Modifier.height(10.dp)) }
        }

        // 底部新增按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelButton(
                text = "＋ 新增存款",
                onClick = { showForm = true },
                modifier = Modifier.width(220.dp),
            )
        }
    }

    if (showForm || editing != null) {
        DepositFormDialog(
            store = store,
            ledgerId = ledgerId,
            initial = editing,
            onDismiss = { showForm = false; editing = null },
            onSaved = { showForm = false; editing = null; tick++ },
        )
    }
    deleting?.let { d ->
        PixelConfirm(
            title = "删除存款",
            message = "确定删除「${d.name}」这笔存款吗？",
            confirmText = "删除",
            onConfirm = { store.deleteDep(d.id, ledgerId); tick++ },
            onDismiss = { deleting = null },
        )
    }
}

private fun LazyListScope.DepositGroup(
    title: String,
    icon: String,
    list: List<Deposit>,
    hideAmount: Boolean,
    onEdit: (Deposit) -> Unit,
    onDelete: (Deposit) -> Unit,
) {
    item {
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(icon, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            PxText(title, size = 14.sp, color = Px.Wood)
            Spacer(Modifier.weight(1f))
            PxText("${list.size} 笔", size = 12.sp, color = Px.GrayText)
        }
        Spacer(Modifier.height(8.dp))
    }
    items(list, key = { it.id }) { d ->
        DepositRow(d = d, hideAmount = hideAmount, onEdit = { onEdit(d) }, onDelete = { onDelete(d) })
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DepositRow(
    d: Deposit,
    hideAmount: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onEdit),
        bg = Px.Cream,
        contentPadding = 10.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(if (d.kind == DepositKind.MONEY) "coinPile" else "gift", size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PxText(d.name.ifEmpty { "（未命名）" }, size = 14.sp)
                    if (d.note.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        PxText(d.note, size = 12.sp, color = Px.GrayText)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    PxText(
                        if (hideAmount) "¥****" else Fmt.yen(d.value),
                        size = 14.sp,
                        color = Px.WoodDark,
                    )
                    Spacer(Modifier.height(2.dp))
                    PxText(Fmt.dateYmd(d.date), size = 11.sp, color = Px.GrayText)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelTag(
                    d.kind.label,
                    bg = if (d.kind == DepositKind.MONEY) Px.Clay else Px.SkyDark,
                )
                Spacer(Modifier.weight(1f))
                PixelIconButton(icon = "trash", size = 30.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除")
            }
        }
    }
}

/** 存款 新增/编辑 表单 */
@Composable
private fun DepositFormDialog(
    store: Store,
    ledgerId: String,
    initial: Deposit?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var kind by remember { mutableStateOf(initial?.kind ?: DepositKind.MONEY) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var valueStr by remember { mutableStateOf(if (initial != null) Fmt.money(initial.value) else "") }
    var showCal by remember { mutableStateOf(false) }

    PixelDialog(
        title = if (initial == null) "新增存款" else "编辑存款",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(valueStr)
                    if (v == null) { store.toast("请填写有效金额"); return@PixelButton }
                    if (name.trim().isEmpty()) { store.toast("请填写物品名称"); return@PixelButton }
                    val base = initial
                    val d = Deposit(
                        id = base?.id ?: "d${System.currentTimeMillis()}",
                        ledgerId = ledgerId,
                        date = date,
                        kind = kind,
                        name = name.trim(),
                        note = note.trim(),
                        value = v,
                    )
                    if (base == null) store.addDep(d) else store.updateDep(d)
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 入库时间
            PxText("入库时间", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelPanel(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { showCal = true },
                    bg = Px.Cream,
                    depth = 2.dp,
                    contentPadding = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PxText(Fmt.dateYmd(date), size = 13.sp)
                        Spacer(Modifier.weight(1f))
                        PixelIcon("calendar", size = 18.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 类型
            PxText("类型", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelDropdown(
                label = "类型",
                options = listOf(
                    PixelOption("金钱类", "coinPile"),
                    PixelOption("非金钱类", "gift"),
                ),
                selected = kind.label,
                onSelect = { kind = if (it == "金钱类") DepositKind.MONEY else DepositKind.GOODS },
                modifier = Modifier.fillMaxWidth(),
                width = 140.dp,
            )
            Spacer(Modifier.height(10.dp))
            PxText("物品名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(value = name, onValueChange = { name = it }, placeholder = "如：现金、黄金", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PxText("备注", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(value = note, onValueChange = { note = it }, placeholder = "可选", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PxText("价值（元）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(value = valueStr, onValueChange = { valueStr = it }, placeholder = "如：9000", numeric = true, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showCal) {
        PixelCalendarDialog(
            initial = date,
            onPick = { date = it; showCal = false },
            onDismiss = { showCal = false },
        )
    }
}
/* ================================================================
 * 编辑账本弹窗：名称 / 字体 / 封面颜色（主页卡片右侧铅笔进入）
 * ================================================================ */

@Composable
fun EditLedgerDialog(
    store: Store,
    ledger: com.miaoyu03.pixelbook.data.Ledger,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember(ledger.id) { mutableStateOf(ledger.name) }
    var font by remember(ledger.id) { mutableStateOf(ledger.font) }
    var cover by remember(ledger.id) { mutableIntStateOf(ledger.coverColor) }

    PixelDialog(
        title = "编辑账本",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val nm = name.trim()
                    if (nm.isEmpty()) { store.toast("请输入账本名称"); return@PixelButton }
                    if (nm.length > Store.MAX_LEDGER_NAME) {
                        store.toast("账本名称不能超过${Store.MAX_LEDGER_NAME}字")
                        return@PixelButton
                    }
                    store.updateLedger(ledger.id, nm, font, cover)
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("账本名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = name,
                onValueChange = { if (it.length <= Store.MAX_LEDGER_NAME) name = it },
                placeholder = "如：日常账本",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PxText("字体", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.miaoyu03.pixelbook.ui.LedgerFonts.list.forEach { f ->
                    FontOptionBox(
                        font = f,
                        selected = font == f,
                        onClick = { font = f },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PxText("账本颜色", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Px.Covers.forEachIndexed { i, c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(c)
                            .clickable { cover = i }
                            .drawBehind {
                                if (i == cover) {
                                    val stroke = 3.dp.toPx()
                                    drawRect(
                                        Px.Brown,
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke)
                                    )
                                }
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == cover) PixelIcon("plus", size = 14.dp)
                    }
                }
            }
        }
    }
}

/** 字体选项块：用该字体本身渲染预览文字，选中高亮描边 */
@Composable
private fun FontOptionBox(
    font: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(if (selected) Px.Grass.copy(alpha = 0.25f) else Px.CreamBg)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = if (selected) 3.dp.toPx() else 2.dp.toPx()
                drawRect(
                    if (selected) Px.Grass else Px.Brown,
                    style = Stroke(width = stroke)
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PxText(
            com.miaoyu03.pixelbook.ui.LedgerFonts.label(font),
            size = 14.sp,
            color = if (selected) Px.GrassDark else Px.Brown,
            font = com.miaoyu03.pixelbook.ui.LedgerFonts.family(font),
        )
    }
}

/* ================================================================
 * 设置弹窗：数据存储目录（主页右上角齿轮进入）
 * ================================================================ */

/** 打开目录选择（SAF，带可持久化权限 flag，重启后仍可读写） */
private class OpenTreeContract : androidx.activity.result.contract.ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

@Composable
fun SettingsDialog(
    store: Store,
    onDismiss: () -> Unit,
    onStorageChanged: () -> Unit,
) {
    var pendingSwitch by remember { mutableStateOf<Uri?>(null) }   // 待确认的目标目录
    var pendingRestore by remember { mutableStateOf(false) }       // 待确认的恢复内部存储
    var showCats by remember { mutableStateOf(false) }             // 类别维护

    val treeLauncher = rememberLauncherForActivityResult(OpenTreeContract()) { uri ->
        if (uri != null) pendingSwitch = uri
    }

    PixelDialog(
        title = "设置",
        onDismiss = onDismiss,
        footer = {
            PixelButton("完成", onDismiss, bg = Px.Clay, height = 40.dp, modifier = Modifier.width(140.dp))
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("数据存储", size = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("chest", size = 18.dp)
                Spacer(Modifier.width(6.dp))
                PxText("当前目录：${store.storageDirDescription()}", size = 12.sp, color = Px.Wood)
            }
            Spacer(Modifier.height(8.dp))
            PxText("切换目录后，现有数据将自动迁移到新目录，账本数据以 JSON 文件保存。", size = 11.sp, color = Px.GrayText)
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    "选择目录",
                    onClick = { treeLauncher.launch(Unit) },
                    bg = Px.Grass, height = 40.dp, icon = "export",
                    modifier = Modifier.weight(1f),
                )
                PixelButton(
                    "恢复内部存储",
                    onClick = { pendingRestore = true },
                    bg = Px.Wood, height = 40.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            PxText("类别维护", size = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("pencil", size = 18.dp)
                Spacer(Modifier.width(6.dp))
                PxText("收入类别 / 花销类别，可新增、编辑、删除（「其他」固定）", size = 12.sp, color = Px.Wood)
            }
            Spacer(Modifier.height(8.dp))
            PixelButton(
                "收入 / 花销类别",
                onClick = { showCats = true },
                bg = Px.Yellow, height = 40.dp, icon = "pencil",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PxText("签名", size = 14.sp)
            Spacer(Modifier.height(6.dp))
            PxText("版本：${AppMeta.VERSION}", size = 12.sp, color = Px.Wood)
            Spacer(Modifier.height(4.dp))
            PxText("GitHub：${AppMeta.GIT_URL}", size = 12.sp, color = Px.Wood)
            Spacer(Modifier.height(14.dp))
            // 底部提示（斜体）
            PxText(
                "若有问题可私信小红书@4987988019",
                size = 11.sp,
                color = Px.GrayText,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        }
    }

    // 切换前确认（覆盖在设置弹窗之上）
    pendingSwitch?.let { uri ->
        PixelConfirm(
            title = "切换存储目录",
            message = "确定把数据存储切换到所选目录吗？现有数据将自动迁移，迁移完成后自动生效。",
            confirmText = "切换",
            onConfirm = {
                if (store.switchStorage(uri)) store.toast("已切换，数据迁移完成")
                else store.toast("切换失败，请检查目录是否可写")
                onStorageChanged()
            },
            onDismiss = { pendingSwitch = null },
        )
    }
    if (pendingRestore) {
        PixelConfirm(
            title = "恢复内部存储",
            message = "确定把数据存储恢复到应用内部吗？现有数据将自动迁移。",
            confirmText = "恢复",
            onConfirm = {
                if (store.switchStorage(null)) store.toast("已恢复内部存储，数据迁移完成")
                else store.toast("恢复失败，请重试")
                onStorageChanged()
            },
            onDismiss = { pendingRestore = false },
        )
    }
    if (showCats) {
        CategoryManagerDialog(
            store = store,
            onDismiss = { showCats = false },
        )
    }
}

/* ================================================================
 * 类别维护弹窗：收入 / 花销分别维护，可新增、编辑、删除；
 * 「其他」固定不可操作；删除被引用类别 → 记录回退「其他」；
 * 编辑被引用类别 → 记录全量同步改名为新类别
 * ================================================================ */

@Composable
private fun CategoryManagerDialog(
    store: Store,
    onDismiss: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<Boolean, String>?>(null) }  // (isIncome, 原类别名)
    var adding by remember { mutableStateOf<Boolean?>(null) }                  // isIncome
    var deleting by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val inCats = remember(tick) { store.incomeCats() }
    val outCats = remember(tick) { store.expenseCats() }

    PixelDialog(
        title = "类别维护",
        onDismiss = onDismiss,
        footer = {
            PixelButton("完成", onDismiss, bg = Px.Clay, height = 40.dp, modifier = Modifier.width(140.dp))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(max = 430.dp),
        ) {
            CategoryGroup(
                title = "收入类别",
                cats = inCats,
                onEdit = { editing = true to it },
                onDelete = { deleting = true to it },
                onAdd = { adding = true },
            )
            Spacer(Modifier.height(16.dp))
            CategoryGroup(
                title = "花销类别",
                cats = outCats,
                onEdit = { editing = false to it },
                onDelete = { deleting = false to it },
                onAdd = { adding = false },
            )
        }
    }

    // 编辑类别（输入框）
    editing?.let { (isIncome, old) ->
        InputDialog(
            title = "编辑类别",
            initial = old,
            onSave = { new ->
                val ok = if (isIncome) store.renameIncomeCat(old, new) else store.renameExpenseCat(old, new)
                if (!ok) store.toast("类别名无效、重复，或「其他」不可编辑")
                else { editing = null; tick++ }
            },
            onDismiss = { editing = null },
        )
    }
    // 新增类别（输入框）
    adding?.let { isIncome ->
        InputDialog(
            title = if (isIncome) "新增收入类别" else "新增花销类别",
            initial = "",
            onSave = { name ->
                val ok = if (isIncome) store.addIncomeCat(name) else store.addExpenseCat(name)
                if (!ok) store.toast("类别名无效或已存在")
                else { adding = null; tick++ }
            },
            onDismiss = { adding = null },
        )
    }
    // 删除确认
    deleting?.let { (isIncome, name) ->
        PixelConfirm(
            title = "删除类别",
            message = "删除「$name」后，所有使用该类别的记录将自动改为「${com.miaoyu03.pixelbook.data.CATEGORY_OTHERS}」。确定删除吗？",
            confirmText = "删除",
            onConfirm = {
                val ok = if (isIncome) store.deleteIncomeCat(name) else store.deleteExpenseCat(name)
                if (!ok) store.toast("删除失败")
                else { deleting = null; tick++ }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** 一组类别：标题 + 类别行（名称 + 编辑/删除；「其他」固定不可操作）+ 新增按钮 */
@Composable
private fun CategoryGroup(
    title: String,
    cats: List<String>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PxText(title, size = 14.sp)
        Spacer(Modifier.height(4.dp))
        cats.forEach { c ->
            val fixed = c == com.miaoyu03.pixelbook.data.CATEGORY_OTHERS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(PixelIcons.iconOfCategory(c), size = 18.dp)
                Spacer(Modifier.width(6.dp))
                PxText(c, size = 13.sp, color = if (fixed) Px.GrayText else Px.Brown, modifier = Modifier.weight(1f))
                if (fixed) {
                    PxText("固定类别", size = 10.sp, color = Px.GrayText)
                } else {
                    PixelIconButton(icon = "pencil", size = 24.dp, bg = Px.CreamDark, onClick = { onEdit(c) }, desc = "编辑")
                    Spacer(Modifier.width(4.dp))
                    PixelIconButton(icon = "trash", size = 24.dp, bg = Px.CreamDark, onClick = { onDelete(c) }, desc = "删除")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        PixelButton(
            text = "＋ 新增类别",
            onClick = onAdd,
            bg = Px.Yellow,
            height = 36.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 类别新增 / 编辑输入弹窗 */
@Composable
private fun InputDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    PixelDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                { onSave(name.trim()) },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        PixelTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "类别名称",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
          

/* ================================================================
 * 金额显示开关（存款明细页右上角）：显示 / 隐藏两段式，像素风
 * ================================================================ */

@Composable
private fun AmountSwitch(hide: Boolean, onToggle: () -> Unit) {
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
        AmountSwitchSeg(label = "显示", selected = !hide, onClick = { if (hide) onToggle() })
        AmountSwitchSeg(label = "隐藏", selected = hide, onClick = { if (!hide) onToggle() })
    }
}

@Composable
private fun AmountSwitchSeg(label: String, selected: Boolean, onClick: () -> Unit) {
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
