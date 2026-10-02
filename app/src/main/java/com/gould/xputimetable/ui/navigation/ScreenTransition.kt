/*
 * ScreenTransition.kt —— 页面转场的方向与时长（从 AppNav 拆出）
 *
 * 作用：只回答一件事「新页面从哪里进来」。拆出去的实际原因是门禁——AppNav 是个
 * 装配型「大 when」，每加一个目的地就长十几行，很快顶到单文件 300 行上限；
 * 转场方向和具体装配无关，单独成文件后一眼能看懂动画是怎么来的。
 *
 * 方向约定（M10 老大需求）：
 *   - 前进 navForward=true  ：自下而上 +height/8 → 有"进入下一层"的方向感
 *   - 返回 navForward=false ：自上而下 -height/8 → 与前进相反，方向本身就是"返回"的提示
 * 入场/出场共用同一套缓动（Motion.EaseOutStandard），避免"有的快有的慢"的观感。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import com.gould.xputimetable.ui.theme.Motion

/**
 * 转场规格（只动位移与透明度，与项目「只动 transform 与 alpha」的动效令牌一致）。
 *
 * @param navForward 见文件头；true = 前进（自下而上），false = 返回（自上而下）
 * @param animated   false 时时长归零（首帧/测试用，不会看到动画）
 */
internal fun screenTransition(
    navForward: Boolean,
    animated: Boolean = true,
): ContentTransform {
    val enterDur = if (animated) Motion.BaseMillis else 0
    val exitDur = if (animated) Motion.FastMillis else 0
    val enterOffset: (Int) -> Int = if (navForward) {
        { fullHeight -> fullHeight / 8 }
    } else {
        { fullHeight -> -fullHeight / 8 }
    }
    return (fadeIn(tween(enterDur, easing = Motion.EaseOutStandard)) +
        slideInVertically(tween(enterDur, easing = Motion.EaseOutStandard), enterOffset))
        .togetherWith(fadeOut(tween(exitDur, easing = Motion.EaseOutStandard)))
}
