/*
 * PayloadParser.kt —— 所有课表解析器的统一入口（架构 §5.2 原样落地）
 *
 * 作用：把"任意来源的原始载荷"翻译成纯领域模型 ParsedSchedule。
 * I 是原始输入类型（WakeUp CSV 是 String，教务直连是抓到的 JSON/DOM）。
 *
 * 为什么是同步函数而不是 suspend：解析是纯 CPU 计算（无 IO、无数据库），
 * 调用方（importer）自行决定在哪个调度器上执行。
 *
 * 错误约定：禁止抛裸异常穿透到 UI——失败一律装进 ScheduleParseResult.Failure
 * 的结构化 ParseError（见 ScheduleParseResult.kt），让界面能给出"为什么失败"
 * 与兜底引导（AC-11），而不是一句统一吞掉的"解析失败"。
 */
package com.gould.xputimetable.parser.api

/**
 * 解析器统一入口。
 *
 * @param I 原始输入类型（如 String = CSV 文本）
 */
interface PayloadParser<I> {

    /** 解析成功返回课程与安排；失败返回结构化错误。 */
    fun parse(input: I): ScheduleParseResult
}
