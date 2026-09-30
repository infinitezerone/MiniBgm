package com.infinitezerone.minibgm.core.network.oauth

import io.ktor.client.HttpClient

/**
 * 本地 OAuth 代理服务契约：
 * 为内嵌登录页面提供环回反向代理，配合原生 ECH 引擎绕开 GFW 对登录授权页的明文 SNI 阻断。
 */
interface OAuthProxyService {
    val isRunning: Boolean
    val port: Int?

    fun start(): Int

    fun stop()
}

expect fun createOAuthProxyService(client: HttpClient): OAuthProxyService
