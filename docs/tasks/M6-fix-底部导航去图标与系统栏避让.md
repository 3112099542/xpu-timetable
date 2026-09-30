# M6-fix 任务规格：底部导航去图标 + 系统栏避让修复

> 来源：产品负责人 2026-09-19 11:31 的 2 项要求（P1 修复 + 底栏去图标）。
> **本文件自包含**：未接触过本项目也能照此实现。所有「现状」均已实测核实（含模拟器截图证据），可直接对照。
> 前置：M6 已交付并通过模拟器验收（12 项 PASS + 1 项 P1）；本任务修复该 P1 并按新需求调整底栏。

---

## 0. 项目背景（30 秒）

西安工程大学课表 App（原生 Android，Kotlin + Jetpack Compose M3，开源）。无后端、无账号、数据全在本机。
本次是 M6 的小补丁，只动**底部导航**与**系统栏避让**，不涉及业务逻辑。

---

## 1. 本次涉及的 2 项（原文 → 裁决）

| # | 原话 | 类型 | 裁决 |
|---|---|---|---|
| 1 | 「p1修复」 | **缺陷修复** | 修 `navigationBarsPadding` 全局缺失（两处，见 §4） |
| 2 | 「将软件下面的"课表"和"我的"图标删掉，只保留文字」 | **UI 调整** | 底栏改为纯文字（见 §3） |

> 两项**合并到同一个改动**里做：因为「去图标」必须把 `NavigationBar` + `NavigationBarItem` 换成自定义 `Row`，
> 而这一换恰好也让 P1 的系统栏避让能用正确姿势实现（见 §3.2 的技术约束）。一次改完，不返工。

---

## 2. 现状（实测于 2026-09-19，可逐条核对）

### 2.1 底栏结构与调用点

```
ui/navigation/AppBottomBar.kt        72 行   ← NavigationBar + 两个 NavigationBarItem
ui/navigation/AppNav.kt             274 行   ← 第 267 行调用 AppBottomBar
```

`AppNav.kt` 的顶层布局（**关键**：底栏不在 Scaffold 内）：

```kotlin
// AppNav.kt:131-135 附近
Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f)) { /* 页面内容 */ }

    // AppNav.kt:265-272 —— 底栏直接放在 Column 末尾，未做任何系统栏避让
    if (screen == AppScreen.Timetable || screen == AppScreen.Profile) {
        AppBottomBar(
            current = screen,
            onOpenTimetable = { popToRoot() },
            onOpenProfile = { if (screen != AppScreen.Profile) navigateTo(AppScreen.Profile) },
        )
    }
}
```

`AppBottomBar.kt` 现有实现（**72 行全文已核实**）：

```kotlin
NavigationBar(
    modifier = modifier.fillMaxWidth().height(40.dp),        // ← 固定 40dp（M5 需求 3）
    containerColor = if (isSystemInDarkTheme()) DarkSurface.copy(alpha = 0.5f)
                     else LightPageBackground.copy(alpha = 0.5f),
    tonalElevation = 0.dp,
) {
    NavigationBarItem(
        selected = current == AppScreen.Timetable,
        onClick = onOpenTimetable,
        icon = { Icon(painterResource(AppIcons.calendarToday), contentDescription = LABEL_TIMETABLE, modifier = Modifier.size(IconSize.Medium)) },
        label = { Text(LABEL_TIMETABLE, fontSize = 10.sp) },
    )
    NavigationBarItem(
        selected = current == AppScreen.Profile,
        onClick = onOpenProfile,
        icon = { Icon(painterResource(AppIcons.user), contentDescription = LABEL_PROFILE, modifier = Modifier.size(IconSize.Medium)) },
        label = { Text(LABEL_PROFILE, fontSize = 10.sp) },
    )
}
```

### 2.2 系统栏避让现状（P1 根因）

`MainActivity.kt:25` 有 `enableEdgeToEdge()`（**无参数**）→ 应用是 edge-to-edge，**系统栏不会自动避让**。

全项目搜避让 API 的结果：

```
grep -rn "navigationBarsPadding\|systemBarsPadding\|safeDrawingPadding\|windowInsetsPadding" app/src/main/java
→ 空（0 处）
```

**为什么三处底部元素里只有两处出问题**（这一点必须理解对，否则会改错地方）：

| 位置 | 是否在 Scaffold 内 | 是否自动收到 insets | 结果 |
|---|---|---|---|
| `ImportPreviewScreen` 的 `bottomBar = {}` 槽 | ✅ 在 | ❌ **M3 的 Scaffold 不给 bottomBar 槽加 insets** | **被导航栏压住** |
| `QrShareScreen` 的按钮（在 **content 槽**内） | ✅ 在 | ✅ 通过 `padding(padding)` 收到 innerPadding | 正常（实测按钮 y=2212，导航栏从 y=2274 起） |
| `AppBottomBar`（在 `Column` 末尾，**完全不在 Scaffold 内**） | ❌ **不在** | ❌ 无任何来源 | **完全被覆盖** |

> **M3 的坑**：`Scaffold` 会把窗口 insets 通过 `innerPadding` 交给 **content 槽**，
> 但**不会**替 `bottomBar` 槽处理 —— 放进去的内容必须自己加 `navigationBarsPadding()`。

**额外的叠加原因（AppBottomBar 特有）**：`NavigationBar` 自带默认 `windowInsets`（会加底部 insets），
但外层 `.height(40.dp)` **把总高固定成 40dp**，而底部 insets 有 48dp（三按钮导航栏）→
padding 挤占超过可用高度 → **内容区被压到 0 → 底栏视觉上完全消失**。

### 2.3 真机事实（模拟器实测，环境 = Android 16 / API 36，与真机同版本）

| 导航模式 | 底栏「课表 / 我的」 | 预览页「确认导入」 |
|---|---|---|
| **三按钮** | **完全不可见**（被导航栏盖住）；点「我的」位置 → **前台变成桌面**（点击落到「最近任务」键） | 只露上沿约 1/3；点击落到导航栏 |
| **手势** | 可见可点（但图标被手势条压住下沿） | 完整可见可点 |

**证据截图**（在 `docs/verify/M6/emu/`）：
- `04_P1缺陷_三按钮导航下确认导入被导航栏压住.png` ↔ `05_对照_手势导航下确认导入完整可点.png`
- 两次复现截图内容完全一致 → **稳定缺陷，非偶发**

> ⚠️ **真机用手势导航，所以这个缺陷在真机上"看不见"**。必须用三按钮导航验证（见 §8）。

---

## 3. 需求 1：底栏去图标，只保留文字

### 3.1 改什么

`AppBottomBar` 显示的内容从「图标 + 文字」变成**只有文字**：

```
改前：   [日历图标]           [用户图标]
          课表                 我的

改后：      课表                我的
```

页面切换行为、显示时机（仅两个根页）、背景色、半透明效果**全部不变**。

### 3.2 为什么不能简单地「删掉 icon 参数」（技术约束，先看再动手）

1. **`NavigationBarItem` 的 `icon` 是必填参数**（`icon: @Composable () -> Unit`），删不掉。
   传空 lambda `icon = {}` 也不可取 —— M3 会把选中指示器（indicator pill）画在 icon 槽上方，
   图标没了 pill 就变成「一个空壳」，且内部固定布局会留下无法消除的空白，文字位置也会偏低。

2. **`.height(40.dp)` 会把 `NavigationBar` 自带的 windowInsets 压扁**（见 §2.2）——
   要修 P1 就必须把 insets 加在 height **之外**，这与 `NavigationBar` 的默认行为冲突。

**→ 结论：把 `NavigationBar` + `NavigationBarItem` 换成自定义 `Row` + 可点 `Text`。**
这一换**同时解决**「去图标」和「系统栏避让」两个需求，是最小且最干净的改动。

### 3.3 推荐实现（可直接照抄，替换 `AppBottomBar.kt` 全文）

> **上方代码的语义写法已用真实编译器实测**：`role = Role.Tab` + `selected = isSelected`
> 通过 Kotlin 2.3.21 前端检查（`androidx.compose.ui:ui` 类路径齐全，对照用例确认编译器确实在报错）。
> 参数名用 `isSelected` 而非 `selected`，是为了避免与语义属性 `selected` 同名（虽加 `this.` 也能编译，但可读性差）。

```kotlin
/*
 * AppBottomBar.kt —— 底部导航（M4-UI R7 + M5 需求 3 + M6-fix）
 *
 * 只有两项，不做日视图或其它 tab（规格 §5 明确不做）。
 * M5 需求 3：高度 40dp、背景 = 主页背景 50% 透明、tonalElevation 0、文字 10sp。
 * M6-fix ①：去掉图标，只保留文字 → NavigationBarItem 的 icon 为必填且布局固定为
 *   「icon 上 / label 下」，无法只留 label，故改为自定义 Row + 可点 Text。
 * M6-fix ②：底部必须 navigationBarsPadding() 避让系统导航栏。
 *   原实现用 NavigationBar + 外层 .height(40.dp)，会把 NavigationBar 自带的
 *   windowInsets 压扁（底部 insets 48dp > 高度 40dp → 内容区被压到 0 → 底栏消失）；
 *   且本底栏直接放在 AppNav 的 Column 末尾，不在 Scaffold 内、拿不到 innerPadding。
 *   三按钮导航下被系统导航栏完全覆盖（实测点击会落到导航栏）。详见 docs/verify/M6/。
 * 显隐由 AppNav 控制：仅在两个根页（课表 / 我的）显示；二级页保持沉浸。
 */
package com.gould.xputimetable.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gould.xputimetable.ui.theme.DarkSurface
import com.gould.xputimetable.ui.theme.LightPageBackground

private const val LABEL_TIMETABLE = "课表"
private const val LABEL_PROFILE = "我的"

/** M5 需求 3 的底栏高度（保持 40dp，不因去图标而改动既定尺寸）。 */
private val BarHeight = 40.dp

/** M6-fix：无图标后文字是唯一元素，10sp 偏小 → 提到 M3 labelMedium 的标准值 12sp。 */
private val LabelFontSize = 12.sp

@Composable
internal fun AppBottomBar(
    current: AppScreen,
    onOpenTimetable: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isSystemInDarkTheme()) {
        DarkSurface.copy(alpha = 0.5f)
    } else {
        LightPageBackground.copy(alpha = 0.5f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // M6-fix ②：必须在 height **之前**（外层）—— 否则 insets 会被 40dp 压扁。
            .navigationBarsPadding()
            .height(BarHeight)
            .background(barColor),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarLabel(
            label = LABEL_TIMETABLE,
            isSelected = current == AppScreen.Timetable,
            onClick = onOpenTimetable,
            modifier = Modifier.weight(1f),
        )
        BottomBarLabel(
            label = LABEL_PROFILE,
            isSelected = current == AppScreen.Profile,
            onClick = onOpenProfile,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 底栏的单个文字项（M6-fix：无图标版本的 tab）。
 * 触摸区 = 整块（fillMaxHeight × weight），因此 40dp 高度即触摸目标高度。
 */
@Composable
private fun BottomBarLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中态：唯一强调色 primary + 加粗；未选中：onSurfaceVariant + 常规
    // （与既有设计一致：全项目唯一强调色 = colorScheme.primary，只给关键状态）
    val color = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab           // 无障碍：告诉读屏这是 tab
                selected = isSelected     // 无障碍：暴露选中态（替代原 NavigationBarItem 的语义）
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = LabelFontSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}
```

### 3.4 设计决策（3 条，都有理由）

| 决策 | 值 | 理由 |
|---|---|---|
| **高度** | **保持 40dp** | 尊重 M5 需求 3 的既定尺寸，本次不引入未要求的设计变更。去图标后 40dp 仍能容纳 12sp 文字并居中 |
| **字号** | 10sp → **12sp** | 原来是「20dp 图标 + 10sp 文字」的平衡；图标移除后 10sp 单薄。12sp 是 M3 `labelMedium` 标准值 |
| **选中态** | primary + **SemiBold** / onSurfaceVariant + Normal | 原来靠 indicator pill（围绕图标）表达选中，图标没了 pill 无意义 → 改用「颜色 + 字重」双通道，无额外装饰 |

> **如需微调**：改 `BarHeight`（§3.3 顶部常量）与 `LabelFontSize` 两个文件级常量即可，不要散落魔法数字。

### 3.5 副作用说明（必须知道，避免误判）

- **`AppIcons.user` 将失去唯一使用者**（原仅 `AppBottomBar.kt:64` 引用）。
  **保留该常量**，在文件里加一行注释 `/** 当前无使用者；如后续页面需要用户图标可直接引用。 */`。
  **不要**删除底层 `res/drawable/lucide_ic_circle_user.xml`（资源删除涉及回归面，超出本次范围）。
- **`AppIcons.calendarToday` 仍有使用者**（`ui/timetable/components/WeekSelector.kt:118`），**不受影响**。
- **`AppNav.kt` 不需要改**：`AppBottomBar` 的函数签名不变（`current` / `onOpenTimetable` / `onOpenProfile` / `modifier`），
  且调用处本来就没传 `modifier`。

---

## 4. 需求 2：修复系统栏避让（P1）

### 4.1 两处必改（不是一处）

| # | 文件 | 位置 | 现状 |
|---|---|---|---|
| A | `ui/navigation/AppBottomBar.kt` | 整个底栏 | **完全被导航栏覆盖**（三按钮导航下不可见、不可点）→ §3.3 的重写已包含修复 |
| B | `ui/import_/ImportPreviewScreen.kt` | `bottomBar = {}` 槽内第 **89 行**的 `Row` | 按钮被压住（只露上沿 1/3）→ 本次补上 |

### 4.2 改法 B（`ImportPreviewScreen.kt`）

现状（第 88-94 行）：

```kotlin
bottomBar = {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),   // ← 只有普通 padding
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
```

改为：

```kotlin
bottomBar = {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // M6-fix：M3 的 Scaffold 不会给 bottomBar 槽加窗口 insets，必须自己避让系统导航栏
            // 顺序要求：navigationBarsPadding 在普通 padding **之前**（外层），
            // 否则底部 insets 会挤进普通 padding 的空间
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
```

并在该文件的 import 区补一行：

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
```

同时**顺手修正该文件第 6 行的注释**（原注释只提了顶栏，是这次缺陷的成因之一）：

```kotlin
// 改前
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；回调方法引用；无输入无 imePadding。
// 改后（把三条系统栏纪律写全）
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding()；底部固定栏 navigationBarsPadding()；
 *   含输入框的页面 imePadding()；回调方法引用。
```

### 4.3 `Modifier` 顺序要求（写错会静默失效）

`navigationBarsPadding()` 必须放在 `height()` / 普通 `padding()` **之前**（即外层）。
Compose 的 Modifier 链里，**先出现的在外层**：

```kotlin
// ✅ 正确：insets 加在外层，内容仍保有 BarHeight 的高度，总高 = BarHeight + insets
Modifier.fillMaxWidth().navigationBarsPadding().height(BarHeight)

// ❌ 错误：height 在外层，会把 insets 挤进 BarHeight 里 → 内容被压扁（这正是当前 AppBottomBar 的问题）
Modifier.fillMaxWidth().height(BarHeight).navigationBarsPadding()
```

---

## 5. 不需要改的地方（避免过度设计）

| 位置 | 为什么不改 |
|---|---|
| `ui/transfer/QrShareScreen.kt` | 它的按钮在 Scaffold 的 **content 槽**内，通过 `padding(padding)` 已收到 innerPadding。实测按钮 y=2212，系统导航栏从 y=2274 起 → **未被遮挡** |
| `ui/settings/SettingsScreen.kt` | 无底部固定按钮（内容整体可滚动） |
| `ui/timetable/TimetableScreen.kt` | 同上 |
| `ui/transfer/ExportSection.kt` | 是「我的」页内的一段内容，无底部固定元素 |
| `AppNav.kt` | 调用签名不变，无需改动 |
| `ui/timetable/components/DayHeader.kt` / `WeekGrid.kt` | 与本次无关 |

> **不要**「顺手」给全项目所有页面加 `navigationBarsPadding()` —— 只有**底部固定元素**需要，
> 可滚动内容的底部留白由 Scaffold 的 innerPadding 负责。多加了会导致双重留白。

---

## 6. 单测

**本次不新增单测**（诚实说明，不凑覆盖率）：

- 本次改动是 **Compose 布局与 Modifier 链**，JVM 单元测试（`app/src/test`）**没有 Compose 测试环境**，测不到
- 底栏的选中态逻辑只有一行 `if (selected) primary else onSurfaceVariant`，抽出纯函数去测**属于为覆盖率而覆盖率**
- 既有的 `AppBottomBar` 也没有测试（M4-UI/M5 时期就没加）

**替代的机械自证**（见 §7，编码 agent 必须执行）：

```bash
# 1) 全项目必须有 navigationBarsPadding（原来 0 处）
grep -rn "navigationBarsPadding" app/src/main/java --include=*.kt

# 2) AppBottomBar 不应再出现 NavigationBarItem（图标已去）
grep -n "NavigationBarItem" app/src/main/java/com/gould/xputimetable/ui/navigation/AppBottomBar.kt

# 3) 两个文件都不应再有「height 在 navigationBarsPadding 之前」的写法
grep -n "height(.*).*navigationBarsPadding\|navigationBarsPadding.*height(" app/src/main/java --include=*.kt
```

---

## 7. 文件清单

**修改**

| 文件 | 现状行数 | 改什么 |
|---|---|---|
| `ui/navigation/AppBottomBar.kt` | 72 | **重写为自定义 Row + 可点 Text**（§3.3 全文）；同时修 A 处 P1；给 `AppIcons.user` 加「当前无使用者」注释 |
| `ui/import_/ImportPreviewScreen.kt` | 157 | `bottomBar` 的 `Row` 加 `.navigationBarsPadding()`（§4.2）；补 import；修正第 6 行注释 |
| `ui/components/AppIcons.kt` | 90 | 仅给 `user` 常量加注释（可选，若嫌多余可跳过） |

**不改**：`AppNav.kt`、`QrShareScreen.kt`、`SettingsScreen.kt`、`TimetableScreen.kt`、
`gradle/libs.versions.toml`、`AndroidManifest.xml`、任何资源文件、任何 DAO / 仓库 / 业务逻辑。

**新增**：无新文件。

---

## 8. 自证步骤（实现方必须跑，逐条给结果）

```bash
export JAVA_HOME=/home/othc3/opt/jdk-21b
cd /home/othc3/WorkBuddy/安卓软件开发

# 1) 构建 + 全量单测（必须重定向到文件，禁止用管道——Gradle 守护进程会占住管道假死）
/home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/m6fix.log 2>&1
echo "EXIT=$?"
grep -E "BUILD SUCCESSFUL|BUILD FAILED|FAILED" /tmp/m6fix.log

# 2) 单测总数不得减少（M6 基线 243）
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
print('  用例 %d / failures %d / errors %d  %s' % (t,f,e,'PASS' if f+e==0 and t>=243 else 'FAIL'))
PY

# 3) 三条机械自证（§6 的三条 grep）
grep -rn "navigationBarsPadding" app/src/main/java --include=*.kt
grep -rc "NavigationBarItem" app/src/main/java/com/gould/xputimetable/ui/navigation/AppBottomBar.kt
grep -n "height(.*).*navigationBarsPadding" app/src/main/java --include=*.kt
```

**期望结果**：
1. `BUILD SUCCESSFUL`，EXIT=0
2. 用例 ≥ 243、0 失败（本次不新增测试，总数应不变）
3. 第 1 条 grep **至少 2 处命中**（`AppBottomBar.kt` + `ImportPreviewScreen.kt`）；
   第 2 条应为 **0**；第 3 条应为 **空**

---

## 9. 明确不做（防止范围蔓延）

- **不改** `AppBottomBar` 的 40dp 高度（M5 既定，本次只去图标）
- **不改** 底栏显示时机（仍仅两个根页显示，二级页沉浸）
- **不改** 底栏背景色的半透明算法（M5 需求 3）
- **不删** `AppIcons.user` 常量与底层 drawable（见 §3.5）
- **不给** 非底部固定元素加 `navigationBarsPadding()`（见 §5）
- **不新增** 底栏第三项 / 日视图 / 其它 tab（规格 §5 明确不做）
- **不引入** 新依赖、不做主题改造

---

## 10. 回传格式（按此汇报，逐条给证据）

```markdown
# M6-fix 实现回传

## 一、构建与测试
- 命令与 EXIT：
- 用例总数（应 ≥243）：failures / errors

## 二、逐项自证
- 底栏去图标：AppBottomBar.kt 是否已无 NavigationBarItem；当前实现方式
- 底栏避让：navigationBarsPadding 所在行号与 Modifier 顺序（在 height 之前？）
- 预览页避让：ImportPreviewScreen.kt 的 navigationBarsPadding 行号
- 三处 grep 的原始输出

## 三、行数门禁
- 改动后各文件行数（全部须 ≤300）

## 四、遇到的问题与自行决策
（例如：选中态的实现选择、是否保留 AppIcons.user）

## 五、明确未做（对照 §9）
```

---

## 11. 我方真机/模拟器验收（交付后执行，实现方不用做）

| # | 验收项 | 判据 |
|---|---|---|
| 1 | **三按钮导航下底栏可见** | 底栏完整显示在导航栏**上方**，不被覆盖 |
| 2 | **三按钮导航下底栏可点** | 点「我的」→ 切到「我的」页（**不是**跳到桌面/最近任务）；点「课表」→ 切回周视图 |
| 3 | **手势导航下底栏不压手势条** | 文字完整可见，不被手势条覆盖 |
| 4 | 预览页按钮可点（三按钮导航） | 「确认导入」完整可见、可点、能完成导入 |
| 5 | 底栏只显示文字 | 无图标；选中项 primary + 加粗，未选中 onSurfaceVariant |
| 6 | 半透明背景保持 | 底栏背景仍是主页背景 50%（亮 `#EEEDF3`/暗 `#111318` 的 50% 叠底） |
| 7 | M6 回归 | 文件导入 / 二维码导入 / 跨周边框 / 闹钟在册 均不受影响 |

> 验收方式：`docs/verify/M6/模拟器验收报告.md` 同款流程（命令行模拟器 + 两种导航模式各跑一遍）。

---

## 12. 契约提醒（写完自检）

- [ ] 全部改动文件 ≤ 300 行
- [ ] 数据类实例一律具名参数
- [ ] 不新增 `!!`（全项目现仅 1 处，在 `TimetableRepositoryImpl.kt:248`）
- [ ] 新增/修改 UI **无 emoji**
- [ ] **无硬编码色值**（本次全部走 `MaterialTheme.colorScheme.*` 与既有 `DarkSurface`/`LightPageBackground`）
- [ ] 尺寸/字号抽成**文件级常量**（`BarHeight` / `LabelFontSize`），不散落魔法数字
- [ ] **不要**给底栏文字加 `fontFeatureSettings = "tnum"` —— 项目里 `tnum` 只用于 `WeekGrid.kt:62`
      的时间轴**数字**列（等宽数字对齐）；「课表 / 我的」无数字，加了无效果且破坏既有惯例
- [ ] 无障碍：两个 tab 保留 `Role.Tab` + `selected` 语义，**文字即标签**（无图标后无需 contentDescription）
- [ ] `libs.versions.toml` 是唯一版本源（本次不涉及）
- [ ] `AppDatabase.version` 仍为 2、无表结构变更
- [ ] 未改 `AndroidManifest.xml`、未加任何权限
- [ ] 未改 `today_widget_info.xml`（88dp 保留，方案 A）
