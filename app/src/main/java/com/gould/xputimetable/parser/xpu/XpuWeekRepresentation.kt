/*
 * XpuWeekRepresentation.kt —— 周次数组 → 「区间 + 单双周 + 可选显式列表」的表示规则
 *
 * Spec M2-B §1.4（实测发现，必须做成纯函数并单测）：16 条安排里有 2 条用
 * 「区间 + 单双周」表达不了（如 2,6,10,14 是纯偶但每隔 4 周，EVEN+2..14 会**多显示**
 * 4/8/12 周）。规则：只有当区间 + 单双周无法精确表达时才填 weekList，
 * 保证绝大多数课程仍走区间语义、无额外负担。
 *
 * 【注意】 已踩坑：真实响应的 weekIndexes **可能乱序**（实测 [16,17,18,11,...]），
 * 必须先 sorted().distinct() 再判断。
 */
package com.gould.xputimetable.parser.xpu

import com.gould.xputimetable.domain.model.WeekType

/** 周次的规范化表示（直接映射到 CourseSession 的三个周次字段）。 */
data class WeekRepresentation(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    /** 仅当区间 + 单双周无法精确表达时非空（升序去重后的精确列表）。 */
    val weeks: List<Int>? = null,
)

/**
 * 把教务给出的周次数组转成规范化表示。纯函数，不抛异常。
 *
 * 规则（Spec §1.4 原文）：给定 weeks = sorted(set(weekIndexes))，s = min, e = max：
 *   - weeks == [s..e]           → 区间 + ALL
 *   - weeks == [s..e] 全部奇数   → 区间 + ODD
 *   - weeks == [s..e] 全部偶数   → 区间 + EVEN
 *   - 其它                      → 区间 + ALL + weekList = weeks
 * 空列表 / 全部非正 → 返回 null（由调用方记 anomalies 跳行）。
 */
fun weekRepresentation(weekIndexes: List<Int>): WeekRepresentation? {
    val weeks = weekIndexes.filter { it >= 1 }.distinct().sorted()
    if (weeks.isEmpty()) return null
    val s = weeks.first()
    val e = weeks.last()
    val full = (s..e).toList()
    return when {
        weeks == full -> WeekRepresentation(s, e, WeekType.ALL)
        weeks == full.filter { it % 2 == 1 } -> WeekRepresentation(s, e, WeekType.ODD)
        weeks == full.filter { it % 2 == 0 } -> WeekRepresentation(s, e, WeekType.EVEN)
        else -> WeekRepresentation(s, e, WeekType.ALL, weeks)
    }
}
