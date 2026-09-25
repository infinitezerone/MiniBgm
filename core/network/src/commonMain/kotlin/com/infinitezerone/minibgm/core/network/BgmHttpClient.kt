package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

object BgmHttpClient {
    /**
     * 全仓库共享的 JSON 序列化单例（白名单见 ArchitectureRulesTest 的 Json 红线测试）：
     * 不可信输入宽容解析 + 编码带上默认值 + 紧凑输出。
     * 新增 `Json { ... }` 实例前先问一句：能不能直接引用这里？
     */
    val jsonConfig =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }

    /**
     * 为 HttpClient 安装通用的底层基础网络插件：
     * - [ContentEncoding]：自动透明处理 gzip / deflate 响应压缩流，防止数据截断；
     * - [HttpRequestRetry]：针对网络抖动、偶发中断及服务端 5xx 进行指数退避重试；
     * - [Logging]：统一受控的请求日志，Debug 记录生命周期，Release 静默。
     */
    fun HttpClientConfig<*>.applyBaseNetworkConfig(
        enableLogging: Boolean = false,
        loggerTag: String = "Bgm/Network",
    ) {
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response ->
                response.status == HttpStatusCode.TooManyRequests || response.status.value in 500..599
            }
            retryOnExceptionIf { _, cause ->
                cause !is CancellationException
            }
            exponentialDelay(
                base = 2.0,
                maxDelayMs = 15000,
                randomizationMs = 500,
            )
        }
        val networkLogger = bgmLogger(loggerTag)
        install(Logging) {
            logger =
                object : Logger {
                    override fun log(message: String) {
                        networkLogger.d { message }
                    }
                }
            level = if (enableLogging) LogLevel.INFO else LogLevel.NONE
        }
    }

    /**
     * 创建轻量级纯净 Base HttpClient，具备完整的解压、重试与日志基础能力，
     * 不附带 Bangumi 业务特定的 Bearer Auth 或 HttpResponseValidator。
     */
    fun createBaseClient(
        engine: HttpClientEngine? = null,
        enableLogging: Boolean = false,
        userAgent: String? = null,
        loggerTag: String = "Bgm/Network",
        block: (HttpClientConfig<*>.() -> Unit)? = null,
    ): HttpClient {
        val config: HttpClientConfig<*>.() -> Unit = {
            applyBaseNetworkConfig(enableLogging = enableLogging, loggerTag = loggerTag)
            if (!userAgent.isNullOrBlank()) {
                install(DefaultRequest) {
                    header(HttpHeaders.UserAgent, userAgent)
                }
            }
            block?.invoke(this)
        }
        return engine?.let {
            HttpClient(it, config)
        } ?: HttpClient(CIO, config)
    }

    /**
     * 构建 Ktor client。
     *
     * @param tokenRefresher 为 null 时构建"token client"：不带 Auth 插件，
     *   专供 [BgmTokenService] 走 Worker 兑换/刷新，避免刷新请求自身携带
     *   过期凭据引发 401 递归；非 null 时构建业务 API client：自动注入
     *   Bearer，401 时经 [tokenRefresher] 刷新并重试。刷新成功时写入新凭据；
     *   回调返回 null 表示凭据已不可恢复：清除本地凭据（登录态随凭据库翻转，
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
            install(HttpCache)
            applyBaseNetworkConfig(enableLogging = enableLogging, loggerTag = "Bgm/Network")
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
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
                                // token 让登录态（由凭据库派生）翻转，完成自动登出闭环——
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
                handleResponseExceptionWithRequest { cause, _ ->
                    if (cause is CancellationException) return@handleResponseExceptionWithRequest
                    if (cause is BgmNetworkException) return@handleResponseExceptionWithRequest
                    throw cause.toBgmNetworkException()
                }
            }
        }
        return engine?.let { HttpClient(it) { bgmConfiguration() } }
            ?: HttpClient(CIO) { bgmConfiguration() }
    }
}
