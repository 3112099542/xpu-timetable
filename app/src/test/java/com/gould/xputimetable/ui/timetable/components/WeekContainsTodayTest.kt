/*
 * WeekContainsTodayTest.kt —— 周次守卫的唯一口径验证（M6 需求 5 自证）
 *
 * weekContainsToday 是 WeekGrid 与 DayHeader 共用的"所显示周是否包含今天"判定。
 * 为什么用 2026-08-24：真机激活学期的 start_date，第 4 周 = 09-14~09-20。
 */
package com.gould.xputimetable.ui.timetable.components

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekContainsTodayTest {

    private val startDate = "2026-08-24" // 周一
    private val today = LocalDate.parse("2026-09-18") // 周五，落在第 4 周（09-14~09-20）

    /** 第 4 周包含 2026-09-18 → true（WeekGrid 的"正在上"标记放行）。 */
    @Test
    fun currentWeek_returnsTrue() {
        assertTrue(weekContainsToday(startDate, week = 4, today = today))
    }

    /** 显示第 3 周（上一周）→ false（缺陷 5：跨周误标记的根场景）。 */
    @Test
    fun previousWeek_returnsFalse() {
        assertFalse(weekContainsToday(startDate, week = 3, today = today))
    }

    /** 显示第 5 周（下一周）→ false。 */
    @Test
    fun nextWeek_returnsFalse() {
        assertFalse(weekContainsToday(startDate, week = 5, today = today))
    }

    /** 边界：今天 = 第 1 周周一（学期首日）→ week=1 含（左边界闭）。 */
    @Test
    fun firstMonday_isIncluded() {
        assertTrue(weekContainsToday(startDate, week = 1, today = LocalDate.parse("2026-08-24")))
    }

    /** 边界：今天 = 2026-08-31（第 2 周周一）→ week=1 不含（右边界开）。 */
    @Test
    fun secondMonday_excludedFromFirstWeek() {
        assertFalse(weekContainsToday(startDate, week = 1, today = LocalDate.parse("2026-08-31")))
    }

    /** 起始日 null → false（不标记，不抛异常）。 */
    @Test
    fun nullStartDate_returnsFalse() {
        assertFalse(weekContainsToday(null, week = 4, today = today))
    }

    /** 起始日空白 → false。 */
    @Test
    fun blankStartDate_returnsFalse() {
        assertFalse(weekContainsToday("", week = 4, today = today))
    }

    /** 起始日非法格式 → false（解析失败不抛异常）。 */
    @Test
    fun invalidStartDate_returnsFalse() {
        assertFalse(weekContainsToday("not-a-date", week = 4, today = today))
    }
}
