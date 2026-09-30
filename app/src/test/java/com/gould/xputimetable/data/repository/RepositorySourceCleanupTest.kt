/*
 * RepositorySourceCleanupTest.kt —— 按来源清理的仓库级单测（Spec M2-C §2.3，AC-24）
 *
 * 用既有 Fakes.kt 假 DAO。安全不变量（与 AC-20 同源）：
 *   - 删 WEB 后 MANUAL 仍在、CSV 仍在；
 *   - 再删 CSV 后 MANUAL 仍在；
 *   - 返回值为删除数；source=MANUAL 时返回 0 且一条不删；
 *   - 只影响目标学期（别学期同来源不算）。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.TermEntity
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorySourceCleanupTest {

    private val courseDao = FakeCourseDao()

    private val repository: TimetableRepository = TimetableRepositoryImpl(
        courseDao = courseDao,
        courseSessionDao = FakeCourseSessionDao(),
        termDao = FakeTermDao(
            active = TermEntity(
                id = 1L, name = "2026-2027-1", startDate = "2026-09-07",
                totalWeeks = 18, isActive = true, createdAt = 1L, updatedAt = 1L,
            ),
        ),
        timeSlotDao = FakeTimeSlotDao(),
        importLogDao = FakeImportLogDao(),
        tx = object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        },
    )

    private fun course(id: String, source: String, termId: Long = 1L): CourseEntity =
        CourseEntity(
            id = id, name = "课$id", code = null, teacher = null, note = null,
            colorTag = 0, source = source, createdAt = 1L, updatedAt = 1L,
            termId = termId, editedAt = null,
        )

    @Test
    fun `删WEB后MANUAL与CSV仍在_返回删除数`() = runTest {
        courseDao.insertAll(
            listOf(
                course("web-1", CourseSource.WEB),
                course("web-2", CourseSource.WEB),
                course("csv-1", CourseSource.WAKEUP_CSV),
                course("manual-1", CourseSource.MANUAL),
            ),
        )

        val deleted = repository.deleteCoursesByTermAndSource(1L, CourseSource.WEB)

        assertEquals(2, deleted)
        val remaining = courseDao.store.map { it.id }.toSet()
        assertEquals(setOf("csv-1", "manual-1"), remaining)
        // DAO 的删除入口确实被调用（真实库中安排经 ON DELETE CASCADE 级联清理，
        // Fake 不模拟级联，此处以调用记录为证据）
        assertEquals(listOf(1L to CourseSource.WEB), courseDao.deletedByTermAndSource)
    }

    @Test
    fun `再删CSV后MANUAL仍在`() = runTest {
        courseDao.insertAll(
            listOf(
                course("csv-1", CourseSource.WAKEUP_CSV),
                course("csv-2", CourseSource.WAKEUP_CSV),
                course("manual-1", CourseSource.MANUAL),
            ),
        )
        assertEquals(2, repository.deleteCoursesByTermAndSource(1L, CourseSource.WAKEUP_CSV))
        // 二次删除已无数据：返回 0，且 MANUAL 未受影响
        assertEquals(0, repository.deleteCoursesByTermAndSource(1L, CourseSource.WAKEUP_CSV))
        assertEquals(setOf("manual-1"), courseDao.store.map { it.id }.toSet())
    }

    @Test
    fun `source为MANUAL时返回0且一条不删`() = runTest {
        courseDao.insertAll(listOf(course("manual-1", CourseSource.MANUAL)))
        assertEquals(0, repository.deleteCoursesByTermAndSource(1L, CourseSource.MANUAL))
        assertEquals(setOf("manual-1"), courseDao.store.map { it.id }.toSet())
    }

    @Test
    fun `只删目标学期_别学期同来源不动`() = runTest {
        courseDao.insertAll(
            listOf(
                course("web-this", CourseSource.WEB, termId = 1L),
                course("web-other", CourseSource.WEB, termId = 2L),
            ),
        )
        assertEquals(1, repository.deleteCoursesByTermAndSource(1L, CourseSource.WEB))
        assertEquals(setOf("web-other"), courseDao.store.map { it.id }.toSet())
    }

    @Test
    fun `无该来源课程时返回0`() = runTest {
        assertEquals(0, repository.deleteCoursesByTermAndSource(1L, CourseSource.WEB))
        assertTrue(courseDao.store.isEmpty())
    }
}
