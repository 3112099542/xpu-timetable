/*
 * ScheduleCodecTest.kt —— 课表快照编解码验证（M6 需求 6-A 自证）
 *
 * 重点：往返保真（含 weeks 显式周次与 note）、坏输入绝不抛异常（转 SchemaMismatch）、
 * 空课表是合法快照（空 ≠ 非法）、toParsedSchedule 的 source/id/term 规则。
 */
package com.gould.xputimetable.data.transfer

import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.parser.api.ParseError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCodecTest {

    // ---------- 夹具（全部具名参数）----------

    private fun snapshot() = ScheduleSnapshotDto(
        v = 1,
        app = "xpu-tt",
        term = TermDto(
            name = "2026-2027-1",
            startDate = "2026-08-24",
            totalWeeks = 18,
        ),
        slots = listOf(
            listOf(1, 480, 590),
            listOf(2, 600, 710),
        ),
        courses = listOf(
            CourseDto(
                name = "大学英语Ⅲ",
                code = "U51G111003",
                teacher = "王婷（R）",
                note = "周三节次偶有调整，看群里通知",
                colorTag = 3,
                sessions = listOf(
                    SessionDto(
                        dayOfWeek = 3,
                        startSection = 1,
                        endSection = 2,
                        startWeek = 1,
                        endWeek = 18,
                        weekType = "ODD",
                        classroom = "A-424语音室",
                        weeks = null,
                    ),
                    SessionDto(
                        dayOfWeek = 5,
                        startSection = 3,
                        endSection = 4,
                        startWeek = 2,
                        endWeek = 16,
                        weekType = "ALL",
                        classroom = null,
                        weeks = listOf(2, 4, 6),
                    ),
                ),
            ),
            CourseDto(
                name = "大学体育Ⅲ",
                code = null,
                teacher = null,
                note = null,
                colorTag = 5,
                sessions = listOf(
                    SessionDto(
                        dayOfWeek = 1,
                        startSection = 5,
                        endSection = 6,
                        startWeek = 1,
                        endWeek = 18,
                        weekType = "EVEN",
                        classroom = "田径场",
                        weeks = null,
                    ),
                ),
            ),
        ),
    )

    private val activeTerm = Term(
        id = 1L,
        name = "2026-2027-1",
        startDate = "2026-08-24",
        totalWeeks = 18,
        isActive = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun errorOf(result: DecodeResult<*>): ParseError.SchemaMismatch =
        (result as DecodeResult.Failure).error as ParseError.SchemaMismatch

    // ---------- 往返保真 ----------

    /** 用例 1：典型快照 encode → decode，课程名/教师/周次范围/单双周/教室全等。 */
    @Test
    fun roundTrip_preservesAllFields() {
        val decoded = ScheduleCodec.decode(ScheduleCodec.encode(snapshot()))
        assertTrue(decoded is DecodeResult.Success)
        val dto = (decoded as DecodeResult.Success).value
        assertEquals(2, dto.courses.size)
        val first = dto.courses[0]
        assertEquals("大学英语Ⅲ", first.name)
        assertEquals("U51G111003", first.code)
        assertEquals("王婷（R）", first.teacher)
        assertEquals(3, first.colorTag)
        assertEquals(2, first.sessions.size)
        val s0 = first.sessions[0]
        assertEquals(3, s0.dayOfWeek)
        assertEquals(1, s0.startSection)
        assertEquals(2, s0.endSection)
        assertEquals(1, s0.startWeek)
        assertEquals(18, s0.endWeek)
        assertEquals("ODD", s0.weekType)
        assertEquals("A-424语音室", s0.classroom)
        assertEquals("2026-08-24", dto.term.startDate)
        assertEquals(18, dto.term.totalWeeks)
        assertEquals(listOf(listOf(1, 480, 590), listOf(2, 600, 710)), dto.slots)
    }

    /** 用例 2：weeks 显式周次列表往返保真。 */
    @Test
    fun roundTrip_preservesExplicitWeekList() {
        val decoded = ScheduleCodec.decode(ScheduleCodec.encode(snapshot()))
        val weeks = (decoded as DecodeResult.Success).value.courses[0].sessions[1].weeks
        assertEquals(listOf(2, 4, 6), weeks)
    }

    /** 用例 3：note 备注往返保真（用户可能填了备注，导出必须保留）。 */
    @Test
    fun roundTrip_preservesNote() {
        val decoded = ScheduleCodec.decode(ScheduleCodec.encode(snapshot()))
        assertEquals("周三节次偶有调整，看群里通知", (decoded as DecodeResult.Success).value.courses[0].note)
    }

    /** 用例 4：空课表（0 门课）能 encode，decode 后 courses 为空、不抛异常。 */
    @Test
    fun emptyCourses_encodesAndDecodes() {
        val empty = snapshot().copy(courses = emptyList())
        val decoded = ScheduleCodec.decode(ScheduleCodec.encode(empty))
        assertTrue(decoded is DecodeResult.Success)
        assertTrue((decoded as DecodeResult.Success).value.courses.isEmpty())
    }

    // ---------- 坏输入（绝不抛异常）----------

    /** 用例 5：decode 非 JSON 文本 → Failure(SchemaMismatch)，不抛异常。 */
    @Test
    fun decodeNonJson_returnsSchemaMismatchWithoutThrowing() {
        val result = ScheduleCodec.decode("这不是一份课表文件")
        val error = errorOf(result)
        assertTrue(error.detail.isNotEmpty())
    }

    /** 用例 6：app 字段不是 xpu-tt → Failure，detail 含「标识」。 */
    @Test
    fun decodeWrongAppId_returnsFailureWithAppHint() {
        val json = ScheduleCodec.encode(snapshot().copy(app = "other-app"))
        val error = errorOf(ScheduleCodec.decode(json))
        assertTrue("detail=${error.detail}", error.detail.contains("标识"))
    }

    /** 用例 7：v=99 未来版本 → Failure，detail 含版本号与「升级」。 */
    @Test
    fun decodeFutureVersion_returnsFailureWithUpgradeHint() {
        val json = ScheduleCodec.encode(snapshot().copy(v = 99))
        val error = errorOf(ScheduleCodec.decode(json))
        assertTrue("detail=${error.detail}", error.detail.contains("99"))
        assertTrue("detail=${error.detail}", error.detail.contains("升级"))
    }

    /** 用例 8：缺 courses 字段 → SchemaMismatch，不抛异常。 */
    @Test
    fun decodeMissingCoursesField_returnsSchemaMismatchWithoutThrowing() {
        val json =
            """{"v":1,"app":"xpu-tt","term":{"name":"t","startDate":"2026-08-24","totalWeeks":18},"slots":[]}"""
        val error = errorOf(ScheduleCodec.decode(json))
        assertTrue(error.detail.isNotEmpty())
    }

    /** 用例 9：courses 为空数组 → 能 decode 成功（边界：空 ≠ 非法）。 */
    @Test
    fun decodeEmptyCoursesArray_succeeds() {
        val json = ScheduleCodec.encode(snapshot().copy(courses = emptyList()))
        val decoded = ScheduleCodec.decode(json)
        assertTrue(decoded is DecodeResult.Success)
        assertTrue((decoded as DecodeResult.Success).value.courses.isEmpty())
    }

    // ---------- toParsedSchedule 的硬要求 ----------

    /** source 全部为 FILE_JSON；id 重新生成且不重复；editedAt 恒为 null；sessions 挂到新课程 id。 */
    @Test
    fun toParsedSchedule_assignsFileJsonSourceAndFreshIds() {
        val parsed = ScheduleCodec.toParsedSchedule(snapshot(), activeTerm)
        assertEquals(CourseSource.FILE_JSON, parsed.courses[0].source)
        assertEquals(CourseSource.FILE_JSON, parsed.courses[1].source)
        assertNotEquals(parsed.courses[0].id, parsed.courses[1].id)
        assertFalse(parsed.courses[0].id.isBlank())
        assertNull(parsed.courses[0].editedAt)
        assertEquals(parsed.courses[0].id, parsed.sessions[0].courseId)
        assertEquals(parsed.courses[0].id, parsed.sessions[1].courseId)
        assertEquals(parsed.courses[1].id, parsed.sessions[2].courseId)
        assertEquals(3, parsed.sessions.size)
    }

    /** weekType 走 WeekCalc.parseWeekType 容错（未知值回退 ALL，不抛异常）。 */
    @Test
    fun toParsedSchedule_fallsBackToAllOnUnknownWeekType() {
        val dirty = snapshot().copy(
            courses = listOf(
                snapshot().courses[0].copy(
                    sessions = listOf(
                        snapshot().courses[0].sessions[0].copy(weekType = "weird-token"),
                    ),
                ),
            ),
        )
        val parsed = ScheduleCodec.toParsedSchedule(dirty, activeTerm)
        assertEquals(com.gould.xputimetable.domain.model.WeekType.ALL, parsed.sessions[0].weekType)
    }

    /** term 一致 → 填激活学期；不一致 → null 且 anomalies 有提示（不自动新建学期）。 */
    @Test
    fun toParsedSchedule_termRules() {
        val sameTerm = ScheduleCodec.toParsedSchedule(snapshot(), activeTerm)
        assertEquals(activeTerm, sameTerm.term)
        val foreignTerm = snapshot().copy(
            term = snapshot().term.copy(startDate = "2027-03-01"),
        )
        val parsed = ScheduleCodec.toParsedSchedule(foreignTerm, activeTerm)
        assertNull(parsed.term)
        assertTrue(
            parsed.anomalies.any { it.contains("文件学期") && it.contains("当前学期") },
        )
    }
}
