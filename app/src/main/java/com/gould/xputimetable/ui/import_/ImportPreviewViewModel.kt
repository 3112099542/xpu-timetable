/*
 * ImportPreviewViewModel.kt —— 导入预览确认页的状态与入库
 *
 * 两段式导入的后半段（AC-10）：load() 只装配「待确认数据」（计数 + 覆盖范围 +
 * 疑似重复），confirm() 才真正走 importer.commit → applyImport 入库。
 *
 * 覆盖范围（AC-13）与疑似重复（AC-21）在确认**之前**从仓库查询：
 *   - existingCount = 当前学期该来源的现有课程数（覆盖模式=将被替换的门数；插入模式=保留的门数）；
 *   - suspectedDuplicates = 导入课程与现有 MANUAL 课程同名者（只提示，不自动合并）。
 *
 * M7 需求 1：入库方式由用户在本页二选一（[ImportMode]），load() 重置为默认的 REPLACE。
 *
 * 无学期时禁止确认（applyImport 无法确定目标学期），给出明确引导而不是让仓库抛异常。
 */
package com.gould.xputimetable.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.ImportMode
import com.gould.xputimetable.domain.model.ParsedSchedule
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ScheduleImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportPreviewUiState(
    val loading: Boolean = true,
    val courseCount: Int = 0,
    val sessionCount: Int = 0,
    val anomalies: List<String> = emptyList(),
    /**
     * AC-13 / M7 需求 1：当前学期**全部非手动来源**的课程门数。
     * 覆盖模式=将被清空的规模；插入模式=将被保留的导入课程规模。
     */
    val existingCount: Int = 0,
    /** 覆盖范围所属来源的显示名（教务直连 / WakeUp 导入），M2-B 起两通道共用本页。 */
    val sourceLabel: String = "",
    /**
     * M7 需求 1：入库方式。REPLACE=覆盖原课表（默认），APPEND=插入原课表。
     * 每次 load() 重置为默认，用户可在本页切换。
     */
    val mode: ImportMode = ImportMode.REPLACE,
    /** AC-21：与手动课程同名的导入课程名（只提示不合并）。 */
    val suspectedDuplicates: List<String> = emptyList(),
    /** 没有激活学期：无法导入，只能返回创建学期。 */
    val noTerm: Boolean = false,
    val committing: Boolean = false,
    /** 入库成功：界面观察后返回周视图。 */
    val committed: Boolean = false,
    val error: String? = null,
)

class ImportPreviewViewModel(
    private val importer: ScheduleImporter,
    private val repository: TimetableRepository,
    /** 保存/删除属数据变更（M3 §4.5）：重排小组件闹钟 + 立即刷新桌面小组件。失败不影响主流程。默认空实现便于测试。 */
    private val onDataChanged: suspend () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(ImportPreviewUiState())
    val state: StateFlow<ImportPreviewUiState> = _state.asStateFlow()

    private var parsed: ParsedSchedule? = null

    /**
     * 装配待确认数据（每次进入预览页调用；全量重置，支持同一 VM 二次导入）。
     *
     * [source]：本批数据的来源（WEB / WAKEUP_CSV，Spec §4.5 预览页泛化）——
     * 覆盖范围查询按它进行，使「将替换 N 门同来源课程」对两条通道都成立。
     */
    fun load(needsConfirm: ImportResult.NeedsConfirm, source: String) {
        parsed = needsConfirm.parsed
        _state.value = ImportPreviewUiState(
            loading = true,
            courseCount = needsConfirm.courseCount,
            sessionCount = needsConfirm.sessionCount,
            anomalies = needsConfirm.anomalies,
            sourceLabel = source.toSourceLabel(),
        )
        viewModelScope.launch {
            runCatching {
                val term = repository.observeActiveTerm().first()
                    ?: return@runCatching null
                // M7 需求 1：覆盖模式的替换范围是"本学期全部非手动来源"，
                // 所以要跨来源求和——只统计本次通道的来源会低报覆盖范围。
                val existingCount = IMPORTED_SOURCES.sumOf { src ->
                    repository.getCoursesByTermAndSource(term.id, src).size
                }
                val manualNames = repository
                    .getCoursesByTermAndSource(term.id, CourseSource.MANUAL)
                    .map { it.name }.toSet()
                existingCount to manualNames
            }.onSuccess { data ->
                if (data == null) {
                    _state.update { it.copy(loading = false, noTerm = true) }
                } else {
                    val (existingCount, manualNames) = data
                    val duplicates = parsed?.courses
                        ?.map { it.name }
                        ?.filter { it in manualNames }
                        ?.distinct()
                        .orEmpty()
                    _state.update {
                        it.copy(loading = false, existingCount = existingCount, suspectedDuplicates = duplicates)
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = "读取当前课表失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /** M7 需求 1：切换入库方式（覆盖原课表 / 插入原课表）。入库进行中不接受切换。 */
    fun setMode(mode: ImportMode) {
        if (_state.value.committing) return
        _state.update { it.copy(mode = mode) }
    }

    /** 用户点「确认导入」：唯一入库动作（AC-10）。 */
    fun confirm() {
        val schedule = parsed ?: return
        val mode = _state.value.mode
        if (_state.value.committing) return
        _state.update { it.copy(committing = true, error = null) }
        viewModelScope.launch {
            runCatching { importer.commit(schedule, mode) }
                .onSuccess { summary ->
                    if (summary.success) {
                        _state.update { it.copy(committing = false, committed = true) }
                        runCatching { onDataChanged() } // 数据变更：重排闹钟 + 刷新桌面小组件
                    } else {
                        _state.update { it.copy(committing = false, error = "导入未写入任何课程：${summary.message}") }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(committing = false, error = "导入失败：${e.message ?: e::class.simpleName ?: "未知错误"}")
                    }
                }
        }
    }

    /** 来源常量 → 用户可读的来源名（覆盖范围卡与摘要文案用；M6 增 FILE_JSON）。 */
    private fun String.toSourceLabel(): String = when (this) {
        CourseSource.WEB -> "教务导入"
        CourseSource.FILE_JSON -> "文件导入"
        else -> "WakeUp 导入"
    }
}

/**
 * 非手动来源的封闭集合 = 「覆盖原课表」模式的替换范围（M7 需求 1）。
 * **新增导入通道时必须同步登记**，否则覆盖模式会漏清该通道的旧数据，导致跨通道重复。
 */
private val IMPORTED_SOURCES = listOf(
    CourseSource.WEB,
    CourseSource.WAKEUP_CSV,
    CourseSource.FILE_JSON,
)
