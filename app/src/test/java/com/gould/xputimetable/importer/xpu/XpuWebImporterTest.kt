/*
 * XpuWebImporterTest.kt —— 教务直连通道的两段式语义测试（Spec M2-B §6）
 *
 * 复用 data/repository/Fakes.kt 的假 DAO（规格约束：不新建第二套、不引 Robolectric/MockK）。
 * 数据源：脱敏金样本 sample_schedule.json（黄金值：9 门课 / 16 条安排 / 2 条显式周次）。
 * 断言重点：
 *   - import() 只解析不写库（AC-10 前半段）；
 *   - import() 失败保留 retainedPayload（AC-11 不丢已拦截数据）；
 *   - commit() 走 applyImport：courses 9 行、course_sessions 16 行、source=WEB（AC-13）；
 *   - MANUAL 课程不被删除（AC-20）；重复 commit 不累积（覆盖语义）。
 */
package com.gould.xputimetable.importer.xpu

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpuWebImporterTest {

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

    private val importer = XpuWebImporter(repository)

    /** 真实拦截形态：uri 是 print-data URL（semesterId=147 从中提取参与稳定 id）。 */
    private val payload = ImportPayload(
        text = fixture(),
        uri = "https://jwglxt.xpu.edu.cn/student/for-std/course-table/semester/147/print-data?semesterId=147&hasExperiment=true",
    )

    @Test
    fun `import 成功返回 NeedsConfirm 且不写库`() = runTest {
        val result = importer.import(payload)
        assertTrue(result is ImportResult.NeedsConfirm)
        val needsConfirm = result as ImportResult.NeedsConfirm
        assertEquals(9, needsConfirm.courseCount)
        assertEquals(16, needsConfirm.sessionCount)
        // AC-10：确认之前不允许有任何写入
        assertTrue(courseDao.store.isEmpty())
        assertTrue(courseDao.inserted.isEmpty())
        assertTrue(sessionDao.insertedAll.isEmpty())
        assertTrue(logDao.inserted.isEmpty())
    }

    @Test
    fun `import 解析失败返回 Failure 且保留 retainedPayload`() = runTest {
        val result = importer.import(ImportPayload(text = "not-json", uri = "https://x"))
        assertTrue(result is ImportResult.Failure)
        val failure = result as ImportResult.Failure
        assertTrue(failure.error is ParseError.SchemaMismatch)
        assertEquals("not-json", failure.retainedPayload.text)
    }

    @Test
    fun `commit 落库 9 门课程 16 条安排 source=WEB`() = runTest {
        val needsConfirm = importer.import(payload) as ImportResult.NeedsConfirm
        val summary = importer.commit(needsConfirm.parsed)

        assertTrue(summary.success)
        assertEquals(9, summary.courseCount)
        assertEquals(16, summary.sessionCount)
        assertEquals(9, courseDao.store.size)
        assertEquals(16, sessionDao.insertedAll.size)
        assertTrue(courseDao.store.all { it.source == CourseSource.WEB })
        // termId 被钉死到目标学期（激活学期 id=1）
        assertTrue(courseDao.store.all { it.termId == 1L })
        // 审计日志已写
        assertEquals(1, logDao.inserted.size)
        assertEquals("SUCCESS", logDao.inserted.first().status)
    }

    @Test
    fun `commit 不删除 MANUAL 课程`() = runTest {
        courseDao.insert(
            CourseEntity(
                id = "manual-1", name = "自习课", code = null, teacher = null, note = null,
                colorTag = 0, source = CourseSource.MANUAL,
                createdAt = 1L, updatedAt = 1L, termId = 1L, editedAt = 1L,
            ),
        )
        val needsConfirm = importer.import(payload) as ImportResult.NeedsConfirm
        val summary = importer.commit(needsConfirm.parsed)

        // AC-20：手动课程仍在库里，且摘要给出保留计数
        assertTrue(courseDao.store.any { it.id == "manual-1" && it.source == CourseSource.MANUAL })
        assertEquals(1, summary.preservedManualCount)
        assertEquals(9, summary.courseCount)
    }

    @Test
    fun `重复 commit 同一数据不累积（覆盖语义）`() = runTest {
        val needsConfirm = importer.import(payload) as ImportResult.NeedsConfirm
        importer.commit(needsConfirm.parsed)
        importer.commit(needsConfirm.parsed)
        // 第二次是按来源整体替换：courses 仍是 9 门（同 id 覆盖），不是 18
        assertEquals(9, courseDao.store.size)
        // 写入侧每次都是全量 9 门（共 18 次插入记录），库里却只有 9 行
        assertEquals(18, courseDao.inserted.size)
        assertEquals(32, sessionDao.insertedAll.size)
    }

    private companion object {
        internal fun fixture(): String =
            XpuWebImporterTest::class.java.classLoader!!
                .getResourceAsStream("parser/xpu/fixtures/sample_schedule.json")!!
                .readBytes().decodeToString()
    }
}
