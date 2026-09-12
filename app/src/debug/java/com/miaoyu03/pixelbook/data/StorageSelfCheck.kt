package com.miaoyu03.pixelbook.data

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 存储自检（仅 debug 版）：设置页「存储自检」入口调用。
 *
 * 目的：在不破坏真实数据的前提下，验证当前存储后端的读写/改名/删除/原子写能力，
 * 并顺带体检现有账户目录是否结构完整。全部动作在根目录下的
 * `.selfcheck/` 临时目录里进行，结束时清理。
 *
 * 只读取现有数据（列出账户/账本数量），不修改任何业务文件。
 */
object StorageSelfCheck {

    private const val SANDBOX = ".selfcheck"

    data class Result(
        val lines: List<String>,
        val passed: Boolean,
    ) {
        fun report(): String = lines.joinToString("\n")
    }

    fun run(context: Context, store: Store): Result {
        val sb = StringBuilder()
        val failures = AtomicInteger()
        var passed = true

        fun ok(name: String, detail: String = "") {
            sb.append("✅ ").append(name)
            if (detail.isNotEmpty()) sb.append("：").append(detail)
            sb.append('\n')
        }

        fun fail(name: String, e: Throwable) {
            passed = false
            failures.incrementAndGet()
            sb.append("❌ ").append(name).append("：")
                .append(e.message ?: e.javaClass.simpleName).append('\n')
        }

        /** 取出真实后端（自检需要底层 IO 能力，超出 Store 的公开 API） */
        val io = store.debugIo()

        try {
            // 准备沙盒
            runCatching { io.deleteDir(SANDBOX) }
            io.createDir(SANDBOX)

            // 1) 写 → 读回一致（含中文与 JSON 特殊字符）
            val payload = """{"中文":"值 with \"quotes\"","n":123,"arr":[1,2,3]}"""
            try {
                io.writeNamedRaw("$SANDBOX/a.json", payload)
                val back = io.readNamedRaw("$SANDBOX/a.json")
                check(back == payload) { "读回内容不一致（写入 ${payload.length} 字符，读回 ${back?.length ?: 0}）" }
                ok("写入/读取", "${payload.length} 字符往返一致")
            } catch (e: Exception) { fail("写入/读取", e) }

            // 2) 目录枚举
            try {
                val entries = io.listDirRaw(SANDBOX)
                check("a.json" in entries) { "列目录缺少 a.json：$entries" }
                ok("目录枚举", entries.joinToString())
            } catch (e: Exception) { fail("目录枚举", e) }

            // 3) 重命名
            try {
                io.writeNamedRaw("$SANDBOX/b.json", "{}")
                io.renameRaw("$SANDBOX/b.json", "$SANDBOX/b2.json")
                val old = io.readNamedRaw("$SANDBOX/b.json")
                val new = io.readNamedRaw("$SANDBOX/b2.json")
                check(old == null && new == "{}") { "改名后 旧=${old ?: "null"} 新=${new ?: "null"}" }
                ok("文件改名")
            } catch (e: Exception) { fail("文件改名", e) }

            // 4) 递归列举全路径
            try {
                val all = io.listAllRaw()
                check(all.any { it.endsWith("$SANDBOX/a.json") }) { "listAllRaw 未包含沙盒文件" }
                ok("全量列举", "${all.size} 个文件")
            } catch (e: Exception) { fail("全量列举", e) }

            // 5) 目录大小统计
            try {
                val size = io.dirSizeBytes(SANDBOX)
                check(size > 0) { "目录大小统计为 $size" }
                ok("目录大小", "$size 字节")
            } catch (e: Exception) { fail("目录大小", e) }

            // 6) 并发写入（验证缓存/后端在多线程下不崩，模拟切换目录/PDF 导出与主线程并发）
            try {
                val threads = 8
                val perThread = 5
                val latch = CountDownLatch(threads)
                val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
                for (t in 0 until threads) {
                    Thread {
                        try {
                            for (i in 0 until perThread) {
                                val p = "$SANDBOX/concurrent_${t}_${i}.json"
                                io.writeNamedRaw(p, """{"t":$t,"i":$i}""")
                                io.readNamedRaw(p)
                            }
                        } catch (e: Throwable) {
                            errors.add(e)
                        } finally {
                            latch.countDown()
                        }
                    }.start()
                }
                check(latch.await(20, TimeUnit.SECONDS)) { "并发写入超时" }
                check(errors.isEmpty()) { "并发写入异常：${errors.firstOrNull()?.message}" }
                val expected = threads * perThread
                val got = io.listDirRaw(SANDBOX).count { it.startsWith("concurrent_") }
                check(got == expected) { "并发写入文件数 $got != $expected" }
                ok("并发读写", "$threads 线程 × $perThread 文件")
            } catch (e: Exception) { fail("并发读写", e) }

            // 7) 原子写残留检查：临时文件不应留在目录里
            try {
                val leftovers = io.listDirRaw(SANDBOX).filter { it.endsWith(".tmp") }
                check(leftovers.isEmpty()) { "存在未清理的临时文件：$leftovers" }
                ok("原子写无残留")
            } catch (e: Exception) { fail("原子写无残留", e) }

            // 8) 删除
            try {
                io.removeRaw("$SANDBOX/a.json")
                check(io.readNamedRaw("$SANDBOX/a.json") == null) { "删除后仍能读到 a.json" }
                ok("文件删除")
            } catch (e: Exception) { fail("文件删除", e) }

            // 9) 目录递归删除
            try {
                io.deleteDir(SANDBOX)
                check(io.listDirRaw(SANDBOX).isEmpty()) { "删除目录后仍能列出内容" }
                ok("目录删除（递归）")
            } catch (e: Exception) { fail("目录删除（递归）", e) }

        } finally {
            runCatching { io.deleteDir(SANDBOX) }
        }

        // 10) 现有数据体检（只读）
        try {
            val accs = store.accounts()
            val ledgers = store.ledgers()
            val badLedger = mutableListOf<String>()
            for (l in ledgers) {
                if (store.ledgerFileExists(l)) continue
                badLedger.add(l.name)
            }
            sb.append("ℹ 当前存储：").append(store.storageDirDescription()).append('\n')
            sb.append("ℹ 账户 ").append(accs.size).append(" 个，账本 ").append(ledgers.size).append(" 本\n")
            if (badLedger.isEmpty()) {
                ok("账本文件齐全", "${ledgers.size} 本均有对应文件")
            } else {
                passed = false
                sb.append("❌ 以下账本索引存在但文件缺失：")
                    .append(badLedger.joinToString("、")).append('\n')
            }
            store.lastWriteError()?.let {
                sb.append("⚠ 最近写入错误：").append(it).append('\n')
            }
            store.lastDataError()?.let {
                passed = false
                sb.append("⚠ 数据异常：").append(it).append('\n')
            }
        } catch (e: Exception) {
            fail("现有数据体检", e)
        }

        sb.append('\n').append(if (passed) "结果：全部通过 ✅" else "结果：发现 ${failures.get()} 项问题 ❌")
        return Result(sb.toString().trimEnd('\n').split('\n'), passed)
    }

    /** 便捷入口：把自检报告交给调用方（toast 太短，设置页用对话框展示） */
    fun runInBackground(context: Context, store: Store, onDone: (Result) -> Unit) {
        Thread {
            val r = runCatching { run(context, store) }
                .getOrElse { Result(listOf("❌ 自检异常：${it.message ?: it.javaClass.simpleName}"), false) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(r) }
        }.start()
    }
}
