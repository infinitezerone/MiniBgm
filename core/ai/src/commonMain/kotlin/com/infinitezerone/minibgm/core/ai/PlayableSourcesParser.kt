package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlinx.serialization.json.Json

/**
 * 从智能体回答中取出 `findPlayableSources` 原样转交的播放清单 JSON。
 *
 * 模型可能整段返回、裹在代码块里，或前后带一句说明，因此按"整段 → 代码块 → 首尾花括号"
 * 依次尝试；解析不出可播条目就返回 null，让上层按普通文本渲染。
 */
object PlayableSourcesParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private val CODE_BLOCK_REGEX = Regex("""```(?:json)?\s*([\s\S]*?)```""")

    fun extract(text: String): PlayableEpisodeList? {
        if (text.isBlank()) return null
        parse(text.trim())?.let { return it }
        CODE_BLOCK_REGEX.findAll(text).forEach { match ->
            parse(match.groupValues[1].trim())?.let { return it }
        }
        return braceSliced(text)?.let { parse(it) }
    }

    private fun braceSliced(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun parse(candidate: String): PlayableEpisodeList? =
        runCatching { json.decodeFromString<PlayableEpisodeList>(candidate) }
            .getOrNull()
            ?.takeIf { it.episodes.isNotEmpty() }
}
