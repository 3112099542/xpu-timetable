# M5-UI 任务规格：动效放慢、主页与底栏配色、顶栏按钮归位、小组件尺寸与文字统一

> 来源：产品负责人 2026-09-17 22:1x 的 8 条 UI 需求（自包含文件，未接触过项目也能实现）。
> 已确认：**2026-08-24 就是真实开学日，数据保持现状，不要改学期起始日**。

---

## 0. 项目背景（30 秒）

- 项目：`/home/othc3/WorkBuddy/安卓软件开发`，西安工程大学专属安卓课表 App（Kotlin + Jetpack Compose + Material 3 + Glance 小组件），无后端，GPL-3.0。
- 构建（**输出重定向到文件，禁止管道调用 Gradle**，守护进程会占管道假死）：
  ```
  export JAVA_HOME=/home/othc3/opt/jdk-21b
  cd /home/othc3/WorkBuddy/安卓软件开发
  /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m5.log 2>&1
  echo "EXIT=$?" >> /tmp/m5.log
  grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/m5.log | head -3
  ```
- 用例基线 **212**，不得减少；沙箱内**无 git**，不要 commit；**不要碰 adb / 不要装机**（真机验收由我方执行，用户手机上有真实课表）。
- 硬门禁：单文件 ≤300 行、除产品指定的颜文字外无 emoji、数据类构造用具名参数、版本目录 `libs.versions.toml` 是版本唯一事实源、不改表结构（`AppDatabase.version` 保持 2）。

---

## 1. 当前相关位置（实测于 2026-09-17，可逐条核对）

| 文件 | 现状 |
|------|------|
| `ui/theme/Tokens.kt` | `Motion.FastMillis=160 / BaseMillis=300 / SceneMillis=640、`EaseOutStandard = CubicBezierEasing(0.16f,1f,0.3f,1f)`；`Corners.Card=4.dp、`TodayMark=8.dp、`MinTouchTarget=44.dp；`Grid.RowHeight=52.dp、`AxisWidth=38.dp
| `ui/timetable/TimetableScreen.kt | `Scaffold(…)` 右侧 FAB 列：`SmallFloatingActionButton(导入)` + `FloatingActionButton(添加课程)`；`WeekSelector(...)` 顶栏；`WeekPager` 用 `HorizontalPager` + `PagerDefaults.flingBehavior(snapAnimationSpec = tween(Motion.BaseMillis, EaseOutStandard))、`PagerSnapDistance.atMost(1)
| `ui/timetable/components/WeekSelector.kt | 左：周次标题 + 周几 + 日期区间；右：`IconButton(返回本周)`（`enabled = overridden`，禁用时 `onSurfaceVariant alpha 0.38、可用时 primary
| `ui/navigation/AppBottomBar.kt | `NavigationBar(modifier = fillMaxWidth())` + 两个 `NavigationBarItem`（课表 / 我的），**无高度/背景/转场设置
| `ui/navigation/AppNav.kt | `when (screen) { … }`，页面切换**无过渡动画
| `widget/TodayWidgetContent.kt` | `WidgetHeader(weekNumber, hasTerm)`：校名 16sp Bold + `onSurface`；右上 `headerRightText` 12sp + `primary`；空态 `AllDoneContent`（颜文字 20sp onSurface +「今天没有课啦」14sp **onSurfaceVariant**）
| 资源 | `res/xml/today_widget_info.xml`（`minWidth=180dp`、`minHeight=110dp`、minResize 110×110dp）；`res/drawable/widget_bg_rounded.xml`（solid `#F8F9FC` + corners 16dp）；`res/drawable-night/widget_bg_rounded.xml`（`#111318`）
| 项目禁硬编码颜色：色值只能出现在 `ui/theme/Color.kt / 资源 XML，组件只引用常量或 `MaterialTheme.*`
| 注：`TimetableScreen.kt` 现 **301 行压线**；本任务若需要它增加行数，请先把它内部 `WeekPager` 拆到 `ui/timetable/components/WeekPager.kt（顺带解决压线）。
| 注：`WeekSelector` 在 App 与「我的」页按钮移动后，需要新增 `onAddCourse` / `onOpenImport` 参数。
| 注：小组件高度与「紧凑布局阈值」：`TodayWidgetContent.kt` 有 `COMPACT_HEIGHT = 100.dp，`size.height < COMPACT_HEIGHT` 时走 `CompactContent`（只显示下一节）。改小组件高度后必须同步复核该阈值（见 §5.1）。

---

## 2. 需求清单（原文 → 我的裁决，不要另起方案

| # | 需求原文 | 裁决 |
|---|---------|------|
| 1 | 左右横移的动画速率太快了，让所有页面的动画速率更优雅，帧率大一点，要更平滑 | §3 |
| 2 | 主页背景颜色设置为 `#DDE0F1` | §4.1 |
| 3 | 将软件下面的按钮（课表和我的）以及它的框背景设置与主页背景颜色一致，透明度 50%，高度下降一半 | §4.2 |
| 4 | 将添加课程按钮和导入按钮添加到软件右上角，放到返回本周按钮的左边 | §4.3 |
| 5 | 将小组件的高度缩小大约五分之一，与 wakeup 课程表小组件一致 | §5.1 |
| 6 | 将小组件背景的颜色由 `#F8F9FD` 改为 `#EEEDF3` | §5.2 |
| 7 | 将小组件右边的字（9.17 第4周 周四）改成和左边西安工程大学的大小一样 | §5.3 |
| 8 | 将小组件的字的颜色设置与“今天没有课啦”颜色一致 | §5.3 |

---

## 3. 动效（需求 1）

### 3.1 令牌

```
object Motion {
    const val FastMillis  = 200          // 按压反馈（原 160 → 200）
    const val BaseMillis  = 450          // 切周、页面切换（原 300 → 450）
    const val SceneMillis = 800          // 首屏编排（原 640 → 800）
    val EaseOutStandard = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}
```

### 3.2 具体落点

1. **左右横移（周视图 `HorizontalPager`**：`snapAnimationSpec = tween(Motion.BaseMillis, easing = Motion.EaseOutStandard)`（改令牌即生效；`PagerSnapDistance.atMost(1)` **保留**——一次手势只翻一周，这个行为已经验收过，别改。
2. **首屏编排**：`EnterRise` 用 `Motion.SceneMillis`，错峰延迟 `80ms → 120ms`（表头先出、列表跟上）。
3. **卡片按压**：`Motion.FastMillis`（200ms）。
4. **页面切换（底部“课表 / 我的”切换，目前是瞬时）**：新增 `AnimatedContent(targetState = screen)`，`transitionSpec = fadeIn(tween(Motion.BaseMillis, easing = Motion.EaseOutStandard) togetherWith fadeOut(tween(Motion.FastMillis, easing = Motion.EaseOutStandard))；另加 8dp 轻微上移（`slideInVertically { it / 8 }）。
5. **禁抖动**：`WeekGrid` 里不要出现 `observeXxx().collectAsState()`，数据由 `state.weekItems[week]` 传入（M4-UI-fix 已预取 ±1 周，`CompactContent/FullContent` 只做渲染。
6. **滑动期间零 IO**：`WeekPage` 内禁止任何 `repository.observeWeek(...).first()` / 数据库同步查询；只允许读 VM 已预取的 `state.weekItems`。
7. **只动 transform / alpha**：动画用 `graphicsLayer`，不要动会触发布局的属性。
8. **降级**：系统“减少动画”开启时关闭入场与切换动画（设计手册硬性要求）。

### 3.3 自证

- 真机连续左右滑动 10 次后 `dumpsys gfxinfo` 卡顿帧占比仍 **<5%**（上次实测 0.75%）。
- 主观：一次滑动≈450ms、无跳帧、不回弹过度。
- 页面切换淡入淡出、约 220ms/160ms。

---

## 4. App 侧配色与按钮（需求 2/3/4）

### 4.1 主页背景 `#DDE0F1`（需求 2）

- 新增常量 `ui/theme/Color.kt`：
  ```
  val LightPageBackground = Color(0xFFDDE0F1)   // 主页（周视图）背景，产品指定
  ```
- `TimetableScreen` 的 `Scaffold(containerColor = LightPageBackground)`（亮色）。深色主题**保持现状**（`DarkSurface #111318`）——产品没给深色值，不要替他发明色值。
- 范围：**只改主页（周视图）**；「我的」页、课程编辑页、导入页背景保持现状；网格行/卡片的既有配色保持现状。

### 4.2 底部导航（需求 3）

- `AppBottomBar.kt`：`NavigationBar(modifier = Modifier.fillMaxWidth().height(40.dp)（默认 80dp，减半）
- `containerColor = LightPageBackground.copy(alpha = 0.5f)`（深色：`DarkSurface.copy(alpha = 0.5f)`；`tonalElevation = 0.dp`（避免 M3 抬升覆盖背景色；`NavigationBarItem` 图标 `20.dp`、文字 `10.sp`；保持 `alwaysShowLabel = true`；每项点击区尽量占满 40dp 高。
- 保留既有选中逻辑（点击走 `popToRoot()` / `navigateTo(Profile)`，不要改导航语义。

### 4.3 顶栏右上：导入 + 添加课程 + 返回本周（需求 4）

- 把 `TimetableScreen` 里的两个 FAB（`SmallFloatingActionButton 导入`、`FloatingActionButton 添加课程`）**移入 `WeekSelector` 的右侧 Row**，顺序（左→右）：`[导入][添加课程][返回本周]`（返回本周仍在最右）。
- `WeekSelector` 新增参数 `onAddCourse: () -> Unit`、`onOpenImport: () -> Unit`；图标：`AppIcons.upload`（导入）、`AppIcons.plus`（添加课程）、`AppIcons.calendarToday`（返回本周）。
- 三个 `IconButton`：触摸目标 ≥44dp；icon `20.dp`；间距 `4.dp`；`contentDescription` 分别是「导入课表」「添加课程」「回到本周」（中文）。
- tint：导入/添加用 `onSurface`；返回本周**保持现状逻辑**（可用 primary、禁用 `onSurfaceVariant alpha 0.38`）。
- 只改按钮位置，**不改任何点击行为**（导入仍打开导入中心、添加仍打开编辑页）。
- 网格空态（`EmptyState`）里的“手动添加 / 导入”按钮**保留**（那是空态引导）。

---

## 5. 小组件（需求 5/6/7/8）

### 5.1 高度缩小约 1/5（需求 5）

- `res/xml/today_widget_info.xml`：`android:minHeight="110dp" → `88dp；`android:minResizeHeight="110dp" → `88dp`；其它字段（minWidth 180dp、targetCell 3×2、resizeMode、updatePeriodMillis=0、widgetCategory=home_screen）**保持不变**。
- 已放置在桌面的实例**不会自动改变尺寸**（Android 行为），验收时由产品负责长按拖动调整或重新添加；新添加的实例按 88dp 默认值。
- **必须同步复核紧凑阈值**：`TodayWidgetContent.kt` 的 `COMPACT_HEIGHT = 100.dp` 会在 88dp 下把有课状态切成 `CompactContent`（只显示下一节，看不到交替底色列表）。裁决：把 `COMPACT_HEIGHT` 改为 **80.dp**（这样 88dp 仍走完整布局：列表 + 交替底色 + 统计行）。若实测 88dp 放不下 4 行：把 `TodayPlanBuilder.MAX_ITEMS` 4 → 3，并同步更新 `TodayPlanTest` 的期望（`remaining.size=3、`overflowCount = total − 3）

### 5.2 背景色（需求 6）

- `res/drawable/widget_bg_rounded.xml`：`solid #F8F9FC → #EEEDF3`（圆角 16dp 不动）。
- `res/drawable-night/widget_bg_rounded.xml`：**保持 `#111318`**（产品只给了亮色值；若后续给深色值再改）。
- `res/layout/today_widget_loading.xml` 与 `res/layout/today_widget_preview.xml` 的背景同步改成 `@drawable/widget_bg_rounded`（保持与内容一致、避免方形→圆角跳变）。

### 5.3 文字（需求 7/8）

- 右上信息行（`headerRightText`，如「9.17  第 4 周  周四」）：
  - **`fontSize = 16.sp`**（与左侧校名一致）
  - **`color = GlanceTheme.colors.onSurfaceVariant`**（与「今天没有课啦」一致——当前 `AllDoneContent` 用的就是 `onSurfaceVariant`）
  - 不再使用 `primary`；`maxLines = 1`（窄小组件不要换行）
- 校名「西安工程大学」：`16.sp`、`FontWeight.Bold`、颜色**保持 `onSurface`**（标题层级）。若产品指的是“校名也用 onSurfaceVariant”，一行改动即可——如不确定，先按本节实现。
- 其它文字（课程行的时间/课名/教室、统计行、颜文字）：字号与语义不变，只确认在 88dp 下不截断：`compact` 时课程名 12.sp；完整布局时时间 12.sp、课名 14.sp、教室 12.sp；统计行 12.sp。
- 交替行底色（`#81D8CF` / `#F8F5D6`，暗色 alpha 0.20/0.16）**保持**，不要因为高度变化改色。

---

## 6. 文件清单

**新增**：无（若拆 `WeekPager` 则新增 `ui/timetable/components/WeekPager.kt`，同时把 `TimetableScreen.kt` 降到 ≤300 行
**修改**（预期）：`ui/theme/Tokens.kt`、`ui/theme/Color.kt`（+`LightPageBackground`）、`ui/timetable/TimetableScreen.kt`（背景、删 FAB 列、可能拆 WeekPager）、`ui/timetable/components/WeekSelector.kt`（+导入/添加按钮）、`ui/navigation/AppBottomBar.kt`（高度/背景）、`ui/navigation/AppNav.kt`（页面切换淡入）、`widget/TodayWidgetContent.kt`（右上字号与颜色、`COMPACT_HEIGHT` 80dp）、`res/xml/today_widget_info.xml`（高度）、`res/drawable/widget_bg_rounded.xml`（背景色）、`res/layout/today_widget_loading.xml`、`res/layout/today_widget_preview.xml`
**测试**：如改 `MAX_ITEMS` 则同步 `TodayPlanTest.kt`（期望值一起改）
**不动**：`data/`、`domain/`、`importer/`、`reminder/`、学期起始日（8/24 已确认正确）、提醒与小组件的数据逻辑

---

## 7. 明确不做

- 不改学期起始日（8/24 已确认）
- 不改课程行的交替底色配色与色条
- 不改导航语义（底部两项走既有 popToRoot / navigateTo
- 不引入任何新依赖
- 不改表结构

## 8. 回传格式

1. EXIT 码与 BUILD 结果；用例总数（XML 汇总）与失败数
2. 改动文件与行数（≤300 行证明；`TimetableScreen.kt` 必须 ≤300
3. 逐条自评：需求 1~8 各一条，写清落点（文件:行号
4. 三条自证：a) 滑动 10 次后 gfxinfo 卡顿占比（我方复验）b) 主背景/底栏在亮色下的实际观感（截图由我方复验）c) 小组件在 88dp 下的布局走的是完整布局（我方复验）
5. 未验证项如实列出

## 9. 我方真机验收（交付后执行）

1. 周视图背景 = `#DDE0F1`；「我的」页背景不变
2. 底部栏：高度约 40dp、背景 = 主页背景 50% 透明、两项可点、切换有淡入
3. 顶栏右上：导入 / 添加 / 返回本周，位置与顺序正确（返回本周在最右）；点导入仍打开导入中心、点添加仍打开编辑页
4. 左右滑动一次仍只翻一周、动画明显变慢（约 450ms）、不掉帧（`dumpsys gfxinfo` 卡顿 <5%）
5. 小组件：高度约 88dp（重新添加或拖动调整后）、背景 `#EEEDF3`、右上文字与校名同字号 16.sp 且颜色 = 「今天没有课啦」同色（onSurfaceVariant）
6. 小组件有课态：仍显示列表 + 交替底色 + 统计行（不因变矮退化成只显示一节）
7. 深色主题：小组件与 App 都不出现刺眼或不可读
8. 回归：提醒闹钟、点通知落周视图（AC-29）、编辑后小组件刷新（AC-18）
