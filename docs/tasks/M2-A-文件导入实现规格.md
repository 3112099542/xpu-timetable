# 实现规格 M2-A：文件导入通道（WakeUp CSV）全链路

> 面向执行编码的 agent。本文件自包含：契约、文件路径、验收标准、验证命令都在下面。
> 权威来源：`docs/05-Spec-规格契约.md`（规格契约）、`docs/03-技术架构文档.md`（架构）、`docs/decisions/ADR-*.md`。
> **与本文档冲突时，以 Spec 与架构文档为准，并在回传里指出冲突点，不要自行改契约。**

## 0. 项目快照（只读，先建立认知）

- 项目路径：`/home/othc3/WorkBuddy/安卓软件开发`；包名 `com.gould.xputimetable`
- 技术栈：Kotlin 2.3.21、AGP 9.x、Compose（Material 3）、**Room 3.0.3（`androidx.room3`）**、KSP 2.3.12、minSdk 26 / targetSdk 36、单模块 + 分层分包 + 手动 DI（无 Hilt/Koin）
- 已实现：5 张表（terms/courses/course_sessions/time_slots/import_logs）、`TimetableRepository` 接口与实现、周视图（WeekGrid 绝对定位）、手动添加/编辑课程表单、启动时预置西工程大作息（10 节）
- 单元测试现状：**66 个全过**（JVM，JUnit4 + kotlinx-coroutines-test，用 `Fakes.kt` 手造假 DAO，**禁止引入 Robolectric/MockK**）
- 构建命令（**严禁管道 `| tail`，守护进程会占管道假死**）：
  ```
  export JAVA_HOME=/home/othc3/opt/jdk-21b
  cd /home/othc3/WorkBuddy/安卓软件开发
  /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/build.log 2>&1
  echo "EXIT=$?" >> /tmp/build.log   # 然后读 /tmp/build.log
  ```

## 1. 本次目标与范围

**目标**：让用户不用手打，用 WakeUp 导出的课程文件（CSV）一次性导入课表，并走完「选择文件 → 解析 → 预览确认 → 入库 → 回周视图」全流程。

**In（本次要做）**
1. `parser/api` 三个类型：`PayloadParser`、`ScheduleParseResult`、`ParseError`（照架构 §5.2 原样实现）
2. `parser/wakeup/WakeupCsvParser`：CSV 文本 → `ParsedSchedule`（容错解析，见 §4）
3. `importer/api`：`ScheduleImporter`、`ImportPayload`、`ImportResult`、`ImportSource`（照架构 §5.4，另含我批准的补充，见 §3）
4. `importer/wakeup/WakeupCsvImporter`：文件通道实现，最终汇入 `TimetableRepository.applyImport(...)`
5. UI 三件套（页面 + ViewModel + 私有组件）：**导入中心页**、**导入预览确认页**、**解析失败提示**（可作为预览页的失败态）
6. 接线：从周视图空状态与主入口进入导入中心（复用现有 `MainActivity` 的状态导航，见 §6）
7. 测试：解析器与导入器单测（含 `parser/wakeup/fixtures/` 固定样本文件）

**Out（本次不做，别越界）**
- 教务直连 WebView 导入（M2-B，依赖真实抓包样本，暂不实现 `parser/xpu`）
- 课前提醒 / 桌面小组件 / 设置页 / Excel 通道（后续任务）
- 引入 Navigation 3（**明确决定：暂不引入**，沿用现有状态导航；理由：`navigation3` 仍是 RC，且当前状态导航已够用。若你发现确实需要，先在回传里说明，不要擅自加依赖）

## 2. 必读的既有契约（改动前先读这些文件）

- `app/src/main/java/com/gould/xputimetable/domain/repository/TimetableRepository.kt` — 尤其 `applyImport(schedule: ParsedSchedule): ImportSummary`（**已实现 AC-13/20/21 语义：按来源整体替换、MANUAL 不删、返回 replacedCount/preservedManualCount/suspectedDuplicateNames**）
- `app/src/main/java/com/gould/xputimetable/domain/model/ImportTypes.kt` — `ParsedSchedule(courses, sessions, term)`、`ImportSummary`
  - 说明：架构目录树里 `ParsedSchedule` 写在 `parser/api/`，但它被 domain 的仓库接口引用，**实际保留在 `domain/model/ImportTypes.kt`**（已批准的现状偏差），parser 直接复用，**不要另建一份**
- `app/src/main/java/com/gould/xputimetable/domain/model/Course.kt` — 含 `CourseSource`（`WEB` / `WAKEUP_CSV` / `MANUAL`）与 `markEdited()`
- `app/src/main/java/com/gould/xputimetable/data/repository/TimetableRepositoryImpl.kt` — 看 `applyImport` 里"同 id 冲突保留用户版本"的防线，理解导入语义
- `app/src/main/java/com/gould/xputimetable/ui/timetable/TimetableScreen.kt` — 现有空状态的两个入口按钮（「手动添加」「从教务导入」）与 FAB，作为进入导入中心的入口

## 3. 接口契约（照抄架构，不要自由发挥）

架构 §5.2（`parser/api`）：
```kotlin
interface PayloadParser<I> {
    fun parse(input: I): ScheduleParseResult
}

sealed interface ScheduleParseResult {
    data class Success(val schedule: ParsedSchedule) : ScheduleParseResult
    data class Failure(val error: ParseError) : ScheduleParseResult
}

sealed interface ParseError {
    data object EmptyPayload : ParseError                      // 空文件/空内容
    data class SchemaMismatch(val detail: String) : ParseError // 表头完全认不出来（改版信号）
    data class InvalidData(val detail: String) : ParseError    // 数据矛盾（如周次越界）
}
```

架构 §5.4（`importer/api`）**+ 我批准的补充**（为落实 AC-10「确认后才写入」，`import()` 只解析并返回待确认结果，真正入库用 `commit()`）：
```kotlin
enum class ImportSource { WEB, WAKEUP_CSV, MANUAL }

/** 原始载荷：本次只用到 text（CSV 文本），uri 便于日志与后续扩展 */
data class ImportPayload(
    val text: String? = null,
    val uri: String? = null,
    val displayName: String? = null,
)

sealed interface ImportResult {
    /** 解析成功，等待用户在预览页确认；anomalies = 被跳过的异常条目说明 */
    data class NeedsConfirm(
        val parsed: ParsedSchedule,
        val courseCount: Int,
        val sessionCount: Int,
        val anomalies: List<String>,
    ) : ImportResult

    /** 解析失败；retainedPayload 用于"不丢失已拦截数据"（AC-11） */
    data class Failure(val error: ParseError, val retainedPayload: ImportPayload) : ImportResult
}

interface ScheduleImporter {
    val source: ImportSource
    suspend fun import(payload: ImportPayload): ImportResult
    /** 预览页确认后调用：走仓库统一入口入库，返回摘要（含 replacedCount / preservedManualCount / suspectedDuplicateNames） */
    suspend fun commit(parsed: ParsedSchedule): ImportSummary
}
```

## 4. CSV 解析规则（容错优先，这是本任务的技术核心）

WakeUp 导出的列名各版本可能不同，**不要假设固定列序**，用"表头关键词匹配 + 别名表"：

| 目标字段 | 表头关键词（含其一即命中，忽略大小写与空格） |
|----------|------------------------------------------|
| 课程名 | 课程名、课程、名称、课名、course、name |
| 教师 | 教师、老师、授课教师、teacher |
| 教室 | 教室、地点、上课地点、位置、room、place |
| 星期几 | 星期、周几、day、weekday |
| 开始节次 | 开始节次、起始节次、开始、start |
| 结束节次 | 结束节次、结束、end |
| 周次 | 周次、上课周、weeks |
| 单双周 | 单双周、单周、双周、weektype |

解析要求：
- **表头识别**：找到包含「课程名/课程」关键词的那一行作为表头；找不到 → `Failure(SchemaMismatch("未识别到表头"))`
- **内容为空**：文件为空或去掉空行后无数据行 → `Failure(EmptyPayload)`
- **周次字符串**要兼容这些写法：`1-16`、`1-16周`、`1-16(周)`、`1,3,5,7,9`、`1-16单`、`单周`、`双周`、`1-16 单周`。规则：取区间 → `startWeek..endWeek`；逗号列表 → 无法表示为单一区间时，取最小/最大作为区间，并把单双周标记为 `ALL`（并在 anomalies 里记一条说明）；含"单" → `ODD`，含"双" → `EVEN`，都没有 → `ALL`
- **星期**兼容：`星期一/周一/一/1/Monday` → 1..7
- **一行多节课**（如周次列写 `1-16`、节次列写 `1-2`）：一条安排即可
- **脏行处理**：任何一行解析不出课程名或星期/节次 → **跳过该行**，并在 `anomalies` 追加 `"第N行：<原因>"`，**不算失败**
- **全部行都解析失败** → `Failure(InvalidData("未解析出任何课程"))`
- 课程 id：用 (课程名 + 教师) 生成稳定 key（如 `"${name.trim()}|${teacher ?: ""}"` 的 UUID v5 或简单哈希），保证重复导入同一文件是"覆盖"而不是"新增重复"
- 生成的 `ParsedSchedule`：`courses` 的 `source` 必须填 `CourseSource.WAKEUP_CSV`；`term` 填 `null`（由仓库回退到当前激活学期）

## 5. UI 规范

三页（按架构 §6.1 硬规则：每页 `XxxScreen.kt` + `XxxViewModel.kt` + `components/`）：
- `ui/import/ImportHubScreen.kt` — 三条通道入口（教务直连 **置灰+说明「暂未开放，需抓包适配」** / WakeUp CSV **可用** / 手动添加），以及"选择文件"动作（用 `ActivityResultContracts.GetContent`）
- `ui/import/ImportPreviewScreen.kt` — 展示：课程数 / 安排条数 / 异常条目列表 / **覆盖范围说明（将替换 N 门同来源课程；手动课程不会删除）** / 疑似重复提示；底部「确认导入」「取消」
- 解析失败态：展示「解析失败：<结构化原因>」+ 引导到兜底通道（手动添加 / 换文件），**不得闪退、不得丢失已选文件**

**必须遵守的界面实现铁律**（Spec §10，2026-09-17 新增，源自两个真实缺陷）：
1. 页面用 `Scaffold`；**自绘顶栏必须 `statusBarsPadding()`**；含输入/选择的页面要 `imePadding()`
2. Compose 表单/列表必须收敛重组范围：子组件只接收**稳定类型参数**；回调**一律用方法引用**（`viewModel::xxx`），**禁止内联 lambda**；选项文案用文件级常量，**禁止组合期新建 `List`**。症状：一次击键触发整页重组 → 掉帧
3. 图标只能经 `ui/components/AppIcons.kt` 引用，**禁止直接写 `R.drawable.lucide_*`**
4. 触摸目标 ≥ 44dp（胶囊/色点类控件用"外层大点击区 + 内层视觉尺寸"）
5. 单文件 ≤ 300 行；Screen 里禁止直接调仓库（必须经 ViewModel）

## 6. 导航接线

现有 `MainActivity` 用一个 `sealed interface EditTarget` 做状态导航（周视图 / 课程编辑）。本次**沿用该模式**扩展为「周视图 / 课程编辑 / 导入中心 / 导入预览」四种状态（可用两个字段或合并成一个 sealed 类型），**不要引入 Navigation 3**。
入口：周视图空状态的「从教务导入」按钮 + 顶栏/菜单（你决定放哪，但必须 ≤1 次点击进入导入中心）。

## 7. 验收标准（逐条对照，完成后逐条自评）

| AC | 要求 |
|----|------|
| AC-08 | 导入中心提供三条通道（教务直连 / WakeUp CSV / 手动添加）；文件通道在无网络时可用 |
| AC-10 | 解析成功后**必须**先展示预览（课程数/条数/异常条目），用户确认后才写入本地 |
| AC-11 | 解析失败提示「解析失败：<原因>」并引导兜底通道；**不得闪退、不得丢失已选文件数据** |
| AC-13 | 预览页明示覆盖范围（将替换的该来源课程数 + 手动课程不会删除），确认后按来源整体替换，无同源重复累积 |
| AC-20 | 导入后 `source=MANUAL` 的手动课程完整保留 |
| AC-21 | 预览页对同名手动课程提示「疑似重复 N 门」，不自动合并 |

## 8. 测试要求（必做，用假对象，禁新依赖）

- `WakeupCsvParserTest`：正常 CSV → 课程/安排数量与字段正确；脏行跳过并进 anomalies；空内容 → `EmptyPayload`；无表头 → `SchemaMismatch`；全脏行 → `InvalidData`；周次写法兼容性（至少覆盖 `1-16`、`1-16周`、`1,3,5`、`单周`、`双周` 五种）
- `WakeupCsvImporterTest`：`import()` 成功返回 `NeedsConfirm`（不写库）；`commit()` 后走 `applyImport`（用 `FakeCourseDao` 断言落库数量与 source）；失败返回 `Failure` 且保留 `retainedPayload`
- fixture：`parser/wakeup/fixtures/sample_wakeup.csv`（正常样本）与 `sample_wakeup_dirty.csv`（含脏行样本），放 `app/src/main/resources/` 或 `app/src/test/resources/`，测试用 `javaClass.classLoader.getResourceAsStream(...)` 读取
- 复用 `app/src/test/java/com/gould/xputimetable/data/repository/Fakes.kt` 的假 DAO（**不要新建第二套**）

## 9. 已知坑（照做，别踩）

1. **Gradle 输出禁止管道**（会假死）→ 一律重定向到文件后读取
2. `rm` 在本机沙箱不可用（被 shim 拦截）→ 清理文件用 `python3 -c "import shutil; shutil.rmtree(...)"`
3. Room 3 的包是 `androidx.room3`，注解方式与普通 Room 教程不同；**本次不新增表**，若确需改 schema，先回传确认（schema JSON 已入库，属活规格）
4. 数据类构造**一律用具名参数**（位置传参会因加列静默错位）
5. 编辑文档表格时锚点要含"上一行末尾 + 新行内容"，避免覆盖（本项目已踩过两次）
6. 每周次计算/单双周判定等确定性逻辑用 `WeekCalc`（`domain/WeekCalc.kt`）的纯函数，**不要自己写第二份**

## 10. 完成后的回传要求

1. 构建与测试：命令 + `EXIT=` 码 + 通过/失败清单（含本次新增用例数）
2. 新增/修改文件清单（每个文件带行数，确认 ≤300）
3. 逐条自评 §7 的 6 条 AC（满足 / 未满足 + 证据行号或测试名）
4. 与本文档/架构冲突之处（如有），以及你做的技术判断与理由
5. 遗留风险与未验证项（**必须如实列出，不得隐藏**）
