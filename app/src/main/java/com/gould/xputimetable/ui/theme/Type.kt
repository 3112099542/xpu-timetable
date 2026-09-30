/*
 * Type.kt —— 字体层级（自定义 M3 Type Scale）
 *
 * 设计依据（UIUX 文档 §5）：
 * - 中文正文走系统字体栈（MiSans / OPPO Sans 等国产 ROM 字体质量已足够，打包中文字体会增加 3–8MB，与轻量定位冲突）。
 * - 数字/时间/周数规划使用等宽字体（Roboto Mono Latin 子集），保证时间轴对齐 —— 在周视图里程碑接入。
 * - 11sp 为全 App 字号下限（教室等次要信息）。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = FontFamily.Default

val XpuTypography = Typography(
    // 页面主标题（如「第 3 周」）
    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    // 卡片内课名（周视图主要信息）
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // 正文
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // 次要信息（教室、教师）
    bodySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    // 按钮文字
    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
