/*
 * CourseSession.kt —— 上课安排（领域模型）
 *
 * 作用：描述"一门课的一次固定上课"：星期几、第几节到第几节、第几周到第几周、单双周、教室。
 * 一门课可有多条安排（周三 1-2 节 + 周五 3-4 节），这是课表类产品的标准拆法，
 * 直接支撑周视图按格渲染与单双周过滤。
 *
 * 字段口径与架构 §7.2 的 course_sessions 表一一对应；
 * weekType 用枚举（存储时才转大写字符串，见 Mappers）。
 */
package com.gould.xputimetable.domain.model

/**
 * 一次固定上课安排。
 *
 * @param id            主键（0 表示新增，由数据库自增生成）
 * @param courseId      所属课程 id
 * @param dayOfWeek     星期几：1 = 周一 … 7 = 周日
 * @param startSection  开始节次（含）
 * @param endSection    结束节次（含）
 * @param startWeek     起始周（含，从 1 开始）
 * @param endWeek       结束周（含）
 * @param weekType      单双周类型
 * @param weeks         显式周次列表（升序去重）：非空时**以它为准**，忽略区间与单双周；
 *                      为空时按 startWeek..endWeek + weekType 判定。M2-B P0-A 增补，
 *                      教务直连存在「区间 + 单双周」表达不了的周次（如 2,6,10,14）。
 * @param classroom     教室，可空
 */
data class CourseSession(
    val id: Long = 0L,
    val courseId: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val weeks: List<Int>? = null,
    val classroom: String? = null,
)
