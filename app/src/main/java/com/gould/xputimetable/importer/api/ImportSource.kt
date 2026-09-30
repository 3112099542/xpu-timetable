/*
 * ImportSource.kt —— 导入通道来源（importer/api）
 *
 * 作用：标识一次导入走的是哪条通道，供日志与预览页展示覆盖范围。
 * 与 courses.source 列的取值（domain/model/Course.kt 的 CourseSource 字符串常量）
 * 一一对应：枚举名 == 列值。两套类型并存是架构 §5.4 的原样落地
 * （存储层用字符串列，导入层用类型安全枚举）。
 */
package com.gould.xputimetable.importer.api

enum class ImportSource {
    /** 教务系统（WebView 直连）导入（M2-B，暂未开放）。 */
    WEB,

    /** WakeUp 导出的 CSV 文件导入（M2-A，本通道）。 */
    WAKEUP_CSV,

    /** 本 App 导出的课表文件 / 二维码导入（M6）。 */
    FILE_JSON,

    /** 手动添加（不走 importer，列出仅为对齐架构枚举）。 */
    MANUAL,
}
