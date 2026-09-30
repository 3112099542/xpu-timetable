/*
 * TimeSlotEntity.kt —— 节次作息表 time_slots 的 Room 实体
 *
 * 作用：把"第几节"映射到当天分钟数，用于周视图时间轴与课前提醒的时间换算。
 *
 * 字段对齐架构 §7.2：section 唯一（一个节次一条），start_minute / end_minute 为当天 0 点起的分钟数。
 * 预置数据为西工程大标准作息（开发期按实际作息核对填充），设置页可编辑。
 */
package com.gould.xputimetable.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "time_slots",
    indices = [Index(name = "index_time_slots_section", value = ["section"], unique = true)],
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "section")
    val section: Int,

    @ColumnInfo(name = "start_minute")
    val startMinute: Int,

    @ColumnInfo(name = "end_minute")
    val endMinute: Int,
)
