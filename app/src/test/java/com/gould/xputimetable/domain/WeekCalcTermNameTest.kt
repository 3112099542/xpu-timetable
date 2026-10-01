/*
 * WeekCalcTermNameTest.kt —— 学期命名规则单测（M9）
 *
 * 背景：M9 起"创建学期"有两个入口（课表页空态、学期设置页），学期名由开学日推定。
 * 该规则原先私有在 TimetableViewModel 里，现在提到 WeekCalc 共用 —— 提到纯函数层
 * 就必须有单测锁住，否则两处入口漂移时只能靠人工发现。
 *
 * 规则：9 月及以后开学 = 秋季第一学期（YYYY-YYYY+1-1）；否则春季第二学期（YYYY-1-YYYY-2）。
 */
package com.gould.xputimetable.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekCalcTermNameTest {

    @Test
    fun `九月一日开学算秋季第一学期`() {
        assertEquals("2026-2027-1", WeekCalc.termNameOf(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `八月开学算春季第二学期（本项目真实开学日 2026-08-24）`() {
        // 注意：8 月属于"9 月之前"，按既有规则归入上一年度的第二学期
        assertEquals("2025-2026-2", WeekCalc.termNameOf(LocalDate.of(2026, 8, 24)))
    }

    @Test
    fun `十二月开学仍算秋季第一学期`() {
        assertEquals("2026-2027-1", WeekCalc.termNameOf(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `二月开学算春季第二学期`() {
        assertEquals("2025-2026-2", WeekCalc.termNameOf(LocalDate.of(2026, 2, 23)))
    }

    @Test
    fun `八月三十一日与九月一日是规则分界`() {
        assertEquals("2025-2026-2", WeekCalc.termNameOf(LocalDate.of(2026, 8, 31)))
        assertEquals("2026-2027-1", WeekCalc.termNameOf(LocalDate.of(2026, 9, 1)))
    }
}
