/*
 * Fakes.kt —— 测试用假 DAO（跨测试文件共享）
 *
 * 作用：本项目不为单测引入 Robolectric / MockK（Spec 约束：不新增测试依赖），而是手写假 DAO 隔离仓库逻辑。
 * 这些假实现被多个测试文件复用（TimetableRepositoryImplTest、RepositoryTermScopeTest、
 * ImportSourceScopeTest、MappersTest），因此集中到本文件并声明为 internal，避免各写一份而逐渐漂移。
 *
 * 设计原则：
 *   1. 只实现"被仓库实际调用"的方法，其余返回空/默认值，保持文件短小；
 *   2. FakeCourseDao 用可变 store 模拟 courses 表本体：插入会写入、删除会真的移除，
 *      这样"按来源替换 + 不删手动课程"的语义才测得出来（而不只是断言调用次数）；
 *   3. 把收到的写入/删除记录下来（inserted / deletedXxx），供用例断言。
 *
 * 注意：假 DAO 不执行真实 SQL（那是 Room 的职责；真实 SQL 语义由团队领导用
 * schema DDL + sqlite3 夹具另行验证），这里只验证仓库层编排逻辑。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.dao.CourseDao
import com.gould.xputimetable.data.db.dao.CourseSessionDao
import com.gould.xputimetable.data.db.dao.ImportLogDao
import com.gould.xputimetable.data.db.dao.SessionWithCoursePojo
import com.gould.xputimetable.data.db.dao.TermDao
import com.gould.xputimetable.data.db.dao.TimeSlotDao
import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.data.db.entity.ImportLogEntity
import com.gould.xputimetable.data.db.entity.TermEntity
import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeCourseDao(
    initial: List<CourseEntity> = emptyList(),
    val inserted: MutableList<CourseEntity> = mutableListOf(),
    val deletedByTermAndSource: MutableList<Pair<Long, String>> = mutableListOf(),
) : CourseDao {

    /** 模拟 courses 表本体 */
    val store: MutableList<CourseEntity> = initial.toMutableList()

    override suspend fun insert(course: CourseEntity) {
        store.removeAll { it.id == course.id } // 对应 REPLACE 语义
        store += course
        inserted += course
    }

    override suspend fun insertAll(courses: List<CourseEntity>) {
        courses.forEach { c ->
            store.removeAll { it.id == c.id }
            store += c
        }
        inserted += courses
    }

    override suspend fun update(course: CourseEntity) {
        store.removeAll { it.id == course.id }
        store += course
    }

    override suspend fun delete(course: CourseEntity) { store.removeAll { it.id == course.id } }

    override suspend fun deleteById(id: String) { store.removeAll { it.id == id } }

    override suspend fun getById(id: String): CourseEntity? = store.find { it.id == id }

    override fun observeAll(): Flow<List<CourseEntity>> = flowOf(store.toList())

    override suspend fun getAll(): List<CourseEntity> = store.toList()

    override suspend fun getByTerm(termId: Long): List<CourseEntity> =
        store.filter { it.termId == termId }.sortedBy { it.name }

    override suspend fun deleteImportedByTermAndSource(termId: Long, source: String) {
        deletedByTermAndSource += termId to source
        store.removeAll { it.termId == termId && it.source == source }
    }

    override suspend fun countByTermAndSource(termId: Long, source: String): Int =
        store.count { it.termId == termId && it.source == source }

    override suspend fun getNamesByTermAndSource(termId: Long, source: String): List<String> =
        store.filter { it.termId == termId && it.source == source }.map { it.name }

    override suspend fun getIdsByTermAndSource(termId: Long, source: String): List<String> =
        store.filter { it.termId == termId && it.source == source }.map { it.id }
}

internal class FakeCourseSessionDao(
    private val sessions: List<CourseSessionEntity> = emptyList(),
    private val courses: List<CourseEntity> = emptyList(),
    val insertedAll: MutableList<CourseSessionEntity> = mutableListOf(),
    val deletedCourseIds: MutableList<String> = mutableListOf(),
) : CourseSessionDao {
    override suspend fun insert(session: CourseSessionEntity) {}
    override suspend fun insertAll(sessions: List<CourseSessionEntity>) { insertedAll += sessions }
    override suspend fun update(session: CourseSessionEntity) {}
    override suspend fun delete(session: CourseSessionEntity) {}
    override suspend fun deleteByCourseId(courseId: String) { deletedCourseIds += courseId }
    override fun observeByCourseId(courseId: String): Flow<List<CourseSessionEntity>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<CourseSessionEntity>> = flowOf(emptyList())

    /** 模拟 SQL 的 INNER JOIN courses ... AND c.term_id = :termId */
    override fun observeWeekSessions(termId: Long, week: Int): Flow<List<SessionWithCoursePojo>> {
        val courseById = courses.associateBy { it.id }
        return flowOf(
            sessions.filter { courseById[it.courseId]?.termId == termId }
                .map { s ->
                    SessionWithCoursePojo(
                        session = s,
                        courseName = courseById[s.courseId]?.name ?: "",
                        colorTag = courseById[s.courseId]?.colorTag ?: 0,
                    )
                },
        )
    }

    override suspend fun getAll(): List<CourseSessionEntity> = sessions
}

internal class FakeTermDao(private val active: TermEntity? = null) : TermDao {
    override suspend fun insert(term: TermEntity): Long = 0
    override suspend fun update(term: TermEntity) {}
    override suspend fun delete(term: TermEntity) {}
    override suspend fun getById(id: Long): TermEntity? = null
    override fun observeActive(): Flow<TermEntity?> = flowOf(active)
    override fun observeAll(): Flow<List<TermEntity>> = flowOf(emptyList())
}

internal class FakeTimeSlotDao(
    private val slots: List<TimeSlotEntity> = emptyList(),
    val inserted: MutableList<TimeSlotEntity> = mutableListOf(),
) : TimeSlotDao {
    override suspend fun insert(slot: TimeSlotEntity) {
        inserted += slot
    }

    override suspend fun insertAll(items: List<TimeSlotEntity>) {
        inserted += items
    }
    override suspend fun update(slot: TimeSlotEntity) {}
    override suspend fun delete(slot: TimeSlotEntity) {}
    override suspend fun getBySection(section: Int): TimeSlotEntity? = slots.find { it.section == section }
    override fun observeAll(): Flow<List<TimeSlotEntity>> = flowOf(slots)
}

internal class FakeImportLogDao(val inserted: MutableList<ImportLogEntity> = mutableListOf()) : ImportLogDao {
    override suspend fun insert(log: ImportLogEntity): Long {
        inserted += log
        return inserted.size.toLong()
    }
    override fun observeAll(): Flow<List<ImportLogEntity>> = flowOf(emptyList())
    override suspend fun getLatest(): ImportLogEntity? = null
}
