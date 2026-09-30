/*
 * WeekHeaderLabelTest.kt —— 表头纯函数单测（M4-UI 规格 §7）
 *
 * 覆盖：weekdayCn 1..7 与越界、monthLabelCn 12 个月与越界、
 * 跨月周取周一月份、startDate 非法/缺失返回 null（UI 留空不崩）。
 */
package com.gould.xputimetable.ui.timetable.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekHeaderLabelTest {

    @Test
    fun `weekdayCn 1 到 7 逐一对应`() {
        assertEquals("周一", weekdayCn(1))
        assertEquals("周二", weekdayCn(2))
        assertEquals("周三", weekdayCn(3))
        assertEquals("周四", weekdayCn(4))
        assertEquals("周五", weekdayCn(5))
        assertEquals("周六", weekdayCn(6))
        assertEquals("周日", weekdayCn(7))
    }

    @Test
    fun `weekdayCn 越界返回空串`() {
        assertEquals("", weekdayCn(0))
        assertEquals("", weekdayCn(8))
        assertEquals("", weekdayCn(-1))
    }

    @Test
    fun `monthLabelCn 12 个月`() {
        (1..12).forEach { month ->
            assertEquals("$month 月", monthLabelCn(month))
        }
    }

    @Test
    fun `monthLabelCn 越界返回空串`() {
        assertEquals("", monthLabelCn(0))
        assertEquals("", monthLabelCn(13))
    }

    @Test
    fun `跨月周取周一所在月份`() {
        // 学期起日 2026-09-14（周一）：第 1 周周一 9/14 → 9 月；第 4 周周一 10/5 → 10 月
        assertEquals(9, monthOfMonday("2026-09-14", 1))
        assertEquals(10, monthOfMonday("2026-09-14", 4))
        // 学期起日 2026-09-28（周一）：第 1 周周一 9/28 → 9 月；第 2 周周一 10/5 → 10 月
        assertEquals(9, monthOfMonday("2026-09-28", 1))
        assertEquals(10, monthOfMonday("2026-09-28", 2))
    }

    @Test
    fun `起日缺失或非法返回 null`() {
        assertNull(monthOfMonday(null, 1))
        assertNull(monthOfMonday("", 1))
        assertNull(monthOfMonday("   ", 1))
        assertNull(monthOfMonday("not-a-date", 1))
    }

    @Test
    fun `常规周取值`() {
        // 学期起日 2026-09-14：第 2 周周一 9/21（9 月）、第 5 周周一 10/12（10 月）
        assertEquals(9, monthOfMonday("2026-09-14", 2))
        assertEquals(10, monthOfMonday("2026-09-14", 5))
    }
}
