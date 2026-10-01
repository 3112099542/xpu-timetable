/*
 * BackKeyWiring.kt —— 系统返回键接线（M9 自 AppNav.kt 抽出）
 *
 * 抽出的原因有两条，都不是为了好看：
 *   1. 功能上：这段是纯接线（把系统返回键接到 BackPolicy 决策 + 导航回调），
 *      与页面渲染无关，放在 AppNav 里会淹没导航结构；
 *   2. 工程上：AppNav.kt 已顶到 300 行门禁，M9 还要再加两个目的地分支。
 *
 * 决策逻辑仍是纯函数 BackPolicy（可单测）；本文件只负责：建回调一次（remember）、
 * 读最新栈深（用 lambda 取，避免闭包捕获过期值）、注册/注销、以及"上一次提示时间"状态。
 */
package com.gould.xputimetable.ui.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun BackKeyWiring(
    /** 当前返回栈深度；用 lambda 取实时值（回调只建一次，不能捕获旧值）。 */
    backStackSize: () -> Int,
    onPopBackStack: () -> Unit,
    /** 首页首次返回：显示"再按一次退出应用"。 */
    onShowExitHint: () -> Unit,
) {
    // 上一次提示时间（null = 从未提示）；退出窗口由 BackPolicy 独立管理
    var lastHintAt by remember { mutableStateOf<Long?>(null) }

    val dispatcherOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
    val dispatcher = dispatcherOwner.onBackPressedDispatcher
    val callback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (BackPolicy.decide(backStackSize(), lastHintAt, System.currentTimeMillis())) {
                    BackAction.Pop -> onPopBackStack()
                    BackAction.HintAndArm -> {
                        lastHintAt = System.currentTimeMillis()
                        onShowExitHint()
                    }
                    BackAction.Exit -> {
                        // 交回系统默认：保留退出动画；不再设回 enabled（依赖 Activity 重建恢复）
                        isEnabled = false
                        dispatcher.onBackPressed()
                    }
                }
            }
        }
    }
    DisposableEffect(dispatcher) {
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}
