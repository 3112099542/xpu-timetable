/*
 * SettingsScreen.kt —— 「我的」页根页（M9 重构为分组列表 + 二级页）
 *
 * 结构参照系统设置页（产品负责人要求，见 M9 需求）：
 *   我的
 *     ── 学期 ──   学期设置            →
 *     ── 权限 ──   精确闹钟授权        →
 *     ── 数据 ──   导出课表文件 / 二维码分享课表
 *     ── 关于 ──   关于与隐私          →
 * 学期与总周数已移入二级页「学期设置」，且**去掉了手动保存按钮**（改为自动保存）。
 *
 * 本页不再持有 SnackbarHost：所有瞬时反馈都走导航层的底部提示通道（onShowHint）——
 * 原方案的 Snackbar 挂在页面 Scaffold 上，切页重挂会把未过期的提示重放一次。
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；图标只经 AppIcons。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.transfer.ExportSection

// ---------- 文件级文案常量 ----------
private const val TITLE = "我的"
private const val GROUP_TERM = "学期"
private const val ROW_TERM = "学期设置"
private const val TERM_NONE = "还没有学期，点此设置"
private const val GROUP_PERMISSIONS = "权限"
private const val ROW_PERMISSION = "精确闹钟授权"
private const val PERMISSION_OK = "已授权"
private const val PERMISSION_MISSING = "未授权：桌面小组件刷新可能不精准"
private const val GROUP_DATA = "数据"
private const val GROUP_ABOUT = "关于"
private const val ROW_ABOUT = "关于与隐私"
private const val ABOUT_SUBTITLE = "非官方校园工具 · 数据仅存本机"
private const val SEP = " · "
private const val LABEL_START_DATE = "起始日 "
private const val SUFFIX_WEEK = " 周"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /** 导出课表文件需要仓库读取激活学期快照。 */
    repository: TimetableRepository,
    /** 进「学期设置」二级页。 */
    onOpenTermSetup: () -> Unit,
    /** 进其他二级页（权限 / 关于）。 */
    onOpenSubPage: (ProfileSubPage) -> Unit,
    /** 跳转二维码分享页。 */
    onShareQr: () -> Unit,
    /** 全局底部提示（导出结果 / 保存失败等）。 */
    onShowHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SettingsGroupHeader(GROUP_TERM)
            SettingsNavRow(
                title = ROW_TERM,
                subtitle = termSummary(state),
                onClick = onOpenTermSetup,
                showDivider = false,
            )

            SettingsGroupHeader(GROUP_PERMISSIONS)
            SettingsNavRow(
                title = ROW_PERMISSION,
                subtitle = if (state.exactAlarmAllowed) PERMISSION_OK else PERMISSION_MISSING,
                onClick = { onOpenSubPage(ProfileSubPage.PERMISSIONS) },
                showDivider = false,
            )

            SettingsGroupHeader(GROUP_DATA)
            ExportSection(
                repository = repository,
                onShowHint = onShowHint,
                onShareQr = onShareQr,
            )

            SettingsGroupHeader(GROUP_ABOUT)
            SettingsNavRow(
                title = ROW_ABOUT,
                subtitle = ABOUT_SUBTITLE,
                onClick = { onOpenSubPage(ProfileSubPage.ABOUT) },
                showDivider = false,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 学期一行摘要：没学期时给引导；有学期时把"起始日 / 总周数"这两个关键值露在行上。 */
private fun termSummary(state: SettingsUiState): String {
    if (!state.hasTerm) return TERM_NONE
    val namePart = if (state.termName.isBlank()) "" else state.termName + SEP
    return namePart + LABEL_START_DATE + state.startDate + SEP + state.totalWeeks + SUFFIX_WEEK
}
