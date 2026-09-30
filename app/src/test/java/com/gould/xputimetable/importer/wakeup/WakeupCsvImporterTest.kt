/*
 * WakeupCsvImporterTest.kt —— 文件导入通道的两段式语义测试（AC-10/11/13）
 *
 * 复用 data/repository/Fakes.kt 的假 DAO（规格 §8：不新建第二套、不引 Robolectric/MockK）。
 * 断言重点：
 *   - import() 成功只返回 NeedsConfirm，**不写库**（AC-10 前半段）；
 *   - import() 失败保留 retainedPayload（AC-11 不丢已拦截数据）；
 *   - commit() 走 applyImport：落库数量、source=WAKEUP_CSV、同文件重复 commit 不累积（AC-13 覆盖语义）。
 */
package com.gould.xputimetable.importer.wakeup

import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.TermEntity
import com.gould.xputimetable.data.repository.FakeCourseDao
import com.gould.xputimetable.data.repository.FakeCourseSessionDao
import com.gould.xputimetable.data.repository.FakeImportLogDao
import com.gould.xputimetable.data.repository.FakeTermDao
import com.gould.xputimetable.data.repository.FakeTimeSlotDao
import com.gould.xputimetable.data.repository.TransactionRunner
import com.gould.xputimetable.data.repository.TimetableRepositoryImpl
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.wakeup.WakeupCsvParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeupCsvImporterTest {

    private val courseDao = FakeCourseDao()
    private val sessionDao = FakeCourseSessionDao()
    private val logDao = FakeImportLogDao()

    private val repository: TimetableRepository = TimetableRepositoryImpl(
        courseDao = courseDao,
        courseSessionDao = sessionDao,
        termDao = FakeTermDao(
            active = TermEntity(
                id = 1L, name = "2026-2027-1", startDate = "2026-09-07",
                totalWeeks = 18, isActive = true, createdAt = 1L, updatedAt = 1L,
            ),
        ),
        timeSlotDao = FakeTimeSlotDao(),
        importLogDao = logDao,
        tx = object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        },
    )

    private val importer = WakeupCsvImporter(
        parser = WakeupCsvParser(nowMillis = { FIXED_NOW }),
        repository = repository,
    )

    @Test
    fun `import 成功返回 NeedsConfirm 且不写库`() = runTest {
        val result = importer.import(
            ImportPayload(text = SAMPLE_CSV, uri = "content://x", displayName = "课表.csv"),
        )
        assertTrue(result is ImportResult.NeedsConfirm)
        val needsConfirm = result as ImportResult.NeedsConfirm
        assertEquals(1, needsConfirm.courseCount)
        assertEquals(1, needsConfirm.sessionCount)
        // AC-10：确认之前不允许有任何写入
        assertTrue(courseDao.store.isEmpty())
        assertTrue(courseDao.inserted.isEmpty())
        assertTrue(sessionDao.insertedAll.isEmpty())
        assertTrue(logDao.inserted.isEmpty())
    }

    @Test
    fun `import 解析失败返回 Failure 且保留 retainedPayload`() = runTest {
        val payload = ImportPayload(text = "随便,一,堆\n1,2,3", uri = "content://x", displayName = "坏文件.csv")
        val result = importer.import(payload)
        assertTrue(result is ImportResult.Failure)
        val failure = result as ImportResult.Failure
        assertTrue(failure.error is ParseError.SchemaMismatch)
        // AC-11：已拦截数据不丢失，可据此引导换文件/重试
        assertEquals(payload.text, failure.retainedPayload.text)
        assertEquals("坏文件.csv", failure.retainedPayload.displayName)
    }

    @Test
    fun `import 空文本返回 EmptyPayload 且保留载荷`() = runTest {
        val payload = ImportPayload(text = "", uri = "content://x", displayName = "空.csv")
        val result = importer.import(payload)
        val failure = result as ImportResult.Failure
        assertEquals(ParseError.EmptyPayload, failure.error)
        assertEquals("空.csv", failure.retainedPayload.displayName)
    }

    @Test
    fun `commit 走 applyImport：课程落库且 source 为 WAKEUP_CSV`() = runTest {
        val needsConfirm = importer.import(
            ImportPayload(text = SAMPLE_CSV, displayName = "课表.csv"),
        ) as ImportResult.NeedsConfirm

        val summary = importer.commit(needsConfirm.parsed)

        assertEquals(1, summary.courseCount)
        assertEquals(1, summary.sessionCount)
        assertEquals(1, courseDao.store.size)
        val stored = courseDao.store.single()
        assertEquals(CourseSource.WAKEUP_CSV, stored.source)
        assertEquals("高数", stored.name)
        assertEquals(1L, stored.termId) // 钉死到激活学期
        assertEquals(1, sessionDao.insertedAll.size)
        assertEquals("SUCCESS", logDao.inserted.single().status)
    }

    @Test
    fun `同文件重复 commit：按来源整体替换，不累积重复`() = runTest {
        val needsConfirm = importer.import(ImportPayload(text = SAMPLE_CSV)) as ImportResult.NeedsConfirm
        importer.commit(needsConfirm.parsed)
        val second = importer.commit(needsConfirm.parsed)

        assertEquals(1, courseDao.store.count { it.source == CourseSource.WAKEUP_CSV })
        assertEquals(1, second.courseCount)
        assertEquals(1, second.replacedCount) // 第二次导入替换了第一次的 1 门
    }

    @Test
    fun `手动课程不被 commit 删除（AC-20，经 applyImport 统一保证）`() = runTest {
        courseDao.store += CourseEntity(
            id = "man-1", name = "手动课", code = null, teacher = null, note = null,
            colorTag = 0, source = CourseSource.MANUAL, createdAt = 1L, updatedAt = 1L,
            termId = 1L, editedAt = null,
        )
        val needsConfirm = importer.import(ImportPayload(text = SAMPLE_CSV)) as ImportResult.NeedsConfirm
        val summary = importer.commit(needsConfirm.parsed)

        assertTrue(courseDao.store.any { it.id == "man-1" && it.source == CourseSource.MANUAL })
        assertEquals(1, summary.preservedManualCount)
    }

    companion object {
        private const val FIXED_NOW = 1_700_000_000_000L
        private const val SAMPLE_CSV =
            "课程名,教师,教室,星期,开始节次,结束节次,周次,单双周\n" +
                "高数,张三,教1-101,星期一,1,2,1-16,每周"
    }
}
