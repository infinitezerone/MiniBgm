package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 社区开源订阅与动态发现的播放源规则。
 *
 * @property name 站点或播放源名称
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

/**
 * 公网检索发现的候选订阅信息。
 */
@Serializable
data class DiscoveredSubscriptionCandidate(
    val name: String,
    val subscriptionUrl: String,
    val description: String = "",
    val sourceCount: Int = 0,
    val aliveCount: Int = 0,
    val averageLatencyMs: Long = 0L,
    val sampleSources: List<String> = emptyList(),
)

/**
 * 订阅校验与连通性探活报告。
 */
@Serializable
data class SubscriptionValidationReport(
    val isHealthy: Boolean,
    val subscriptionUrl: String,
    val totalRules: Int = 0,
    val aliveRules: Int = 0,
    val averageLatencyMs: Long = 0L,
    val sources: List<DiscoveredSource> = emptyList(),
    val errorMessage: String? = null,
)
