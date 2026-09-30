/*
 * WeekCalcEdgeCaseTest.kt —— WeekCalc 脏数据边界（2026-09-17 健壮性加固的机器证据）
 *
 * 目的：证明 nextSessionTime 面对越界/矛盾数据时**返回 null 而不是崩溃或长时间空转**。
 * 这类数据在真实场景里并不罕见：导入的作息表可能缺项、手工改库可能写出矛盾区间。
 */
package com.gould.xputimetable.domain

import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.time.ZonedDateTime

class WeekCalcEdgeCaseTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    private fun session(
        dayOfWeek: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 16,
        weekType: WeekType = WeekType.ALL,
    ) = CourseSession(
        id = 1L,
        courseId = "c1",
        dayOfWeek = dayOfWeek,
        startSection = 1,
        endSection = 2,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
    )

    private fun at(day: LocalDate, minute: Int): Long =
        ZonedDateTime.of(day, LocalTime.of(minute / 60, minute % 60), zone).toInstant().toEpochMilli()

    @Test
    fun `作息分钟数越界时返回 null`() {
        val from = at(monday, 400)
        assertNull(WeekCalc.nextSessionTime(session(), 2000, 2100, monday, from, zone))
    }

    @Test
    fun `作息结束时间不晚于开始时间时返回 null`() {
        val from = at(monday, 400)
        assertNull(WeekCalc.nextSessionTime(session(), 600, 600, monday, from, zone))
        assertNull(WeekCalc.nextSessionTime(session(), 600, 500, monday, from, zone))
    }

    @Test
    fun `星期越界时返回 null`() {
        val from = at(monday, 400)
        assertNull(WeekCalc.nextSessionTime(session(dayOfWeek = 0), 480, 570, monday, from, zone))
        assertNull(WeekCalc.nextSessionTime(session(dayOfWeek = 8), 480, 570, monday, from, zone))
    }

    @Test
    fun `起始周晚于结束周的矛盾数据返回 null`() {
        val from = at(monday, 400)
        assertNull(WeekCalc.nextSessionTime(session(startWeek = 10, endWeek = 3), 480, 570, monday, from, zone))
    }

    @Test
    fun `超大结束周不会让计算失控（按上限裁剪后返回 null 而不是无限扫描）`() {
        val farFuture = at(monday.plusDays(3650), 400) // 10 年后，早已超出 60 周上限
        assertNull(WeekCalc.nextSessionTime(session(endWeek = 9999), 480, 570, monday, farFuture, zone))
    }

    @Test
    fun `正常数据仍然照常返回（确保加固没有误伤）`() {
        val from = at(monday, 400)
        assertNotNull(WeekCalc.nextSessionTime(session(), 480, 570, monday, from, zone))
    }
}
