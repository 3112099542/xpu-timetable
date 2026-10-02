/*
 * WeekSelector.kt —— 周次标题条（M4-UI R1/R4/R6 + M5 需求 4）
 *
 * R1：主标题「第 N 周」后跟周几（由今天决定，与显示周次无关）。
 * R4：「返回本周」常驻顶栏最右，已在当前周时禁用（alpha 0.38），位置恒定不跳动。
 * R6：双击标题区直接回本周；单击不做任何事（避免与双击冲突）。
 * M5 需求 4：原右下 FAB 的「导入 / 添加课程」迁入顶栏右侧（返回本周左边），
 *   只改位置不改点击行为；网格空态的"手动添加 / 导入"引导按钮保留。
 */
package com.gould.xputimetable.ui.timetable.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.R
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.Corners
import com.gould.xputimetable.ui.theme.IconSize
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

/** 1..7 → 「周一」…「周日」；越界返回空串（不抛异常）。 */
fun weekdayCn(dow: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(dow - 1).orEmpty()

@Composable
fun WeekSelector(
    week: Int,
    totalWeeks: Int,
    overridden: Boolean,
    startDate: String?,
    onBackToCurrentWeek: () -> Unit,
    onAddCourse: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题区（R1 + R6）：双击回本周，命中区高度 >= 44dp；单击无动作
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Corners.MinTouchTarget)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onBackToCurrentWeek() }) },
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "第 $week 周",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = weekdayCn(LocalDate.now().dayOfWeek.value),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = weekRangeText(startDate, week) ?: "共 $totalWeeks 周",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 右上动作区（M5 需求 4 / M9 调序）：[添加课程][导入][返回本周]，返回本周仍在最右；
        // 导入/添加 tint = onSurface，返回本周保持原逻辑（可用 primary / 禁用 alpha 0.38）
        Row(verticalAlignment = Alignment.CenterVertically) {
            // M9：添加课程与导入的位置互换（产品负责人要求：添加在前、导入在后）
            IconButton(onClick = onAddCourse) {
                Icon(
                    painter = painterResource(AppIcons.plus),
                    contentDescription = "添加课程",
                    modifier = Modifier.size(IconSize.Medium),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onOpenImport) {
                Icon(
                    painter = painterResource(AppIcons.upload),
                    contentDescription = "导入课表",
                    modifier = Modifier.size(IconSize.Medium),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            // 返回本周（R4）：常驻最右、位置恒定；已在当前周时禁用（视觉 alpha 0.38）
            IconButton(onClick = onBackToCurrentWeek, enabled = overridden) {
                Icon(
                    painter = painterResource(AppIcons.calendarToday),
                    contentDescription = stringResource(R.string.week_back_to_current),
                    modifier = Modifier.size(IconSize.Medium),
                    // M10：非本周高亮改黑色（原 primary），与"蓝色字样改黑"统一
                    tint = if (overridden) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
    }
}

/** 该周周一起 7 天的区间文案；学期起日缺失或解析失败时返回 null。 */
private fun weekRangeText(startDate: String?, week: Int): String? {
    if (startDate.isNullOrBlank()) return null
    return runCatching {
        val monday = WeekCalc.mondayOfWeek(LocalDate.parse(startDate), week)
        val sunday = monday.plusDays(6)
        "${monday.format(monthDayFormatter)} - ${sunday.format(monthDayFormatter)}"
    }.getOrNull()
}
