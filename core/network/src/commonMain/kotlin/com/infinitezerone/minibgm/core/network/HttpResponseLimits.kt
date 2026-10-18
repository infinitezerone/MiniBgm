package com.infinitezerone.minibgm.core.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable

/**
 * 读取响应正文，最多 [maxBytes]。
 *
 * 超限时截断而不是抛错：调用方都是"解析失败即降级"的路径（JSON 截断后解析失败
 * 会自然落到空结果），截断比"先读满内存再判超限"少一次判断，也不会把半截正文
 * 当成成功。
 */
internal suspend fun HttpResponse.readLimitedText(maxBytes: Int): String {
    val channel = bodyAsChannel()
    val buffer = ByteArray(maxBytes)
    var offset = 0
    while (offset < buffer.size) {
        val read = channel.readAvailable(buffer, offset, buffer.size - offset)
        if (read <= 0) break
        offset += read
    }
    return buffer.decodeToString(0, offset)
}
