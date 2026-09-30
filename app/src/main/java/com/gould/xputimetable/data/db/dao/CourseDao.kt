/*
 * CourseDao.kt —— 课程表访问接口
 *
 * 作用：定义对 courses 表的增删改查。Room 3 要求 DAO 方法要么是 suspend（协程里等结果），
 * 要么返回 Flow（数据变化时自动推送）。本文件严格遵守：写操作为 suspend，观察类返回 Flow。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.gould.xputimetable.data.db.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: String): CourseEntity?

    @Query("SELECT * FROM courses ORDER BY name ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    /** 取全部课程（suspend，非 Flow 场景使用）。 */
    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<CourseEntity>

    /** 取某学期全部课程（M6 导出用；不能复用 observeAll 的 Flow——导出是一次性读取）。 */
    @Query("SELECT * FROM courses WHERE term_id = :termId ORDER BY name ASC")
    suspend fun getByTerm(termId: Long): List<CourseEntity>

    // ---------- 按来源替换（Spec AC-13 / AC-20 / AC-21）----------

    /**
     * 删除某学期中**指定导入来源**的全部课程（配合 applyImport 的「按来源整体替换」）。
     *
     * AC-20 不变量（P0）：SQL 里额外加了 `source != 'MANUAL'` 的兜底条件——即使调用方
     * 误传 MANUAL，本方法也**一条都不会删**。这条硬约束写在 SQL 而非仅写在注释里，
     * 是为了让「手动添加/编辑过的课程永不被导入删除」无法被上层误用破坏。
     * 注意：删除课程会经 courses→course_sessions 的 ON DELETE CASCADE 连带清掉其安排。
     */
    @Query("DELETE FROM courses WHERE term_id = :termId AND source = :source AND source != 'MANUAL'")
    suspend fun deleteImportedByTermAndSource(termId: Long, source: String)

    /** 统计某学期某来源的课程数（applyImport 用它算出本次被替换的数量）。 */
    @Query("SELECT COUNT(*) FROM courses WHERE term_id = :termId AND source = :source")
    suspend fun countByTermAndSource(termId: Long, source: String): Int

    /** 取某学期某来源的课程名（用于 AC-21 的「疑似重复」比对，调用方自行去重）。 */
    @Query("SELECT name FROM courses WHERE term_id = :termId AND source = :source")
    suspend fun getNamesByTermAndSource(termId: Long, source: String): List<String>

    /**
     * 取某学期某来源的课程 id。
     *
     * 用途：applyImport 写入前用它找出「已被用户编辑过（source=MANUAL）」的课程 id。
     * 若导入课程的 id 与之相同，说明是同一门课被用户改过——此时保留用户版本、跳过导入版本，
     * 因为 insertAll 使用 REPLACE，直接写入会把用户的修改静默覆盖掉（违反 AC-22）。
     */
    @Query("SELECT id FROM courses WHERE term_id = :termId AND source = :source")
    suspend fun getIdsByTermAndSource(termId: Long, source: String): List<String>
}
