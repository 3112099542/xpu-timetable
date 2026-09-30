/*
 * WeekCalcTest.kt —— 周次判定与当前周计算（纯 JVM 单测，不依赖 Android）
 *
 * 覆盖：单双周判定、周次区间边界、开学前返回 null（不能出现负数周次）、给定周次求周一。
 */
package com.gould.xputimetable.domain

import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekCalcTest {

    private val start: LocalDate = LocalDate.of(2026, 9, 7) // 周一

    @Test
    fun `ALL 类型任意周都生效`() {
        assertTrue(WeekCalc.isSessionActive(WeekType.ALL, 1, 16, 9))
    }

    @Test
    fun `ODD 类型奇数周生效`() {
        assertTrue(WeekCalc.isSessionActive(WeekType.ODD, 1, 16, 3))
    }

    @Test
    fun `ODD 类型偶数周不生效`() {
        assertFalse(WeekCalc.isSessionActive(WeekType.ODD, 1, 16, 4))
    }

    @Test
    fun `EVEN 类型偶数周生效`() {
        assertTrue(WeekCalc.isSessionActive(WeekType.EVEN, 1, 16, 4))
    }

    @Test
    fun `EVEN 类型奇数周不生效`() {
        assertFalse(WeekCalc.isSessionActive(WeekType.EVEN, 1, 16, 3))
    }

    @Test
    fun `起始周边界：week 等于 startWeek 生效`() {
        assertTrue(WeekCalc.isSessionActive(WeekType.ALL, 2, 16, 2))
    }

    @Test
    fun `结束周边界：week 等于 endWeek 生效`() {
        assertTrue(WeekCalc.isSessionActive(WeekType.ALL, 1, 10, 10))
    }

    @Test
    fun `早于起始周不生效`() {
        assertFalse(WeekCalc.isSessionActive(WeekType.ALL, 3, 16, 2))
    }

    @Test
    fun `晚于结束周不生效`() {
        assertFalse(WeekCalc.isSessionActive(WeekType.ALL, 1, 16, 17))
    }

    @Test
    fun `currentWeek 开学当天为第 1 周`() {
        assertEquals(1, WeekCalc.currentWeek(start, start))
    }

    @Test
    fun `currentWeek 两周后为第 3 周`() {
        assertEquals(3, WeekCalc.currentWeek(start, start.plusDays(14)))
    }

    @Test
    fun `currentWeek 开学前返回 null，绝不返回负数周次`() {
        assertNull(WeekCalc.currentWeek(start, start.minusDays(1)))
    }

    @Test
    fun `mondayOfWeek 第 3 周周一为起始日加 14 天`() {
        assertEquals(start.plusDays(14), WeekCalc.mondayOfWeek(start, 3))
    }

    @Test
    fun `mondayOfWeek 传入 0 按第 1 周处理`() {
        assertEquals(start, WeekCalc.mondayOfWeek(start, 0))
    }
}
