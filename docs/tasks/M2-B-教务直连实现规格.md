# 实现规格 M2-B：教务直连通道（WebView 授权 + 拦截解析）

> 面向执行编码的 agent。**本文件自包含**：接口、字段映射、SQL、验收标准、验证命令都在下面。
> 权威来源：`docs/05-Spec-规格契约.md`、`docs/03-技术架构文档.md`、`docs/decisions/ADR-*.md`；冲突时以它们为准并回传指出。
> 前序：`docs/tasks/M2-A-文件导入实现规格.md`（已完成，113 测试全过）。本次在其基础上加**第三条通道**。

## 0. 环境与现状（只读）

- 项目：`/home/othc3/WorkBuddy/安卓软件开发`，包名 `com.gould.xputimetable`
- 构建（**禁管道**，一律重定向到文件）：
  ```
  export JAVA_HOME=/home/othc3/opt/jdk-21b
  cd /home/othc3/WorkBuddy/安卓软件开发
  /home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/build.log 2>&1
  echo "EXIT=$?" >> /tmp/build.log
  ```
- 测试基线：**113 个全过**（含 `Fakes.kt` 手造假 DAO；**禁引入 Robolectric/MockK**）
- 已有：Room 3 五表、`TimetableRepository.applyImport`（按来源替换 + MANUAL 保护 + 审计）、周视图、手动编辑、**文件导入通道（M2-A）**、启动预置西工程大作息
- 已脱敏抓包金样本：`app/src/test/resources/parser/xpu/fixtures/sample_schedule.json`（真实姓名/学号已替换为占位符，**禁止再引入真实个人信息**）

## 1. 抓包分析结论（2026-09-17 实测，以下均为事实，不要再猜）

### 1.1 课表接口（唯一需要拦截的请求）

```
GET https://jwglxt.xpu.edu.cn/student/for-std/course-table/semester/{semesterId}/print-data?semesterId={semesterId}&hasExperiment=true
```
- 请求头特征：`X-Requested-With: XMLHttpRequest`，`Referer: https://jwglxt.xpu.edu.cn/student/for-std/course-table`
- 响应：`application/json`，约 200KB
- **鉴权只靠 Cookie**（名字为 `SESSION`、`__pstsid__`），**没有**自定义 token / Authorization 头
- 课表页（人看的页面）：`https://jwglxt.xpu.edu.cn/student/for-std/course-table`
- 这是强智系教务系统（前端资源路径 `/student/static/eams-ui/...`）

> **结论**：架构 §5.1「策略 A（拦截 XHR/JSON）只对 GET 场景有效」在这里**完全成立** → 走策略 A，策略 B（DOM 注入）本次不做。

### 1.2 登录链路（实测）

- 一网通办入口 `https://sz.xpu.edu.cn/auth2/login`，登录成功后走 OAuth：`/auth2/oauth/authorize?...client_id=...` → 302 → `sz.xpu.edu.cn?code=...`
- 教务系统打开在**独立标签页**（`jwglxt.xpu.edu.cn`），用户在该页时已带 `SESSION` Cookie
- **App 的做法**：WebView 起始页设为课表页 URL；若未登录，WebView 会自行跳转登录页，**由用户在 WebView 内完成登录（含滑块）**，App 对凭据零接触零存储

### 1.3 响应结构与字段映射

顶层：`{ "studentTableVms": [ { 学生信息..., "activities": [ ... ] } ] }`；课表数据在 **`studentTableVms[0].activities[]`**（本次 16 条 = 16 条上课安排）。

| 强智字段 | 含义 | 映射到 |
|----------|------|--------|
| `courseName` | 课程名（如「大学体育Ⅲ」） | `Course.name` |
| `courseCode` | 课程编号（如 `U51G111003`） | `Course.code` + **分组键** |
| `teachers` | 教师数组（如 `["王婷（R）"]`） | `Course.teacher`（多教师用「、」连接；空数组 → null） |
| `room` | 教室（如 `A-424语音室`、`南环田径场（临潼）`） | `CourseSession.classroom` |
| `weekday` | 星期几，**1=周一** | `CourseSession.dayOfWeek` |
| `startUnit` / `endUnit` | 起止节次（如 3 / 4） | `CourseSession.startSection` / `endSection` |
| `weekIndexes` | 周次数组（如 `[4,5,...,18]`；**可能乱序**） | 见 §1.4 |
| `weeksStr` | 周次原文（如 `4~18`、`2,6,10,14`、`1~3(单),4~5,...`） | 仅用于异常提示与日志 |
| `startTime`/`endTime` | 该次课的起止时间（如 `10:10`、`12:00`，**无前导零**） | 可用于校准作息，**不入库** |
| `lessonId` | 教学班 id | 不入库（仅日志） |
| `campus`/`building` | 校区/楼（`临潼校区` / `D楼`） | 不入库（教室已有完整信息） |

**分组规则**：同一 `courseCode` 的多条 activity = **同一门课的多条安排**（实测：`电工学（A）` = 周一 5-6 节 + 周三 3-4 节）。→ 9 门课 / 16 条安排。

**id 生成（幂等导入的关键，必须稳定）**：
- `Course.id = UUID.nameUUIDFromBytes("xpu:$semesterId:$courseCode".toByteArray()).toString()`
  （稳定 ⇒ 重复导入是"同 id 覆盖"，且用户手动编辑过的课会被 `applyImport` 的同 id 防线保护，符合 AC-22）
- `CourseSession.id = 0L`（自增；旧安排由 `applyImport` 删课程时级联清理）

**colorTag 规则**：把该次导入的课程按 `courseCode` 升序排列后 `index % 12`（同课同色、跨课程颜色分散、结果稳定可复现）。

### 1.4 ⚠️ 周次问题（本次实测发现，**必须改模型**）

实测 16 条里有 **2 条用「区间 + 单双周」表达不了**：

| 课程 | `weeksStr` | `weekIndexes`（已排序） |
|------|-----------|------------------------|
| 大学英语Ⅲ | `2,6,10,14` | `[2,6,10,14]`（纯偶但每隔 4 周 → EVEN+2..14 会**多显示** 4/8/12 周） |
| 大学英语Ⅲ | `1~3(单),4~5,7~9,11~13,15~16` | `[1,3,4,5,7,8,9,11,12,13,15,16]`（非连续非单双） |

另 14 条可用「区间 + ALL」精确表达（本样本没有纯 ODD/EVEN 案例，但逻辑上保留该能力）。

**表示规则（必须实现为纯函数并单测）**：
```
给定 weeks = sorted(set(weekIndexes))：
  s = min, e = max
  若 weeks == [s..e]                        → startWeek=s, endWeek=e, weekType=ALL,  weekList=null
  否则若 weeks == [s..e] 中所有奇数           → startWeek=s, endWeek=e, weekType=ODD,  weekList=null
  否则若 weeks == [s..e] 中所有偶数           → startWeek=s, endWeek=e, weekType=EVEN, weekList=null
  否则                                      → startWeek=s, endWeek=e, weekType=ALL,  weekList=weeks
```
即：**只有当区间+单双周无法精确表达时，才填 `weekList`**（保证绝大多数课程仍走老路径、无额外负担）。
**注意**：`weekIndexes` 在真实响应里**可能乱序**（实测 `[16,17,18,11,...]`），必须先排序去重再判断——这是本项目新踩过的坑，务必写成注释。

### 1.5 作息交叉验证（已完成，结论可信）

用真实 `startTime/endTime` 校验我们预置的西工程大作息：第 1-2 节 `8:00-9:50`、第 3-4 节 `10:10-12:00`、第 5-6 节 `14:00-15:50` → **3/3 完全吻合**，说明 `data/db/DefaultTimeSlots.kt` 的取值正确，本次**不改**它。

### 1.6 金样本黄金期望值（测试硬断言用，已核算）

```
sample_schedule.json 解析结果：
  activities（安排）数 = 16
  唯一课程数（按 courseCode 分组） = 9
  weekList 非空的安排 = 2（都属「大学英语Ⅲ」：2,6,10,14 与 1,3,4,5,7,8,9,11,12,13,15,16）
  其余 14 条 weekList = null 且 weekType = ALL
```
> **`scheduleText.dateTimeText.textZh`** 字段（如 `1~10周 星期二 1~2节; ...`）是现成的人话摘要，**建议用于预览页展示**，并与解析结果交叉校验。

## 2. 前置任务 P0-A：周次模型扩展（`week_list`）

**为什么**：§1.4 的 2/16 条数据现有模型装不下。可选方案：① 降级成 min~max（会**多显示**没课的周，学生看到"这周有课其实没课"，属可见缺陷）② 扩展模型存显式周次列表。**本项目采用②**（数据里已给出精确周次，不该丢）。

改动清单：
1. `data/db/entity/CourseSessionEntity.kt`：新增
   ```kotlin
   /** 显式周次列表（CSV，升序），非空时以它为准；为空时按 start_week..end_week + week_type 判定 */
   @ColumnInfo(name = "week_list") val weekList: String?,
   ```
2. `data/db/dao/CourseSessionDao.kt`：周视图查询改为**两支 OR**（SQL 原文，照抄）：
   ```sql
   SELECT s.*, c.name AS course_name, c.color_tag AS color_tag
   FROM course_sessions s
   INNER JOIN courses c ON c.id = s.course_id
   WHERE c.term_id = :termId
     AND (
       (s.week_list IS NULL
         AND s.start_week <= :week AND s.end_week >= :week
         AND (UPPER(s.week_type) = 'ALL'
              OR (UPPER(s.week_type) = 'ODD'  AND :week % 2 = 1)
              OR (UPPER(s.week_type) = 'EVEN' AND :week % 2 = 0)))
       OR
       (s.week_list IS NOT NULL AND (',' || s.week_list || ',') LIKE ('%,' || :week || ',%'))
     )
   ORDER BY s.day_of_week ASC, s.start_section ASC
   ```
3. `domain/model/CourseSession.kt`：新增 `val weeks: List<Int>? = null`（**带默认值**，避免既有调用点全改）
4. `data/repository/Mappers.kt`：`List<Int>` ⇄ CSV 互转（升序、去重；空列表 → null）
5. `domain/WeekCalc.kt`：新增纯函数
   `fun isSessionActive(weeks: List<Int>?, weekType: WeekType, startWeek: Int, endWeek: Int, week: Int): Boolean`
   → `weeks != null` 时只按列表判定；否则沿用旧逻辑（旧函数保留，供测试与兼容）
6. `ui/courseedit/CourseEditViewModel.kt`：手动编辑保存时**清空 `weeks`**（用户改动周次 → 回到区间语义），并在编辑页对这类课程显示提示「该课周次由教务给出精确列表，手动修改后将改为区段设置」（一行文字即可，别做复杂 UI）
7. schema：`app/schemas/.../1.json` 为导出产物，**改完重新构建即自动更新**（MVP 期允许破坏性迁移，已在 `AppContainer` 兜底）

## 3. 前置任务 P0-B：接入 kotlinx-serialization（解析 JSON 必需）

现状：`gradle/libs.versions.toml` 里 `kotlinx-serialization-json` 已声明但**未接线**；App 依赖里没有它，也没有序列化插件。
要求：
1. `app/build.gradle.kts` 加 `implementation(libs.kotlinx.serialization.json)`，并在 plugins 块加 `alias(libs.plugins.kotlin.serialization)`
2. **不要** apply `org.jetbrains.kotlin.android`（AGP 9 自带 Kotlin，踩过坑）
3. 配置 `Json { ignoreUnknownKeys = true }`（学校接口随时可能加字段，必须容忍）
4. 若插件应用失败 → **立即停下回传错误原文**，不要自己手写 JSON 解析器，也不要改用 `org.json`（它在 JVM 单测不可用）
5. 完成后确认 113 个既有测试仍全过

## 4. 主任务：教务直连通道

### 4.1 `parser/xpu/XpuEndpoints.kt`（内容直接用，别改）

```kotlin
object XpuEndpoints {
    const val HOST_JWGLXT = "jwglxt.xpu.edu.cn"
    const val HOST_PORTAL = "sz.xpu.edu.cn"           // 一网通办（登录入口，备用/提示）
    const val COURSE_TABLE_PAGE = "https://jwglxt.xpu.edu.cn/student/for-std/course-table"
    /** 课表数据接口（GET，鉴权仅靠 Cookie）：semester/{id}/print-data */
    val PRINT_DATA_REGEX = Regex(
        """https://jwglxt\.xpu\.edu\.cn/student/for-std/course-table/semester/(\d+)/print-data.*"""
    )
    fun semesterIdOf(url: String): String? = PRINT_DATA_REGEX.find(url)?.groupValues?.get(1)
}
```

### 4.2 `parser/xpu/dto/XpuScheduleDto.kt`
按 §1.3 建模：`XpuScheduleResponse(studentTableVms: List<XpuStudentTableVm>)` → `activities: List<XpuActivity>`；字段用 `@SerialName` 对齐；所有可能缺失的字段给默认值或可空（**防御式**：缺失不能让整次导入崩）。`weekIndexes` 用 `List<Int> = emptyList()`。

### 4.3 `parser/xpu/XpuJsonParser.kt`
实现 M2-A 已建的 `PayloadParser<String>`：输入是响应体字符串，输出 `ScheduleParseResult`。
- JSON 解析失败 → `Failure(SchemaMismatch("..."))`
- `studentTableVms` 空 → `Failure(InvalidData("响应中没有学生课表数据"))`
- `activities` 空 → `Failure(InvalidData("本学期没有课程安排"))`
- 单条 activity 缺必要字段（课程名/星期/节次）→ 跳过并记 `anomalies`（**不算整次失败**）
- 产出：9 门课 / 16 条安排 / 2 条 weekList 非空（§1.6 黄金值）

### 4.4 WebView 拦截（策略 A）
- 新建 `ui/web/CaptureWebViewClient.kt`（`WebViewClient` 子类）：在 `shouldInterceptRequest(view, request)` 中
  1. URL 命中 `XpuEndpoints.PRINT_DATA_REGEX` 时：
     - 用 `java.net.HttpURLConnection` 代发该 GET（**不引入 OkHttp**：目录里 okhttp 未接线，保持零新依赖）
     - 带上 `Cookie`（`CookieManager.getInstance().getCookie(url)`）、`X-Requested-With: XMLHttpRequest`、`Referer`（见 §1.1）
     - 读响应体字符串 → 【副本1】包装成 `WebResourceResponse("application/json", "utf-8", stream)` 返回给 WebView（页面正常渲染，用户无感知）；【副本2】通过回调交给封装层
  2. 其他请求返回 `null`（交给 WebView 默认处理）
- ⚠️ `shouldInterceptRequest` 在**后台线程**被调用：这里可以做同步网络请求，但**不要**碰任何 View / 主线程 UI
- 失败兜底：代发失败时 `return null`（让 WebView 自己发），并记录错误 → 触发「解析失败/网络不通」提示（AC-11 / AC-12）
- 页面：`ui/web/CourseCaptureScreen.kt`（`AndroidView` 包 `WebView`）+ `CourseCaptureViewModel.kt`（状态机：加载中 → 已登录未命中 → 命中解析中 → 成功跳预览 / 失败给提示）；WebView 需 `javaScriptEnabled = true`、`domStorageEnabled = true`，**不得**注入任何 JS 去读凭据

### 4.5 接线与预览
- `ui/import/ImportHubScreen.kt`：把「教务直连」从置灰改为可用 → 进入 `CourseCaptureScreen`
- **预览页泛化**：`ImportPreviewViewModel` 当前按 `WAKEUP_CSV` 写死查询同来源课程数 → 改为**接收 `source` 参数**（WEB / WAKEUP_CSV），使覆盖范围与疑似重复对两条通道都成立
- 确认导入 → `importer.commit(parsed)` 走既有 `applyImport` **同一入口**（不得另写入库逻辑）

## 5. 验收标准（逐条自评，附证据）

| AC | 本次要求 |
|----|---------|
| AC-09 | 教务直连在内置 WebView 加载 jwglxt 课表页；**不读取/不存储/不上传任何凭据**（代码里不得出现读取密码框、不得写 Cookie 到磁盘/日志） |
| AC-10 | 拦截解析成功后**先展示预览**（课程数/条数/异常条目），用户确认后才写入 |
| AC-11 | 解析失败提示「解析失败：教务系统可能已改版」并引导兜底通道；不闪退；**不丢失已拦截数据**（保留原始响应体字符串供重试/上报） |
| AC-12 | 网络不可达提示「无法连接学校服务器，请在校园网环境下重试或使用文件导入」 |
| AC-13 | 预览页明示覆盖范围（将替换 N 门 WEB 来源课程 / 手动课程不会被删除），确认后按来源整体替换 |
| AC-20 | 导入不得删除 `source=MANUAL` 课程（复用既有 `applyImport` 双防线） |
| AC-21 | 同名手动课程提示「疑似重复 N 门」，不自动合并 |
| AC-22 | 用户手动编辑过的导入课程（同 id）在下次导入时保留用户版本 |

## 6. 测试要求

- `XpuWeekRepresentationTest`（P0-A 的纯函数）：区间/单双周/显式列表三类判定，含**乱序 weekIndexes**、空列表、重复周次
- `CourseSessionDaoWeekFilterTest`（可选，用 `room-sql-fixture-verify` 思路：用导出的 schema DDL + sqlite3 造数据验证 OR 分支；不引依赖）
- `XpuJsonParserTest`：用 `sample_schedule.json` 做 **golden 断言**（9 门课 / 16 条安排 / 2 条 weekList 非空 / 第 1 条安排的星期与节次与样本一致）；空 `studentTableVms` → `InvalidData`；非法 JSON → `SchemaMismatch`；缺字段的 activity 被跳过并记 anomalies
- `XpuWebImporterTest`：`import()` 只解析不写库；`commit()` 后走 `applyImport`（假 DAO 断言 `courses` 9 行、`course_sessions` 16 行、source=WEB）
- 既有 113 测试必须全绿

## 7. 已知坑（照做）

1. `weekIndexes` **可能乱序** → 先 `sorted().distinct()`（本次实测踩到）
2. `startTime` 是 `8:00` **无前导零**，任何时间解析都要容错
3. `semesterId` 在 URL 里（本次为 147，**会变**）→ 必须正则提取，**不得硬编码**
4. `shouldInterceptRequest` 在后台线程；**禁止**在其中触碰 View
5. Room 3 注解包名 `androidx.room3`；加列后 schema 由构建自动导出，无需写迁移（MVP 期）
6. 数据类构造一律**具名参数**；Gradle 输出**禁管道**；`rm` 不可用（用 python 删）
7. 改文档表格时锚点含"上一行末尾 + 新行"（本项目踩过两次覆盖事故）
8. 若开启 R8/minify，`@Serializable` DTO 需 keep 规则（现状 `release` 已开 minify，注意验证 release 构建能正常解析——**至少跑通 `assembleRelease`**）

## 8. 隐私红线（违反即不合格）

- 凭据零读取、零存储、零上传；不 dump Cookie 到日志；不把 Cookie 写入文件
- 抓包样本只允许**脱敏后**版本进入 `app/src/test/resources/`（真实姓名/学号一律替换）
- 不得把登录页 HTML、Cookie、学号写入任何提交物

## 9. 回传要求

1. 构建/测试：命令 + `EXIT=` 码 + 通过/失败清单（含新增用例数）
2. 文件清单（新增/修改，每个 ≤300 行）
3. 逐条自评 §5 的 9 条 AC（满足/未满足 + 证据：测试名或行号）
4. P0-A/P0-B 两项前置的完成证据（schema 新列截图或 JSON 片段；序列化库版本与插件名）
5. 与本文档/架构冲突处 + 你的技术判断理由
6. **未验证项与遗留风险如实列出**（例如：真机 WebView 登录态无法在本环境验证的部分）
