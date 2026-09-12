package com.miaoyu03.pixelbook.ui

import androidx.compose.runtime.Composable
import com.miaoyu03.pixelbook.data.Store

/**
 * release 版占位：正式包不包含任何诊断入口（与 StorageSelfCheck 一起被源码集隔离）。
 * debug 版见 app/src/debug/java/.../StorageDiagnostics.kt。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun StorageDiagnosticsSection(store: Store) {
    // release 版无诊断入口，故意为空
}
