/*
 * QrCodec.kt —— 课表二维码的载荷编解码与位图生成/识别（M6 需求 6-B）
 *
 * 方案（§7.1 已实测裁决，勿改）：deflate(level 9) → base64 → 单张二维码，
 * 载荷前缀 "XPUTT1:"（扫码/选图时快速判断"这是我们 App 的二维码"，避免误报解析）。
 * 超过 2000 字节（含前缀总长）不做分片——直接拒绝并让 UI 提示改用文件导出。
 *
 * 纯函数部分（toQrPayload/fromQrPayload）可在 JVM 单测；Bitmap 部分依赖 Android，
 * 不做单测。本期不接相机（零新权限），导入走 SAF 选相册截图。
 */
package com.gould.xputimetable.data.transfer

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object QrCodec {

    private const val PREFIX = "XPUTT1:"

    /** 单张二维码的载荷上限（含前缀后的总字节数，§7.1：版本 25/M 级留 5% 余量）。 */
    const val MAX_PAYLOAD_BYTES = 2000

    /** JSON 文本 → 二维码载荷；超过 [MAX_PAYLOAD_BYTES] 返回 null（不分片）。 */
    fun toQrPayload(json: String): String? {
        val payload = PREFIX + Base64.getEncoder().encodeToString(deflate(json.toByteArray(Charsets.UTF_8)))
        return if (payload.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) null else payload
    }

    /** 二维码载荷 → JSON 文本；前缀不符 / base64 非法 / 解压失败一律返回 null（不抛）。 */
    fun fromQrPayload(payload: String): String? {
        if (!payload.startsWith(PREFIX)) return null
        val bytes = runCatching {
            Base64.getDecoder().decode(payload.removePrefix(PREFIX))
        }.getOrNull() ?: return null
        return runCatching { inflate(bytes).toString(Charsets.UTF_8) }.getOrNull()
    }

    /** 载荷 → 二维码 Bitmap（ECC M / 边距 1 模块）；任何失败返回 null（不抛）。 */
    fun encode(text: String, sizePx: Int = 720): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                // 二维码本体必须纯黑纯白才可扫——这是「无硬编码颜色」纪律的唯一例外
                pixels[y * sizePx + x] =
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Bitmap → 载荷文本（相册截图识别）；识别失败返回 null（不抛）。 */
    fun decode(bitmap: Bitmap): String? = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.TRY_HARDER to true))
        }
        reader.decodeWithState(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
        ).text
    }.getOrNull()

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            DeflaterOutputStream(out, deflater).use { it.write(data) }
        } finally {
            deflater.end()
        }
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray =
        InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
