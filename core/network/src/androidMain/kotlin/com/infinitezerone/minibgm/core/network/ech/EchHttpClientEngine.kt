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
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

class EchEngineConfig : HttpClientEngineConfig() {
    var timeoutMillis: Long = 15000

    /** Require ECH for eligible hosts instead of silently exposing the SNI on fallback. */
    var requireEch: Boolean = false
}

/**
 * 基于 Rust ECH 协议栈的 Ktor 客户端引擎。
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
                    is OutgoingContent.ReadChannelContent -> {
                        val channel = body.readFrom()
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = channel.readAvailable(buffer, 0, buffer.size)
                            if (count < 0) break
                            if (count > 0) output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    else -> throw IOException("ECH engine does not support streaming request body: ${body::class.qualifiedName}")
                }

            if (!EchNativeClient.isAvailable()) {
                throw IOException("libminibgm_ech.so is not available on this device")
            }

            val host = data.url.host
            val port = data.url.port
            val targetAddrs = AdaptiveDnsResolver.resolveTargetAddrs(host, port)
            val enableEch = AdaptiveDnsResolver.isEchEligible(host)
            val echConfig = if (enableEch) EchConfigStore.getActiveConfig(host) else null

            val nativeCall =
                try {
                    EchNativeClient.nativeFetchCancellable(
                        url = urlString,
                        method = methodString,
                        headerKeys = headerKeysList.toTypedArray(),
                        headerValues = headerValuesList.toTypedArray(),
                        body = bodyBytes,
                        timeoutMs = config.timeoutMillis,
                        targetAddrs = targetAddrs,
                        enableEch = enableEch,
                        requireEch = config.requireEch && enableEch,
                        echConfig = echConfig,
                    )
                } catch (t: Throwable) {
                    logger.e(t) { "EchNativeClient.nativeFetch crashed for $urlString" }
                    throw IOException("ECH native fetch failed: ${t.message}", t)
                }

            val nativeResp = nativeCall.response
            if (nativeResp.errorMessage != null && nativeResp.errorMessage.isNotBlank()) {
                logger.w { "ECH native fetch failed: ${nativeResp.errorMessage} ($urlString)" }
                throw IOException("ECH connection failed: ${nativeResp.errorMessage}")
            }

            if (!nativeResp.updatedEchConfig.isNullOrBlank()) {
                EchConfigStore.updateConfig(host, nativeResp.updatedEchConfig)
            }

            if (!nativeResp.connectedAddr.isNullOrBlank()) {
                AdaptiveDnsResolver.recordSuccess(host, nativeResp.connectedAddr)
            }

            val responseHeadersBuilder = HeadersBuilder()
            for (i in nativeResp.headerKeys.indices) {
                val k = nativeResp.headerKeys[i]
                val v = nativeResp.headerValues[i]
                responseHeadersBuilder.append(k, v)
            }
            val responseHeaders: Headers = responseHeadersBuilder.build()

            val statusCode = HttpStatusCode.fromValue(nativeResp.statusCode)
            val responseBodyChannel = ByteChannel(autoFlush = true)
            CoroutineScope(callContext).launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val chunk = EchNativeClient.nativeReadBodyChunkCancellable(nativeCall.requestId)
                        if (chunk == null || chunk.isEmpty()) break
                        responseBodyChannel.writeFully(chunk, 0, chunk.size)
                    }
                    responseBodyChannel.close()
                } catch (error: Throwable) {
                    responseBodyChannel.cancel(error)
                } finally {
                    EchNativeClient.nativeCancel(nativeCall.requestId)
                }
            }

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
