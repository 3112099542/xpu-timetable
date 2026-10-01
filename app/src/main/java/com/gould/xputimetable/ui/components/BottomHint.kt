/*
 * BottomHint.kt —— 底部瞬时提示浮层（M9：由顶部改为底部弹出 + 黑灰底）
 *
 * 沿革：M7 先做成"顶部胶囊"（解决原 Snackbar 挂在页面 Scaffold 上、切页重挂会重放的问题）；
 * M9 按产品负责人要求改为**从下往上弹出**、底色**黑灰**。
 *
 * 承载方仍是导航根层（AppNav 的内容区 Box），因此：
 *   - 不依赖任何页面的 Scaffold → 页面切换不重建宿主，提示不会被重放；
 *   - 作为 Box 最后一个子项绘制 → 盖在页面内容之上。
 * 位置在**内容区底部**而不是屏幕最底：这样有底部导航栏时提示浮在导航栏**上方**，
 * 不会挡住「课表 / 我的」两个 tab。
 *
 * 动效只动 alpha 与位移：从下沿外侧滑入（slideInVertically 位移 = 自身高度）。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gould.xputimetable.ui.theme.Hint
import com.gould.xputimetable.ui.theme.Motion

@Composable
internal fun BottomHint(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) +
            slideInVertically(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) { it },
        exit = fadeOut(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) +
            slideOutVertically(tween(Motion.FastMillis, easing = Motion.EaseOutStandard)) { it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Hint.BottomOffset),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Hint.ContentColor,
                modifier = Modifier
                    .background(
                        color = Hint.ContainerColor,
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
