# M4-W 任务规格：小组件改版——优雅圆角 + 头部/右上信息 + 剩余课程与逐节推进

> 来源：产品负责人提供的两张小组件对比截图（上图 = 我们的小组件，黄/红/蓝框标注；下图 = WakeUp 课程表，黄/红框 = 要仿照的内容）+ 文字需求 4 组。
> 本文件**自包含**：你没有接触过本项目也能照此实现。所有现状描述均实测于 2026-09-17 的代码。

---

## 0. 项目背景（30 秒了解）

- 项目：`/home/othc3/WorkBuddy/安卓软件开发` —— 西安工程大学专属安卓课表 App（Kotlin + Jetpack Compose + Material 3 + Glance 小组件），GPL-3.0，无后端。
- 构建（**输出重定向到文件，禁止管道调用 Gradle**，守护进程会占住管道假死）：
  ```
  export JAVA_HOME=/home/othc3/opt/jdk-21b
  cd /home/othc3/WorkBuddy/安卓软件开发
  /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m4w.log 2>&1
  echo "EXIT=$?" >> /tmp/m4w.log
  grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/m4w.log | head -3
  ```
- 用例基线 **212**，不得减少；沙箱内**没有 git**，不要 commit；**不要碰 adb / 不要装机**（真机验收由我方执行，用户手机上有真实课表数据）。

---

## 1. 现状（全部实测于 2026-09-17，可逐条核对）

小组件相关文件（都在 `app/src/main/java/com/gould/xputimetable/widget/`）：

| 文件 | 行数 | 职责 |
|------|------|------|
| `TodayPlan.kt` | 104 | 纯函数：`TodayItem` / `TodayPlan(items, nextIndex, overflowCount, emptyReason)` / `TodayPlanBuilder.build(dayItems, nowMinute, hasTerm)` |
| `TodayWidget.kt` | 130 | `GlanceAppWidget`：取数（激活学期 → 当前周 → `observeWeek` → 今天 → 作息分钟）+ `GlanceTheme` 双主题色 + 装载失败降级为 NO_TERM 空态 |
| `TodayWidgetContent.kt` | 249 | 渲染：`TitleRow()`（「📅 今天 · 周四」）+ 课程行（色条 + 时间 + 课名 + 教室）+ `FooterLine`（「今天的课已全部结束」）+ `EmptyContent`（居中图标 + 两行文案） |
| `TodayWidgetReceiver.kt` | 16 | `GlanceAppWidgetReceiver` |
| `WidgetRefresher.kt` | 21 | 唯一刷新入口 `refreshAll(context)`（`TodayWidget().updateAll`） |
| `DailyWidgetRefreshWorker.kt` | 59 | WorkManager 每日 00:05 周期任务（KEEP，唯一名 `widget_daily_refresh`） |

资源与元数据：`res/xml/today_widget_info.xml`（`updatePeriodMillis=0`、`widgetCategory=home_screen`）、`res/layout/today_widget_loading.xml`、`res/layout/today_widget_preview.xml`、`res/values/strings.xml`（`widget_label`/`widget_description`）。

**刷新触发源（现有 5 类，全部只调 `WidgetRefresher.refreshAll`）**：App 启动（`TimetableApp.onCreate`）；数据变更（编辑/导入/清理/学期，经 `AppContainer.onDataChanged`）；小组件添加/改尺寸（receiver）；每日 00:05 Worker；**提醒触发后**（`ReminderManager` 的 `afterAlarmFired` 钩子）。

**注意**：**"课程结束时刻"不是刷新触发源** —— 所以现在上完一节课后，小组件会一直显示那一节，直到下一个自然触发点。这是本任务要解决的核心问题之一。

---

## 2. 产品需求（对照截图）

产品负责人给了一张对比图：上图是**我们的**小组件（黄框 = 标题「📅 今天 · 周四」，红框 = 右上空白，蓝框 = 「今天的课已全部结束」），下图是 **WakeUp 课程表**的小组件（黄框 = 左上「西安工程大学」，红框 = 右上「9.17 第4周 周四」蓝色文字）。要求：

1. **R-圆角**：为小组件增加优雅的圆角设计
2. **R-头部**：黄框内容（我们的标题）按 WakeUp 黄框样式替换
3. **R-右上**：红框空白处按 WakeUp 红框样式，显示「日期 · 第 N 周 · 周几」
4. **R-剩余统计**：有课时，蓝框区域显示「**今天还有 x 节课，加油！**」（x = 今天剩余课程数）
5. **R-逐节推进**：**每上完一节课，从列表删掉那一节、下一节上移**
6. **R-交替底色**：为区分相邻课程，行背景增加浅色底，`#81D8CF` 与 `#F8F5D6` 每**两行**交替
7. **R-全部上完空态**：今日课程全部上完后，小组件**中间**显示：
   ```
   (๑˃̵ᴗ˂̵)
   今天没有课啦
   ```
   （颜文字为产品负责人明确指定，不受项目"无 emoji"纪律约束——它是字符组合而非 emoji）

---

## 3. 设计裁决（按此实现，不要另起方案）

### 3.1 头部仿 WakeUp（替换黄框内容）

- **左上**：`「西安工程大学」`（新字符串资源 `widget_school_name`），`titleMedium` + `FontWeight.SemiBold`，色 `onSurface`。**删除现有 `TitleRow` 的日历图标与「今天 · 周四」**。
- **右上**：`「M.d  第 N 周  周X」`（如 `9.17  第 1 周  周四`），单行右对齐，色 `colorScheme.primary`（仿 WakeUp 蓝字），`labelMedium`。
  - 内容 = 今天日期（`M.d` 格式）+ 学期周次 + 今天星期。
  - **周次口径说明**：我们显示的是**本 App 学期定义下的周次**（当前为第 1 周），与 WakeUp 的"第 4 周"不同源（两者学期起始日定义不同）。显示我们自己的口径，不要试图对齐 WakeUp。
- M3 `ColorProviders` 主题与既有取数逻辑**不动**。

### 3.2 圆角（widget 根背景）

- 新增 `res/drawable/widget_bg_rounded.xml`（shape：solid `#F8F9FC` + corners `16dp`）与 `res/drawable-night/widget_bg_rounded.xml`（solid `#111318`，同样圆角）。
- Glance 根：`GlanceModifier.background(ImageProvider(R.drawable.widget_bg_rounded)).padding(12.dp)`。
  - **内容内边距 12dp**：圆角 16dp 下避免文字贴角。
- 形状 drawable 自带 outline → 系统（Android 12+）会按背景轮廓裁剪出圆角 ✓；`res/layout/today_widget_loading.xml` 与 preview 同步加同款背景与圆角，避免"方形加载态 → 圆角内容"跳变。

### 3.3 列表行：只显示**未结束**课程 + 交替底色（替换蓝框内容的前半）

`TodayPlanBuilder.build` 的语义变更（**这是纯函数改动，必须先改单测**）：

```kotlin
data class TodayPlan(
    val remaining: List<TodayItem>,   // ★ 未结束课程（nowMinute < endMinute），升序，至多 4 条
    val remainingTotal: Int,          // ★ 未结束课程总数（「今天还有 x 节课」的 x，含被折叠的）
    val emptyReason: EmptyReason,     // NO_TERM / ALL_DONE_OR_NONE / NONE
)
```

- **列表内容 = 未结束课程**：已结束的（`nowMinute >= endMinute`）**不进列表** → "每上完一节就消失、下一节上移"由刷新时机保证（§3.5）。
- `nextIndex` 字段删除：列表里第一门就是"下一节/正在上"，无需高亮下标（高亮样式见 §3.4，**保留**在 `remaining[0]` 上：底色 + 加粗，视觉不变）。
- `overflowCount` 保留（仍按 `remainingTotal - visible` 计算）。
- 空态收敛为两类（**取代 M3 规格里的三态文案**，M3 §4.2 由本规格取代并已回写）：
  - `NO_TERM` → 「还没有课表」+「打开 App 导入」（引导态，保留）
  - `ALL_DONE_OR_NONE` → 居中：`(๑˃̵ᴗ˂̵)` + `今天没有课啦`（**今日无课**与**今日课上完**统一用此空态——产品负责人明确指定）
  - `EmptyContent` 的日历图标删除（仿 WakeUp 的极简居中：颜文字 + 一行文案）。

**单测（`TodayPlanTest.kt` 重写，≥9 例，全部改用新语义）**：

| # | 场景 | 期望 |
|---|------|------|
| 1 | 今日 3 门，now=450（7:30） | remaining 3 门升序，remainingTotal=3，首门即第 1 项 |
| 2 | 第 1 门进行中（now=510，8:00-9:50） | 仍在 remaining 首位（未结束就不删） |
| 3 | 第 1 门已结束（now=600，8:00-9:50 结束于 590） | 该门**不进列表** |
| 4 | 今日 2 门全部已结束 | emptyReason=ALL_DONE_OR_NONE |
| 5 | 今日无课 | emptyReason=ALL_DONE_OR_NONE |
| 6 | 无激活学期 | NO_TERM（优先级最高） |
| 7 | 6 门，now=0 | remaining 取前 4，remainingTotal=6 |
| 8 | 连堂 3-4 节（600-740） | 作为一个 item |
| 9 | 边界：now == endMinute | 视为已结束（严格 `<`），不进列表 |

### 3.4 列表行底色：`#81D8CF` / `#F8F5D6` 逐行交替（R-交替底色）

- 规则：`remaining` **按行索引交替**——偶数行（0,2,4…）`#81D8CF`，奇数行（1,3,5…）`#F8F5D6`（周期 2 的逐行交替，即"每两行完成一次两色循环"）。
- **色条保留**（`CoursePalette.base(colorTag)`，4dp 竖条）——行底色负责"区分相邻课的节奏"，色条负责"课程归属色"，两者职责不同。
- 行内文字：`onSurface`（深色字在两个浅色底上对比度均 ≥ 7:1，自证即可）。
- **暗色主题**：直接用题给亮色会刺眼 → 用 `ColorProvider(day, night)`：
  ```kotlin
  // 偶数行
  ColorProvider(day = Color(0xFF81D8CF), night = Color(0xFF81D8CF).copy(alpha = 0.20f))
  // 奇数行
  ColorProvider(day = Color(0xFFF8F5D6), night = Color(0xFFF8F5D6).copy(alpha = 0.16f))
  ```
  （夜色下为半透明叠加在暗 surface 上，文字用 `onSurface` 浅色。）
- 行圆角 8dp、行内边距 6dp×4dp（替换现有行背景逻辑）。
- 时间与教室文字色：`onSurface` 的 70%（比当前 onSurfaceVariant 更深一点，保证在彩底上可读）。

### 3.5 底部统计行 + "上完一节就消失"的刷新时机（替换蓝框内容 + R-逐节推进）

- 列表下方一行（左对齐，沿用现有 `FooterLine` 样式）：**`今天还有 x 节课，加油！`**（x = `remainingTotal`）。
- 空态（ALL_DONE_OR_NONE / NO_TERM）时不显示该行，显示 §3.3 的居中空态。
- **逐节推进的刷新时机**：现有 5 类触发源不覆盖"课程结束时刻"，**必须新增**：

  新增 `widget/EndOfClassRefreshScheduler.kt`：

  ```kotlin
  object EndOfClassRefreshScheduler {
      private const val REQUEST_CODE = 4002   // 与提醒闹钟(4001)不冲突，独立 PendingIntent

      /** 计算今天下一门"未结束课程"的结束时刻并安排精确刷新；无则取消。 */
      fun schedule(context: Context, nextEndEpochMilli: Long?) { … }
  }
  ```

  - 用 `AlarmManager.setExactAndAllowWhileIdle`（**精确闹钟权限本机已授权**，`cmd appops get` → `Uid mode: SCHEDULE_EXACT_ALARM: allow`）；`PendingIntent.getBroadcast` + 独立 `REQUEST_CODE = 4002`，`FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`（重复安排即覆盖，幂等）。
  - 触发目标：`.widget.TodayWidgetReceiver`（复用现有 receiver，action 用 `ACTION_APPWIDGET_UPDATE`，extras 带 `appWidgetIds`——**不要新增 receiver**）。
  - 每次小组件取数完成时（`TodayWidget.provideGlance` 尾部）调用 `schedule(context, 下一门未结束课程的结束时刻)`；无未结束课程时传 null → 取消该闹钟。
  - `SystemEventReceiver`（BOOT / TIME_SET / TIMEZONE_CHANGED 已在监听）收到事件后**也会重排提醒** → 在其 `onReceive` 里同样调用一次 `EndOfClassRefreshScheduler` 的重排（经 `WidgetRefresher` 顺带）——保证重启/改时区后不丢。
  - 注意：`setExactAndAllowWhileIdle` 在 Doze 下有节流，但课程结束时刻的刷新允许秒级偏差（下一节还没开始），可接受；**不要**改用 `setAndAllowWhileIdle`（会延迟到小时级，产品可见）。
  - 实现提示：结束时刻 = `LocalDate.now().atStartOfDay().plusMinutes(endMinute.toLong())` 转 epoch（`systemDefault()` 时区）；若该时刻已过（课程刚结束），直接立即刷新一次并返回（`schedule` 内部对"已过时刻"改为立即 `refreshAll`，不再挂闹钟）。

### 3.6 右上信息（替换红框内容）

- `M.d` 用 `DateTimeFormatter.ofPattern("M.d")`；周次 = 学期周次（第 1 周）；星期 = 今天周几（复用 `weekdayCn`）。
- 单行 `labelMedium`，右对齐，`colorScheme.primary`；暗色主题下 `primary` 自动为浅蓝 ✓ 对比度自证 ≥4.5:1。

---

## 4. 文件清单

**新增**：`widget/EndOfClassRefreshScheduler.kt`（≤60 行）、`res/drawable/widget_bg_rounded.xml`、`res/drawable-night/widget_bg_rounded.xml`
**修改**：`TodayPlan.kt`（语义变更+重写单测）、`TodayWidget.kt`（背景/圆角/内边距/调度调用）、`TodayWidgetContent.kt`（头部、行底色交替、空态、统计行）、`SystemEventReceiver.kt`（事件后重排小组件调度）、`res/layout/today_widget_loading.xml`、`res/layout/today_widget_preview.xml`、`res/values/strings.xml`（+`widget_school_name`）
**删除**：无整文件删除；`TitleRow` 的日历图标与「今天 · 周四」逻辑删除

---

## 5. 明确不做

- 不做小组件内点击课程跳详情、不做周视图小组件、不做锁屏小组件
- 不改周视图/App 的任何 UI（本任务只动 `widget/` 与上述资源）
- 不改表结构（`AppDatabase.version` 保持 2）、不动提醒调度（`reminder/`）
- 不引入任何新依赖（图标库、Glance、WorkManager 均已就位）

---

## 6. 交付纪律（本项目硬门禁）

- 单文件 ≤ 300 行；除产品指定的颜文字外**无 emoji**；构造数据类**具名参数**
- 版本目录（`libs.versions.toml`）是唯一事实源，不在模块里写死版本
- **不改表结构**；不动 `ui/timetable/`、`ui/navigation/`、`data/`、`domain/`、`importer/`、`reminder/`（唯一例外：`SystemEventReceiver` 里加一行重排调用）
- 沙箱内没有 git，不要 commit/push

## 7. 回传格式

1. 构建命令的 EXIT 码与 BUILD 结果；用例总数（XML 汇总）与失败数
2. 新增/修改文件与行数
3. 逐条自评（见 §8 的 4 条验收 + §3.3 的 9 例单测逐个给出实际返回值）
4. `grep -rn "今天 · 周四\|lucide_ic_calendar" app/src/main/java/com/gould/xputimetable/widget/` 的输出（应已无旧头部残留）
5. 未验证项如实列出（真机项由我方复验）

## 8. 我方真机验收（交付后执行）

1. 桌面小组件呈**圆角卡片**（16dp），内容不贴角；加载态与预览图同款圆角
2. 头部：左上「西安工程大学」、右上「9.17 第 1 周 周四」（primary 色右对齐）
3. 有未结束课：列表**只显示未结束课程**、行底色 `#81D8CF`/`#F8F5D6` 逐行交替、色条保留、底部「今天还有 x 节课，加油！」且 x 与库内未结束课数一致
4. 到达某节课结束时刻（次日白天自然发生，或由我方核对 `dumpsys alarm` 中 `REQUEST_CODE=4002` 闹钟的注册时刻）→ 该课从列表消失、下一节上移、x 减 1
5. 全部上完 / 今日无课 → 居中 `(๑˃̵ᴗ˂̵)` + 「今天没有课啦」
6. 深色主题切换 → 行底色为半透明暗色变体、文字可读
7. 回归：点提醒通知落周视图（AC-29）、编辑课程后小组件即时刷新（AC-18）、重启后闹钟与刷新不丢

---

## 11. 真机复验后的修复任务（2026-09-17 复验，只此一项）

### 复验结果（先说通过的）
- ✅ 圆角卡片（16dp）渲染正确、观感优雅；内容不贴角
- ✅ 空态（今日课上完）渲染正确：居中 (๑˃̵ᴗ˂̵) + 「今天没有课啦」，与 WakeUp 空态观感一致
- ✅ `TodayPlan` 新语义、单测 17 例全过；构建 212/0

### ★ 缺陷（P1）：**空态下头部缺失**
- 真机证据（截图）：空态下小组件**只有居中的颜文字与文案**，没有「西安工程大学」头部，也没有右上「9.17 第 N 周 周X」
- 对照：WakeUp 的空态**带头部**（校名 + 日期周次星期）——这正是要仿照的布局
- 根因：头部渲染被放在了「有课程列表」的分支里（`TodayWidgetContent` 的 `FullContent` 内），空态分支（`EmptyContent`）没有头部 → 规格遗漏（规格 §3.3 只写了空态内容，没写"头部在任何状态都必须渲染"），**规格责任在撰写方（我方），已补写本节**

### 修法（最小改动）
把头部行（校名 + 右上「M.d 第 N 周 周X」）**提出为独立 Composable**，并在**所有状态**的渲染路径最上方调用：
```
Column(fillMaxSize) {
    WidgetHeader(weekNumber, hasTerm)      // ← 所有状态都有
    when (plan.emptyReason) { ... }         // 空态：居中颜文字；有课：列表 + 统计行
}
```
- 空态时右上信息规则：学期内（含假期越界）→ 「M.d 周X」；**无学期** → 只显示「M.d」（无周次可显示）
- 居中空态块放在头部下方的剩余空间内（`defaultWeight`），保持视觉居中

### 修复验收（真机）
1. 空态下：头部（校名 + 右上信息）**必须**渲染，颜文字块在其下方居中
2. 有课状态下：头部 + 列表 + 统计行，与现有实现一致（回归）
3. 圆角、暗色主题不受影响（回归）

### 回传
改动文件与行数 + 空态/有课两态的头像截图说明（我方真机复验）+ 未验证项
