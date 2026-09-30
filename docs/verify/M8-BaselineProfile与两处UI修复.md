# M8 验收报告 —— Baseline Profile · 退出提示浮层 · 「回到本周」修复

> 日期：2026-09-30｜构建：AGP 9.4.0 / Kotlin 2.3.21 / compose-bom 2026.08.00
> 单测：**237 用例 / 28 测试类 / 0 失败**（`--rerun-tasks` 强制重跑，非缓存）
> release APK：**7.69 MB**（debug 版 21.3 MB）

---

## 一、Baseline Profile（新增）

### 做了什么

| 项 | 内容 |
|---|---|
| 生成器模块 | `:baselineprofile`（`com.android.test` + `androidx.baselineprofile` 1.5.0） |
| 接线 | app 侧也应用插件 + `baselineProfile(project(":baselineprofile"))`；`automaticGenerationDuringBuild = false` |
| 采集脚本 | `BaselineProfileGenerator`：冷启动 → 等周视图内容 → 翻周 → 切底栏往返 |
| 基准脚本 | `StartupBenchmarks`：`Partial(BaselineProfileMode.Require)` vs `None()`，各 10 次迭代 |
| 生成物 | `app/src/release/generated/baselineProfiles/{baseline-prof,startup-prof}.txt`（各 18019 条规则） |

### 关键修正：必须显式开 startup profile

首次采集时插件给出警告 ——

```
No startup profile rules were generated for the variant `release`.
This is most likely because there are no instrumentation test with baseline profile
rule, which specify `includeInStartupProfile = true`.
```

**androidx.benchmark 1.5.0 起 `includeInStartupProfile` 默认为 false**，不传就丢掉启动路径的
AOT 规则，而那正是"刚打开卡"最该优化的部分。已显式传 `true`，重采后
`startup-prof.txt` 正常生成，构建日志出现 `mergeReleaseStartupProfile`。

### 验证（可复现）

| 检查 | 方法 | 结果 |
|---|---|---|
| profile 进包 | 解包 APK 查 `assets/dexopt/` | `baseline.prof = 10562 B` + `baseline.profm = 662 B`；**无 profile 的对照构建为 7093 B 占位** |
| 规则有效 | 统计 `baseline-prof.txt` 的包前缀分布 | Compose UI 5912 / runtime 2248 / foundation 1021 / animation 944 / material3 854 / **本项目 595** / coroutines 474 / datastore 461 |
| 混淆翻译 | 构建日志 | `expandReleaseArtProfileWildcards` → `compileReleaseArtProfile` → `mergeReleaseStartupProfile` 均执行，无告警 |

### ⚠️ 未达成的部分：没能证明提速幅度（诚实说明）

在模拟器上做了同代码 A/B（仅 profile 有无之差，APK 内 profile 字节数确实不同）：

| 组 | 冷启动 `am start -W TotalTime` ×5 | 中位 |
|---|---|---|
| A：含 profile | 381 / 393 / 399 / 361 / 409 ms | **393 ms** |
| B：无 profile | 369 / 377 / 411 / 382 / 383 ms | **382 ms** |

**测不出差异**。原因分析（不是"profile 没用"，是测法不够）：

1. `am start -W TotalTime` 只覆盖到首帧，抖动 ±10%，5 次样本分辨不出小效应；
2. 模拟器为 `swiftshader_indirect` 软件渲染，启动耗时被图形初始化占满；
3. profile 的收益需要 ART 在安装期实际采用 —— `adb install` 触发的 dexopt 档位与真机安装
   （带云 profile 的系统路径）不一定相同。

**下一步（真机，一条命令）**：`StartupBenchmarks` 已写好，它用 Macrobenchmark 标准做法
（同设备、只改编译模式、10 次迭代、输出 TTID 的 min/median/max）：

```sh
ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.gould.xputimetable.baselineprofile.StartupBenchmarks
```

> 因此**本项结论只能是"已接入并正确打包"，不能声称"启动快了 X%"**。

### 附带改动

- release 变体改用 **debug keystore 签名**（`signingConfigs.getByName("debug")`）：
  ① `baselineProfile` 会派生 `nonMinifiedRelease` 并按装到设备采集，未签名装不上；
  ② 与本机既有安装同签名 → 覆盖安装保留数据。
  **发布 GitHub Releases 前必须换正式 keystore**（换签名会导致无法覆盖安装、数据丢失）。
- `profileinstaller 1.4.1` 显式声明（虽已由 Compose 传递引入 1.4.0，但本功能依赖它，
  不依赖别家库的传递关系）。

---

## 二、退出提示改为现代化全局顶置浮层

### 根因

原实现把「再按一次退出应用」做成 **Snackbar，宿主挂在周视图自己的 `Scaffold`** 上
（`TimetableScreen.kt` 的 `snackbarHost = { SnackbarHost(snackbarHostState) }`）。
于是：

1. 提示的**显示位置在内容区底部**（底部导航栏之上）——层级不对；
2. `SnackbarHostState` 跨页面存活，而宿主随页面重建 → **切到「我的」再切回，
   Scaffold 重挂会把未过期的 Snackbar 重新显示一次**（用户看到的"又出现在下面"）。

### 改法

新增 `ui/components/TopHint.kt`：顶部居中、`statusBarsPadding()` 避让状态栏、
全弧度胶囊（`inverseSurface` / `inverseOnSurface`）、淡入 + 轻微下滑（`Motion.FastMillis`）、
驻留 `Hint.VisibleMillis = 1800ms` 自动消失。

**挂在 `AppNav` 内容区 Box 内**（页面 Scaffold 之外），因此页面切换既不重建宿主、
也不会重放；作为 Box 最后一个子项绘制 → 全局置顶。

计时用 nonce 模式（`LaunchedEffect(exitHintNonce)` + `delay`）：连按会重置驻留时间，
状态写入集中在一处，避免"回调里 set 状态 + 协程里再 set"的竞态。

### 验证（模拟器，像素级）

顶部 80–340px 区域「深色像素数」作为指标（胶囊出现会显著增大）：

| 步骤 | 指标 | 判定 |
|---|---|---|
| 按下返回键前 | 190 | 无提示 |
| 按下后（立即截屏） | **1312** | **提示出现**（指标灵敏，约 7× 跳变） |
| 3 秒后 | 190 | 自动消失 ✓ |
| 切到「我的」再切回「课表」 | **190** | **无重放 ✓（旧缺陷的重现场景）** |
| 连按两次（间隔 0.4s） | 前台变为 `com.android.launcher3` | 退出成功 ✓ |

截图确认视觉：深色胶囊「再按一次退出应用」居中于顶部、状态栏之下，页面内容在后方正常显示；
胶囊与「+」「回到本周」按钮**不重叠**（会压住标题行的「周三」文字与日期区间一行，属瞬时浮层的正常覆盖）。

---

## 三、「回到本周」按钮长亮修复

### 根因

`weekIsOverridden` 直接取 `override != null`，而 `weekOverride` 会被**程序化滚动回写**：

1. 点在别的周 → 点「回到本周」→ `backToCurrentWeek()` 清空覆盖 → 显示的周回到本周（如第 6 周）；
2. `WeekPager` 观察到 `state.week` 变化 → `animateScrollToPage` 程序化滚动；
3. 滚动落定后 `snapshotFlow { currentPage }` 把**当前页（=本周）回写**成 `setWeek(6)`；
4. 于是 `weekOverride` 又变成 6（非 null）→ `weekIsOverridden = true` → **按钮重新变亮且不再熄灭**。

`WeekPager` 原有 `programmaticTarget` 守卫能压低概率，但"程序化滚动结束"与
"snapshotFlow 尾帧投递"都在主线程、天然在赛跑，**加守卫只能降低概率，不能消除**。

### 改法（语义层幂等，不去补时序）

新增 `ui/timetable/WeekOverridePolicy.kt`（与 `BackPolicy` 同风格的纯函数）：

- `normalize(target, autoWeek)`：目标周 == 自动周 → 返回 `null`（"滑到本周"等价于**没有覆盖**）；
- `isOverridden(override, autoWeek)`：**显示周与本周不同**才算覆盖（兜底，即使有脏值也不长亮）。

`TimetableViewModel`：状态新增 `autoWeek`（**钳制后**的自动周），`setWeek`/`moveWeek` 写入前
统一走 `normalize`。**这样无论回写何时到达，结果都一致** —— 从根上消除竞态。

> 用"钳制后"的自动周很重要：放假期间 `WeekCalc.currentWeek` 可能返回 20（总周数 18），
> 若拿未钳制的值比较，第 18 周（末周）会被误判为覆盖。

### 验证（模拟器，像素级）

「回到本周」图标的颜色是唯一状态指示（可用 = `primary`；禁用 = `onSurfaceVariant` 38% 灰）。
采样图标中心周围最深 40 像素的均值：

| 状态 | 显示周 | 图标 RGB | 判定 |
|---|---|---|---|
| 初始 | 第 1 周（本周） | (163,165,179) | 禁用灰 ✓ |
| 滑到下一周 | **第 2 周** | **(71,93,146)** | **primary = 可用 ✓** |
| 等 3 秒 | 第 2 周 | (71,93,146) | 保持可用 ✓ |
| 点「回到本周」 | 第 1 周 | (163,165,179) | 禁用 ✓ |
| **再等 4 秒** | 第 1 周 | (163,165,179) | **依然禁用 ✓（旧缺陷恰在此刻重亮）** |

补充：`uiautomator dump` 的 `enabled` 字段**不反映 Compose 的禁用态**（实测按钮已禁用时
仍显示 `enabled="true"`）→ 这类状态必须用截图取色或 accessibility state，不能只看 dump。

---

## 四、顺带完成：容器内 git 可用（详见 `~/文档/沙箱环境修复记录/2026-09-30 容器内从零启用git/`）

容器内无系统 git 且无法编译；同时 GitHub 存在**按 IP 的间歇性拦截**。
方案：**JGit（Eclipse 官方 Java 实现）+ 本地 CONNECT 转发代理**（把 `github.com:443`
中继到可达机房 IP；只监听 127.0.0.1，纯 TCP 中继）。

- `git clone` 公开仓库实测通过（历史完整）；`git-receive-pack` 返回 401 = **push 通路已通、只差凭据**
- 本项目仓库已建立：分支 `main`，两次提交（`ec4f5fe` 初始化 / `f7e37c8` M8）
- 提交前做了隐私扫描：抓包样本（含 Cookie）与签名密钥按 `.gitignore` 排除；
  发现**教务解析测试夹具里残留真实学号**（10 位，`code` 字段），已脱敏为同形状假值并跑测试确认无破坏
- 修正 `.gitignore` 缺陷：原 `/build` 只匹配根目录，会漏掉 `app/build`（344 MB / 4341 文件）
  与 `baselineprofile/build`（178 MB）→ 改为 `build/`

---

## 五、结论与遗留

| 需求 | 状态 |
|---|---|
| 加上 Baseline Profile | ✅ 已接入、已打包；⚠️ **提速幅度未验证**（模拟器测不出，真机用 `StartupBenchmarks`） |
| 退出提示现代化 + 全局顶置 | ✅ 顶置胶囊 + 自动消失 + 切页不重放（像素级验证） |
| 「回到本周」长亮修复 | ✅ 像素级验证全流程通过（含旧缺陷的重现场景） |
| （老大追加）把 git 搞定 | ✅ 本地 git 全功能可用；push 仅差凭据 |

**遗留**
1. **真机 A/B 冷启动测量**（待手机可用）：`StartupBenchmarks` 已就绪，一条命令即可；
2. **Baseline Profile 建议在真机重采一次**：当前 profile 采自模拟器（API 36 与真机同版本，
   但热点分布与真机不同）。流程已固化，重采后 `baseline-prof.txt` 会更新；
3. **发布前换正式 keystore**（现用 debug 密钥签名 release）；
4. 测试夹具里的**教师姓名**未脱敏（半公开信息，但与被抓学生的课表绑定）—— 待老大裁决是否处理。
