/*
 * ImportPayload.kt —— 导入的原始载荷（importer/api）
 *
 * 作用：把"载荷从哪来、长什么样"与"怎么解析"解耦。UI 层负责把用户选中的文件/
 * 拦截到的响应装进本结构，importer 只认本结构——不认识 ContentResolver、WebView。
 * 这样 importer 可在 JVM 单测里用纯文本构造，UI 层的获取方式怎么变都不影响解析。
 *
 * 本次（M2-A）只用到 text（CSV 文本）；uri 与 displayName 用于失败保留
 * （AC-11「不得丢失已选文件」的提示与重试）与 import_logs 审计。
 */
package com.gould.xputimetable.importer.api

/**
 * 一次导入的原始输入。
 *
 * @param text        载荷文本（WakeUp CSV 场景即文件全文）
 * @param uri         载荷来源 Uri（SAF 选中文件的 content://…），可空
 * @param displayName 展示名（如「课表.csv」），可空
 */
data class ImportPayload(
    val text: String? = null,
    val uri: String? = null,
    val displayName: String? = null,
)
