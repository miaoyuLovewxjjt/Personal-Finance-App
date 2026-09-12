package com.miaoyu03.pixelbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.Account
import com.miaoyu03.pixelbook.data.DepositCats
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.MAX_ACCOUNT_NAME_LEN
import com.miaoyu03.pixelbook.data.MAX_ACCOUNT_NOTE_LEN
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.PixelButton
import com.miaoyu03.pixelbook.ui.PixelDialog
import com.miaoyu03.pixelbook.ui.PixelIcon
import com.miaoyu03.pixelbook.ui.PixelIconButton
import com.miaoyu03.pixelbook.ui.PixelPanel
import com.miaoyu03.pixelbook.ui.PixelTextField
import com.miaoyu03.pixelbook.ui.PxText

/* ================================================================
 * 目录页角色面板：
 *  ① 角色形象（短发男子 / 长发女子）+ 账户名 + 生日 + 备注 + 铅笔编辑
 *  ② 我的钱包（图标装饰 + 文字可点击 → 我的钱包页）
 *  ③ 我的资产（图标装饰 + 文字可点击 → 我的资产页）+ 类别金额 + 总资产价值
 * 规则：可跳转项 = 图标 + 文字，点击文字跳转，图标仅为装饰。
 * ================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCard(
    store: Store,
    account: Account,
    onEditProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenWork: () -> Unit,
    onOpenSaving: () -> Unit,
) {
    // 资产类别汇总（账户级公用资产；默认类别序在前，自定义按名称）
    val deps = remember(account.id) { store.accountDepList(account.id) }
    val byCat = remember(deps) { deps.groupBy { it.category } }
    val orderedCats = remember(byCat) {
        val keys = byCat.keys
        (DepositCats.list.filter { it in keys } + keys.filter { it !in DepositCats.list }.sorted())
            .map { it to byCat.getValue(it).sumOf { d -> d.value } }
    }
    val total = remember(deps) { deps.sumOf { it.value } }
    // 职业名（点按跳转职业页）
    val workOccupation = remember(account.id) { store.workProfile(account.id).occupation }

    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        bg = Px.Cream,
        contentPadding = 12.dp,
    ) {
        // 关闭点击组件最小触摸尺寸强制（48dp），保证图标严格按内容左对齐
        CompositionLocalProvider(
            androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false,
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ① 角色头部：头像 + 账户名/生日/备注 + 右上铅笔编辑
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(if (account.avatar == 1) "avatarWoman" else "avatarMan", size = 60.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PxText(account.name, size = 17.sp, color = Px.Brown, maxLines = 1)
                    // 职业（点按文字 → 职业页；briefcase 图标为装饰）
                    val occ = workOccupation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon("briefcase", size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        PxText(
                            occ.ifEmpty { "设置职业" },
                            size = 11.sp,
                            color = if (occ.isEmpty()) Px.GrayText else Px.WoodDark,
                            modifier = Modifier
                                .clickable(onClick = onOpenWork)
                                .padding(vertical = 2.dp, horizontal = 1.dp),
                        )
                    }
                    if (account.birthday.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        PxText("生日 · ${account.birthday}", size = 11.sp, color = Px.GrayText)
                    }
                    if (account.note.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        PxText(
                            account.note, size = 11.sp, color = Px.GrayText,
                            fontStyle = FontStyle.Italic, maxLines = 2,
                        )
                    }
                }
                PixelIconButton(
                    icon = "pencilTitle", size = 34.dp, bg = Px.CreamDark,
                    onClick = onEditProfile, desc = "编辑资料",
                )
            }
            Spacer(Modifier.height(12.dp))
            ProfileDivider()
            Spacer(Modifier.height(2.dp))
            // ② 我的钱包（图标装饰 + 文字可点击 → 我的钱包页；与资产入口同款图标/字号）
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("bankCard", size = 28.dp)
                Spacer(Modifier.width(8.dp))
                PxText(
                    "我的钱包", size = 15.sp, color = Px.Brown,
                    modifier = Modifier
                        .clickable(onClick = onOpenWallet)
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            ProfileDivider()
            Spacer(Modifier.height(2.dp))
            // ②b 我的物品（纸箱图标装饰 + 文字可点击 → 我的物品页；与钱包入口同款）
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("box", size = 28.dp)
                Spacer(Modifier.width(8.dp))
                PxText(
                    "我的物品", size = 15.sp, color = Px.Brown,
                    modifier = Modifier
                        .clickable(onClick = onOpenItems)
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            ProfileDivider()
            Spacer(Modifier.height(6.dp))
            // ③ 我的资产：三联排版 —— 左侧竖排标题 | 右侧 类别名 | 金额
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左栏：chest 图标 + 竖排「我的资产」（文字可点击 → 资产明细页）
                // 图标起点与「我的钱包」行图标严格对齐（同为卡片内容左缘，无水平缩进）
                Column(
                    modifier = Modifier
                        .clickable(onClick = onOpenSaving)
                        .width(32.dp)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 宝箱素材透明留白较多，使用 32dp 后与上方钱包图标的视觉大小一致
                    PixelIcon("chest", size = 32.dp)
                    Spacer(Modifier.height(2.dp))
                    PxText("我", size = 15.sp, color = Px.Brown, align = TextAlign.Center)
                    PxText("的", size = 15.sp, color = Px.Brown, align = TextAlign.Center)
                    PxText("资", size = 15.sp, color = Px.Brown, align = TextAlign.Center)
                    PxText("产", size = 15.sp, color = Px.Brown, align = TextAlign.Center)
                }
                Spacer(Modifier.width(6.dp))
                // 右区两栏：类别名（左）｜ 金额（右，固定宽右对齐）
                Column(modifier = Modifier.weight(1f)) {
                    if (orderedCats.isEmpty()) {
                        PxText(
                            "还没有资产记录，点左侧「我的资产」添加",
                            size = 11.sp, color = Px.GrayText,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    } else {
                        orderedCats.forEach { (cat, amt) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                PxText(
                                    cat.ifEmpty { "未分类" }, size = 14.sp, color = Px.Brown,
                                    modifier = Modifier.weight(1f),
                                )
                                PxText(
                                    Fmt.yen(amt), size = 14.sp, color = Px.WoodDark,
                                    align = TextAlign.End, modifier = Modifier.width(104.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ProfileDivider()
                    Spacer(Modifier.height(6.dp))
                    // 总资产价值（类别名栏 + 金额大字）
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PxText("总资产价值", size = 11.sp, color = Px.GrayText, modifier = Modifier.weight(1f))
                        PxText(
                            Fmt.yen(total), size = 14.sp, color = Px.Brown,
                            align = TextAlign.End, modifier = Modifier.width(104.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

/** 角色面板内分隔线（奶油深档） */
@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .background(Px.CreamDark),
    )
}

/* ================================================================
 * 账户资料编辑弹窗：角色形象 / 账户名 / 生日 / 备注
 * ================================================================ */

@Composable
fun ProfileDialog(
    store: Store,
    account: Account,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var avatar by remember { mutableIntStateOf(account.avatar) }
    var name by remember { mutableStateOf(account.name) }
    var birthday by remember { mutableStateOf(account.birthday) }
    var note by remember { mutableStateOf(account.note) }

    PixelDialog(
        title = "编辑资料",
        onDismiss = onDismiss,
        contentScrollable = true,
        footer = {
            PixelButton("取消", onDismiss, bg = Px.Wood, height = 40.dp, modifier = Modifier.width(110.dp))
            PixelButton(
                "保存",
                {
                    val ok = store.updateAccountProfile(account.id, name, birthday, note, avatar)
                    if (!ok) store.toast("账户名无效或已存在") else onSaved()
                },
                bg = Px.Clay, height = 40.dp, modifier = Modifier.width(110.dp),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PxText("角色形象", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarOption(index = 0, selected = avatar == 0, onClick = { avatar = 0 })
                AvatarOption(index = 1, selected = avatar == 1, onClick = { avatar = 1 })
            }
            Spacer(Modifier.height(10.dp))
            PxText("账户名", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = name,
                onValueChange = { name = Fmt.clip(it, MAX_ACCOUNT_NAME_LEN) },
                placeholder = "账户名（≤30 字，改名同步文件夹）",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("生日", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = birthday,
                onValueChange = { birthday = Fmt.clip(it, 20) },
                placeholder = "如：1998-06-15",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PxText("备注", size = 12.sp, color = Px.GrayText)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = note,
                onValueChange = { note = Fmt.clip(it, MAX_ACCOUNT_NOTE_LEN) },
                placeholder = "写点什么…（≤60 字）",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 角色头像选项块（选中高亮描边） */
@Composable
private fun AvatarOption(index: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(if (selected) Px.Grass.copy(alpha = 0.25f) else Px.CreamBg)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = if (selected) 3.dp.toPx() else 2.dp.toPx()
                drawRect(
                    if (selected) Px.Grass else Px.Brown,
                    style = Stroke(width = stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(if (index == 1) "avatarWoman" else "avatarMan", size = 56.dp)
    }
}
