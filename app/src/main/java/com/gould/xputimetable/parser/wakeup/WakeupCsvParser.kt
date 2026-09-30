/*
 * WakeupCsvParser.kt —— WakeUp 课表 CSV → ParsedSchedule（兜底导入通道的解析核心）
 *
 * 容错策略（Spec M2-A §4）：WakeUp 各版本列名不一，**不假设固定列序**，
 * 用「表头关键词别名匹配」定位列；脏行（课程名/星期/节次/周次解析不出）跳过并
 * 记入 anomalies，**不算失败**——一门课表文件里混几行坏数据不该让整个导入失败。
 *
 * 表头识别的一个实现判断（规格未细说）：只含「课程名/课程」关键词、但认不出
 * 任何其他必要列（星期/节次/周次）的行，视为文件标题行而非表头（如「2026 秋课程表」），
 * 继续向下找真正的表头——避免标题行被误判成表头后整文件变成 SchemaMismatch。
 *
 * 稳定 id：(课程名 + 教师) 生成 UUID（UUID.nameUUIDFromBytes，MD5 v3），
 * 保证重复导入同一文件是"按来源整体替换覆盖"而不是"新增重复"。
 */
package com.gould.xputimetable.parser.wakeup

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.PayloadParser
import com.gould.xputimetable.parser.api.ScheduleParseResult
import com.gould.xputimetable.parser.api.ScheduleParseResult.Failure
import com.gould.xputimetable.parser.api.ScheduleParseResult.Success
import java.util.UUID

class WakeupCsvParser(
    /** 时间源注入：生产用系统时钟，单测注入固定值保证解析结果确定可比。 */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PayloadParser<String> {

    override fun parse(input: String): ScheduleParseResult {
        val lines = splitLines(input)
        // 规格 §4：文件为空或去掉空行后无内容 → EmptyPayload（先于表头判定，
        // 纯空白文件不该报"格式不认识"去吓用户）
        if (lines.all { cells -> cells.all { it.isBlank() } }) {
            return Failure(ParseError.EmptyPayload)
        }
        // indexOfFirst 未找到时返回 -1（不是 null），必须显式判负——否则 lines[-1] 直接越界
        val headerIndex = findHeaderIndex(lines)
        if (headerIndex < 0) {
            return Failure(ParseError.SchemaMismatch("未识别到表头（未找到包含课程名列的表头行）"))
        }

        val columnByIndex = mapColumns(lines[headerIndex])
        val missing = REQUIRED_COLUMNS.filterNot { column -> columnByIndex.values.contains(column) }
        if (missing.isNotEmpty()) {
            return Failure(ParseError.SchemaMismatch("表头缺少必要列：${missingDescription(missing)}"))
        }

        val dataRows = lines.drop(headerIndex + 1).withIndex()
            .filter { (_, cells) -> cells.any { it.isNotBlank() } }
        if (dataRows.none()) return Failure(ParseError.EmptyPayload)

        val anomalies = mutableListOf<String>()
        val coursesById = LinkedHashMap<String, Course>()
        val sessions = mutableListOf<CourseSession>()
        val now = nowMillis()

        for ((offset, cells) in dataRows) {
            val lineNumber = headerIndex + 1 + offset + 1 // offset 相对 headerIndex+1，再转 1 基文件行号
            when (val row = buildRow(cells, columnByIndex, lineNumber, now, anomalies)) {
                null -> Unit // 脏行：原因已由 buildRow 追加进 anomalies
                else -> {
                    val course = coursesById.getOrPut(row.course.id) { row.course }
                    sessions += row.session.copy(courseId = course.id)
                }
            }
        }

        if (coursesById.isEmpty()) {
            return Failure(ParseError.InvalidData("未解析出任何课程（${anomalies.size} 行全部解析失败）"))
        }
        return Success(
            ParsedSchedule(
                courses = coursesById.values.toList(),
                sessions = sessions.toList(),
                term = null, // 由仓库回退到当前激活学期（规格 §4）
                anomalies = anomalies.toList(),
            ),
        )
    }

    /** 单行解析结果：课程（含稳定 id）+ 该行对应的一条安排。 */
    private data class Row(val course: Course, val session: CourseSession)

    private fun buildRow(
        cells: List<String>,
        columnByIndex: Map<Int, CsvColumn>,
        lineNumber: Int,
        now: Long,
        anomalies: MutableList<String>,
    ): Row? {
        fun cell(column: CsvColumn): String =
            columnByIndex.entries.firstOrNull { it.value == column }?.key?.let { cells.getOrNull(it) }?.trim().orEmpty()

        val name = cell(CsvColumn.NAME)
        val teacher = cell(CsvColumn.TEACHER).takeIf { it.isNotEmpty() }
        if (name.isEmpty()) {
            anomalies += "第${lineNumber}行：课程名为空，已跳过"
            return null
        }
        val day = parseDayOfWeek(cell(CsvColumn.DAY))
        if (day == null) {
            anomalies += "第${lineNumber}行：星期无法识别「${cell(CsvColumn.DAY)}」，已跳过"
            return null
        }
        val startCell = parseSectionCell(cell(CsvColumn.START_SECTION))
        val endCell = parseSectionCell(cell(CsvColumn.END_SECTION))
        val sections = mergeSections(startCell, endCell)
        if (sections == null) {
            anomalies += "第${lineNumber}行：节次无法识别「${cell(CsvColumn.START_SECTION)}~${cell(CsvColumn.END_SECTION)}」，已跳过"
            return null
        }
        val weeks = parseWeekSpec(cell(CsvColumn.WEEKS), cell(CsvColumn.WEEK_TYPE).ifBlank { null })
        if (weeks == null) {
            anomalies += "第${lineNumber}行：周次无法识别「${cell(CsvColumn.WEEKS)}」，已跳过"
            return null
        }
        weeks.anomaly?.let { anomalies += "第${lineNumber}行：$it" }

        val key = "${name.trim()}|${teacher ?: ""}"
        val id = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
        val course = Course(
            id = id,
            name = name.trim(),
            code = null,
            teacher = teacher,
            note = null,
            colorTag = stableColorOf(id),
            source = CourseSource.WAKEUP_CSV,
            createdAt = now,
            updatedAt = now,
            termId = 0L, // 占位：applyImport 会把 termId 钉死到目标学期（见 Mappers）
            editedAt = null,
        )
        val session = CourseSession(
            courseId = id,
            dayOfWeek = day,
            startSection = sections.first,
            endSection = sections.second,
            startWeek = weeks.startWeek,
            endWeek = weeks.endWeek,
            weekType = weeks.weekType,
            classroom = cell(CsvColumn.ROOM).takeIf { it.isNotEmpty() },
        )
        return Row(course, session)
    }

    /** 合并开始/结束节次两个单元格（任一可含区间如 "1-2"），取全部数字的 min/max。 */
    private fun mergeSections(start: Pair<Int, Int>?, end: Pair<Int, Int>?): Pair<Int, Int>? {
        if (start == null && end == null) return null
        val numbers = start?.toList().orEmpty() + end?.toList().orEmpty()
        if (numbers.isEmpty()) return null
        val min = numbers.min()
        val max = numbers.max()
        return if (min < 1 || max < min) null else min to max
    }

    /** 课程颜色：id 哈希取模 12 色板（0..11），同文件重复导入颜色不变。 */
    private fun stableColorOf(id: String): Int = (id.hashCode().mod(COLOR_PALETTE_SIZE))

    /** 找表头行：某行有课程名列且至少还有一个其它必要列（防标题行误判）。返回 -1 = 未找到。 */
    private fun findHeaderIndex(lines: List<List<String>>): Int = lines.indexOfFirst { cells ->
        val recognized = cells.mapNotNull { WakeupHeaderAliases.match(normalizeHeaderCell(it)) }.toSet()
        CsvColumn.NAME in recognized && recognized.any { it != CsvColumn.NAME }
    }

    /** 列语义映射：列下标 → 目标字段（同字段多列时取最左）。 */
    private fun mapColumns(headerCells: List<String>): Map<Int, CsvColumn> {
        val result = LinkedHashMap<Int, CsvColumn>()
        headerCells.forEachIndexed { index, cell ->
            val column = WakeupHeaderAliases.match(normalizeHeaderCell(cell)) ?: return@forEachIndexed
            if (result.values.contains(column).not()) result[index] = column
        }
        return result
    }

    /** 按行切分（处理 \r\n 与引号内逗号），并去掉首行 BOM。 */
    private fun splitLines(input: String): List<List<String>> =
        input.split('\n').mapIndexed { index, line ->
            val cleaned = (if (index == 0) line.removePrefix("\uFEFF") else line).removeSuffix("\r")
            splitCsvLine(cleaned)
        }

    /** 单行 CSV 切分：双引号内的逗号不切分；相邻双引号（""）是转义的双引号。 */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes; i++
                }
                c == ',' && !inQuotes -> {
                    cells += current.toString(); current.clear(); i++
                }
                else -> {
                    current.append(c); i++
                }
            }
        }
        cells += current.toString()
        return cells
    }

    private companion object {
        val REQUIRED_COLUMNS = listOf(
            CsvColumn.NAME, CsvColumn.DAY, CsvColumn.START_SECTION, CsvColumn.END_SECTION, CsvColumn.WEEKS,
        )
        const val COLOR_PALETTE_SIZE = 12
        fun missingDescription(missing: List<CsvColumn>): String = missing.joinToString("、") { it.label() }
        fun CsvColumn.label(): String = when (this) {
            CsvColumn.NAME -> "课程名"; CsvColumn.TEACHER -> "教师"; CsvColumn.ROOM -> "教室"
            CsvColumn.DAY -> "星期"; CsvColumn.START_SECTION -> "开始节次"; CsvColumn.END_SECTION -> "结束节次"
            CsvColumn.WEEKS -> "周次"; CsvColumn.WEEK_TYPE -> "单双周"
        }
    }
}
