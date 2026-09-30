/*
 * CellLayout.kt —— 同格多课的布局纯函数（AC-23，Spec M2-C §1.2）
 *
 * 纯 Kotlin、无 Compose 依赖，便于 JVM 单测。要修的缺陷（真机实测 2026-09-17）：
 * 周一 1-2 节同时有 3 门课（MANUAL + WAKEUP_CSV + WEB），WeekGrid 绝对定位把卡片
 * 叠在同一位置，只有最后绘制的一张可见——数据在库里、用户看不见，学生按课表上课
 * 会漏课，属最高等级缺陷。
 *
 * 规则优先级（全部确定性判断）：
 *  1) n == 1                    → 占满整格
 *  2) cellWidth / n >= 48dp     → 横向等分（平板/横屏）
 *  3) cellHeight / n >= 24dp    → 纵向等分（手机竖屏主路径：列宽约 46dp，横向切不可读）
 *  4) 否则                      → 只画第 1 张，其余折叠（UI 显示「+N」角标）
 *
 * 已知妥协（不藏，见 M2-C §1.3）：拆到 2-3 张时单卡触摸目标会小于 44dp 可达性下限
 * ——AC-23 优先保证「看得见」；用户可点开编辑页看全字段，或用「按来源清理」去掉
 * 重复来源的课。不要为触摸目标牺牲可见性。
 */
package com.gould.xputimetable.ui.timetable.components

import com.gould.xputimetable.domain.model.SessionWithCourse

/** 一格内一张卡的位置与尺寸（单位：dp，相对该格左上角）。 */
data class CardSlot(
    val dx: Float,
    val dy: Float,
    val width: Float,
    val height: Float,
)

/** 同格布局结果：可画的卡片 + 被折叠的数量（折叠时只画第一张）。 */
data class CellArrangement(
    val slots: List<CardSlot>,
    val foldedCount: Int,
)

object CellLayout {
    /** 横向并排时每卡最小宽度。 */
    const val MIN_CARD_WIDTH = 48f

    /** 纵向拆分时每卡最小高度（容 1 行 13sp 文字）。 */
    const val MIN_CARD_HEIGHT = 24f

    fun arrange(n: Int, cellWidth: Float, cellHeight: Float): CellArrangement {
        if (n <= 1) {
            return CellArrangement(listOf(CardSlot(0f, 0f, cellWidth, cellHeight)), 0)
        }
        // 2) 横向等分：平板/横屏列宽足够时优先（卡片仍可读）
        if (cellWidth / n >= MIN_CARD_WIDTH) {
            val w = cellWidth / n
            val slots = (0 until n).map { CardSlot(it * w, 0f, w, cellHeight) }
            return CellArrangement(slots, 0)
        }
        // 3) 纵向等分：手机竖屏主路径（节次行高 52dp，跨节卡片可拆）
        if (cellHeight / n >= MIN_CARD_HEIGHT) {
            val h = cellHeight / n
            val slots = (0 until n).map { CardSlot(0f, it * h, cellWidth, h) }
            return CellArrangement(slots, 0)
        }
        // 4) 折叠：空间装不下 → 只画第 1 张，其余由 UI 显示「+N」角标
        return CellArrangement(listOf(CardSlot(0f, 0f, cellWidth, cellHeight)), n - 1)
    }
}

/**
 * 同一天内的安排按**节次区间重叠**分组（AC-23 前置逻辑，纯函数）。
 *
 * 两条安排的 [startSection..endSection] 有交集 ⇒ 同一冲突组。注意跨节连排（span > 1）
 * 的卡片必须按区间判重叠，不能只比 startSection（1-2 节的课与 2-3 节的课在第 2 节
 * 相撞）。组间互不相交 ⇒ 不同组各画各的，互不影响。
 */
internal fun groupOverlapping(items: List<SessionWithCourse>): List<List<SessionWithCourse>> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedWith(
        compareBy({ it.session.startSection }, { it.session.endSection }),
    )
    val groups = mutableListOf<MutableList<SessionWithCourse>>()
    var groupEnd = Int.MIN_VALUE
    for (item in sorted) {
        val start = item.session.startSection
        val end = item.session.endSection
        if (start <= groupEnd && groups.isNotEmpty()) {
            groups.last() += item
        } else {
            groups += mutableListOf(item)
        }
        if (end > groupEnd) groupEnd = end
    }
    return groups
}
