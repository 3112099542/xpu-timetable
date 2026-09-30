/*
 * EmptyState.kt —— 空状态引导（Spec AC-04）
 *
 * 作用：没有任何课程时显示引导页，**绝不显示空白网格**。
 *
 * 两个入口都必须是真实的：
 *   - 手动添加：进入课程编辑表单（M1 已可用）；
 *   - 从教务导入：导入中心属 M2，点击后由调用方给出明确说明（"将在下一里程碑开放"），
 *     而不是"点了没反应"——任何按钮都不允许无反馈。
 */
package com.gould.xputimetable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.R

@Composable
fun EmptyState(
    onAddManually: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.empty_state_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_state_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddManually) {
            Text(stringResource(R.string.empty_state_action_manual))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport) {
            Text(stringResource(R.string.empty_state_action_import))
        }
    }
}
