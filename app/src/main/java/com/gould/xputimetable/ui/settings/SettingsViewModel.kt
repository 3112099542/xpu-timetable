/*
 * SettingsViewModel.kt —— 「我的」页与「学期设置」页的状态（M9 重构）
 *
 * M9 变更（产品负责人要求）：
 *   1. 学期与总周数移入二级页「学期设置」；
 *   2. **去掉手动保存按钮** —— 选起始日 / 调总周数后自动保存（连点去抖，只写最后一次）；
 *   3. 「创建本学期」不再写死"本周一 + 18 周"：进入本页时用户先选起始日，
 *      选中即创建（起始日决定周次判定，必须由用户定）。
 *
 * 精确闹钟授权状态仍在此 VM（桌面小组件刷新精度依赖它；未授权时降级为不精确）。
 *
 * M7：课前提醒功能已整体移除 —— 本页不再有提醒开关与提前分钟。
 */
package com.gould.xputimetable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.domain.model.Term
import com.gould.xputimetable.domain.repository.TimetableRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 总周数的取值范围与默认值（顶层常量：同一文件的顶层 data class 也要用，
 *  放在 companion 里顶层声明处不可见）。 */
internal const val MIN_TOTAL_WEEKS = 1
internal const val MAX_TOTAL_WEEKS = 30
private const val DEFAULT_TOTAL_WEEKS = 18

data class SettingsUiState(
    val loading: Boolean = true,
    /** 是否已有激活学期：决定学期设置页是「创建」语义还是「编辑」语义。 */
    val hasTerm: Boolean = false,
    val termName: String = "",
    /** 起始日（ISO yyyy-MM-dd）；未设置时为空串。 */
    val startDate: String = "",
    val totalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    /** 精确闹钟授权（Android 12+ 特殊权限）：未授权时小组件刷新降级为不精确。 */
    val exactAlarmAllowed: Boolean = true,
    /** 每次自动保存成功自增：界面据此短暂显示「已自动保存」（不弹提示，避免打扰）。 */
    val savedTick: Int = 0,
    /** 失败等需要用户知道的错误（由界面转成底部提示）。 */
    val error: String? = null,
)

class SettingsViewModel(
    private val repository: TimetableRepository,
    /** 精确闹钟授权检查（小组件刷新精度依赖它）。默认 true 便于测试。 */
    private val canScheduleExact: () -> Boolean = { true },
    /** 学期保存/校正属数据变更（M3 §4.5）：重排小组件闹钟 + 立即刷新小组件。默认空实现便于测试。 */
    private val onTermChanged: suspend () -> Unit = {},
    /** M11：「显示老师姓名」初始值（UniPrefs 有值时由设置页写入的开关驱动）。默认开。 */
    showTeacherFlow: Flow<Boolean> = flowOf(true),
    /** M11：「显示老师姓名」写入通道。默认空实现便于测试（测试不落盘）。 */
    private val saveShowTeacher: suspend (Boolean) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** 连点步进器时只写最后一次（去抖）。 */
    private var autoSaveJob: Job? = null

    /**
     * 「显示老师姓名」当前值（课程卡据此显示/隐藏教师行）。
     *
     * 与 TimetableViewModel 一样用 [SharingStarted.Eagerly]：开关必须当场响应，
     * 不能等到有人订阅时才补发（见那边对 WhileSubscribed 竞态的说明）。
     */
    val showTeacher: StateFlow<Boolean> = showTeacherFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * 切换「显示老师姓名」（M11）。
     *
     * 写入失败只提示不冒泡：这是界面偏好，不该把设置页整个搞崩；
     * 上层 SettingsScreen 已接了全局底部提示，这里不重复弹。
     */
    fun toggleShowTeacher(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { saveShowTeacher(enabled) }
                .onFailure { _state.update { it.copy(error = "设置未保存：${it.error ?: "写入失败"}") } }
        }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { repository.observeActiveTerm().first() }
                .onSuccess { term ->
                    _state.value = SettingsUiState(
                        loading = false,
                        hasTerm = term != null,
                        termName = term?.name.orEmpty(),
                        startDate = term?.startDate.orEmpty(),
                        totalWeeks = term?.totalWeeks ?: DEFAULT_TOTAL_WEEKS,
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
     * 从系统设置页返回时刷新授权状态（界面 ON_RESUME 时调用）。
     * 授权变化后的小组件闹钟重排由 SystemEventReceiver 监听
     * ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED 完成，本方法只更新界面状态。
     */
    fun refreshPermissionState() {
        _state.update { it.copy(exactAlarmAllowed = canScheduleExact()) }
    }

    // ---------- 学期（自动保存） ----------

    /** 选好起始日：写状态并触发自动保存；没有学期时这一步即「创建」。 */
    fun selectStartDate(date: String) {
        _state.update { it.copy(startDate = date) }
        scheduleAutoSave()
    }

    /** 总周数步进：越界自动钳制，随后自动保存。 */
    fun adjustWeeks(delta: Int) {
        _state.update {
            it.copy(totalWeeks = (it.totalWeeks + delta).coerceIn(MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS))
        }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS)
            persist()
        }
    }

    private suspend fun persist() {
        val s = _state.value
        if (s.startDate.isBlank()) return // 起始日未选：不写库（创建语义下用户还没做完选择）
        val weeks = s.totalWeeks.coerceIn(MIN_TOTAL_WEEKS, MAX_TOTAL_WEEKS)
        // 创建分支算出的学期名要回写进状态：否则「我的」页的摘要行会缺学期名（留下一个多余分隔符）
        var createdName: String? = null
        runCatching {
            val existing = repository.observeActiveTerm().first()
            val now = System.currentTimeMillis()
            if (existing == null) {
                val start = LocalDate.parse(s.startDate)
                createdName = WeekCalc.termNameOf(start)
                repository.upsertTerm(
                    Term(
                        id = 0L,
                        name = createdName,
                        startDate = s.startDate,
                        totalWeeks = weeks,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                repository.upsertTerm(
                    existing.copy(startDate = s.startDate, totalWeeks = weeks, updatedAt = now),
                )
            }
        }.onSuccess {
            _state.update {
                it.copy(
                    hasTerm = true,
                    termName = createdName ?: it.termName,
                    savedTick = it.savedTick + 1,
                    error = null,
                )
            }
            // 起始日变了 → 周次判定变 → 今日列表也变 → 重排闹钟 + 刷新小组件
            runCatching { onTermChanged() }
        }.onFailure { e ->
            _state.update { it.copy(error = "保存学期失败：${e.message ?: "未知错误"}") }
        }
    }

    // ---------- 事件消费 ----------

    fun consumeError() = _state.update { it.copy(error = null) }

    companion object {
        /** 连点步进器时只写最后一次：去抖窗口。 */
        private const val AUTO_SAVE_DEBOUNCE_MILLIS = 350L
    }
}
