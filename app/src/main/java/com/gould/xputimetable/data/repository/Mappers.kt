/*
 * Mappers.kt —— 实体(Entity)与领域模型(Domain)的互转（单一职责）
 *
 * 作用：把"屋里屋外"两层对象摆渡清楚。Room 直接读写的是 data/db/entity 下的实体，
 * 而 UI / 业务逻辑只认 domain/model 下的领域模型。二者字段名一致、但类型可能不同
 * （最典型的是 week_type：实体里是 String，领域里是 WeekType 枚举），所有这类"翻译"
 * 都集中在本文件，避免在仓库实现里散落 toXxx() 转换、也避免领域层被 Room 注解污染。
 *
 * 设计要点：
 *   1. 全部是顶层扩展函数（CourseEntity.toDomain()、Course.toEntity() 等），
 *      无状态、可单测、不依赖 Android；
 *   2. week_type 的枚举 <-> 字符串互转放在边界处：实体→领域用 WeekCalc.parseWeekType
 *      （脏数据一律回退 ALL，绝不抛异常），领域→实体用 WeekType.name.uppercase()（枚举名就是列值）；
 *   3. SessionWithCoursePojo 是 SQL JOIN 的载体（数据层），转成领域 SessionWithCourse；
 *   4. ParsedSchedule（占位导入结构）转成实体列表，供 applyImport 事务写入。
 *
 * 为什么独立成文件：团队约定"单一职责 + 可单测"，映射与仓储逻辑分开后，两边都更短、更聚焦，
 * 也方便 MappersTest 专门验证 round-trip 不丢字段（含 editedAt 这类新字段）。
 */
package com.gould.xputimetable.data.repository

import com.gould.xputimetable.data.db.dao.SessionWithCoursePojo
import com.gould.xputimetable.data.db.entity.CourseEntity
import com.gould.xputimetable.data.db.entity.CourseSessionEntity
import com.gould.xputimetable.data.db.entity.ImportLogEntity
import com.gould.xputimetable.data.db.entity.TermEntity
import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.SessionWithCourse
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.model.TimeSlot
import com.gould.xputimetable.domain.model.WeekSchedule

// ---------- Course ----------

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    code = code,
    teacher = teacher,
    note = note,
    colorTag = colorTag,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    termId = termId,
    editedAt = editedAt,
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    code = code,
    teacher = teacher,
    note = note,
    colorTag = colorTag,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    termId = termId,
    editedAt = editedAt,
)

// ---------- CourseSession（week_type / week_list 在此翻译）----------

fun CourseSessionEntity.toDomain(): CourseSession = CourseSession(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    endSection = endSection,
    startWeek = startWeek,
    endWeek = endWeek,
    weekType = WeekCalc.parseWeekType(weekType),
    weeks = weekList.toWeeksOrNull(),
    classroom = classroom,
)

/**
 * 写入侧不变量：week_type 一律存**大写枚举名**（ALL/ODD/EVEN）。
 * 这是与 SQL 里 UPPER(s.week_type) 配套的「双保险」：新数据 canonical 大写，SQL 的 UPPER()
 * 再兜住历史/外部导入的脏数据（小写或变体 token），保证周视图不会因大小写静默漏课。
 * 所有写 week_type 的路径（upsertCourse / applyImport）都经此单一入口，勿在别处拼字符串。
 *
 * week_list 同理只经此单一入口编码（升序、去重；空列表 → null），与
 * CourseSessionDao 两支 OR 查询的解析端（weekListToWeeks）互为镜像。
 */
fun CourseSession.toEntity(): CourseSessionEntity = CourseSessionEntity(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    endSection = endSection,
    startWeek = startWeek,
    endWeek = endWeek,
    weekType = weekType.name.uppercase(),
    weekList = weeks.toWeekListCsv(),
    classroom = classroom,
)

/** List<Int> → CSV（升序去重）；null / 空列表 → null（回到区间语义）。 */
internal fun List<Int>?.toWeekListCsv(): String? =
    this?.distinct()?.sorted()?.takeIf { it.isNotEmpty() }?.joinToString(",")

/** CSV → List<Int>（升序）；null / 空白 / 全脏 token → null（容错，绝不抛异常）。 */
internal fun String?.toWeeksOrNull(): List<Int>? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val weeks = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
    return weeks.takeIf { it.isNotEmpty() }
}

// ---------- Term ----------

fun TermEntity.toDomain(): Term = Term(
    id = id,
    name = name,
    startDate = startDate,
    totalWeeks = totalWeeks,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Term.toEntity(): TermEntity = TermEntity(
    id = id,
    name = name,
    startDate = startDate,
    totalWeeks = totalWeeks,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---------- TimeSlot ----------

fun TimeSlotEntity.toDomain(): TimeSlot = TimeSlot(
    id = id,
    section = section,
    startMinute = startMinute,
    endMinute = endMinute,
)

fun TimeSlot.toEntity(): TimeSlotEntity = TimeSlotEntity(
    id = id,
    section = section,
    startMinute = startMinute,
    endMinute = endMinute,
)

// ---------- 周视图 JOIN 结果 → 领域 SessionWithCourse ----------

fun SessionWithCoursePojo.toDomain(): SessionWithCourse = SessionWithCourse(
    session = session.toDomain(),
    courseName = courseName,
    colorTag = colorTag,
)

// ---------- ParsedSchedule（占位导入结构）→ 实体列表 ----------

/**
 * ParsedSchedule → 课程实体列表。
 *
 * 关键：每个导入课程强制归入 [termId] 指定的学期（覆盖其自带的 termId）。这是 Spec AC-13
 * 「重复导入同一学期以覆盖方式更新本学期数据」的落地——导入的所有课程都打上目标学期标记。
 * 目标学期优先取 ParsedSchedule.term（接 parser 后以导入解析出的学期为准），缺省时由调用方
 * （applyImport）回退到当前激活学期。占位阶段 ParsedSchedule.term 可能为 null，故此处只负责
 * 把 termId 钉死到每个课程实体上。
 */
fun ParsedSchedule.toCourseEntities(termId: Long): List<CourseEntity> =
    courses.map { it.copy(termId = termId).toEntity() }

fun ParsedSchedule.toSessionEntities(): List<CourseSessionEntity> = sessions.map { it.toEntity() }

// ---------- ImportLog 构造（applyImport 写审计用）----------

fun ParsedSchedule.toImportLog(
    status: String,
    courseCount: Int,
    createdAt: Long,
    replacedCount: Int = 0,
    preservedManualCount: Int = 0,
): ImportLogEntity = ImportLogEntity(
    source = courses.firstOrNull()?.source ?: "UNKNOWN",
    status = status,
    courseCount = courseCount,
    createdAt = createdAt,
    replacedCount = replacedCount,
    preservedManualCount = preservedManualCount,
)

// ---------- ImportSummary 构造 ----------

fun ParsedSchedule.toImportSummary(
    success: Boolean,
    courseCount: Int,
    sessionCount: Int,
    replacedCount: Int = 0,
    preservedManualCount: Int = 0,
    suspectedDuplicateNames: List<String> = emptyList(),
): ImportSummary = ImportSummary(
    success = success,
    courseCount = courseCount,
    sessionCount = sessionCount,
    message = buildSummaryMessage(courseCount, sessionCount, replacedCount, preservedManualCount),
    replacedCount = replacedCount,
    preservedManualCount = preservedManualCount,
    suspectedDuplicateNames = suspectedDuplicateNames,
)

/**
 * 导入摘要的文案（日志与结果页共用）。
 * 说明：这里明确写出"替换了 X 门同来源课程 / 保留了 Y 门手动课程"，
 * 是为了让 AC-13 的覆盖范围在事后可读——用户能看懂"这次导入动了我哪些数据"。
 */
private fun buildSummaryMessage(
    courseCount: Int,
    sessionCount: Int,
    replacedCount: Int,
    preservedManualCount: Int,
): String = buildString {
    append("导入 $courseCount 门课程 / $sessionCount 条上课安排")
    if (replacedCount > 0) append("；替换同来源课程 $replacedCount 门")
    append("；保留手动添加课程 $preservedManualCount 门")
}

// 便于 WeekSchedule 整体组装
fun List<SessionWithCoursePojo>.toWeekSchedule(week: Int): WeekSchedule =
    WeekSchedule(week = week, items = map { it.toDomain() })
