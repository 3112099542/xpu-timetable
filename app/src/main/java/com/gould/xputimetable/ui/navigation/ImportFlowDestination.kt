/*
 * ImportFlowDestination.kt —— 编辑 / 导入这一组的装配（从 AppNav 拆出）
 *
 * 与 ProfileFlowDestination.kt 同构：只负责「给当前屏幕补依赖 + 创建 ViewModel」，
 * 不持有返回栈、不参与返回判定、不碰返回方向（转场方向在 AppNav 里统一管）。
 * 拆出去的实际原因是门禁——AppNav 是个装配型「大 when」，每加一个目的地就长十几行。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.courseedit.CourseEditScreen
import com.gould.xputimetable.ui.courseedit.CourseEditViewModel
import com.gould.xputimetable.ui.import_.CleanupScreen
import com.gould.xputimetable.ui.import_.CleanupViewModel
import com.gould.xputimetable.ui.import_.ImportPreviewScreen
import com.gould.xputimetable.ui.import_.ImportPreviewViewModel
import com.gould.xputimetable.ui.web.CourseCaptureScreen
import com.gould.xputimetable.ui.web.CourseCaptureViewModel

@Composable
internal fun ImportFlowDestination(
    screen: AppScreen,
    repository: TimetableRepository,
    jsonFileImporter: ScheduleImporter,
    xpuImporter: ScheduleImporter,
    /** M3：数据变更（编辑/导入/清理）→ 重排提醒 + 立即刷新小组件（AC-18） */
    onDataChanged: suspend () -> Unit,
    /** 跨页提示的文本（导入中心显示清理页回传的「已清理 N 门」），由 AppNav 持有 */
    hubNotice: String?,
    /** 提示被消费掉（导入中心显示后清空，避免下次进页又弹一次） */
    onHubNoticeShown: () -> Unit,
    /** 回传跨页提示（清理页完成后交给 AppNav 转给导入中心） */
    onNotice: (String) -> Unit,
    navigateTo: (AppScreen) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    when (screen) {
        is AppScreen.CourseEdit -> {
            // 数据变更触发点（M2-D 时机② + M3 AC-18）：保存/删除成功后重排闹钟并刷新小组件
            //
            // M10：**必须按 target 传 key**。viewModel() 不传 key 时按"类名"取实例，
            // 同一 Activity 的 ViewModelStore 下所有 CourseEdit 共用一个 VM →
            // "点课程 A 进编辑 → 返回 → 点添加课程"会复用还装着 A 的 VM
            // （配合 prepareNew 的旧守卫就表现为"添加课程页显示上一门课的数据"）。
            val targetKey = when (val t: EditTarget = screen.target) {
                is EditTarget.New -> "course-edit-new"
                is EditTarget.Edit -> "course-edit-${t.courseId}-${t.sessionId}"
            }
            val vm: CourseEditViewModel = viewModel(
                key = targetKey,
                factory = simpleFactory { CourseEditViewModel(repository, onDataChanged) },
            )
            LaunchedEffect(screen) {
                when (val t: EditTarget = screen.target) {
                    is EditTarget.New -> vm.prepareNew(t.termId, t.week, t.totalWeeks)
                    is EditTarget.Edit -> vm.load(t.courseId, t.sessionId)
                }
            }
            CourseEditScreen(
                viewModel = vm,
                title = if (screen.target is EditTarget.New) "添加课程" else "编辑课程",
                onBack = onBack,
            )
        }

        AppScreen.ImportHub -> ImportHubDestination(
            repository = repository,
            jsonFileImporter = jsonFileImporter,
            notice = hubNotice,
            onNoticeShown = onHubNoticeShown,
            onBack = onBack,
            onOpenWeb = { navigateTo(AppScreen.CourseCapture) },
            onOpenCleanup = { navigateTo(AppScreen.Cleanup) },
            onManualAdd = { termId, totalWeeks ->
                navigateTo(AppScreen.CourseEdit(EditTarget.New(termId, 1, totalWeeks)))
            },
            onParsed = { needsConfirm -> navigateTo(AppScreen.ImportPreview(needsConfirm)) },
        )

        AppScreen.Cleanup -> {
            // 数据变更触发点（M2-D 时机② + M3 AC-18）：清理删除后重排闹钟并刷新小组件
            val vm: CleanupViewModel = viewModel(
                factory = simpleFactory { CleanupViewModel(repository, onDataChanged) },
            )
            CleanupScreen(
                viewModel = vm,
                onBack = onBack,
                onCleaned = { count ->
                    onNotice("已清理 $count 门")
                    onBack()
                },
            )
        }

        AppScreen.CourseCapture -> {
            val vm: CourseCaptureViewModel = viewModel(
                factory = simpleFactory { CourseCaptureViewModel(xpuImporter) },
            )
            CourseCaptureScreen(
                viewModel = vm,
                onBack = onBack,
                onParsed = { needsConfirm -> navigateTo(AppScreen.ImportPreview(needsConfirm)) },
            )
        }

        is AppScreen.ImportPreview -> {
            // 覆盖范围按本批数据的来源计算（WEB / FILE_JSON，预览页已泛化）；
            // commit 走与解析同通道的 importer（两者最终都汇入 applyImport 同一入口）。
            // M9：WakeUp CSV 通道删除后，非 WEB 的数据一律走本 App 的 JSON 通道
            val source = screen.needsConfirm.parsed.courses.firstOrNull()?.source
                ?: CourseSource.FILE_JSON
            val importer = when (source) {
                CourseSource.WEB -> xpuImporter
                else -> jsonFileImporter
            }
            val vm: ImportPreviewViewModel = viewModel(
                factory = simpleFactory { ImportPreviewViewModel(importer, repository, onDataChanged) },
            )
            LaunchedEffect(screen) { vm.load(screen.needsConfirm, source) }
            ImportPreviewScreen(
                viewModel = vm,
                onBack = onBack,
                onDone = onDone,
            )
        }

        else -> Unit
    }
}
