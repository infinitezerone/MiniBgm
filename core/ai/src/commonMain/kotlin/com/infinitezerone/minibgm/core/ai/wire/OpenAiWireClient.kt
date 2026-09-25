package com.infinitezerone.minibgm.core.ai.wire

import com.infinitezerone.minibgm.core.ai.resolveApiBase
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.model.AiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * 轻量级原生 Wire 协议客户端，对齐 OpenAI 标准 /chat/completions 接口，零第三方框架依赖。
 */
class OpenAiWireClient(
    val httpClient: HttpClient = defaultHttpClient,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        },
) {
    companion object {
        private const val TIMEOUT_MS = 120_000L

        val defaultHttpClient: HttpClient by lazy {
            HttpClient(CIO) {
                val httpLogger = bgmLogger("Bgm/AiHttp")
                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                httpLogger.d { message }
                            }
                        }
                    level = LogLevel.INFO
                }
                install(io.ktor.client.plugins.compression.ContentEncoding) {
                    gzip()
                    deflate()
                }
                install(io.ktor.client.plugins.HttpRequestRetry) {
                    maxRetries = 2
                    retryIf { _, response ->
                        response.status.value in 500..599
                    }
                    retryOnExceptionIf { _, cause ->
                        cause !is kotlinx.coroutines.CancellationException
                    }
                    exponentialDelay(
                        base = 2.0,
                        maxDelayMs = 10_000,
                        randomizationMs = 500,
                    )
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = TIMEOUT_MS
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = TIMEOUT_MS
                }
            }
        }
    }

    suspend fun chatCompletion(
        config: AiConfig,
        request: WireChatRequest,
    ): WireChatResponse {
        val chatUrl = buildChatCompletionsUrl(config.endpoint, config.provider)
        val bodyText = json.encodeToString(WireChatRequest.serializer(), request)

        val response =
            httpClient.post(chatUrl) {
                contentType(ContentType.Application.Json)
                if (config.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${config.apiKey.trim()}")
                }
                setBody(bodyText)
            }

        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: $responseText")
        }

        return json.decodeFromString(WireChatResponse.serializer(), responseText)
    }

    internal fun buildChatCompletionsUrl(
        rawEndpoint: String,
        provider: String,
    ): String {
        val isGemini = provider.equals(AiConfig.PROVIDER_GEMINI, ignoreCase = true)
        val isOllama = provider.equals(AiConfig.PROVIDER_OLLAMA, ignoreCase = true)
        val defaultBase =
            when {
                isOllama -> "http://10.0.2.2:11434"
                isGemini -> "https://generativelanguage.googleapis.com/v1beta/openai"
                else -> "https://api.openai.com/v1"
            }

        val endpoint = rawEndpoint.ifBlank { defaultBase }
        val base = resolveApiBase(endpoint, provider)
        return "$base/chat/completions"
    }
}
