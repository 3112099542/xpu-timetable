/*
 * WeekGridOverlapTest.kt —— 区间重叠分组纯逻辑单测（Spec M2-C §1.4）
 *
 * 断言：同一组内两两相交（含 span>1 的跨节连排按区间判重叠）；不同组互不相交；
 * 输入为空时无分组。
 */
package com.gould.xputimetable.ui.timetable.components

import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekGridOverlapTest {

    private fun item(courseId: String, start: Int, end: Int): SessionWithCourse = SessionWithCourse(
        courseName = courseId,
        colorTag = 0,
        session = CourseSession(
            courseId = courseId,
            dayOfWeek = 1,
            startSection = start,
            endSection = end,
            startWeek = 1,
            endWeek = 18,
            weekType = WeekType.ALL,
            weeks = null,
            classroom = null,
        ),
    )

    private fun List<List<SessionWithCourse>>.assertGroupsValid() {
        for (group in this) {
            // 组内不变量是**连通分量**语义（Spec §1.2「有交集 ⇒ 同组」的传递闭包）：
            // 按 start 排序后，每个成员与前面成员的并集有交（链式如 A1-2/B2-3/C3-4
            // 中 A 与 C 不相交但仍属同组——布局上它们经由 B 连成一坨，必须同组处理）。
            val sorted = group.sortedBy { it.session.startSection }
            var reach = sorted.first().session.endSection
            for (i in 1 until sorted.size) {
                val s = sorted[i].session.startSection
                assertTrue("组内第 $i 个成员与前方并集应相交", s <= reach)
                reach = maxOf(reach, sorted[i].session.endSection)
            }
        }
        for (g1 in indices) for (g2 in g1 + 1 until size) {
            for (a in this[g1]) for (b in this[g2]) {
                assertTrue(
                    "跨组 ${a.session.courseId} 与 ${b.session.courseId} 不应相交",
                    a.session.startSection > b.session.endSection || b.session.startSection > a.session.endSection,
                )
            }
        }
    }

    @Test
    fun `空输入无分组`() {
        assertTrue(groupOverlapping(emptyList()).isEmpty())
    }

    @Test
    fun `互不重叠的安排各自成组`() {
        val groups = groupOverlapping(listOf(item("A", 1, 2), item("B", 3, 4), item("C", 5, 6)))
        assertEquals(3, groups.size)
        groups.assertGroupsValid()
    }

    @Test
    fun `同格两门课成一组`() {
        val groups = groupOverlapping(listOf(item("A", 1, 2), item("B", 1, 2)))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
        groups.assertGroupsValid()
    }

    @Test
    fun `跨节连排按区间判重叠_不能只比startSection`() {
        // A(1-2) 与 B(2-3)：startSection 不同（1 vs 2）但第 2 节相撞 ⇒ 同组
        val groups = groupOverlapping(listOf(item("A", 1, 2), item("B", 2, 3)))
        assertEquals(1, groups.size)
        groups.assertGroupsValid()
    }

    @Test
    fun `真机缺陷场景_三来源同格加相邻课`() {
        // 周一：高数(1-2) + 高等数学(1-2) + 马原(1-2) 互相重叠；英语(4-5) 独立
        val groups = groupOverlapping(
            listOf(
                item("高数MANUAL", 1, 2),
                item("高等数学CSV", 1, 2),
                item("马原WEB", 1, 2),
                item("英语", 4, 5),
            ),
        )
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].size)
        assertEquals(1, groups[1].size)
        groups.assertGroupsValid()
    }

    @Test
    fun `链式重叠_并入同组`() {
        // A(1-2) B(2-3) C(3-4)：B 与 A、C 都相交 ⇒ 三条全在一组（连通分量语义）
        val groups = groupOverlapping(listOf(item("A", 1, 2), item("B", 2, 3), item("C", 3, 4)))
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
        groups.assertGroupsValid()
    }

    @Test
    fun `长卡横跨短卡_区间重叠`() {
        // D(1-6) 覆盖 A(2-3) 与 B(5-6)：三条连通
        val groups = groupOverlapping(listOf(item("A", 2, 3), item("D", 1, 6), item("B", 5, 6)))
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
        groups.assertGroupsValid()
    }

    @Test
    fun `乱序输入_分组稳定`() {
        val shuffled = listOf(item("C", 5, 6), item("A", 1, 2), item("B", 3, 4))
        val groups = groupOverlapping(shuffled)
        assertEquals(3, groups.size)
        // 组内按 startSection 排序：A 组在最前
        assertEquals("A", groups[0][0].session.courseId)
        groups.assertGroupsValid()
    }
}
