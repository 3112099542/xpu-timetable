/*
 * XpuWebImporter.kt —— 教务直连通道的编排层（两段式导入，AC-10）
 *
 * 与 JsonFileImporter 同构：import() 只解析不写库（预览确认前零写入），
 * commit() 才走 repository.applyImport 同一入口（按来源替换 + MANUAL 双防线，AC-20）。
 *
 * semesterId 的来源：拦截到的 print-data URL（ImportPayload.uri 携带），
 * 由 XpuEndpoints.semesterIdOf 正则提取；URL 缺失/不匹配时回退 "0"
 * （仅影响稳定 id 的种子，不影响解析正确性）。
 */
package com.gould.xputimetable.importer.xpu

import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ImportSource
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.parser.api.ParseError
import com.gould.xputimetable.parser.api.ScheduleParseResult
import com.gould.xputimetable.parser.xpu.XpuEndpoints
import com.gould.xputimetable.parser.xpu.XpuJsonParser

class XpuWebImporter(
    private val repository: TimetableRepository,
) : ScheduleImporter {

    override val source: ImportSource = ImportSource.WEB

    override suspend fun import(payload: ImportPayload): ImportResult {
        val text = payload.text
        if (text.isNullOrBlank()) {
            return ImportResult.Failure(ParseError.EmptyPayload, payload)
        }
        val semesterId = payload.uri?.let(XpuEndpoints::semesterIdOf) ?: "0"
        return when (val result = XpuJsonParser(semesterId).parse(text)) {
            is ScheduleParseResult.Success -> ImportResult.NeedsConfirm(
                parsed = result.schedule,
                courseCount = result.schedule.courses.size,
                sessionCount = result.schedule.sessions.size,
                anomalies = result.schedule.anomalies,
            )
            is ScheduleParseResult.Failure -> ImportResult.Failure(result.error, payload)
        }
    }

    override suspend fun commit(parsed: ParsedSchedule, mode: ImportMode): ImportSummary =
        repository.applyImport(parsed, mode)
}
