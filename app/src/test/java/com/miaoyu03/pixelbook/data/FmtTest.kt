package com.miaoyu03.pixelbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 金额解析/格式化回归测试。
 *
 * 背景：早期 `parseCents` 用 Double 计算（`(v * 100).toLong()`），
 * 二进制无法精确表示 0.29 这类小数 → `0.29 * 100 = 28.999…` → 截断成 28 分，
 * 用户录入的金额静默少 1 分。以下用例锁定「精确到分」的行为。
 */
class FmtTest {

    /* ---------- parseCents：整型精确解析 ---------- */

    @Test
    fun `曾被浮点截断的金额解析正确`() {
        // 这些值在旧的 Double 实现下会各少 1 分
        assertEquals(29L, Fmt.parseCents("0.29"))
        assertEquals(113L, Fmt.parseCents("1.13"))
        assertEquals(1999L, Fmt.parseCents("19.99"))
    }

    @Test
    fun `两位数小数全部精确`() {
        // 穷举 0.00 ~ 19.99，确保分位永不丢失
        for (i in 0..1999) {
            val yuan = i / 100
            val cents = i % 100
            val text = "$yuan.${cents.toString().padStart(2, '0')}"
            assertEquals("解析 $text 应等于 $i 分", i.toLong(), Fmt.parseCents(text))
        }
    }

    @Test
    fun `整数与省略小数部分`() {
        assertEquals(500L, Fmt.parseCents("5"))
        assertEquals(500L, Fmt.parseCents("5."))
        assertEquals(50L, Fmt.parseCents(".5"))
        assertEquals(0L, Fmt.parseCents("0"))
        assertEquals(0L, Fmt.parseCents("0.00"))
    }

    @Test
    fun `容忍千分位与货币符号`() {
        assertEquals(900000L, Fmt.parseCents("9,000"))
        assertEquals(123456L, Fmt.parseCents("1,234.56"))
        assertEquals(10000L, Fmt.parseCents("¥100"))
        assertEquals(10000L, Fmt.parseCents("￥100"))
        assertEquals(1234L, Fmt.parseCents("  12.34  "))
    }

    @Test
    fun `零头补位正确`() {
        assertEquals(5L, Fmt.parseCents("0.05"))
        assertEquals(50L, Fmt.parseCents("0.5"))
        assertEquals(1200L, Fmt.parseCents("12.0"))
    }

    @Test
    fun `上界为9位整数2位小数`() {
        assertEquals(99999999999L, Fmt.parseCents("999999999.99"))
        assertEquals(99999999900L, Fmt.parseCents("999999999"))
        // 超界拒绝
        assertNull(Fmt.parseCents("9999999999"))
        assertNull(Fmt.parseCents("1000000000"))
        // 小数超 2 位拒绝（金额最小单位是分）
        assertNull(Fmt.parseCents("1.234"))
    }

    @Test
    fun `非法输入返回null`() {
        assertNull(Fmt.parseCents(""))
        assertNull(Fmt.parseCents("   "))
        assertNull(Fmt.parseCents("."))
        assertNull(Fmt.parseCents("abc"))
        assertNull(Fmt.parseCents("-5"))        // 负数不合法
        assertNull(Fmt.parseCents("1.2.3"))     // 多个小数点
        assertNull(Fmt.parseCents("1e3"))       // 科学计数法不当作金额
        assertNull(Fmt.parseCents("12元"))
    }

    /* ---------- money：格式化不丢精度 ---------- */

    @Test
    fun `格式化保留两位小数与千分位`() {
        assertEquals("0.00", Fmt.money(0L))
        assertEquals("0.05", Fmt.money(5L))
        assertEquals("0.29", Fmt.money(29L))
        assertEquals("12.34", Fmt.money(1234L))
        assertEquals("1,000.00", Fmt.money(100000L))
        assertEquals("12,345.67", Fmt.money(1234567L))
        assertEquals("999,999,999.99", Fmt.money(99999999999L))
    }

    @Test
    fun `负数格式化`() {
        assertEquals("-0.05", Fmt.money(-5L))
        assertEquals("-12.34", Fmt.money(-1234L))
    }

    @Test
    fun `yen 前缀`() {
        assertEquals("¥12.34", Fmt.yen(1234L))
        assertEquals("¥0.00", Fmt.yen(0L))
    }

    @Test
    fun `解析与格式化互为逆运算`() {
        val samples = listOf("0.01", "0.29", "1.13", "19.99", "1234.56", "999999999.99")
        for (s in samples) {
            val cents = Fmt.parseCents(s)!!
            val expected = java.text.DecimalFormat("#,##0.00").format(
                java.math.BigDecimal(s)
            )
            assertEquals("往返 $s", expected, Fmt.money(cents))
        }
    }

    /* ---------- cleanAmountInput：录入实时清洗 ---------- */

    @Test
    fun `清洗只保留数字与一个小数点`() {
        assertEquals("12.34", Fmt.cleanAmountInput("12.34"))
        assertEquals("1234", Fmt.cleanAmountInput("a1b2c3d4"))
        // 第二个小数点起被丢弃，不是拼接：1 . 2 (.) 3 (.) 4 → 1.23（小数位满 2 后停止）
        assertEquals("1.23", Fmt.cleanAmountInput("1.2.3.4"))
        assertEquals("12.34", Fmt.cleanAmountInput("12.34元"))
        assertEquals("", Fmt.cleanAmountInput(""))
    }

    @Test
    fun `清洗限制整数9位小数2位`() {
        assertEquals("123456789", Fmt.cleanAmountInput("1234567890123"))
        assertEquals("1.23", Fmt.cleanAmountInput("1.2345"))
        assertEquals("123456789.99", Fmt.cleanAmountInput("123456789.99"))
    }

    @Test
    fun `清洗结果可被解析`() {
        val cleaned = Fmt.cleanAmountInput("1,234.567")
        assertEquals(123456L, Fmt.parseCents(cleaned))
    }

    /* ---------- clip：按码点截断 ---------- */

    @Test
    fun `截断不劈开代理对`() {
        assertEquals("abc", Fmt.clip("abcdef", 3))
        assertEquals("中文", Fmt.clip("中文字符", 2))
        assertEquals("ab", Fmt.clip("ab", 5))          // 不足不补
        // max <= 0 视为「不限制」，原样返回（调用方用 0 表示禁用截断）
        assertEquals("abc", Fmt.clip("abc", 0))
        // emoji 是代理对，按码点计为 1 个字符，不应被截成半个
        assertEquals("😀", Fmt.clip("😀😀", 1))
        assertEquals("😀😀", Fmt.clip("😀😀", 2))
    }
}
