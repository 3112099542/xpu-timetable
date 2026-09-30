/*
 * WakeupFieldParsersTest.kt —— CSV 字段文法的容错矩阵测试（周次/星期/节次/表头别名）
 *
 * 覆盖 Spec M2-A §4 要求的全部周次写法，以及脏数据"绝不静默接受"的边界。
 */
package com.gould.xputimetable.parser.wakeup

import com.gould.xputimetable.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeupFieldParsersTest {

    // ---------- 周次文法（Spec 要求至少覆盖 1-16 / 1-16周 / 1,3,5 / 单周 / 双周）----------

    @Test
    fun `区间写法 1-16`() {
        assertEquals(WeekSpec(1, 16, WeekType.ALL), parseWeekSpec("1-16", null))
    }

    @Test
    fun `区间带周字 1-16周`() {
        assertEquals(WeekSpec(1, 16, WeekType.ALL), parseWeekSpec("1-16周", null))
    }

    @Test
    fun `区间带括号 1-16(周)`() {
        assertEquals(WeekSpec(1, 16, WeekType.ALL), parseWeekSpec("1-16(周)", null))
    }

    @Test
    fun `区间带全角括号 1-8（单）`() {
        assertEquals(WeekSpec(1, 8, WeekType.ODD), parseWeekSpec("1-8（单）", null))
    }

    @Test
    fun `逗号列表 1,3,5 放宽为区间并记异常`() {
        val spec = parseWeekSpec("1,3,5", null)!!
        assertEquals(1, spec.startWeek)
        assertEquals(5, spec.endWeek)
        assertEquals(WeekType.ALL, spec.weekType)
        assertEquals(true, spec.anomaly != null)
    }

    @Test
    fun `连续逗号列表 1,2,3 区间化且无异常`() {
        val spec = parseWeekSpec("1,2,3", null)!!
        assertEquals(1, spec.startWeek)
        assertEquals(3, spec.endWeek)
        assertEquals(WeekType.ALL, spec.weekType)
        assertNull(spec.anomaly)
    }

    @Test
    fun `裸单周`() {
        assertEquals(WeekSpec(1, 18, WeekType.ODD), parseWeekSpec("单周", null))
    }

    @Test
    fun `裸双周`() {
        assertEquals(WeekSpec(1, 18, WeekType.EVEN), parseWeekSpec("双周", null))
    }

    @Test
    fun `区间加内联单周 1-16 单周`() {
        assertEquals(WeekSpec(1, 16, WeekType.ODD), parseWeekSpec("1-16 单周", null))
    }

    @Test
    fun `区间加单字 1-16单`() {
        assertEquals(WeekSpec(1, 16, WeekType.ODD), parseWeekSpec("1-16单", null))
    }

    @Test
    fun `单数字 5`() {
        assertEquals(WeekSpec(5, 5, WeekType.ALL), parseWeekSpec("5", null))
    }

    @Test
    fun `裸每周走默认学期区间`() {
        assertEquals(WeekSpec(1, 18, WeekType.ALL), parseWeekSpec("每周", null))
    }

    @Test
    fun `单双周列提供单周标记`() {
        assertEquals(WeekSpec(1, 16, WeekType.ODD), parseWeekSpec("1-16", "单周"))
    }

    @Test
    fun `单双周列提供双周标记`() {
        assertEquals(WeekSpec(1, 16, WeekType.EVEN), parseWeekSpec("1-16", "双周"))
    }

    @Test
    fun `周次空但单双周列有标记`() {
        assertEquals(WeekSpec(1, 18, WeekType.EVEN), parseWeekSpec("", "双周"))
    }

    @Test
    fun `周次内联标记优先于单双周列`() {
        assertEquals(WeekSpec(1, 16, WeekType.ODD), parseWeekSpec("1-16单", "双周"))
    }

    @Test
    fun `倒置区间 3-1 判脏`() {
        assertNull(parseWeekSpec("3-1", null))
    }

    @Test
    fun `垃圾字符串 abc 判脏`() {
        assertNull(parseWeekSpec("abc", null))
    }

    @Test
    fun `空串判脏`() {
        assertNull(parseWeekSpec("", null))
        assertNull(parseWeekSpec(" ", " "))
    }

    // ---------- 星期 ----------

    @Test
    fun `星期写法兼容`() {
        assertEquals(1, parseDayOfWeek("星期一"))
        assertEquals(1, parseDayOfWeek("周一"))
        assertEquals(1, parseDayOfWeek("一"))
        assertEquals(1, parseDayOfWeek("1"))
        assertEquals(1, parseDayOfWeek("Monday"))
        assertEquals(5, parseDayOfWeek("friday"))
        assertEquals(7, parseDayOfWeek("星期日"))
        assertEquals(7, parseDayOfWeek("天"))
    }

    @Test
    fun `星期脏数据判脏`() {
        assertNull(parseDayOfWeek("星期八"))
        assertNull(parseDayOfWeek("0"))
        assertNull(parseDayOfWeek("八"))
        assertNull(parseDayOfWeek("abc"))
        assertNull(parseDayOfWeek(""))
    }

    // ---------- 节次 ----------

    @Test
    fun `节次写法兼容`() {
        assertEquals(1 to 2, parseSectionCell("1-2"))
        assertEquals(1 to 2, parseSectionCell("第1-2节"))
        assertEquals(2 to 2, parseSectionCell("2"))
    }

    @Test
    fun `节次脏数据判脏`() {
        assertNull(parseSectionCell(""))
        assertNull(parseSectionCell("x"))
    }

    // ---------- 表头别名 ----------

    @Test
    fun `表头别名匹配（忽略大小写与空白）`() {
        assertEquals(CsvColumn.NAME, WakeupHeaderAliases.match(normalizeHeaderCell(" 课程名 ")))
        assertEquals(CsvColumn.NAME, WakeupHeaderAliases.match(normalizeHeaderCell("Course")))
        assertEquals(CsvColumn.TEACHER, WakeupHeaderAliases.match(normalizeHeaderCell("授课教师")))
        assertEquals(CsvColumn.ROOM, WakeupHeaderAliases.match(normalizeHeaderCell("上课地点")))
        assertEquals(CsvColumn.DAY, WakeupHeaderAliases.match(normalizeHeaderCell("Weekday")))
        assertEquals(CsvColumn.START_SECTION, WakeupHeaderAliases.match(normalizeHeaderCell("开始节次")))
        assertEquals(CsvColumn.END_SECTION, WakeupHeaderAliases.match(normalizeHeaderCell("结束节次")))
        assertEquals(CsvColumn.WEEKS, WakeupHeaderAliases.match(normalizeHeaderCell("上课周")))
        assertEquals(CsvColumn.WEEK_TYPE, WakeupHeaderAliases.match(normalizeHeaderCell("weektype")))
        assertNull(WakeupHeaderAliases.match(normalizeHeaderCell("学分")))
    }
}
