package com.miaoyu03.pixelbook.ui.screens

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val ledgers = remember(tick) { store.ledgers() }

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
}

@Composable
private fun LedgerCard(
    ledger: com.miaoyu03.pixelbook.data.Ledger,
    totalDeposits: com.miaoyu03.pixelbook.data.Cents,
    onClick: () -> Unit,
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
            PixelIcon(
                name = "ledger",
                size = 40.dp,
                desc = "账本",
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                PxText(ledger.name, size = 15.sp)
                Spacer(Modifier.height(4.dp))
                PxText("总存款 ${Fmt.yen(totalDeposits)}", size = 12.sp, color = Px.Wood)
            }
            PixelIcon("chevronR", size = 20.dp, desc = "进入")
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

    val deposits = remember(tick) { store.depList(ledgerId) }
    val total = remember(tick) { store.totalDeposits(ledgerId) }
    val money = deposits.filter { it.kind == DepositKind.MONEY }
    val goods = deposits.filter { it.kind == DepositKind.GOODS }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "存款明细", onBack = onBack)

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
                PxText("总存款 ${Fmt.yen(total)}", size = 16.sp)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            DepositGroup(
                title = "金钱类",
                icon = "coinPile",
                list = money,
                onEdit = { editing = it },
                onDelete = { deleting = it },
            )
            DepositGroup(
                title = "非金钱类",
                icon = "gift",
                list = goods,
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
        DepositRow(d = d, onEdit = { onEdit(d) }, onDelete = { onDelete(d) })
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DepositRow(
    d: Deposit,
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
                    PxText(Fmt.yen(d.value), size = 14.sp, color = Px.WoodDark)
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