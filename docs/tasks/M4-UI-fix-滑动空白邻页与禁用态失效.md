# M4-UI-fix 任务规格：滑动过程中的空白邻页 + 「返回本周」禁用态失效

> 来源：M4-UI 真机验收（2026-09-17）。两个缺陷**都有真机证据**，都属于 P1（不影响数据，但直接违背 AC-31 与"平滑优雅"的产品要求）。
> 只改 `ui/timetable/` 内文件，**不改数据层语义、不加依赖、不改表结构**。

---

## 缺陷 1（P1）：滑动过程中，邻页是**空白课表**

### 现象（真机证据）
慢速拖动（`input motionevent DOWN/MOVE` 拖到 50% 时截图）：左半是本周课程正在滑出，**右半进入的邻页只有「9 月」月份徽标 + 时间轴（1/2/3…），课程卡片全空**；手指松开落定后（约 1–2 帧）课程才出现。

用户感受就是：**"滑动时看到一张空课表"** —— 与需求原话「动画速率要求平滑优雅」直接冲突。

### 根因（精确到行）
`ui/timetable/TimetableScreen.kt` 的 `WeekPager` 页体内：

```kotlin
items = if (isCurrentWeek) state.items else emptyList(),   // ← 非当前页一律空
```

而 `state.items` 只承载**当前周**的数据（`TimetableViewModel` 用 `observeWeek(term.id, week)` 按周查询）。所以邻页在滑动过程中**没有任何可渲染的数据**。

### 修法（预取相邻周）
在 `TimetableViewModel` 增加相邻周缓存，页面按 `week` 取数：

1. 新增状态字段：`val weekItems: Map<Int, List<SessionWithCourse>>`（建议键为周次，**只保留 ±1 三周**，避免查询与内存随周次增长）。
2. 订阅：在现有"当前周"流之外，再为 `week-1`、`week`、`week+1` 各订阅一次 `repository.observeWeek(term.id, w)`（用 `combine` 或 `merge` 汇入同一 map 写入；**越界周次按 1..totalWeeks 裁剪**，不查不存）。
3. 页面渲染改为：`items = state.weekItems[week].orEmpty()`（**不再依赖 `isCurrentWeek`**）。
4. `EmptyState` 的判定条件：仅当**该页就是当前周**且该周确实为空时才显示（保持现状语义，不要因为邻页数据未到就误显示空态）。

### 验收判据（真机）
- 慢速拖动到约 50% 时截图：**邻页必须显示那一周的课程卡片**，不得为空网格
- 落定后**不得**闪回上一周的内容；滚动位置仍保持（回归 M4-UI 验收第 6 步）
- 学期首周 / 末周：越界侧不查库、不报错、不显示错误空态
- 连续快速左右滑动 10 次后 `dumpsys gfxinfo` 卡顿帧占比 **< 5%**（现状 3.34%，修好后应不劣化）

---

## 缺陷 2（P1）：「返回本周」的禁用态**永远不出现**，且启动即被"钉"在覆盖状态

### 现象（真机证据）
当前显示的就是当前周（第 1 周）时，`uiautomator` 报告「回到本周」按钮 `enabled=true`（应 disabled，视觉 alpha 0.38）。三次复现（启动后、双击回本周后、滑动回本周后）。

### 根因（精确到行）
`ui/timetable/TimetableScreen.kt`（`WeekPager`）的双向同步：

```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect { page ->
        if (programmaticTarget == null) viewModel.setWeek(page + 1)   // ← 首次发射也会回写
    }
}
```

`HorizontalPager` 首次组合即发射 `currentPage = 0` → `setWeek(1)` → `TimetableViewModel.setWeek` 写入 `weekOverride = 1`（非 null）
→ `weekIsOverridden = true` **从启动起就恒为真**，而按钮的可用性绑定的正是 `overridden`。

### 影响（不止外观）
1. AC-31 要求的"已在当前周 → 禁用态"从未达成；
2. **语义退化**：`weekOverride` 一旦写入就代表"用户手动指定了周次"，于是"默认自动跟随当前周"失效——例如开学日/周次推进后，应用仍被钉在启动时的那一周上，直到用户手动点一次「返回本周」。

### 修法（回写前加守卫）
```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect { page ->
        val target = page + 1
        // 只接受"与 VM 不一致"的变化：既跳过首次发射，也跳过程序化滚动期间的中间帧
        if (programmaticTarget == null && target != viewModel.state.value.week) {
            viewModel.setWeek(target)
        }
    }
}
```
（如 VM 未暴露 `state` 供读取，可改为在 `WeekPager` 内读 `state.week`——`state` 已是入参，等价可用。）

> 备选实现（二选一，不要都做）：仅在 `pagerState.isScrollInProgress` 为真时接受回写。但**必须**保证：程序化 `animateScrollToPage` 期间不回写（现有 `programmaticTarget` 守卫保留）。

### 验收判据（真机）
1. 冷启动 → 停在当前周 → 「回到本周」**禁用**（`enabled=false`、视觉 alpha 0.38）
2. 滑到第 2 周 → 按钮变**可用**（primary 色）；顶部表头显示「第 2 周」
3. 双击标题回到第 1 周 → 按钮**又变禁用**
4. 冷启动后**不做任何手势**保持 10 秒 → 周次仍等于自动计算值（不被写成覆盖值）
   - 可机械核对：`WeekSelector` 传入的 `overridden` 为 false（或临时日志）；若不便观测，用"按钮禁用态"作为等价判据

---

## 交付纪律

- 只改 `ui/timetable/TimetableScreen.kt` 与 `ui/timetable/TimetableViewModel.kt`（如需要可动 `ui/timetable/components/*`）
- 单文件 ≤ 300 行；无 emoji；**不加依赖**；不改表结构（`AppDatabase.version` 保持 2）；不动 `data/`、`domain/`、`reminder/`、`widget/`
- 沙箱无 git，不要 commit；**不要碰 adb / 不要装机**

## 构建与自测

```
export JAVA_HOME=/home/othc3/opt/jdk-21b
cd /home/othc3/WorkBuddy/安卓软件开发
/home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m4fix.log 2>&1
echo "EXIT=$?" >> /tmp/m4fix.log
grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/m4fix.log | head -3
```
（**禁止**管道调用 Gradle。）用例基线 **212**，不得减少；若为"越界周次裁剪"或"回写守卫"新增纯函数，请补单测并报告增量。

## 回传格式

1. 两条命令的 EXIT 码与 BUILD 结果；用例总数与失败数
2. 改动文件与行数；`weekItems` 的键范围与订阅方式（贴关键代码）
3. 逐条自评 **缺陷 1 验收 4 项** 与 **缺陷 2 验收 4 项**
4. 未验证项如实列出（真机项由我方复验：慢速拖动中途截图、按钮禁用态、gfxinfo）
