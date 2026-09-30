/*
 * WakeupCsvParserTest.kt —— WakeUp CSV 解析器测试（fixture 驱动 + 结构化错误）
 *
 * fixture 位于 app/src/test/resources/parser/wakeup/fixtures/，
 * 经 classLoader 读取（规格 §8 指定方式）。
 */
package com.gould.xputimetable.parser.wakeup

import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.ScheduleParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeupCsvParserTest {

    private val parser = WakeupCsvParser(nowMillis = { FIXED_NOW })

    private fun parseSuccess(text: String) =
        (parser.parse(text) as ScheduleParseResult.Success).schedule

    @Test
    fun `正常样本：课程数与安排数正确`() {
        val schedule = parseSuccess(fixture("sample_wakeup.csv"))
        assertEquals(5, schedule.courses.size)
        assertEquals(7, schedule.sessions.size)
    }

    @Test
    fun `正常样本：同一课程多行合并为多条安排`() {
        val schedule = parseSuccess(fixture("sample_wakeup.csv"))
        val math = schedule.courses.single { it.name == "高等数学" }
        val mathSessions = schedule.sessions.filter { it.courseId == math.id }
        assertEquals(2, mathSessions.size)
        assertEquals(setOf(1, 3), mathSessions.map { it.dayOfWeek }.toSet())
    }

    @Test
    fun `正常样本：字段正确（引号内逗号、空教室、英文星期、单双周列）`() {
        val schedule = parseSuccess(fixture("sample_wakeup.csv"))
        val math = schedule.courses.single { it.name == "高等数学" }
        assertEquals("张三", math.teacher)
        val monday = schedule.sessions.single { it.courseId == math.id && it.dayOfWeek == 1 }
        assertEquals("教1-101, 东校区", monday.classroom) // 引号内的逗号必须不切分
        assertEquals(1, monday.startSection)
        assertEquals(2, monday.endSection)
        assertEquals(1, monday.startWeek)
        assertEquals(16, monday.endWeek)
        assertEquals(WeekType.ALL, monday.weekType)

        // 周三行：单双周列 = 单周 → ODD
        val wednesday = schedule.sessions.single { it.courseId == math.id && it.dayOfWeek == 3 }
        assertEquals(WeekType.ODD, wednesday.weekType)

        // 大学物理：教室空 → null；Friday → 5；周次内联"单" → ODD
        val physics = schedule.courses.single { it.name == "大学物理" }
        val physicsSession = schedule.sessions.single { it.courseId == physics.id }
        assertEquals(null, physicsSession.classroom)
        assertEquals(5, physicsSession.dayOfWeek)
        assertEquals(WeekType.ODD, physicsSession.weekType)

        // 体育：裸"单周" → 默认学期区间 1..18
        val pe = schedule.courses.single { it.name == "体育" }
        val peSession = schedule.sessions.single { it.courseId == pe.id }
        assertEquals(1, peSession.startWeek)
        assertEquals(18, peSession.endWeek)
        assertEquals(WeekType.ODD, peSession.weekType)

        // 形势与政策：星期日 → 7；1-8(单) → ODD 1..8
        val policy = schedule.courses.single { it.name == "形势与政策" }
        val policySession = schedule.sessions.single { it.courseId == policy.id }
        assertEquals(7, policySession.dayOfWeek)
        assertEquals(1, policySession.startWeek)
        assertEquals(8, policySession.endWeek)
        assertEquals(WeekType.ODD, policySession.weekType)
    }

    @Test
    fun `正常样本：逗号周次列表放宽为区间并记入 anomalies`() {
        val schedule = parseSuccess(fixture("sample_wakeup.csv"))
        assertEquals(1, schedule.anomalies.size)
        assertTrue(schedule.anomalies.single().contains("第5行"))
        val english = schedule.courses.single { it.name == "大学英语" }
        val thursday = schedule.sessions.single { it.courseId == english.id && it.dayOfWeek == 4 }
        assertEquals(1, thursday.startWeek)
        assertEquals(9, thursday.endWeek)
        assertEquals(WeekType.ALL, thursday.weekType)
    }

    @Test
    fun `正常样本：source 为 WAKEUP_CSV，term 为空（由仓库回退激活学期）`() {
        val schedule = parseSuccess(fixture("sample_wakeup.csv"))
        assertTrue(schedule.courses.all { it.source == CourseSource.WAKEUP_CSV })
        assertEquals(null, schedule.term)
    }

    @Test
    fun `脏行样本：坏行跳过进 anomalies，好行保留`() {
        val schedule = parseSuccess(fixture("sample_wakeup_dirty.csv"))
        assertEquals(2, schedule.courses.size)
        assertEquals(2, schedule.sessions.size)
        assertEquals(4, schedule.anomalies.size)
        assertTrue(schedule.anomalies.any { it.startsWith("第3行") && it.contains("课程名为空") })
        assertTrue(schedule.anomalies.any { it.startsWith("第4行") && it.contains("星期") })
        assertTrue(schedule.anomalies.any { it.startsWith("第5行") && it.contains("节次") })
        assertTrue(schedule.anomalies.any { it.startsWith("第6行") && it.contains("周次") })
    }

    @Test
    fun `空内容：EmptyPayload`() {
        val result = parser.parse("")
        assertTrue(result is ScheduleParseResult.Failure)
        assertEquals(ParseError.EmptyPayload, (result as ScheduleParseResult.Failure).error)
    }

    @Test
    fun `只有空白行：EmptyPayload`() {
        val result = parser.parse("  \n\n\r\n")
        assertEquals(ParseError.EmptyPayload, (result as ScheduleParseResult.Failure).error)
    }

    @Test
    fun `只有表头无数据行：EmptyPayload`() {
        val result = parser.parse("课程名,教师,教室,星期,开始节次,结束节次,周次,单双周\n")
        assertEquals(ParseError.EmptyPayload, (result as ScheduleParseResult.Failure).error)
    }

    @Test
    fun `无表头：SchemaMismatch`() {
        val result = parser.parse("a,b,c,d,e,f,g,h\n1,2,3,4,5,6,7,8")
        val error = (result as ScheduleParseResult.Failure).error
        assertTrue(error is ParseError.SchemaMismatch)
        assertTrue((error as ParseError.SchemaMismatch).detail.contains("表头"))
    }

    @Test
    fun `表头缺必要列：SchemaMismatch 并指明缺哪列`() {
        val result = parser.parse("课程名,教师,星期\n高数,张三,周一")
        val error = (result as ScheduleParseResult.Failure).error as ParseError.SchemaMismatch
        assertTrue(error.detail.contains("节次") && error.detail.contains("周次"))
    }

    @Test
    fun `全部行脏：InvalidData`() {
        val result = parser.parse(HEADER + "\n,李四,教2-202,星期八,1,2,1-16,每周")
        val error = (result as ScheduleParseResult.Failure).error as ParseError.InvalidData
        assertTrue(error.detail.contains("未解析出任何课程"))
    }

    @Test
    fun `表头前的标题行被跳过，仍能找到真表头`() {
        val csv = "2026秋课程表\n" + HEADER + "\n高数,张三,教1-101,星期一,1,2,1-16,每周"
        val schedule = parseSuccess(csv)
        assertEquals(1, schedule.courses.size)
        assertEquals("高数", schedule.courses.single().name)
    }

    @Test
    fun `BOM 首行被剥离`() {
        val csv = "\uFEFF" + HEADER + "\n高数,张三,教1-101,星期一,1,2,1-16,每周"
        val schedule = parseSuccess(csv)
        assertEquals(1, schedule.courses.size)
    }

    @Test
    fun `重复解析同一文件：课程 id 稳定（覆盖而非新增重复）`() {
        val first = parseSuccess(fixture("sample_wakeup.csv"))
        val second = parseSuccess(fixture("sample_wakeup.csv"))
        assertEquals(first.courses.map { it.id }.toSet(), second.courses.map { it.id }.toSet())
    }

    @Test
    fun `一行多节课（节次列写区间）：一条安排`() {
        val csv = HEADER + "\n高数,张三,教1-101,星期一,1-2,,1-16,每周"
        val schedule = parseSuccess(csv)
        assertEquals(1, schedule.sessions.size)
        assertEquals(1, schedule.sessions.single().startSection)
        assertEquals(2, schedule.sessions.single().endSection)
    }

    companion object {
        private const val FIXED_NOW = 1_700_000_000_000L
        private const val HEADER =
            "课程名,教师,教室,星期,开始节次,结束节次,周次,单双周"

        internal fun fixture(name: String): String =
            WakeupCsvParserTest::class.java.classLoader!!
                .getResourceAsStream("parser/wakeup/fixtures/$name")!!
                .readBytes().decodeToString()
    }
}
