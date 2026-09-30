/*
 * CleanupViewModel.kt —— 按来源清理的状态机（M2-C 任务 2，AC-24）
 *
 * 列出当前激活学期各来源（教务导入 / 文件导入）的课程数；
 * 选择来源 → 二次确认 → deleteCoursesByTermAndSource（MANUAL 永不删除，
 * 代码 + SQL 双防线）→ cleanedCount 事件由界面消费（Snackbar 报「已清理 N 门」）。
 */
package com.gould.xputimetable.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CleanupUiState(
    val loading: Boolean = true,
    /** 当前激活学期的各来源课程数。 */
    val webCount: Int = 0,
    val csvCount: Int = 0,
    /** 待二次确认的来源（非空时显示确认弹窗）。 */
    val confirmSource: String? = null,
    val cleaning: Boolean = false,
    /** 清理完成事件：界面消费（Snackbar）后置空。 */
    val cleanedCount: Int? = null,
)

class CleanupViewModel(
    private val repository: TimetableRepository,
    /** 保存/删除属数据变更（M3 §4.5）：重排小组件闹钟 + 立即刷新桌面小组件。失败不影响主流程。默认空实现便于测试。 */
    private val onDataChanged: suspend () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(CleanupUiState())
    val state: StateFlow<CleanupUiState> = _state.asStateFlow()

    init {
        loadCounts()
    }

    fun loadCounts() {
        viewModelScope.launch {
            runCatching {
                val term = repository.observeActiveTerm().first()
                if (term == null) {
                    null
                } else {
                    termId = term.id
                    repository.getCoursesByTermAndSource(term.id, CourseSource.WEB).size to
                        repository.getCoursesByTermAndSource(term.id, CourseSource.WAKEUP_CSV).size
                }
            }.onSuccess { counts ->
                _state.update {
                    if (counts == null) {
                        it.copy(loading = false)
                    } else {
                        it.copy(loading = false, webCount = counts.first, csvCount = counts.second)
                    }
                }
            }.onFailure {
                _state.update { s -> s.copy(loading = false) }
            }
        }
    }

    /** 选择来源 → 请求二次确认。 */
    fun requestClean(source: String) {
        if (_state.value.cleaning) return
        _state.update { it.copy(confirmSource = source) }
    }

    fun dismissConfirm() {
        if (_state.value.cleaning) return
        _state.update { it.copy(confirmSource = null) }
    }

    /** 二次确认后执行（MANUAL 在仓库层被双防线拒绝，到不了这里也删不掉）。 */
    fun confirmClean() {
        val source = _state.value.confirmSource ?: return
        _state.update { it.copy(cleaning = true) }
        viewModelScope.launch {
            runCatching { repository.deleteCoursesByTermAndSource(termId, source) }
                .onSuccess { count ->
                    _state.update {
                        it.copy(
                            cleaning = false,
                            confirmSource = null,
                            cleanedCount = count,
                            webCount = if (source == CourseSource.WEB) 0 else it.webCount,
                            csvCount = if (source == CourseSource.WAKEUP_CSV) 0 else it.csvCount,
                        )
                    }
                    runCatching { onDataChanged() } // 数据变更：重排闹钟 + 刷新桌面小组件
                }
                .onFailure {
                    _state.update { s -> s.copy(cleaning = false, confirmSource = null) }
                }
        }
    }

    /** 界面已消费清理完成事件。 */
    fun consumeCleaned() = _state.update { it.copy(cleanedCount = null) }

    private var termId: Long = 0L
}
