/*
 * ExportSection.kt —— 「我的」页的课表导出区块（M6 需求 6-A/6-B）
 *
 * 自 SettingsScreen 拆出（SettingsScreen 已 259 行，内联会破 300 行门禁）。
 * 两个条目：导出课表文件（SAF CreateDocument 落 .json）与 二维码分享课表（导航到 QrShareScreen）。
 * 写文件在 Dispatchers.IO；结果用既有 Snackbar 提示（不新增 Toast 组件）。
 * 图标复用既有 AppIcons（upload / fileText），不新增图标资源。
 */
package com.gould.xputimetable.ui.transfer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.data.transfer.ScheduleCodec
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- 文件级文案常量（Spec §10 铁律 2）----------

private const val EXPORT_FILE_TITLE = "导出课表文件"
private const val EXPORT_FILE_SUBTITLE = "保存为 .json，可发给同学或备份"
private const val QR_SHARE_ENTRY_TITLE = "二维码分享课表"
private const val QR_SHARE_ENTRY_SUBTITLE = "生成一张二维码，对方扫码即可导入"
private const val EXPORT_FILE_NAME = "xpu-timetable.json"
private const val EXPORT_DONE = "课表已导出"
private const val EXPORT_EMPTY = "还没有课表可导出"
private const val EXPORT_FAILED_PREFIX = "导出失败"

@Composable
internal fun ExportSection(
    repository: TimetableRepository,
    onShareQr: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching { repository.exportSnapshot() }
            val snapshot = result.getOrNull()
            when {
                result.isFailure ->
                    snackbarHostState.showSnackbar("$EXPORT_FAILED_PREFIX：${result.exceptionOrNull()?.message ?: "未知错误"}")
                snapshot == null ->
                    snackbarHostState.showSnackbar(EXPORT_EMPTY)
                else -> {
                    runCatching {
                        val json = ScheduleCodec.encode(snapshot)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(json.toByteArray())
                            } ?: error("无法写入所选位置")
                        }
                    }.onSuccess { snackbarHostState.showSnackbar(EXPORT_DONE) }
                        .onFailure {
                            snackbarHostState.showSnackbar("$EXPORT_FAILED_PREFIX：${it.message ?: "未知错误"}")
                        }
                }
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        ExportEntry(
            title = EXPORT_FILE_TITLE,
            subtitle = EXPORT_FILE_SUBTITLE,
            iconRes = AppIcons.upload,
        ) { exportLauncher.launch(EXPORT_FILE_NAME) }
        ExportEntry(
            title = QR_SHARE_ENTRY_TITLE,
            subtitle = QR_SHARE_ENTRY_SUBTITLE,
            iconRes = AppIcons.fileText,
            onClick = onShareQr,
        )
    }
}

/** 「更多」区可点条目：图标 + 标题/说明，整行可点（触摸目标远超 44dp 下限）。 */
@Composable
private fun ExportEntry(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.Large),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
