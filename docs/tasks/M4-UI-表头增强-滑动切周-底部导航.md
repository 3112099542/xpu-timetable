# M4-UI 任务规格：表头信息增强 · 滑动切周 · 底部导航与「我的」页

> 来源：产品负责人提供的 WakeUp 课程表截图（含黄色标注）+ 5 组需求，逐条对应见 §1。
> 本任务是 **MVP 之后的 UI 打磨批次**（Spec 里程碑 M1–M3 之外），以 **AC-30 ~ AC-35** 纳入验收。
>
> **不新增任何依赖**：滑动用 Compose 自带 `HorizontalPager`（foundation 已有）；新图标直接用 ADR-002 的 Lucide 库
> （`lucide_ic_circle_user` 已在库内，无需手写矢量、无需下载）；不改表结构、不动数据层/提醒/导入链路。

---

## 0. 交付目标（一句话）

把周视图从"能看"打磨到"好看、好用、不误触"：**表头一眼看懂今天在哪、切周靠手势与手势以外的零误触入口、设置归位到「我的」**。

---

## 1. 需求逐条对应（截图标黄 = 需求点）

| # | 需求（原话要点） | 落地位置 | 验收 |
|---|------------------|----------|------|
| R1 | 「第 n 周」后面加上周几（今天是周几） | `components/WeekSelector.kt` 主标题 | AC-30 |
| R2 | 星期与时间轴交界空白处加「几月」 | `TimetableScreen.kt` 的 `DayHeader` 左侧 38dp 单元格（与 `WeekGrid` 的 `axisWidth = 38.dp` 对齐） | AC-30 |
| R3 | 今天不够明显：**黑底圆角方块 + 日期镂空** | `DayHeader` 中今天那一列的日期 | AC-30 |
| R4 | 返回本周被上下周按钮挤开、易误点 → **返回本周常显并固定在原设置按钮位置；删掉上下周按钮** | `WeekSelector.kt` | AC-31 |
| R5 | **课表页左右滑动切上下周**，动画平滑优雅 | `TimetableScreen.kt` + `TimetableViewModel` | AC-31 |
| R6 | **双击左上角「第 n 周」直接回本周** | `WeekSelector.kt` | AC-32 |
| R7 | 底部两个切换项「课表 / 我的」，**设置移入「我的」** | `AppNav.kt` + 新 `ProfileScreen` | AC-33 / AC-34 |
| R8 | 整体 UI 优化 | §3 设计令牌 + §4 逐项要求 | AC-35 |

---

## 2. 设计定调（Direction Lock）

按 `frontend-design` 方法论先定调，再动手：

- **受众与场景**：在校学生，课间/路上单手快速查看「今天/下一节在哪」。信息密度高、停留时间短、误触代价大（切错周看到空课表会慌）。
- **调性（二选一，选定后不许混搭）**：**极致极简 + 工业实用**——高密度但层级精确、发丝分隔线、等宽数字、单一强调色。
- **记忆点（Differentiation）**：**今天的黑底镂空日期块** —— 扫一眼就知道今天在哪一列；这也是竞品截图中最值得学的一点。
- **配色（60 / 30 / 10）**：中性表面色为主（`surface` / `surfaceVariant`）60%，文字与分隔（`onSurface` / `outlineVariant`）30%，**唯一强调色 `colorScheme.primary`** 10% 只给「返回本周可用态」与今天列的星期字；课程卡颜色仍走 `CoursePalette`（不计入三步分配）。
- **字体策略（对设计手册"禁用默认字体"的**有意例外**，理由写清）**：Android 端不引入外部展示字体——中文覆盖需要额外 3–5 MB 且字形回退不可控，与"轻量"定位冲突。替代手段：**字重梯度 + 字号梯度 + 等宽数字（`FontFeatureSettings("tnum")`）**，让数字列绝对对齐。
- **动效**：一律只动 `transform` / `alpha`；时长取手册基准（微交互 160ms、常规 300ms、场景 640ms），缓动统一 `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`。
- **氛围**：只保留一处——网格分隔线用 `outlineVariant` 12% 透明度的发丝线；**不加阴影、不加渐变、不加纹理**（极简方向过度装饰等同于敷衍）。

---

## 3. 设计令牌（新增 `ui/theme/Tokens.kt`）

组件**禁止**再写魔法数字与硬编码颜色，统一引用：

```kotlin
/** 动效令牌（对齐设计手册基准：微交互 160 / 常规过渡 300 / 场景过渡 640）。 */
object Motion {
    const val FastMillis = 160          // 按压、悬停反馈
    const val BaseMillis = 300          // 切周、面板
    const val SceneMillis = 640         // 首屏编排（仅首次进入）
    val EaseOutStandard = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/** 圆角与尺寸令牌。 */
object Corners {
    val Card = 4.dp          // 课程卡（★ 2026-09-17 订正：规格初版误写"现状 8dp"，实测 CourseCard.kt 为 4dp；按"保持不变"意图取实际值）
    val TodayMark = 8.dp     // 今天日期方块
    val MinTouchTarget = 44.dp   // 手册硬性要求：≥44dp
}

/** 网格尺寸（与既有实现保持一致，集中定义避免散落）。 */
object Grid {
    val RowHeight = 52.dp
    val AxisWidth = 38.dp    // WeekGrid 与 DayHeader 必须同源
}
```

- 现有 `rowHeight`/`axisWidth` 是各自文件内的 private 常量（`WeekGrid.kt:52`、`TimetableScreen.kt:53`）→ **收敛到 `Grid`**，两处引用同一常量（防止将来只改一处导致表头与网格错位）。
- 颜色一律取 `MaterialTheme.colorScheme.*`（Spec §8 红线：禁硬编码颜色）；**禁用纯 `#000000` / `#FFFFFF`**（手册禁止），"黑底"用 `onSurface`（浅色下即近黑 `#1A1C1E`），"镂空"用 `surface`。

---

## 4. 逐项实现要求

### R1 + R6：表头「第 N 周 周四」+ 双击回本周（`components/WeekSelector.kt`）

```kotlin
// 周标题：主标题一行放下「第 4 周」与「周四」，两段同字号、周几用次要色更克制
Row(verticalAlignment = Alignment.Bottom) {
    Text("第 $week 周", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.width(8.dp))
    Text(weekdayCn(today.dayOfWeek.value), style = MaterialTheme.typography.titleMedium,
         color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- **必须**在标题所在 `Column` 上加双击手势，命中区高度 ≥44dp（`Modifier.heightIn(min = Grid.MinTouchTarget)`）：

```kotlin
.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onBackToCurrentWeek() }) }
```

- 单击**不做任何事**（避免与双击冲突导致误触）。
- 周几由**今天**决定（`LocalDate.now()`），与当前显示的周次无关 —— 产品明确要求"今天是周几"。
- 新增纯函数（可单测）：

```kotlin
/** 1..7 → 「周一」…「周日」；越界返回空串（不抛异常）。 */
fun weekdayCn(dow: Int): String = listOf("周一","周二","周三","周四","周五","周六","周日").getOrNull(dow - 1).orEmpty()
```

### R2 + R3：月份徽标 + 今天黑底镂空方块（`TimetableScreen.kt` 的 `DayHeader`）

**R2 月份徽标**（网格左上角、星期行与时间轴交界处）：

```kotlin
Box(Modifier.width(Grid.AxisWidth)) {          // 与 WeekGrid 左轴同宽，保证对齐
    MonthBadge(month = monthOfDisplayedWeek)   // 竖排：数字一行 + 「月」一行，labelSmall，onSurfaceVariant
}
```

- 取月规则（**确定无歧义**）：取**所显示那一周的周一**所在月份（`WeekCalc.mondayOfWeek(startDate, week).monthValue`）。
  当前周自然显示当前月；跨月周（如 9/29–10/5）以周一 9/29 为准显示「9 月」。
- `startDate` 缺失/解析失败 → 该位置**留空**（不显示占位符、不崩）。
- 新增纯函数 `fun monthLabelCn(month: Int): String = "${month} 月"`（竖排由 UI 拆成两行渲染）。

**R3 今天标记**（今天那一列的日期，且仅今天）：

```kotlin
if (isToday) {
    Box(
        modifier = Modifier
            .size(28.dp)                                  // 方块本体 28dp；外层点击区由父行保证 ≥44dp
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(Corners.TodayMark)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.format(dayFormatter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,     // 「镂空」= 数字取背景色
            fontWeight = FontWeight.SemiBold,
        )
    }
} else {
    Text(date.format(dayFormatter), /* 现状样式不变 */)
}
```

- 星期几文字：今天那列用 `colorScheme.primary` + `FontWeight.SemiBold`（其余列保持 `onSurfaceVariant`）。
- **只在"所显示周包含今天"时**画方块；滑到别的周时该列不高亮（见 AC-31 的过去周要求）。
- 对比度自证：`onSurface(#1A1C1E)` 底 + `surface(#F8F9FC)` 字 ≈ **12:1**（≥4.5:1 ✓）；深色主题下自动反相（`onSurface` 近白、`surface` 近黑），无需额外分支。
- 圆角**必须**存在（`Corners.TodayMark = 8.dp`）；不得用直角、不得加阴影。

### R4 + R5：删除上下周按钮、返回本周常驻、左右滑动切周

**R4（`WeekSelector.kt`）**：

- **删除** `chevronLeft` / `chevronRight` 两个 `IconButton`（连带 `onMoveWeek` 参数、`R.string.week_previous` / `week_next` 资源一起删——设计清单要求无死代码）。
- 「返回本周」**常驻**顶栏最右（即原设置图标的位置，设置已迁走）：

```kotlin
IconButton(onClick = onBackToCurrentWeek, enabled = overridden) {
    Icon(painterResource(AppIcons.calendarToday), contentDescription = "回到本周",
         modifier = Modifier.size(IconSize.Medium),
         tint = if (overridden) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
}
```

- **已在当前周时呈禁用态**（`enabled = false`，视觉 alpha 0.38）。理由：常驻保证位置恒定不跳动（解决"按钮挤开→误点"的根因），禁用态防止"点了没反应"的困惑。
- 顶栏**不再有设置图标**（R7 迁移）。

**R5 滑动切周（`TimetableScreen.kt`）**：

- 用 `HorizontalPager`（`androidx.compose.foundation.pager`），**每页 = `DayHeader` + `WeekGrid`**（表头随页滑动，视觉与竞品一致）；`WeekSelector` 与 `TermStatusHint` 留在 pager 外（滑动过程中不变，落定后更新）。

```kotlin
val pagerState = rememberPagerState(initialPage = state.week - 1, pageCount = { state.totalWeeks })
HorizontalPager(
    state = pagerState,
    beyondViewportPageCount = 0,                       // 内存友好
    key = { it },
    flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),   // 一次手势最多翻一周（可控、不跳跃）
        snapAnimationSpec = tween(Motion.BaseMillis, easing = Motion.EaseOutStandard),
    ),
) { page -> WeekPage(week = page + 1, ...) }
```

- **双向同步（必须防抖，否则会死循环）**：
  ```kotlin
  LaunchedEffect(pagerState) {
      snapshotFlow { pagerState.currentPage }.collect { page -> viewModel.setWeek(page + 1) }
  }
  LaunchedEffect(state.week) {                        // 外部变化：双击/返回本周/通知跳转
      if (pagerState.currentPage != state.week - 1) pagerState.animateScrollToPage(state.week - 1)
  }
  ```
- `TimetableViewModel` 新增 `fun setWeek(week: Int)`（内部 `coerceIn(1, totalWeeks)` 并写 `weekOverride`）；`moveWeek(delta)` 保留（双击回本周仍走 `backToCurrentWeek()`）。**学期越界时**（`beforeTermStart` / `afterTermEnd`）滑动仍受限在 `1..totalWeeks`。
- **竖直滚动位置在切周后保持**：把 `WeekGrid` 用的 `rememberScrollState()` 提升到 `TimetableScreen` 层，作为参数传给每一页（现状是 `WeekGrid` 内部 `remember`，切页会各记一份）。
- 手势区域**仅限 pager 内容区**：底部导航、顶栏不参与横向滑动。

### R7：底部导航（课表 / 我的）+ 设置迁移（`ui/navigation/AppNav.kt`）

- `AppScreen` 增加 `data object Profile : AppScreen`。
- 底部用 M3 `NavigationBar` + 两个 `NavigationBarItem`：
  - 「课表」：`AppIcons.calendarToday`（已有），选中条件 = `screen == Timetable`
  - 「我的」：**新增** `AppIcons.user = R.drawable.lucide_ic_circle_user`（库内已有该图标，无需手写矢量）
- **只在两个根页显示**（`screen == Timetable || screen == Profile`）；编辑/导入中心/抓取/预览/清理等页面不显示（保持沉浸，沿用现有返回栈）。
- 点击行为（复用现有栈，不引入新的导航机制）：
  - 点「课表」→ `popToRoot()`
  - 点「我的」→ 若当前不是 Profile 则 `navigateTo(AppScreen.Profile)`
- **「我的」页 = 现有 `SettingsScreen` 的内容**（学期 / 课前提醒 / 权限 / 关于与隐私），改造为：
  - 标题为「我的」，**去掉顶栏返回箭头**（返回由系统返回键 + 底部导航承担；`SettingsScreen` 的 `onBack` 参数删除）
  - 底部追加一个**占位区块**「更多功能陆续加入」（纯文本，**不得**做成不可点的假按钮，不得用 emoji）
- 连带修改：`TimetableScreen` 的 `onOpenSettings` 参数删除；`AppNav` 的 `Settings` 分支改名为 `Profile` 并作为根页之一。

### R8：整体 UI 优化（逐条给判据，便于自检）

| 项 | 要求 |
|----|------|
| 层次 | 不新增阴影层级；卡片只用 1dp 发丝边 + 8dp 圆角；分隔线 `outlineVariant.copy(alpha = 0.12f)` |
| 数字对齐 | 时间轴与卡片内时间用 `TextStyle(fontFeatureSettings = "tnum")`（等宽数字） |
| 字阶 | 周标题 `titleLarge/SemiBold`、日期 `labelMedium`、课程名 `bodySmall/Medium`、教室 `labelSmall` —— 全项目不超过 4 档 |
| 触摸目标 | 双击区、返回本周、底部导航项、课程卡 ≥44dp（课程卡现为格内自适应，小格情况已在 AC-23 记录妥协） |
| 动效 | 切周 `tween(300, EaseOutStandard)`；卡片按下 `scale 0.98 / alpha 0.9`（`Motion.FastMillis`）；**首次**进入课表时表头→网格错峰上浮 2 组（间隔 80ms，`SceneMillis`），之后不再播放 |
| 状态覆盖 | 加载 / 无学期 / 错误 / 越界（未开学、本学期已结束）/ 过去周（今天列不高亮）/ 跨月周（月份徽标变化）—— 六态齐全 |
| 无障碍 | 所有图标按钮有中文 `contentDescription`；文字对比度自证 ≥4.5:1；双击回本周有等价按钮入口（双路径） |
| 反 AI 味自检 | 交付时按下表逐条给结论（不是"全部通过"，要给具体证据）：① 字体（本任务明知例外，写理由）② 配色（无渐变、单一强调色）③ 布局（非模板三卡）④ 卡片（无统一阴影堆叠）⑤ 图标（全部有信息含义）⑥ 文案（无"赋能/新一代"）⑦ 动效（三档时长区分，非一律 300ms 淡入）⑧ 层次（发丝线而非纯平）⑨ 人格（黑底镂空日期块为本产品识别点）⑩ 状态（六态）⑪ 记忆点（同上）⑫ 可访问性（对比度/触摸目标/降级） |

---

## 5. 明确不做

- 不做日视图、不做「学习/日程」等其它 tab（底部只有两项）
- 不做周次无限循环滑动（1..totalWeeks 硬边界）
- 不做手势自定义、不做字体下载、不引入任何新依赖
- 「我的」页本轮只承载"原有设置 + 占位"，不新增实质功能
- 不改数据层、提醒、导入、小组件任何逻辑

---

## 6. 交付纪律

- 单文件 ≤ 300 行；**无 emoji**；构造数据类用具名参数
- 不改表结构（`AppDatabase.version` 保持 2）；不动 `data/`、`domain/`（除新增纯函数所在文件）、`importer/`、`reminder/`、`widget/`
- 颜色/尺寸/动效一律走 `MaterialTheme` 与 §3 令牌，禁止魔法数字与硬编码色值
- 删除死代码与死资源（`week_previous` / `week_next` / `onMoveWeek` / `DayHeader` 中重复的 `axisWidth`）
- 沙箱内没有 git，不要 commit/push；**不要碰 adb / 不要装机**（真机验收由我方执行）

## 7. 构建与自测

```
export JAVA_HOME=/home/othc3/opt/jdk-21b
cd /home/othc3/WorkBuddy/安卓软件开发
/home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m4ui.log 2>&1
echo "EXIT=$?" >> /tmp/m4ui.log
grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/m4ui.log | head -3
```
（**禁止**管道调用 Gradle。）用例基线 **205**；新增纯函数单测 ≥6 例（`weekdayCn` 1..7 与越界、`monthLabelCn` 12 个月、跨月周取周一月份、`startDate` 非法返回空），总数应 **≥ 211**。

## 8. 回传格式

1. 两条命令的 EXIT 码与 BUILD 结果；用例总数与失败数
2. 新增/修改文件清单与行数
3. 逐条自评 **AC-30 ~ AC-35**（文件:行号 + 对应单测名/手工判据）
4. **反 AI 味 12 项自检结论**（逐项给证据，例：③ 布局——说明本页为何不属于"居中标题+三卡"模板）
5. 未验证项如实列出（必须真机的：滑动流畅度与一次手势只翻一周、双击手感、底部导航切换、返回键回归、"我的"页内容呈现、深色主题下的黑底方块反相效果）

## 9. 我方真机验收（交付后执行）

1. 表头显示「第 N 周 周四」；网格左上角显示「9 月」
2. 今天那一列：日期为**圆角黑底 + 镂空数字**，星期几加粗高亮；非今天列无该标记（截图取证）
3. 顶栏**没有**上下周按钮；「返回本周」常驻最右；已在当前周时为禁用态（alpha 0.38）
4. 在课表区域**左滑一次** → 只前进一周；**右滑一次** → 只后退一周；全程表头随之滑动、落定后「第 N 周」更新
5. 滑到第 6 周后**双击左上角标题** → 立即回第 4 周（带 300ms 动画）
6. 竖直滚动到第 10 节后切周 → 滚动位置保持
7. 底部「课表 / 我的」切换正常；「我的」内含原设置四项且无返回箭头
8. 在「我的」页按**系统返回键** → 回到「课表」，**不退出应用**（AC-27 回归）
9. 滑动动画平滑度：`adb shell dumpsys gfxinfo com.gould.xputimetable reset` → 连续滑动 10 次 → `framestats` 中 **janky frames 占比 < 5%**（有数据优先，无数据则记录现象）
10. 回归：点提醒通知仍落周视图（AC-29）、小组件内容不受影响、编辑/导入链路正常

## 10. 术语表

| 术语 | 说明 |
|------|------|
| `HorizontalPager` | Compose foundation 的水平分页容器，本任务用它实现"一周一页"的滑动切周 |
| `PagerSnapDistance.atMost(1)` | 限制一次手势最多翻一页，避免快速滑动连翻数周 |
| 镂空（knockout） | 文字取背景色、方块取前景色，视觉上像从方块里"挖"出字 |
| 反 AI 味清单 | 设计手册 12 项检查表，用于排除模板化、无辨识度的界面 |
