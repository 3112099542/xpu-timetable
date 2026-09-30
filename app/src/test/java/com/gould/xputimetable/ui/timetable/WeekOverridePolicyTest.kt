/*
 * WeekOverridePolicyTest.kt —— 周次覆盖归一化与「回到本周」可用性单测
 *
 * 回归目标（M7 真实缺陷）：手动滑到别的周 → 点「回到本周」→ 按钮重新变亮且不再熄灭。
 * 根因是 pager 程序化滚动落定后把"当前页（=本周）"回写成覆盖值；归一化后该回写幂等。
 *
 * 覆盖：目标=自动周（核心回归）、目标≠自动周、自动周为 null（起始日无效/未开学）、
 *       放假期间自动周被钳制、isOverridden 的三态与兜底。
 */
package com.gould.xputimetable.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekOverridePolicyTest {

    // ---------- normalize：写入侧的幂等化 ----------

    @Test
    fun `目标周就是自动周时归一化为 null（回到本周后被回写不再点亮按钮）`() {
        val result = WeekOverridePolicy.normalize(target = 6, autoWeek = 6)
        assertNull(result)
    }

    @Test
    fun `目标周不同于自动周时保留该周次`() {
        assertEquals(7, WeekOverridePolicy.normalize(target = 7, autoWeek = 6))
        assertEquals(5, WeekOverridePolicy.normalize(target = 5, autoWeek = 6))
        assertEquals(1, WeekOverridePolicy.normalize(target = 1, autoWeek = 6))
    }

    @Test
    fun `学期起始日无效（自动周为 null）时任何指定都算覆盖`() {
        assertEquals(6, WeekOverridePolicy.normalize(target = 6, autoWeek = null))
        assertEquals(1, WeekOverridePolicy.normalize(target = 1, autoWeek = null))
    }

    @Test
    fun `放假期间自动周超出总周数、钳制到末周后仍能归一化`() {
        // 第 18 周（末周）被钳制的自动周，用户滑到末周等价于回到本周
        assertNull(WeekOverridePolicy.normalize(target = 18, autoWeek = 18))
        // 未钳制的情况会漏判：20 != 18 → 仍然点亮按钮（回归保护，故 VM 传钳制值）
        assertEquals(18, WeekOverridePolicy.normalize(target = 18, autoWeek = 20))
    }

    // ---------- isOverridden：显示侧（按钮是否可点/高亮） ----------

    @Test
    fun `没有覆盖值时为 false`() {
        assertFalse(WeekOverridePolicy.isOverridden(override = null, autoWeek = 6))
        assertFalse(WeekOverridePolicy.isOverridden(override = null, autoWeek = null))
    }

    @Test
    fun `覆盖值不同于自动周时为 true`() {
        assertTrue(WeekOverridePolicy.isOverridden(override = 7, autoWeek = 6))
    }

    @Test
    fun `历史脏值等于自动周时仍为 false（兜底，不依赖 normalize 一定生效）`() {
        assertFalse(WeekOverridePolicy.isOverridden(override = 6, autoWeek = 6))
    }

    @Test
    fun `未开学（自动周为 null）时有覆盖值即为 true`() {
        assertTrue(WeekOverridePolicy.isOverridden(override = 3, autoWeek = null))
    }
}
