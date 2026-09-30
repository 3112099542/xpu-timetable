/*
 * SettingsScreen.kt —— 「我的」页（M2-D 设置内容，M4-UI R7 迁入底部导航）
 *
 * 自上而下：学期设置（起始日日期选择器 + 总周数）→ 精确闹钟授权引导 →
 * 关于与隐私声明 → 导入导出（M6）。
 * M7：课前提醒功能已整体移除（提醒开关、提前分钟、通知权限引导一并删去）。
 *
 * M4-UI：本页改为根页（标题「我的」，无返回箭头——返回由系统返回键与底部导航承担），
 * 底部追加占位区块「更多功能陆续加入」（纯文本，非假按钮）。
 *
 * 界面铁律（Spec §10）：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；
 * 回调用方法引用；图标只经 AppIcons。权限引导只跳系统设置，不做运行时请求弹窗
 * （拒绝权限不影响课表功能，AC-16）。
 */
package com.gould.xputimetable.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.transfer.ExportSection

// ---------- 文件级文案常量 ----------

private const val TITLE = "我的"
private const val SECTION_TERM = "学期"
private const val HINT_NO_TERM = "还没有学期；先在课表页创建本学期后才能校正"
private const val SECTION_PERMISSIONS = "权限"
private const val HINT_PERMISSIONS_OK = "小组件刷新所需权限已就绪"
private const val HINT_EXACT_ALARM = "未获精确闹钟授权：桌面小组件的刷新可能不精准（最长延后约 1 小时，系统省电策略）；课表功能不受影响"
private const val ACTION_REQUEST_EXACT = "去授权"
private const val SECTION_ABOUT = "关于与隐私"
private const val TEXT_ABOUT_1 = "本应用为非官方的校园工具，与学校无关；课表数据仅保存在本机。"
private const val TEXT_ABOUT_2 = "不读取、不存储、不上传任何教务凭据；登录只发生在你自己的设备上。"
private const val TEXT_ABOUT_3 = "开源协议 GPL-3.0（仓库地址占位）。"
private const val SECTION_MORE = "更多"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /** M6 需求 6：导出课表文件需要仓库读取激活学期快照。 */
    repository: TimetableRepository,
    /** M6 需求 6-B：跳转二维码分享页。 */
    onShareQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
        viewModel.consumeError()
    }
    LaunchedEffect(state.termSaved) {
        if (state.termSaved) {
            snackbarHostState.showSnackbar("学期已保存")
            viewModel.consumeTermSaved()
        }
    }

    // 从系统设置页返回时刷新授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsTopBar()
            Spacer(Modifier.height(8.dp))

            // ---------- 学期 ----------
            SectionTitle(SECTION_TERM)
            if (state.termName.isBlank()) {
                Text(HINT_NO_TERM, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TermSection(state, viewModel)
            }
            Spacer(Modifier.height(16.dp))

            // ---------- 权限（M7：只剩精确闹钟 —— 桌面小组件刷新的精度依赖它） ----------
            SectionTitle(SECTION_PERMISSIONS)
            if (!state.exactAlarmAllowed && Build.VERSION.SDK_INT >= 31) {
                PermissionGuide(
                    hint = HINT_EXACT_ALARM,
                    actionText = ACTION_REQUEST_EXACT,
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                )
            } else {
                Text(HINT_PERMISSIONS_OK, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            // ---------- 关于 / 隐私 ----------
            SectionTitle(SECTION_ABOUT)
            listOf(TEXT_ABOUT_1, TEXT_ABOUT_2, TEXT_ABOUT_3).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(16.dp))

            // ---------- 导入导出（M6 需求 6；实现拆至 ui/transfer/ExportSection.kt 守行数门禁） ----------
            SectionTitle(SECTION_MORE)
            ExportSection(
                repository = repository,
                onShareQr = onShareQr,
                snackbarHostState = snackbarHostState,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 顶栏：根页无返回箭头（返回由系统返回键与底部导航承担）。 */
@Composable
private fun SettingsTopBar() {
    Text(
        text = TITLE,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun PermissionGuide(hint: String, actionText: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onAction) { Text(actionText) }
    }
}
