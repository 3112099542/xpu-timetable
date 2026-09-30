# M2-E 任务规格：返回键导航与二次返回退出（AC-26 / AC-27 / AC-28）

> 目标一句话：**除系统 Home 键外，任何页面都不许被"一下退出"**——首页（周视图）按两次返回才退出；其他页面按返回回到上一个页面。
>
> **不新增依赖、不改表结构、不动 data/domain/importer 层。** 只动导航与两处 Screen。
>
> 命名说明：本任务占用 **M2-E**，原计划的「桌面小组件」顺延为 **M2-F**。

---

## 1. 现状（已逐条核实，不是推测）

| 事实 | 证据 |
|------|------|
| 全项目**没有任何**返回键处理 | `grep -rn "BackHandler\|onBackPressed\|OnBackPressedCallback" app/src/main app/src/test` → **零命中** |
| 导航是单状态机、**没有返回栈** | `ui/navigation/AppNav.kt:74`：`var screen by remember { mutableStateOf<AppScreen>(AppScreen.Timetable) }` |
| 每个页面的"上一页"是各自写死的字面量 | `AppNav.kt` 内各屏 `onBack = { screen = ... }`（第 108/118/137/146/169/186 行附近） |
| 结果：系统返回键在**任何**页面都直接退出应用 | 真机复现（Android 16 真机，用户反馈） |
| 真机 Android 16 / API 36，targetSdk 36 | `getprop ro.build.version.sdk` = 36；`app/build.gradle.kts:28` |
| 首页已有 Snackbar 载体（可直接复用） | `ui/timetable/TimetableScreen.kt:66` `SnackbarHostState`、`:78-79` `Scaffold(snackbarHost = ...)` |
| 两处二次确认弹窗 | `ui/courseedit/CourseEditScreen.kt:167`（删除课程）、`ui/import_/CleanupScreen.kt:147`（清理导入） |
| WebView 页**无**网页历史回退处理 | `ui/web/CourseCaptureScreen.kt` 全文无 `canGoBack`/`goBack` |

**关键平台约束**：targetSdk 36 在 Android 15+ 上预测式返回（predictive back）默认开启 →
`Activity.onBackPressed()` **重写不会被调用**，必须使用 `OnBackPressedCallback` / Compose `BackHandler`。
本任务**不得**使用 `onBackPressed()` 重写，也**不得**在 `AndroidManifest` 里把 `enableOnBackInvokedCallback` 关掉来绕过。

---

## 2. 需求（验收标准原文，Spec §9 已入库）

- **AC-26（P0）**：周视图（首页）按返回 → **先提示**「再按一次退出应用」，**不得**退出；提示后 **2 秒内**再按 → **必须**退出；超过 2 秒再按 → 只重新提示，**不得**退出。
- **AC-27（P0）**：非首页（课程编辑 / 导入中心 / 网页抓取 / 清理导入 / 导入预览 / 设置）按返回 → **回到进入该页面时的上一个页面**，**不得**退出应用；逐层可回到首页，首页为栈底不可再弹。
- **AC-28（P1）**：二次确认弹窗打开时按返回 → **只关弹窗**，不得弹栈、不得退出。

---

## 3. 方案裁决（按此实现，不要另起方案）

### 3.1 引入**最小返回栈**，替掉"每屏写死上一页"

```kotlin
val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Timetable) }
fun navigateTo(next: AppScreen) { backStack.add(next) }
fun goBack() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
fun popToRoot() { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
val screen = backStack.last()
```

- 当前页 = `backStack.last()`，渲染仍用现有的 `when`。
- 各屏既有的 `onBack` 回调**一律改成 `::goBack`**（同一个实现，消除"父页面"两份事实源）。
- 各屏进入回调改成 `navigateTo(...)`（`onAddCourse` / `onEditCourse` / `onOpenImport` / `onOpenSettings` / `onOpenWeb` / `onOpenCleanup` / `onManualAdd` / `onParsed`）。
- 导入预览的 `onDone`（导入完成 → 回周视图）用 `popToRoot()`。
- **顺带修掉一个真实不一致**：现状从「导入中心 → 手动添加」进入编辑页后，返回去向是**周视图**（跳过导入中心）。改为返回栈后，它会正确回到导入中心。

> 说明：`remember` 而非 `rememberSaveable` —— `AppScreen` 内含 `ImportResult.NeedsConfirm` 等不可保存类型，且现有行为本就是"进程死亡/旋转回周视图"（AppNav 头注释已声明）。**本任务不改这一点**（见 §8）。

### 3.2 返回策略写成**纯函数**（可单测，禁止把判定散在 UI 里）

新建 `app/src/main/java/com/gould/xputimetable/ui/navigation/BackPolicy.kt`：

```kotlin
package com.gould.xputimetable.ui.navigation

/** 一次返回键按下应执行的动作。 */
internal sealed interface BackAction {
    data object Pop : BackAction            // 栈内还有上一页：弹出一层
    data object HintAndArm : BackAction     // 首页首次（或提示已过期）：只提示 + 记录时间
    data object Exit : BackAction           // 首页且提示未过期：退出应用
}

internal object BackPolicy {
    /** 二次返回的有效窗口（毫秒）。 */
    const val EXIT_WINDOW_MILLIS = 2_000L

    /**
     * 返回键决策：栈深 + 上次提示时间 + 当前时间 → 动作。
     * [lastHintAtMillis] 为 null 表示"从未提示过"。
     * 时钟回拨（now < lastHint）时按"未过期"处理为**不退出**（安全侧）。
     */
    fun decide(stackSize: Int, lastHintAtMillis: Long?, nowMillis: Long): BackAction = when {
        stackSize > 1 -> BackAction.Pop
        lastHintAtMillis != null && nowMillis - lastHintAtMillis in 0..EXIT_WINDOW_MILLIS -> BackAction.Exit
        else -> BackAction.HintAndArm
    }
}
```

新增单测 `app/src/test/java/com/gould/xputimetable/ui/navigation/BackPolicyTest.kt`，**至少覆盖 7 例**：

| # | stackSize | lastHintAtMillis | now | 期望 |
|---|-----------|------------------|-----|------|
| 1 | 2 | null | 任意 | `Pop`（栈 >1 时与时间无关） |
| 2 | 1 | null | 任意 | `HintAndArm` |
| 3 | 1 | 有值 | 间隔 **0 ms** | `Exit`（边界含 0） |
| 4 | 1 | 有值 | 间隔 **1 999 ms** | `Exit` |
| 5 | 1 | 有值 | 间隔 **2 000 ms** | `Exit`（边界含 2000） |
| 6 | 1 | 有值 | 间隔 **2 001 ms** | `HintAndArm` |
| 7 | 1 | 有值 | now **早于** lastHint（时钟回拨） | `HintAndArm`（不退出） |

### 3.3 系统返回键接线：单个 `OnBackPressedCallback`（**canonical 模式**）

在 `AppNav` 内：

```kotlin
val dispatcherOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
val dispatcher = dispatcherOwner.onBackPressedDispatcher
var lastHintAt by remember { mutableStateOf<Long?>(null) }

val callback = remember {
    object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when (BackPolicy.decide(backStack.size, lastHintAt, System.currentTimeMillis())) {
                BackAction.Pop -> goBack()
                BackAction.HintAndArm -> { lastHintAt = System.currentTimeMillis(); /* 提示见 3.4 */ }
                BackAction.Exit -> { isEnabled = false; dispatcher.onBackPressed() }  // 交回系统默认（保留退出动画）
            }
        }
    }
}
DisposableEffect(dispatcher) {
    dispatcher.addCallback(callback)
    onDispose { callback.remove() }
}
```

要点（都是踩过的坑，照做）：

1. **退出必须**用 `isEnabled = false` 后 `dispatcher.onBackPressed()`，交给系统默认行为；
   **不要**直接 `activity.finish()`（会跳过系统返回动画，且在预测式返回下表现不一致）。
2. `callback` 用 `remember { }` 建一次，**不要**每次重组新建（否则回调反复增删，可能出现"第一次按没反应"）。
3. `isEnabled = false` 之后**不要**再把它设回 true —— 需要恢复时依赖 Activity 重建（退出后进程即离开前台的正常语义）。
4. `backStack` / `lastHintAt` 被回调闭包捕获：用 `remember` 的 `MutableState` 或 `mutableStateListOf`（读值时取最新），
   **不要**把 `stackSize` 之类在闭包创建时快照成常量（经典错误：永远判为首页）。

### 3.4 提示载体：复用周视图既有 Snackbar（**不新增 Toast、不新增布局**）

- 把 `SnackbarHostState` 的创建从 `TimetableScreen` 提升到 `AppNav`：
  `AppNav` 里 `val snackbarHostState = remember { SnackbarHostState() }`，
  `TimetableScreen` 新增参数 `snackbarHostState: SnackbarHostState`（**去掉**其内部的 `remember { SnackbarHostState() }`，`:66`），
  其内部一次性错误提示（`:73-76`）改用传入的 state，其余不变。
- `AppNav` 用 `rememberCoroutineScope()` 在 `HintAndArm` 分支里
  `scope.launch { snackbarHostState.showSnackbar("再按一次退出应用", duration = SnackbarDuration.Short) }`（**异步、不阻塞返回键处理**）。
- **退出窗口（2 秒）由 `BackPolicy` 独立管理，禁止与 Snackbar 时长绑定**（Snackbar 的 Short 时长不是 2 秒）。

### 3.5 网页抓取页：返回优先回退**网页历史**

`ui/web/CourseCaptureScreen.kt` 内注册**本屏自己的** `BackHandler`（先于 `AppNav` 的全局回调被调用——后注册者优先，
子 Composable 后注册，故天然优先；不需要在 `AppNav` 里加 if）：

```kotlin
BackHandler(enabled = true) {
    val view = webViewHolder.value
    if (view != null && view.canGoBack()) view.goBack() else onBack()
}
```

- 为此需把 WebView 实例从 `WebViewSection` 暴露出来：在 `CourseCaptureScreen` 内
  `val webViewHolder = remember { mutableStateOf<WebView?>(null) }`，在 `AndroidView(update = { wv -> webViewHolder.value = wv })` 处赋值
  （现有 `DisposableEffect` 的 `destroy()` 逻辑保持不变，不得泄漏）。
- **裁决理由**：一网通办登录是"统一身份认证 → 服务篇 → 我的课表"多跳流程，若返回键直接从抓取页跳走，用户当次登录上下文即丢失。
- 网页已无历史时返回 **导入中心**（与该屏 `onBack` 一致）。

### 3.6 弹窗优先（AC-28）

Compose `Dialog`（`AlertDialog` 的底层）是独立窗口，会先消费返回事件并触发 `onDismissRequest`，理论上不需要额外代码。
**但必须真机核对**：弹窗打开时按返回 = 只关弹窗。若实测出现"弹窗关掉且同时弹了栈"（说明外层回调抢先），
则改为：由 `CourseEditScreen` / `CleanupScreen` 把「弹窗是否打开」上报（参数 `isDialogOpen: Boolean`），
`AppNav` 的全局回调在 `isDialogOpen` 时**直接 return（不消费）**。实测通过就不要加这个复杂度。

---

## 4. 精确改动清单

| 文件 | 改动 |
|------|------|
| `ui/navigation/BackPolicy.kt` | **新增**：`BackAction` + `BackPolicy.decide(...)`（≤ 60 行） |
| `ui/navigation/AppNav.kt` | `screen` → `backStack`（`mutableStateListOf`）+ `navigateTo/goBack/popToRoot`；各屏 `onBack = ::goBack`、进入回调改 `navigateTo`；预览 `onDone = ::popToRoot`；新增 `SnackbarHostState` 并传给 `TimetableScreen`；新增 `OnBackPressedCallback` 接线（§3.3） |
| `ui/timetable/TimetableScreen.kt` | 加参数 `snackbarHostState: SnackbarHostState`，删掉内部 `remember`（`:66`），其余不动 |
| `ui/web/CourseCaptureScreen.kt` | 暴露 WebView 实例（holder）；本屏 `BackHandler`（§3.5） |
| `app/src/test/.../ui/navigation/BackPolicyTest.kt` | **新增**：7 例边界单测（§3.2） |

`AppNav.kt` 当前 196 行，本次会增长；**仍须 ≤ 300 行**（项目硬门禁）。若超出，按页面把 `when` 分支里的装配逻辑抽成私有 `@Composable` 函数放在同包内，**不要**引入新的抽象层。

---

## 5. 交付纪律

- 文件 ≤ 300 行；**无 emoji**（图标用既有 `AppIcons` 矢量资源）；**不新增任何依赖**
- **不改表结构**（`AppDatabase.version` 保持 2）
- 禁止用 `onBackPressed()` 重写、禁止 `System.exit(0)`、禁止直接 `activity.finish()` 做退出（见 §3.3 要点 1）
- 不要动 `data/`、`domain/`、`importer/`、`reminder/` 任何文件（本次不涉及）
- 沙箱内**没有 git**，不要尝试 commit/push；直接落盘并在回传里给文件清单
- 构建/测试命令（**输出重定向到文件，禁止管道 `| tail`**）：
  ```
  export JAVA_HOME=/home/othc3/opt/jdk-21b
  cd /home/othc3/WorkBuddy/安卓软件开发
  /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m2e.log 2>&1
  ```
- 回传格式：`assembleDebug` 与 `testDebugUnitTest` 的 EXIT 码 + 用例总数（**当前基线 181**，新增后应 ≥ 188）+ 改动文件与行数 + 逐条自评 **AC-26 / AC-27 / AC-28** + 未验证项如实列出（真机行为由我方验收）

---

## 6. 我方真机验收方法（交付后执行，供实现方了解"done"的标准）

用 `adb shell input keyevent KEYCODE_BACK` 驱动（这是**被测行为本身**，与"禁用返回键抄近路导航"的纪律不冲突），逐条：

1. 周视图按 1 次 → 应出现 Snackbar「再按一次退出应用」，**应用仍在前台**（`dumpsys window | grep mCurrentFocus` 仍是我们包名）
2. 紧接着 2 秒内再按 1 次 → 应用退出（前台变为桌面）
3. 重启 App → 周视图按 1 次 → 等 3 秒 → 再按 1 次 → **不得**退出，只重新提示（AC-26 的过期分支）
4. 周视图 → 设置 → 按返回 → 回周视图（不退出）；周视图 → 导入中心 → 清理导入 → 按返回 → 回导入中心 → 再按返回 → 回周视图（AC-27 逐层）
5. 清理导入页打开二次确认弹窗 → 按返回 → 弹窗关闭、仍在清理导入页、应用不退出（AC-28）
6. 抓取页：登录进入"我的课表"后按返回 → 应回退网页（不是跳回导入中心）；再连按到网页无历史 → 回导入中心
7. 回归：各屏左上角返回箭头与系统返回键**去向必须一致**

---

## 7. 明确不做（避免范围膨胀）

- 编辑页"未保存内容"的返回二次确认（本次不做；`CourseEditScreen` 的返回仍是直接丢弃表单）
- 返回栈持久化（旋转屏/进程死亡仍回周视图，属既有已知低危缺口，与本任务无关）
- 不引入 Navigation 3 或任何导航库（`OPEN-DECISIONS` 中 navigation3 仍为 RC）
- 不改动各屏的 UI 布局与视觉

---

## 8. 附：术语（中文对照）

| 术语 | 说明 |
|------|------|
| 返回栈（back stack） | 记录"用户从哪来"的页面序列，栈底是首页 |
| `OnBackPressedCallback` | AndroidX 提供的返回事件回调注册机制，预测式返回下唯一被系统调用的入口 |
| 预测式返回（predictive back） | Android 13+ 的新返回机制；targetSdk 35+ 在 Android 15+ 默认开启，旧的 `onBackPressed()` 订阅失效 |
| Snackbar | Material 3 底部短提示条；本项目周视图已有宿主，本次复用 |
| `popToRoot` | 一次退回栈底（导入完成回周视图即用这个） |
