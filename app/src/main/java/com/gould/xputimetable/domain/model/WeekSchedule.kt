/*
 * WeekSchedule.kt —— 周视图聚合（领域模型）
 *
 * 作用：把"某一周的全部上课安排"整理成周视图直接可用的数据结构。
 *   TimetableRepository.observeWeek(termId, week) 返回的就是它。
 *
 * 为什么需要聚合而非直接返回 CourseSession 列表：
 *   周视图的每一格里要同时画"课程名 + 教室 + 颜色"，而 CourseSession 只持有
 *   courseId。把课程信息（名/色）与安排预先合并成一个 SessionWithCourse，
 *   渲染层就不用再拿着 courseId 去反查课程表，逻辑更内聚。
 *
 * 注意：WeekSchedule 是"只读视图数据"。它的字段来自 courses 与 course_sessions
 * 两表的 JOIN（见架构 §7.3），由仓库实现层在查询后组装，本文件只做数据承载。
 */
package com.gould.xputimetable.domain.model

/**
 * 单条已合并课程信息的上课安排（周视图一格要画的内容）。
 *
 * @param session     原始上课安排
 * @param courseName  所属课程名（来自 courses 表）
 * @param colorTag    课程调色板索引（来自 courses 表，保证同色）
 */
data class SessionWithCourse(
    val session: CourseSession,
    val courseName: String,
    val colorTag: Int,
)

/**
 * 某一周的全部上课安排（已与课程信息合并），供周视图渲染。
 *
 * @param week   周次（从 1 开始；开学前不会构造本对象，见 WeekCalc.currentWeek）
 * @param items  本周所有生效的安排（已含课程名与颜色）
 */
data class WeekSchedule(
    val week: Int,
    val items: List<SessionWithCourse>,
)
