package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.AssetAccount
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.MAX_ASSET_SUB_LEN
import com.miaoyu03.pixelbook.data.MAX_CAT_LEN
import com.miaoyu03.pixelbook.data.MAX_ROLE_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.data.fullLabel
import com.miaoyu03.pixelbook.data.label
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelConfirm
import com.miaoyu03.pixelbook.ui.PixelDropdown
import com.miaoyu03.pixelbook.ui.PixelHeader
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelOption
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelSegSwitch
import com.miaoyu03.pixelbook.ui.PixelTag
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText
import java.time.LocalDate
import java.time.YearMonth

/* ================================================================
 * 资产账户信息维护页（我的记账 → 资产账户信息维护）
 * 左侧导航：账户信息维护 / 每日流水（记账明细同款折叠样式）
 * 资产信息页：按「资产来源(类别)」分组卡片 → 每行显示 图标+名称+金额
 * ================================================================ */

/** 资产来源默认下拉选项 */
private val SOURCE_PRESETS = listOf("支付宝", "银行卡", "微信", "自定义")

/** 来源 → 图标 */
private fun sourceIcon(src: String): String = when (src) {
    "支付宝" -> "alipay"
    "微信" -> "chat"
    "银行卡" -> "bankCard"
    else -> "coinPile"
}

@Composable
fun AssetsScreen(
    store: Store,
    accountId: String,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }          // 0=资产信息 1=每日流水
    var navCollapsed by remember { mutableStateOf(false) }
    var flowOpen by remember { mutableStateOf(true) }  // 每日流水子导航(年/月/日)整体展开
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var expandedYears by remember { mutableStateOf(setOf<Int>()) }
    var expandedMonths by remember { mutableStateOf(setOf<String>()) }
    var tick by remember { mutableIntStateOf(0) }

    val ledgers = remember(tick, accountId) { store.ledgersOf(accountId) }
    val allTxs = remember(tick, ledgers) {
        ledgers.flatMap { l -> store.txList(l.id).map { Triple(l, it, it.amount * if (it.dir == TxDir.IN) 1 else -1) } }
    }
    val allDates = remember(allTxs) { allTxs.map { it.second.date }.distinct().sortedDescending() }
    val years = remember(allDates) { (allDates.map { it.year } + LocalDate.now().year).distinct().sortedDescending() }

    // 进入每日流水时：自动展开当前选中日期的 年 → 月 链，让用户能直接看到日层级
    LaunchedEffect(tab, flowOpen, selectedDate, allDates) {
        if (tab == 1 && flowOpen && allDates.isNotEmpty()) {
            val y = selectedDate.year
            val ym = "%04d-%02d".format(y, selectedDate.monthValue)
            expandedYears = expandedYears + y
            expandedMonths = expandedMonths + ym
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PixelHeader(title = "我的钱包", onBack = onBack)

        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧导航（同记账明细：右侧分隔竖线 + 条目层级 + 折叠三角骑线）
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (navCollapsed) 38.dp else 108.dp)
                    .background(Px.CreamBg)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRect(
                            Px.Brown,
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - stroke, 0f),
                            size = androidx.compose.ui.geometry.Size(stroke, size.height),
                        )
                    },
            ) {
                if (!navCollapsed) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 两个主入口（一级，纯文字无图标）
                        AssetNavRow(
                            title = "账户信息",
                            icon = null,
                            level = 1,
                            active = tab == 0,
                            expanded = null,
                        ) { tab = 0 }
                        AssetNavRow(
                            title = "每日流水",
                            icon = null,
                            level = 1,
                            active = tab == 1,
                            expanded = flowOpen,   // 右侧折叠箭头：收起=▼ 展开=▶
                        ) {
                            // 未激活：切到每日流水并展开子导航；已激活：点按收起/展开子导航
                            if (tab != 1) { tab = 1; flowOpen = true } else { flowOpen = !flowOpen }
                        }
                        // 每日流水下的 年→月→日（二级折叠；flowOpen 控制整体展开）
                        if (tab == 1 && flowOpen) {
                            Spacer(Modifier.height(2.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                years.forEach { y ->
                                    val yExp = y in expandedYears
                                    AssetNavRow("${y}年", null, level = 2, active = false, expanded = yExp) {
                                        expandedYears = if (yExp) expandedYears - y else expandedYears + y
                                    }
                                    if (yExp) {
                                        val months = (1..12).filter { m -> allDates.any { it.year == y && it.monthValue == m } }
                                        months.forEach { m ->
                                            val ym = "%04d-%02d".format(y, m)
                                            val mExp = ym in expandedMonths
                                            AssetNavRow("${m}月", null, level = 2, active = false, expanded = mExp) {
                                                expandedMonths = if (mExp) expandedMonths - ym else expandedMonths + ym
                                            }
                                            if (mExp) {
                                                allDates.filter { it.year == y && it.monthValue == m }.forEach { d ->
                                                    AssetNavRow("${d.dayOfMonth}日", null, level = 3, active = d == selectedDate, expanded = null) {
                                                        selectedDate = d
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // 折叠三角（骑右缘竖线居中）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { navCollapsed = !navCollapsed }
                            .offset(x = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelIcon(if (navCollapsed) "triR" else "triL", size = 15.dp, desc = if (navCollapsed) "展开导航" else "折叠导航")
                    }
                }
            }

            // 右侧内容
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    1 -> DailyFlowPane(store, accountId, selectedDate, allTxs)
                    else -> AssetListPane(store, accountId, onChanged = { tick++ })
                }
            }
        }
    }
}

/**
 * 左侧导航行（记账明细同款视觉）：
 * level=1 一级(图标+文字)；level=2 缩进二级(文字+行尾折叠三角)；level=3 更深缩进。
 * expanded=null 表示不可折叠（不显示三角）。
 * 折叠箭头统一：收起 = 倒三角(▼ chevronD)，展开 = 朝右开口(▶ chevronR)。
 */
@Composable
private fun AssetNavRow(
    title: String,
    icon: String?,
    level: Int,
    active: Boolean,
    expanded: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Px.Grass.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                horizontal = when (level) {
                    1 -> 10.dp
                    2 -> 20.dp
                    else -> 30.dp
                },
                vertical = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PixelIcon(icon, size = 16.dp)
            Spacer(Modifier.width(5.dp))
        }
        PxText(
            title,
            size = when (level) { 1 -> 14.sp; 2 -> 13.sp; else -> 12.sp },
            color = when { active -> Px.GrassDark; level == 1 -> Px.Brown; else -> Px.Wood },
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        // 折叠箭头：收起 = 倒三角(▼)，展开 = 朝右开口(▶)
        if (expanded != null) {
            PixelIcon(if (expanded) "chevronR" else "chevronD", size = 12.dp)
        }
    }
}

/* ================================================================
 * 资产信息维护（按来源分组，钱袋/卡图标，清晰大字）
 * ================================================================ */

@Composable
private fun AssetListPane(
    store: Store,
    accountId: String,
    onChanged: () -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AssetAccount?>(null) }
    var confirmDelete by remember { mutableStateOf<AssetAccount?>(null) }   // 删除前确认（有流水时提示）
    var hideBal by remember { mutableStateOf(false) }
    // 数据变更计数：新增/编辑/删除后自增 → assets 重新读取（remember 依赖它，而不是只依赖 accountId）
    var refresh by remember { mutableIntStateOf(0) }
    fun bump() { refresh++; onChanged() }

    val assets = remember(accountId, refresh) { store.assetsOf(accountId) }
    val grouped = remember(assets) { assets.groupBy { it.category } }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：标题 + 隐藏开关（新增按钮在内容下方）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon("balance", size = 32.dp)
            Spacer(Modifier.width(8.dp))
            PxText("余额一览", size = 17.sp)
            Spacer(Modifier.weight(1f))
            PixelSegSwitch(hidden = hideBal, onToggle = { hideBal = !hideBal })
        }

        if (assets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelIcon("balance", size = 56.dp)
                    Spacer(Modifier.height(8.dp))
                    PxText("还没有资产账户", size = 14.sp, color = Px.GrayText)
                    Spacer(Modifier.height(4.dp))
                    PxText("点下方「新增」添加支付宝 / 银行卡 / 微信", size = 12.sp, color = Px.GrayText)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                grouped.forEach { (cat, list) ->
                    item {
                        // 类别头：图标 + 名称 + 个数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelIcon(sourceIcon(cat), size = 24.dp)
                            Spacer(Modifier.width(6.dp))
                            PxText(cat, size = 15.sp, color = Px.Wood)
                            Spacer(Modifier.weight(1f))
                            PxText("${list.size} 个", size = 12.sp, color = Px.GrayText)
                        }
                    }
                    items(list.size, key = { list[it].id }) { i ->
                        val a = list[i]
                        AssetCard(
                            a = a,
                            balance = store.assetBalance(accountId, a.id),
                            hide = hideBal,
                            onClick = { editing = a },
                            onDelete = { confirmDelete = a },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }

        // 底部：新增按钮（内容之下）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelButton(
                text = "＋ 新增钱包账户",
                onClick = { showForm = true },
                bg = Px.Grass,
                modifier = Modifier.width(200.dp),
            )
        }
    }

    if (showForm || editing != null) {
        AssetFormDialog(
            store = store,
            accountId = accountId,
            initial = editing,
            onDismiss = { showForm = false; editing = null },
            onSaved = { showForm = false; editing = null; bump() },
        )
    }
    // 删除资产前确认：该资产已被流水引用时明确提示（历史流水将显示为「未指定」）
    confirmDelete?.let { a ->
        val inUse = store.assetHasFlow(accountId, a.id)
        PixelConfirm(
            title = "删除资产账户",
            message = if (inUse) {
                "「${a.label()}」已被历史流水引用，删除后这些流水将显示为「未指定」。确定删除吗？"
            } else {
                "确定删除资产账户「${a.label()}」吗？"
            },
            confirmText = "删除",
            onConfirm = {
                store.deleteAsset(accountId, a.id)
                bump()
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/** 单条资产卡片：名称/角色 + 账号 + 最新余额 + 编辑/删除（来源由分组标题表达，卡片内不再放大图标） */
@Composable
private fun AssetCard(
    a: AssetAccount,
    balance: Long,
    hide: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clickable(onClick = onClick),
        bg = Px.Cream,
        contentPadding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 名称（角色优先）+ 账号（长名换行完整显示，不裁字）
            Column(modifier = Modifier.weight(1f)) {
                PxText(
                    if (a.role.isNotEmpty()) a.role else a.sub.ifEmpty { a.category },
                    size = 15.sp,
                    color = Px.Brown,
                    maxLines = 2,
                )
                // 副行只显示与主行不同的补充信息（角色=主行时显示「类别 · 卡号」）
                val mainLine = if (a.role.isNotEmpty()) a.role else a.sub.ifEmpty { a.category }
                val subLine = if (a.role.isNotEmpty()) "${a.category}${if (a.sub.isNotEmpty()) " · ${a.sub}" else ""}" else ""
                if (subLine.isNotEmpty()) {
                    PxText(
                        if (hide) "•••• ••••" else subLine,
                        size = 11.sp,
                        color = Px.GrayText,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // 最新余额（自动累计，只读）
            Column(horizontalAlignment = Alignment.End) {
                PxText("最新余额", size = 10.sp, color = Px.GrayText)
                PxText(
                    if (hide) "¥****" else Fmt.yen(balance),
                    size = 15.sp,
                    color = if (balance >= 0) Px.GrassDark else Px.ClayDark,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 编辑（铅笔）在删除（垃圾桶）左侧；两按钮间距拉开
            PixelIconButton(icon = "pencil", size = 26.dp, bg = Px.CreamDark, onClick = onClick, desc = "编辑")
            Spacer(Modifier.width(10.dp))
            PixelIconButton(icon = "trash", size = 26.dp, bg = Px.CreamDark, onClick = onDelete, desc = "删除")
        }
    }
}

/** 资产账户新增/编辑：来源下拉(支付宝/银行卡/微信/自定义) + 账号 + 角色 */
@Composable
private fun AssetFormDialog(
    store: Store,
    accountId: String,
    initial: AssetAccount?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var source by remember { mutableStateOf(initial?.category?.takeIf { it in SOURCE_PRESETS } ?: if (initial != null) "自定义" else "支付宝") }
    var customSource by remember { mutableStateOf(if (initial != null && initial.category !in SOURCE_PRESETS) initial.category else "") }
    var sub by remember { mutableStateOf(initial?.sub ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }

    // 资产来源最终名
    fun finalSource(): String = if (source == "自定义") customSource.trim() else source

    PixelDialog(
        title = if (initial == null) "新增钱包账户" else "编辑钱包账户",
        onDismiss = onDismiss,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val src = finalSource()
                    if (src.isEmpty()) { store.toast("请选择或输入资产来源"); return@PixelButton }
                    val a = AssetAccount(
                        id = initial?.id ?: "a${System.currentTimeMillis()}",
                        category = src,
                        sub = sub.trim(),
                        role = role.trim(),
                    )
                    if (!store.saveAsset(accountId, a)) {
                        store.toast("角色不可重复")
                        return@PixelButton
                    }
                    onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
        contentScrollable = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            PxText("资产来源", size = 13.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelDropdown(
                label = "资产来源",
                options = SOURCE_PRESETS.map { PixelOption(it, sourceIcon(it)) },
                selected = source,
                onSelect = { source = it },
                modifier = Modifier.fillMaxWidth(),
                width = 140.dp,
            )
            if (source == "自定义") {
                Spacer(Modifier.height(8.dp))
                PixelTextField(
                    value = customSource,
                    onValueChange = { customSource = Fmt.clip(it, MAX_CAT_LEN) },
                    placeholder = "如：余额宝、信用卡",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            PxText("账号（卡号/账号/户名）", size = 13.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = sub,
                onValueChange = { sub = Fmt.clip(it, MAX_ASSET_SUB_LEN) },
                placeholder = "如：6222 **** 8888",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("角色（可选，不可重复）", size = 13.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = role,
                onValueChange = { role = Fmt.clip(it, MAX_ROLE_LEN) },
                placeholder = "如：工资卡",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            PxText("最新余额由流水自动累计，仅展示不可修改。", size = 11.sp, color = Px.GrayText, fontStyle = FontStyle.Italic)
        }
    }
}

/* ================================================================
 * 每日流水（年→月→日，按资产分组，收入/支出带正负号）
 * ================================================================ */

@Composable
private fun DailyFlowPane(
    store: Store,
    accountId: String,
    selectedDate: LocalDate,
    allTxs: List<Triple<com.miaoyu03.pixelbook.data.Ledger, com.miaoyu03.pixelbook.data.Tx, Long>>,
) {
    var hideAmount by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val assets = remember(accountId) { store.assetsOf(accountId) }
    val assetById = remember(assets) { assets.associateBy { it.id } }

    val dayTxs = allTxs.filter { it.second.date == selectedDate }

    Column(modifier = Modifier.fillMaxSize()) {
        // 日期标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon("calendarCute", size = 26.dp)
            Spacer(Modifier.width(6.dp))
            PxText(Fmt.dateFull(selectedDate), size = 16.sp)
        }
        // 标题下方靠右：显示/隐藏金额（与标题及下方内容留间距）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            PixelSegSwitch(showLabel = "显示金额", hideLabel = "隐藏金额", hidden = hideAmount, onToggle = { hideAmount = !hideAmount })
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 当天小结
            item {
                val inSum = dayTxs.filter { it.third > 0 }.sumOf { it.third }
                val outSum = dayTxs.filter { it.third < 0 }.sumOf { it.third }
                PixelPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    bg = Px.Cream,
                    contentPadding = 10.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxText("收入", size = 12.sp, color = Px.GrassDark)
                        Spacer(Modifier.width(8.dp))
                        PxText(if (hideAmount) "¥****" else "+${Fmt.money(inSum)}", size = 14.sp, color = Px.GrassDark)
                        Spacer(Modifier.weight(1f))
                        PxText("支出", size = 12.sp, color = Px.WoodDark)
                        Spacer(Modifier.width(8.dp))
                        PxText(if (hideAmount) "¥****" else "-${Fmt.money(-outSum)}", size = 14.sp, color = Px.WoodDark)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (dayTxs.isEmpty()) {
                item {
                    Spacer(Modifier.height(60.dp))
                    PxText("这一天没有流水", size = 13.sp, color = Px.GrayText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            } else {
                // 按资产分组（未指定 → 未指定账户）
                dayTxs.groupBy { it.second.asset.ifEmpty { "未指定" } }.forEach { (astId, txs) ->
                    val headerKey = if (astId == "未指定") "未指定" else assetById[astId]?.label() ?: "未指定"
                    val isCollapsed = headerKey in collapsed
                    item {
                        // 资产分组头 = 该组父级：更深的文字、更大的图标与字号，视觉上明显高于组内流水条目
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCollapsed) Color.Transparent else Px.Yellow.copy(alpha = 0.16f))
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .clickable { collapsed = if (isCollapsed) collapsed - headerKey else collapsed + headerKey },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelIcon(if (isCollapsed) "chevronD" else "chevronR", size = 14.dp)
                            Spacer(Modifier.width(6.dp))
                            PixelIcon(if (astId == "未指定") "dots" else (assetById[astId]?.category?.let { sourceIcon(it) } ?: "bankCard"), size = 22.dp)
                            Spacer(Modifier.width(7.dp))
                            PxText(headerKey, size = 16.sp, color = Px.Brown, modifier = Modifier.weight(1f))
                            val net = txs.sumOf { it.third }
                            PxText(
                                if (hideAmount) "¥****" else if (net >= 0) "+${Fmt.money(net)}" else "-${Fmt.money(-net)}",
                                size = 15.sp,
                                color = if (net >= 0) Px.GrassDark else Px.ClayDark,
                            )
                        }
                    }
                    if (!isCollapsed) {
                        txs.sortedBy { it.second.time }.forEach { (l, t, signed) ->
                            item { FlowRow(l, t, signed, hideAmount) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FlowRow(
    l: com.miaoyu03.pixelbook.data.Ledger,
    t: com.miaoyu03.pixelbook.data.Tx,
    signed: Long,
    hideAmount: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PxText(t.time, size = 12.sp, color = Px.GrayText, modifier = Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            PxText(t.category + if (t.name.isNotEmpty()) " · ${t.name}" else "", size = 13.sp, maxLines = 1)
            PxText("${l.name}", size = 10.sp, color = Px.GrayText, maxLines = 1)
        }
        PxText(
            if (hideAmount) "¥****" else if (signed >= 0) "+${Fmt.money(signed)}" else "-${Fmt.money(-signed)}",
            size = 14.sp,
            color = if (signed >= 0) Px.GrassDark else Px.WoodDark,
        )
    }
}
