/*
 * TimeSlotSeedTest.kt —— 作息表预置
 *
 * 建立于 2026-09-17（首次安装后作息表为空 → 提醒永久失效）；2026-10-02 随 M10 变更契约。
 *
 * 【契约（M10 起）：按节次**增量补齐**】
 *   - 某节的作息不存在 → 补上预置值；
 *   - 已存在的节 → 一律不动（用户改过的作息永不被覆盖）。
 *
 * 【为什么不是"表为空才写"】
 *   默认作息从 10 节扩到 12 节后，老库里已经有 10 条 → 表非空 → 11/12 节永远补不上，
 *   而周视图网格兜底渲染 12 行 → 第 11、12 行显示不出时间。
 *   这正是用户报告的「11、12 节不显示时间」缺陷，第 3 个用例是它的回归测试。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlotSeedTest {

    private val noOpTx = object : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private fun repo(timeSlotDao: FakeTimeSlotDao) = TimetableRepositoryImpl(
        courseDao = FakeCourseDao(),
        courseSessionDao = FakeCourseSessionDao(),
        termDao = FakeTermDao(active = null),
        timeSlotDao = timeSlotDao,
        importLogDao = FakeImportLogDao(),
        tx = noOpTx,
    )

    @Test
    fun `作息表为空时写入完整预置作息`() = runTest {
        val dao = FakeTimeSlotDao(slots = emptyList())
        repo(dao).ensureDefaultTimeSlots()

        assertEquals(12, dao.inserted.size)
        val first = dao.inserted.minBy { it.section }
        assertEquals(1, first.section)
        assertEquals(480, first.startMinute) // 08:00
        assertEquals(530, first.endMinute) // 08:50
        // 节次号连续且唯一
        assertEquals((1..12).toList(), dao.inserted.map { it.section }.sorted())
    }

    @Test
    fun `已存在的节一律不动，只补缺失的节`() = runTest {
        // 用户把第 1 节改成了 08:20-09:05（500/545）
        val custom = TimeSlotEntity(id = 1L, section = 1, startMinute = 500, endMinute = 545)
        val dao = FakeTimeSlotDao(slots = listOf(custom))
        repo(dao).ensureDefaultTimeSlots()

        // 只补 2..12，绝不包含已被用户改过的第 1 节
        assertEquals((2..12).toList(), dao.inserted.map { it.section }.sorted())
        assertFalse("不得覆盖用户改过的第 1 节", dao.inserted.any { it.section == 1 })
        // 说明：FakeTimeSlotDao.slots 是 private，这里无法直接读回原记录做断言；
        // "不覆盖"这一点由「inserted 中不含第 1 节」保证（写入侧本就是只补差集）。
    }

    @Test
    fun `老库只有 1到10 节时补上 11 与 12 节（回归：11、12 节不显示时间）`() = runTest {
        val old = (1..10).map { s ->
            TimeSlotEntity(id = s.toLong(), section = s, startMinute = 480 + s, endMinute = 530 + s)
        }
        val dao = FakeTimeSlotDao(slots = old)
        repo(dao).ensureDefaultTimeSlots()

        assertEquals(listOf(11, 12), dao.inserted.map { it.section }.sorted())
        val s11 = dao.inserted.first { it.section == 11 }
        val s12 = dao.inserted.first { it.section == 12 }
        assertTrue("第 11 节必须有时间", s11.endMinute > s11.startMinute)
        assertTrue("第 12 节必须有时间", s12.endMinute > s12.startMinute)
        // 已存在的 1..10 一条都不许重写
        assertTrue(dao.inserted.none { it.section in 1..10 })
    }
}
