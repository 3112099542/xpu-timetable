/*
 * WidgetHeaderTextTest.kt —— 小组件头部文案的降级规则单测（M10）
 *
 * 背景：小组件在窄宽度下头部会被截断（3 格时「10.2 第 1 周 周五」放不下，与校名挤在一起）。
 * 产品负责人的裁决是「缩放时可以改内容来解决截断，优先不显示第几周和日期」
 * —— 即按宽度砍内容，而不是缩字号（M5 需求 7/8 要求右上与校名同字号）。
 *
 * 本测试锁两件事：① 阈值分档；② 砍内容的优先级（日期 → 周次 → 周几）。
 * 都是纯函数，不依赖 Android 运行时。
 */
package com.gould.xputimetable.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetHeaderTextTest {

    // ---------- ① 阈值分档（格宽基准：1 格 ≈ 84dp） ----------

    @Test
    fun `四格及以上显示全量`() {
        assertEquals(HeaderDetail.FULL, headerDetailFor(336.dp)) // 4 格
        assertEquals(HeaderDetail.FULL, headerDetailFor(280.dp)) // 阈值下沿
    }

    @Test
    fun `三格档砍掉日期但保留周次`() {
        assertEquals(HeaderDetail.NO_DATE, headerDetailFor(252.dp)) // 3 格
        assertEquals(HeaderDetail.NO_DATE, headerDetailFor(279.dp)) // 全量阈值之下
        assertEquals(HeaderDetail.NO_DATE, headerDetailFor(200.dp)) // 本档下沿
    }

    @Test
    fun `模拟器实测的110dp（声明最小宽）走保守档`() {
        // AOSP Launcher3 上报的 LocalSize 就是 minResizeWidth(110dp)；
        // 该值下「校名 + 周几」才放得下，故必须是 WEEKDAY_ONLY（宁少不截断）。
        assertEquals(HeaderDetail.WEEKDAY_ONLY, headerDetailFor(110.09524f.dp))
    }

    @Test
    fun `两格及更窄只保留周几`() {
        assertEquals(HeaderDetail.WEEKDAY_ONLY, headerDetailFor(168.dp)) // 2 格
        assertEquals(HeaderDetail.WEEKDAY_ONLY, headerDetailFor(199.dp)) // 本档阈值之下
        assertEquals(HeaderDetail.WEEKDAY_ONLY, headerDetailFor(84.dp)) // 1 格
    }

    // ---------- ② 砍内容的优先级：日期 → 周次 → 周几 ----------

    @Test
    fun `全量档输出日期加周次加周几`() {
        assertEquals(
            "10.2  第 1 周  周五",
            headerRightText("10.2", weekNumber = 1, weekdayText = "周五", detail = HeaderDetail.FULL),
        )
    }

    @Test
    fun `三格档先砍日期`() {
        assertEquals(
            "第 1 周  周五",
            headerRightText("10.2", weekNumber = 1, weekdayText = "周五", detail = HeaderDetail.NO_DATE),
        )
    }

    @Test
    fun `最窄档只留周几`() {
        assertEquals(
            "周五",
            headerRightText("10.2", weekNumber = 1, weekdayText = "周五", detail = HeaderDetail.WEEKDAY_ONLY),
        )
    }

    @Test
    fun `假期越界时没有周次但仍显示日期与周几`() {
        // currentWeek 为 null（开学前/学期结束后）→ 不显示周次
        assertEquals(
            "10.2  周五",
            headerRightText("10.2", weekNumber = null, weekdayText = "周五", detail = HeaderDetail.FULL),
        )
    }

    @Test
    fun `无学期时不显示周几只显示日期`() {
        assertEquals(
            "10.2",
            headerRightText("10.2", weekNumber = null, weekdayText = null, detail = HeaderDetail.FULL),
        )
    }

    @Test
    fun `无学期且最窄档时输出空串（不留下多余分隔符）`() {
        assertEquals(
            "",
            headerRightText("10.2", weekNumber = null, weekdayText = null, detail = HeaderDetail.WEEKDAY_ONLY),
        )
    }
}
