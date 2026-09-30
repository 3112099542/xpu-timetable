/*
 * TermEntity.kt —— 学期表 terms 的 Room 实体
 *
 * 作用：课表的时间基准。周次计算依赖 start_date（该学期第一周的周一），见 WeekCalc。
 *
 * 字段对齐架构 §7.2：name、start_date（ISO 字符串）、total_weeks、is_active、时间戳。
 * 索引 is_active：加速"取当前激活学期"（这是每次进入周视图的第一步）。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "terms",
    indices = [Index(name = "index_terms_is_active", value = ["is_active"])],
)
data class TermEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** 第一周周一的 ISO 日期（yyyy-MM-dd） */
    @ColumnInfo(name = "start_date")
    val startDate: String,

    @ColumnInfo(name = "total_weeks")
    val totalWeeks: Int,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
