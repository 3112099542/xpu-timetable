/*
 * MappersTest.kt —— 实体与领域模型往返不丢字段
 *
 * 重点：新增字段（editedAt 等）必须被往返保留，否则静默丢数据。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.dao.SessionWithCoursePojo
import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `Course 往返闭合（含 termId 与 editedAt）`() {
        val domain = Course(
            id = "c1", name = "高等数学", code = "080101", teacher = "张三", note = null,
            colorTag = 3, source = CourseSource.MANUAL, createdAt = 1000L, updatedAt = 2000L,
            termId = 7L, editedAt = 3000L
        )
        assertEquals(domain, domain.toEntity().toDomain())

        val neverEdited = Course(
            id = "c2", name = "线代", code = null, teacher = null, note = null,
            colorTag = 0, source = CourseSource.MANUAL, createdAt = 1L, updatedAt = 1L, termId = 1L, editedAt = null
        )
        assertEquals(neverEdited, neverEdited.toEntity().toDomain())

        val entity = CourseEntity(
            id = "c3", name = "英语", code = null, teacher = "李四", note = "备注", colorTag = 5,
            source = CourseSource.WEB, createdAt = 1L, updatedAt = 1L, termId = 2L, editedAt = null
        )
        assertEquals(entity, entity.toDomain().toEntity())
        assertNull(entity.toDomain().editedAt)

        val session = CourseSession(
            id = 1L, courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = WeekType.ALL, classroom = "A-301"
        )
        assertEquals(session, session.toEntity().toDomain())

        assertEquals(WeekType.ODD, CourseSession(
            id = 2L, courseId = "c1", dayOfWeek = 3, startSection = 5, endSection = 6,
            startWeek = 2, endWeek = 16, weekType = WeekType.ODD, classroom = "B-202"
        ).toEntity().toDomain().weekType)

        assertEquals(WeekType.EVEN, CourseSession(
            id = 3L, courseId = "c1", dayOfWeek = 5, startSection = 3, endSection = 4,
            startWeek = 9, endWeek = 16, weekType = WeekType.EVEN, classroom = "C-303"
        ).toEntity().toDomain().weekType)

        val term = Term(
            id = 1L, name = "2026-2027-1", startDate = "2026-09-07", totalWeeks = 18,
            isActive = true, createdAt = 1L, updatedAt = 1L
        )
        assertEquals(term, term.toEntity().toDomain())

        val slot = TimeSlot(
            id = 1L, section = 1, startMinute = 480, endMinute = 520
        )
        assertEquals(slot, slot.toEntity().toDomain())

        val pojo = SessionWithCoursePojo(
            session = CourseSession(
                id = 1L, courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
                startWeek = 1, endWeek = 16, weekType = WeekType.ALL, classroom = "A-301"
            ).toEntity(),
            courseName = "高等数学", colorTag = 3
        )
        assertEquals("高等数学", pojo.toDomain().courseName)
        assertEquals(3, pojo.toDomain().colorTag)

        // week_type 写入侧一律大写（与 SQL 的 UPPER() 配套）
        assertEquals("ODD", CourseSession(
            id = 0L, courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = WeekType.ODD, classroom = null
        ).toEntity().weekType)

        assertEquals("EVEN", CourseSession(
            id = 0L, courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = WeekType.EVEN, classroom = null
        ).toEntity().weekType)

        assertEquals("ALL", CourseSession(
            id = 0L, courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = WeekType.ALL, classroom = null
        ).toEntity().weekType)
    }
}
