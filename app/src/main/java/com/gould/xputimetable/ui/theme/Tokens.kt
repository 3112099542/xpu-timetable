/*
 * Tokens.kt —— 设计令牌（M4-UI 规格 §3）
 *
 * 作用：把动效时长/缓动、圆角与触摸目标、网格尺寸收敛到唯一事实源，
 * 组件禁止再写魔法数字。WeekGrid 与 DayHeader 的左轴宽度必须同源（Grid.AxisWidth），
 * 防止将来只改一处导致表头与网格错位。
 *
 * 备注：规格写「课程卡现状 8dp，保持不变」，但实测现状为 4dp（CourseCard.kt），
 * 按"保持不变"的意图令牌取实际值 4dp（见回传报告）。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/** 动效令牌（M5-UI 放慢一档：微交互 200 / 常规过渡 450 / 场景过渡 800）。 */
object Motion {
    const val FastMillis = 200          // 按压、悬停反馈
    const val BaseMillis = 450           // 切周、页面切换
    const val SceneMillis = 800          // 首屏编排（仅首次进入）
    val EaseOutStandard = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/** 圆角与尺寸令牌。 */
object Corners {
    val Card = 4.dp          // 课程卡（沿用现状实测值，见文件头备注）
    val TodayMark = 8.dp      // 今天日期方块
    val Pill = 28.dp          // 全弧度胶囊（顶部瞬时提示）
    val MinTouchTarget = 44.dp   // 手册硬性要求：≥44dp
}

/**
 * 顶部瞬时提示浮层（M7：退出提示现代化）。
 *
 * 独立成组而不是塞进 Corners：它是"浮层"这一类语义的完整尺寸集合，
 * 后续同类浮层（如导入结果提示）可直接复用同一组值。
 */
object Hint {
    val CornerRadius = 28.dp        // 全弧度胶囊
    val HorizontalPadding = 24.dp
    val VerticalPadding = 12.dp
    val TopOffset = 8.dp            // 状态栏下沿再下移的量
    /** 驻留时长：略短于 BackPolicy.EXIT_WINDOW_MILLIS(2s)，让"提示消失"先于"可退出"。 */
    const val VisibleMillis = 1_800L
}

/** 网格尺寸（与既有实现保持一致，集中定义避免散落）。 */
object Grid {
    val RowHeight = 52.dp
    val AxisWidth = 38.dp    // WeekGrid 与 DayHeader 必须同源
}
