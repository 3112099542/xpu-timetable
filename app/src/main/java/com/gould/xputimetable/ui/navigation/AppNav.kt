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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import com.gould.xputimetable.ui.components.BottomHint
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

/** 二次返回退出的提示文案（M7 起由 Snackbar 改为导航层浮层；M9 改为底部弹出）。 */
private const val EXIT_HINT = "再按一次退出应用"

@Composable
internal fun AppNav(
    repository: TimetableRepository,
    jsonFileImporter: ScheduleImporter,
    xpuImporter: ScheduleImporter,
    canScheduleExact: () -> Boolean,
    /** M3：数据变更（编辑/导入/清理/学期）→ 重排提醒 + 立即刷新小组件（AC-18）。 */
    onDataChanged: suspend () -> Unit,
) {
    // 最小返回栈：栈底是首页；进入新页 push，返回 pop，导入完成回栈底（顺带修掉"导入中心→手动添加"的返回去向 bug）
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Timetable) }

    // M10：转场方向（产品要求"返回的动画统一为从上往下淡入"）。
    // 前进 = 从下往上（新页从下方推入，有"进入下一层"的方向感）
    // 返回 = 从上往下（新页从上方落下）—— 与前进相反，方向本身即是"返回"的提示。
    var navForward by remember { mutableStateOf(true) }

    fun navigateTo(next: AppScreen) {
        navForward = true
        backStack.add(next)
    }
    fun goBack() {
        if (backStack.size > 1) {
            navForward = false
            backStack.removeAt(backStack.lastIndex)
        }
    }
    fun popToRoot() {
        if (backStack.size > 1) {
            navForward = false
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }
    }
    val screen: AppScreen = backStack.last()

    // 点击提醒通知 / singleTop 复用：MainActivity 发布"回到课表"事件 → 回到返回栈栈底（周视图）。

    // 导航层持有的跨页提示（M2-C：清理完成后送回导入中心的 Snackbar）
    var hubNotice by remember { mutableStateOf<String?>(null) }

    // 首页二次返回退出：退出窗口由 BackPolicy 独立管理；提示走下面的全局提示通道。
    val snackbarHostState = remember { SnackbarHostState() }

    // M9：**全局瞬时提示通道** —— 任何页面都能发一条底部提示
    // （退出提示 / 学期自动保存失败 / 学期已创建…）。宿主在导航层而非页面 Scaffold，
    // 因此切页不会重放（这正是原「保存学期」Snackbar 的缺陷来源）。
    var hintText by remember { mutableStateOf<String?>(null) }
    var hintNonce by remember { mutableIntStateOf(0) }
    val showHint: (String) -> Unit = remember {
        { message: String -> hintText = message; hintNonce++ }
    }
    // 显示 → 驻留 → 隐藏集中在这一处；发送方只管调用 showHint
    LaunchedEffect(hintNonce) {
        if (hintNonce == 0) return@LaunchedEffect
        delay(Hint.VisibleMillis)
        hintText = null
    }

    // 系统返回键接线（M9 抽到 BackKeyWiring.kt：纯接线 + 为 AppNav 腾行数）
    BackKeyWiring(
        backStackSize = { backStack.size },
        onPopBackStack = ::goBack,
        onShowExitHint = { showHint(EXIT_HINT) },
    )

    // M4-UI R7：内容区 + 底部导航（仅两个根页显示，二级页保持沉浸）
    val showBottomBar = screen == AppScreen.Timetable || screen == AppScreen.Profile
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
                    // 位移方向取当前的导航方向：前进 +height/8（自下而上），返回 -height/8（自上而下）。
                    // 用同一套时长与缓动（450ms 入场 / 200ms 出场），避免"有的快有的慢"的观感。
                    val enterOffset: (Int) -> Int = if (navForward) {
                        { fullHeight -> fullHeight / 8 }
                    } else {
                        { fullHeight -> -fullHeight / 8 }
                    }
                    (fadeIn(tween(enterDur, easing = Motion.EaseOutStandard)) +
                        slideInVertically(tween(enterDur, easing = Motion.EaseOutStandard), enterOffset))
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
                            // M9：空态「创建本学期」进学期设置页，由用户选起始日（不再写死本周一）
                            onCreateTerm = { navigateTo(AppScreen.TermSetup(fromEmptyState = true)) },
                        )
                    }

                    is AppScreen.CourseEdit -> {
                        // 数据变更触发点（M2-D 时机② + M3 AC-18）：保存/删除成功后重排闹钟并刷新小组件
                        //
                        // M10：**必须按 target 传 key**。viewModel() 不传 key 时按"类名"取实例，
                        // 同一 Activity 的 ViewModelStore 下所有 CourseEdit 共用一个 VM →
                        // "点课程 A 进编辑 → 返回 → 点添加课程"会复用还装着 A 的 VM
                        // （配合 prepareNew 的旧守卫就表现为"添加课程页显示上一门课的数据"）。
                        val targetKey = when (val t: EditTarget = current.target) {
                            is EditTarget.New -> "course-edit-new"
                            is EditTarget.Edit -> "course-edit-${t.courseId}-${t.sessionId}"
                        }
                        val vm: CourseEditViewModel = viewModel(
                            key = targetKey,
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
                        // 覆盖范围按本批数据的来源计算（WEB / FILE_JSON，预览页已泛化）；
                        // commit 走与解析同通道的 importer（两者最终都汇入 applyImport 同一入口）。
                        // M9：WakeUp CSV 通道删除后，非 WEB 的数据一律走本 App 的 JSON 通道
                        val source = current.needsConfirm.parsed.courses.firstOrNull()?.source
                            ?: CourseSource.FILE_JSON
                        val importer = when (source) {
                            CourseSource.WEB -> xpuImporter
                            else -> jsonFileImporter
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
                        onShowHint = showHint,
                        onOpenTermSetup = { navigateTo(AppScreen.TermSetup(fromEmptyState = false)) },
                        onOpenSubPage = { page -> navigateTo(AppScreen.ProfileSub(page)) },
                        onShareQr = { navigateTo(AppScreen.QrShare) },
                    )

                    AppScreen.QrShare -> {
                        QrShareScreen(
                            repository = repository,
                            onBack = ::goBack,
                        )
                    }

                    is AppScreen.TermSetup -> TermSetupDestination(
                        fromEmptyState = current.fromEmptyState,
                        repository = repository,
                        canScheduleExact = canScheduleExact,
                        onDataChanged = onDataChanged,
                        onShowHint = showHint,
                        onBack = ::goBack,
                        onDone = ::popToRoot,
                    )

                    is AppScreen.ProfileSub -> ProfileSubDestination(
                        page = current.page,
                        repository = repository,
                        canScheduleExact = canScheduleExact,
                        onDataChanged = onDataChanged,
                        onBack = ::goBack,
                    )
                }
            }

            // 底部提示浮层（M7 建立 / M9 改为底部 + 黑灰）：挂在导航层这个 Box 上
            // （**页面 Scaffold 之外**）→ ① 切页不重建宿主，提示不会被重放；
            // ② 作为 Box 最后一个子项绘制，盖在页面内容之上；③ 位于内容区底部，
            // 有底部导航栏时浮在导航栏**上方**，不挡两个 tab。
            BottomHint(
                visible = hintText != null,
                text = hintText.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 二级页不显示底部导航栏，内容区直抵屏幕底 → 需自行避让系统导航栏
                    .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding()),
            )
        }

        // 底部导航（R7）：仅在两个根页显示；点击复用现有返回栈，不引入新导航机制
        if (showBottomBar) {
            AppBottomBar(
                current = screen,
                onOpenTimetable = { popToRoot() },
                onOpenProfile = { if (screen != AppScreen.Profile) navigateTo(AppScreen.Profile) },
            )
        }
    }
}
