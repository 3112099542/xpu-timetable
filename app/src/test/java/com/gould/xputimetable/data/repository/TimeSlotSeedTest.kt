/*
 * TimeSlotSeedTest.kt —— 作息表预置（2026-09-17 修复：首次安装后作息表为空，导致提醒永久失效）
 *
 * 覆盖两点：① 空表时写入西工程大标准作息；② 已有作息时不覆盖（幂等，用户改过不被重置）。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `作息表为空时写入西工程大标准作息`() = runTest {
        val dao = FakeTimeSlotDao(slots = emptyList())
        repo(dao).ensureDefaultTimeSlots()

        assertEquals(10, dao.inserted.size)
        val first = dao.inserted.minBy { it.section }
        assertEquals(1, first.section)
        assertEquals(480, first.startMinute) // 08:00
        assertEquals(530, first.endMinute) // 08:50
        // 节次号连续且唯一
        assertEquals((1..10).toList(), dao.inserted.map { it.section }.sorted())
    }

    @Test
    fun `已有作息时不覆盖（用户改过也不会被重置）`() = runTest {
        val custom = TimeSlotEntity(id = 1L, section = 1, startMinute = 500, endMinute = 545)
        val dao = FakeTimeSlotDao(slots = listOf(custom))
        repo(dao).ensureDefaultTimeSlots()

        assertTrue("非空时不应再写入预置数据", dao.inserted.isEmpty())
    }
}
