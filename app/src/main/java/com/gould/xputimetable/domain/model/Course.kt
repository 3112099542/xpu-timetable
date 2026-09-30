/*
 * Course.kt —— 课程（领域模型）
 *
 * 作用：代表"一门课"的静态信息（与具体哪天上、上几节无关）。
 *   课表类产品把数据拆成两层：
 *     - Course（本文件）：名称、教师、颜色、来源等"这门课是什么"；
 *     - CourseSession（安排）：这门课在某星期几、某节次、某周次的"一次上课"。
 *   一门课可以有多个安排（如周三 1-2 节 + 周五 3-4 节两处），这样拆才能
 *   支撑周视图按格渲染与单双周过滤。
 *
 * source 含义：WEB（教务导入）/ WAKEUP_CSV（WakeUp 文件导入）/ MANUAL（手动添加），
 * 与架构 §7.2 的 courses.source 列一致。取值统一用 CourseSource 常量，禁止散落字符串。
 *
 * termId 含义：本课程归属的学期（terms.id）。这是 Spec AC-13「重复导入同一学期以覆盖方式
 * 更新本学期数据」得以实现的支点——没有学期归属，下学期导入的新课会与旧课混在同一张周视图。
 */
package com.gould.xputimetable.domain.model

/**
 * 一门课的基础信息。
 *
 * @param id        课程唯一 ID（UUID 字符串，导入与手动添加统一口径）
 * @param name      课程名
 * @param code      课程编号，可空
 * @param teacher   授课教师，可空
 * @param note      备注，可空
 * @param colorTag  调色板索引（0..11），而非 ARGB 直存——换主题不失真，同一门课永远同色
 * @param source    来源：CourseSource.WEB / WAKEUP_CSV / MANUAL
 * @param createdAt 创建时间，epoch 毫秒
 * @param updatedAt 更新时间，epoch 毫秒
 * @param termId    归属学期 id（terms.id）——本学期数据隔离与重复导入覆盖的支点
 * @param editedAt  用户最后一次手动编辑时间；null = 从未被用户编辑过（禁止用 0 表示空）
 */
data class Course(
    val id: String,
    val name: String,
    val code: String?,
    val teacher: String?,
    val note: String?,
    val colorTag: Int,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val termId: Long,
    val editedAt: Long? = null,
)

/**
 * 课程来源常量（与架构 §7.2 的 courses.source 列取值一致）。
 * 集中定义，避免各处散落 magic string；导入来源判定与 AC-20 保护判定都以此为准。
 */
object CourseSource {
    /** 教务系统（WebView 直连）导入 */
    const val WEB = "WEB"

    /** WakeUp CSV / Excel 文件导入 */
    const val WAKEUP_CSV = "WAKEUP_CSV"

    /** 本 App 导出的课表文件 / 二维码导入（M6）；独立来源避免触发同源整体替换误删旧数据 */
    const val FILE_JSON = "FILE_JSON"

    /** 手动添加，或由导入数据经用户手动编辑后升格而来（受 AC-20/AС-22 保护） */
    const val MANUAL = "MANUAL"
}

/**
 * 用户编辑标记（团队领导裁决 2026-09-16，对应 Spec 验收 AC-22）。
 *
 * 不变量：用户编辑 ⇒ 失去导入来源身份 ⇒ 获得 MANUAL 保护。即用户手动编辑任意一门课程后
 * （无论其原 source 是什么），该课程的 source 必须置为 MANUAL 并记录 editedAt，
 * 从而获得「任何来源的导入都不得删除它」的保护（见架构 §8.2 第 10 条与 AC-20）。
 *
 * 为什么不是"保留原 source"：若保留 WEB，下次同源导入会把用户改过的内容当同源记录整体
 * 替换掉——这是一条无提示的数据丢失路径。代价只是可能出现同名重复，而重复可见、可自行清理。
 *
 * 实现约束：本函数是纯函数（无副作用、不碰数据库），落库由仓库层的 upsertCourse 负责；
 * UI 编辑路径的唯一入口是它，禁止在界面里手写 copy(source = MANUAL)。
 * 幂等：对本来就是 MANUAL 的课程重复调用是安全的（只刷新时间戳）。
 *
 * @param nowMillis 当前时间（由调用方注入，便于单测确定性）
 */
fun Course.markEdited(nowMillis: Long): Course = copy(
    source = CourseSource.MANUAL,
    editedAt = nowMillis,
    updatedAt = nowMillis,
)
