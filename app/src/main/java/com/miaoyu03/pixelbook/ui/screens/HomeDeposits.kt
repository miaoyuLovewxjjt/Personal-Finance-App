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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.miaoyu03.pixelbook.data.CATEGORY_OTHERS
import com.miaoyu03.pixelbook.data.Deposit
import com.miaoyu03.pixelbook.data.DepositCats
import com.miaoyu03.pixelbook.data.DepositKind
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.MAX_CAT_LEN
import com.miaoyu03.pixelbook.data.MAX_NOTE_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelCalendarDialog
import com.miaoyu03.pixelbook.ui.StorageDiagnosticsSection
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelIcons
import com.miaoyu03.pixelbook.ui.PixelOption
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelSegSwitch
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
    onBack: () -> Unit,              // 返回启动首页
    onOpenLedger: (String) -> Unit,
    onOpenAccountDeposits: (String) -> Unit,
    onOpenAssets: (String) -> Unit,
    onOpenItems: (String) -> Unit,   // 我的物品（持有物清单）
    onOpenWork: (String) -> Unit,    // 职业（个人收入形象卡）
    onOpenTasks: (String) -> Unit,   // 我的任务（全部任务列表）
) {
    var tick by remember { mutableIntStateOf(0) }
    var showNewLedger by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Ledger?>(null) }
    var editing by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Ledger?>(null) }
    var showProfileEdit by remember { mutableStateOf(false) }   // 角色面板资料编辑

    val accounts = remember(tick) { store.accounts() }
    val curId = remember(tick) { store.currentAccountId() }
    val account = accounts.firstOrNull { it.id == curId }
    val ledgers = remember(tick, account?.id) {
        account?.let { store.ledgersOf(it.id) } ?: emptyList()
    }

    // 回到前台（含跨 0 点）刷新：页头日期、账本列表等随 tick 重算
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：返回 + 今日日期（页面最上方；账户名/生日在下方角色卡展示）
        PixelHeader(
            title = Fmt.dateFull(LocalDate.now()),
            onBack = onBack,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { Spacer(Modifier.height(14.dp)) }
                if (account != null) {
                    item { Spacer(Modifier.height(16.dp)) }
                    // 角色面板（头像/资料 + 我的钱包入口 + 我的资产概览）
                    item {
                        ProfileCard(
                            store = store,
                            account = account,
                            onEditProfile = { showProfileEdit = true },
                            onOpenWallet = { onOpenAssets(account.id) },
                            onOpenItems = { onOpenItems(account.id) },
                            onOpenWork = { onOpenWork(account.id) },
                            onOpenSaving = { onOpenAccountDeposits(account.id) },
                        )
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                    // 任务列表（我的账本上方；可新增、完成后打对勾）
                    item {
                        TodayTasksSection(
                            store = store,
                            accountId = account.id,
                            refreshKey = tick,        // 读取 tick：任务增删/勾选后 item 正确重组
                            onChange = { tick++ },
                            onOpenAll = { onOpenTasks(account.id) },
                        )
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                    // 我的记账区标题（账本列表）
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PxText("我的记账", size = 15.sp, color = Px.Brown)
                            Spacer(Modifier.weight(1f))
                            PxText("${ledgers.size} / ${com.miaoyu03.pixelbook.data.MAX_LEDGER_PER_ACCOUNT}", size = 11.sp, color = Px.GrayText)
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                    items(ledgers, key = { it.id }) { ledger ->
                        LedgerCard(
                            ledger = ledger,
                            onClick = { onOpenLedger(ledger.id) },
                            onEdit = { editing = ledger },
                            onDelete = { deleting = ledger },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (ledgers.isEmpty()) {
                        item {
                            Spacer(Modifier.height(30.dp))
                            PxText("该账户还没有账本，点击下方按钮新建", size = 11.sp, color = Px.GrayText)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        PixelButton(
                            text = if (ledgers.size >= com.miaoyu03.pixelbook.data.MAX_LEDGER_PER_ACCOUNT) "已达 60 本账本上限" else "＋ 新建账本",
                            onClick = { showNewLedger = true },
                            bg = Px.Yellow,
                            enabled = ledgers.size < com.miaoyu03.pixelbook.data.MAX_LEDGER_PER_ACCOUNT,
                            modifier = Modifier.width(220.dp),
                        )
                    }
                } else {
                    item {
                        Spacer(Modifier.height(60.dp))
                        PixelPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            bg = Px.Cream,
                            contentPadding = 18.dp,
                        ) {
                            PxText("还没有账户。请回到首页新建一个账户，账户下可建立多个账本。", size = 13.sp, color = Px.GrayText)
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }

    // 新建账本（当前账户下）
    if (showNewLedger && account != null) {
        NewLedgerDialog(
            accountId = account.id,
            onDismiss = { showNewLedger = false },
            onCreated = { id ->
                showNewLedger = false
                tick++
                onOpenLedger(id)   // 新建后进入该账本
            },
            store = store,
        )
    }
    deleting?.let { ledger ->
        PixelConfirm(
            title = "删除账本",
            message = "删除后，账本「${ledger.name}」的全部流水、资产、天气记录将一并删除，且无法恢复。确定删除吗？",
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
    // 角色面板资料编辑弹窗（头像/账户名/生日/备注）
    if (showProfileEdit && account != null) {
        ProfileDialog(
            store = store,
            account = account,
            onDismiss = { showProfileEdit = false },
            onSaved = { showProfileEdit = false; tick++ },
        )
    }
}

/** 账户新建 / 编辑输入弹窗（同名不允许，长度 ≤30）。供新首页与账户管理共用。 */
@Composable
fun AccountNameDialog(
    title: String,
    initial: String,
    hint: String,
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    dupHint: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    PixelDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val ok = onSave(name.trim())
                    if (!ok) dupHint() else onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PixelTextField(
                value = name,
                onValueChange = { name = Fmt.clip(it, com.miaoyu03.pixelbook.data.MAX_ACCOUNT_NAME_LEN) },
                placeholder = "账户名",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            PxText(hint, size = 11.sp, color = Px.GrayText)
        }
    }
}

@Composable
private fun LedgerCard(
    ledger: com.miaoyu03.pixelbook.data.Ledger,
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
            // 封面图标：手绘账本（马赛克硬边）
            PixelIcon("ledger", size = 48.dp)
            Spacer(Modifier.width(12.dp))
            PxText(ledger.name, size = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
            // 右侧：编辑（铅笔）在前、删除（垃圾桶）在后
            PixelIconButton(icon = "pencil", size = 26.dp, bg = Px.CreamDark, onClick = onEdit, desc = "编辑账本")
            Spacer(Modifier.width(4.dp))
            PixelIconButton(icon = "trash", size = 26.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除账本")
        }
    }
}

/** 新建账本弹窗（当前账户下）：名称 + 封面色选择 */
@Composable
private fun NewLedgerDialog(
    store: Store,
    accountId: String,
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
                    val created = store.addLedger(accountId, nm, cover)
                    if (created == null) {
                        store.toast("每个账户最多 ${com.miaoyu03.pixelbook.data.MAX_LEDGER_PER_ACCOUNT} 本账本")
                        return@PixelButton
                    }
                    onCreated(created.id)
                },
                bg = Px.Grass, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("账本名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = name,
                onValueChange = { name = Fmt.clip(it, Store.MAX_LEDGER_NAME) },   // 最长 30 字
                placeholder = "如：日常账本", modifier = Modifier.fillMaxWidth(),
            )
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

/** 字节数格式化：B / KB / MB */
private fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f MB", b / 1024.0 / 1024.0)
}

/* ================================================================
 * 账户存款明细（我的记账 → 存款明细）：
 * 资产为账户级「公用一本」，不分账本；各账本旧资产已自动汇入（备注标来源）。
 * ================================================================ */

@Composable
fun AccountDepositsScreen(
    store: Store,
    accountId: String,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var hideAmount by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Deposit?>(null) }
    var deleting by remember { mutableStateOf<Deposit?>(null) }

    val deposits = remember(tick, accountId) { store.accountDepList(accountId) }
    val total = remember(tick, accountId) { store.accountTotalDeposits(accountId) }
    val grouped = remember(deposits) { deposits.groupBy { it.category } }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(
            title = "我的资产",
            onBack = onBack,
        )

        // 标题下方靠右：显示/隐藏金额开关（与标题及下方卡片留间距）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AmountSwitch(hide = hideAmount, onToggle = { hideAmount = !hideAmount })
        }

        // 账户总资产卡
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            bg = Px.Cream,
            contentPadding = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PixelIcon("chest", size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    PxText(
                        if (hideAmount) "总资产 ¥****" else "总资产 ${Fmt.yen(total)}",
                        size = 16.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                PxText(
                    "共 ${deposits.size} 笔 · 按类别分组",
                    size = 11.sp,
                    color = Px.GrayText,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 公用资产：按类别分组（现金/黄金/股票/基金/珠宝首饰/其他 + 自定义），各组内时间降序
        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (cat, list) ->
                DepositGroup(
                    title = cat.ifEmpty { "未分类" },
                    icon = depCatIcon(cat),
                    list = list,
                    hideAmount = hideAmount,
                    onEdit = { editing = it },
                    onDelete = { deleting = it },
                )
            }
            if (deposits.isEmpty()) {
                item {
                    Spacer(Modifier.height(60.dp))
                    PxText("还没有资产，点击下方按钮新增", size = 13.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
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
                text = "＋ 新增资产",
                onClick = { showForm = true },
                modifier = Modifier.width(220.dp),
            )
        }
    }

    if (showForm || editing != null) {
        AccountDepositFormDialog(
            store = store,
            accountId = accountId,
            initial = editing,
            onDismiss = { showForm = false; editing = null },
            onSaved = { showForm = false; editing = null; tick++ },
        )
    }
    deleting?.let { d ->
        PixelConfirm(
            title = "删除资产",
            message = "确定删除「${d.name}」这笔资产吗？",
            confirmText = "删除",
            onConfirm = { store.deleteAccountDep(accountId, d.id); tick++ },
            onDismiss = { deleting = null },
        )
    }
}

/** 账户公用资产 新增/编辑 表单（新增不归属任何账本） */
@Composable
private fun AccountDepositFormDialog(
    store: Store,
    accountId: String,
    initial: Deposit?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var valueStr by remember { mutableStateOf(if (initial != null) Fmt.money(initial.value) else "") }
    var showCal by remember { mutableStateOf(false) }
    // 类别：新记录默认「现金」；编辑旧数据按 kind 推导（金钱类→现金，非金钱类→其他，兼容旧存档）；
    // 老类别不在类别表中时归入「自定义」并预填输入框
    val initCat = initial?.let { it.category.ifEmpty { if (it.kind == DepositKind.MONEY) DepositCats.CASH else CATEGORY_OTHERS } } ?: DepositCats.CASH
    val depCats = remember(accountId) { store.depCats(accountId) }
    var cat by remember {
        mutableStateOf(if (initCat == CUSTOM_CAT || initCat in depCats) initCat else CUSTOM_CAT)
    }
    var customCat by remember {
        mutableStateOf(if (initCat != CUSTOM_CAT && initCat !in depCats) initCat else "")
    }

    PixelDialog(
        title = if (initial == null) "新增资产" else "编辑资产",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val v = Fmt.parseCents(valueStr)
                    if (v == null) { store.toast("请填写有效金额"); return@PixelButton }
                    if (name.trim().isEmpty()) { store.toast("请填写物品名称"); return@PixelButton }
                    // 类别解析：自定义 → 输入框内容（新类别自动入库）；现金=金钱类，其余=非金钱类（旧版兼容）
                    val finalCat = if (cat == CUSTOM_CAT) {
                        val c = customCat.trim()
                        if (c.isEmpty()) { store.toast("请输入自定义类别"); return@PixelButton }
                        store.addDepCat(accountId, c)
                        c
                    } else cat
                    val base = initial
                    val d = Deposit(
                        id = base?.id ?: "d${System.currentTimeMillis()}",
                        ledgerId = "",
                        date = date,
                        kind = if (finalCat == DepositCats.CASH) DepositKind.MONEY else DepositKind.GOODS,
                        name = name.trim(),
                        note = note.trim(),
                        value = v,
                        category = finalCat,
                    )
                    if (base == null) store.addAccountDep(accountId, d) else store.updateAccountDep(accountId, d)
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
        contentScrollable = true,
    ) {
        // 表单体由 PixelDialog 提供弹性滚动：软键盘弹出后仍能查看/点选下方字段
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
                        PixelIcon("calendar", size = 24.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 类型
            PxText("类别", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelDropdown(
                label = "类别",
                options = depCats.map { PixelOption(it, depCatIcon(it)) } + listOf(PixelOption(CUSTOM_CAT, "dots")),
                selected = if (cat == CUSTOM_CAT) CUSTOM_CAT else cat,
                onSelect = { sel -> cat = sel },
                modifier = Modifier.fillMaxWidth(),
                width = 140.dp,
            )
            if (cat == CUSTOM_CAT) {
                Spacer(Modifier.height(8.dp))
                PxText("自定义类别", size = 12.sp, color = Px.GrayText)
                Spacer(Modifier.height(4.dp))
                PixelTextField(
                    value = customCat,
                    onValueChange = { customCat = Fmt.clip(it, MAX_CAT_LEN) },
                    placeholder = "如：收藏品、珠宝",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            PxText("物品名称", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(value = name, onValueChange = { name = it }, placeholder = "如：现金、黄金", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PxText("备注", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = note,
                onValueChange = { note = Fmt.clip(it, MAX_NOTE_LEN) },
                placeholder = "可选",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("价值（元）", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = valueStr,
                onValueChange = { valueStr = Fmt.cleanAmountInput(it) },
                placeholder = "如：9000", numeric = true, modifier = Modifier.fillMaxWidth(),
            )
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
                PixelIcon(depCatIcon(d.category), size = 26.dp)
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
                    d.category.ifEmpty { d.kind.label },
                    bg = depCatColor(d.category),
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
 * 编辑账本弹窗：名称 / 字体（主页卡片右侧铅笔进入）
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
                    // 封面配色保留原值（已不提供编辑入口）
                    store.updateLedger(ledger.id, nm, font, ledger.coverColor)
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
            Spacer(Modifier.height(14.dp))
            // 存储位置提示（账户文件夹内的账本单文件）
            PxText("数据按账户文件夹存放于当前存储目录，账本名自动同步文件名", size = 11.sp, color = Px.GrayText)
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
    accountId: String,
    onDismiss: () -> Unit,
    onStorageChanged: () -> Unit,
) {
    var pendingSwitch by remember { mutableStateOf<Uri?>(null) }   // 待确认的目标目录

    // 账号管理（新增/编辑/删除）
    var tickAcc by remember { mutableIntStateOf(0) }
    var addingAcc by remember { mutableStateOf(false) }
    var editingAcc by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Account?>(null) }
    var deletingAcc by remember { mutableStateOf<com.miaoyu03.pixelbook.data.Account?>(null) }
    val accs = remember(tickAcc) { store.accountsByRecent() }
    val curAcc = remember(tickAcc, accs) { store.currentAccountId() }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val treeLauncher = rememberLauncherForActivityResult(OpenTreeContract()) { uri ->
        if (uri != null) {
            // 关键：SAF 授权必须显式 takePersistableUriPermission 才能跨重启持有。
            // 否则只是本次进程/会话有效，重启后 storage_tree 指向的目录失去访问权，
            // 启动时读不到 my_account.json → 误显示「没有账户/账本」（数据其实还在目录里）。
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            pendingSwitch = uri
        }
    }

    PixelDialog(
        title = "设置",
        onDismiss = onDismiss,
        contentScrollable = true,
        footer = {
            PixelButton("完成", onDismiss, bg = Px.Clay, height = 40.dp, modifier = Modifier.width(140.dp))
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ---------- 账号管理（新首页右上角设置进入） ----------
            PxText("账号管理", size = 14.sp)
            Spacer(Modifier.height(6.dp))
            accs.forEach { a ->
                val isCur = a.id == curAcc
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCur) Px.Grass.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon("idcard", size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    PxText(a.name, size = 13.sp, color = Px.Brown, modifier = Modifier.weight(1f), maxLines = 1)
                    if (isCur) PixelTag("当前", bg = Px.Grass, textColor = Px.Cream)
                    Spacer(Modifier.width(6.dp))
                    PixelIconButton(icon = "pencil", size = 24.dp, onClick = { editingAcc = a }, desc = "编辑")
                    Spacer(Modifier.width(2.dp))
                    PixelIconButton(icon = "trash", size = 24.dp, onClick = { deletingAcc = a }, desc = "删除")
                }
            }
            PixelButton(
                "＋ 新建账户",
                onClick = { addingAcc = true },
                bg = Px.Yellow, height = 36.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PxText("数据存储", size = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("chest", size = 18.dp)
                Spacer(Modifier.width(6.dp))
                PxText("当前目录：${store.storageDirDescription()}", size = 12.sp, color = Px.Wood)
            }
            // 上次切换结果（失败原因直接展示，方便排查）
            store.lastSwitchResult()?.let { last ->
                Spacer(Modifier.height(4.dp))
                PxText("上次切换：$last", size = 11.sp, color = if (last.startsWith("ok:")) Px.GrassDark else Px.Red)
            }
            // 最近一次写入错误（toast 错过也能查）
            store.lastWriteError()?.let { err ->
                Spacer(Modifier.height(4.dp))
                PxText("最近写入出错：$err", size = 11.sp, color = Px.Red)
            }
            // 数据损坏提示（如账本 JSON 解析失败）：不会静默当成空账本
            store.lastDataError()?.let { err ->
                Spacer(Modifier.height(4.dp))
                PxText("数据异常：$err", size = 11.sp, color = Px.Red)
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
            }
            // 类别维护（属于当前账户）
            Spacer(Modifier.height(10.dp))
            // 历史存储目录（只读列举：序号 + 路径，自动换行；点击路径可复制）
            val history = store.storageHistory()
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PxText("历史存储目录", size = 12.sp, color = Px.Wood)
                val clip = LocalClipboardManager.current
                history.forEachIndexed { idx, (_, _, path) ->
                    Spacer(Modifier.height(6.dp))
                    PxText(
                        "${idx + 1}、$path",
                        size = 11.sp,
                        color = Px.Brown,
                        modifier = Modifier.clickable {
                            clip.setText(AnnotatedString(path))
                            store.toast("已复制路径：$path")
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PxText("四季记账app信息", size = 14.sp)
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
            // debug 版「存储自检」入口；release 版为空的占位实现（源码集隔离，正式包不含诊断代码）
            StorageDiagnosticsSection(store)
        }
    }

    // 切换前确认（覆盖在设置弹窗之上）
    pendingSwitch?.let { uri ->
        PixelConfirm(
            title = "切换存储目录",
            message = "确定把数据存储切换到所选目录吗？现有数据将自动迁移（数据较多时需等待几秒），迁移完成后自动生效。",
            confirmText = "切换",
            onConfirm = {
                pendingSwitch = null
                // 整树拷贝 + 旧数据整理都在后台执行，避免主线程卡死被系统判 ANR（表现为闪退）
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    store.toast("正在迁移数据，请稍候…")
                }
                Thread {
                    val r = store.switchStorage(uri)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 成功时附带绝对路径，便于确认真实生效目录
                        store.toast(if (r.startsWith("ok:")) "$r\n路径：${store.storagePath()}" else r)
                        onStorageChanged()
                    }
                }.start()
            },
            onDismiss = { pendingSwitch = null },
        )
    }
    // 账号管理弹窗
    if (addingAcc) {
        AccountNameDialog(
            title = "新建账户",
            initial = "",
            hint = "账户名（不可与已有账户同名）",
            onSave = { nm -> store.addAccount(nm) != null },
            onDismiss = { addingAcc = false },
            onSaved = { addingAcc = false; tickAcc++; onStorageChanged() },
            dupHint = { store.toast("账户名无效或已存在") },
        )
    }
    editingAcc?.let { a ->
        AccountNameDialog(
            title = "编辑账户",
            initial = a.name,
            hint = "修改账户名将自动同步重命名数据文件夹",
            onSave = { nm -> store.renameAccount(a.id, nm) },
            onDismiss = { editingAcc = null },
            onSaved = { editingAcc = null; tickAcc++ },
            dupHint = { store.toast("账户名无效或已存在") },
        )
    }
    deletingAcc?.let { a ->
        PixelConfirm(
            title = "删除账户",
            message = "删除账户「${a.name}」将同时删除其下全部账本与数据，无法恢复。确定删除吗？",
            confirmText = "删除",
            onConfirm = {
                val ok = store.deleteAccount(a.id)
                if (ok) {
                    // 若删的是当前账户，切到剩余第一个（若有）
                    if (store.currentAccountId() == null) {
                        store.accounts().firstOrNull()?.let { store.setCurrentAccountId(it.id) }
                    }
                    tickAcc++
                    onStorageChanged()
                } else store.toast("删除失败")
            },
            onDismiss = { deletingAcc = null },
        )
    }
}

/* ================================================================
 * 金额显示开关（存款明细页右上角）：显示 / 隐藏两段式，像素风
 * ================================================================ */

@Composable
private fun AmountSwitch(hide: Boolean, onToggle: () -> Unit) {
    PixelSegSwitch(hidden = hide, onToggle = onToggle)
}

/** 资产类别 → 图标（现金=钞票 / 黄金=金条 / 股票=走势图 / 基金=简化柱状图 / 珠宝首饰=项链 / 其他·自定义=省略号） */
fun depCatIcon(cat: String): String = when (cat) {
    DepositCats.CASH -> "bills"
    DepositCats.GOLD -> "goldBars"
    DepositCats.STOCK -> "stockTrend"
    DepositCats.FUND -> "statChart"
    DepositCats.JEWELRY -> "jewelryNecklace"
    else -> "dots"
}

/** 资产类别 → 标签底色（默认类低饱和暖色，自定义灰） */
fun depCatColor(cat: String): Color = when (cat) {
    DepositCats.CASH -> Px.Clay
    DepositCats.GOLD -> Px.YellowDark
    DepositCats.STOCK -> Px.SkyDark
    DepositCats.FUND -> Px.WoodDark
    DepositCats.JEWELRY -> Px.Red
    else -> Px.GrayText
}
