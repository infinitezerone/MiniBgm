package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.withContext
import java.io.IOException

class EchEngineConfig : HttpClientEngineConfig() {
    var timeoutMillis: Long = 15000
}

/**
 * 基于 Rust ECH 原生协议栈的 Ktor 客户端引擎
 *
 * 专门用于穿透对 Cloudflare 节点（api.bgm.tv / bgm.tv）的 SNI 审查阻断与 DNS 污染。
 * 若原生库不可用或网络异常，抛出异常供上层捕获或重试。
 */
@OptIn(InternalAPI::class)
class EchHttpClientEngine(
    override val config: EchEngineConfig = EchEngineConfig(),
) : HttpClientEngineBase("EchHttpClientEngine") {
    private val logger = bgmLogger("Bgm/EchEngine")

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()

        return withContext(dispatcher) {
            val urlString = data.url.toString()
            val methodString = data.method.value

            val headerKeysList = ArrayList<String>()
            val headerValuesList = ArrayList<String>()

            data.headers.forEach { name, values ->
                values.forEach { v ->
                    headerKeysList.add(name)
                    headerValuesList.add(v)
                }
            }

            // 处理 Body
            val bodyBytes: ByteArray? =
                when (val body = data.body) {
                    is OutgoingContent.ByteArrayContent -> body.bytes()
                    is OutgoingContent.NoContent -> null
                    else -> null
                }

            if (!EchNativeClient.isAvailable()) {
                throw IOException("libminibgm_ech.so is not available on this device")
            }

            val nativeResp =
                try {
                    EchNativeClient.nativeFetch(
                        url = urlString,
                        method = methodString,
                        headerKeys = headerKeysList.toTypedArray(),
                        headerValues = headerValuesList.toTypedArray(),
                        body = bodyBytes,
                        timeoutMs = config.timeoutMillis,
                    )
                } catch (t: Throwable) {
                    logger.e(t) { "EchNativeClient.nativeFetch crashed for $urlString" }
                    throw IOException("ECH native fetch failed: ${t.message}", t)
                }

            if (nativeResp.errorMessage != null && nativeResp.errorMessage.isNotBlank()) {
                logger.w { "ECH native fetch failed: ${nativeResp.errorMessage} ($urlString)" }
                throw IOException("ECH connection failed: ${nativeResp.errorMessage}")
            }

            val responseHeadersBuilder = HeadersBuilder()
            for (i in nativeResp.headerKeys.indices) {
                val k = nativeResp.headerKeys[i]
                val v = nativeResp.headerValues[i]
                responseHeadersBuilder.append(k, v)
            }
            val responseHeaders: Headers = responseHeadersBuilder.build()

            val statusCode = HttpStatusCode.fromValue(nativeResp.statusCode)
            val responseBodyChannel = ByteReadChannel(nativeResp.body)

            HttpResponseData(
                statusCode = statusCode,
                requestTime = requestTime,
                headers = responseHeaders,
                version = HttpProtocolVersion.HTTP_1_1,
                body = responseBodyChannel,
                callContext = callContext,
            )
        }
    }
}
