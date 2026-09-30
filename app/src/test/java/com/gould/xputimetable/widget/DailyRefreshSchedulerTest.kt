/*
 * DailyRefreshSchedulerTest.kt —— 跨天刷新闹钟的纯函数验证（M6 需求 2 自证）
 *
 * 只测 nextRefreshEpochMilli（纯计算，注入 now，不依赖 Android）。
 * 期望值用同机 systemDefault 时区换算成 epoch 比对（函数实现同样用 systemDefault）。
 */
package com.gould.xputimetable.widget

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRefreshSchedulerTest {

    private fun epochOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun assertNext(now: LocalDateTime, expected: LocalDateTime) {
        assertEquals(epochOf(expected), DailyRefreshScheduler.nextRefreshEpochMilli(now))
    }

    /** 当天 00:00 → 当天 00:05（还没到目标时刻，排今天）。 */
    @Test
    fun next_atMidnight_returnsTodayTarget() {
        assertNext(
            LocalDateTime.parse("2026-09-19T00:00:00"),
            LocalDateTime.parse("2026-09-19T00:05:00"),
        )
    }

    /** 当天 00:04:59 → 当天 00:05（差一秒仍属"今天未到"）。 */
    @Test
    fun next_oneSecondBefore_returnsTodayTarget() {
        assertNext(
            LocalDateTime.parse("2026-09-19T00:04:59"),
            LocalDateTime.parse("2026-09-19T00:05:00"),
        )
    }

    /** 边界：now == 00:05 整 → 次日 00:05（isBefore 为 false，避免刚过就立刻又触发）。 */
    @Test
    fun next_exactlyAtTarget_returnsTomorrowTarget() {
        assertNext(
            LocalDateTime.parse("2026-09-19T00:05:00"),
            LocalDateTime.parse("2026-09-20T00:05:00"),
        )
    }

    /** 白天任意时刻 → 次日 00:05。 */
    @Test
    fun next_atNoon_returnsTomorrowTarget() {
        assertNext(
            LocalDateTime.parse("2026-09-19T12:00:00"),
            LocalDateTime.parse("2026-09-20T00:05:00"),
        )
    }

    /** 当天 23:59:59 → 次日 00:05。 */
    @Test
    fun next_lastSecondOfDay_returnsTomorrowTarget() {
        assertNext(
            LocalDateTime.parse("2026-09-19T23:59:59"),
            LocalDateTime.parse("2026-09-20T00:05:00"),
        )
    }

    /** 跨月/跨年：12-31 深夜 → 次年 01-01 00:05。 */
    @Test
    fun next_acrossYearBoundary_returnsNextYearTarget() {
        assertNext(
            LocalDateTime.parse("2026-12-31T23:59:00"),
            LocalDateTime.parse("2027-01-01T00:05:00"),
        )
    }
}
