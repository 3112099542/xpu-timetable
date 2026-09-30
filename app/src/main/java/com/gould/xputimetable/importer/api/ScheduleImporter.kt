/*
 * ScheduleImporter.kt —— 导入通道统一契约（importer/api）
 *
 * 作用（架构 §5.4）：所有通道（WebView 捕获、WakeUp CSV、手动编辑）最终都汇入
 * TimetableRepository.applyImport(ParsedSchedule) 同一入口入库。仓库不关心数据从哪来，
 * 这保证兜底通道与主通道共享全部去重/合并/学期切换逻辑，不会长出两套入库代码。
 *
 * 两段式（Spec M2-A 批准的补充，落实 AC-10）：
 *   1. import(payload)：只解析，返回 NeedsConfirm（不写库）或 Failure；
 *   2. commit(parsed)：用户在预览页确认后调用，走仓库统一入口入库，
 *      返回 ImportSummary（含 replacedCount / preservedManualCount / suspectedDuplicateNames，
 *      即 AC-13 / AC-20 / AC-21 的机器证据）。
 */
package com.gould.xputimetable.importer.api

import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ImportSummary
import com.gould.xputimetable.domain.model.ParsedSchedule

interface ScheduleImporter {

    /** 本通道的来源标识。 */
    val source: ImportSource

    /** 解析原始载荷：成功返回待确认结果（不写库），失败返回原因与保留的载荷。 */
    suspend fun import(payload: ImportPayload): ImportResult

    /**
     * 预览页确认后调用：走仓库统一入口入库，返回导入摘要。
     *
     * [mode] 由预览页的用户选择传入（M7 需求 1）：「覆盖原课表」或「插入原课表」。
     * 默认 REPLACE，与既有调用点语义保持一致。
     */
    suspend fun commit(
        parsed: ParsedSchedule,
        mode: ImportMode = ImportMode.REPLACE,
    ): ImportSummary
}
