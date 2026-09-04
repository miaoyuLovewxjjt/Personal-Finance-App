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
import com.miaoyu03.pixelbook.data.CATEGORY_OTHERS
import com.miaoyu03.pixelbook.data.Deposit
import com.miaoyu03.pixelbook.data.DepositKind
import com.miaoyu03.pixelbook.data.Fmt
import com.miaoyu03.pixelbook.data.Ledger
import com.miaoyu03.pixelbook.data.Store
import com.miaoyu03.pixelbook.data.Tx
import com.miaoyu03.pixelbook.data.TxDir
import com.miaoyu03.pixelbook.data.Weather
import com.miaoyu03.pixelbook.ui.LedgerFonts
import com.miaoyu03.pixelbook.ui.PixelIcons
import com.miaoyu03.pixelbook.ui.Px
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

/**
 * 像素风 PDF 导出：整页 2x 超采样软件位图绘制（纯 Skia，规避 hwui 崩溃），清晰不模糊。
 *
 * 布局与 App 对齐：卡片式区块 + 宽松行距，块整体不跨页（杜绝文字挤压/骑标题）。
 * 章节结构（同 App 左侧导航的年 → 月 → 日分级）：
 *   一、账本信息
 *   二、存款明细
 *   三、收支明细
 *       {2026年}
 *           {2026年9月}
 *               {9月1日}  当日明细 → 日总结
 *               ...
 *           {9月总结}
 *       {2026年总结}
 */
object PdfExporter {

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val RENDER_SCALE = 2f
    private const val M = 40f
    private const val W = PAGE_W - M * 2
    private const val TOP = 34f
    private const val BOTTOM = PAGE_H - 58f

    private const val F_BODY = 12f      // 正文
    private const val F_SMALL = 10.5f   // 辅助
    private const val F_TINY = 9.5f     // 极细小字

    /** 导出入口：返回总页数 */
    fun export(context: Context, store: Store, ledger: Ledger, out: OutputStream): Int {
        val doc = PdfDocument()
        val typeface = fontOf(context, ledger.font)   // 跟随账本设置的字体
        val eng = Engine(doc, typeface)
        val txs = store.txList(ledger.id)
        val deps = store.depList(ledger.id)
        try {
            coverAndInfo(eng, ledger, txs, deps)
            deposits(eng, deps)
            txHierarchy(eng, store, ledger, txs)
            eng.finish()
            doc.writeTo(out)
        } catch (e: Exception) {
            android.util.Log.e("PdfExp", "export failed", e)
            throw e
        } finally {
            doc.close()
        }
        return eng.pageNo
    }

    private fun fontOf(context: Context, font: String): Typeface = when (font) {
        LedgerFonts.CUTE -> ResourcesCompat.getFont(context, R.font.zcool_kuaile)
        LedgerFonts.KAITI -> ResourcesCompat.getFont(context, R.font.lxgw_wenkai)
        LedgerFonts.SONG -> ResourcesCompat.getFont(context, R.font.zcool_xiaowei)
        else -> ResourcesCompat.getFont(context, R.font.zpix)
    } ?: Typeface.DEFAULT

    /* ================= 一、封面 / 账本信息 ================= */

    private fun coverAndInfo(eng: Engine, ledger: Ledger, txs: List<Tx>, deps: List<Deposit>) {
        val c = eng.c!!
        val cx = PAGE_W / 2f

        icon(c, PixelIcons.ledgerIcon(ledger.coverColor).asAndroidBitmap(), cx, 120f, 84f)
        val titleSize = when {
            ledger.name.length > 22 -> 15f
            ledger.name.length > 14 -> 18f
            else -> 25f
        }
        eng.txt.draw(c, ledger.name, cx, 216f, titleSize, Px.Brown.toArgb(), Paint.Align.CENTER)
        eng.txt.draw(c, "像素记账 · 收支报告", cx, 252f, 13f, Px.Wood.toArgb(), Paint.Align.CENTER)

        // 账本信息卡
        // 账本信息卡：值自动换行（最多 2 行，不截断），面板高度按内容自适应
        val infoLines = listOf(
            Triple("账本名称", ledger.name, false),
            Triple("创建时间", safeDate(ledger.createdAt), false),
            Triple("导出时间", Fmt.date(LocalDate.now()), false),
            Triple("总存款", Fmt.yen(deps.sumOf { it.value }), true),
            Triple("存款记录", "${deps.size} 笔", false),
            Triple("收支记录", "收入 ${txs.count { it.dir == TxDir.IN }} 笔 · 支出 ${txs.count { it.dir == TxDir.OUT }} 笔", false),
        )
        val infoY = 306f
        val labelPad = 24f   // 值起始：label 后留白
        val perLineH = 34f   // 单行行高
        val twoLineH = 52f   // 换行后两行占高
        val lineGap = 6f
        // 第一次遍历：按宽度计算每行占用高度 → 面板总高
        var infoH = 40f
        for ((label, value, _) in infoLines) {
            val labelW = eng.txt.width(label, F_SMALL)
            val avail = W - labelW - labelPad - 24f
            infoH += if (eng.txt.width(value, F_SMALL) <= avail) perLineH else twoLineH
        }
        infoH += (infoLines.size - 1) * lineGap
        panel(c, M, infoY, W, infoH, Px.Cream.toArgb())
        var iy = infoY + 18f
        for ((label, value, amount) in infoLines) {
            val labelW = eng.txt.width(label, F_SMALL)
            eng.txt.draw(c, label, M + 16f, iy, F_SMALL, Px.GrayText.toArgb())
            val valueColor = if (amount) Px.WoodDark.toArgb() else Px.Brown.toArgb()
            val valueSize = if (amount) 12f else F_SMALL
            val avail = W - labelW - labelPad - 24f
            if (eng.txt.width(value, valueSize) <= avail) {
                eng.txt.draw(c, value, M + W - 16f, iy, valueSize, valueColor, Paint.Align.RIGHT)
            } else {
                // 写不下：自动换行（最多 2 行），仍铺在卡内，不截断
                eng.txt.drawWrap(c, value, M + 16f + labelW + labelPad, iy, F_SMALL, valueColor, avail - 8f, 18f, 2)
            }
            iy += if (eng.txt.width(value, valueSize) <= avail) perLineH + lineGap else twoLineH + lineGap
        }

        // 章节目录：行自动换行（最多 2 行），面板高度按内容自适应
        val tocLines = listOf(
            Pair("ledger", "一、账本信息"),
            Pair("chest", "二、存款明细"),
            Pair("expense", "三、收支明细（按 年 → 月 → 日 分级，含日/月/年度各级总结）"),
        )
        val tocY = infoY + infoH + 24f
        var tocH = 24f
        for ((_, txt) in tocLines) {
            tocH += if (eng.txt.width(txt, F_BODY) <= W - 80f) 32f else 52f
        }
        tocH += 16f   // 底部留白，避免最后一行动态贴边框
        panel(c, M, tocY, W, tocH, Px.Cream.toArgb())
        eng.txt.draw(c, "目录", M + 16f, tocY + 20f, 15f, Px.Brown.toArgb())
        var ty = tocY + 48f
        for ((iconN, txt) in tocLines) {
            icon(c, PixelIcons.get(iconN).asAndroidBitmap(), M + 16f, ty, 14f)
            if (eng.txt.width(txt, F_BODY) <= W - 80f) {
                eng.txt.draw(c, txt, M + 42f, ty, F_BODY, Px.Brown.toArgb())
                ty += 32f
            } else {
                eng.txt.drawWrap(c, txt, M + 42f, ty, F_BODY, Px.Brown.toArgb(), W - 60f, 18f, 2)
                ty += 52f
            }
        }
        // 封面页结束：推进游标，让后续章节从新页开始（避免重叠）
        eng.y = tocY + tocH + 10f
    }

    private fun infoRow(c: Canvas, eng: Engine, label: String, value: String, y: Float, amount: Boolean = false) {
        eng.txt.draw(c, label, M + 16f, y, F_SMALL, Px.GrayText.toArgb())
        eng.txt.draw(
            c, eng.fit(value, W - 210f, F_SMALL), M + W - 16f, y, F_SMALL,
            if (amount) Px.WoodDark.toArgb() else Px.Brown.toArgb(),
            Paint.Align.RIGHT,
        )
    }

    private fun tocRow(c: Canvas, eng: Engine, iconName: String, text: String, y: Float) {
        icon(c, PixelIcons.get(iconName).asAndroidBitmap(), M + 16f, y, 14f)
        eng.txt.draw(c, text, M + 42f, y, F_BODY, Px.Brown.toArgb())
    }

    /* ================= 二、存款明细（卡片式列表） ================= */

    private fun deposits(eng: Engine, deps: List<Deposit>) {
        eng.newSection()
        eng.ensure(60f)
        chapterHeader(eng, "二、存款明细", "共 ${deps.size} 笔")
        val c = eng.c!!

        val sorted = deps.sortedWith(
            compareBy<Deposit> { if (it.kind == DepositKind.MONEY) 0 else 1 }
                .thenByDescending { it.date }
        )
        if (sorted.isEmpty()) {
            emptyNote(c, eng, "暂无存款记录")
        } else {
            for (d in sorted) {
                // 每张存款卡片整体一行，不跨页
                eng.ensure(42f)
                val y = eng.y
                card(c, M, y, W, 38f, Px.Cream.toArgb())
                val tagBg = if (d.kind == DepositKind.MONEY) Px.Clay.toArgb() else Px.SkyDark.toArgb()
                tag(c, eng, M + 34f, y + 18f, d.kind.label, tagBg)
                eng.txt.draw(
                    c, Fmt.dateYmd(d.date) + "  " + eng.fit(d.name.ifEmpty { "（未命名）" }, 130f, F_BODY),
                    M + 104f, y + 18f, F_BODY, Px.Brown.toArgb(),
                )
                eng.txt.draw(c, eng.fit(d.note, 120f, F_SMALL), M + 250f, y + 18f, F_SMALL, Px.GrayText.toArgb())
                eng.txt.draw(c, Fmt.yen(d.value), M + W - 16f, y + 19f, F_BODY, Px.WoodDark.toArgb(), Paint.Align.RIGHT)
                eng.y = y + 38f + 8f
            }
        }
        // 合计
        eng.ensure(40f)
        val y = eng.y
        fillRect(c, M, y, W, 34f, Px.Wood.toArgb())
        eng.txt.draw(c, "合计 · 总存款", M + 14f, y + 17f, F_BODY, Px.Cream.toArgb())
        eng.txt.draw(
            c, Fmt.yen(deps.sumOf { it.value }), M + W - 14f, y + 17f, F_BODY,
            Px.Cream.toArgb(), Paint.Align.RIGHT,
        )
        eng.y = y + 34f + 14f
    }

    /* ================= 三、收支明细（年 → 月 → 日分级 + 各级总结，同 App 布局） ================= */

    private fun txHierarchy(eng: Engine, store: Store, ledger: Ledger, txs: List<Tx>) {
        eng.newSection()
        eng.ensure(60f)
        chapterHeader(eng, "三、收支明细", "共 ${txs.size} 笔")
        val c = eng.c!!

        val byYear = txs.groupBy { it.date.year }.toSortedMap(compareByDescending { it })
        if (byYear.isEmpty()) {
            emptyNote(c, eng, "暂无收支记录")
            return
        }
        for ((year, yList) in byYear) {
            yearHeader(eng, year, yList)
            val byMonth = yList.groupBy { Fmt.ymKey(it.date) }.toSortedMap(compareBy { it })
            for ((ym, mList) in byMonth) {
                val ymObj = YearMonth.parse(ym)
                monthHeader(eng, "${ymObj.year}年${ymObj.monthValue}月", mList)
                val byDay = mList.groupBy { it.date }.toSortedMap(compareBy { it })
                for ((date, dList) in byDay) {
                    dayHeader(eng, date, store.weather(ledger.id, date))
                    val rows = dList.sortedWith(compareBy<Tx> { it.time }.thenBy { it.amount })
                    for (t in rows) {
                        txRow(eng, t)
                    }
                    daySummary(eng, date, dList)
                }
                monthSummary(eng, ym, mList)
                // 月图表：收入/花销占比环形图（同 App 月度总结页）
                ratioCard(
                    eng, "${ymObj.monthValue}月收入占比",
                    "总收入", Fmt.money(inSumOf(mList)),
                    catRatio(mList, TxDir.IN), Px.GrassDark.toArgb(),
                )
                ratioCard(
                    eng, "${ymObj.monthValue}月花销占比",
                    "总花销", Fmt.money(outSumOf(mList)),
                    catRatio(mList, TxDir.OUT), Px.WoodDark.toArgb(),
                )
            }
            yearSummary(eng, year, yList)
            // 年图表：收入/花销占比环形图 + 按月收入/花销柱状图（同 App 年度总结页）
            ratioCard(
                eng, "${year}年收入占比",
                "总收入", Fmt.money(inSumOf(yList)),
                catRatio(yList, TxDir.IN), Px.GrassDark.toArgb(),
            )
            ratioCard(
                eng, "${year}年花销占比",
                "总花销", Fmt.money(outSumOf(yList)),
                catRatio(yList, TxDir.OUT), Px.WoodDark.toArgb(),
            )
            monthBarCard(eng, year, yList)
        }
    }

    private fun inSumOf(list: List<Tx>): Long = list.filter { it.dir == TxDir.IN }.sumOf { it.amount }
    private fun outSumOf(list: List<Tx>): Long = list.filter { it.dir == TxDir.OUT }.sumOf { it.amount }

    /** 分类汇总（降序，返回 分类→金额 分） */
    private fun catRatio(list: List<Tx>, dir: TxDir): List<Pair<String, Long>> =
        list.filter { it.dir == dir }
            .groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }


    /** 年标题条：深木双段条，全宽（醒目大标题） */
    private fun yearHeader(eng: Engine, year: Int, yList: List<Tx>) {
        eng.ensure(52f)
        val y = eng.y
        val c = eng.c!!
        titleBar(c, eng, "${year}年", 17f,
            "收入 ${Fmt.money(inSumOf(yList))}   支出 ${Fmt.money(outSumOf(yList))}",
            isDark = true, h = 42f)
        eng.y = y + 42f + 12f
    }

    /** 月标题条：暖黄条，全宽 */
    private fun monthHeader(eng: Engine, title: String, mList: List<Tx>) {
        eng.ensure(44f)
        val y = eng.y
        val c = eng.c!!
        titleBar(c, eng, title, 14f,
            "收入 ${Fmt.money(inSumOf(mList))}   支出 ${Fmt.money(outSumOf(mList))}",
            isDark = false, h = 36f)
        eng.y = y + 36f + 10f
    }

    /** 日标题条：全宽浅木条（醒目，独立成条） */
    private fun dayHeader(eng: Engine, date: LocalDate, weather: Weather?) {
        eng.ensure(36f)
        val y = eng.y
        val c = eng.c!!
        fillRect(c, M, y, W, 30f, Px.CreamDark.toArgb())
        val stroker = Paint().apply {
            isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 2f; this.color = Px.Brown.toArgb()
        }
        c.drawRect(M + 1f, y + 1f, M + W - 1f, y + 30f - 1f, stroker)
        icon(c, PixelIcons.get("calendar").asAndroidBitmap(), M + 20f, y + 15f, 13f)
        eng.txt.draw(c, "${Fmt.dayOfMonth(date)}  ${Fmt.weekday(date)}", M + 40f, y + 15f, 13f, Px.WoodDark.toArgb())
        // 天气图标跟随日期右侧
        weather?.let { w ->
            icon(c, PixelIcons.get(w.iconName()).asAndroidBitmap(), M + W - 24f, y + 15f, 14f)
        }
        eng.y = y + 30f + 8f
    }

    /** 单笔明细（描边卡片，全宽左对齐，卡片间距 6） */
    private fun txRow(eng: Engine, t: Tx) {
        eng.ensure(40f)
        val y = eng.y
        val c = eng.c!!
        card(c, M, y, W, 36f, Px.Cream.toArgb())
        icon(c, PixelIcons.get(if (t.dir == TxDir.IN) "income" else "expense").asAndroidBitmap(), M + 20f, y + 18f, 15f)
        eng.txt.draw(c, t.time, M + 46f, y + 18f, F_SMALL, Px.GrayText.toArgb())
        eng.txt.draw(
            c, eng.fit(t.category + if (t.name.isNotEmpty()) " · ${t.name}" else "", 150f, F_BODY),
            M + 98f, y + 18f, F_BODY, Px.Brown.toArgb(),
        )
        eng.txt.draw(c, eng.fit(t.note, 130f, F_SMALL), M + 262f, y + 18f, F_SMALL, Px.GrayText.toArgb())
        eng.txt.draw(
            c, Fmt.yen(t.amount), M + W - 16f, y + 18f, F_BODY,
            if (t.dir == TxDir.IN) Px.GrassDark.toArgb() else Px.WoodDark.toArgb(),
            Paint.Align.RIGHT,
        )
        eng.y = y + 36f + 6f
    }

    /** 日总结：全宽卡片（鲜明标题 + 宽行距），紧跟当日明细 */
    private fun daySummary(eng: Engine, date: LocalDate, dList: List<Tx>) {
        val inSum = inSumOf(dList)
        val outSum = outSumOf(dList)
        val maxIn = dList.filter { it.dir == TxDir.IN }.maxByOrNull { it.amount }
        val maxOut = dList.filter { it.dir == TxDir.OUT }.maxByOrNull { it.amount }

        eng.ensure(176f)
        val y = eng.y
        val c = eng.c!!
        panel(c, M, y, W, 170f, Px.Cream.toArgb())
        eng.txt.draw(c, "日总结", M + 16f, y + 14f, 11f, Px.GrayText.toArgb())
        statLine(c, eng, "总收入", Fmt.money(inSum), Px.GrassDark.toArgb(), M + 16f, y + 40f, 160f)
        statLine(c, eng, "总支出", Fmt.money(outSum), Px.WoodDark.toArgb(), M + 186f, y + 40f, 160f)
        statLine(
            c, eng, "结余", Fmt.money(inSum - outSum),
            if (inSum - outSum >= 0) Px.GrassDark.toArgb() else Px.ClayDark.toArgb(),
            M + 356f, y + 40f, 135f,
        )
        maxLine(c, eng, "最大收入", maxIn, Px.GrassDark.toArgb(), M + 16f, y + 66f, 270f)
        maxLine(c, eng, "最大花销", maxOut, Px.WoodDark.toArgb(), M + 16f, y + 112f, 270f)
        eng.y = y + 170f + 14f
    }

    /** 月总结：醒目标题条 + 数据卡，紧跟最后一日的日总结 */
    private fun monthSummary(eng: Engine, ym: String, mList: List<Tx>) {
        val inSum = inSumOf(mList)
        val outSum = outSumOf(mList)
        val maxIn = mList.filter { it.dir == TxDir.IN }.maxByOrNull { it.amount }
        val maxOut = mList.filter { it.dir == TxDir.OUT }.maxByOrNull { it.amount }
        val maxOutDay = maxDayOf(mList, TxDir.OUT)
        val maxInDay = maxDayOf(mList, TxDir.IN)
        val ymObj = YearMonth.parse(ym)

        eng.ensure(240f)
        val y = eng.y
        val c = eng.c!!
        titleBar(c, eng, "${ymObj.monthValue}月总结", 15f, "", isDark = false, h = 34f)
        eng.y = y + 34f + 10f
        val y2 = eng.y
        panel(c, M, y2, W, 172f, Px.Cream.toArgb())
        statLine(c, eng, "总收入", Fmt.money(inSum), Px.GrassDark.toArgb(), M + 16f, y2 + 32f, 165f)
        statLine(c, eng, "总支出", Fmt.money(outSum), Px.WoodDark.toArgb(), M + 192f, y2 + 32f, 165f)
        statLine(
            c, eng, "总结余", Fmt.money(inSum - outSum),
            if (inSum - outSum >= 0) Px.GrassDark.toArgb() else Px.ClayDark.toArgb(),
            M + 368f, y2 + 32f, 120f,
        )
        statLine(c, eng, "支出最多日", maxOutDay?.let { Fmt.dayOfMonth(it) } ?: "—", Px.Brown.toArgb(), M + 16f, y2 + 62f, 200f)
        statLine(c, eng, "收入最多日", maxInDay?.let { Fmt.dayOfMonth(it) } ?: "—", Px.Brown.toArgb(), M + 240f, y2 + 62f, 200f)
        maxLine(c, eng, "本月最大收入", maxIn, Px.GrassDark.toArgb(), M + 16f, y2 + 92f, 280f)
        maxLine(c, eng, "本月最大花销", maxOut, Px.WoodDark.toArgb(), M + 16f, y2 + 138f, 280f)
        eng.y = y2 + 172f + 14f
    }

    /** 年度总结：醒目深色标题条 + 数据卡，收尾章节 */
    private fun yearSummary(eng: Engine, year: Int, yList: List<Tx>) {
        val inSum = inSumOf(yList)
        val outSum = outSumOf(yList)
        val maxIn = yList.filter { it.dir == TxDir.IN }.maxByOrNull { it.amount }
        val maxOut = yList.filter { it.dir == TxDir.OUT }.maxByOrNull { it.amount }
        val maxOutMonth = maxMonthOf(yList, TxDir.OUT)
        val maxInMonth = maxMonthOf(yList, TxDir.IN)

        eng.ensure(250f)
        val y = eng.y
        val c = eng.c!!
        titleBar(c, eng, "${year}年总结", 17f, "", isDark = true, h = 42f)
        eng.y = y + 42f + 12f
        val y2 = eng.y
        panel(c, M, y2, W, 172f, Px.Cream.toArgb())
        statLine(c, eng, "总收入", Fmt.money(inSum), Px.GrassDark.toArgb(), M + 16f, y2 + 32f, 175f)
        statLine(c, eng, "总支出", Fmt.money(outSum), Px.WoodDark.toArgb(), M + 206f, y2 + 32f, 175f)
        statLine(
            c, eng, "总结余", Fmt.money(inSum - outSum),
            if (inSum - outSum >= 0) Px.GrassDark.toArgb() else Px.ClayDark.toArgb(),
            M + 396f, y2 + 32f, 95f,
        )
        statLine(c, eng, "支出最多月", maxOutMonth?.let { ymLabel(it) } ?: "—", Px.Brown.toArgb(), M + 16f, y2 + 62f, 220f)
        statLine(c, eng, "收入最多月", maxInMonth?.let { ymLabel(it) } ?: "—", Px.Brown.toArgb(), M + 250f, y2 + 62f, 220f)
        maxLine(c, eng, "本年度最大收入", maxIn, Px.GrassDark.toArgb(), M + 16f, y2 + 92f, 290f)
        maxLine(c, eng, "本年度最大花销", maxOut, Px.WoodDark.toArgb(), M + 16f, y2 + 138f, 290f)
        eng.y = y2 + 172f + 14f
    }
    /** 标签左 + 值右 的横排条目（同一基线，宽松间距） */
    private fun statLine(
        c: Canvas, eng: Engine, label: String, value: String, valueColor: Int,
        x: Float, y: Float, w: Float,
    ) {
        eng.txt.draw(c, label, x, y, 11f, Px.GrayText.toArgb())
        eng.txt.draw(c, eng.fit(value, w - 46f, 11.5f), x + w - 4f, y, 11.5f, valueColor, Paint.Align.RIGHT)
    }

    /** 最大收入/花销条目：首行 标签+金额（右侧）；次行 日期+分类·名称 自动换行 */
    /** 最大收入/花销条目：标签+明细+金额 默认同一行（金额固定最右）；
     *  明细写不下时从断点自动换行续写（最多续 2 行），金额始终钉在第一行右侧 */
    private fun maxLine(
        c: Canvas, eng: Engine, label: String, tx: Tx?, color: Int,
        x: Float, y: Float, maxW: Float,
    ) {
        eng.txt.draw(c, "$label：", x, y, 11f, Px.GrayText.toArgb())
        if (tx == null) {
            eng.txt.draw(c, "—", x + maxW - 4f, y, 11f, Px.GrayText.toArgb(), Paint.Align.RIGHT)
            return
        }
        val amountStr = Fmt.yen(tx.amount)
        val detail = "${tx.date.monthValue}.${tx.date.dayOfMonth} ${tx.category}" +
            if (tx.name.isNotEmpty()) " · ${tx.name}" else ""
        val labelW = eng.txt.width("$label：", 11f)
        val amountW = eng.txt.width(amountStr, 11f)
        val gap = 8f
        // 明细在第一行的可用宽度（右侧留给金额）
        val availW = (maxW - labelW - amountW - gap * 2).coerceAtLeast(24f)
        val detailW = eng.txt.width(detail, 10.5f)
        // 金额永远画在第一行最右
        eng.txt.draw(c, amountStr, x + maxW - 4f, y, 11f, color, Paint.Align.RIGHT)
        if (detailW <= availW) {
            // 全部塞得下：标签 + 明细 + 金额 一行
            eng.txt.draw(c, detail, x + labelW + gap, y, 10.5f, color)
        } else {
            // 明细超宽：首行画能放下的部分，剩余部分换行续写（左对齐，续行不占金额行）
            val first = eng.txt.prefix(detail, 10.5f, availW)
            eng.txt.draw(c, first, x + labelW + gap, y, 10.5f, color)
            val rest = detail.substring(first.length)
            if (rest.isNotEmpty()) {
                eng.txt.drawWrap(c, rest, x, y + 18f, 10.5f, color, maxW - 4f, 16f, 2)
            }
        }
    }

    /** 统一标题条：全宽；isDark=true 深木双段，false 暖黄 */
    private fun titleBar(c: Canvas, eng: Engine, title: String, size: Float, right: String, isDark: Boolean, h: Float) {
        if (isDark) {
            fillRect(c, M, eng.y, W, h / 2f, Px.WoodDark.toArgb())
            fillRect(c, M, eng.y + h / 2f, W, h / 2f, Px.Wood.toArgb())
        } else {
            fillRect(c, M, eng.y, W, h, 0xFFEBD9A8.toInt())
        }
        val line = eng.line
        line.color = Px.BrownDark.toArgb()
        line.strokeWidth = 2f
        c.drawLine(M, eng.y + h, M + W, eng.y + h, line)
        eng.txt.draw(c, title, M + 14f, eng.y + h / 2f, size, if (isDark) Px.Cream.toArgb() else Px.Brown.toArgb())
        if (right.isNotEmpty()) {
            eng.txt.draw(c, right, M + W - 14f, eng.y + h / 2f, 11f, if (isDark) Px.Cream.toArgb() else Px.Brown.toArgb(), Paint.Align.RIGHT)
        }
    }

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

    private fun ymLabel(ym: String): String {
        val m = YearMonth.parse(ym)
        return "${m.year}年${m.monthValue}月"
    }

    /* ================= 图表卡：占比环形图 / 月度双柱图 ================= */

    /** 占比环形图卡：环 + 中心总金额 + 右侧图例（分类/占比/金额），同 App 环形占比卡 */
    private fun ratioCard(
        eng: Engine, title: String,
        centerLabel: String, centerValue: String,
        data: List<Pair<String, Long>>, valueColor: Int,
    ) {
        val total = data.sumOf { it.second }.coerceAtLeast(1)
        eng.ensure(206f)
        val y = eng.y
        val c = eng.c!!
        panel(c, M, y, W, 200f, Px.Cream.toArgb())
        eng.txt.draw(c, title, M + 30f, y + 16f, 14f, Px.Brown.toArgb())

        // 左侧环形图
        val cx = M + 106f
        val cy = y + 112f
        donut(c, cx, cy, 62f, 42f, data, total)
        // 环中心大字
        eng.txt.draw(c, centerLabel, cx, cy - 8f, 11f, Px.GrayText.toArgb(), Paint.Align.CENTER)
        eng.txt.draw(c, centerValue, cx, cy + 14f, 13f, valueColor, Paint.Align.CENTER)

        // 右侧图例（分类 · 金额 · 占比）
        var ly = y + 44f
        for ((name, v) in data) {
            eng.txt.draw(c, name, M + 190f, ly, 11f, Px.Brown.toArgb())
            eng.txt.draw(
                c, Fmt.money(v), M + 300f, ly, 11f,
                if (name == CATEGORY_OTHERS) Px.GrayText.toArgb() else valueColor, Paint.Align.RIGHT,
            )
            eng.txt.draw(
                c, "${(v.toFloat() / total * 100 + 0.5f).toInt()}%", M + W - 28f, ly, 11f,
                Px.GrayText.toArgb(), Paint.Align.RIGHT,
            )
            ly += 20f
        }
        eng.y = y + 200f + 14f
    }

    /** 像素环形图（对应 App PixelDonut：底色环 + 分类段 + 深棕分割线 + 挖空内圆） */
    private fun donut(
        c: Canvas,
        cx: Float, cy: Float,
        outer: Float, inner: Float,
        segs: List<Pair<String, Long>>,
        total: Long,
    ) {
        val fill = Paint().apply { isAntiAlias = false }
        val rect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        fill.color = Px.CreamBg.toArgb()
        c.drawArc(rect, 0f, 360f, true, fill)
        var a0 = -90f
        for ((name, v) in segs) {
            if (v <= 0) continue
            val sweep = v.toFloat() / total * 360f
            val gap = if (segs.size > 1) 1.2f else 0f
            fill.color = PixelIcons.colorOfCategory(name).toArgb()
            if (sweep - gap > 0.2f) c.drawArc(rect, a0 + gap / 2f, sweep - gap, true, fill)
            a0 += sweep
        }
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
        fill.color = Px.Cream.toArgb()
        c.drawCircle(cx, cy, inner, fill)
    }

    /** 月度双柱图卡：12 个月 × 收入（草绿）/ 花销（陶土橘），同 App 月度柱状图 */
    private fun monthBarCard(eng: Engine, year: Int, yList: List<Tx>) {
        val monthsIn = (1..12).map { m ->
            yList.filter { it.date.monthValue == m && it.dir == TxDir.IN }.sumOf { it.amount }
        }
        val monthsOut = (1..12).map { m ->
            yList.filter { it.date.monthValue == m && it.dir == TxDir.OUT }.sumOf { it.amount }
        }
        val maxV = (monthsIn + monthsOut).maxOrNull()?.coerceAtLeast(1) ?: 1

        eng.ensure(272f)
        val y = eng.y
        val c = eng.c!!
        panel(c, M, y, W, 266f, Px.Cream.toArgb())
        eng.txt.draw(c, "按月收入 / 花销（${year}年）", M + 16f, y + 16f, 14f, Px.Brown.toArgb())
        // 图例
        legendChip(c, eng, Px.ChartIn.toArgb(), "收入", M + 200f, y + 16f)
        legendChip(c, eng, Px.ChartOut.toArgb(), "花销", M + 270f, y + 16f)

        // 柱区
        val plotX = M + 20f
        val plotW = W - 40f
        val plotTop = y + 44f
        val plotBottom = y + 224f
        val plotH = plotBottom - plotTop
        val mw = plotW / 12f
        val barW = (mw * 0.24f).coerceAtMost(13f)

        // 月份分隔线 + 基线
        val line = eng.line
        line.color = Px.CreamDark.toArgb()
        line.strokeWidth = 1f
        for (m in 1..11) {
            c.drawLine(plotX + m * mw, plotTop, plotX + m * mw, plotBottom, line)
        }
        line.color = Px.Brown.toArgb()
        line.strokeWidth = 2f
        c.drawLine(plotX, plotBottom, plotX + plotW, plotBottom, line)

        // 双柱
        fun bars(list: List<Long>, color: Int, isLeft: Boolean) {
            val fill = Paint().apply { isAntiAlias = false; this.color = color }
            val stroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1.5f; this.color = Px.Brown.toArgb() }
            list.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val cx = plotX + i * mw + if (isLeft) mw * 0.28f else mw * 0.72f
                val h = (v.toFloat() / maxV) * plotH
                val x0 = cx - barW / 2f
                val y0 = plotBottom - h
                c.drawRect(x0, y0, x0 + barW, plotBottom, fill)
                c.drawRect(x0, y0, x0 + barW, plotBottom, stroke)
            }
        }
        bars(monthsIn, Px.ChartIn.toArgb(), true)
        bars(monthsOut, Px.ChartOut.toArgb(), false)

        // 月份刻度
        for (m in 1..12) {
            eng.txt.draw(c, "$m", plotX + (m - 1) * mw + mw / 2f, y + 240f, 9.5f, Px.GrayText.toArgb(), Paint.Align.CENTER)
        }
        eng.y = y + 266f + 14f
    }

    /** 小图例色块 + 文字 */
    private fun legendChip(c: Canvas, eng: Engine, color: Int, label: String, x: Float, cy: Float) {
        fillRect(c, x, cy - 5f, 12f, 10f, color)
        val stroker = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1f; this.color = Px.Brown.toArgb() }
        c.drawRect(x + 0.5f, cy - 5.5f, x + 12.5f, cy + 5.5f, stroker)
        eng.txt.draw(c, label, x + 18f, cy, 10.5f, Px.Brown.toArgb())
    }

    private fun Weather.iconName(): String = when (this) {
        Weather.SUNNY -> "sun"; Weather.CLOUDY -> "cloud"; Weather.RAIN -> "rain"
        Weather.SNOW -> "snow"
    }

    /* ================= 通用绘制 ================= */

    /** 章标题（木棕两段条） */
    private fun chapterHeader(eng: Engine, title: String, sub: String = "") {
        val c = eng.c!!
        val y = eng.y
        fillRect(c, M, y, W, 44f, Px.WoodDark.toArgb())
        fillRect(c, M, y + 22f, W, 22f, Px.Wood.toArgb())
        val line = eng.line
        line.color = Px.BrownDark.toArgb()
        line.strokeWidth = 2f
        c.drawLine(M, y + 44f, M + W, y + 44f, line)
        eng.txt.draw(c, title, M + 14f, y + 22f, 17f, Px.Cream.toArgb())
        if (sub.isNotEmpty()) {
            eng.txt.draw(c, sub, M + W - 14f, y + 22f, 11f, Px.Cream.toArgb(), Paint.Align.RIGHT)
        }
        eng.y = y + 44f + 14f
    }

    /** 描边卡片（奶油底 + 深棕边框） */
    private fun card(c: Canvas, x: Float, y: Float, w: Float, h: Float, bg: Int) {
        fillRect(c, x, y, w, h, bg)
        val stroker = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Px.Brown.toArgb()
        }
        val sw = 1f
        c.drawRect(x + sw, y + sw, x + w - sw, y + h - sw, stroker)
    }

    private fun emptyNote(c: Canvas, eng: Engine, text: String) {
        eng.ensure(56f)
        val y = eng.y
        fillRect(c, M, y, W, 48f, Px.Cream.toArgb())
        eng.txt.draw(c, text, PAGE_W / 2f, y + 24f, F_BODY, Px.GrayText.toArgb(), Paint.Align.CENTER)
        eng.y = y + 48f + 10f
    }

    private fun tag(c: Canvas, eng: Engine, cx: Float, cy: Float, text: String, bg: Int) {
        val size = 10f
        val w = eng.txt.width(text, size) + 12f
        val h = 18f
        val x = cx - w / 2f
        val y = cy - h / 2f
        fillRect(c, x, y, w, h, bg)
        eng.txt.draw(c, text, cx, cy, size, Px.Cream.toArgb(), Paint.Align.CENTER)
    }

    private fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float, bg: Int, depth: Float = 3f) {
        fillRect(c, x, y, w, h, Px.BrownDark.toArgb())
        fillRect(c, x, y + depth, w, h - depth, Px.Brown.toArgb())
        fillRect(c, x + depth, y + depth, w - depth, h - depth, Px.WoodDark.toArgb())
        fillRect(c, x + depth * 2, y + depth * 2, w - depth * 2, h - depth * 2, bg)
        val stroker = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 2f; color = Px.Brown.toArgb() }
        val sw = 1f
        c.drawRect(x + depth * 2 + sw, y + depth * 2 + sw, x + w - depth * 2 - sw, y + h - depth * 2 - sw, stroker)
    }

    private fun fillRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val p = Paint().apply { isAntiAlias = false; this.color = color }
        c.drawRect(x, y, x + w, y + h, p)
    }

    private fun icon(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, size: Float) {
        val half = size / 2f
        c.drawBitmap(bmp, null, RectF(cx - half, cy - half, cx + half, cy + half), Paint())
    }

    private fun safeDate(s: String): String = runCatching {
        Fmt.date(LocalDate.parse(s))
    }.getOrDefault(s)

    /* ================= 页面引擎（2x 超采样软件画布） ================= */

    private class Engine(private val doc: PdfDocument, typeface: Typeface) {

        val txt = Txt(typeface)
        val line = Paint().apply { isAntiAlias = false; strokeWidth = 1f }
        var pageNo = 0; private set
        var y = TOP
        private var page: PdfDocument.Page? = null
        private var pageBitmap: Bitmap? = null
        var c: Canvas? = null
            private set
        private val pagePaint = Paint()
        private val texture: Paint by lazy {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val p = Paint().apply { isAntiAlias = false }
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
            val bmp = Bitmap.createBitmap(
                (PAGE_W * RENDER_SCALE).toInt(), (PAGE_H * RENDER_SCALE).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            pageBitmap = bmp
            val cv = Canvas(bmp)
            cv.scale(RENDER_SCALE, RENDER_SCALE)
            cv.drawRect(0f, 0f, PAGE_W, PAGE_H, texture)
            c = cv
            y = TOP
        }

        fun finishPage() {
            val cv = c ?: return
            val line = this.line
            line.color = Px.GrayText.toArgb()
            line.strokeWidth = 1f
            cv.drawLine(M, PAGE_H - 44f, PAGE_W - M, PAGE_H - 44f, line)
            txt.draw(cv, AppMeta.GIT_URL, M, PAGE_H - 32f, F_TINY, Px.GrayText.toArgb())
            txt.draw(cv, "第 $pageNo 页", PAGE_W - M, PAGE_H - 32f, F_TINY, Px.GrayText.toArgb(), Paint.Align.RIGHT)
            val bmp = pageBitmap
            if (bmp != null) {
                page?.canvas?.drawBitmap(bmp, null, RectF(0f, 0f, PAGE_W, PAGE_H), pagePaint)
            }
            doc.finishPage(page)
            pageBitmap?.recycle()
            pageBitmap = null
            c = null
            page = null
        }

        fun finish() = finishPage()

        /** 章节分页：当前页已有内容则强制另起新页（章节标题不在页尾/骑在其他内容上） */
        fun newSection() {
            if (y > TOP + 1f) newPage()
        }

        /** 预留垂直空间，不足则翻页；返回是否发生了翻页 */
        fun ensure(h: Float): Boolean {
            if (y + h > BOTTOM) {
                        newPage()
                return true
            }
            return false
        }

        /** 文本按宽度截断，超出补 ".."，杜绝溢出/压行 */
        fun fit(text: String, maxW: Float, size: Float): String {
            if (text.isEmpty() || txt.width(text, size) <= maxW) return text
            var s = text
            while (s.length > 1 && txt.width(s, size) > maxW) s = s.dropLast(1)
            return s.trimEnd() + ".."
        }
    }

    /** 文本绘制：软件画布（纯 Skia） */
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

        /** 取能放入 maxW 内的最长前缀（不足则返回原串） */
        fun prefix(text: String, size: Float, maxW: Float): String {
            if (text.isEmpty() || width(text, size) <= maxW) return text
            var end = text.length
            while (end > 1 && width(text.substring(0, end), size) > maxW) end--
            return text.substring(0, end)
        }

        /** 自动换行绘制：按最大宽度拆分，最多 maxLines 行 */
        fun drawWrap(
            c: Canvas,
            text: String,
            x: Float,
            centerY: Float,
            size: Float,
            color: Int,
            maxW: Float,
            lineGap: Float,
            maxLines: Int,
        ) {
            if (text.isEmpty()) return
            var rest = text
            var line = 0
            while (rest.isNotEmpty() && line < maxLines) {
                val seg = prefix(rest, size, maxW)
                draw(c, seg, x, centerY + line * lineGap, size, color)
                rest = rest.substring(seg.length)
                line++
                if (seg.isEmpty()) break
            }
        }

        private fun baseline(centerY: Float, size: Float): Float {
            p.textSize = size
            val fm = p.fontMetrics
            return centerY - (fm.ascent + fm.descent) / 2f
        }
    }
}