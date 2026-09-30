/*
 * SettingsViewModel.kt —— 设置页状态
 *
 * 1. 学期设置：起始日与总周数可编辑（现状是「创建本学期」写死"本周一 + 18 周"，
 *    用户无法校正，而周次判定的正确性完全依赖起始日）；保存走既有 upsertTerm。
 * 2. 精确闹钟授权状态：供界面显示引导 —— 桌面小组件的跨天刷新(4003) 与
 *    课程结束刷新(4002) 都依赖它，未授权时会降级为不精确（最长延后约 1 小时）。
 *
 * M7：课前提醒功能已整体移除 —— 本页不再有提醒开关与提前分钟，
 * 连带删除了 ReminderManager/Scheduler/Notifier 与 ReminderPrefs 偏好层。
 */
package com.gould.xputimetable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    /** 当前激活学期（无学期时各字段为空，保存按钮给出引导）。 */
    val termName: String = "",
    val startDate: String = "",
    val totalWeeksText: String = "",
    /** 精确闹钟授权（Android 12+ 特殊权限）：未授权时小组件刷新降级为不精确。 */
    val exactAlarmAllowed: Boolean = true,
    /** 学期保存成功事件（Snackbar 消费后清除）。 */
    val termSaved: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val repository: TimetableRepository,
    /** 精确闹钟授权检查（小组件刷新精度依赖它）。默认 true 便于测试。 */
    private val canScheduleExact: () -> Boolean = { true },
    /** 学期保存/校正属数据变更（M3 §4.5）：重排小组件闹钟 + 立即刷新小组件。默认空实现便于测试。 */
    private val onTermChanged: suspend () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { repository.observeActiveTerm().first() }
                .onSuccess { term ->
                    _state.value = SettingsUiState(
                        loading = false,
                        termName = term?.name.orEmpty(),
                        startDate = term?.startDate.orEmpty(),
                        totalWeeksText = term?.totalWeeks?.toString().orEmpty(),
                        exactAlarmAllowed = canScheduleExact(),
                    )
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "读取设置失败：${e.message ?: "未知错误"}")
                    }
                }
        }
    }

    /**
     * 从系统设置页返回时刷新授权状态（SettingsScreen ON_RESUME 时调用）。
     * 授权变化后的小组件闹钟重排由 SystemEventReceiver 监听
     * ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED 完成，本方法只更新界面状态。
     */
    fun refreshPermissionState() {
        _state.update { it.copy(exactAlarmAllowed = canScheduleExact()) }
    }

    // ---------- 学期设置 ----------

    fun setStartDate(date: String) = _state.update { it.copy(startDate = date) }

    fun setTotalWeeks(value: String) =
        _state.update { it.copy(totalWeeksText = value.filter { c -> c.isDigit() }.take(2)) }

    fun saveTerm() {
        viewModelScope.launch {
            val s = _state.value
            val weeks = s.totalWeeksText.toIntOrNull()
            if (s.startDate.isBlank() || weeks == null || weeks !in 1..MAX_TOTAL_WEEKS) {
                _state.update {
                    it.copy(error = "请先填写起始日与总周数（1-$MAX_TOTAL_WEEKS 周）")
                }
                return@launch
            }
            runCatching {
                val term = repository.observeActiveTerm().first()
                    ?: error("还没有学期，请先在课表页创建")
                repository.upsertTerm(
                    term.copy(
                        startDate = s.startDate,
                        totalWeeks = weeks,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(termSaved = true, error = null) }
                // 起始日变了 → 周次判定变 → 今日列表也变 → 重排闹钟 + 刷新小组件
                runCatching { onTermChanged() }
            }.onFailure { e ->
                _state.update { it.copy(error = "保存学期失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    // ---------- 事件消费 ----------

    fun consumeError() = _state.update { it.copy(error = null) }

    fun consumeTermSaved() = _state.update { it.copy(termSaved = false) }

    companion object {
        const val MAX_TOTAL_WEEKS = 30
    }
}
