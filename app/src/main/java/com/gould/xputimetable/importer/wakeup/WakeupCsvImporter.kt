/*
 * WakeupCsvImporter.kt —— WakeUp CSV 文件导入通道（importer/wakeup）
 *
 * 作用：把「CSV 文本 → 解析 → 待确认 → 确认入库」串起来。本类不含 CSV 文法细节
 * （那是 WakeupCsvParser 的职责），只做编排：
 *   - import()：调 parser，产出 NeedsConfirm（**不写库**，AC-10）或 Failure（保留载荷，AC-11）；
 *   - commit()：走 TimetableRepository.applyImport 统一入口入库（按来源整体替换、
 *     MANUAL 不删、返回摘要——AC-13/20/21 语义全部由仓库保证，本通道不自带第二套）。
 *
 * suspend 的意义：import 的解析可能发生在调用方的任意调度器；commit 是真 IO（Room）。
 */
package com.gould.xputimetable.importer.wakeup

import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ImportSource
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.PayloadParser
import com.gould.xputimetable.parser.api.ScheduleParseResult

class WakeupCsvImporter(
    private val parser: PayloadParser<String>,
    private val repository: TimetableRepository,
) : ScheduleImporter {

    override val source: ImportSource = ImportSource.WAKEUP_CSV

    override suspend fun import(payload: ImportPayload): ImportResult {
        val text = payload.text
        if (text.isNullOrBlank()) {
            return ImportResult.Failure(ParseError.EmptyPayload, payload)
        }
        return when (val parsed = parser.parse(text)) {
            is ScheduleParseResult.Success -> ImportResult.NeedsConfirm(
                parsed = parsed.schedule,
                courseCount = parsed.schedule.courses.size,
                sessionCount = parsed.schedule.sessions.size,
                anomalies = parsed.schedule.anomalies,
            )
            is ScheduleParseResult.Failure -> ImportResult.Failure(parsed.error, payload)
        }
    }

    override suspend fun commit(parsed: ParsedSchedule, mode: ImportMode): ImportSummary =
        repository.applyImport(parsed, mode)
}
