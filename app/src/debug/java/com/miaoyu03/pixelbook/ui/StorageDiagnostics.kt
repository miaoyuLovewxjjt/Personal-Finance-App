package com.miaoyu03.pixelbook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaoyu03.pixelbook.data.StorageSelfCheck
import com.miaoyu03.pixelbook.data.Store

/**
 * 设置页「存储自检」入口（debug 版实现）。
 * release 版由 app/src/release 下的同名函数替代为空实现。
 *
 * 自检在后台线程跑，结果用对话框展示（toast 显示不下）。
 */
@Composable
fun StorageDiagnosticsSection(store: Store) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(16.dp))
    PxText("开发者选项（debug）", size = 14.sp)
    Spacer(Modifier.height(6.dp))
    PixelButton(
        if (running) "自检中…" else "存储自检",
        onClick = {
            running = true
            StorageSelfCheck.runInBackground(ctx, store) { r ->
                running = false
                report = r.report()
            }
        },
        bg = Px.Sky, height = 36.dp, enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    )

    report?.let { text ->
        PixelDialog(
            title = "存储自检结果",
            onDismiss = { report = null },
            contentScrollable = true,
            footer = {
                PixelButton(
                    "关闭", { report = null }, bg = Px.Clay, height = 40.dp,
                    modifier = Modifier.width(120.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = Px.Brown)
            }
        }
    }
}
