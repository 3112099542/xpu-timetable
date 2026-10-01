/*
 * JsonFileImporter.kt —— 本 App 导出的课表文件（.json）导入通道（M6 需求 6-A）
 *
 * 与 XpuWebImporter 同形态：本类不含 JSON 文法细节（那是 ScheduleCodec 的职责），
 * 只做编排——import() 只解析不写库（AC-10），commit() 走仓库统一入口 applyImport
 * （按来源整体替换、MANUAL 保护等语义全部由仓库保证，本通道不自带第二套）。
 *
 * source 必须是独立的 FILE_JSON：applyImport 按「学期 + 来源」整体替换，若复用
 * WEB / WAKEUP_CSV 会把对应通道导入的课表整体删掉（数据丢失路径，规格明令避免）。
 *
 * 不感知二维码：二维码「解图 → 还原成 JSON 文本」全部发生在 UI 层，本通道只收文本载荷。
 */
package com.gould.xputimetable.importer.file

import com.gould.xputimetable.data.transfer.DecodeResult
import com.gould.xputimetable.data.transfer.ScheduleCodec
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ImportSource
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.parser.api.ParseError
import kotlinx.coroutines.flow.first

class JsonFileImporter(
    private val repository: TimetableRepository,
) : ScheduleImporter {

    override val source: ImportSource = ImportSource.FILE_JSON

    override suspend fun import(payload: ImportPayload): ImportResult {
        val text = payload.text
        if (text.isNullOrBlank()) {
            return ImportResult.Failure(ParseError.EmptyPayload, payload)
        }
        // 取激活学期做 term 一致性判定：一致 → 填激活学期（覆盖更新）；
        // 不一致 → term 为 null（applyImport 回退激活学期），两种情况都在 anomalies 给提示
        val activeTerm = runCatching { repository.observeActiveTerm().first() }.getOrNull()
        return when (val r = ScheduleCodec.decodeToParsedSchedule(text, activeTerm)) {
            is DecodeResult.Success -> ImportResult.NeedsConfirm(
                parsed = r.value,
                courseCount = r.value.courses.size,
                sessionCount = r.value.sessions.size,
                anomalies = r.value.anomalies,
            )
            is DecodeResult.Failure -> ImportResult.Failure(r.error, payload)
        }
    }

    override suspend fun commit(parsed: ParsedSchedule, mode: ImportMode): ImportSummary =
        repository.applyImport(parsed, mode)
}
