/*
 * SettingsRows.kt —— 「我的」页的分组列表构件（M9）
 *
 * 结构参照系统设置页（产品负责人要求）：分组标题 → 若干条目 → 右侧「>」进二级页。
 * 三种构件：
 *   - SettingsGroupHeader：分组标题（小号灰字）
 *   - SettingsNavRow     ：可进二级页的条目（右侧「>」）
 *   - SettingsActionRow  ：点击即执行的条目（无箭头，如「导出课表文件」）
 * 条目之间用发丝分隔线，由调用方通过 showDivider 控制最后一条不画线。
 *
 * 界面约定：色值只走 colorScheme；尺寸走 ui/theme/Tokens.kt 的 ListRow（禁魔法数字）；
 * 图标只经 AppIcons；文案由调用方以文件级常量传入。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.ListRow

/** 分组标题（小号灰字，靠上留出与上一组的间距）。 */
@Composable
internal fun SettingsGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = ListRow.GroupSpacing, bottom = 4.dp),
    )
}

/** 可进二级页的条目：右侧「>」（与系统设置页一致）。 */
@Composable
internal fun SettingsNavRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    SettingsRowShell(onClick = onClick, showDivider = showDivider) {
        SettingsRowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(ListRow.IconGap))
        Icon(
            painter = painterResource(AppIcons.chevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ListRow.ChevronSize),
        )
    }
}

/** 点击即执行的条目（无箭头）。 */
@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    SettingsRowShell(onClick = onClick, showDivider = showDivider) {
        SettingsRowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
    }
}

/** 条目外壳：统一的触摸目标、留白与发丝分隔线；内容按横向排布（文本可 weight）。 */
@Composable
private fun SettingsRowShell(
    onClick: () -> Unit,
    showDivider: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.MinHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = ListRow.HorizontalPadding, vertical = ListRow.VerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant
                            .copy(alpha = ListRow.DividerAlpha),
                    ),
            )
        }
    }
}

@Composable
private fun SettingsRowTexts(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
