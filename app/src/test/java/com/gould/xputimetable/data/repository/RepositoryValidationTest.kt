/*
 * RepositoryValidationTest.kt —— 数据层输入校验（2026-09-17 健壮性加固的机器证据）
 *
 * 目的：证明"坏数据不会被静默写进库"。界面已校验一次，这里验证数据层的兜底校验真的生效：
 * 任何调用方（未来可能是导入器或小组件）传入非法数据时，都会明确抛 IllegalArgumentException，
 * 而不是落库成"看着正常但渲染不出来"的脏数据。
 *
 * 断言方式：捕获异常并核对异常类型与提示关键字（提示是给用户看的中文原因）。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.model.WeekType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryValidationTest {

    private val noOpTx = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private fun repo() = TimetableRepositoryImpl(
        courseDao = FakeCourseDao(),
        courseSessionDao = FakeCourseSessionDao(),
        termDao = FakeTermDao(active = null),
        timeSlotDao = FakeTimeSlotDao(),
        importLogDao = FakeImportLogDao(),
        tx = noOpTx,
    )

    private fun course(
        id: String = "c1",
        name: String = "高等数学",
        termId: Long = 1L,
        colorTag: Int = 0,
    ) = Course(
        id = id,
        name = name,
        code = null,
        teacher = null,
        note = null,
        colorTag = colorTag,
        source = CourseSource.MANUAL,
        createdAt = 1L,
        updatedAt = 1L,
        termId = termId,
        editedAt = null,
    )

    private fun session(
        courseId: String = "c1",
        dayOfWeek: Int = 1,
        startSection: Int = 1,
        endSection: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
    ) = CourseSession(
        id = 0L,
        courseId = courseId,
        dayOfWeek = dayOfWeek,
        startSection = startSection,
        endSection = endSection,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = WeekType.ALL,
        classroom = null,
    )

    private suspend fun expectIllegalArgument(keyword: String, block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("应当抛出 IllegalArgumentException（关键字：$keyword）")
        } catch (e: IllegalArgumentException) {
            val message = e.message.orEmpty()
            assertTrue("异常提示应包含「$keyword」，实际：$message", message.contains(keyword))
        }
    }

    @Test
    fun `upsertCourse 拒绝空课程名`() = runTest {
        expectIllegalArgument("课程名") {
            repo().upsertCourse(course(name = "   "), listOf(session()))
        }
    }

    @Test
    fun `upsertCourse 拒绝非法的学期归属`() = runTest {
        expectIllegalArgument("学期") {
            repo().upsertCourse(course(termId = 0L), listOf(session()))
        }
    }

    @Test
    fun `upsertCourse 拒绝 courseId 与课程不一致的安排`() = runTest {
        expectIllegalArgument("不一致") {
            repo().upsertCourse(course(id = "c1"), listOf(session(courseId = "c2")))
        }
    }

    @Test
    fun `upsertCourse 拒绝非法星期`() = runTest {
        expectIllegalArgument("星期") {
            repo().upsertCourse(course(), listOf(session(dayOfWeek = 8)))
        }
    }

    @Test
    fun `upsertCourse 拒绝开始节次晚于结束节次`() = runTest {
        expectIllegalArgument("节次") {
            repo().upsertCourse(course(), listOf(session(startSection = 5, endSection = 2)))
        }
    }

    @Test
    fun `upsertCourse 拒绝起始周晚于结束周`() = runTest {
        expectIllegalArgument("周次") {
            repo().upsertCourse(course(), listOf(session(startWeek = 10, endWeek = 3)))
        }
    }

    @Test
    fun `upsertCourse 接受合法数据并写入`() = runTest {
        val courseDao = FakeCourseDao()
        TimetableRepositoryImpl(
            courseDao = courseDao,
            courseSessionDao = FakeCourseSessionDao(),
            termDao = FakeTermDao(active = null),
            timeSlotDao = FakeTimeSlotDao(),
            importLogDao = FakeImportLogDao(),
            tx = noOpTx,
        ).upsertCourse(course(), listOf(session()))
        assertEquals(1, courseDao.store.size)
    }

    @Test
    fun `updateTimeSlot 拒绝节次号小于 1`() = runTest {
        expectIllegalArgument("节次号") {
            repo().updateTimeSlot(TimeSlot(id = 1L, section = 0, startMinute = 480, endMinute = 520))
        }
    }

    @Test
    fun `updateTimeSlot 拒绝越界分钟数`() = runTest {
        expectIllegalArgument("0..1439") {
            repo().updateTimeSlot(TimeSlot(id = 1L, section = 1, startMinute = 480, endMinute = 1500))
        }
    }

    @Test
    fun `updateTimeSlot 拒绝开始时间不早于结束时间`() = runTest {
        expectIllegalArgument("早于") {
            repo().updateTimeSlot(TimeSlot(id = 1L, section = 1, startMinute = 520, endMinute = 520))
        }
    }
}
