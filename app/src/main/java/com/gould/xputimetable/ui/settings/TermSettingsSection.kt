/*
 * TermSettingsSection.kt —— 设置页的学期区块（M2-D，从 SettingsScreen 拆出以守 300 行纪律）
 *
 * 学期名只读展示；起始日用 Material3 日期选择器；总周数数字输入；保存走 upsertTerm。
 * DatePicker 的毫秒值是 UTC 口径，与 LocalDate.ofEpochDay 互转（毫秒 / 86_400_000）。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset

private const val LABEL_START_DATE = "起始日（第一周周一）"
private const val LABEL_TOTAL_WEEKS = "总周数"
private const val ACTION_SAVE_TERM = "保存学期"
private const val ACTION_PICK = "选择"
private const val ACTION_CONFIRM = "确定"
private const val ACTION_CANCEL = "取消"
private const val MILLIS_PER_DAY = 86_400_000L

@Composable
internal fun TermSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = state.startDate,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(LABEL_START_DATE) },
        trailingIcon = {
            TextButton(onClick = { showDatePicker = true }) { Text(ACTION_PICK) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.totalWeeksText,
        onValueChange = viewModel::setTotalWeeks,
        label = { Text(LABEL_TOTAL_WEEKS) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = viewModel::saveTerm, modifier = Modifier.fillMaxWidth()) {
        Text(ACTION_SAVE_TERM)
    }

    if (showDatePicker) {
        val initialMillis = remember(state.startDate) {
            runCatching { LocalDate.parse(state.startDate) }
                .getOrNull()
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()?.toEpochMilli()
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        viewModel.setStartDate(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY).toString())
                    }
                    showDatePicker = false
                }) { Text(ACTION_CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(ACTION_CANCEL) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
