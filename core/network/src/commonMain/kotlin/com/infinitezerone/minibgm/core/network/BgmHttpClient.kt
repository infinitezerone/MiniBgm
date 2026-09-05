package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object BgmHttpClient {
    val jsonConfig =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
            prettyPrint = true
        }

    /**
     * 构建 Ktor client。
     *
     * @param tokenRefresher 为 null 时构建"token client"：不带 Auth 插件，
     *   专供 [BgmTokenService] 走 Worker 兑换/刷新，避免刷新请求自身携带
     *   过期凭据引发 401 递归；非 null 时构建业务 API client：自动注入
     *   Bearer，401 时经 [tokenRefresher] 刷新并重试。刷新成功时写入新凭据；
     *   回调返回 null 表示凭据已不可恢复：清除本地凭据（登录态随之翻转，
     *   完成自动登出闭环）并沿用原始 401 上抛。
     * @param enableLogging 仅 debug 构建开启；LogLevel.INFO 只记录请求
     *   生命周期，不含 header 与 body，不会泄漏凭据。
     * @param userAgent 必填：由 app 层以 BuildConfig.VERSION_NAME 拼装，
     *   版本号随构建自动更新；commonMain 读不到 BuildConfig 故不设默认值。
     * @param engine 测试注入点（MockEngine）；默认 CIO 生产引擎。
     */
    fun create(
        tokenProvider: TokenProvider,
        enableLogging: Boolean = false,
        userAgent: String,
        tokenRefresher: (suspend (oldRefreshToken: String) -> BgmTokenPair?)? = null,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        fun HttpClientConfig<*>.bgmConfiguration() {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = if (enableLogging) LogLevel.ALL else LogLevel.NONE
            }
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
            if (tokenRefresher != null) {
                install(Auth) {
                    bearer {
                        // 主动在所有请求携带 Bearer Token，避免因等待 401 挑战被 HttpResponseValidator 提前拦截
                        sendWithoutRequest { true }
                        loadTokens {
                            tokenProvider.getAccessToken()?.let { accessToken ->
                                BearerTokens(accessToken, tokenProvider.getRefreshToken().orEmpty())
                            }
                        }
                        refreshTokens {
                            val oldRefreshToken = tokenProvider.getRefreshToken()
                            val refreshed =
                                oldRefreshToken
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { tokenRefresher(it) }
                            if (refreshed == null) {
                                // 凭据已不可恢复（无 refresh token 或刷新被拒）：清除本地
                                // token 让 isLoggedIn 翻转为 false，完成自动登出闭环——
                                // 响应式登录态只能由凭据存储驱动，Unauthorized 仅用于错误提示
                                tokenProvider.clearTokens()
                                return@refreshTokens null
                            }
                            tokenProvider.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                            BearerTokens(refreshed.accessToken, refreshed.refreshToken)
                        }
                    }
                }
            }
            HttpResponseValidator {
                validateResponse { response ->
                    when (response.status) {
                        HttpStatusCode.Unauthorized -> throw BgmNetworkException.Unauthorized()
                        HttpStatusCode.Forbidden -> throw BgmNetworkException.Forbidden()
                        HttpStatusCode.NotFound -> throw BgmNetworkException.NotFound()
                        HttpStatusCode.TooManyRequests -> throw BgmNetworkException.RateLimited()
                        HttpStatusCode.InternalServerError,
                        HttpStatusCode.BadGateway,
                        HttpStatusCode.ServiceUnavailable,
                        -> throw BgmNetworkException.ServerError(response.status.value)
                    }
                }
            }
        }
        return engine?.let { HttpClient(it) { bgmConfiguration() } }
            ?: HttpClient(CIO) { bgmConfiguration() }
    }
}
