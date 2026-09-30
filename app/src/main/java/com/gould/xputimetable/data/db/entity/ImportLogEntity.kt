/*
 * ImportLogEntity.kt —— 导入审计表 import_logs 的 Room 实体
 *
 * 作用：记录每次导入的结果，便于排查"为什么这次导入少了几门课""上次失败原因是什么"。
 * 这是审计日志，与课程数据无外键关联（独立表，ER 图中 import_logs 独立）。
 *
 * 字段对齐架构 §7.2：
 *   - source：来源（WEB / WAKEUP_CSV）；
 *   - status：SUCCESS 或 FAILED(reason)（失败原因附在字符串里）；
 *   - course_count：成功导入课程数，可空（失败导入可能没成功写入，留空）；
 *   - replaced_count：本次替换掉的该来源课程数（AC-13 审计）；
 *   - preserved_manual_count：本次导入后保留的手动课程数（AC-20 审计证据）。
 *
 * 注意：status 用字符串而非枚举，是因为失败原因自由文本（如 FAILED(网络超时)），
 * 用枚举会丢失信息；具体分类在 ImportError（导入阶段）约束，落库时序列化成文本。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "import_logs")
data class ImportLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "course_count")
    val courseCount: Int?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** 本次替换掉的该来源课程数（Spec AC-13 的审计留痕）。 */
    @ColumnInfo(name = "replaced_count")
    val replacedCount: Int = 0,

    /** 本次导入后保留的手动课程数（Spec AC-20 的审计留痕，可事后查证"没删用户数据"）。 */
    @ColumnInfo(name = "preserved_manual_count")
    val preservedManualCount: Int = 0,
)
