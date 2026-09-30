/*
 * TodayPlanTest.kt —— 今日课程判定纯函数单测（M4-W 新语义）
 *
 * 列表 = 未结束课程（nowMinute < endMinute 严格小于，== 视为已结束）；
 * 空态收敛两类：NO_TERM / ALL_DONE_OR_NONE（今日无课与全部上完统一）。
 * 规格 §3.3 的 9 个必备场景逐例给出实际返回值 + 补充边界。
 */
package com.gould.xputimetable.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPlanTest {

    /** 构造一门课（节次/分钟显式给出，测试不依赖 TimeSlot 种子数据）。 */
    private fun item(
        startSection: Int,
        endSection: Int,
        name: String = "课$startSection",
        classroom: String? = null,
        startMinute: Int = startSection * 100,
        endMinute: Int = endSection * 100 + 40,
        colorTag: Int = 0,
    ) = TodayItem(
        startSection = startSection,
        endSection = endSection,
        courseName = name,
        classroom = classroom,
        startMinute = startMinute,
        endMinute = endMinute,
        colorTag = colorTag,
    )

    // ---------- 规格 §3.3 表 9 例 ----------

    @Test
    fun `1_今日3门7点30_未结束3门升序且总数为3`() {
        val day = listOf(
            item(5, 6, name = "下午课", startMinute = 840, endMinute = 940),   // 14:00-15:40
            item(1, 2, name = "早课", startMinute = 480, endMinute = 580),     // 8:00-9:40
            item(3, 4, name = "午前课", startMinute = 600, endMinute = 700),   // 10:00-11:40
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 450) // 7:30
        assertEquals(listOf("早课", "午前课", "下午课"), plan.remaining.map { it.courseName })
        assertEquals(3, plan.remainingTotal)
        assertEquals("早课", plan.remaining.first().courseName) // 首门即第 1 项
        assertEquals(EmptyReason.NONE, plan.emptyReason)
    }

    @Test
    fun `2_第1门进行中_仍在remaining首位`() {
        val day = listOf(
            item(1, 2, startMinute = 480, endMinute = 590), // 8:00-9:50
            item(3, 4, startMinute = 600, endMinute = 700),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 510) // 8:30 正在上
        assertEquals("课1", plan.remaining.first().courseName)
        assertEquals(2, plan.remainingTotal)
    }

    @Test
    fun `3_第1门已结束_该门不进列表`() {
        val day = listOf(
            item(1, 2, startMinute = 480, endMinute = 590), // 8:00-9:50，结束于 590
            item(3, 4, startMinute = 600, endMinute = 700),
            item(5, 6, startMinute = 840, endMinute = 940),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 600) // 10:00
        assertEquals(listOf("课3", "课5"), plan.remaining.map { it.courseName })
        assertEquals(2, plan.remainingTotal)
    }

    @Test
    fun `4_今日2门全部已结束_ALL_DONE_OR_NONE`() {
        val day = listOf(
            item(1, 2, startMinute = 480, endMinute = 580),
            item(3, 4, startMinute = 600, endMinute = 700),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 1260) // 21:00
        assertTrue(plan.remaining.isEmpty())
        assertEquals(0, plan.remainingTotal)
        assertEquals(EmptyReason.ALL_DONE_OR_NONE, plan.emptyReason)
    }

    @Test
    fun `5_今日无课_ALL_DONE_OR_NONE`() {
        val plan = TodayPlanBuilder.build(emptyList(), nowMinute = 600)
        assertTrue(plan.remaining.isEmpty())
        assertEquals(0, plan.remainingTotal)
        assertEquals(0, plan.overflowCount)
        assertEquals(EmptyReason.ALL_DONE_OR_NONE, plan.emptyReason)
    }

    @Test
    fun `6_无激活学期_NO_TERM且优先级最高`() {
        val plan = TodayPlanBuilder.build(emptyList(), nowMinute = 600, hasTerm = false)
        assertEquals(EmptyReason.NO_TERM, plan.emptyReason)
        assertTrue(plan.remaining.isEmpty())
    }

    @Test
    fun `7_6门凌晨0点_remaining取前3且总数为6`() {
        val day = (1..6).map { item(it, it, startMinute = 400 + it * 60, endMinute = 440 + it * 60) }
        val plan = TodayPlanBuilder.build(day, nowMinute = 0)
        assertEquals(TodayPlanBuilder.MAX_ITEMS, plan.remaining.size)
        assertEquals(6, plan.remainingTotal)
        assertEquals(3, plan.overflowCount)
    }

    @Test
    fun `8_连堂3到4节_作为一个item且时间取首节起末节止`() {
        val one = item(3, 4, name = "连堂课", startMinute = 600, endMinute = 740)
        val plan = TodayPlanBuilder.build(listOf(one), nowMinute = 500)
        val shown = plan.remaining.single()
        assertEquals(3, shown.startSection)
        assertEquals(4, shown.endSection)
        assertEquals(600, shown.startMinute)
        assertEquals(740, shown.endMinute)
        assertEquals(1, plan.remainingTotal)
    }

    @Test
    fun `9_边界nowMinute等于endMinute_严格小于视为已结束不进列表`() {
        val day = listOf(
            item(1, 2, startMinute = 480, endMinute = 580),
            item(3, 4, startMinute = 600, endMinute = 700),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 580) // == 第 1 门 endMinute
        assertEquals(listOf("课3"), plan.remaining.map { it.courseName })
    }

    // ---------- 补充边界 ----------

    @Test
    fun `恰好差一分钟结束_仍在列表`() {
        val day = listOf(item(1, 2, startMinute = 480, endMinute = 580))
        val plan = TodayPlanBuilder.build(day, nowMinute = 579)
        assertEquals(listOf("课1"), plan.remaining.map { it.courseName })
        assertEquals(1, plan.remainingTotal)
    }

    @Test
    fun `乱序输入_输出始终按开始时间升序`() {
        val day = listOf(
            item(5, 6, startMinute = 840, endMinute = 940),
            item(1, 2, startMinute = 480, endMinute = 580),
            item(7, 8, startMinute = 1140, endMinute = 1240),
            item(3, 4, startMinute = 600, endMinute = 700),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 0)
        assertEquals(listOf(480, 600, 840), plan.remaining.map { it.startMinute })
    }

    @Test
    fun `恰好4门未结束_展示3门折叠1门`() {
        val day = (1..4).map { item(it, it, startMinute = 400 + it * 60, endMinute = 440 + it * 60) }
        val plan = TodayPlanBuilder.build(day, nowMinute = 0)
        assertEquals(3, plan.remaining.size)
        assertEquals(4, plan.remainingTotal)
        assertEquals(1, plan.overflowCount)
    }

    @Test
    fun `同一起始时间_按节次次序稳定排序`() {
        val day = listOf(
            item(3, 4, startMinute = 600, endMinute = 700),
            item(1, 2, startMinute = 600, endMinute = 700),
        )
        val plan = TodayPlanBuilder.build(day, nowMinute = 500)
        assertEquals(listOf(1, 3), plan.remaining.map { it.startSection })
    }

    @Test
    fun `无学期时即使传入课程也不展示`() {
        val day = listOf(item(1, 2, startMinute = 480, endMinute = 580))
        val plan = TodayPlanBuilder.build(day, nowMinute = 400, hasTerm = false)
        assertTrue(plan.remaining.isEmpty())
        assertEquals(EmptyReason.NO_TERM, plan.emptyReason)
    }

    @Test
    fun `已结束课程不挤占折叠额度_6门中2门结束`() {
        val day = (1..6).map { n ->
            item(n, n, startMinute = 400 + n * 60, endMinute = 440 + n * 60)
        }
        val plan = TodayPlanBuilder.build(day, nowMinute = 580) // 第 1、2 门已结束
        assertEquals(3, plan.remaining.size)
        assertEquals(4, plan.remainingTotal)
        assertEquals(1, plan.overflowCount)
        assertEquals(listOf(3, 4, 5), plan.remaining.map { it.startSection })
    }

    @Test
    fun `首门结束后第二门上移成为首位_逐节推进语义`() {
        val day = listOf(
            item(1, 2, startMinute = 480, endMinute = 580),
            item(3, 4, startMinute = 600, endMinute = 700),
            item(5, 6, startMinute = 840, endMinute = 940),
        )
        val before = TodayPlanBuilder.build(day, nowMinute = 500)
        val after = TodayPlanBuilder.build(day, nowMinute = 590) // 第 1 门刚结束
        assertEquals("课1", before.remaining.first().courseName)
        assertEquals("课3", after.remaining.first().courseName)
        assertEquals(2, after.remainingTotal)
    }

    @Test
    fun `全部已结束_统计与折叠均为0`() {
        val day = (1..6).map { n ->
            item(n, n, startMinute = 300 + n * 30, endMinute = 320 + n * 30)
        }
        val plan = TodayPlanBuilder.build(day, nowMinute = 1300)
        assertTrue(plan.remaining.isEmpty())
        assertEquals(0, plan.remainingTotal)
        assertEquals(0, plan.overflowCount)
        assertEquals(EmptyReason.ALL_DONE_OR_NONE, plan.emptyReason)
    }
}
