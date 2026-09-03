package com.miaoyu03.pixelbook.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.miaoyu03.pixelbook.R
import com.miaoyu03.pixelbook.data.AppMeta
import com.miaoyu03.pixelbook.data.Deposit
import com.miaoyu03.pixelbook.data.DepositKind
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Ledger
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.data.Weather
import com.miaoyu03.pixelbook.ui.PixelIcons
import com.miaoyu03.pixelbook.ui.Px
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 像素风 PDF 导出：用 android.graphics.pdf.PdfDocument 手绘，
 * 完全复刻 App 视觉规范（米色棋盘纹理、深棕描边、阶梯硬阴影、Zpix 像素字体、马赛克图标）。
 *
 * 章节顺序：一、账本信息 → 二、存款明细 → 三、收支流水 → 四、总结（最后章节）。
 */
object PdfExporter {

    private const val PAGE_W = 595f          // A4 纵向 pt
    private const val PAGE_H = 842f
    private const val M = 40f                // 页边距
    private const val W = PAGE_W - M * 2     // 内容宽度 515
    private const val TOP = 34f
    private const val BOTTOM = PAGE_H - 58f

    /** 导出入口：返回总页数 */
    fun export(context: Context, store: Store, ledger: Ledger, out: OutputStream): Int {
        val doc = PdfDocument()
        val typeface = ResourcesCompat.getFont(context, R.font.zpix) ?: Typeface.DEFAULT
        val eng = Engine(doc, typeface)
        val txs = store.txList(ledger.id)
        val deps = store.depList(ledger.id)
        try {
            coverAndInfo(eng, ledger, txs, deps)
            deposits(eng, ledger, deps)
            txsSection(eng, ledger, txs, store)
            summary(eng, ledger, txs, deps)
            eng.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return eng.pageNo
    }

    /* ================= 一、封面 / 账本信息 ================= */

    private fun coverAndInfo(eng: Engine, ledger: Ledger, txs: List<Tx>, deps: List<Deposit>) {
        val c = eng.c!!
        val cx = PAGE_W / 2f

        // 居中大账本封面图标 + 标题
        icon(c, PixelIcons.ledgerIcon(ledger.coverColor).asAndroidBitmap(), cx, 120f, 84f)
        // 长账本名自适应缩小，保证不超出一行
        val titleSize = when {
            ledger.name.length > 22 -> 14f
            ledger.name.length > 14 -> 17f
            else -> 24f
        }
        eng.txt.draw(c, ledger.name, cx, 216f, titleSize, Px.Brown.toArgb(), Paint.Align.CENTER)
        eng.txt.draw(c, "像素记账 · 账本报告", cx, 248f, 12f, Px.Wood.toArgb(), Paint.Align.CENTER)

        // 账本信息卡
        val infoY = 292f
        val inCount = txs.count { it.dir == TxDir.IN }
        val outCount = txs.count { it.dir == TxDir.OUT }
        panel(c, M, infoY, W, 170f, Px.Cream.toArgb())
        infoRow(c, eng, "账本名称", ledger.name, infoY + 26f)
        infoRow(c, eng, "创建时间", safeDate(ledger.createdAt), infoY + 52f)
        infoRow(c, eng, "导出时间", Fmt.date(LocalDate.now()), infoY + 78f)
        infoRow(c, eng, "收支记录", "收入 $inCount 笔 · 支出 $outCount 笔", infoY + 104f)
        infoRow(c, eng, "存款记录", "${deps.size} 笔", infoY + 130f)
        infoRow(c, eng, "总存款", Fmt.yen(deps.sumOf { it.value }), infoY + 156f, amount = true)

        // 章节目录
        val tocY = 488f
        panel(c, M, tocY, W, 158f, Px.Cream.toArgb())
        eng.txt.draw(c, "目录", M + 14f, tocY + 20f, 14f, Px.Brown.toArgb())
        tocRow(c, eng, "ledger", "一、账本信息", tocY + 48f)
        tocRow(c, eng, "chest", "二、存款明细", tocY + 74f)
        tocRow(c, eng, "expense", "三、收支流水", tocY + 100f)
        tocRow(c, eng, "statChart", "四、总结（数据统计，最后章节）", tocY + 126f)
    }

    private fun infoRow(c: Canvas, eng: Engine, label: String, value: String, y: Float, amount: Boolean = false) {
        eng.txt.draw(c, label, M + 14f, y, 11f, Px.GrayText.toArgb())
        eng.txt.draw(
            c, value, M + W - 14f, y, if (amount) 13f else 11f,
            if (amount) Px.WoodDark.toArgb() else Px.Brown.toArgb(),
            Paint.Align.RIGHT,
        )
    }

    private fun tocRow(c: Canvas, eng: Engine, iconName: String, text: String, y: Float) {
        icon(c, PixelIcons.get(iconName).asAndroidBitmap(), M + 14f, y, 13f)
        eng.txt.draw(c, text, M + 36f, y, 12f, Px.Brown.toArgb())
    }

    /* ================= 二、存款明细 ================= */

    private fun deposits(eng: Engine, ledger: Ledger, deps: List<Deposit>) {
        eng.ensure(54f)
        chapterHeader(eng, "二、存款明细", "共 ${deps.size} 笔")
        val c = eng.c!!

        // 表头
        tableHead(
            c, eng,
            listOf(60f, 96f, 110f, 160f, 89f),
            listOf("类型", "入库时间", "名称", "备注", "价值"),
        )

        // 按 类型 → 时间 排序（金钱类在前，组内时间从新到旧）
        val sorted = deps.sortedWith(
            compareBy<Deposit> { if (it.kind == DepositKind.MONEY) 0 else 1 }
                .thenByDescending { it.date }
        )
        if (sorted.isEmpty()) {
            emptyNote(c, eng, "暂无存款记录")
        } else {
            for (d in sorted) {
                eng.ensure(26f)
                val y = eng.y
                fillRow(c, y, 26f, Px.Cream.toArgb())
                // 类型像素标签
                val tagBg = if (d.kind == DepositKind.MONEY) Px.Clay.toArgb() else Px.SkyDark.toArgb()
                tag(c, eng, M + 6f, y + 13f, d.kind.label, tagBg)
                eng.txt.draw(c, Fmt.dateYmd(d.date), M + 60f + 8f, y + 13f, 10.5f, Px.Brown.toArgb())
                eng.txt.draw(c, d.name.ifEmpty { "（未命名）" }, M + 60f + 96f + 8f, y + 13f, 10.5f, Px.Brown.toArgb())
                eng.txt.draw(c, d.note, M + 60f + 96f + 110f + 8f, y + 13f, 10f, Px.GrayText.toArgb())
                eng.txt.draw(c, Fmt.yen(d.value), M + W - 8f, y + 13f, 11f, Px.WoodDark.toArgb(), Paint.Align.RIGHT)
                eng.y = y + 26f + 3f
            }
        }
        // 合计行
        eng.ensure(30f)
        val y = eng.y
        fillRow(c, y, 28f, Px.Wood.toArgb())
        eng.txt.draw(c, "合计 · 总存款", M + 12f, y + 14f, 11f, Px.Cream.toArgb())
        eng.txt.draw(
            c, Fmt.yen(deps.sumOf { it.value }), M + W - 12f, y + 14f, 13f,
            Px.Cream.toArgb(), Paint.Align.RIGHT,
        )
        eng.y = y + 28f + 10f
    }

    /* ================= 三、收支流水 ================= */

    private fun txsSection(eng: Engine, ledger: Ledger, txs: List<Tx>, store: Store) {
        eng.ensure(54f)
        chapterHeader(eng, "三、收支流水", "共 ${txs.size} 笔")
        val c = eng.c!!

        tableHead(
            c, eng,
            listOf(78f, 34f, 48f, 120f, 150f, 85f),
            listOf("日期", "时间", "收支", "分类 · 名称", "备注", "金额"),
        )

        // 按月分组（新 → 旧），月内按 日期 → 时间（新 → 旧）
        val byMonth = txs.groupBy { Fmt.ymKey(it.date) }.toSortedMap(compareByDescending { it })
        if (byMonth.isEmpty()) {
            emptyNote(c, eng, "暂无收支记录")
            return
        }
        for ((ym, list) in byMonth) {
            val monthObj = YearMonth.parse(ym)
            val inSum = list.filter { it.dir == TxDir.IN }.sumOf { it.amount }
            val outSum = list.filter { it.dir == TxDir.OUT }.sumOf { it.amount }
            val bal = inSum - outSum

            // 月份小计行
            eng.ensure(28f)
            var y = eng.y
            fillRow(c, y, 26f, 0x66E8B84B.toInt())   // 暖黄半透明
            eng.txt.draw(
                c, "${monthObj.year}年${monthObj.monthValue}月", M + 10f, y + 13f, 12f,
                Px.Brown.toArgb(),
            )
            eng.txt.draw(
                c, "收入 ${Fmt.money(inSum)}    支出 ${Fmt.money(outSum)}    结余 ${Fmt.money(bal)}",
                M + W - 10f, y + 13f, 10.5f, Px.Brown.toArgb(), Paint.Align.RIGHT,
            )
            eng.y = y + 26f + 4f

            val rows = list.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.time })
            for (t in rows) {
                val broke = eng.ensure(24f)
                y = eng.y
                if (broke) {
                    // 跨页续行小标题
                    eng.txt.draw(
                        c, "（续）${monthObj.year}年${monthObj.monthValue}月", M, y + 10f, 9f,
                        Px.GrayText.toArgb(),
                    )
                    eng.y = y + 16f
                    y = eng.y
                }
                fillRow(c, y, 24f, Px.Cream.toArgb())

                // 日期 + 天气小图标
                eng.txt.draw(c, Fmt.dayOfMonth(t.date), M + 6f, y + 12f, 10f, Px.Brown.toArgb())
                store.weather(ledger.id, t.date)?.let { w ->
                    icon(c, PixelIcons.get(w.iconName()).asAndroidBitmap(), M + 62f, y + 12f, 11f)
                }
                eng.txt.draw(c, t.time, M + 78f + 6f, y + 12f, 10f, Px.GrayText.toArgb())
                // 收支方向
                icon(
                    c, PixelIcons.get(if (t.dir == TxDir.IN) "income" else "expense").asAndroidBitmap(),
                    M + 78f + 34f + 24f, y + 12f, 12f,
                )
                // 分类 · 名称
                eng.txt.draw(
                    c, t.category + if (t.name.isNotEmpty()) " · ${t.name}" else "",
                    M + 78f + 34f + 48f + 6f, y + 12f, 10.5f, Px.Brown.toArgb(),
                )
                eng.txt.draw(c, t.note, M + 78f + 34f + 48f + 120f + 6f, y + 12f, 10f, Px.GrayText.toArgb())
                // 金额
                eng.txt.draw(
                    c, (if (t.dir == TxDir.IN) "+" else "-") + Fmt.yen(t.amount),
                    M + W - 8f, y + 12f, 11f,
                    if (t.dir == TxDir.IN) Px.GrassDark.toArgb() else Px.WoodDark.toArgb(),
                    Paint.Align.RIGHT,
                )
                eng.y = y + 24f + 3f
            }
        }
        eng.y += 8f
    }

    private fun Weather.iconName(): String = when (this) {
        Weather.SUNNY -> "sun"; Weather.CLOUDY -> "cloud"; Weather.RAIN -> "rain"
        Weather.SNOW -> "snow"
    }

    /* ================= 四、总结（最后章节） ================= */

    private fun summary(eng: Engine, ledger: Ledger, txs: List<Tx>, deps: List<Deposit>) {
        eng.ensure(54f)
        chapterHeader(eng, "四、总结")
        val c = eng.c!!

        val totalIn = txs.filter { it.dir == TxDir.IN }.sumOf { it.amount }
        val totalOut = txs.filter { it.dir == TxDir.OUT }.sumOf { it.amount }
        val balance = totalIn - totalOut
        val totalDep = deps.sumOf { it.value }

        // 总览 2x2（总存款/总收入/总支出/结余）
        eng.ensure(94f)
        var y = eng.y
        panel(c, M, y, W, 90f, Px.Cream.toArgb())
        statCell(c, eng, "coinPile", "总存款", Fmt.yen(totalDep), Px.Wood.toArgb(), M + 4f, y + 5f)
        statCell(c, eng, "income", "总收入", Fmt.yen(totalIn), Px.GrassDark.toArgb(), M + W / 2f + 4f, y + 5f)
        statCell(c, eng, "expense", "总支出", Fmt.yen(totalOut), Px.WoodDark.toArgb(), M + 4f, y + 47f)
        statCell(
            c, eng, "coin", "结余",
            Fmt.yen(balance), if (balance >= 0) Px.GrassDark.toArgb() else Px.ClayDark.toArgb(),
            M + W / 2f + 4f, y + 47f,
        )
        eng.y = y + 90f + 10f

        // 最多日 / 月
        eng.ensure(94f)
        y = eng.y
        panel(c, M, y, W, 90f, Px.Cream.toArgb())
        val maxExpDay = maxDayOf(txs, TxDir.OUT)
        val maxIncDay = maxDayOf(txs, TxDir.IN)
        val maxExpMonth = maxMonthOf(txs, TxDir.OUT)
        val maxIncMonth = maxMonthOf(txs, TxDir.IN)
        statCell(c, eng, "calendar", "支出最多日", maxExpDay?.let { Fmt.dayOfMonth(it) } ?: "—", Px.Brown.toArgb(), M + 4f, y + 5f)
        statCell(c, eng, "calendarGold", "收入最多日", maxIncDay?.let { Fmt.dayOfMonth(it) } ?: "—", Px.Brown.toArgb(), M + W / 2f + 4f, y + 5f)
        statCell(c, eng, "calendar", "支出最多月", maxExpMonth?.let { ymMonth(it) } ?: "—", Px.Brown.toArgb(), M + 4f, y + 47f)
        statCell(c, eng, "calendarGold", "收入最多月", maxIncMonth?.let { ymMonth(it) } ?: "—", Px.Brown.toArgb(), M + W / 2f + 4f, y + 47f)
        eng.y = y + 90f + 10f

        // 每月收支表
        val monthTab = txs.groupBy { Fmt.ymKey(it.date) }.toSortedMap(compareByDescending { it })
        if (monthTab.isNotEmpty()) {
            eng.ensure(58f)
            y = eng.y
            panel(c, M, y, W, 42f + monthTab.size * 24f, Px.Cream.toArgb())
            eng.txt.draw(c, "每月收支", M + 14f, y + 16f, 13f, Px.Brown.toArgb())
            var ry = y + 40f
            tableRowLine(c, M, ry, W, Px.Brown.toArgb(), 1.5f)
            for ((ym, list) in monthTab) {
                val mi = list.filter { it.dir == TxDir.IN }.sumOf { it.amount }
                val mo = list.filter { it.dir == TxDir.OUT }.sumOf { it.amount }
                ry += 24f
                val moObj = YearMonth.parse(ym)
                eng.txt.draw(c, "${moObj.year}年${moObj.monthValue}月", M + 14f, ry, 11f, Px.Brown.toArgb())
                eng.txt.draw(c, Fmt.yen(mi), M + W / 2f - 6f, ry, 11f, Px.GrassDark.toArgb(), Paint.Align.RIGHT)
                eng.txt.draw(c, Fmt.yen(mo), M + W * 0.74f - 6f, ry, 11f, Px.WoodDark.toArgb(), Paint.Align.RIGHT)
                eng.txt.draw(c, Fmt.yen(mi - mo), M + W - 14f, ry, 11f, Px.Brown.toArgb(), Paint.Align.RIGHT)
            }
            eng.y = y + 42f + monthTab.size * 24f + 8f
        }

        // 支出分类占比
        catRatioPanel(eng, "支出分类占比", iconList = txs.filter { it.dir == TxDir.OUT })
        // 收入分类占比
        catRatioPanel(eng, "收入分类占比", iconList = txs.filter { it.dir == TxDir.IN })

        // 底部说明
        eng.ensure(30f)
        eng.y += 6f
        eng.txt.draw(
            c, "数据每日自动同步生成，不可编辑", PAGE_W / 2f, eng.y + 10f, 10f,
            Px.GrayText.toArgb(), Paint.Align.CENTER,
        )
        eng.y += 26f
    }

    /** 分类占比面板：左侧环形图 + 右侧图例（图标/名称/占比/金额） */
    private fun catRatioPanel(eng: Engine, title: String, iconList: List<Tx>) {
        if (iconList.isEmpty()) return
        val cats = iconList.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
        val total = cats.sumOf { it.second }.coerceAtLeast(1)

        val rowsH = cats.size * 20f + 30f
        val panelH = 134f.coerceAtLeast(rowsH + 34f)
        eng.ensure(panelH + 10f)
        val y = eng.y
        val c = eng.c!!
        panel(c, M, y, W, panelH, Px.Cream.toArgb())
        eng.txt.draw(c, title, M + 14f, y + 16f, 13f, Px.Brown.toArgb())

        // 左侧环形图
        val donutCx = M + 76f
        val donutCy = y + panelH / 2f + 4f
        donut(c, donutCx, donutCy, 46f, 30f, cats, total.toLong())

        // 右侧图例
        var ly = y + 42f
        for ((name, v) in cats) {
            if (ly > eng.y + panelH - 20f) break
            val percent = (v.toFloat() / total * 100f).roundToInt()
            icon(c, PixelIcons.get(PixelIcons.iconOfCategory(name)).asAndroidBitmap(), M + 138f, ly, 12f)
            eng.txt.draw(c, name, M + 156f, ly, 11f, Px.Brown.toArgb())
            eng.txt.draw(c, "$percent%", M + 232f, ly, 10f, Px.GrayText.toArgb())
            eng.txt.draw(c, Fmt.yen(v), M + W - 14f, ly, 11f, Px.WoodDark.toArgb(), Paint.Align.RIGHT)
            ly += 20f
        }
        eng.y = y + panelH + 10f
    }

    /** 像素环形图（对应 App PixelDonut：分割线 + 挖空内圆） */
    private fun donut(
        c: Canvas,
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
        segs: List<Pair<String, Long>>,
        total: Long,
    ) {
        val fill = Paint().apply { isAntiAlias = false }
        val rect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        // 底色环
        fill.color = Px.CreamBg.toArgb()
        c.drawArc(rect, 0f, 360f, true, fill)
        var a0 = -90f
        for ((name, v) in segs) {
            if (v <= 0) continue
            val sweep = v.toFloat() / total * 360f
            val gap = if (segs.size > 1) 1.2f else 0f
            fill.color = PixelIcons.colorOfCategory(name).toArgb()
            if (sweep - gap > 0.2f) {
                c.drawArc(rect, a0 + gap / 2f, sweep - gap, true, fill)
            }
            a0 += sweep
        }
        // 深棕分割线
        val line = Paint().apply { isAntiAlias = false; color = Px.BrownDark.toArgb(); strokeWidth = 1.5f }
        var a = -90f
        for ((_, v) in segs) {
            if (v <= 0) continue
            val rad = Math.toRadians(a.toDouble())
            c.drawLine(
                cx + inner * cos(rad).toFloat(), cy + inner * sin(rad).toFloat(),
                cx + outer * cos(rad).toFloat(), cy + outer * sin(rad).toFloat(), line,
            )
            a += v.toFloat() / total * 360f
        }
        // 挖空内圆（奶油卡片底）
        fill.color = Px.Cream.toArgb()
        c.drawCircle(cx, cy, inner, fill)
    }

    private fun statCell(
        c: Canvas,
        eng: Engine,
        iconName: String,
        label: String,
        value: String,
        valueColor: Int,
        x: Float,
        top: Float,
    ) {
        val w = W / 2f - 8f
        icon(c, PixelIcons.get(iconName).asAndroidBitmap(), x + 10f, top + 12f, 12f)
        eng.txt.draw(c, label, x + 28f, top + 12f, 10.5f, Px.GrayText.toArgb())
        eng.txt.draw(c, value, x + w / 2f, top + 32f, 13f, valueColor, Paint.Align.CENTER)
    }

    /* ================= 通用绘制 ================= */

    private fun chapterHeader(eng: Engine, title: String, sub: String = "") {
        val c = eng.c!!
        val y = eng.y
        // 木棕两段页头条（对应 App PixelHeader）
        fillRect(c, M, y, W, 40f, Px.WoodDark.toArgb())
        fillRect(c, M, y + 20f, W, 20f, Px.Wood.toArgb())
        val line = eng.line
        line.color = Px.BrownDark.toArgb()
        line.strokeWidth = 2f
        c.drawLine(M, y + 40f, M + W, y + 40f, line)
        eng.txt.draw(c, title, M + 12f, y + 20f, 16f, Px.Cream.toArgb())
        if (sub.isNotEmpty()) {
            eng.txt.draw(c, sub, M + W - 12f, y + 20f, 11f, Px.Cream.toArgb(), Paint.Align.RIGHT)
        }
        eng.y = y + 40f + 12f
    }

    /** 表头行（米色底 + 底部深棕细线） */
    private fun tableHead(c: Canvas, eng: Engine, widths: List<Float>, labels: List<String>) {
        val y = eng.y
        fillRect(c, M, y, W, 24f, Px.CreamDark.toArgb())
        var x = M
        labels.forEachIndexed { i, label ->
            val right = i == labels.lastIndex
            val align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
            val px = if (right) x + widths[i] - 8f else x + 8f
            eng.txt.draw(c, label, px, y + 12f, 10f, Px.Wood.toArgb(), align)
            x += widths[i]
        }
        val line = eng.line
        line.color = Px.Brown.toArgb()
        line.strokeWidth = 1.5f
        c.drawLine(M, y + 24f, M + W, y + 24f, line)
        eng.y = y + 26f
    }

    /** 普通行底色 */
    private fun fillRow(c: Canvas, y: Float, h: Float, color: Int) {
        fillRect(c, M, y, W, h, color)
    }

    private fun emptyNote(c: Canvas, eng: Engine, text: String) {
        eng.ensure(52f)
        val y = eng.y
        fillRect(c, M, y, W, 44f, Px.Cream.toArgb())
        eng.txt.draw(c, text, PAGE_W / 2f, y + 22f, 11f, Px.GrayText.toArgb(), Paint.Align.CENTER)
        eng.y = y + 44f + 8f
    }

    /** 像素标签块（对应 PixelTag） */
    private fun tag(c: Canvas, eng: Engine, cx: Float, cy: Float, text: String, bg: Int) {
        val size = 9.5f
        val w = eng.txt.width(text, size) + 10f
        val h = 16f
        val x = cx - w / 2f
        val y = cy - h / 2f
        fillRect(c, x, y, w, h, bg)
        eng.txt.draw(c, text, cx, cy, size, Px.Cream.toArgb(), Paint.Align.CENTER)
    }

    private fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float, bg: Int, depth: Float = 3f) {
        // 阶梯硬阴影（对应 PixelPanel）
        fillRect(c, x, y, w, h, Px.BrownDark.toArgb())
        fillRect(c, x, y + depth, w, h - depth, Px.Brown.toArgb())
        fillRect(c, x + depth, y + depth, w - depth, h - depth, Px.WoodDark.toArgb())
        fillRect(c, x + depth * 2, y + depth * 2, w - depth * 2, h - depth * 2, bg)
        // 深棕描边
        val stroker = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 2f; color = Px.Brown.toArgb() }
        val sw = 1f
        c.drawRect(x + depth * 2 + sw, y + depth * 2 + sw, x + w - depth * 2 - sw, y + h - depth * 2 - sw, stroker)
    }

    private fun tableRowLine(c: Canvas, x: Float, y: Float, w: Float, color: Int, sw: Float) {
        val line = Paint().apply { isAntiAlias = false; this.color = color; strokeWidth = sw }
        c.drawLine(x, y, x + w, y, line)
    }

    private fun fillRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val p = Paint().apply { isAntiAlias = false; this.color = color }
        c.drawRect(x, y, x + w, y + h, p)
    }

    /** 像素图标（最近邻缩放，保持马赛克） */
    private fun icon(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, size: Float) {
        val half = size / 2f
        c.drawBitmap(bmp, null, RectF(cx - half, cy - half, cx + half, cy + half), Paint())
    }

    private fun safeDate(s: String): String = runCatching {
        Fmt.date(LocalDate.parse(s))
    }.getOrDefault(s)

    private fun maxDayOf(txs: List<Tx>, dir: TxDir): LocalDate? =
        txs.filter { it.dir == dir }
            .groupBy { it.date }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }?.key

    private fun maxMonthOf(txs: List<Tx>, dir: TxDir): String? =
        txs.filter { it.dir == dir }
            .groupBy { Fmt.ymKey(it.date) }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }?.key

    private fun ymMonth(ym: String): String {
        val m = YearMonth.parse(ym)
        return "${m.monthValue}月（${m.year}年）"
    }

/* ================= 页面引擎 ================= */

    /**
     * 整页先在软件位图上绘制（画布 = 纯 Skia，避免 hwui 对 PdfDocument 画布
     * 文本/位图绘制的原生崩溃：DrawTextFunctor / drawBitmap 空指针），
     * 页完成时把整页位图一次 drawBitmap 进 PDF 页。
     */
    private class Engine(private val doc: PdfDocument, typeface: Typeface) {

        val txt = Txt(typeface)
        val line = Paint().apply { isAntiAlias = false; strokeWidth = 1f }
        var pageNo = 0; private set
        var y = TOP
        private var page: PdfDocument.Page? = null
        private var pageBitmap: Bitmap? = null
        /** 页面软件画布（所有绘制都在这上面） */
        var c: Canvas? = null
            private set
        private val pagePaint = Paint()
        private val texture: Paint by lazy {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint().apply { isAntiAlias = false }
            // 与 App 米色棋盘纹理同款（深一档砖在右上/左下）
            p.color = 0xFFF2E6C8.toInt()
            cv.drawRect(0f, 0f, 8f, 8f, p)
            p.color = 0xFFEBDBB6.toInt()
            cv.drawRect(4f, 0f, 8f, 4f, p)
            cv.drawRect(0f, 4f, 4f, 8f, p)
            Paint().apply {
                isAntiAlias = false
                shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }

        init {
            newPage()
        }

        fun newPage() {
            finishPage()
            pageNo++
            val p = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create()
            )
            page = p
            val bmp = Bitmap.createBitmap(PAGE_W.toInt(), PAGE_H.toInt(), Bitmap.Config.ARGB_8888)
            pageBitmap = bmp
            val cv = Canvas(bmp)
            cv.drawRect(0f, 0f, PAGE_W, PAGE_H, texture)
            c = cv
            y = TOP
        }

        fun finishPage() {
            val cv = c ?: return
            // 页脚：分隔线 + 页码（先画在软件画布上）
            val line = this.line
            line.color = Px.GrayText.toArgb()
            line.strokeWidth = 1f
            cv.drawLine(M, PAGE_H - 44f, PAGE_W - M, PAGE_H - 44f, line)
            // 页脚左侧：项目 git 地址（右侧页码）
            txt.draw(cv, AppMeta.GIT_URL, M, PAGE_H - 32f, 10f, Px.GrayText.toArgb())
            txt.draw(cv, "第 $pageNo 页", PAGE_W - M, PAGE_H - 32f, 10f, Px.GrayText.toArgb(), Paint.Align.RIGHT)
            // 整页位图 → PDF 画布
            val bmp = pageBitmap
            if (bmp != null) {
                page?.canvas?.drawBitmap(bmp, 0f, 0f, pagePaint)
            }
            doc.finishPage(page)
            pageBitmap?.recycle()
            pageBitmap = null
            c = null
            page = null
        }

        fun finish() = finishPage()

        /** 预留垂直空间，不足则翻页；返回是否发生了翻页 */
        fun ensure(h: Float): Boolean {
            if (y + h > BOTTOM) {
                newPage()
                return true
            }
            return false
        }
    }

    /**
     * 文本绘制：直接画在软件页面上（纯 Skia，安全）。
     * 早期版本先渲染文本位图再贴，同样能避开 hwui 文本路径。
     */
    private class Txt(typeface: Typeface) {
        private val p = Paint().apply {
            this.typeface = typeface
            isAntiAlias = true
        }

        fun width(text: String, size: Float): Float {
            p.textSize = size
            return p.measureText(text)
        }

        fun draw(
            c: Canvas,
            text: String,
            x: Float,
            centerY: Float,
            size: Float,
            color: Int,
            align: Paint.Align = Paint.Align.LEFT,
        ) {
            p.textSize = size
            p.color = color
            p.textAlign = align
            c.drawText(text, x, baseline(centerY, size), p)
        }

        private fun baseline(centerY: Float, size: Float): Float {
            p.textSize = size
            val fm = p.fontMetrics
            return centerY - (fm.ascent + fm.descent) / 2f
        }
    }
}