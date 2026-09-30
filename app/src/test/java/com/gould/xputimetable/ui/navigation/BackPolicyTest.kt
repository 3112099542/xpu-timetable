/*
 * BackPolicyTest.kt —— 返回键决策纯函数单测（7 例边界，对应规格 §3.2）
 *
 * 覆盖：栈深优先、首次提示、0ms/1999ms/2000ms 边界均 Exit、2001ms 过期、时钟回拨安全侧。
 */
package com.gould.xputimetable.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackPolicyTest {

    @Test
    fun `栈深大于 1 恒 Pop 且与时间无关`() {
        val result = BackPolicy.decide(stackSize = 2, lastHintAtMillis = null, nowMillis = 999L)
        assertEquals(BackAction.Pop, result)
    }

    @Test
    fun `首页且从未提示过为 HintAndArm`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = null, nowMillis = 123L)
        assertEquals(BackAction.HintAndArm, result)
    }

    @Test
    fun `间隔 0ms 边界为 Exit`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = 1000L, nowMillis = 1000L)
        assertEquals(BackAction.Exit, result)
    }

    @Test
    fun `间隔 1999ms 为 Exit`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = 1000L, nowMillis = 2999L)
        assertEquals(BackAction.Exit, result)
    }

    @Test
    fun `间隔 2000ms 边界为 Exit`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = 1000L, nowMillis = 3000L)
        assertEquals(BackAction.Exit, result)
    }

    @Test
    fun `间隔 2001ms 过期为 HintAndArm`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = 1000L, nowMillis = 3001L)
        assertEquals(BackAction.HintAndArm, result)
    }

    @Test
    fun `时钟回拨 now 早于 lastHint 不退出（安全侧）`() {
        val result = BackPolicy.decide(stackSize = 1, lastHintAtMillis = 5000L, nowMillis = 1000L)
        assertEquals(BackAction.HintAndArm, result)
    }
}
