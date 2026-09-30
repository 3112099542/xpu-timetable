/*
 * ScheduleParseResult.kt —— 解析结果与结构化错误（架构 §5.2 原样落地）
 *
 * 作用：解析只有两种结局——成功（带 ParsedSchedule）或失败（带结构化 ParseError）。
 * ParseError 的三种子类各有明确的用户语义，是 AC-11「解析失败提示 + 兜底引导」的数据基础：
 *   - EmptyPayload    空文件/空内容：引导换文件，不吓用户；
 *   - SchemaMismatch  表头完全认不出来：改版/格式不对的信号，引导换通道（手动添加）；
 *   - InvalidData    数据本身矛盾（如全部行都解析不出课程）：说明文件坏了。
 *
 * 说明：ParsedSchedule 的正式定义保留在 domain/model/ImportTypes.kt
 * （已批准的现状偏差：它被 TimetableRepository 接口引用，属领域层类型），此处不另建一份。
 */
package com.gould.xputimetable.parser.api

import com.gould.xputimetable.domain.model.ParsedSchedule

/** 解析结果：成功或结构化失败，无第三态。 */
sealed interface ScheduleParseResult {

    /** 解析成功，携带可直接入库的领域模型。 */
    data class Success(val schedule: ParsedSchedule) : ScheduleParseResult

    /** 解析失败，携带结构化错误（禁止吞成统一文案）。 */
    data class Failure(val error: ParseError) : ScheduleParseResult
}

/** 结构化解析错误：用户提示与日志排查都靠它。 */
sealed interface ParseError {

    /** 空文件 / 空内容（去掉空行后无数据行）。 */
    data object EmptyPayload : ParseError

    /** 表头完全认不出来（学校改版 / 格式不对的信号）。 */
    data class SchemaMismatch(val detail: String) : ParseError

    /** 数据本身矛盾（如周次越界、全部行都解析不出任何课程）。 */
    data class InvalidData(val detail: String) : ParseError
}
