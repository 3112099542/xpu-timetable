/*
 * ImportSourceScopeTest.kt —— 导入的「覆盖范围」语义测试（Spec AC-13 / AC-20 / AC-21）
 *
 * 背景（M2）：原 Spec 只写"覆盖"，未定义范围。若实现成整学期清空 → 静默删除用户课程（不可逆）。
 *   当时的裁决是按来源整体替换，source = MANUAL 一律保留。
 *
 * M7 需求 1 修订：导入改为用户二选一（ImportMode.REPLACE / APPEND），"覆盖范围"随之明确：
 *   - REPLACE（覆盖原课表）：清空本学期**全部非手动**来源，用本次内容重建。
 *     修订理由：多通道混用后，"只清同来源"会让别的通道的旧数据留下来、与本次内容同名并列
 *     （真机实测：教务导入 9 门 + 文件导入 9 门，内容完全相同却各占一份）。
 *     这不违背 M2 的初衷——当年担心的是"静默"删除，而现在清空规模会在预览页明示，
 *     且是用户主动选择「覆盖原课表」的结果；MANUAL 双防线依旧不动。
 *   - APPEND（插入原课表）：不清任何东西，本次内容追加在后。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.WeekType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSourceScopeTest {

    private val termId = 1L

    private fun course(
        id: String,
        name: String,
        source: String,
    ) = CourseEntity(
        id = id,
        name = name,
        code = null,
        teacher = null,
        note = null,
        colorTag = 0,
        source = source,
        createdAt = 1L,
        updatedAt = 1L,
        termId = termId,
        editedAt = null,
    )

    private fun scheduleOf(vararg courses: Course) = ParsedSchedule(
        courses = courses.toList(),
        sessions = emptyList(),
        term = Term(
            id = termId,
            name = "2026-2027-1",
            startDate = "2026-09-07",
            totalWeeks = 18,
            isActive = true,
            createdAt = 1L,
            updatedAt = 1L,
        ),
    )

    private fun repo(courseDao: FakeCourseDao, logDao: FakeImportLogDao = FakeImportLogDao()) = TimetableRepositoryImpl(
        courseDao = courseDao,
        courseSessionDao = FakeCourseSessionDao(),
        termDao = FakeTermDao(active = null),
        timeSlotDao = FakeTimeSlotDao(),
        importLogDao = logDao,
        tx = object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        },
    )

    @Test
    fun `同源重复导入：旧记录被整体替换，store 中无重复累积`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "old-1", name = "旧高数", source = CourseSource.WEB),
                course(id = "old-2", name = "旧线代", source = CourseSource.WEB),
            ),
        )
        val summary = repo(dao).applyImport(scheduleOf(course(id = "new-1", name = "新高数", source = CourseSource.WEB).toDomain()))

        assertEquals(2, summary.replacedCount)
        assertEquals(listOf("new-1"), dao.store.map { it.id })
        assertEquals(1, summary.courseCount)
    }

    @Test
    fun `同源重复导入：MANUAL 课程仍在，且计数来自真实查询`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "web-1", name = "教务课", source = CourseSource.WEB),
                course(id = "man-1", name = "手动课", source = CourseSource.MANUAL),
            ),
        )
        val summary = repo(dao).applyImport(scheduleOf(course(id = "web-2", name = "教务新课", source = CourseSource.WEB).toDomain()))

        assertTrue("手动课程必须仍在", dao.store.any { it.id == "man-1" && it.source == CourseSource.MANUAL })
        assertEquals(1, summary.preservedManualCount)
        assertEquals(setOf("man-1", "web-2"), dao.store.map { it.id }.toSet())
    }

    @Test
    fun `覆盖模式：清空本学期全部非手动来源，用本次内容重建`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "web-1", name = "教务课", source = CourseSource.WEB),
                course(id = "csv-1", name = "文件课", source = CourseSource.WAKEUP_CSV),
                course(id = "man-1", name = "手动课", source = CourseSource.MANUAL),
            ),
        )
        // 默认模式即 REPLACE，此处显式传入使意图明确
        val summary = repo(dao).applyImport(
            scheduleOf(course(id = "web-2", name = "教务新课", source = CourseSource.WEB).toDomain()),
            ImportMode.REPLACE,
        )

        // 跨来源的旧导入课程都被清掉——这正是"教务 9 门 + 文件 9 门重复"的解法
        assertEquals(2, summary.replacedCount)
        assertEquals(setOf("man-1", "web-2"), dao.store.map { it.id }.toSet())
    }

    @Test
    fun `插入模式：不动任何现有课程，本次内容追加在后`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "web-1", name = "教务课", source = CourseSource.WEB),
                course(id = "man-1", name = "手动课", source = CourseSource.MANUAL),
            ),
        )
        val summary = repo(dao).applyImport(
            scheduleOf(course(id = "new-1", name = "新增课", source = CourseSource.FILE_JSON).toDomain()),
            ImportMode.APPEND,
        )

        assertEquals(0, summary.replacedCount) // 插入模式不替换任何东西
        assertEquals(setOf("web-1", "man-1", "new-1"), dao.store.map { it.id }.toSet())
    }

    @Test
    fun `插入模式：MANUAL 同样不动，且计数来自真实查询`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(course(id = "man-1", name = "手动课", source = CourseSource.MANUAL)),
        )
        val summary = repo(dao).applyImport(
            scheduleOf(course(id = "new-1", name = "导入课", source = CourseSource.WEB).toDomain()),
            ImportMode.APPEND,
        )

        assertTrue("手动课程必须仍在", dao.store.any { it.id == "man-1" })
        assertEquals(1, summary.preservedManualCount)
    }

    @Test
    fun `同名冲突：写入 suspectedDuplicateNames，且两者并存（不自动合并）`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(course(id = "man-1", name = "高等数学", source = CourseSource.MANUAL)),
        )
        val summary = repo(dao).applyImport(scheduleOf(course(id = "web-1", name = "高等数学", source = CourseSource.WEB).toDomain()))

        assertEquals(listOf("高等数学"), summary.suspectedDuplicateNames)
        assertEquals(2, dao.store.count { it.name == "高等数学" })
    }

    @Test
    fun `同 id 冲突：用户编辑过的课程不会被导入版本覆盖`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "c1", name = "高等数学（用户改过的教室）", source = CourseSource.MANUAL),
            ),
        )
        val summary = repo(dao).applyImport(scheduleOf(course(id = "c1", name = "高等数学（教务原文）", source = CourseSource.WEB).toDomain()))

        val kept = dao.store.single { it.id == "c1" }
        assertEquals(CourseSource.MANUAL, kept.source)
        assertEquals("高等数学（用户改过的教室）", kept.name)
        assertEquals(1, summary.preservedManualCount)
        assertTrue("同 id 冲突需提示用户", summary.suspectedDuplicateNames.isNotEmpty())
    }

    @Test
    fun `导入审计：import_logs 记录被替换数与保留的手动课程数`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "web-1", name = "教务课", source = CourseSource.WEB),
                course(id = "man-1", name = "手动课", source = CourseSource.MANUAL),
            ),
        )
        val logDao = FakeImportLogDao()
        repo(dao, logDao).applyImport(scheduleOf(course(id = "web-2", name = "教务新课", source = CourseSource.WEB).toDomain()))

        val log = logDao.inserted.single()
        assertEquals(1, log.replacedCount)
        assertEquals(1, log.preservedManualCount)
        assertEquals("SUCCESS", log.status)
    }

    @Test
    fun `getCoursesByTermAndSource：导入预览的覆盖范围与疑似重复数据源（M2-A 增补方法）`() = runTest {
        val dao = FakeCourseDao(
            initial = listOf(
                course(id = "csv-1", name = "旧文件课", source = CourseSource.WAKEUP_CSV),
                course(id = "man-1", name = "手动课", source = CourseSource.MANUAL),
                course(id = "other-term", name = "别学期的课", source = CourseSource.WAKEUP_CSV).let {
                    // 手动构造别的学期（course() 帮助函数固定 termId=1，这里复制改掉）
                    it.copy(termId = 99L)
                },
            ),
        )
        val csvCourses = repo(dao).getCoursesByTermAndSource(termId, CourseSource.WAKEUP_CSV)
        val manualNames = repo(dao).getCoursesByTermAndSource(termId, CourseSource.MANUAL).map { it.name }

        assertEquals(listOf("旧文件课"), csvCourses.map { it.name }) // 只看本学期，别学期不算
        assertEquals(listOf("手动课"), manualNames)
    }
}
