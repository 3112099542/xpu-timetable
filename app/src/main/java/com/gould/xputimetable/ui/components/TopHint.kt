/*
 * TopHint.kt —— 顶部瞬时提示浮层（M7：退出提示现代化 + 全局置顶）
 *
 * 背景：原先"再按一次退出应用"复用周视图 Scaffold 的 SnackbarHost，有两个缺陷：
 *   ① 宿主在**页面内部**——切到「我的」再切回周视图时 Scaffold 重挂，未过期的
 *      Snackbar 会被重新显示一次（用户看到"切回来又冒出来"）；
 *   ② 位置在内容区底部（底部导航栏之上），与"系统级提示"的视觉层级不符。
 *
 * 本组件由导航根层（AppNav 最外层 Box，Alignment.TopCenter）持有：
 *   - 不依赖任何页面的 Scaffold → 页面切换不会重建它，提示按自己的节奏消失；
 *   - 顶部居中 + statusBarsPadding() 避让状态栏 → 全局置顶。
 *
 * 视觉：全弧度胶囊 + inverseSurface/inverseOnSurface（M3 为"瞬时浮层"定义的角色色，
 * 非纯黑/纯白，符合设计约定），无图标、无阴影堆叠（工业极简调性）。
 * 动效只动 alpha 与位移，时长沿用 Motion.FastMillis。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gould.xputimetable.ui.theme.Hint
import com.gould.xputimetable.ui.theme.Motion

@Composable
internal fun TopHint(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) +
            slideInVertically(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) { -it / 2 },
        exit = fadeOut(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = Hint.TopOffset),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(Hint.CornerRadius),
                    )
                    .padding(
                        horizontal = Hint.HorizontalPadding,
                        vertical = Hint.VerticalPadding,
                    ),
            )
        }
    }
}
