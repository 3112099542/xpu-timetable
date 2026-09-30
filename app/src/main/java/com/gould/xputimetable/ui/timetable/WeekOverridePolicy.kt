/*
 * WeekOverridePolicy.kt —— 周次覆盖与「回到本周」可用性的判定纯函数
 *
 * 背景（M7 修复的真实缺陷）：手动滑到别的周后点「回到本周」，按钮会重新变亮且不再熄灭。
 * 根因是"覆盖值"与"显示周"耦合在一起：
 *   1. backToCurrentWeek() 清空覆盖 → VM 周次回落到自动周（如第 6 周）；
 *   2. WeekPager 的程序化滚动落定后，把当前页回写成 setWeek(6)；
 *   3. 这次回写与"清空覆盖"无关，于是 weekOverride 又变成 6（非 null），
 *      weekIsOverridden 判定为 true —— 按钮长亮，按第二次才恢复。
 *
 * 为什么不在 pager 侧补时序：程序化滚动的"结束"与 snapshotFlow 尾帧的"投递"天然在赛跑
 * （两者都在主线程，谁先谁后取决于帧调度），靠加守卫只能压低概率，不能消除。
 * 因此改在**语义层**让这一步幂等：目标周就是自动周时，等价于"没有覆盖"，直接存 null。
 * 无论回写何时到达，结果都一致。
 *
 * 与 BackPolicy / WeekCalc 同风格：纯函数、无状态、可独立单测。
 */
package com.gould.xputimetable.ui.timetable

internal object WeekOverridePolicy {

    /**
     * 把"用户指定的周次"规范化为覆盖值。
     *
     * [autoWeek] 是**钳制后**的自动周（按学期起始日推算，并限制在 1..totalWeeks）；
     * 学期起始日缺失或无法解析时为 null —— 此时没有"本周"可言，任何指定都算真实覆盖。
     *
     * 返回 null 表示"不覆盖"（回到自动周）。
     */
    fun normalize(target: Int, autoWeek: Int?): Int? =
        if (autoWeek != null && target == autoWeek) null else target

    /**
     * 是否处于"手动覆盖"状态（决定「回到本周」按钮是否可点、是否高亮为 primary）。
     *
     * 语义修正：**显示的周次与本周不同**才算覆盖，而不是"曾经手动切过"——
     * 用户此刻看的就是本周时，这个按钮没有可做的动作。
     * 正常路径下 normalize 已保证 override 不会等于 autoWeek，这里再判一次是**兜底**：
     * 即便存在历史脏值或将来新增的写入点，按钮也不会错误长亮。
     */
    fun isOverridden(override: Int?, autoWeek: Int?): Boolean =
        override != null && override != autoWeek
}
