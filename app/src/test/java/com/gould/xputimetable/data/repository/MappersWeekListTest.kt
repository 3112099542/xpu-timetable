/*
 * MappersWeekListTest.kt —— week_list CSV ⇄ List<Int> 往返与容错（P0-A）
 *
 * 映射是 week_list 的唯一读写入口（与 DAO 两支 OR 查询互为镜像），这里钉死：
 *   - 升序、去重；空列表 / null → null（回到区间语义）；
 *   - 读侧对空白 / 脏 token 容错，绝不抛异常；
 *   - Entity ⇄ Domain 往返不丢 weeks 字段。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersWeekListTest {

    @Test
    fun `编码_升序去重`() {
        assertEquals("2,6,10,14", listOf(14, 2, 10, 6).toWeekListCsv())
        assertEquals("2,6,10,14", listOf(2, 6, 2, 10, 14).toWeekListCsv())
    }

    @Test
    fun `编码_空与null都回null`() {
        assertNull(null.toWeekListCsv())
        assertNull(emptyList<Int>().toWeekListCsv())
    }

    @Test
    fun `解码_容错脏token且升序去重`() {
        assertEquals(listOf(2, 6, 10, 14), "14, 2,10,,6".toWeeksOrNull())
        assertEquals(listOf(1, 3), "1,abc,3".toWeeksOrNull())
        assertNull(null.toWeeksOrNull())
        assertNull("".toWeeksOrNull())
        assertNull(" , ".toWeeksOrNull())
        assertNull("abc".toWeeksOrNull())
    }

    @Test
    fun `Entity与Domain往返_weeks不丢`() {
        val domain = CourseSession(
            courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 2, endWeek = 14, weekType = WeekType.ALL,
            weeks = listOf(2, 6, 10, 14), classroom = "A-101",
        )
        val entity = domain.toEntity()
        assertEquals("2,6,10,14", entity.weekList)
        assertEquals(domain, entity.toDomain())

        // weeks 为 null 的老数据：week_list 为 null，往返一致
        val legacy = domain.copy(weeks = null)
        assertNull(legacy.toEntity().weekList)
        assertEquals(legacy, legacy.toEntity().toDomain())
    }

    @Test
    fun `Entity默认构造_weekList为null`() {
        // 既有构造点（Fakes / 旧测试）不带 weekList 也能编译与往返
        val entity = CourseSessionEntity(
            courseId = "c1", dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = "ALL", classroom = null,
        )
        assertNull(entity.weekList)
        assertNull(entity.toDomain().weeks)
    }
}
