/*
 * ImportTypes.kt —— 导入相关的领域类型（占位定义）
 *
 * 作用：TimetableRepository 接口（见 domain/repository/TimetableRepository.kt）的方法签名
 * 引用了 ParsedSchedule / ImportSummary 两个类型。本文件给出它们的"最小可用"
 * 领域定义，使接口在 M1 阶段即可独立编译。
 *
 * 为什么放这里而不是 parser/api：正式的 ParsedSchedule 属于导入阶段的 parser/api 包，
 * 其字段要在抓包样本落地后才会最终敲定。M1 只做数据层，不引入 parser/importer 依赖，
 * 因此先在此给出占位定义。导入阶段接入时，会以 parser/api 的正式定义替换，
 * 本文件的占位可删除（接口签名本身不变）。
 */
package com.gould.xputimetable.domain.model

/**
 * 导入通道解析出的课表数据。
 *
 * 真实定义将在导入阶段由 parser/api 提供，预计至少包含课程、安排与可选学期信息。
 * 这里用领域模型组装，保证 applyImport 签名可编译。
 *
 * @param anomalies 解析时被跳过/降级条目的说明（如「第3行：星期无法识别」）。
 *   M2-A 增补（带默认值，兼容既有构造点）：Spec 要求 NeedsConfirm 携带异常条目说明，
 *   而 PayloadParser 的 Success 只回 ParsedSchedule，说明必须随领域模型流转才能到达预览页。
 */
data class ParsedSchedule(
    val courses: List<Course>,
    val sessions: List<CourseSession>,
    val term: Term?,
    val anomalies: List<String> = emptyList(),
)

/**
 * 一次导入的结果摘要（占位），由 applyImport 返回。
 *
 * @param success                 是否成功
 * @param courseCount             成功写入的课程数
 * @param sessionCount            成功写入的安排数
 * @param message                 给用户/日志的可读说明（失败原因等）
 * @param replacedCount           本次被替换掉的该来源课程数（Spec AC-13 的覆盖范围）
 * @param preservedManualCount    本次导入后仍保留的手动添加课程数——来自导入后的真实查询，
 *                                不是推算；它是「AC-20 手动课程未被删除」的可验证证据
 * @param suspectedDuplicateNames 需要提示的用户数据（同名或同 id 冲突的导入课程名，去重）；
 *                                供预览页做 AC-21 提示，只提示不自动合并
 */
data class ImportSummary(
    val success: Boolean,
    val courseCount: Int,
    val sessionCount: Int,
    val message: String,
    val replacedCount: Int = 0,
    val preservedManualCount: Int = 0,
    val suspectedDuplicateNames: List<String> = emptyList(),
)

