/*
 * TimeSlot.kt —— 节次作息（领域模型）
 *
 * 作用：把"第几节"映射到当天具体时间（当天 0 点起的分钟数），用于：
 *   ① 周视图左侧时间轴显示上课时间；
 *   ② 桌面小组件把"第 N 节"换算成当天的上下课时刻。
 *
 * 为什么存分钟数而不是 "08:00" 字符串：计算与比较需要频繁加减，整数运算既快又不会
 * 出现格式解析错误；显示时再格式化（见 WeekGrid 的 formatMinute）。
 */
package com.gould.xputimetable.domain.model

/**
 * 某一节次的作息时间。
 *
 * @param id          主键（0 表示新增）
 * @param section     节次号（唯一）
 * @param startMinute 开始时间（当天 0 点起的分钟数，如 08:00 = 480）
 * @param endMinute   结束时间（同上，必须大于 startMinute）
 */
data class TimeSlot(
    val id: Long = 0L,
    val section: Int,
    val startMinute: Int,
    val endMinute: Int,
)
