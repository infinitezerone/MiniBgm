package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.resolveRequestBudgetMillis
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpRequestTimeoutException
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

/**
 * 交给 Rust 的超时比 Kotlin 侧预算多留的余量：
 * 让**超时边界由 Kotlin 侧掌握**——这样所有超时都从同一个入口（TimeoutCancellationException）
 * 收敛成 HttpRequestTimeoutException；Rust 的超时只作为最后兜底。
 */
private const val NATIVE_TIMEOUT_SLACK_MS = 500L

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
        val timeoutConfig = data.getCapabilityOrNull(HttpTimeoutCapability)
        val requestBudgetMillis = resolveRequestBudgetMillis(timeoutConfig, config.timeoutMillis)
        val socketTimeoutMillis = timeoutConfig?.socketTimeoutMillis ?: config.timeoutMillis
        val connectTimeoutMillis = timeoutConfig?.connectTimeoutMillis ?: 10_000L

        val deadlineNanos =
            if (requestBudgetMillis > 0L &&
                requestBudgetMillis != Long.MAX_VALUE
            ) {
                System.nanoTime() + requestBudgetMillis * NANOS_PER_MILLI
            } else {
                Long.MAX_VALUE
            }

        return withContext(dispatcher) {
            val urlString = data.url.toString()
            if (!urlString.startsWith("https://", ignoreCase = true)) {
                throw IOException("EchHttpClientEngine only supports https requests; got: $urlString")
            }
            val methodString = data.method.value

            // 1. 优先提取 Body 字节，确保请求体长度与格式确定
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

            val headerKeysList = ArrayList<String>()
            val headerValuesList = ArrayList<String>()

            // 2. 提取 Headers：过滤掉 Transfer-Encoding 与原有陈旧的 Content-Length，
            // 避免因 ReadChannelContent 携带 Transfer-Encoding: chunked 导致对端服务端在收到非 chunked 的裸 JSON 时等待超时
            data.headers.forEach { name, values ->
                if (!name.equals(HttpHeaders.TransferEncoding, ignoreCase = true) &&
                    !name.equals(HttpHeaders.ContentLength, ignoreCase = true)
                ) {
                    values.forEach { v ->
                        headerKeysList.add(name)
                        headerValuesList.add(v)
                    }
                }
            }

            // 3. body 自带的 Content-Type 必须由引擎折进头部
            val bodyContentType = data.body.contentType
            if (bodyContentType != null &&
                headerKeysList.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ) {
                headerKeysList.add(HttpHeaders.ContentType)
                headerValuesList.add(bodyContentType.toString())
            }

            // 4. 精确注入实际 bodyBytes 长度
            if (bodyBytes != null) {
                headerKeysList.add(HttpHeaders.ContentLength)
                headerValuesList.add(bodyBytes.size.toString())
            }

            if (!EchNativeClient.isAvailable()) {
                throw IOException("libminibgm_ech.so is not available on this device")
            }

            val host = data.url.host
            val port = data.url.port
            val enableEch = AdaptiveDnsResolver.isEchEligible(host)
            if (!enableEch) {
                // 非种子域名：后台 DoH 探测 ECH 能力（Type 65），**fail-open**——
                // 当前请求立刻按直连发出，探测命中后由后续请求自动升级走 ECH。
                // 严禁在这里等待探测结果：那是把不可控的 DoH 往返塞进主请求路径，
                // 未知图床首图会白等最多 1.5s（与「未知外部图床必须 Fail-Open 直连」红线冲突）。
                EchCapabilityDetector.probeAsyncIfUnknown(host)
            }
            val targetAddrs = AdaptiveDnsResolver.resolveTargetAddrs(host, port)
            val echConfig = if (enableEch) EchConfigStore.getActiveConfig() else null

            // 超时边界必须由 Kotlin 侧掌握：Rust 自己超时只会回一个 errorMessage，
            // 到 Kotlin 就退化成普通 IOException（分不清「超时」还是「连不上」），
            // 于是「超时最多重试一次」的上限与归因全部失效。
            val nativeBudget =
                if (deadlineNanos != Long.MAX_VALUE) {
                    remainingMillis(deadlineNanos).coerceAtLeast(connectTimeoutMillis)
                } else {
                    maxOf(connectTimeoutMillis, socketTimeoutMillis)
                }
            val nativeCall =
                try {
                    withTimeout(nativeBudget) {
                        EchNativeClient.nativeFetchCancellable(
                            url = urlString,
                            method = methodString,
                            headerKeys = headerKeysList.toTypedArray(),
                            headerValues = headerValuesList.toTypedArray(),
                            body = bodyBytes,
                            // Rust 侧留余量：正常情况永远由上面的 withTimeout 先触发，
                            // 它只在 Kotlin 计时器被系统挂起时兜底
                            timeoutMs = nativeBudget + NATIVE_TIMEOUT_SLACK_MS,
                            targetAddrs = targetAddrs,
                            enableEch = enableEch,
                            requireEch = config.requireEch && enableEch,
                            echConfig = echConfig,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    val isRequestTimeout =
                        cancellation is TimeoutCancellationException ||
                            (deadlineNanos != Long.MAX_VALUE && remainingMillis(deadlineNanos) <= 0L)
                    if (isRequestTimeout) {
                        AdaptiveDnsResolver.recordFailure(host)
                        // 自有超时必须翻译成 HttpRequestTimeoutException（IOException）：
                        // 直接抛 CancellationException 会被上层当成「调用方主动取消」——重试插件显式排除取消语义，
                        // 于是「一次超时」直接判死整次请求；调用方也无法区分「我们等超时了」和「用户取消了」。
                        // 与本文件 body 读取处（translate 成 IOException）保持同一套契约。
                        throw HttpRequestTimeoutException(
                            url = urlString,
                            timeoutMillis = requestBudgetMillis,
                            cause = cancellation,
                        )
                    }
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

            // RFC 9849 §6.1.8（Impact of Retry on Future Connections）严格合规：
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
                                "ECH response body exceeded the $requestBudgetMillis ms request budget",
                            )
                        }
                        val chunkTimeout =
                            if (deadlineNanos != Long.MAX_VALUE) {
                                minOf(socketTimeoutMillis, remaining)
                            } else {
                                socketTimeoutMillis
                            }
                        val chunk =
                            try {
                                withTimeout(chunkTimeout) {
                                    EchNativeClient.nativeReadBodyChunkCancellable(nativeCall.requestId)
                                }
                            } catch (timeout: TimeoutCancellationException) {
                                // 必须翻译成 IOException：TimeoutCancellationException 属于取消语义，
                                // 上层会把它当作"调用方主动取消"而不是"读超时"，两者的重试含义完全不同。
                                throw IOException(
                                    "ECH response body read timed out after $chunkTimeout ms",
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
    private fun remainingMillis(deadlineNanos: Long): Long {
        if (deadlineNanos == Long.MAX_VALUE) return Long.MAX_VALUE
        return ((deadlineNanos - System.nanoTime()) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
