/*
 * ImportPreviewCards.kt —— 预览确认页的信息卡片（页面私有组件）
 *
 * 覆盖范围卡（AC-13）与疑似重复卡（AC-21）是本页的合规重点：
 * 用户必须在确认前看到「会替换什么、不会动什么」。全部组件只收稳定类型参数。
 */
package com.gould.xputimetable.ui.import_.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.ImportMode

/** 顶部双统计：课程数 / 安排条数（大数字 + 说明，一眼读出导入体量）。 */
@Composable
internal fun PreviewStatRow(courseCount: Int, sessionCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatBadge(value = courseCount, label = "门课程", modifier = Modifier.weight(1f))
        StatBadge(value = sessionCount, label = "条上课安排", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatBadge(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 覆盖范围卡（AC-13 / M7 需求 1）：按入库方式明示「会替换什么、会保留什么」。 */
@Composable
internal fun CoverageCard(
    existingCount: Int,
    sourceLabel: String,
    mode: ImportMode,
    modifier: Modifier = Modifier,
) {
    val label = sourceLabel.ifEmpty { "导入" }
    val scopeLine = when (mode) {
        ImportMode.REPLACE ->
            if (existingCount > 0) {
                "将清空本学期现有导入课程 $existingCount 门，用本次内容替换"
            } else {
                "本学期暂无可清空的导入课程（本次为全新导入）"
            }
        ImportMode.APPEND ->
            if (existingCount > 0) {
                "现有导入课程 $existingCount 门全部保留，本次课程追加在后"
            } else {
                "本学期暂无导入课程，本次课程将直接写入"
            }
    }
    InfoCard(
        title = "覆盖范围（本次来源：$label）",
        lines = listOf(scopeLine, "手动添加的课程不会被删除"),
        modifier = modifier,
    )
}

// ---------- M7 需求 1：导入方式选择 ----------

private const val MODE_TITLE = "导入方式"
private const val MODE_REPLACE = "覆盖原课表"
private const val MODE_APPEND = "插入原课表"
private const val MODE_HINT_REPLACE = "清空本学期现有导入课程，用本次内容替换（手动添加的课程保留）"
private const val MODE_HINT_APPEND = "保留现有课表，把本次课程追加进去（同名课程会并列显示）"

/**
 * 入库方式二选一（M7 需求 1）。
 *
 * 用 M3 的单选分段按钮，选中项与下方说明同步变化——用户在按「确认导入」之前，
 * 能清楚知道这个选择会怎么改动课表。
 */
@Composable
internal fun ImportModeSelector(
    mode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 稳定化点击回调（项目纪律：禁内联 lambda 传给子组件）
    val onReplaceClick = remember(onModeChange) { { onModeChange(ImportMode.REPLACE) } }
    val onAppendClick = remember(onModeChange) { { onModeChange(ImportMode.APPEND) } }
    Column(modifier.fillMaxWidth()) {
        Text(text = MODE_TITLE, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.padding(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == ImportMode.REPLACE,
                onClick = onReplaceClick,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(text = MODE_REPLACE) }
            SegmentedButton(
                selected = mode == ImportMode.APPEND,
                onClick = onAppendClick,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(text = MODE_APPEND) }
        }
        Text(
            text = if (mode == ImportMode.REPLACE) MODE_HINT_REPLACE else MODE_HINT_APPEND,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 疑似重复卡（AC-21）：同名手动课程只提示，不自动合并。 */
@Composable
internal fun DuplicateCard(duplicateNames: List<String>, modifier: Modifier = Modifier) {
    if (duplicateNames.isEmpty()) return
    InfoCard(
        title = "疑似重复 ${duplicateNames.size} 门",
        lines = listOf("与手动添加的课程同名（不会自动合并）：") + duplicateNames,
        modifier = modifier,
    )
}

/** 异常条目卡：脏行如实展示（AC-10 预览内容之一）。 */
@Composable
internal fun AnomalyCard(anomalies: List<String>, modifier: Modifier = Modifier) {
    if (anomalies.isEmpty()) return
    InfoCard(
        title = "已跳过 ${anomalies.size} 条异常",
        lines = anomalies,
        modifier = modifier,
    )
}

@Composable
private fun InfoCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.padding(4.dp))
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
