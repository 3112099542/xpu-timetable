/*
 * Term.kt —— 学期（领域模型）
 *
 * 作用：课表的时间基准。周次计算依赖 startDate（该学期第一周的周一）：
 *   当前周 = （今天 - startDate）的天数 / 7 + 1（见 WeekCalc.currentWeek，纯函数）。
 *
 * 约定：
 *   - startDate 用 ISO 日期字符串（yyyy-MM-dd）存储，便于 Room 落库与排序；
 *   - isActive 表示当前激活学期，全局至多一条为 1（多学期管理属 Out-of-Scope，见 Spec §3）；
 *   - 开学前 currentWeek 返回 null（绝不返回负数周次），由界面显示"未开学"。
 */
package com.gould.xputimetable.domain.model

/**
 * 一个学期。
 *
 * @param id          主键（0 表示新增）
 * @param name        学期名，如 "2026-2027-1"
 * @param startDate   第一周周一的 ISO 日期（yyyy-MM-dd）
 * @param totalWeeks  总教学周数
 * @param isActive    是否为当前激活学期
 * @param createdAt   创建时间，epoch 毫秒
 * @param updatedAt   更新时间，epoch 毫秒
 */
data class Term(
    val id: Long = 0L,
    val name: String,
    val startDate: String,
    val totalWeeks: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
