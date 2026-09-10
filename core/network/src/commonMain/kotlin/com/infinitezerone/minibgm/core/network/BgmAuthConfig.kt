package com.infinitezerone.minibgm.core.network

import io.ktor.http.URLBuilder

/**
 * Bangumi OAuth 2.0 公开认证配置
 *
 * @property clientId Bangumi 开发者平台分配的公开 Client ID
 * @property redirectUri 客户端自定义 Scheme 回调地址（须与 Bangumi 开发者
 *   平台注册值以及 Worker 端注入的 REDIRECT_URI 常量一致）
 * @property tokenProxyUrl Cloudflare Worker 安全换票网关地址 (Client Secret 仅存储于 Worker 端)
 */
data class BgmAuthConfig(
    val clientId: String = "bgm69976a90e07dcf869",
    val redirectUri: String = "minibgm://oauth/callback",
    val tokenProxyUrl: String = "https://bgmplus-auth.zeronex.dpdns.org/oauth/token",
) {
    /**
     * 构造供浏览器 / Custom Tabs 打开的 Bangumi 官方网页授权地址。
     * 经 URLBuilder 统一百分号编码，避免 state/redirect_uri 手工拼接注入。
     *
     * @param state 用于防止 CSRF 跨站请求伪造与回调劫持的安全随机状态码
     * @param responseType 授权类型，默认 "code"
     */
    fun buildAuthorizeUrl(
        state: String,
        responseType: String = "code",
    ): String =
        URLBuilder(AUTHORIZE_ENDPOINT)
            .apply {
                parameters.append("client_id", clientId)
                parameters.append("response_type", responseType)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("state", state)
            }.buildString()

    private companion object {
        const val AUTHORIZE_ENDPOINT = "https://bgm.tv/oauth/authorize"
    }
}
