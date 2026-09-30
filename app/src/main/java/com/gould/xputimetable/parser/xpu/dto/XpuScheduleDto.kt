/*
 * XpuScheduleDto.kt —— 强智系教务 print-data 响应的 DTO（M2-B §1.3 实测建模）
 *
 * 结构：{ "studentTableVms": [ { 学生信息..., "activities": [ ... ] } ] }
 * 课表数据在 studentTableVms[0].activities[]（本次金样本 16 条）。
 *
 * 防御式约定：所有可能缺失的字段一律可空或给默认值——学校接口随时可能加/减字段
 * （Json 配置 ignoreUnknownKeys），单条 activity 缺必要字段只跳行（anomalies），不让整次导入崩。
 */
package com.gould.xputimetable.parser.xpu.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XpuScheduleResponse(
    @SerialName("studentTableVms")
    val studentTableVms: List<XpuStudentTableVm> = emptyList(),
)

@Serializable
data class XpuStudentTableVm(
    /** 只取课表数据；学生信息字段（姓名/学号等）一律不建模——隐私红线，用不到就不读。 */
    @SerialName("activities")
    val activities: List<XpuActivity> = emptyList(),
)

@Serializable
data class XpuActivity(
    /** 课程名（如「大学体育Ⅲ」） */
    @SerialName("courseName")
    val courseName: String? = null,

    /** 课程编号（如 U51G111003），同一 code 的多条 activity = 同一门课的多条安排（分组键） */
    @SerialName("courseCode")
    val courseCode: String? = null,

    /** 教师数组（如 ["王婷（R）"]），多教师用「、」连接 */
    @SerialName("teachers")
    val teachers: List<String> = emptyList(),

    /** 教室（如 A-424语音室） */
    @SerialName("room")
    val room: String? = null,

    /** 星期几，1 = 周一 */
    @SerialName("weekday")
    val weekday: Int? = null,

    /** 起止节次（如 3 / 4） */
    @SerialName("startUnit")
    val startUnit: Int? = null,

    @SerialName("endUnit")
    val endUnit: Int? = null,

    /** 周次数组（如 [4,5,...,18]；实测可能乱序，见 XpuWeekRepresentation） */
    @SerialName("weekIndexes")
    val weekIndexes: List<Int> = emptyList(),

    /** 周次原文（如 4~18、2,6,10,14），仅用于异常提示 */
    @SerialName("weeksStr")
    val weeksStr: String? = null,
)
