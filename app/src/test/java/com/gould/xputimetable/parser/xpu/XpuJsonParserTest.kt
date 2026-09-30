/*
 * XpuJsonParserTest.kt —— 教务 JSON 解析器的单测（Spec M2-B §6，golden 断言）
 *
 * 金样本：app/src/test/resources/parser/xpu/fixtures/sample_schedule.json（已脱敏）。
 * 黄金期望值（Spec §1.6 已核算，测试硬断言）：
 *   activities = 16；唯一课程数（按 courseCode 分组）= 9；
 *   weekList 非空的安排 = 2（都属「大学英语Ⅲ」）；其余 14 条 weekList = null 且 weekType = ALL。
 */
package com.gould.xputimetable.parser.xpu

import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.WeekType
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.ScheduleParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class XpuJsonParserTest {

    private val parser = XpuJsonParser(semesterId = "147")

    // ---------- 金样本黄金值 ----------

    @Test
    fun 金样本_9门课16条安排() {
        val result = parser.parse(fixture("sample_schedule.json"))
        assertTrue(result is ScheduleParseResult.Success)
        val schedule = (result as ScheduleParseResult.Success).schedule
        assertEquals(9, schedule.courses.size)
        assertEquals(16, schedule.sessions.size)
        // 全部来自教务通道，且 termId 占位（applyImport 会钉死到目标学期）
        assertTrue(schedule.courses.all { it.source == CourseSource.WEB })
        assertTrue(schedule.courses.all { it.termId == 0L })
    }

    @Test
    fun 金样本_weekList非空恰好2条_其余为ALL区间() {
        val schedule = (parser.parse(fixture("sample_schedule.json")) as ScheduleParseResult.Success).schedule
        val explicit = schedule.sessions.filter { it.weeks != null }
        assertEquals(2, explicit.size)
        // 两条都属于「大学英语Ⅲ」（金样本 §1.6），且是显式列表的精确值
        assertTrue(explicit.all { it.weeks == listOf(2, 6, 10, 14) || it.weeks == listOf(1, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15, 16) })
        val interval = schedule.sessions.filter { it.weeks == null }
        assertEquals(14, interval.size)
        assertTrue(interval.all { it.weekType == WeekType.ALL })
    }

    @Test
    fun 金样本_第1条安排的星期与节次与样本一致() {
        // 样本第一条：大学体育Ⅲ，weekday=1，startUnit=3，endUnit=4，weeks=4..18
        val schedule = (parser.parse(fixture("sample_schedule.json")) as ScheduleParseResult.Success).schedule
        val first = schedule.sessions.first { it.dayOfWeek == 1 && it.startSection == 3 }
        assertEquals(4, first.endSection)
        assertEquals(4, first.startWeek)
        assertEquals(18, first.endWeek)
        assertEquals(WeekType.ALL, first.weekType)
        assertEquals("南环田径场（临潼）", first.classroom)
    }

    @Test
    fun 金样本_稳定id_按semesterId与courseCode生成() {
        val schedule = (parser.parse(fixture("sample_schedule.json")) as ScheduleParseResult.Success).schedule
        val expected = UUID.nameUUIDFromBytes("xpu:147:U51G111003".toByteArray(Charsets.UTF_8)).toString()
        val sport = schedule.courses.first { it.name == "大学体育Ⅲ" }
        assertEquals(expected, sport.id)
        // 同 courseCode 的多条安排共享同一课程 id（分组正确）
        val byCourse = schedule.sessions.groupBy { it.courseId }
        assertEquals(9, byCourse.keys.size)
        assertTrue(byCourse.values.any { it.size == 2 }) // 电工学（A）：周一 5-6 + 周三 3-4
    }

    @Test
    fun 金样本_颜色按courseCode升序取模12() {
        val schedule = (parser.parse(fixture("sample_schedule.json")) as ScheduleParseResult.Success).schedule
        // parser 按 courseCode 升序分组后 index % 12 赋色：courses 已按 code 升序、颜色为 0..8
        assertEquals(List(9) { it }, schedule.courses.map { it.colorTag })
        assertEquals(
            schedule.courses.map { it.code }.sortedBy { it.orEmpty() },
            schedule.courses.map { it.code },
        )
    }

    // ---------- 结构化错误 ----------

    @Test
    fun 非法JSON_SchemaMismatch() {
        val result = parser.parse("这不是JSON {{{")
        assertTrue(result is ScheduleParseResult.Failure)
        assertEquals(
            ParseError.SchemaMismatch::class,
            (result as ScheduleParseResult.Failure).error::class,
        )
    }

    @Test
    fun 空studentTableVms_InvalidData() {
        val result = parser.parse("""{"studentTableVms": []}""")
        val error = (result as ScheduleParseResult.Failure).error
        assertTrue(error is ParseError.InvalidData)
        assertEquals("响应中没有学生课表数据", (error as ParseError.InvalidData).detail)
    }

    @Test
    fun activities为空_InvalidData() {
        val result = parser.parse("""{"studentTableVms": [{"activities": []}]}""")
        val error = (result as ScheduleParseResult.Failure).error
        assertTrue(error is ParseError.InvalidData)
        assertEquals("本学期没有课程安排", (error as ParseError.InvalidData).detail)
    }

    // ---------- 防御式：缺字段跳行进 anomalies，不算整次失败 ----------

    @Test
    fun 缺必要字段的activity_跳过并记anomalies() {
        val json = """
            {"studentTableVms": [{"activities": [
              {"courseName": "好课", "courseCode": "GOOD001", "weekday": 1,
               "startUnit": 1, "endUnit": 2, "weekIndexes": [1,2,3]},
              {"courseName": "缺星期", "courseCode": "BAD001", "weekday": null,
               "startUnit": 1, "endUnit": 2, "weekIndexes": [1]},
              {"courseName": "缺节次", "courseCode": "BAD002", "weekday": 2,
               "startUnit": null, "weekIndexes": [1]},
              {"courseName": "缺周次", "courseCode": "BAD003", "weekday": 2,
               "startUnit": 1, "endUnit": 2, "weekIndexes": []},
              {"courseName": "", "courseCode": "BAD004", "weekday": 3,
               "startUnit": 1, "endUnit": 2, "weekIndexes": [1]},
              {"courseName": "缺课程编号", "weekday": 4,
               "startUnit": 1, "endUnit": 2, "weekIndexes": [1]}
            ]}]}
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result is ScheduleParseResult.Success)
        val schedule = (result as ScheduleParseResult.Success).schedule
        // 好课保留；缺编号的按课程名分组也保留（并记一条降级说明）；其余 4 条跳过
        assertEquals(2, schedule.courses.size)
        assertEquals(2, schedule.sessions.size)
        assertEquals(5, schedule.anomalies.size)
        assertTrue(schedule.anomalies.any { it.contains("星期无效") })
        assertTrue(schedule.anomalies.any { it.contains("缺少节次") })
        assertTrue(schedule.anomalies.any { it.contains("周次为空") })
        assertTrue(schedule.anomalies.any { it.contains("课程名为空") })
        assertTrue(schedule.anomalies.any { it.contains("缺少课程编号") })
    }

    @Test
    fun 多教师用顿号连接_空教师为null() {
        val json = """
            {"studentTableVms": [{"activities": [
              {"courseName": "多师课", "courseCode": "T001", "teachers": ["张三（R）", "李四"],
               "weekday": 1, "startUnit": 1, "endUnit": 2, "weekIndexes": [1]},
              {"courseName": "无师课", "courseCode": "T002", "teachers": [],
               "weekday": 2, "startUnit": 1, "endUnit": 2, "weekIndexes": [1]}
            ]}]}
        """.trimIndent()
        val schedule = (parser.parse(json) as ScheduleParseResult.Success).schedule
        assertEquals("张三（R）、李四", schedule.courses.first { it.code == "T001" }.teacher)
        assertEquals(null, schedule.courses.first { it.code == "T002" }.teacher)
    }

    @Test
    fun semesterId参与稳定id_不同学期不同id() {
        val json = """
            {"studentTableVms": [{"activities": [
              {"courseName": "课", "courseCode": "C001", "weekday": 1,
               "startUnit": 1, "endUnit": 2, "weekIndexes": [1]}
            ]}]}
        """.trimIndent()
        val s147 = (XpuJsonParser("147").parse(json) as ScheduleParseResult.Success).schedule
        val s148 = (XpuJsonParser("148").parse(json) as ScheduleParseResult.Success).schedule
        assertTrue(s147.courses.first().id != s148.courses.first().id)
    }

    private companion object {
        internal fun fixture(name: String): String =
            XpuJsonParserTest::class.java.classLoader!!
                .getResourceAsStream("parser/xpu/fixtures/$name")!!
                .readBytes().decodeToString()
    }
}
