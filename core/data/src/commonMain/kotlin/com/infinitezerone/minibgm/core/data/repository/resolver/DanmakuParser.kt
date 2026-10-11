package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMode
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.serialization.Serializable

@Serializable
internal data class DandanComment(
    val cid: Long = 0L,
    val p: String = "",
    val m: String = "",
)

@Serializable
internal data class DandanCommentResponse(
    val count: Int = 0,
    val comments: List<DandanComment> = emptyList(),
)

object DanmakuParser {
    /**
     * 解析弹弹play JSON 响应体为弹幕列表。
     */
    fun parseDandanJson(json: String): List<DanmakuItem> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return emptyList()
        val response =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<DandanCommentResponse>(trimmed)
            }.getOrNull() ?: return emptyList()

        return response.comments
            .mapNotNull { comment ->
                val text = comment.m.trim()
                if (text.isEmpty()) return@mapNotNull null
                val parts = comment.p.split(",")
                val timeSec = parts.firstOrNull()?.toFloatOrNull() ?: return@mapNotNull null
                val modeCode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val colorCode = parts.getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF
                val mode =
                    when (modeCode) {
                        4 -> DanmakuMode.BOTTOM
                        5 -> DanmakuMode.TOP
                        else -> DanmakuMode.SCROLL
                    }
                DanmakuItem(
                    timeMs = (timeSec * 1000).toLong(),
                    text = text,
                    color = colorCode,
                    mode = mode,
                )
            }.sortedBy { it.timeMs }
    }

    private val XML_DANMAKU_REGEX = Regex("""<d\s+p="([^"]+)"[^>]*>([^<]+)</d>""", RegexOption.IGNORE_CASE)

    /**
     * 解析 Bilibili / XML 格式弹幕文本。
     */
    fun parseXml(xml: String): List<DanmakuItem> {
        if (!xml.contains("<d p=", ignoreCase = true)) return emptyList()
        return XML_DANMAKU_REGEX
            .findAll(xml)
            .mapNotNull { match ->
                val p = match.groupValues[1]
                val text = match.groupValues[2].trim()
                if (text.isEmpty()) return@mapNotNull null
                val parts = p.split(",")
                val timeSec = parts.firstOrNull()?.toFloatOrNull() ?: return@mapNotNull null
                val modeCode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val colorCode = parts.getOrNull(3)?.toIntOrNull() ?: 0xFFFFFF
                val mode =
                    when (modeCode) {
                        4 -> DanmakuMode.BOTTOM
                        5 -> DanmakuMode.TOP
                        else -> DanmakuMode.SCROLL
                    }
                DanmakuItem(
                    timeMs = (timeSec * 1000).toLong(),
                    text = text,
                    color = colorCode,
                    mode = mode,
                )
            }.sortedBy { it.timeMs }
            .toList()
    }
}
