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

/**
 * 弹幕正文解析结果。
 *
 * 「解析失败」必须与「这一集确实没有弹幕」区分开：前者是可观测的故障（截断/畸形/错页），
 * 后者是正常空态。此前两者都坍缩成空列表，导致 512 KB 截断在日志里被记成"成功解析 0 条"。
 */
internal sealed interface DanmakuParseResult {
    data class Success(
        val items: List<DanmakuItem>,
    ) : DanmakuParseResult

    data class Failure(
        val reason: String,
    ) : DanmakuParseResult
}

object DanmakuParser {
    /**
     * 解析弹弹play JSON 响应体，保留失败原因（供仓库层记 warn、区分空态）。
     */
    internal fun parseDandanJsonResult(json: String): DanmakuParseResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return DanmakuParseResult.Failure("正文为空")
        if (!trimmed.startsWith("{")) {
            return DanmakuParseResult.Failure(
                "正文不是 JSON 对象（疑似错误页或编码异常）length=" + trimmed.length,
            )
        }
        // 截断的 JSON 往往仍能"看起来像 JSON"，但最后一条对象不会闭合；
        // 显式判尾字符，比等解码器抛 Unexpected EOF 更早、更容易归因。
        if (!trimmed.endsWith("}")) {
            return DanmakuParseResult.Failure("正文不完整（疑似被截断）length=" + trimmed.length)
        }
        val response =
            runCatching {
                BgmHttpClient.jsonConfig.decodeFromString<DandanCommentResponse>(trimmed)
            }.getOrElse { error ->
                return DanmakuParseResult.Failure("JSON 解码失败: " + (error.message ?: "未知错误"))
            }

        val items =
            response.comments
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
        return DanmakuParseResult.Success(items)
    }

    /**
     * 解析弹弹play JSON 响应体为弹幕列表；失败返回空列表（保留给既有调用方与测试）。
     * 需要区分失败与空态时用 [parseDandanJsonResult]。
     */
    fun parseDandanJson(json: String): List<DanmakuItem> =
        (parseDandanJsonResult(json) as? DanmakuParseResult.Success)?.items ?: emptyList()

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
