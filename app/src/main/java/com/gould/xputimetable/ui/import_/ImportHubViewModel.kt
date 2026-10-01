/*
 * ImportHubViewModel.kt —— 导入中心的状态与文件解析编排
 *
 * 两段式导入的前半段（AC-10）：submitJson 只做「读取 → 解析」，产出
 * pending（NeedsConfirm，待预览页确认）或 failure（AC-11：结构化原因 + 已选文件
 * 不丢失——retainedPayload 留在本 VM，可原地重试，无需重新选文件）。
 *
 * M9：**WakeUp CSV 通道已删除**（产品负责人要求）。文件类导入只剩本 App 导出的
 * JSON / 二维码截图一条通道（二维码截图也是解出 JSON 文本后走同一入口），
 * 因此 VM 不再需要"按通道分派 importer"。
 *
 * 健壮性约定：importer/import 的任何异常都转为 failure 文案，绝不冒泡到
 * viewModelScope（那会崩溃）。parsing=true 时忽略新的提交，防连点。
 */
package com.gould.xputimetable.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportPayload
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.parser.api.ParseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 解析失败的展示态：原因 + 已选文件名（保留展示，AC-11）。 */
data class ImportFailureUi(
    val reason: String,
    val fileName: String?,
    /** retainedPayload 还在（文件读取成功但解析失败）时允许原地重试。 */
    val canRetry: Boolean,
)

data class ImportHubUiState(
    val term: Term? = null,
    val parsing: Boolean = false,
    val failure: ImportFailureUi? = null,
    /** 解析成功待确认：界面观察后导航到预览页并 consumePending。 */
    val pending: ImportResult.NeedsConfirm? = null,
)

class ImportHubViewModel(
    /** 文件类导入通道（M9 起唯一：本 App 导出的 JSON；二维码截图解出的文本也走这里）。 */
    private val jsonFileImporter: ScheduleImporter,
    private val repository: TimetableRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportHubUiState())
    val state: StateFlow<ImportHubUiState> = _state.asStateFlow()

    /** AC-11：失败不丢已选文件，保留原始载荷供重试。 */
    private var retainedPayload: ImportPayload? = null

    init {
        // 手动添加入口需要当前学期信息（EditTarget.New 的默认周次范围）
        viewModelScope.launch {
            runCatching { repository.observeActiveTerm().first() }
                .onSuccess { term -> _state.update { it.copy(term = term) } }
        }
    }

    /** 用户选好文件（或二维码截图解出文本）后由界面调用，payload 由 UI 层装好。 */
    fun submitJson(payload: ImportPayload) {
        if (_state.value.parsing) return
        retainedPayload = payload
        _state.update { it.copy(parsing = true, failure = null, pending = null) }
        viewModelScope.launch {
            val result = runCatching { jsonFileImporter.import(payload) }
            result.onSuccess { r ->
                when (r) {
                    is ImportResult.NeedsConfirm ->
                        _state.update { it.copy(parsing = false, pending = r) }
                    is ImportResult.Failure ->
                        _state.update { it.copy(parsing = false, failure = r.toUi()) }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        parsing = false,
                        failure = ImportFailureUi(
                            reason = "解析失败：${e.message ?: e::class.simpleName ?: "未知错误"}",
                            fileName = payload.displayName,
                            canRetry = true,
                        ),
                    )
                }
            }
        }
    }

    /** 文件读取本身失败（文件被移走/无权限）：无法重试，只能换文件。 */
    fun markReadFailure(fileName: String?) {
        retainedPayload = null
        _state.update {
            it.copy(
                parsing = false,
                failure = ImportFailureUi(
                    reason = "无法读取所选文件（可能已被移动、删除或无访问权限）",
                    fileName = fileName,
                    canRetry = false,
                ),
            )
        }
    }

    /** 原地重试：用保留的载荷再解析一次（不丢已选文件）。 */
    fun retry() {
        retainedPayload?.let { submitJson(it) }
    }

    /** 界面已消费 pending（已导航到预览页）。 */
    fun consumePending() = _state.update { it.copy(pending = null) }
}

/** ParseError → 用户可读的结构化原因（AC-11：展示「解析失败：<原因>」）。 */
private fun ImportResult.Failure.toUi(): ImportFailureUi = ImportFailureUi(
    reason = when (val e: ParseError = error) {
        is ParseError.EmptyPayload -> "解析失败：文件内容为空"
        // M9：WakeUp 通道删除后，文件类只可能是本 App 导出的课表，提示相应收窄
        is ParseError.SchemaMismatch ->
            "解析失败：文件格式无法识别（${e.detail}），请确认是本 App 导出的课表文件或二维码截图"
        is ParseError.InvalidData -> "解析失败：${e.detail}"
    },
    fileName = retainedDisplayName(),
    canRetry = true,
)

private fun ImportResult.Failure.retainedDisplayName(): String? = retainedPayload.displayName
