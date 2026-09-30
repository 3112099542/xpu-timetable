/*
 * WeekType.kt —— 单双周类型（领域枚举）
 *
 * 作用：描述一门课在"哪几周上"。教务数据里常见三种情况：
 *   - ALL ：每周都上（如 1-16 周）；
 *   - ODD ：只在单周上（1、3、5…）；
 *   - EVEN：只在双周上（2、4、6…）。
 *
 * 为什么用枚举而不是字符串：周次判定是课表正确性的核心（架构 §7.3 的 SQL 过滤与
 * WeekCalc 的纯函数都依赖它）。用枚举能在编译期拦住 "od"、"Odd " 这类脏值；
 * 只有在数据库边界才转成字符串（week_type 列存大写枚举名，见 Mappers 的写入侧不变量）。
 */
package com.gould.xputimetable.domain.model

enum class WeekType {
    ALL,
    ODD,
    EVEN,
}
