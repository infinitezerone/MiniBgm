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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        // 一个请求预算覆盖"连接 + ECH 握手 + 响应头 + 响应体"全程。响应体是分块拉取的，
        // 必须逐块扣减剩余预算：本引擎声明了 HttpTimeoutCapability，Ktor 不会再用
        // socketTimeoutMillis 兜底，服务端在响应头之后卡住就会让请求永久悬挂。
        val deadlineNanos = System.nanoTime() + config.timeoutMillis * NANOS_PER_MILLI

        return withContext(dispatcher) {
            val urlString = data.url.toString()
            if (!urlString.startsWith("https://", ignoreCase = true)) {
                throw IOException("EchHttpClientEngine only supports https requests; got: $urlString")
            }
            val methodString = data.method.value

            val headerKeysList = ArrayList<String>()
            val headerValuesList = ArrayList<String>()

            data.headers.forEach { name, values ->
                values.forEach { v ->
                    headerKeysList.add(name)
                    headerValuesList.add(v)
                }
            }

            // body 自带的 Content-Type 必须由引擎折进头部。Ktor 的官方引擎（OkHttp / CIO）都会做这件事，
            // 本引擎此前只转抄 data.headers，于是 `setBody(ByteArrayContent(bytes, ct))` 的类型被丢掉，
            // 上游收到"有 body 但无 Content-Type"的请求，Fastify 一类框架会以 415 Unsupported Media Type 拒收。
            // 显式头部优先：只有调用方没自己写 Content-Type 时才补。
            val bodyContentType = data.body.contentType
            if (bodyContentType != null &&
                headerKeysList.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ) {
                headerKeysList.add(HttpHeaders.ContentType)
                headerValuesList.add(bodyContentType.toString())
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
            val echConfig = if (enableEch) EchConfigStore.getActiveConfig() else null

            val nativeCall =
                try {
                    EchNativeClient.nativeFetchCancellable(
                        url = urlString,
                        method = methodString,
                        headerKeys = headerKeysList.toTypedArray(),
                        headerValues = headerValuesList.toTypedArray(),
                        body = bodyBytes,
                        timeoutMs = remainingMillis(deadlineNanos),
                        targetAddrs = targetAddrs,
                        enableEch = enableEch,
                        requireEch = config.requireEch && enableEch,
                        echConfig = echConfig,
                    )
                } catch (cancellation: CancellationException) {
                    AdaptiveDnsResolver.recordFailure(host)
                    throw cancellation
                } catch (t: Throwable) {
                    AdaptiveDnsResolver.recordFailure(host)
                    logger.e(t) { "EchNativeClient.nativeFetch crashed for $urlString" }
                    throw IOException("ECH native fetch failed: ${t.message}", t)
                }

            val nativeResp = nativeCall.response
            if (nativeResp.errorMessage != null && nativeResp.errorMessage.isNotBlank()) {
                AdaptiveDnsResolver.recordFailure(host)
                logger.w { "ECH native fetch failed: ${nativeResp.errorMessage} ($urlString)" }
                throw IOException("ECH connection failed: ${nativeResp.errorMessage}")
            }

            // RFC 9849 §6.1.6 严格合规：
            // 服务端下发的 retry_configs 仅用于 Rust 底层单连接重试，绝不回填内存、绝不跨连接复用、
            // 绝不落盘持久化，杜绝 config_id 明文跨连接追踪向量（Tracking Vector）。
            // 跨请求性能由 H2 连接池多路复用与底层握手快速自愈保障。

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
                        val remaining = remainingMillis(deadlineNanos)
                        if (remaining <= 0L) {
                            throw IOException(
                                "ECH response body exceeded the ${config.timeoutMillis} ms request budget",
                            )
                        }
                        val chunk =
                            try {
                                withTimeout(remaining) {
                                    EchNativeClient.nativeReadBodyChunkCancellable(nativeCall.requestId)
                                }
                            } catch (timeout: TimeoutCancellationException) {
                                // 必须翻译成 IOException：TimeoutCancellationException 属于取消语义，
                                // 上层会把它当作"调用方主动取消"而不是"读超时"，两者的重试含义完全不同。
                                throw IOException(
                                    "ECH response body read timed out after ${config.timeoutMillis} ms",
                                    timeout,
                                )
                            }
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

    /** 剩余预算（毫秒，向下取整且不小于 0）。 */
    private fun remainingMillis(deadlineNanos: Long): Long = ((deadlineNanos - System.nanoTime()) / NANOS_PER_MILLI).coerceAtLeast(0L)

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
