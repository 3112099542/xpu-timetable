/*
 * CourseSessionDao.kt —— 上课安排表访问接口
 *
 * 作用：对 course_sessions 表的增删改查。写操作 suspend，观察类返回 Flow（Room 3 要求）。
 *
 * 方法说明：
 *   - insert / insertAll / update：写入与覆盖；
 *   - deleteByCourseId：删除某门课的全部安排（供 upsertCourse 的"先清后写"使用）；
 *     注意 courses 表已声明 ON DELETE CASCADE，直接删 CourseEntity 时安排也会被级联删除，
 *     但"整体替换"需要先显式删除旧安排再写入新的，故保留此方法；
 *   - observeWeekSessions：周视图核心查询（含课程名与颜色），周次与单双周过滤在 SQL 完成。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CourseSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<CourseSessionEntity>)

    @Update
    suspend fun update(session: CourseSessionEntity)

    @Delete
    suspend fun delete(session: CourseSessionEntity)

    @Query("DELETE FROM course_sessions WHERE course_id = :courseId")
    suspend fun deleteByCourseId(courseId: String)

    @Query("SELECT * FROM course_sessions WHERE course_id = :courseId ORDER BY day_of_week ASC, start_section ASC")
    fun observeByCourseId(courseId: String): Flow<List<CourseSessionEntity>>

    @Query("SELECT * FROM course_sessions ORDER BY day_of_week ASC, start_section ASC")
    fun observeAll(): Flow<List<CourseSessionEntity>>

    /**
     * 某周全部生效安排（含课程名与颜色），供周视图渲染。
     *
     * 周次过滤**在 SQL 层完成**，M2-B P0-A 起为**两支 OR**（week_list 优先语义）：
     *   - 支 1（区间语义，既有路径）：week_list 为空 → start_week..end_week + 单双周判定；
     *   - 支 2（显式列表语义）：week_list 非空 → 周数字出现在 CSV 列表中即生效。
     *     匹配手法：给两侧补逗号后 LIKE（',4,' LIKE '%,4,%'），避免 4 误命中 14/40。
     * UPPER(s.week_type) 用于兜住小写等脏数据（见 pitfalls：大小写不一致会静默漏课）。
     */
    @Query(
        """
        SELECT s.*, c.name AS course_name, c.color_tag AS color_tag
        FROM course_sessions s
        INNER JOIN courses c ON c.id = s.course_id
        WHERE c.term_id = :termId
          AND (
            (s.week_list IS NULL
              AND s.start_week <= :week AND s.end_week >= :week
              AND (UPPER(s.week_type) = 'ALL'
                   OR (UPPER(s.week_type) = 'ODD'  AND :week % 2 = 1)
                   OR (UPPER(s.week_type) = 'EVEN' AND :week % 2 = 0)))
            OR
            (s.week_list IS NOT NULL AND (',' || s.week_list || ',') LIKE ('%,' || :week || ',%'))
          )
        ORDER BY s.day_of_week ASC, s.start_section ASC
        """,
    )
    fun observeWeekSessions(termId: Long, week: Int): Flow<List<SessionWithCoursePojo>>

    /** 取全部安排（suspend，非 Flow 场景使用）。 */
    @Query("SELECT * FROM course_sessions")
    suspend fun getAll(): List<CourseSessionEntity>
}
