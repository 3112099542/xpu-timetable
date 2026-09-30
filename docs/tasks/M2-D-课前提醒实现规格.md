# 实现规格 M2-D：课前提醒 + 提醒设置 + 最小设置入口

> 面向执行编码的 agent。自包含；权威来源 `docs/05-Spec-规格契约.md`（本次不新增 AC，落实既有 **AC-14 / AC-15 / AC-16** 与页面清单第 7/8 项）。
> 前序：M2-A 文件导入、M2-B 教务直连（真机端到端已通）、M2-C 同格并排 + 按来源清理。
> 构建（**禁管道**）：`export JAVA_HOME=/home/othc3/opt/jdk-21b && cd /home/othc3/WorkBuddy/安卓软件开发 && /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/build.log 2>&1; echo "EXIT=$?" >> /tmp/build.log`
> 测试基线：**168 个全过**；**禁引入新依赖**，唯一例外见 §2.3。

---

## 1. 目标

用户在上课前 N 分钟收到系统通知「课名 · 时间 · 教室」，点击直达课表；重启手机后提醒自动恢复；
用户拒绝通知权限时课表功能完全不受影响。

**In**：提醒排程与通知、重启/时区/时间变更后的恢复、提醒设置（开关 + 提前 N 分钟）、设置页最小入口（学期起始日与总周数校正 + 关于/隐私声明）、通知权限引导
**Out**：桌面小组件（M2-E）、单课级"不提醒"覆盖（可留到 M2-E 之后）、农历/节假日调休

## 2. 前置事实（先读，别重复造）

1. **下一节课的计算已存在**：`TimetableRepository.findNextClass(fromEpochMilli): NextClass?`（`data/repository/TimetableRepositoryImpl.kt`），内部用纯函数 `WeekCalc.nextSessionTime(...)`，已处理单双周、跨天、跨周与作息缺失。
2. **作息已预置**：`time_slots` 首次启动写入西工程大标准 10 节（已与真实 `startTime/endTime` 交叉验证 3/3 吻合）。
3. **`course_sessions.week_list`** 已支持显式周次（教务直连真实数据需要），周次判定已在 SQL 与 `WeekCalc` 双处实现。
4. **通知渠道目前不存在**：全项目没有 NotificationManager / 权限声明 / 没有 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`。
5. **偏好存储不存在**：架构计划用 `UserPreferencesStore`；`androidx-datastore-preferences` 已在 `gradle/libs.versions.toml` 声明但**未接线**。

## 3. 实现要求

### 3.1 权限与清单（**必须与代码同批交付**，本项目曾因漏声明 INTERNET 导致功能不可用）

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />      <!-- 13+ 运行时权限 -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />     <!-- 12+ 特殊权限 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />                <!-- 通知唤醒用，可选 -->
```
- `SCHEDULE_EXACT_ALARM` 需在 Android 12+ 用 `AlarmManager.canScheduleExactAlarms()` 检查；**未授权时降级**为 `setAndAllowWhileIdle`（不精确但可用），并在设置页显示"精确提醒需要授权"的引导按钮（跳系统设置）
- 通知渠道：`NotificationChannel`（id 固定如 `class_reminder`，重要性 `IMPORTANCE_HIGH`；渠道名与说明用中文）

### 3.2 排程策略（**单一"下一节课"一次性闹钟**，不要批量排全天）

```
触发重排的时机（全部必须接上）：
  ① App 启动（TimetableApp.onCreate 或 MainActivity 首次进入）
  ② 导入成功 / 课程编辑保存成功 / 课程删除成功 之后
  ③ 设备重启（BootReceiver，ACTION_BOOT_COMPLETED）
  ④ 系统时间或时区变化（ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED）
  ⑤ 闹钟触发后（收到后立即重排下一次）
  ⑥ 用户修改提醒设置（开关或提前分钟）后

排程算法（写在 ReminderScheduler 里，逻辑必须可单测）：
  next = findNextClass(now)
  若 next == null 或 提醒开关关闭 → 取消既有闹钟，结束
  triggerAt = next.startEpochMilli - 提前分钟 * 60_000
  若 triggerAt <= now → 说明"已进入提前窗口"，**立即**触发（而不是等到下一节课）
  否则 → setExactAndAllowWhileIdle(triggerAt)；未获精确闹钟权限时用 setAndAllowWhileIdle
```
- **不补发过期提醒（AC-15）**：若闹钟在课程开始**之后**才被系统唤醒（例如设备长时间关机），要判断 `now < next.endEpochMilli` 才发通知；已结束则只重排下一次，不发
- 闹钟用 `PendingIntent`（`FLAG_IMMUTABLE`；requestCode 固定，保证可取消/覆盖）

### 3.3 通知内容（AC-14）

- 标题：`即将上课`（或更精确的「N 分钟后上课」）
- 正文：`课名 · 起止时间 · 教室`（教室为空时省略该项；时间格式 `HH:mm-HH:mm`）
- 点击：`PendingIntent` 打开 `MainActivity`（直达周视图；若将来有"今日课程"页则跳那里）
- 通知 id 固定（同一节课重复触发不叠加多条）

### 3.4 设置页最小入口（Spec 页面 7/8 的核心项）

新增 `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt`（从周视图顶栏进入，≤1 次点击）：

1. **学期设置**：显示当前学期名；**可编辑**起始日（日期选择器）与总周数；保存后写回 `terms`（用既有 `upsertTerm`）
   —— 现状是「创建本学期」写死"本周一 + 18 周"，用户无法校正，而提醒的正确性完全依赖起始日
2. **提醒**：开关（默认开）+ 提前分钟（默认 15；可选 5/10/15/30/60，或步进输入）；修改后立即重排（时机 ⑥）
3. **精确提醒授权**：若未授权，显示说明 + 「去授权」按钮（跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`）
4. **通知权限**：若被拒，显示说明 + 「去开启」按钮；**课表功能不得受任何影响**（AC-16）
5. **关于 / 隐私声明**（可同页或二级页）：非官方工具声明、凭据零读取零存储零上传、GPL-3.0 与仓库地址占位

### 3.5 偏好存储

接线 `androidx-datastore-preferences`（已在版本目录声明、属架构既定选型，**这是本次唯一允许的"新依赖"**，且无需新增版本号）：
```kotlin
data class ReminderPrefs(val enabled: Boolean = true, val leadMinutes: Int = 15)
interface UserPreferencesStore {           // 实现放 data/prefs/
    val reminderPrefs: Flow<ReminderPrefs>
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setLeadMinutes(minutes: Int)
}
```
装配进 `AppContainer`，与既有的手动 DI 风格一致（不引入 Hilt）。

## 4. 测试要求（纯逻辑必须可单测，系统 API 部分列真机步骤）

- `ReminderScheduleTest`：给定 now + 下一节课 + 提前分钟 → 断言 triggerAt（含：已进入提前窗口 → 立即触发；提醒关闭 → 不排；无下一节课 → 不排）
- `ReminderShouldNotifyTest`：闹钟晚于课程开始时 → 不发（过期不补发，AC-15）；课程进行中 → 发
- 既有 168 个测试必须全绿
- 系统集成部分（权限、重启恢复、通知展示）**不写自动化测试**（项目不引 UI/Instrumentation 依赖），在回传里给出真机验证步骤

## 5. 真机验证方法（我方执行，供你知晓验收口径）

1. 设置提前分钟为 **720**（12 小时）→ 下一节课必落在窗口内 → 应**立即**收到通知（点开看「课名 · 时间 · 教室」与点击跳转）
2. 关闭提醒 → 通知不再出现；重开 → 恢复
3. 拒绝通知权限 → 课表仍可正常查看/导入/编辑（AC-16）
4. `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED`（或重启手机）→ 提醒计划仍在
5. 把提前分钟改回 15 → 验证不再立即触发，而是按 `起始时间 − 15 分钟` 排程

## 6. 通用纪律（违反即退回）

1. 单文件 ≤300 行；无 emoji（含注释）；数据类构造用具名参数
2. **清单权限与代码同批**（漏声明 = 功能直接不可用，本项目已踩两次：INTERNET）
3. 改实体/表结构 ⇒ 同时升 `AppDatabase.version` + 在 `data/db/Migrations.kt` 补迁移；保证 `@Database` 注解内只有一个 `version`、全文件只有一个 `@Database`
4. 不得改 `parser/xpu`、`WeekCalc`、`applyImport` 的既有语义；提醒所需数据一律经 `TimetableRepository`（不得让 UI/调度器直接碰 DAO）
5. 不得使用 `setRepeating` 模拟"每天提醒"（耗电且不可控）；坚持"一次一节课"

## 7. 回传要求

1. 构建/测试：命令 + `EXIT=` 码 + 用例数（基线 168 + 新增）；`assembleRelease` 也要过
2. 文件清单（新增/修改 + 行数，全 ≤300）
3. 逐条自评 **AC-14 / AC-15 / AC-16**（满足/未满足 + 证据：测试名或行号）
4. 排程算法的伪代码或关键代码片段（我要核对"提前窗口内立即触发""过期不补发""无精确闹钟权限降级"三条分支）
5. 未验证项与遗留风险（如实列；系统集成部分由我方真机验证）
