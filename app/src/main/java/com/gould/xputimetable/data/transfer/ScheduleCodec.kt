/*
 * ScheduleCodec.kt —— 课表快照的 JSON 编解码（M6 需求 6，文件导出/导入与二维码共用）
 *
 * 设计要点：
 *   - 用专属 DTO 而非直接序列化 Course/CourseSession 领域类：领域类含 WeekType 枚举与
 *     领域专属字段，未来加字段会破坏兼容；DTO 字段用短名（要喂二维码，省字节）。
 *   - encode 必须紧凑（encodeDefaults=false + explicitNulls=false + 不 pretty-print），
 *     这是二维码容量实测（§7.1：真机课表 deflate+base64 后 772 字节）的前提。
 *   - 明确不导出：id（导入时重新生成，跨设备 UUID 无意义且会冲突）、source（统一按
 *     FILE_JSON 新来源入库，不继承原来源——复用 WEB/WAKEUP_CSV 会触发"按来源整体
 *     替换"误删旧数据）、createdAt/updatedAt/editedAt（导入取当前时间；editedAt 保持
 *     null，导入数据不算"用户编辑过"，否则会误获 MANUAL 保护）。
 *   - v 与 app 故意不给默认值：encodeDefaults=false 下无默认值的字段才总会写出，
 *     保证导出文件永远自带"我是谁/我什么版本"标识，解码端据此校验。
 */
package com.gould.xputimetable.data.transfer

import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.parser.api.ParseError
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 当前快照格式版本；decode 遇到更大的 v 提示升级 App。 */
private const val FORMAT_VERSION = 1

/** 导出文件/二维码的 App 标识（decode 时校验，拒绝外来文件）。 */
private const val APP_ID = "xpu-tt"

@Serializable
data class ScheduleSnapshotDto(
    val v: Int,
    val app: String,
    val term: TermDto,
    /** [section, startMinute, endMinute]（作息表全量）。 */
    val slots: List<List<Int>>,
    val courses: List<CourseDto>,
)

/** 学期元信息。故意不含 id 与 isActive——导入一律落到本机当前激活学期。 */
@Serializable
data class TermDto(
    val name: String,
    val startDate: String,
    val totalWeeks: Int,
)

@Serializable
data class CourseDto(
    val name: String,
    val code: String? = null,
    val teacher: String? = null,
    /** 用户可能填了备注，导出要保留。 */
    val note: String? = null,
    /** 0..N 调色板索引（CoursePalette 用）。 */
    val colorTag: Int,
    val sessions: List<SessionDto>,
)

@Serializable
data class SessionDto(
    /** 1=周一 … 7=周日 */
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    /** "ALL" / "ODD" / "EVEN"。 */
    val weekType: String,
    val classroom: String? = null,
    /** 显式周次列表（week_list 列，可空）。 */
    val weeks: List<Int>? = null,
)

/** 编解码结果（本文件私有语义，不进 parser/api——ParseError 不为此扩子类）。 */
sealed interface DecodeResult<out T> {
    data class Success<T>(val value: T) : DecodeResult<T>
    data class Failure(val error: ParseError) : DecodeResult<Nothing>
}

object ScheduleCodec {

    private val encodeJson = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    private val decodeJson = Json {
        ignoreUnknownKeys = true
    }

    /** DTO → 压缩紧凑的 JSON 文本（给文件导出与二维码共用）。 */
    fun encode(snapshot: ScheduleSnapshotDto): String = encodeJson.encodeToString(snapshot)

    /**
     * JSON 文本 → DTO（宽松解析：非法 JSON / 缺字段 / 标识或版本不符都转 Failure，绝不抛）。
     */
    fun decode(text: String): DecodeResult<ScheduleSnapshotDto> {
        val dto = runCatching { decodeJson.decodeFromString<ScheduleSnapshotDto>(text) }
            .getOrElse { return DecodeResult.Failure(ParseError.SchemaMismatch("不是本 App 导出的课表文件")) }
        if (dto.app != APP_ID) {
            return DecodeResult.Failure(ParseError.SchemaMismatch("文件标识不是 $APP_ID"))
        }
        if (dto.v > FORMAT_VERSION) {
            return DecodeResult.Failure(
                ParseError.SchemaMismatch("文件版本 v=${dto.v} 高于本 App 支持的 v=$FORMAT_VERSION，请升级 App"),
            )
        }
        return DecodeResult.Success(dto)
    }

    /**
     * DTO → 领域模型（补齐 id/时间戳/source，产出可直接 applyImport 的 ParsedSchedule）。
     *
     * term 规则（§6.3）：文件学期起日与 [activeTerm] 一致 → 填激活学期（覆盖更新）；
     * 不一致或 [activeTerm] 为 null → 填 null（applyImport 回退到激活学期，绝不自动新建学期）。
     */
    fun toParsedSchedule(dto: ScheduleSnapshotDto, activeTerm: Term?): ParsedSchedule {
        val now = System.currentTimeMillis()
        val anomalies = mutableListOf<String>()
        if (activeTerm != null) {
            anomalies.add("文件学期为「${dto.term.name}」，将导入到当前学期「${activeTerm.name}」")
        }
        val term = activeTerm?.takeIf { it.startDate == dto.term.startDate }
        val courseIds = dto.courses.map { UUID.randomUUID().toString() }
        val courses = dto.courses.mapIndexed { index, c ->
            Course(
                id = courseIds[index],
                name = c.name,
                code = c.code,
                teacher = c.teacher,
                note = c.note,
                colorTag = c.colorTag,
                source = CourseSource.FILE_JSON,
                createdAt = now,
                updatedAt = now,
                termId = activeTerm?.id ?: 0L,
                editedAt = null,
            )
        }
        val sessions = dto.courses.flatMapIndexed { index, c ->
            c.sessions.map { s ->
                CourseSession(
                    id = 0L,
                    courseId = courseIds[index],
                    dayOfWeek = s.dayOfWeek,
                    startSection = s.startSection,
                    endSection = s.endSection,
                    startWeek = s.startWeek,
                    endWeek = s.endWeek,
                    weekType = WeekCalc.parseWeekType(s.weekType),
                    weeks = s.weeks,
                    classroom = s.classroom,
                )
            }
        }
        return ParsedSchedule(
            courses = courses,
            sessions = sessions,
            term = term,
            anomalies = anomalies,
        )
    }

    /** 一步到位：JSON 文本 → ParsedSchedule（无学期上下文的纯解码，term 恒为 null）。 */
    fun decodeToParsedSchedule(text: String): DecodeResult<ParsedSchedule> =
        decodeToParsedSchedule(text, activeTerm = null)

    /** 同上，但带学期上下文（JsonFileImporter 用：term 一致性规则与提示由 toParsedSchedule 落实）。 */
    fun decodeToParsedSchedule(text: String, activeTerm: Term?): DecodeResult<ParsedSchedule> =
        when (val r = decode(text)) {
            is DecodeResult.Success -> DecodeResult.Success(toParsedSchedule(r.value, activeTerm))
            is DecodeResult.Failure -> r
        }

    /** 领域模型 → DTO（导出组装集中在这里，仓库实现只管取数）。 */
    fun fromDomain(
        term: Term,
        courses: List<Course>,
        sessions: List<CourseSession>,
        slots: List<TimeSlot>,
    ): ScheduleSnapshotDto {
        val sessionsByCourse = sessions.groupBy { it.courseId }
        return ScheduleSnapshotDto(
            v = FORMAT_VERSION,
            app = APP_ID,
            term = TermDto(
                name = term.name,
                startDate = term.startDate,
                totalWeeks = term.totalWeeks,
            ),
            slots = slots.sortedBy { it.section }.map { slot ->
                listOf(slot.section, slot.startMinute, slot.endMinute)
            },
            courses = courses.map { c ->
                CourseDto(
                    name = c.name,
                    code = c.code,
                    teacher = c.teacher,
                    note = c.note,
                    colorTag = c.colorTag,
                    sessions = (sessionsByCourse[c.id] ?: emptyList()).map { s ->
                        SessionDto(
                            dayOfWeek = s.dayOfWeek,
                            startSection = s.startSection,
                            endSection = s.endSection,
                            startWeek = s.startWeek,
                            endWeek = s.endWeek,
                            weekType = s.weekType.name,
                            classroom = s.classroom,
                            weeks = s.weeks,
                        )
                    },
                )
            },
        )
    }
}
