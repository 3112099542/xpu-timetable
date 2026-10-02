/*
 * CourseEditScreen.kt —— 手动添加 / 编辑课程表单（页面装配层）
 *
 * 设计取舍：
 *   - 字段按「基本信息 / 时间 / 地点」排列，每组不超过 4 项（降低工作记忆负担）；
 *   - 节次与周次用「- 值 +」步进器而不是自由输入，从源头避免非法输入（Spec AC-07）；
 *   - 颜色给 12 个固定色点，与课程色板一致（颜色 = 课程身份）。
 *   - 可复用控件在 components/FormControls.kt（拆分原因见该文件头注释）。
 *
 * 修复记录（2026-09-17）：
 *   ① **状态栏不再遮挡按钮**：应用是 edge-to-edge（MainActivity 调了 enableEdgeToEdge），
 *      而本页此前是裸 Column、没有任何 insets 处理，导致顶栏的「关闭 / 保存」被状态栏压住点不到。
 *      现在改用 Scaffold，顶栏加 statusBarsPadding()、内容加 imePadding()
 *      （键盘弹出时输入框与底部按钮同样不会被遮挡）。
 *   ② **标签上浮动画不再掉帧**：此前每个输入框的回调都是内联 lambda（每次重组新建实例，
 *      对 Compose 是"不稳定参数"），加上父级读取整个 draft，一次击键会让整张表单
 *      （3 个输入框 + 标签行 + 12 个色点 + 步进器）全部重组，标签动画因此逐帧丢帧。
 *      现在：字段拆成独立小组件、只接收自己需要的稳定类型参数，回调统一用方法引用
 *      （viewModel::setXxx，实例可稳定复用），未变更的字段会被 Compose 直接跳过重组；
 *      错误标记也由"字符串包含判断"改为枚举字段定位（DraftField），省掉每帧的字符串扫描。
 */
package com.gould.xputimetable.ui.courseedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.courseedit.components.CourseColorRow
import com.gould.xputimetable.ui.courseedit.components.DayOfWeekRow
import com.gould.xputimetable.ui.courseedit.components.LabeledField
import com.gould.xputimetable.ui.courseedit.components.RangeRow
import com.gould.xputimetable.ui.courseedit.components.SectionTitle
import com.gould.xputimetable.ui.courseedit.components.WeekTypeRow
import com.gould.xputimetable.ui.theme.IconSize

@Composable
fun CourseEditScreen(
    viewModel: CourseEditViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.draft.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(draft.saved) {
        if (draft.saved) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // statusBarsPadding：edge-to-edge 下顶栏必须自己吃状态栏内边距，否则按钮被状态栏压住
            EditTopBar(
                title = title,
                onBack = onBack,
                onSave = viewModel::save,
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // 键盘弹出时压缩滚动区，输入框与底部按钮不被遮挡
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BasicInfoFields(
                name = draft.name,
                teacher = draft.teacher,
                classroom = draft.classroom,
                errorField = draft.errorField,
                onNameChange = viewModel::setName,
                onTeacherChange = viewModel::setTeacher,
                onClassroomChange = viewModel::setClassroom,
            )

            SectionTitle("星期")
            DayOfWeekRow(selected = draft.dayOfWeek, onSelect = viewModel::setDayOfWeek)

            SectionTitle("节次")
            RangeRow(
                startLabel = "开始",
                endLabel = "结束",
                start = draft.startSection,
                end = draft.endSection,
                range = CourseEditViewModel.SECTION_RANGE,
                onStartChange = viewModel::setStartSection,
                onEndChange = viewModel::setEndSection,
            )

            SectionTitle("周次")
            if (draft.hasExactWeeks) {
                Text(
                    text = "该课周次由教务给出精确列表，手动修改后将改为区段设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            RangeRow(
                startLabel = "起始周",
                endLabel = "结束周",
                start = draft.startWeek,
                end = draft.endWeek,
                range = CourseEditViewModel.WEEK_RANGE,
                onStartChange = viewModel::setStartWeek,
                onEndChange = viewModel::setEndWeek,
            )

            SectionTitle("单双周")
            WeekTypeRow(selected = draft.weekType, onSelect = viewModel::setWeekType)

            SectionTitle("课程颜色")
            CourseColorRow(selected = draft.colorTag, onSelect = viewModel::setColorTag)

            draft.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            if (draft.courseId != null) {
                Button(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这门课")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除课程") },
            text = { Text("将删除这门课的全部上课安排。删除后无法恢复，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

/** 顶栏：取消（关闭）/ 标题 / 保存。 */
@Composable
private fun EditTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(AppIcons.close),
                contentDescription = "取消",
                modifier = Modifier.size(IconSize.Medium),
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSave) {
            Icon(
                painter = painterResource(AppIcons.check),
                contentDescription = "保存",
                modifier = Modifier.size(IconSize.Large),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 基本信息区：三个输入框各自独立接收参数，互不牵连重组。 */
@Composable
private fun BasicInfoFields(
    name: String,
    teacher: String,
    classroom: String,
    errorField: DraftField?,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onClassroomChange: (String) -> Unit,
) {
    LabeledField(
        value = name,
        label = "课程名",
        isError = errorField == DraftField.NAME,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledField(
        value = teacher,
        label = "教师（可选）",
        isError = errorField == DraftField.TEACHER,
        onValueChange = onTeacherChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    LabeledField(
        value = classroom,
        label = "教室（可选）",
        isError = errorField == DraftField.CLASSROOM,
        onValueChange = onClassroomChange,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
