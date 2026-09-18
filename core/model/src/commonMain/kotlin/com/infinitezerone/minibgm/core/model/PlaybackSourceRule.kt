package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 自定义番剧播放/跳转规则数据模型。
 *
 * [urlTemplate] 支持通用占位符：
 * - `{title}`: 番剧名称（将进行 URL 编码）
 * - `{ep}`: 分集编号（如 1, 2）
 * - `{subjectId}`: Bangumi 条目 ID
 * - `{episodeId}`: Bangumi 分集 ID
 */
@Serializable
data class PlaybackSourceRule(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val isEnabled: Boolean = true,
    val description: String = "",
) {
    /**
     * 针对具体分集安全替换占位符并返回解析后的目标 URL。
     */
    fun resolveUrl(
        title: String,
        ep: String,
        subjectId: Long = 0L,
        episodeId: Long = 0L,
    ): String =
        urlTemplate
            .replace("{title}", encodeParam(title))
            .replace("{ep}", encodeParam(ep))
            .replace("{subjectId}", subjectId.toString())
            .replace("{episodeId}", episodeId.toString())

    companion object {
        fun encodeParam(value: String): String =
            buildString {
                for (byte in value.encodeToByteArray()) {
                    val code = byte.toInt() and 0xFF
                    if (isUnreserved(code)) {
                        append(code.toChar())
                    } else {
                        append('%')
                        val hex = code.toString(16).uppercase()
                        if (hex.length == 1) append('0')
                        append(hex)
                    }
                }
            }

        private fun isUnreserved(code: Int): Boolean =
            (code in 'a'.code..'z'.code) ||
                (code in 'A'.code..'Z'.code) ||
                (code in '0'.code..'9'.code) ||
                code == '-'.code ||
                code == '_'.code ||
                code == '.'.code ||
                code == '~'.code
    }
}
