/*
 * WeekGrid.kt —— 周视图网格（时间轴 + 7 列同屏）
 *
 * 作用：把本周的课程安排按「星期几 × 节次」画成网格。
 *
 * 核心实现纪律（架构 §11 已知坑，竞品曾踩）：
 *   **行高用绝对定位，绝不用 weight 均分。** 用 weight 均分每一行会让跨节次的卡片
 *   （如 1-2 节连排）在累计舍入误差下错位；这里每一行高度固定 52dp，卡片通过
 *   偏移量 offset(y = 行高 × (起始节次 - 1)) + 高度 = 行高 × 节次数 来定位，
 *   数学上精确，不会出现"半天掉一格"的问题。
 *
 * 其他依据（UIUX §7.1 / §7.3 / §7.5）：
 *   - 7 列同屏（不横向滚动，适配手机竖屏）；
 *   - 节次范围取「作息表最大节次」与「课程最大结束节次」的较大值，兜底 12 节；
 *   - 空白格不画任何东西（零渲染），网格线用背景绘制，避免多余的 Composable。
 */
package com.gould.xputimetable.ui.timetable.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.ui.theme.Grid
import java.time.LocalDate
import kotlin.math.max

/** 单节次行高（固定值，配合绝对定位使用；唯一来源 Grid.RowHeight 令牌）。 */
private val rowHeight: Dp = Grid.RowHeight

/** 组内卡与卡之间的缝隙（dp），沿用改造前 2dp 呼吸感。 */
private const val CARD_GAP = 2f

/** 兜底节次数：作息表为空时按 12 节渲染。 */
private const val defaultSectionCount = 12

/** 等宽数字（R8：时间轴数字列绝对对齐，fontFeatureSettings "tnum"）。 */
private const val TNUM = "tnum"

@Composable
fun WeekGrid(
    items: List<SessionWithCourse>,
    timeSlots: List<TimeSlot>,
    week: Int,
    startDate: String?,
    today: LocalDate,
    nowMinute: Int,
    /** M11：「显示老师姓名」开关（透传到课程卡，见 CourseCard.showTeacher）。 */
    showTeacher: Boolean = true,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onCourseClick: (SessionWithCourse) -> Unit,
) {
    // M7 ②：节数与索引结果按输入缓存——nowMinute 每分钟推进一次，若不缓存则每分钟
    // 全量重算（maxOfOrNull 遍历、7 次 filter、7 次 groupOverlapping）。
    val sectionCount = remember(timeSlots, items) {
        maxOf(
            defaultSectionCount,
            timeSlots.maxOfOrNull { it.section } ?: 0,
            items.maxOfOrNull { it.session.endSection } ?: 0,
        )
    }
    val totalHeight = rowHeight * sectionCount
    val todayDow = today.dayOfWeek.value
    // M6 需求 5：只有"所显示周 == 当前真实周"时，今天的课才算"正在上"，
    // 否则翻到上一周/下一周时同一天格会被误标 primary 边框（守卫与 DayHeader 同源）
    // M7 ②：含 LocalDate.parse + 异常兜底，按输入缓存
    val showsCurrentWeek = remember(startDate, week, today) {
        weekContainsToday(startDate, week, today)
    }
    // M7 ②：作息按节次建索引。原先在时间轴循环里对每节做一次线性 find（节数 × 节数）
    val slotsBySection = remember(timeSlots) { timeSlots.associateBy { it.section } }
    // M7 ②：按天预分组并缓存。原先每次重组都对 items 做 7 次 filter + groupOverlapping
    val groupsByDay = remember(items) {
        (1..7).associateWith { day ->
            groupOverlapping(items.filter { it.session.dayOfWeek == day })
        }
    }
    // 发丝分隔线（R8）：outlineVariant 12% 透明度，不加阴影
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    val axisText = MaterialTheme.colorScheme.onSurfaceVariant
    // M7 性能：时间轴文字样式按 Typography 缓存。
    // 原先是每个 Text 各自 copy(fontFeatureSettings = TNUM) 一次——
    // 12 节 × 2 个 Text × 预组合 3 页 = 每帧新建 72 个 TextStyle。
    val typography = MaterialTheme.typography
    val sectionTextStyle = remember(typography) { typography.bodySmall.copy(fontFeatureSettings = TNUM) }
    val minuteTextStyle = remember(typography) { typography.labelSmall.copy(fontFeatureSettings = TNUM) }
    val minuteTextColor = remember(axisText) { axisText.copy(alpha = 0.75f) }

    Column(modifier = modifier.verticalScroll(scrollState)) {
        // M7 性能：原先 7 天各用一个 BoxWithConstraints（其实现是 SubcomposeLayout），
        // 首屏预组合 3 页 → 21 次 subcompose，是冷启动单帧偏长的主要来源。
        // 改为在外层取一次可用宽度、按 (可用宽 − 轴宽) / 7 算出列宽，7 天改用普通 Box(weight)。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columnWidth = with(LocalDensity.current) {
                ((constraints.maxWidth - Grid.AxisWidth.toPx()).coerceAtLeast(0f) / 7f).toDp().value
            }
            Row(Modifier.height(totalHeight).fillMaxWidth()) {

                // ---------- 左侧时间轴：节次号 + 起始时间 ----------
                Column(Modifier.width(Grid.AxisWidth).fillMaxHeight()) {
                    for (section in 1..sectionCount) {
                        val slot = slotsBySection[section]
                        Box(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = section.toString(),
                                    style = sectionTextStyle,
                                    color = axisText,
                                )
                                if (slot != null) {
                                    Text(
                                        text = formatMinute(slot.startMinute),
                                        style = minuteTextStyle,
                                        color = minuteTextColor,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------- 7 天列（AC-23：同格多课全部可见） ----------
                for (day in 1..7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawBehind {
                                val step = rowHeight.toPx()
                                for (i in 1 until sectionCount) {
                                    drawLine(
                                        color = divider,
                                        start = Offset(0f, i * step),
                                        end = Offset(size.width, i * step),
                                        strokeWidth = 1f,
                                    )
                                }
                            },
                    ) {
                        // 同一天内按节次区间重叠分组：每组独立布局，组间互不影响
                        // （分组结果已按 items 缓存，见上方 groupsByDay）
                        for (group in groupsByDay[day].orEmpty()) {
                            ConflictGroup(
                                group = group,
                                cellWidth = columnWidth,
                                // M6 需求 5：isToday 加周次守卫——翻到别的周不画"正在上"边框
                                isToday = showsCurrentWeek && day == todayDow,
                                timeSlots = timeSlots,
                                nowMinute = nowMinute,
                                onCourseClick = onCourseClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一个冲突组（区间重叠的若干安排）的渲染：组内卡片位置由 CellLayout.arrange 决定
 * （横向等分 / 纵向等分 / 折叠只画第一张 + 「+N」角标）。组的纵向范围取成员的
 * min(start)..max(end)，行高仍是绝对定位（52dp/节，纪律不变）。
 *
 * 已知妥协（M2-C §1.3，不藏）：拆到 2-3 张时单卡触摸目标会小于 44dp——AC-23
 * 优先保证"看得见"；用户可点开编辑页看全字段，或用「按来源清理」去掉重复来源。
 */
@Composable
private fun ConflictGroup(
    group: List<SessionWithCourse>,
    cellWidth: Float,
    isToday: Boolean,
    timeSlots: List<TimeSlot>,
    nowMinute: Int,
    /** M11：「显示老师姓名」开关（由 WeekGrid 透传进来）。 */
    showTeacher: Boolean = true,
    onCourseClick: (SessionWithCourse) -> Unit,
) {
    // M7 性能：组的几何与布局只依赖 (group, cellWidth)，按输入缓存。
    // nowMinute 每分钟推进会重组一次，原实现每次都重算 min/max 与 CellLayout.arrange。
    val minStart = remember(group) { group.minOf { it.session.startSection } }
    val maxEnd = remember(group) { group.maxOf { it.session.endSection } }
    val groupHeight = rowHeight * (maxEnd - minStart + 1) - 2.dp
    val arrangement = remember(group, cellWidth, groupHeight) {
        CellLayout.arrange(group.size, cellWidth, groupHeight.value)
    }

    Box(
        modifier = Modifier
            .offset(y = rowHeight * (minStart - 1))
            .height(groupHeight),
    ) {
        group.forEachIndexed { index, item ->
            val slot = arrangement.slots[index]
            CourseCard(
                item = item,
                isNow = isToday && isRunning(item, timeSlots, nowMinute),
                showTeacher = showTeacher,
                modifier = Modifier
                    .offset(x = (slot.dx + CARD_GAP / 2f).dp, y = (slot.dy + CARD_GAP / 2f).dp)
                    .size(
                        width = max(slot.width - CARD_GAP, 4f).dp,
                        height = max(slot.height - CARD_GAP, 4f).dp,
                    ),
                // M7 性能：传稳定引用而不是在这里构造 `{ onCourseClick(item) }`
                // （后者每次重组都新建 lambda，会让 CourseCard 无法跳过重组）
                onCourseClick = onCourseClick,
            )
        }
        if (arrangement.foldedCount > 0) {
            FoldedBadge(
                count = arrangement.foldedCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** 折叠角标：首卡右上角「+N」（小号文字 + 半透明底；不用 emoji 图标）。 */
@Composable
private fun FoldedBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "+$count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .offset(x = (-3).dp, y = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 该安排此刻是否正在进行（用于高亮）。 */
private fun isRunning(
    item: SessionWithCourse,
    timeSlots: List<TimeSlot>,
    nowMinute: Int,
): Boolean {
    val start = timeSlots.find { it.section == item.session.startSection }?.startMinute ?: return false
    val end = timeSlots.find { it.section == item.session.endSection }?.endMinute ?: return false
    return nowMinute in start until end
}

/** 分钟数 → "08:00"。 */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
