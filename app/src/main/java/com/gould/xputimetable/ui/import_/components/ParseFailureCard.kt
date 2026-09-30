/*
 * ParseFailureCard.kt —— 解析失败提示（AC-11，页面私有组件）
 *
 * 铁律：不闪退、不丢失已选文件。卡片三件事：
 *   1. 如实展示「解析失败：<结构化原因>」；
 *   2. 显示已选文件名（数据仍在，可原地重试）；
 *   3. 引导兜底通道：换文件 / 手动添加（教务直连失败时同理可用）。
 */
package com.gould.xputimetable.ui.import_.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize

@Composable
internal fun ParseFailureCard(
    reason: String,
    fileName: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onPickAnother: () -> Unit,
    onManualAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(AppIcons.circleAlert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(IconSize.Medium),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (fileName != null) {
                Text(
                    text = "已选文件：$fileName（数据未丢失，可换文件或重试）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp, start = 28.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickAnother) { Text("换一个文件") }
                if (canRetry) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.padding(start = 8.dp)) {
                        Text("重新解析")
                    }
                }
                TextButton(onClick = onManualAdd, modifier = Modifier.padding(start = 8.dp)) {
                    Text("手动添加")
                }
            }
        }
    }
}
