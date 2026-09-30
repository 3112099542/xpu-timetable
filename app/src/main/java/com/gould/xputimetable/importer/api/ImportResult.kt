/*
 * ImportResult.kt —— 导入通道的两段式结果（importer/api）
 *
 * 作用：落实 AC-10「确认后才写入」。import() 只解析并返回**待确认**的结果，
 * 真正入库必须等用户在预览页点「确认导入」后走 commit()。
 * 因此结果只有两种：
 *   - NeedsConfirm：解析成功，等待确认（携带课程/安排计数与异常条目说明）；
 *   - Failure：解析失败（携带结构化原因 + 保留的原始载荷，见 AC-11）。
 *
 * 注意：不存在"导入成功"态——成功与否由 commit() 返回的 ImportSummary 描述。
 */
package com.gould.xputimetable.importer.api

import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.parser.api.ParseError

sealed interface ImportResult {

    /** 解析成功，等待用户在预览页确认。 */
    data class NeedsConfirm(
        val parsed: ParsedSchedule,
        val courseCount: Int,
        val sessionCount: Int,
        /** 被跳过的异常条目说明（如「第3行：星期无法识别」），供预览页如实展示。 */
        val anomalies: List<String>,
    ) : ImportResult

    /**
     * 解析失败。
     *
     * @param error          结构化原因（EmptyPayload / SchemaMismatch / InvalidData）
     * @param retainedPayload 保留的原始载荷——预览失败态据此「换文件 / 重试」，
     *                         不丢失已拦截数据（AC-11）
     */
    data class Failure(val error: ParseError, val retainedPayload: ImportPayload) : ImportResult
}
