/*
 * AppNav.kt —— 应用内状态导航（M2-A：状态栈，不引入 Navigation 3）
 *
 * 决策依据（OPEN-DECISIONS.md 2026-09-17「M2 导航」）：navigation3 1.2.0 仍为 RC，
 * 沿用 MainActivity 的 sealed 状态导航模式并扩展（目标类型与 simpleFactory / ProfileDestination
 * 已拆至同包 Destinations.kt，M6 为守 300 行门禁）。
 * M6：新增 QrShare 目的地；ImportHub 挂两条新通道（JSON 文件 / 二维码截图），SAF launcher
 *   在本文件装配，读取/解图在 UI 层完成后以普通文本载荷走 vm.submitJson（importer 不感知二维码）；
 *   ImportPreview 的 commit 分派改三分支（FILE_JSON → jsonFileImporter，消除隐式耦合）。
 * 本文件只做装配（状态切换 + ViewModel 创建），不含业务逻辑。
 * M7：二次返回退出的提示由"周视图 Scaffold 的 Snackbar"改为**导航根层的顶部浮层**
 *   （ui/components/TopHint.kt）——原方案随页面切换重挂会重放提示，且位置在底栏上方；
 *   现在宿主在导航层，切换页面不影响，且全局置顶。
 * 解析结果（NeedsConfirm）经导航状态传递给预览页；进程死亡会回到周视图，
 * 与既有状态导航的行为一致（未用 rememberSaveable 持久化）。
 */
package com.gould.xputimetable.ui.navigation

import android.provider.Settings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.components.TopHint
import com.gould.xputimetable.ui.courseedit.CourseEditScreen
import com.gould.xputimetable.ui.courseedit.CourseEditViewModel
import com.gould.xputimetable.ui.import_.CleanupScreen
import com.gould.xputimetable.ui.import_.CleanupViewModel
import com.gould.xputimetable.ui.import_.ImportPreviewScreen
import com.gould.xputimetable.ui.import_.ImportPreviewViewModel
import com.gould.xputimetable.ui.theme.Hint
import com.gould.xputimetable.ui.theme.Motion
import com.gould.xputimetable.ui.timetable.TimetableScreen
import com.gould.xputimetable.ui.timetable.TimetableViewModel
import com.gould.xputimetable.ui.transfer.QrShareScreen
import com.gould.xputimetable.ui.web.CourseCaptureScreen
import com.gould.xputimetable.ui.web.CourseCaptureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** 二次返回退出的提示文案（M7：由 Snackbar 改为顶部浮层，文案集中于此）。 */
private const val EXIT_HINT = "再按一次退出应用"

@Composable
internal fun AppNav(
    repository: TimetableRepository,
    wakeupImporter: ScheduleImporter,
    jsonFileImporter: ScheduleImporter,
    xpuImporter: ScheduleImporter,
    canScheduleExact: () -> Boolean,
    /** M3：数据变更（编辑/导入/清理/学期）→ 重排提醒 + 立即刷新小组件（AC-18）。 */
    onDataChanged: suspend () -> Unit,
) {
    // 最小返回栈：栈底是首页；进入新页 push，返回 pop，导入完成回栈底（顺带修掉"导入中心→手动添加"的返回去向 bug）
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Timetable) }
    fun navigateTo(next: AppScreen) { backStack.add(next) }
    fun goBack() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun popToRoot() { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val screen: AppScreen = backStack.last()

    // 点击提醒通知 / singleTop 复用：MainActivity 发布"回到课表"事件 → 回到返回栈栈底（周视图）。

    // 导航层持有的跨页提示（M2-C：清理完成后送回导入中心的 Snackbar）
    var hubNotice by remember { mutableStateOf<String?>(null) }

    // 首页二次返回退出：退出窗口由 BackPolicy 独立管理；提示本身渲染在导航根层的 TopHint 上
    // （见下方内容区 Box 内），**不再复用页面 Scaffold 的 SnackbarHost** ——
    // 那样会随页面切换重挂而重放提示（M7 修复）。
    val snackbarHostState = remember { SnackbarHostState() }
    var lastHintAt by remember { mutableStateOf<Long?>(null) }
    var exitHintVisible by remember { mutableStateOf(false) }
    // 每次按键自增：让驻留计时协程重启（连按会重新计时），也保证同一毫秒内连按仍触发刷新
    var exitHintNonce by remember { mutableIntStateOf(0) }

    // 提示的显示 → 驻留 → 隐藏集中在这一处；按键回调只负责 nonce++
    LaunchedEffect(exitHintNonce) {
        if (exitHintNonce == 0) return@LaunchedEffect
        exitHintVisible = true
        delay(Hint.VisibleMillis)
        exitHintVisible = false
    }

    // 系统返回键接线：canonical 模式，建一次（remember），闭包读栈深/提示时间取最新值
    val dispatcherOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
    val dispatcher = dispatcherOwner.onBackPressedDispatcher
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (BackPolicy.decide(backStack.size, lastHintAt, System.currentTimeMillis())) {
                    BackAction.Pop -> goBack()
                    BackAction.HintAndArm -> {
                        lastHintAt = System.currentTimeMillis()
                        exitHintNonce++
                    }
                    BackAction.Exit -> {
                        // 交回系统默认：保留退出动画；不再设回 enabled（依赖 Activity 重建恢复）
                        isEnabled = false
                        dispatcher.onBackPressed()
                    }
                }
            }
        }
    }
    DisposableEffect(dispatcher) {
        dispatcher.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }

    // M4-UI R7：内容区 + 底部导航（仅两个根页显示，二级页保持沉浸）
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            // M5 §3.2.4/§3.2.8：页面切换淡入 450ms + 1/8 高度上移、淡出 200ms；系统「减少动画」时直切
            val resolver = LocalContext.current.contentResolver
            val animated = remember(resolver) {
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
            }
            val enterDur = if (animated) Motion.BaseMillis else 0
            val exitDur = if (animated) Motion.FastMillis else 0
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(enterDur, easing = Motion.EaseOutStandard)) +
                        slideInVertically(tween(enterDur, easing = Motion.EaseOutStandard)) { it / 8 })
                        .togetherWith(fadeOut(tween(exitDur, easing = Motion.EaseOutStandard)))
                },
                label = "screenTransition",
            ) { current: AppScreen ->
                when (current) {
                    AppScreen.Timetable -> {
                        val vm: TimetableViewModel = viewModel(factory = simpleFactory { TimetableViewModel(repository) })
                        val state = vm.uiState.value
                        TimetableScreen(
                            viewModel = vm,
                            snackbarHostState = snackbarHostState,
                            onAddCourse = { navigateTo(AppScreen.CourseEdit(EditTarget.New(state.term?.id, state.week, state.totalWeeks))) },
                            onEditCourse = { courseId, sessionId ->
                                navigateTo(AppScreen.CourseEdit(EditTarget.Edit(courseId, sessionId)))
                            },
                            onOpenImport = { navigateTo(AppScreen.ImportHub) },
                        )
                    }

                    is AppScreen.CourseEdit -> {
                        // 数据变更触发点（M2-D 时机② + M3 AC-18）：保存/删除成功后重排闹钟并刷新小组件
                        val vm: CourseEditViewModel = viewModel(
                            factory = simpleFactory { CourseEditViewModel(repository, onDataChanged) },
                        )
                        LaunchedEffect(current) {
                            when (val t: EditTarget = current.target) {
                                is EditTarget.New -> vm.prepareNew(t.termId, t.week, t.totalWeeks)
                                is EditTarget.Edit -> vm.load(t.courseId, t.sessionId)
                            }
                        }
                        CourseEditScreen(
                            viewModel = vm,
                            title = if (current.target is EditTarget.New) "添加课程" else "编辑课程",
                            onBack = ::goBack,
                        )
                    }

                    AppScreen.ImportHub -> ImportHubDestination(
                        repository = repository,
                        wakeupImporter = wakeupImporter,
                        jsonFileImporter = jsonFileImporter,
                        notice = hubNotice,
                        onNoticeShown = { hubNotice = null },
                        onBack = ::goBack,
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
                            onBack = ::goBack,
                            onCleaned = { count ->
                                hubNotice = "已清理 $count 门"
                                goBack()
                            },
                        )
                    }

                    AppScreen.CourseCapture -> {
                        val vm: CourseCaptureViewModel = viewModel(
                            factory = simpleFactory { CourseCaptureViewModel(xpuImporter) },
                        )
                        CourseCaptureScreen(
                            viewModel = vm,
                            onBack = ::goBack,
                            onParsed = { needsConfirm -> navigateTo(AppScreen.ImportPreview(needsConfirm)) },
                        )
                    }

                    is AppScreen.ImportPreview -> {
                        // 覆盖范围按本批数据的来源计算（WEB / WAKEUP_CSV / FILE_JSON，预览页已泛化）；
                        // commit 走与解析同通道的 importer（两者最终都汇入 applyImport 同一入口）。
                        // M6：三分支显式分派——原先 FILE_JSON 会落到 wakeupImporter（隐式耦合）
                        val source = current.needsConfirm.parsed.courses.firstOrNull()?.source
                            ?: CourseSource.WAKEUP_CSV
                        val importer = when (source) {
                            CourseSource.WEB -> xpuImporter
                            CourseSource.FILE_JSON -> jsonFileImporter
                            else -> wakeupImporter
                        }
                        val vm: ImportPreviewViewModel = viewModel(
                            factory = simpleFactory { ImportPreviewViewModel(importer, repository, onDataChanged) },
                        )
                        LaunchedEffect(current) { vm.load(current.needsConfirm, source) }
                        ImportPreviewScreen(
                            viewModel = vm,
                            onBack = ::goBack,
                            onDone = ::popToRoot,
                        )
                    }

                    AppScreen.Profile -> ProfileDestination(
                        repository = repository,
                        canScheduleExact = canScheduleExact,
                        onDataChanged = onDataChanged,
                        onShareQr = { navigateTo(AppScreen.QrShare) },
                    )

                    AppScreen.QrShare -> {
                        QrShareScreen(
                            repository = repository,
                            onBack = ::goBack,
                        )
                    }
                }
            }

            // 退出提示（M7）：挂在导航层这个 Box 上（**页面 Scaffold 之外**），因此
            //   ① 页面切换不会重建宿主 —— 提示不会被重放，按自己的节奏消失；
            //   ② 作为 Box 的最后一个子项绘制 —— 全局置顶，覆盖页面内容与底栏之上。
            TopHint(
                visible = exitHintVisible,
                text = EXIT_HINT,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // 底部导航（R7）：仅在两个根页显示；点击复用现有返回栈，不引入新导航机制
        if (screen == AppScreen.Timetable || screen == AppScreen.Profile) {
            AppBottomBar(
                current = screen,
                onOpenTimetable = { popToRoot() },
                onOpenProfile = { if (screen != AppScreen.Profile) navigateTo(AppScreen.Profile) },
            )
        }
    }
}
