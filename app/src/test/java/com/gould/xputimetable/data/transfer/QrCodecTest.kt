/*
 * QrCodecTest.kt —— 二维码载荷编解码验证（M6 需求 6-B 自证）
 *
 * 只测纯函数 toQrPayload / fromQrPayload（deflate + base64 + XPUTT1: 前缀）；
 * Bitmap 相关（encode/decode）依赖 Android 运行时，不做 JVM 单测。
 */
package com.gould.xputimetable.data.transfer

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {

    /** 用例 1：往返全等（含中文与换行——课表字段的真实形态）。 */
    @Test
    fun payloadRoundTrip_preservesChineseAndNewlines() {
        val json = """
            {"v":1,"app":"xpu-tt","term":{"name":"2026-2027-1","startDate":"2026-08-24","totalWeeks":18},
             "courses":[{"name":"大学英语Ⅲ","teacher":"王婷（R）","colorTag":3,"sessions":[]}]}
        """.trimIndent()
        val payload = QrCodec.toQrPayload(json)
        assertEquals(json, QrCodec.fromQrPayload(payload!!))
    }

    /** 用例 2：无 XPUTT1: 前缀的串（如普通网址）→ null（快速拒绝非本 App 二维码）。 */
    @Test
    fun fromQrPayload_rejectsMissingPrefix() {
        assertNull(QrCodec.fromQrPayload("https://example.com/schedule"))
    }

    /** 用例 3：前缀正确但 base64 非法 → null（不抛异常）。 */
    @Test
    fun fromQrPayload_rejectsInvalidBase64WithoutThrowing() {
        assertNull(QrCodec.fromQrPayload("XPUTT1:%%%not-base64%%%"))
    }

    /** 用例 4：3000 字节不可压缩输入（伪随机文本）→ 压缩后仍超限 → null（不做分片）。 */
    @Test
    fun toQrPayload_rejectsOversizedIncompressibleInput() {
        val random = Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val big = buildString {
            repeat(3000) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        assertNull(QrCodec.toQrPayload(big))
    }

    /** 用例 5：1000 字节重复 JSON → payload < 1000（证明真的压缩了）。 */
    @Test
    fun toQrPayload_reallyCompressesRepetitiveJson() {
        val json = """{"name":"课程名","day":1,"start":1,"end":2,"weeks":"1-18"}""".let { unit ->
            buildString { repeat(20) { append(unit) } }
        }
        assertTrue("input=${json.length}", json.length >= 1000)
        val payload = QrCodec.toQrPayload(json)!!
        assertTrue("payload=${payload.length}", payload.length < 1000)
        assertEquals(json, QrCodec.fromQrPayload(payload))
    }
}
