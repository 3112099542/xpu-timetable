/*
 * CourseCard.kt —— 周视图里的一张课程卡
 *
 * 作用：把一条上课安排（已合并课程名与颜色）画成格子里的色块。
 *
 * 设计依据（UIUX 文档 §7.2 / §7.6）：
 *   - 色块即身份：颜色来自课程固定的调色板索引（不随动态取色漂移，见 CoursePalette）；
 *   - 信息分三级：课名（主）→ 教室（次，11sp 下限）→ 不画图标（空间不足时宁可省略）；
 *   - 正在上课的课程用主色描边高亮（对应 Spec AC-01 的"当前/下一节"提示）；
 *   - 卡片之间留 2dp 缝隙换呼吸感（§7.4）。
 *
 * M7 性能（卡片越多越卡，实测定位后移除）：
 *   原实现每张卡片持有 2 个 animateFloatAsState（按压 scale + alpha）、
 *   1 个 MutableInteractionSource 与 1 个 collectIsPressedAsState 订阅。
 *   卡片是全项目数量最多的可组合单元——一天 4~5 节 × 横向预组合 3 页 = 数十张，
 *   于是变成几十个常驻动画状态与状态订阅，每次重组都要逐个检查，
 *   这正是"卡片越多越卡"的主要来源（冷启动首屏曾出现 550ms 单帧）。
 *   课表卡片是密集网格元素，按压缩放带来的收益远小于它的开销，故整体移除；
 *   点击能力保留（无涟漪，符合既定的极简/工业风）。shape 也提升为文件级常量，
 *   避免每张卡片新建 RoundedCornerShape。
 */
package com.gould.xputimetable.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.ui.theme.Corners
import com.gould.xputimetable.ui.theme.CoursePalette

/** 卡片圆角（文件级常量：原先每张卡片都新建一次 RoundedCornerShape）。 */
private val CardShape = RoundedCornerShape(Corners.Card)

/** 正在上课的高亮描边宽度（常量，避免每次组合新建 Dp 比较）。 */
private val NowBorderWidth = 1.5.dp

@Composable
fun CourseCard(
    item: SessionWithCourse,
    isNow: Boolean,
    modifier: Modifier = Modifier,
    onCourseClick: (SessionWithCourse) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val container = CoursePalette.container(item.colorTag, dark)
    val content = CoursePalette.onContainer(item.colorTag, dark)
    // M7 性能：点击回调在卡片内部记忆化。
    // 原先是调用处写 `onClick = { onCourseClick(item) }`——每张卡片每次重组都新建实例，
    // 属于不稳定参数，使 CourseCard 无法跳过重组（卡片越多影响越大）。
    // 现在上层传稳定引用 onCourseClick，lambda 只按 (item, onCourseClick) 建一次。
    val onClick = remember(item, onCourseClick) { { onCourseClick(item) } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(container)
            .then(
                if (isNow) {
                    Modifier.border(NowBorderWidth, MaterialTheme.colorScheme.primary, CardShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Column {
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val classroom = item.session.classroom?.takeIf { it.isNotBlank() }
            if (classroom != null) {
                Text(
                    text = classroom,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
