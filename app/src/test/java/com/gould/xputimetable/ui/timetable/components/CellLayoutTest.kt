/*
 * CellLayoutTest.kt —— 同格布局纯函数单测（Spec M2-C §1.4）
 *
 * 三档列宽（手机 46dp / 平板 120dp / 极窄 30dp）× n=1..5 × 多档高度：
 *   ① 所有 slot 两两不重叠且不越界；② 规则 2)/3)/4) 的优先级正确；③ foldedCount 正确。
 */
package com.gould.xputimetable.ui.timetable.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellLayoutTest {

    private val widths = listOf(46f, 120f, 30f)   // 手机竖屏 / 平板 / 极窄
    private val heights = listOf(52f, 104f, 156f, 260f) // 1..5 节（行高 52dp）

    // ---------- ① 全量性质：不重叠、不越界、slot 数与折叠一致 ----------

    @Test
    fun `全部组合_slot两两不重叠且不越界`() {
        for (w in widths) for (h in heights) for (n in 1..5) {
            val a = CellLayout.arrange(n, w, h)
            assertEquals("可画数 n=$n w=$w h=$h", n - a.foldedCount, a.slots.size)
            for (i in a.slots.indices) {
                val s = a.slots[i]
                assertTrue("不越左上 n=$n w=$w h=$h", s.dx >= 0f && s.dy >= 0f)
                assertTrue("不越右下 n=$n w=$w h=$h", s.dx + s.width <= w + 1e-4f && s.dy + s.height <= h + 1e-4f)
                assertTrue("尺寸为正 n=$n w=$w h=$h", s.width > 0f && s.height > 0f)
                for (j in i + 1 until a.slots.size) {
                    val o = a.slots[j]
                    val overlapX = s.dx < o.dx + o.width - 1e-4f && o.dx < s.dx + s.width - 1e-4f
                    val overlapY = s.dy < o.dy + o.height - 1e-4f && o.dy < s.dy + s.height - 1e-4f
                    assertTrue("slot $i 与 $j 重叠 n=$n w=$w h=$h", !(overlapX && overlapY))
                }
            }
        }
    }

    // ---------- ② 优先级 ----------

    @Test
    fun `规则1_n为1占满整格`() {
        val a = CellLayout.arrange(1, 46f, 52f)
        assertEquals(0, a.foldedCount)
        assertEquals(listOf(CardSlot(0f, 0f, 46f, 52f)), a.slots)
    }

    @Test
    fun `规则2_平板宽度够横向等分`() {
        // 120 / 2 = 60 >= 48 → 横向；120 / 3 = 40 < 48 → 不横向（走纵向或折叠）
        val two = CellLayout.arrange(2, 120f, 52f)
        assertEquals(0, two.foldedCount)
        assertTrue(two.slots.all { it.dy == 0f && it.height == 52f })
        assertEquals(60f, two.slots[0].width, 1e-4f)
        assertEquals(60f, two.slots[1].dx, 1e-4f)
        val three = CellLayout.arrange(3, 120f, 52f)
        // 120 / 3 = 40 < 48 且 52 / 3 < 24 → 不横向不纵向，折叠 2 张
        assertEquals(2, three.foldedCount)
    }

    @Test
    fun `规则3_手机竖屏走纵向等分`() {
        // 46 / 3 ≈ 15.3 < 48；156 / 3 = 52 >= 24 → 纵向（真机场景：3 门课 × 跨 3 节）
        val a = CellLayout.arrange(3, 46f, 156f)
        assertEquals(0, a.foldedCount)
        assertTrue(a.slots.all { it.dx == 0f && it.width == 46f })
        assertEquals(52f, a.slots[0].height, 1e-4f)
        assertEquals(52f, a.slots[1].dy, 1e-4f)
        assertEquals(104f, a.slots[2].dy, 1e-4f)
    }

    @Test
    fun `规则4_单节极窄折叠并报foldedCount`() {
        // 46 / 3 < 48；52 / 3 ≈ 17.3 < 24 → 折叠：只画第 1 张
        val a = CellLayout.arrange(3, 46f, 52f)
        assertEquals(2, a.foldedCount)
        assertEquals(1, a.slots.size)
        assertEquals(CardSlot(0f, 0f, 46f, 52f), a.slots[0])
    }

    @Test
    fun `规则4_极窄列宽两门课纵向可拆`() {
        // 30 / 2 = 15 < 48；104 / 2 = 52 >= 24 → 纵向
        val a = CellLayout.arrange(2, 30f, 104f)
        assertEquals(0, a.foldedCount)
        assertTrue(a.slots.all { it.dx == 0f && it.width == 30f })
        assertEquals(52f, a.slots[0].height, 1e-4f)
    }

    // ---------- ③ 折叠边界 ----------

    @Test
    fun `两门课单节手机宽_纵向恰好容不下时折叠`() {
        // 46 / 2 = 23 < 48；52 / 2 = 26 >= 24 → 纵向可拆（单节两门课仍可见）
        val a = CellLayout.arrange(2, 46f, 52f)
        assertEquals(0, a.foldedCount)
        assertEquals(2, a.slots.size)
        assertEquals(26f, a.slots[0].height, 1e-4f)
        assertEquals(26f, a.slots[1].dy, 1e-4f)
    }

    @Test
    fun `折叠后第一个slot占满整格`() {
        for (w in widths) for (h in heights) {
            val a = CellLayout.arrange(5, w, h)
            if (a.foldedCount > 0) {
                assertEquals(4, a.foldedCount)
                assertEquals(1, a.slots.size)
                assertEquals(CardSlot(0f, 0f, w, h), a.slots[0])
            }
        }
    }
}
