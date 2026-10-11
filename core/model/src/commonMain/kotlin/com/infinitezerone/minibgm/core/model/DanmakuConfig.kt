package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 弹弹play 聚合弹幕配置：
 * - [endpoint]：弹幕服务器端点（空串表示使用默认官方端点 [DEFAULT_ENDPOINT]，支持配置社区自建/兼容端点如 danmu_api）；
 * - [appId]：弹弹play 开放平台应用 ID；
 * - [appSecret]：弹弹play 开放平台应用密钥（仅本地参与 SHA256 签名，不随网络请求传输）。
 */
@Serializable
data class DanmakuConfig(
    val endpoint: String = "",
    val appId: String = "",
    val appSecret: String = "",
) {
    val effectiveEndpoint: String
        get() = endpoint.trim().ifBlank { DEFAULT_ENDPOINT }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.dandanplay.net"
    }
}
