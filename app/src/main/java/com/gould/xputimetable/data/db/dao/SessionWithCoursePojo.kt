/*
 * SessionWithCoursePojo.kt —— 周视图 JOIN 查询的承载对象（数据层）
 *
 * 作用：CourseSessionDao.observeWeekSessions 的返回元素。它把"一条安排 + 课程名 + 颜色"
 * 合并成一行结果，避免 UI 层拿着 courseId 再反查课程表。
 *
 * 为什么叫 Pojo：它不是 @Entity（不对应独立表），只是 SQL JOIN 结果的映射容器；
 * 由 Mappers 转成领域模型 SessionWithCourse 后交给界面使用。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import com.gould.xputimetable.data.db.entity.CourseSessionEntity

data class SessionWithCoursePojo(
    @Embedded
    val session: CourseSessionEntity,

    @ColumnInfo(name = "course_name")
    val courseName: String,

    @ColumnInfo(name = "color_tag")
    val colorTag: Int,
)
