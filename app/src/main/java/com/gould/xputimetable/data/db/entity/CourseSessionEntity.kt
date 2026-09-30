/*
 * CourseSessionEntity.kt —— 上课安排表 course_sessions 的 Room 实体
 *
 * 作用：记录"一门课在星期几、第几节、第几周上"。一门课可有多条安排。
 *
 * 字段对齐架构 §7.2：
 *   - id 自增主键；course_id 外键 → courses.id，ON DELETE CASCADE
 *     （删除课程即级联删除其全部安排；课程被导入替换时也依赖这条级联清掉旧安排）；
 *   - day_of_week：1 = 周一 … 7 = 周日；
 *   - week_type：TEXT，存**大写枚举名** ALL / ODD / EVEN（写入侧统一，见 Mappers）；
 *     读取时由 WeekCalc.parseWeekType 容错解析；SQL 过滤用 UPPER(week_type) 兜底脏数据。
 *   索引：course_id（外键必索引）、day_of_week。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "course_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_course_sessions_course_id", value = ["course_id"]),
        Index(name = "index_course_sessions_day_of_week", value = ["day_of_week"]),
    ],
)
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "course_id")
    val courseId: String,

    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int,

    @ColumnInfo(name = "start_section")
    val startSection: Int,

    @ColumnInfo(name = "end_section")
    val endSection: Int,

    @ColumnInfo(name = "start_week")
    val startWeek: Int,

    @ColumnInfo(name = "end_week")
    val endWeek: Int,

    @ColumnInfo(name = "week_type")
    val weekType: String,

    /**
     * 显式周次列表（CSV，升序），非空时以它为准；为空时按 start_week..end_week + week_type 判定。
     * M2-B P0-A 增列：教务直连实测发现 2/16 条安排的周次（如 2,6,10,14）无法用
     * 「区间 + 单双周」精确表达，降级成 min~max 会多显示没课的周（可见缺陷）。
     * 判定语义见 CourseSessionDao 的两支 OR 查询与 WeekCalc.isSessionActive 重载。
     */
    @ColumnInfo(name = "week_list")
    val weekList: String? = null,

    @ColumnInfo(name = "classroom")
    val classroom: String?,
)
