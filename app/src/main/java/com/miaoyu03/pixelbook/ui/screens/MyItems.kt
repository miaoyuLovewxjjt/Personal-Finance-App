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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Cents
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Item
import com.miaoyu03.pixelbook.data.MAX_ITEM_NAME_LEN
import com.miaoyu03.pixelbook.data.MAX_NOTE_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelCalendarDialog
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/* ================================================================
 * 我的物品：账户级物品清单（记录持有物与持有成本）
 *  - 买入时间（用户填）、录入时间（自动）
 *  - 已用天数 = 买入日至今天数（当天买入算第 1 天）
 *  - 日均价格 = 买入价格 ÷ 已用天数（四舍五入到分）
 *  - 全局价值一览：总价值 + 件数 + 全部物品日均合计
 * ================================================================ */

/** 已用天数（买入当天算第 1 天；未来日期按 1 天兜底） */
private fun usedDaysOf(buyDate: LocalDate): Long =
    (ChronoUnit.DAYS.between(buyDate, LocalDate.now()) + 1).coerceAtLeast(1)

/** 日均价格（分/天，四舍五入） */
private fun dailyAvgOf(price: Cents, days: Long): Cents =
    if (days <= 0) price else (price + days / 2) / days

@Composable
fun MyItemsScreen(
    store: Store,
    accountId: String,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var deleting by remember { mutableStateOf<Item?>(null) }

    val items = remember(tick, accountId) { store.itemsOf(accountId) }
    val total = items.sumOf { it.price }
    val totalDaily = items.sumOf { dailyAvgOf(it.price, usedDaysOf(it.buyDate)) }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "我的物品", onBack = onBack)

        // 全局价值一览（总价值 / 件数 / 日均合计）
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            bg = Px.Cream,
            contentPadding = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PixelIcon("box", size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    PxText("总价值 ${Fmt.yen(total)}", size = 16.sp)
                }
                Spacer(Modifier.height(2.dp))
                PxText(
                    "共 ${items.size} 件 · 全部日均合计 ${Fmt.yen(totalDaily)}/天",
                    size = 11.sp,
                    color = Px.GrayText,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                item {
                    Spacer(Modifier.height(60.dp))
                    PxText(
                        "还没有物品，点击下方按钮新增",
                        size = 13.sp, color = Px.GrayText,
                        align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(items, key = { it.id }) { it ->
                ItemRow(
                    item = it,
                    onEdit = { editing = it },
                    onDelete = { deleting = it },
                )
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        // 底部：新增物品
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelButton(
                text = "＋ 新增物品",
                onClick = { showForm = true },
                modifier = Modifier.width(220.dp),
            )
        }
    }

    if (showForm || editing != null) {
        ItemFormDialog(
            store = store,
            accountId = accountId,
            initial = editing,
            onDismiss = { showForm = false; editing = null },
            onSaved = { showForm = false; editing = null; tick++ },
        )
    }
    deleting?.let { it ->
        PixelConfirm(
            title = "删除物品",
            message = "确定删除「${it.name}」这件物品吗？",
            confirmText = "删除",
            onConfirm = { store.deleteItem(accountId, it.id); tick++ },
            onDismiss = { deleting = null },
        )
    }
}

/** 单件物品卡：名称 / 买入价格 · 买入时间 · 已用天数 · 日均价格 · 备注 / 编辑删除 */
@Composable
private fun ItemRow(
    item: Item,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val days = usedDaysOf(item.buyDate)
    val avg = dailyAvgOf(item.price, days)
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
                PixelIcon("box", size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PxText(item.name.ifEmpty { "（未命名）" }, size = 14.sp)
                    if (item.note.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        PxText(item.note, size = 12.sp, color = Px.GrayText)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    PxText(Fmt.yen(item.price), size = 14.sp, color = Px.WoodDark)
                    Spacer(Modifier.height(2.dp))
                    PxText("买入 ${Fmt.dateYmd(item.buyDate)}", size = 11.sp, color = Px.GrayText)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxText(
                    "已用 $days 天 · 日均 ${Fmt.yen(avg)}/天",
                    size = 11.sp,
                    color = Px.Wood,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.weight(1f))
                // 编辑（铅笔）在删除（垃圾桶）左侧
                PixelIconButton(icon = "pencil", size = 30.dp, bg = Px.CreamDark, onClick = onEdit, desc = "编辑")
                Spacer(Modifier.width(4.dp))
                PixelIconButton(icon = "trash", size = 30.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除")
            }
        }
    }
}

/* ================================================================
 * 物品 新增 / 编辑 表单：名称、买入时间（日历）、买入价格、备注
 * 录入时间自动记录（编辑态只读展示）
 * ================================================================ */

@Composable
private fun ItemFormDialog(
    store: Store,
    accountId: String,
    initial: Item?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var buyDate by remember { mutableStateOf(initial?.buyDate ?: LocalDate.now()) }
    var priceStr by remember { mutableStateOf(if (initial != null) Fmt.money(initial.price) else "") }
    var showCal by remember { mutableStateOf(false) }

    PixelDialog(
        title = if (initial == null) "新增物品" else "编辑物品",
        onDismiss = onDismiss,
        contentScrollable = true,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(priceStr)
                    if (v == null) { store.toast("请填写有效买入价格"); return@PixelButton }
                    if (name.trim().isEmpty()) { store.toast("请填写物品名称"); return@PixelButton }
                    val base = initial
                    val it = Item(
                        id = base?.id ?: "i${System.currentTimeMillis()}",
                        name = name.trim(),
                        note = note.trim(),
                        buyDate = buyDate,
                        createdAt = base?.createdAt ?: LocalDate.now().toString(),
                        price = v,
                    )
                    if (base == null) store.addItem(accountId, it) else store.updateItem(accountId, it)
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("物品名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = name,
                onValueChange = { name = Fmt.clip(it, MAX_ITEM_NAME_LEN) },
                placeholder = "如：耳机、相机、纪念币",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("买入时间", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelPanel(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { showCal = true },
                bg = Px.Cream,
                depth = 2.dp,
                contentPadding = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PxText(Fmt.dateYmd(buyDate), size = 13.sp)
                    Spacer(Modifier.weight(1f))
                    PixelIcon("calendar", size = 18.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            PxText("买入价格（元）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = priceStr,
                onValueChange = { priceStr = Fmt.cleanAmountInput(it) },
                placeholder = "如：1200",
                numeric = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("备注", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = note,
                onValueChange = { note = Fmt.clip(it, MAX_NOTE_LEN) },
                placeholder = "可选",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val created = initial?.createdAt?.takeIf { it.isNotEmpty() } ?: LocalDate.now().toString()
            PxText(
                "录入时间：$created · 已用天数与日均价格按买入时间自动计算",
                size = 11.sp, color = Px.GrayText,
            )
        }
    }

    if (showCal) {
        PixelCalendarDialog(
            initial = buyDate,
            onPick = { buyDate = it; showCal = false },
            onDismiss = { showCal = false },
        )
    }
}
