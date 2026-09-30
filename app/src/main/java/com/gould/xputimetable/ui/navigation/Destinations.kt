/*
 * Destinations.kt —— 导航目标类型与目的地装配（M6 自 AppNav.kt 拆出，守 300 行门禁）
 *
 * 内容：EditTarget / AppScreen（含 M6 新增的 QrShare 目的地）/ simpleFactory 工厂 /
 * ProfileDestination（「我的」页目的地，原 AppNav 最长的 when 分支）。
 * 与 AppNav.kt 同包（ui.navigation），互引无需 import。
 */
package com.gould.xputimetable.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.import_.ImportHubScreen
import com.gould.xputimetable.ui.import_.ImportHubViewModel
import com.gould.xputimetable.ui.import_.readImportPayload
import com.gould.xputimetable.ui.settings.SettingsScreen
import com.gould.xputimetable.ui.settings.SettingsViewModel
import com.gould.xputimetable.ui.transfer.readQrImagePayload
import kotlinx.coroutines.launch

// M6：JSON 文件与二维码截图的 SAF 选择 MIME（JSON 放宽到 text/plain 与 octet-stream：
// 实测部分文件管理器把 .json 报成 octet-stream）
private val MIME_JSON_FILES = arrayOf("application/json", "text/plain", "application/octet-stream")
private val MIME_IMAGES = arrayOf("image/*")

/** 编辑目标：New = 新增课程；Edit = 编辑既有课程。 */
internal sealed interface EditTarget {
    data class New(val termId: Long?, val week: Int, val totalWeeks: Int) : EditTarget
    data class Edit(val courseId: String, val sessionId: Long) : EditTarget
}

/** 全部页面状态（M4-UI 起为七状态；M6 增 QrShare：二维码分享页）。 */
internal sealed interface AppScreen {
    data object Timetable : AppScreen
    data class CourseEdit(val target: EditTarget) : AppScreen
    data object ImportHub : AppScreen
    data object CourseCapture : AppScreen
    data object Cleanup : AppScreen
    data class ImportPreview(val needsConfirm: ImportResult.NeedsConfirm) : AppScreen
    data object Profile : AppScreen
    data object QrShare : AppScreen
}

/** 简易 ViewModel 工厂（避免为每个 ViewModel 各写一个匿名对象）。 */
internal inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** 「我的」页目的地（M4-UI R7）：承载原设置内容 + M6 的导出/二维码入口。根页无返回箭头。 */
@Composable
internal fun ProfileDestination(
    repository: TimetableRepository,
    /** 精确闹钟授权检查（M7：小组件刷新精度依赖它）。 */
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    onShareQr: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(
        factory = simpleFactory {
            SettingsViewModel(
                repository = repository,
                canScheduleExact = canScheduleExact,
                onTermChanged = onDataChanged,
            )
        },
    )
    SettingsScreen(
        viewModel = vm,
        repository = repository,
        onShareQr = onShareQr,
    )
}

/** 导入中心目的地（M6 起含 JSON 文件与二维码截图两条新通道；SAF launcher 在此装配）。 */
@Composable
internal fun ImportHubDestination(
    repository: TimetableRepository,
    wakeupImporter: ScheduleImporter,
    jsonFileImporter: ScheduleImporter,
    notice: String?,
    onNoticeShown: () -> Unit,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenCleanup: () -> Unit,
    onManualAdd: (termId: Long?, totalWeeks: Int) -> Unit,
    onParsed: (ImportResult.NeedsConfirm) -> Unit,
) {
    val vm: ImportHubViewModel = viewModel(
        factory = simpleFactory { ImportHubViewModel(wakeupImporter, jsonFileImporter, repository) },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // M6 需求 6：两条新通道的 SAF launcher；读取/解图在 UI 层完成后
    // 以普通文本载荷走 submitJson（importer 不感知二维码）
    val pickJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = readImportPayload(context, uri)
            if (payload == null) vm.markReadFailure(null) else vm.submitJson(payload)
        }
    }
    val pickQrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = readQrImagePayload(context, uri)
            if (payload == null) vm.markReadFailure(null) else vm.submitJson(payload)
        }
    }
    ImportHubScreen(
        viewModel = vm,
        onBack = onBack,
        onOpenWeb = onOpenWeb,
        onOpenCleanup = onOpenCleanup,
        onManualAdd = onManualAdd,
        onPickJsonFile = { pickJsonLauncher.launch(MIME_JSON_FILES) },
        onPickQrImage = { pickQrLauncher.launch(MIME_IMAGES) },
        onParsed = onParsed,
        notice = notice,
        onNoticeShown = onNoticeShown,
    )
}
