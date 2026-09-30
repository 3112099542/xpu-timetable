/*
 * BaselineProfileGenerator.kt —— Baseline Profile 采集脚本（M7）
 *
 * 采什么：只采"真实用户会走的路径"，采得越贴切 profile 越有用：
 *   ① 冷启动（Application 初始化、Room 建库与首查询、Compose 首帧、主题装配）
 *   ② 周视图首屏之后的异步内容（课程卡片进入组合 —— 首帧之后才到，必须等）
 *   ③ 翻周（Compose Pager + WeekGrid 的分组 / 冲突布局 / 绘制）
 *   ④ 底栏切「我的」再切回（设置页与导航层）
 *
 * 为什么必须"等"：BaselineProfileRule 只记录**实际执行到**的方法。Room 查询与课程卡片
 * 都落在首帧之后异步完成，若 startActivityAndWait() 后立刻结束，最值得优化的卡片布局
 * 路径根本采不到，profile 也就白生成了。
 *
 * 找不到元素时不抛异常（findObject 返回 null，用 ?. 短路）——采集宁可少采几段，
 * 也不能因为界面文案变化而让整个生成任务失败。
 *
 * 关于 @LargeTest：**刻意不加**。它只是 JUnit 的分类注解（供 -e annotation 过滤用），
 * 对采集没有任何影响；而它所在的 androidx.test:runner 只以 runtime 形式传递进来，
 * 加了反而要为此显式声明一个用不到的编译期依赖（AGP 的基线采集任务会运行本模块的
 * 全部测试，不做注解过滤）。
 */
package com.gould.xputimetable.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 被测应用包名（与 app/build.gradle.kts 的 applicationId 一致）。 */
private const val TARGET_PACKAGE = "com.gould.xputimetable"

/** "周视图已渲染"的判定依据：顶栏周次标题「第 N 周」。 */
private const val WEEK_TITLE_HINT = "第"

/** 底部导航两项的文案。 */
private const val TAB_TIMETABLE = "课表"
private const val TAB_PROFILE = "我的"

private const val WAIT_MILLIS = 5_000L
private const val SWIPE_STEPS = 24

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = TARGET_PACKAGE, maxIterations = 8) {
            pressHome()
            startActivityAndWait()
            awaitTimetableContent()
            swipeWeeks()
            visitProfileAndBack()
        }
    }

    /** 等周视图把异步内容画出来（Room 查询完成 → 课程卡片进入组合）。 */
    private fun MacrobenchmarkScope.awaitTimetableContent() {
        device.wait(Until.hasObject(By.textContains(WEEK_TITLE_HINT)), WAIT_MILLIS)
        device.waitForIdle()
    }

    /** 翻周：来回各一次，覆盖"目标页组合 + 邻页预组合"这条滑动主路径。 */
    private fun MacrobenchmarkScope.swipeWeeks() {
        val width = device.displayWidth
        val centerY = device.displayHeight / 2
        device.swipe(width * 3 / 4, centerY, width / 4, centerY, SWIPE_STEPS)
        device.waitForIdle()
        device.swipe(width / 4, centerY, width * 3 / 4, centerY, SWIPE_STEPS)
        device.waitForIdle()
    }

    /** 切到「我的」再切回：覆盖底栏导航与设置页（返回用底栏，不碰系统返回键）。 */
    private fun MacrobenchmarkScope.visitProfileAndBack() {
        device.findObject(By.text(TAB_PROFILE))?.click()
        device.waitForIdle()
        device.findObject(By.text(TAB_TIMETABLE))?.click()
        device.waitForIdle()
    }
}
