/*
 * QrImageReader.kt —— 二维码图片 → JSON 文本（M6 需求 6-B，导入侧的 UI 层装配）
 *
 * 分层要求（规格 §7.3）：importer 完全不感知二维码——「解图 → 还原成 JSON 文本」
 * 全部发生在 UI 层，importer 只收到一个普通文本载荷。本文件是 SAF 选图结果的唯一解码点：
 * decodeStream → QrCodec.decode → fromQrPayload → ImportPayload(text = json)。
 * 任何一步失败都返回 null（由调用方转可读失败提示，不崩溃）。
 */
package com.gould.xputimetable.ui.transfer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.gould.xputimetable.data.transfer.QrCodec
import com.gould.xputimetable.importer.api.ImportPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读相册选中的二维码截图 → 还原出课表 JSON 文本载荷。
 * 非本 App 二维码（无 XPUTT1: 前缀）、载荷损坏、读流/解图失败一律返回 null。
 */
internal suspend fun readQrImagePayload(context: Context, uri: Uri): ImportPayload? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null
            val payload = QrCodec.decode(bitmap) ?: return@runCatching null
            val json = QrCodec.fromQrPayload(payload) ?: return@runCatching null
            ImportPayload(
                text = json,
                uri = uri.toString(),
                displayName = null,
            )
        }.getOrNull()
    }
