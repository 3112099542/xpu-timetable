/*
 * RepositoryTermScopeTest.kt —— 跨学期隔离
 *
 * 目的：验证「不同学期的课程互不串台——周视图 JOIN 只返回目标学期的安排（架构 §7.3 的 c.term_id 过滤）。
 * 假 DAO 模拟 SQL 的 INNER JOIN + term_id 过滤；真实 SQL 语义由团队领导用 schema DDL + sqlite3 夹具另行验证。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.WeekType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryTermScopeTest {

    private fun course(id: String, name: String, termId: Long) = CourseEntity(
        id = id,
        name = name,
        code = null,
        teacher = null,
        note = null,
        colorTag = 0,
        source = CourseSource.WEB,
        createdAt = 1L,
        updatedAt = 1L,
        termId = termId,
        editedAt = null,
    )

    @Test
    fun `observeWeekSessions 的跨学期课程互不串台`() = runTest {
        val courses = listOf(
            course(id = "c1", name = "本学期的课", termId = 1L),
            course(id = "c2", name = "上学期的课", termId = 2L),
        )
        val sessions = listOf(
            CourseSessionEntity(
                id = 1L,
                courseId = "c1",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                startWeek = 1,
                endWeek = 16,
                weekType = WeekType.ALL.name.uppercase(),
                classroom = "A-101",
            ),
            CourseSessionEntity(
                id = 2L,
                courseId = "c2",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                startWeek = 1,
                endWeek = 16,
                weekType = WeekType.ALL.name.uppercase(),
                classroom = "B-202",
            ),
        )

        val dao = FakeCourseSessionDao(sessions = sessions, courses = courses)

        val week1 = dao.observeWeekSessions(termId = 1L, week = 1).first()
        assertEquals(1, week1.size)
        assertEquals("本学期的课", week1.single().courseName)
        assertEquals("A-101", week1.single().session.classroom)

        val week2 = dao.observeWeekSessions(termId = 2L, week = 1).first()
        assertEquals("上学期的课", week2.single().courseName)
    }
}
