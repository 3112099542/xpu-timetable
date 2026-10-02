/*
 * FormControls.kt —— 编辑页可复用表单控件（ui/courseedit/components/）
 *
 * 为什么拆出来：编辑页主体若把控件都写在一起会超过 300 行（项目硬约束，Spec §10）。
 * 这些控件本身与"课程编辑"业务无关，只是受主题约束的通用输入控件，独立后可被后续的
 * 导入确认页、设置页复用。
 *
 * 性能约定（2026-09-17）：控件只接收自己需要的**稳定类型**参数（String / Int / 枚举 /
 * Boolean / 方法引用），选项文案用文件级常量而不是每次重组新建 List——
 * List 在 Compose 里属不稳定类型，会让整个控件无法跳过重组，是标签动画掉帧的常见原因之一。
 */
package com.gould.xputimetable.ui.courseedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.ui.theme.CoursePalette

/** 星期文案（文件级常量，避免每次重组新建 List）。 */
private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 单双周文案。 */
private val weekTypeLabels = listOf("全部", "单周", "双周")

/** 小节标题：表单分组用。 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/** 单行文本输入框（课程名 / 教师 / 教室共用）。 */
@Composable
internal fun LabeledField(
    value: String,
    label: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        modifier = modifier,
    )
}

/** 星期选择行：对外用"星期几（1..7）"，索引换算收敛在控件内部。 */
@Composable
internal fun DayOfWeekRow(selected: Int, onSelect: (Int) -> Unit) {
    SingleSelectRow(
        labels = dayLabels,
        selectedIndex = (selected - 1).coerceIn(0, dayLabels.lastIndex),
        onSelectIndex = { index -> onSelect(index + 1) },
    )
}

/** 单双周选择行。 */
@Composable
internal fun WeekTypeRow(selected: WeekType, onSelect: (WeekType) -> Unit) {
    val selectedIndex = when (selected) {
        WeekType.ALL -> 0
        WeekType.ODD -> 1
        WeekType.EVEN -> 2
    }
    SingleSelectRow(
        labels = weekTypeLabels,
        selectedIndex = selectedIndex,
        onSelectIndex = { index ->
            onSelect(
                when (index) {
                    1 -> WeekType.ODD
                    2 -> WeekType.EVEN
                    else -> WeekType.ALL
                },
            )
        },
    )
}

/** 通用单选行（胶囊样式）。 */
@Composable
private fun SingleSelectRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // 外层只负责"够大的触摸目标"（≥48dp 高，可访问性要求），内层保持 32dp 视觉尺寸
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clickable { onSelectIndex(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        // M10：选中态去蓝（原 primaryContainer 底 + primary 边框）→ 中性底 + 黑边框
                        .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 「- 值 +」区间行（节次 / 周次共用）。 */
@Composable
internal fun RangeRow(
    startLabel: String,
    endLabel: String,
    start: Int,
    end: Int,
    range: IntRange,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stepper(label = startLabel, value = start, range = range, onValueChange = onStartChange)
        Text("-", modifier = Modifier.padding(horizontal = 12.dp))
        Stepper(label = endLabel, value = end, range = range, onValueChange = onEndChange)
    }
}

/** 步进器（越界时按钮自动禁用，从源头阻止非法值）。 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, enabled = value > range.first) {
            Text("-")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, enabled = value < range.last) {
            Text("+")
        }
    }
}

/** 12 色课程色板选择行（颜色 = 课程身份，固定色不可随主题漂移）。 */
@Composable
internal fun CourseColorRow(selected: Int, onSelect: (Int) -> Unit) {
    // 12 色分两行：每项触摸目标 44dp（可访问性下限）且 12×44dp 在手机宽度内放不下，
    // 故 6 个一行；色点本身仍是 28dp 视觉尺寸，四周留白参与触摸。
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val rowCount = 2
        val perRow = CoursePalette.size / rowCount
        repeat(rowCount) { row ->
            Row {
                repeat(perRow) { col ->
                    val index = row * perRow + col
                    val isSelected = index == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CoursePalette.base(index))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}
