/*
 * ExportSection.kt —— 「我的」页的课表导出区块（M6 需求 6-A/6-B；M9 改样式与提示通道）
 *
 * 两个条目：导出课表文件（SAF CreateDocument 落 .json）与 二维码分享课表（导航到 QrShareScreen）。
 * 写文件在 Dispatchers.IO。
 *
 * M9 变更：
 *   - 条目改用「我的」页统一的行样式（SettingsActionRow / SettingsNavRow），
 *     与系统设置页的分组列表一致（不再用带彩色前置图标的卡片式条目）；
 *   - 结果提示改走导航层的**底部提示通道**（onShowHint），不再用页面级 Snackbar ——
 *     后者挂在页面 Scaffold 上，切页重挂会把未过期的提示重放一次。
 */
package com.gould.xputimetable.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gould.xputimetable.data.transfer.ScheduleCodec
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.settings.SettingsActionRow
import com.gould.xputimetable.ui.settings.SettingsNavRow
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
private const val WRITE_FAILED = "无法写入所选位置"

@Composable
internal fun ExportSection(
    repository: TimetableRepository,
    /** 全局底部提示（导出结果）。 */
    onShowHint: (String) -> Unit,
    onShareQr: () -> Unit,
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
                    onShowHint("$EXPORT_FAILED_PREFIX：${result.exceptionOrNull()?.message ?: "未知错误"}")
                snapshot == null -> onShowHint(EXPORT_EMPTY)
                else -> {
                    runCatching {
                        val json = ScheduleCodec.encode(snapshot)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(json.toByteArray())
                            } ?: error(WRITE_FAILED)
                        }
                    }.onSuccess { onShowHint(EXPORT_DONE) }
                        .onFailure { onShowHint("$EXPORT_FAILED_PREFIX：${it.message ?: "未知错误"}") }
                }
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsActionRow(
            title = EXPORT_FILE_TITLE,
            subtitle = EXPORT_FILE_SUBTITLE,
            onClick = { exportLauncher.launch(EXPORT_FILE_NAME) },
        )
        SettingsNavRow(
            title = QR_SHARE_ENTRY_TITLE,
            subtitle = QR_SHARE_ENTRY_SUBTITLE,
            onClick = onShareQr,
            showDivider = false,
        )
    }
}
