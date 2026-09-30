/*
 * TodayWidget.kt —— 桌面小组件入口与数据装载（M3，只读「今日课程」）
 *
 * 数据流（规格 §2，已核实）：
 *   observeActiveTerm → WeekCalc.currentWeek（null = 不在学期周内，按「今天没有课」）
 *   → observeWeek(termId, week)（周次/单双周过滤已在 SQL 完成，UI 不再过滤）
 *   → 筛 dayOfWeek == 今天 → 用 observeTimeSlots 换算分钟 → TodayPlanBuilder.build
 * 无激活学期 → EmptyReason.NO_TERM。
 *
 * 不发起网络请求、不显示账号信息、不提供编辑（架构 §9.4：只读视图）。
 */
package com.gould.xputimetable.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import com.gould.xputimetable.TimetableApp
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.ui.theme.DarkBackground
import com.gould.xputimetable.ui.theme.DarkOnPrimary
import com.gould.xputimetable.ui.theme.DarkOnBackground
import com.gould.xputimetable.ui.theme.DarkOnSurface
import com.gould.xputimetable.ui.theme.DarkOnSurfaceVariant
import com.gould.xputimetable.ui.theme.DarkPrimary
import com.gould.xputimetable.ui.theme.DarkPrimaryContainer
import com.gould.xputimetable.ui.theme.DarkSurface
import com.gould.xputimetable.ui.theme.DarkSurfaceVariant
import com.gould.xputimetable.ui.theme.LightBackground
import com.gould.xputimetable.ui.theme.LightOnPrimary
import com.gould.xputimetable.ui.theme.LightOnBackground
import com.gould.xputimetable.ui.theme.LightOnSurface
import com.gould.xputimetable.ui.theme.LightOnSurfaceVariant
import com.gould.xputimetable.ui.theme.LightPrimary
import com.gould.xputimetable.ui.theme.LightPrimaryContainer
import com.gould.xputimetable.ui.theme.LightSurface
import com.gould.xputimetable.ui.theme.LightSurfaceVariant
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (plan, weekNumber) = runCatching { loadTodayPlan(context) }
            .getOrElse {
                // 装载失败按"无课表"空态渲染，绝不白屏/崩溃
                WidgetLoad(
                    plan = TodayPlanBuilder.build(emptyList(), nowMinute = 0, hasTerm = false),
                    weekNumber = null,
                )
            }
        provideContent {
            GlanceTheme(
                colors = ColorProviders(
                    light = lightColorScheme(
                        primary = LightPrimary,
                        onPrimary = LightOnPrimary,
                        primaryContainer = LightPrimaryContainer,
                        onPrimaryContainer = LightOnBackground,
                        background = LightBackground,
                        onBackground = LightOnBackground,
                        surface = LightSurface,
                        onSurface = LightOnSurface,
                        surfaceVariant = LightSurfaceVariant,
                        onSurfaceVariant = LightOnSurfaceVariant,
                    ),
                    dark = darkColorScheme(
                        primary = DarkPrimary,
                        onPrimary = DarkOnPrimary,
                        primaryContainer = DarkPrimaryContainer,
                        onPrimaryContainer = DarkOnSurface,
                        background = DarkBackground,
                        onBackground = DarkOnBackground,
                        surface = DarkSurface,
                        onSurface = DarkOnSurface,
                        surfaceVariant = DarkSurfaceVariant,
                        onSurfaceVariant = DarkOnSurfaceVariant,
                    ),
                ),
            ) {
                TodayWidgetContent(plan, weekNumber)
            }
        }
        // M4-W R-逐节推进：取数渲染完成后安排「下一门未结束课程」结束时刻的精确刷新；
        // 失败（如精确闹钟权限被撤销）绝不影响渲染
        runCatching { EndOfClassRefreshScheduler.schedule(context, nextEndEpochMilli(plan)) }
    }

    /** 取数结果：视图数据 + 头部周次（无学期/不在学期周内时为 null）。 */
    private data class WidgetLoad(val plan: TodayPlan, val weekNumber: Int?)

    /**
     * 今天下一门**结束**的时刻（epoch 毫秒，systemDefault 时区）；今天课全上完则 null。
     *
     * M7 修正：取 `minOf { endMinute }` 而不是 `remaining.firstOrNull()`。
     * `remaining` 按**开始时间**升序，第一项开课最早但未必最早结束——
     * 例如「A 课 1–4 节」与「B 课 2–3 节」同一天时，B 会在 A 之前结束，
     * 原实现把闹钟挂在 A 的结束时刻，于是 **B 结束时小组件不会刷新**（不满足"每节课结束都刷"）。
     */
    private fun nextEndEpochMilli(plan: TodayPlan): Long? =
        plan.remaining.minOfOrNull { it.endMinute }
            ?.let { LocalDate.now().atStartOfDay().plusMinutes(it.toLong()) }
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()

    private suspend fun loadTodayPlan(context: Context): WidgetLoad {
        val app = context.applicationContext as TimetableApp
        val repository = app.container.repository
        val today = LocalDate.now()
        val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute

        val term = repository.observeActiveTerm().first()
            ?: return WidgetLoad(
                plan = TodayPlanBuilder.build(emptyList(), nowMinute, hasTerm = false),
                weekNumber = null,
            )

        val startDate = runCatching { LocalDate.parse(term.startDate) }.getOrNull()
        val week = startDate?.let { WeekCalc.currentWeek(it, today) }
        // 开学前 / 起始日异常 / 超出学期：不在学期周内 → 「今天没有课」（规格 §2）
            ?: return WidgetLoad(
                plan = TodayPlanBuilder.build(emptyList(), nowMinute, hasTerm = true),
                weekNumber = null,
            )

        val schedule = repository.observeWeek(term.id, week).first()
        val slots = repository.observeTimeSlots().first().associateBy { it.section }
        val todayDow = today.dayOfWeek.value
        val items = schedule.items
            .asSequence()
            .filter { it.session.dayOfWeek == todayDow }
            .mapNotNull { it.toTodayItem(slots) }
            .toList()
        return WidgetLoad(
            plan = TodayPlanBuilder.build(items, nowMinute, hasTerm = true),
            weekNumber = week,
        )
    }

    /** 节次作息缺失时无法给出时间，该条不进卡片（作息已预置，正常不会发生）。 */
    private fun SessionWithCourse.toTodayItem(
        slots: Map<Int, com.gould.xputimetable.domain.model.TimeSlot>,
    ): TodayItem? {
        val startSlot = slots[session.startSection] ?: return null
        val endSlot = slots[session.endSection] ?: startSlot
        return TodayItem(
            startSection = session.startSection,
            endSection = session.endSection,
            courseName = courseName,
            classroom = session.classroom,
            startMinute = startSlot.startMinute,
            endMinute = endSlot.endMinute,
            colorTag = colorTag,
        )
    }
}
