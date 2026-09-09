package com.miaoyu03.pixelbook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.ui.LedgerFonts
import com.miaoyu03.pixelbook.ui.LocalLedgerFont
import com.miaoyu03.pixelbook.ui.Px
import com.miaoyu03.pixelbook.ui.creamTexture
import com.miaoyu03.pixelbook.ui.screens.AccountDepositsScreen
import com.miaoyu03.pixelbook.ui.screens.AssetsScreen
import com.miaoyu03.pixelbook.ui.screens.DetailScreen
import com.miaoyu03.pixelbook.ui.screens.EntryScreen
import com.miaoyu03.pixelbook.ui.screens.HomeScreen
import com.miaoyu03.pixelbook.ui.screens.MonthScreen
import com.miaoyu03.pixelbook.ui.screens.StartScreen
import com.miaoyu03.pixelbook.ui.screens.YearScreen
import java.time.LocalDate

/** 页面路由：单 Activity + 状态栈 */
sealed class Screen {
    data object Start : Screen()                // 启动首页：四季记账（选账号）
    data object Home : Screen()                 // 目录页：资产/钱包入口 + 账本列表
    data class AccountDeposits(val accountId: String) : Screen()   // 账户公用资产
    data class Assets(val accountId: String) : Screen()            // 资产账户信息维护
    data class Detail(val ledgerId: String) : Screen()
    data class Entry(val ledgerId: String, val date: LocalDate) : Screen()
    data class Month(val ledgerId: String, val ym: String) : Screen()
    data class Year(val ledgerId: String, val year: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 浅色像素背景 → 深色状态栏图标
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PixelBookApp()
        }
    }
}

@Composable
fun PixelBookApp() {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    // FIXME: seed 后首帧数据同步渲染（无闪烁）
    val stack = remember { mutableStateListOf<Screen>(Screen.Start) }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .creamTexture()
            // 内容区避让系统栏：状态栏（透明区模拟器会拦截点击）、手势导航区
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val top = stack.last()
        when (top) {
            is Screen.Start -> StartScreen(
                store = store,
                onStart = { id ->
                    store.setCurrentAccountId(id)
                    stack.add(Screen.Home)
                },
            )
            is Screen.Home -> HomeScreen(
                store = store,
                onBack = { stack.removeAt(stack.lastIndex) },
                onOpenLedger = { stack.add(Screen.Detail(it)) },
                onOpenAccountDeposits = { stack.add(Screen.AccountDeposits(it)) },
                onOpenAssets = { stack.add(Screen.Assets(it)) },
            )
            is Screen.AccountDeposits -> AccountDepositsScreen(
                store = store,
                accountId = top.accountId,
                onBack = { stack.removeAt(stack.lastIndex) },
            )
            is Screen.Assets -> AssetsScreen(
                store = store,
                accountId = top.accountId,
                onBack = { stack.removeAt(stack.lastIndex) },
            )
            is Screen.Detail -> WithLedgerFont(store, top.ledgerId) {
                DetailScreen(
                    store = store,
                    ledgerId = top.ledgerId,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    // +：新增所选日期的那一天（进入记一笔页，默认=左侧选中的日期）
                    onAdd = { d -> stack.add(Screen.Entry(top.ledgerId, d)) },
                    // 点击月份/年份标题 → 直接跳对应总结页
                    onMonth = { ym -> stack.add(Screen.Month(top.ledgerId, ym)) },
                    onYear = { y -> stack.add(Screen.Year(top.ledgerId, y)) },
                )
            }
            is Screen.Entry -> WithLedgerFont(store, top.ledgerId) {
                EntryScreen(
                    store = store,
                    ledgerId = top.ledgerId,
                    date = top.date,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onSaved = { stack.removeAt(stack.lastIndex) },
                )
            }
            is Screen.Month -> WithLedgerFont(store, top.ledgerId) {
                MonthScreen(
                    store = store,
                    ledgerId = top.ledgerId,
                    ym = top.ym,
                    onBack = { stack.removeAt(stack.lastIndex) },
                )
            }
            is Screen.Year -> WithLedgerFont(store, top.ledgerId) {
                YearScreen(
                    store = store,
                    ledgerId = top.ledgerId,
                    initialYear = top.year,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onMonth = { m -> stack.add(Screen.Month(top.ledgerId, m)) },
                )
            }
        }
    }
}

/** 账本页统一提供该账本所选字体（LocalLedgerFont），页面内所有 PxText 自动跟随 */
@Composable
private fun WithLedgerFont(
    store: Store,
    ledgerId: String,
    content: @Composable () -> Unit,
) {
    val fontName = remember(ledgerId) { store.ledger(ledgerId)?.font ?: LedgerFonts.PIXEL }
    CompositionLocalProvider(LocalLedgerFont provides LedgerFonts.family(fontName)) {
        content()
    }
}