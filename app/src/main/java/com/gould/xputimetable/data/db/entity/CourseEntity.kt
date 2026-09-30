/*
 * CourseEntity.kt —— 课程表 courses 的 Room 实体
 *
 * 作用：把领域模型 Course 映射到 SQLite 的 courses 表。这是 Room 直接读写的对象，
 * 带 @Entity / @PrimaryKey / @ColumnInfo 等 Room 3 注解（包名 androidx.room3）。
 *
 * 字段严格对齐架构 §7.2：
 *   - id：TEXT 主键（UUID）；name/color_tag/source/created_at/updated_at/term_id 非空；
 *   - code/teacher/note/edited_at：可空；
 *   - term_id：归属学期（terms.id），外键 ON DELETE CASCADE（删除学期即级联删除其课程）；
 *   - edited_at：用户最后手动编辑时间（null = 从未编辑；禁止用 0 表示空 —— 0 是合法时间戳）。
 *   索引 index_courses_term_id 加速「按学期取课程」与周视图 JOIN 过滤。
 *
 * 列名用 @ColumnInfo 显式写成 snake_case，与架构表结构、§7.3 的 JOIN SQL 完全一致，
 * 避免 Room 默认用小驼峰导致列名漂移、后续查询对不上。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = TermEntity::class,
            parentColumns = ["id"],
            childColumns = ["term_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_courses_term_id", value = ["term_id"])],
)
data class CourseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "code")
    val code: String?,

    @ColumnInfo(name = "teacher")
    val teacher: String?,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "color_tag")
    val colorTag: Int,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "term_id")
    val termId: Long,

    /**
     * 用户最后一次手动编辑的时间（epoch 毫秒），可空。
     * 语义（AC-22）：null = 从未编辑；非空 = 被用户改过（此时 source 应为 MANUAL，受导入保护）。
     * 保留它是为了不丢失溯源——否则"这门课原本来自教务导入"这条信息会永久丢失。
     * 注意：用 NULL 表示空，禁止用 0 / -1（0 是合法时间戳，会产生歧义）。
     */
    @ColumnInfo(name = "edited_at")
    val editedAt: Long?,
)
