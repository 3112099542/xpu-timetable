/*
 * WeekCalcParseTest.kt —— week_type 脏数据容错与 nextSessionTime 时间换算
 *
 * 重点：解析绝不抛异常（未知值回退 ALL）—— 否则一条脏数据会让整张课表崩掉。
 */
package com.gould.xputimetable.domain

import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.time.ZonedDateTime

class WeekCalcParseTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(day: LocalDate, minute: Int): Long =
        ZonedDateTime.of(day, LocalTime.of(minute / 60, minute % 60), zone).toInstant().toEpochMilli()

    @Test
    fun `标准 ODD`() {
        assertEquals(WeekType.ODD, WeekCalc.parseWeekType("ODD"))
    }

    @Test
    fun `标准 EVEN`() {
        assertEquals(WeekType.EVEN, WeekCalc.parseWeekType("EVEN"))
    }

    @Test
    fun `标准 ALL`() {
        assertEquals(WeekType.ALL, WeekCalc.parseWeekType("ALL"))
    }

    @Test
    fun `小写 odd 也能识别（忽略大小写）`() {
        assertEquals(WeekType.ODD, WeekCalc.parseWeekType("odd"))
    }

    @Test
    fun `首尾空白被忽略`() {
        assertEquals(WeekType.EVEN, WeekCalc.parseWeekType("  EVEN  "))
    }

    @Test
    fun `空串回退 ALL`() {
        assertEquals(WeekType.ALL, WeekCalc.parseWeekType(""))
    }

    @Test
    fun `null 回退 ALL`() {
        assertEquals(WeekType.ALL, WeekCalc.parseWeekType(null))
    }

    @Test
    fun `未知 token 回退 ALL 且不抛异常`() {
        assertEquals(WeekType.ALL, WeekCalc.parseWeekType("SINGLE"))
    }

    @Test
    fun `nextSessionTime 今天还没开始的课返回今天的开始时刻`() {
        val monday = LocalDate.of(2026, 9, 7)
        val session = CourseSession(
            id = 1L, courseId = "c1", dayOfWeek = 1,
            startSection = 1, endSection = 2, startWeek = 1, endWeek = 16, weekType = WeekType.ALL
        )
        val from = at(monday, 400) // 06:40，早于第 1 节 08:00
        val result = WeekCalc.nextSessionTime(session, 480, 570, monday, from, zone)
        assertEquals(at(monday, 480), result)
    }

    @Test
    fun `nextSessionTime 本周同一天已结束则顺延到下一周`() {
        val monday = LocalDate.of(2026, 9, 7)
        val session = CourseSession(
            id = 1L, courseId = "c1", dayOfWeek = 1,
            startSection = 1, endSection = 2, startWeek = 1, endWeek = 16, weekType = WeekType.ALL
        )
        val from = at(monday, 700) // 11:40，已过第 2 节结束
        val result = WeekCalc.nextSessionTime(session, 480, 570, monday, from, zone)
        assertEquals(at(monday.plusDays(7), 480), result)
    }

    @Test
    fun `nextSessionTime 作息缺失返回 null`() {
        val monday = LocalDate.of(2026, 9, 7)
        val session = CourseSession(
            id = 1L, courseId = "c1", dayOfWeek = 1,
            startSection = 1, endSection = 2, startWeek = 1, endWeek = 16, weekType = WeekType.ALL
        )
        assertNull(WeekCalc.nextSessionTime(session, null, null, monday, Instant.now().toEpochMilli(), zone))
    }

    @Test
    fun `nextSessionTime 超出结束周返回 null`() {
        val monday = LocalDate.of(2026, 9, 7)
        val session = CourseSession(
            id = 1L, courseId = "c1", dayOfWeek = 1,
            startSection = 1, endSection = 2, startWeek = 1, endWeek = 2, weekType = WeekType.ALL
        )
        assertNull(WeekCalc.nextSessionTime(session, 480, 570, monday, at(monday.plusDays(30), 400), zone))
    }
}
