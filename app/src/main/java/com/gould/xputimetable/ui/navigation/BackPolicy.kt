/*
 * BackPolicy.kt —— 返回键决策纯函数（AC-26 / AC-27 的判定核心）
 *
 * 把"返回键该做什么"从 UI 里抽出来，做成可单测的纯函数，避免逻辑散在 AppNav 与各个 Screen。
 * 决策只看三件事：当前返回栈深度、上次提示时间、当前时间。
 */
package com.gould.xputimetable.ui.navigation

/** 一次返回键按下应执行的动作。 */
internal sealed interface BackAction {
    data object Pop : BackAction            // 栈内还有上一页：弹出一层
    data object HintAndArm : BackAction     // 首页首次（或提示已过期）：只提示 + 记录时间
    data object Exit : BackAction           // 首页且提示未过期：退出应用
}

internal object BackPolicy {
    /** 二次返回的有效窗口（毫秒）。 */
    const val EXIT_WINDOW_MILLIS = 2_000L

    /**
     * 返回键决策：栈深 + 上次提示时间 + 当前时间 → 动作。
     * [lastHintAtMillis] 为 null 表示"从未提示过"。
     * 时钟回拨（now < lastHint）时差值成负数，不落在 0..窗口 内 → 按"未过期"处理为不退出（安全侧）。
     */
    fun decide(stackSize: Int, lastHintAtMillis: Long?, nowMillis: Long): BackAction = when {
        stackSize > 1 -> BackAction.Pop
        lastHintAtMillis != null && nowMillis - lastHintAtMillis in 0..EXIT_WINDOW_MILLIS -> BackAction.Exit
        else -> BackAction.HintAndArm
    }
}
