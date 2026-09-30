# 实现规格 M2-C：周视图同格并排（AC-23）+ 按来源清理（AC-24）

> 面向执行编码的 agent。自包含；权威来源 `docs/05-Spec-规格契约.md`（本次已新增 **AC-23 / AC-24**）。
> 前序：M2-A（文件导入）、M2-B（教务直连，已在真机端到端跑通）。
> 构建（**禁管道**）：`export JAVA_HOME=/home/othc3/opt/jdk-21b && cd /home/othc3/WorkBuddy/安卓软件开发 && /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/build.log 2>&1; echo "EXIT=$?" >> /tmp/build.log`
> 测试基线：**147 个全过**，**不得引入新依赖**（Robolectric/MockK/OkHttp 一律禁止）。

---

## 任务 1（P0）：同格多课必须全部可见（AC-23）

### 1.1 缺陷与证据（真机实测，2026-09-17）

- 周一 1-2 节实际有 **3 门课**：`高数(MANUAL)` + `高等数学(WAKEUP_CSV)` + `马克思主义基本原理(WEB)`
- 现状：`WeekGrid` 用绝对定位把卡片叠在同一位置 → **只有最后绘制的一张可见**，其余静默不可见（截图已确认）
- 全学期统计（真机 DB）：**6 处**同格多课（周1第1节 3 门；周2第1节、周3第3节、周4第3节、周5第1节、周5第5节 各 2 门）

**为什么必须修**：这是"数据在库里、用户看不见"的静默缺陷——学生按课表去上课会漏课，属最高等级缺陷。

### 1.2 布局规则（必须实现为**纯函数**并单测）

新增 `ui/timetable/components/CellLayout.kt`（纯 Kotlin，无 Compose 依赖，便于单测）：

```kotlin
/** 一格内一张卡的位置与尺寸（单位：dp，相对该格左上角） */
data class CardSlot(val dx: Float, val dy: Float, val width: Float, val height: Float)

/** 同格布局结果：可画的卡片 + 被折叠的数量（折叠时只画第一张） */
data class CellArrangement(val slots: List<CardSlot>, val foldedCount: Int)

object CellLayout {
    const val MIN_CARD_WIDTH = 48f    // 横向并排时每卡最小宽度
    const val MIN_CARD_HEIGHT = 24f   // 纵向拆分时每卡最小高度（容 1 行 13sp 文字）

    /**
     * 规则（按优先级，全部为确定性判断）：
     *  1) n == 1                      → 占满整格
     *  2) cellWidth / n >= 48dp       → 横向等分（平板/横屏会走这条）
     *  3) cellHeight / n >= 24dp      → 纵向等分（**手机竖屏走这条**：列宽仅约 46dp，横向切会窄到不可读）
     *  4) 否则                        → 只画第 1 张，其余 foldedCount = n - 1（由 UI 显示「+N」角标）
     */
    fun arrange(n: Int, cellWidth: Float, cellHeight: Float): CellArrangement
}
```

`WeekGrid.kt` 改动要点：
1. 按 `(dayOfWeek)` 分组后，**在列内再做区间重叠分组**：同一天内，若两条安排的 `[startSection..endSection]` 区间有交集 ⇒ 属于同一"冲突组"（注意：跨节连排的 `span>1` 卡片必须按区间判重叠，不能只比 startSection）
2. 每个冲突组用 `BoxWithConstraints` 拿到该列宽度 → 调 `CellLayout.arrange(...)` → 按返回的 `CardSlot` 绝对定位（`offset(x=dx.dp, y=dy.dp)` + `size(width.dp, height.dp)`）
3. 保持既有纪律：**行高仍用绝对定位（52dp/节）**，不得改成 weight 均分
4. 折叠时（`foldedCount > 0`）在组内首卡右上角显示「+N」文字角标（小号文字 + 半透明底；**禁止 emoji 图标**）
5. 同格每张卡都必须可点（点击仍进课程编辑页）

### 1.3 已知妥协（写进代码注释，不要藏）

同格拆到 2-3 张时，单卡触摸目标会小于 44dp（可达性下限）——AC-23 优先保证"**看得见**"，用户可点开编辑页查看全部字段，或用任务 2 的「按来源清理」去掉重复来源的课程。**不要**为了满足触摸目标而牺牲可见性。

### 1.4 测试要求

- `CellLayoutTest`：n=1..5 × 三档尺寸（手机列宽 46dp / 平板 120dp / 极窄 30dp）→ 断言：① 所有 slot 不重叠且不越界 ② 满足 2)/3)/4) 的优先级 ③ `foldedCount` 正确
- `WeekGridOverlapTest`（若可行，纯逻辑层）：给定含重叠与不重叠的 sessions 列表，断言重叠分组结果（同一组内两两相交；不同组互不相交）

---

## 任务 2（P1）：按来源清理课程（AC-24）

### 2.1 需求

用户在**导入中心**能看到并清理"某个来源导入的课程"（教务导入 / WakeUp 文件导入），用于：
- 清掉误导入或测试数据（真实场景：导错了学期、导错了文件）
- **绝不删除手动添加的课程**（沿用 AC-20 的安全不变量）

### 2.2 实现

1. `TimetableRepository` 新增（只读改为写，注意用事务）：
   ```kotlin
   /** 删除某学期某来源的课程，返回删除数量。MANUAL 永不删除（与 applyImport 的兜底一致）。 */
   suspend fun deleteCoursesByTermAndSource(termId: Long, source: String): Int
   ```
   实现：先 `COUNT(*)` 再 `DELETE FROM courses WHERE term_id=? AND source=? AND source != 'MANUAL'`（SQL 里的 `source != 'MANUAL'` 是**硬兜底**，不得省略），走 `tx.run`，安排由 `course_sessions` 的级联自动清理。
2. `ui/import/ImportHubScreen.kt` 增加一个次要入口「清理导入的课程」→ `ui/import/CleanupScreen.kt`（或同页弹窗，你择优，但须满足下面验收）：
   - 列出当前学期各来源的课程数（`教务导入 N 门` / `文件导入 N 门`；N=0 时该项置灰）
   - 选择来源 → **二次确认弹窗**，文案明确「将删除该来源的 N 门课程；手动添加的课程不会被删除」
   - 确认后执行 → 回到导入中心/周视图，并用 Snackbar 报「已清理 N 门」
3. 目标学期：取当前激活学期（与导入一致）

### 2.3 测试要求

- `RepositorySourceCleanupTest`（用既有 `Fakes.kt`）：删除 WEB 后 MANUAL 仍在、CSV 仍在；再删 CSV 后 MANUAL 仍在；返回值为删除数
- UI 层不强制加测试（项目不引入 UI 测试依赖），但需在回传里给出**真机验证步骤**

---

## 3. 通用纪律（违反即退回）

1. 单文件 ≤300 行；无 emoji（含注释）；数据类构造用**具名参数**
2. 改实体/表结构 ⇒ **必须同时升 `AppDatabase.version` 并在 `data/db/Migrations.kt` 补迁移**（本项目已因漏做导致真机升级崩溃）；且保证 `@Database` 注解内**只有一个 version 参数、全文件只有一个 `@Database`**
3. 新增联网/通知等能力 ⇒ 同步在 `AndroidManifest.xml` 声明权限（M2-B 曾因漏 `INTERNET` 导致 WebView 打不开）
4. 不得引入新依赖；不得改动 `parser/xpu` 的端点常量与拦截正则（已在真机验证可用）
5. 若改动 `docs/`，只允许追加 Spec 变更记录，不得改写既有 AC 语义

## 4. 回传要求

1. 构建/测试：命令 + `EXIT=` 码 + 用例数（基线 147 + 新增）；`assembleRelease` 也要过
2. 文件清单（新增/修改 + 行数，全部 ≤300）
3. 逐条自评 **AC-23 / AC-24**（满足/未满足 + 证据：测试名或行号）
4. `CellLayout.arrange` 的**规则优先级**在三种列宽下的实际取值（举例说明手机竖屏 3 门课会走哪条）
5. 未验证项与遗留风险（如实列；真机验证由我方执行）
