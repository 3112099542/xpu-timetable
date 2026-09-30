/*
 * XpuJsonParser.kt —— 强智系教务 print-data JSON → ParsedSchedule（教务直连通道解析核心）
 *
 * 实现 M2-A 已建的 PayloadParser<String>：输入是拦截到的响应体字符串。
 * 解析规则见 Spec M2-B §1.3/§4.3：
 *   - JSON 解析失败 → SchemaMismatch；studentTableVms 空 / activities 空 → InvalidData；
 *   - 单条 activity 缺必要字段（课程名/星期/节次/周次）→ 跳过并记 anomalies，不算整次失败；
 *   - 同一 courseCode 的多条 activity = 同一门课的多条安排（分组）；
 *   - Course.id = UUID("xpu:$semesterId:$courseCode")，稳定 ⇒ 幂等导入 + AC-22 同 id 防线；
 *   - colorTag = 按 courseCode 升序排列后 index % 12（同课同色、跨课程分散、结果稳定）。
 */
package com.gould.xputimetable.parser.xpu

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.PayloadParser
import com.gould.xputimetable.parser.api.ScheduleParseResult
import com.gould.xputimetable.parser.api.ScheduleParseResult.Failure
import com.gould.xputimetable.parser.api.ScheduleParseResult.Success
import com.gould.xputimetable.parser.xpu.dto.XpuActivity
import com.gould.xputimetable.parser.xpu.dto.XpuScheduleResponse
import java.util.UUID
import kotlinx.serialization.json.Json

class XpuJsonParser(
    /** 学期 id（从拦截到的 print-data URL 提取，会变，不得硬编码）：参与稳定 id 生成。 */
    private val semesterId: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PayloadParser<String> {

    override fun parse(input: String): ScheduleParseResult {
        val response = try {
            json.decodeFromString(XpuScheduleResponse.serializer(), input)
        } catch (e: Exception) {
            return Failure(ParseError.SchemaMismatch("教务响应不是有效的 JSON（${e.message ?: e::class.simpleName}）"))
        }
        if (response.studentTableVms.isEmpty()) {
            return Failure(ParseError.InvalidData("响应中没有学生课表数据"))
        }
        // 课表数据在 studentTableVms[0]（金样本里后续 vm 是重复的同一名学生）
        val activities = response.studentTableVms[0].activities
        if (activities.isEmpty()) {
            return Failure(ParseError.InvalidData("本学期没有课程安排"))
        }

        val anomalies = mutableListOf<String>()
        val activitiesByCode = LinkedHashMap<String, MutableList<XpuActivity>>()
        for ((index, activity) in activities.withIndex()) {
            val name = activity.courseName?.trim().orEmpty()
            if (name.isEmpty()) {
                anomalies += "第${index + 1}条安排：课程名为空，已跳过"
                continue
            }
            val code = activity.courseCode?.trim().takeUnless { it.isNullOrEmpty() }
            if (code == null) {
                anomalies += "「$name」：缺少课程编号，按课程名分组"
                activitiesByCode.getOrPut(name) { mutableListOf() } += activity
                continue
            }
            activitiesByCode.getOrPut(code) { mutableListOf() } += activity
        }

        val now = nowMillis()
        val sessions = mutableListOf<CourseSession>()
        // 先构建安排并记录「产出过有效安排」的分组键——全部安排都被跳过的课程
        // 不生成孤儿 Course（否则预览页与库里会出现 0 安排的课程）
        val keysWithSession = LinkedHashSet<String>()
        val activitiesByCodeKeys = activitiesByCode.keys.toList()
        val courseIds = activitiesByCodeKeys.associateWith { stableCourseId(it) }
        for (key in activitiesByCodeKeys) {
            for (activity in activitiesByCode.getValue(key)) {
                val session = buildSession(activity, courseIds.getValue(key), anomalies) ?: continue
                sessions += session
                keysWithSession += key
            }
        }

        // colorTag：按分组键升序排列后 index % 12（同课同色、跨课程分散、稳定可复现）
        val sortedKeys = keysWithSession.sorted()
        val courses = sortedKeys.mapIndexed { index, key ->
            val head = activitiesByCode.getValue(key).first()
            Course(
                id = courseIds.getValue(key),
                name = head.courseName.orEmpty().trim(),
                code = if (key == head.courseName?.trim()) null else key,
                teacher = head.teachers.filter { it.isNotBlank() }.joinToString("、").ifEmpty { null },
                note = null,
                colorTag = index % COLOR_PALETTE_SIZE,
                source = CourseSource.WEB,
                createdAt = now,
                updatedAt = now,
                termId = 0L, // 占位：applyImport 会把 termId 钉死到目标学期
                editedAt = null,
            )
        }

        if (courses.isEmpty()) {
            return Failure(ParseError.InvalidData("未解析出任何课程安排（${anomalies.size} 条全部解析失败）"))
        }
        return Success(
            ParsedSchedule(
                courses = courses,
                sessions = sessions,
                term = null, // 由仓库回退到当前激活学期（与 CSV 通道一致）
                anomalies = anomalies.toList(),
            ),
        )
    }

    /** 单条 activity → CourseSession；缺必要字段（星期/节次/周次）记 anomalies 返回 null。 */
    private fun buildSession(
        activity: XpuActivity,
        courseId: String,
        anomalies: MutableList<String>,
    ): CourseSession? {
        val label = activity.courseName?.trim().orEmpty().ifEmpty { "未知课程" }
        fun skip(reason: String): CourseSession? {
            val weeks = activity.weeksStr.orEmpty().ifEmpty { activity.weekIndexes.toString() }
            anomalies += "「$label」（周次 $weeks）：$reason，已跳过"
            return null
        }
        val day = activity.weekday
        if (day == null || day !in 1..7) return skip("星期无效")
        val start = activity.startUnit ?: return skip("缺少节次")
        val end = activity.endUnit ?: start
        if (start < 1 || end < start) return skip("节次无效")
        val weeks = weekRepresentation(activity.weekIndexes) ?: return skip("周次为空或无法识别")
        return CourseSession(
            courseId = courseId,
            dayOfWeek = day,
            startSection = start,
            endSection = end,
            startWeek = weeks.startWeek,
            endWeek = weeks.endWeek,
            weekType = weeks.weekType,
            weeks = weeks.weeks,
            classroom = activity.room?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** 幂等导入的关键：同一学期同一 courseCode 永远生成同一 id（AC-22 同 id 防线的前提）。 */
    private fun stableCourseId(key: String): String =
        UUID.nameUUIDFromBytes("xpu:$semesterId:$key".toByteArray(Charsets.UTF_8)).toString()

    private companion object {
        const val COLOR_PALETTE_SIZE = 12

        /** 学校接口随时可能加字段，必须容忍未知键（Spec P0-B §3.3）。 */
        val json: Json = Json { ignoreUnknownKeys = true }
    }
}
