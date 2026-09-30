/*
 * TimetableRepositoryImplTest.kt —— 仓库层组装逻辑（假 DAO + 假事务，不建真实 SQLite 连接）
 *
 * 覆盖：applyImport 的审计写入、upsertCourse 的先清后写、updateTimeSlot 的校验。
 * M7：findNextClass 随课前提醒功能一并移除，相关用例已删。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.domain.model.WeekType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class TimetableRepositoryImplTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private val noOpTx = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private fun mondayOfThisWeek(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun term(startDate: String) = com.gould.xputimetable.data.db.entity.TermEntity(
        id = 1L,
        name = "2026-2027-1",
        startDate = startDate,
        totalWeeks = 18,
        isActive = true,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun slots() = listOf(
        com.gould.xputimetable.data.db.entity.TimeSlotEntity(
            id = 1L,
            section = 1,
            startMinute = 480,
            endMinute = 520,
        ),
        com.gould.xputimetable.data.db.entity.TimeSlotEntity(
            id = 2L,
            section = 2,
            startMinute = 530,
            endMinute = 570,
        ),
    )

    private fun courseEntity() = CourseEntity(
        id = "c1",
        name = "高数",
        code = null,
        teacher = "张三",
        note = null,
        colorTag = 3,
        source = CourseSource.WEB,
        createdAt = 1L,
        updatedAt = 2L,
        termId = 1L,
        editedAt = null,
    )

    private fun sessionEntity(id: Long = 1L) = CourseSessionEntity(
        id = id,
        courseId = "c1",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = WeekType.ALL.name.uppercase(),
        classroom = "A-301",
    )

    private fun repo(
        courseDao: FakeCourseDao = FakeCourseDao(),
        courseSessionDao: FakeCourseSessionDao = FakeCourseSessionDao(),
        termDao: FakeTermDao = FakeTermDao(active = null),
        timeSlotDao: FakeTimeSlotDao = FakeTimeSlotDao(),
        importLogDao: FakeImportLogDao = FakeImportLogDao(),
    ) = TimetableRepositoryImpl(
        courseDao = courseDao,
        courseSessionDao = courseSessionDao,
        termDao = termDao,
        timeSlotDao = timeSlotDao,
        importLogDao = importLogDao,
        zoneId = zone,
        tx = noOpTx,
    )

    @Test
    fun `applyImport 写入课程与审计并返回摘要`() = runTest {
        val dao = FakeCourseDao()
        val logDao = FakeImportLogDao()
        val repository = repo(courseDao = dao, importLogDao = logDao)

        val schedule = com.gould.xputimetable.domain.model.ParsedSchedule(
            courses = listOf(
                Course(
                    id = "c9",
                    name = "英语",
                    code = null,
                    teacher = null,
                    note = null,
                    colorTag = 2,
                    source = CourseSource.WEB,
                    createdAt = 1L,
                    updatedAt = 1L,
                    termId = 1L,
                    editedAt = null,
                ),
            ),
            sessions = listOf(
                CourseSession(
                    id = 0L,
                    courseId = "c9",
                    dayOfWeek = 2,
                    startSection = 1,
                    endSection = 2,
                    startWeek = 1,
                    endWeek = 16,
                    weekType = WeekType.ALL,
                    classroom = "D-401",
                ),
            ),
            term = com.gould.xputimetable.domain.model.Term(
                id = 1L,
                name = "2026-2027-1",
                startDate = mondayOfThisWeek().toString(),
                totalWeeks = 18,
                isActive = true,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val summary = repository.applyImport(schedule)

        assertTrue(summary.success)
        assertEquals(1, summary.courseCount)
        assertEquals(1, summary.sessionCount)
        assertEquals(1, dao.store.size)
        assertEquals("SUCCESS", logDao.inserted.single().status)
    }

    @Test
    fun `upsertCourse 先写课程再清旧安排后写新安排`() = runTest {
        val courseDao = FakeCourseDao()
        val sessionDao = FakeCourseSessionDao()
        val repository = repo(courseDao = courseDao, courseSessionDao = sessionDao)

        repository.upsertCourse(
            courseEntity().toDomain(),
            listOf(sessionEntity().toDomain()),
        )

        assertEquals(1, courseDao.store.size)
        assertEquals(listOf("c1"), sessionDao.deletedCourseIds)
        assertEquals(1, sessionDao.insertedAll.size)
    }

    @Test
    fun `updateTimeSlot 分钟数越界抛异常`() = runTest {
        try {
            repo().updateTimeSlot(
                com.gould.xputimetable.domain.model.TimeSlot(id = 1L, section = 1, startMinute = -1, endMinute = 520),
            )
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 期望路径
        }
    }

    @Test
    fun `updateTimeSlot 开始时间不早于结束时间抛异常`() = runTest {
        try {
            repo().updateTimeSlot(
                com.gould.xputimetable.domain.model.TimeSlot(id = 1L, section = 1, startMinute = 520, endMinute = 480),
            )
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 期望路径
        }
    }
}
