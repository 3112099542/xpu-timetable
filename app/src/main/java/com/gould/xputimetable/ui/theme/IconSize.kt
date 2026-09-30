/*
 * IconSize.kt —— 全项目图标尺寸常量（ADR-002 规则 2）
 *
 * 作用：图标只允许 16 / 20 / 24dp 三档，常量集中在此，禁止在界面代码里散落魔法数字。
 *   16dp：行内小图标（卡片内、文字旁）
 *   20dp：按钮内图标
 *   24dp：独立图标（顶栏动作、导航）
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.ui.unit.dp

object IconSize {
    val Small = 16.dp
    val Medium = 20.dp
    val Large = 24.dp
}
