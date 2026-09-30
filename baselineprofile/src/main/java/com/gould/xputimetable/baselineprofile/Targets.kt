/*
 * Targets.kt —— 采集/基准模块的共享目标常量
 *
 * 生成器（BaselineProfileGenerator）与基准（StartupBenchmarks）必须用**同一份**
 * 包名与判定文案；否则将来改一处忘一处，会出现"profile 采到了 A、基准测的是 B"这种
 * 静默失配。故抽到单独文件，作为唯一事实源。
 */
package com.gould.xputimetable.baselineprofile

/** 被测应用包名（与 app/build.gradle.kts 的 applicationId 一致）。 */
internal const val TARGET_PACKAGE = "com.gould.xputimetable"

/** 「周视图已渲染」的判定依据之一：顶栏周次标题「第 N 周」中的「第」。 */
internal const val WEEK_TITLE_HINT = "第"

/** 底部导航两项的文案（用于驱动切页路径）。 */
internal const val TAB_TIMETABLE = "课表"
internal const val TAB_PROFILE = "我的"
