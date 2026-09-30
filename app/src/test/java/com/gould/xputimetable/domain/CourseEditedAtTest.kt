/*
 * CourseEditedAtTest.kt —— 编辑归属（edited_at）相关测试
 *
 * 为「用户编辑 ⇒ 失去导入来源身份 ⇒ 获得 MANUAL 保护」这条不变量提供机器证据（Spec AC-22）。
 * 纯 JVM 单测，不依赖 Android。
 */
package com.gould.xputimetable.domain

import com.gould.xputimetable.domain.model.Course
import com.gould.xputimetable.domain.model.CourseSource
import com.gould.xputimetable.domain.model.markEdited
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CourseEditedAtTest {

    private fun webCourse(editedAt: Long? = null) = Course(
        id = "c-1",
        name = "高等数学",
        code = "080101",
        teacher = "张三",
        note = null,
        colorTag = 3,
        source = CourseSource.WEB,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        termId = 7L,
        editedAt = editedAt,
    )

    @Test
    fun `markEdited 把 source 升格为 MANUAL`() {
        assertEquals(CourseSource.MANUAL, webCourse().markEdited(2_000L).source)
    }

    @Test
    fun `markEdited 写入 editedAt 与 updatedAt 为同一时刻`() {
        val edited = webCourse().markEdited(2_000L)
        assertEquals(2_000L, edited.editedAt)
        assertEquals(2_000L, edited.updatedAt)
    }

    @Test
    fun `markEdited 不改动其他字段`() {
        val before = webCourse()
        val after = before.markEdited(2_000L)
        assertEquals(before.id, after.id)
        assertEquals(before.name, after.name)
        assertEquals(before.code, after.code)
        assertEquals(before.teacher, after.teacher)
        assertEquals(before.colorTag, after.colorTag)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.termId, after.termId)
    }

    @Test
    fun `markEdited 是纯函数：原对象不被修改`() {
        val before = webCourse()
        val after = before.markEdited(2_000L)
        assertNotEquals(before, after)
        assertEquals(CourseSource.WEB, before.source)
        assertNull(before.editedAt)
    }

    @Test
    fun `markEdited 对已是 MANUAL 的课程幂等`() {
        val manual = webCourse(editedAt = 900L).copy(source = CourseSource.MANUAL)
        val edited = manual.markEdited(2_000L)
        assertEquals(CourseSource.MANUAL, edited.source)
        assertEquals(2_000L, edited.editedAt)
        assertEquals(manual.id, edited.id)
    }

    @Test
    fun `未编辑过的课程 editedAt 默认为 null`() {
        assertNull(webCourse().editedAt)
    }

    @Test
    fun `往返保持：editedAt 有值时保留，无值时保持 null`() {
        assertEquals(1_500L, webCourse(editedAt = 1_500L).editedAt)
        assertNull(webCourse().editedAt)
    }
}
