/*
 * WakeupFieldParsers.kt —— WakeUp CSV 的字段级解析纯函数（供 WakeupCsvParser 组合）
 *
 * 为什么独立成文件（架构树只列了 WakeupCsvParser.kt，此为拆分）：
 * 架构 §6.1 硬规则 1「单文件不超过 300 行」优先于目录树形态；表头别名匹配与
 * 周次/星期/节次的容错文法是可独立单测的纯函数子职责，拆开后两个文件各自聚焦。
 *
 * 全部函数无状态、不抛异常：解析不出返回 null，由调用方决定跳行进 anomalies。
 * 周次判定复用领域层 WeekCalc 的口径（ODD/EVEN/ALL 语义一致），不另写第二份。
 */
package com.gould.xputimetable.parser.wakeup

import com.gould.xputimetable.domain.model.WeekType

/** 解析出的周次规格（闭区间 + 单双周 + 可选的文法说明）。 */
internal data class WeekSpec(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    /** 文法损失说明（如逗号列表被放宽为区间），非空时由调用方追加进 anomalies。 */
    val anomaly: String? = null,
)

/** CSV 列语义：按表头关键词识别的目标字段。 */
internal enum class CsvColumn { NAME, TEACHER, ROOM, DAY, START_SECTION, END_SECTION, WEEKS, WEEK_TYPE }

/** 表头关键词别名表（忽略大小写与所有空白字符；含其一即命中）。 */
internal object WakeupHeaderAliases {
    val keywords: Map<CsvColumn, List<String>> = mapOf(
        CsvColumn.NAME to listOf("课程名", "课程", "名称", "课名", "course", "name"),
        CsvColumn.TEACHER to listOf("教师", "老师", "授课教师", "teacher"),
        CsvColumn.ROOM to listOf("教室", "地点", "上课地点", "位置", "room", "place"),
        CsvColumn.DAY to listOf("星期", "周几", "day", "weekday"),
        CsvColumn.START_SECTION to listOf("开始节次", "起始节次", "开始", "start"),
        CsvColumn.END_SECTION to listOf("结束节次", "结束", "end"),
        CsvColumn.WEEKS to listOf("周次", "上课周", "weeks"),
        CsvColumn.WEEK_TYPE to listOf("单双周", "单周", "双周", "weektype"),
    )

    /** 单元格文本（规范化后）命中哪一列；未命中返回 null。 */
    fun match(normalizedCell: String): CsvColumn? =
        keywords.entries.firstOrNull { (_, words) -> words.any { normalizedCell.contains(it) } }?.key
}

/** 规范化表头单元格：去 BOM、去所有空白、转小写。 */
internal fun normalizeHeaderCell(raw: String): String =
    raw.replace("\uFEFF", "").filterNot { it.isWhitespace() }.lowercase()

/**
 * 解析周次字符串（容错文法，Spec M2-A §4 的技术核心）。
 * 兼容：1-16 / 1-16周 / 1-16(周) / 1,3,5,7,9 / 1-16单 / 单周 / 双周 / 1-16 单周。
 *
 * 规则：区间 → 原样；纯数字 → 单周；逗号列表 → 能连成连续区间则区间化（无异常），
 * 否则取 min/max 为区间且单双周放宽为 ALL（记 anomaly，宁可多显不可漏课）；
 * 含"单" → ODD，含"双" → EVEN；裸"单周/双周"（无数字）→ 默认学期区间 1..18。
 * 周次字符串里的单/双标记优先于独立的单双周列（显式写在周次上更具体）。
 */
internal fun parseWeekSpec(raw: String, weekTypeColumn: String?): WeekSpec? {
    val text = raw.trim()
    val column = weekTypeColumn?.trim().orEmpty()
    if (text.isEmpty() && column.isEmpty()) return null

    // 单双周：优先周次字符串里的显式标记，其次独立单双周列（含字判定，兼容"单周/双周/每单周"）
    val inlineType = when {
        text.contains("单") -> WeekType.ODD
        text.contains("双") -> WeekType.EVEN
        else -> null
    }
    val columnType = when {
        column.contains("单") -> WeekType.ODD
        column.contains("双") -> WeekType.EVEN
        else -> null
    }
    val weekType = inlineType ?: columnType ?: WeekType.ALL

    // 只留数字、逗号与连字符，再去掉中文单位与全半角括号；全角逗号先归一为半角。
    // 数字提取不带符号（"-"只当区间分隔符）：带 "-?" 会把 "1-16" 拆成 1 与 -16。
    val digitsOnly = text.replace('，', ',').filter { it.isDigit() || it == ',' || it == '-' }
    val numbers = Regex("\\d+").findAll(digitsOnly).map { it.value.toInt() }.toList()

    if (numbers.isEmpty()) {
        // 无数字时的容错边界（防脏行漏网）：
        //   - 周次本身是语义词（每周/单周/双周）→ 默认学期区间（与 App 默认 18 周一致）；
        //   - 周次为空、由单双周列补语义词 → 同上；
        //   - 周次是任意其它字符串（如 "abc"）→ **判脏返回 null**，单双周列救不了它。
        return when {
            inlineType != null -> WeekSpec(DEFAULT_MIN_WEEK, DEFAULT_MAX_WEEK, weekType)
            text in KNOWN_ALL_WEEK_WORDS -> WeekSpec(DEFAULT_MIN_WEEK, DEFAULT_MAX_WEEK, weekType)
            text.isEmpty() && (columnType != null || column in KNOWN_ALL_WEEK_WORDS) ->
                WeekSpec(DEFAULT_MIN_WEEK, DEFAULT_MAX_WEEK, weekType)
            else -> null
        }
    }

    val hasComma = digitsOnly.contains(',')
    return if (hasComma) {
        val sorted = numbers.distinct().sorted()
        val min = sorted.first()
        val max = sorted.last()
        val consecutive = sorted.zipWithNext().all { (a, b) -> b - a == 1 }
        if (consecutive && sorted.size > 1 || sorted.size == 1) {
            WeekSpec(min, max, weekType)
        } else {
            // 无法精确表示为单一区间：放宽为 min-max + ALL（覆盖所有提到的周，绝不漏课）
            WeekSpec(min, max, WeekType.ALL, anomaly = "周次「$text」无法精确表示，已按第 $min-$max 周处理")
        }
    } else {
        val interval = Regex("(\\d+)\\s*-\\s*(\\d+)").find(text)
        if (interval != null) {
            val start = interval.groupValues[1].toInt()
            val end = interval.groupValues[2].toInt()
            if (start < 1 || end < start) return null
            WeekSpec(start, end, weekType)
        } else {
            val n = numbers.first()
            if (n < 1) return null
            WeekSpec(n, n, weekType)
        }
    }
}

private const val DEFAULT_MIN_WEEK = 1
private const val DEFAULT_MAX_WEEK = 18

/** 无数字周次的已知"每周都上"语义词（其余无数字串一律判脏，不静默接受）。 */
private val KNOWN_ALL_WEEK_WORDS = setOf("每周", "每週", "全部", "所有", "all", "every")

/**
 * 解析星期：星期一/周一/一/1/Monday → 1..7；认不出返回 null。
 */
internal fun parseDayOfWeek(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val digits = text.filter { it.isDigit() }
    if (digits.isNotEmpty()) return digits.toIntOrNull()?.takeIf { it in 1..7 }
    val chinese = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
    chinese.firstOrNull { text.contains(it.first) }?.let { return it.second }
    val english = listOf("monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7)
    val lower = text.lowercase()
    english.firstOrNull { lower.contains(it.first) }?.let { return it.second }
    return null
}

/**
 * 解析节次单元格：兼容"2"（单节）与"1-2"（区间），支持"第1-2节"这类装饰；
 * 返回 (min, max)；认不出返回 null。
 */
internal fun parseSectionCell(raw: String): Pair<Int, Int>? {
    val numbers = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
    if (numbers.isEmpty()) return null
    val start = numbers.min()
    val end = numbers.max()
    if (start < 1 || end < start) return null
    return start to end
}
