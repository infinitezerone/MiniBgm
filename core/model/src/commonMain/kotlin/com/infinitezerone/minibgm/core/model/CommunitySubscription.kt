package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 社区开源订阅与动态发现的播放源规则。
 *
 * @property name 站点或播放源名称（如 "AGE动漫"、"樱花动漫"）
 * @property urlTemplate 播放/搜索 URL 模板
 * @property description 站点描述与适用范围说明
 * @property latencyMs 测速延迟（毫秒），0 表示未测速
 * @property isAlive 是否通过连通性与健康度探活
 */
@Serializable
data class DiscoveredSource(
    val name: String,
    val urlTemplate: String,
    val description: String = "",
    val latencyMs: Long = 0L,
    val isAlive: Boolean = true,
) {
    /**
     * 转换为本地标准 [PlaybackSourceRule]。
     */
    fun toPlaybackSourceRule(id: String): PlaybackSourceRule =
        PlaybackSourceRule(
            id = id,
            name = name,
            urlTemplate = urlTemplate,
            isEnabled = true,
            description = description,
        )
}
