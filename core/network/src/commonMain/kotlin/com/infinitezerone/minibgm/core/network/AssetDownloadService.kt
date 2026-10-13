package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * 资产与大文件 HTTP 流式下载网络服务契约。
 */
interface AssetDownloadService {
    /**
     * 发起流式下载。
     *
     * @param url 下载目标直链
     * @param rangeStart 断点续传起始字节偏移量（0 表示全量下载）
     * @param onChunk 每一分块读取回调 (buffer, readLength, totalBytes, isPartial)
     * @return 本次请求流读取的总字节数
     */
    suspend fun streamAsset(
        url: String,
        rangeStart: Long = 0L,
        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
    ): Long
}

internal fun isLoopbackHttpUrl(url: String): Boolean {
    val normalized = url.trim().lowercase()
    return normalized.startsWith("http://127.0.0.1:") ||
        normalized.startsWith("http://127.0.0.1/") ||
        normalized.startsWith("http://localhost:") ||
        normalized.startsWith("http://localhost/") ||
        normalized.startsWith("http://[::1]:") ||
        normalized.startsWith("http://[::1]/")
}

internal class AssetDownloadServiceImpl(
    private val client: HttpClient,
    private val loopbackClient: HttpClient? = null,
) : AssetDownloadService {
    private val logger = bgmLogger("Bgm/AssetDownloadService")

    private val defaultLoopbackClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout)
        }
    }

    override suspend fun streamAsset(
        url: String,
        rangeStart: Long,
        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
    ): Long {
        val isLoopback = isLoopbackHttpUrl(url)
        // 本地回环 HTTP 流（如 librqbit 本地流媒体服务 http://127.0.0.1:<port>/...）必须走纯流式 CIO 引擎：
        // 1. EchHttpClientEngine 仅允许 https:// 外部请求，拒绝明文 http://；
        // 2. EchHttpClientEngine 会在 JNI 层一次性聚合完整响应体，不适合 GB 级本地视频流；
        // 3. 单测若显式注入 MockEngine 则保留给 MockEngine 拦截断言。
        val activeClient =
            if (isLoopback &&
                !client.engine::class
                    .simpleName
                    .orEmpty()
                    .contains("Mock")
            ) {
                loopbackClient ?: defaultLoopbackClient
            } else {
                client
            }

        return activeClient
            .prepareGet(url) {
                if (rangeStart > 0L) {
                    header(HttpHeaders.Range, "bytes=$rangeStart-")
                }
                timeout {
                    requestTimeoutMillis = null
                    // P2P 本地流在寻道/连接 Peer 阶段可能超过 30s 才产出首个顺序分片，放宽回环流读取超时至 120s
                    socketTimeoutMillis = if (isLoopback) 120_000L else 30_000L
                    connectTimeoutMillis = 15_000L
                }
            }.execute { response ->
                val status = response.status
                if (status == HttpStatusCode.RequestedRangeNotSatisfiable) {
                    throw IOException("HTTP 416 Range Not Satisfiable")
                }

                val isPartial = status == HttpStatusCode.PartialContent
                val isOk = status == HttpStatusCode.OK

                if (!isPartial && !isOk) {
                    throw IOException("HTTP ${status.value}: ${response.status.description}")
                }

                val streamLength = response.contentLength() ?: -1L
                val totalBytes =
                    if (isPartial && streamLength > 0L) {
                        rangeStart + streamLength
                    } else if (isOk && streamLength > 0L) {
                        streamLength
                    } else {
                        -1L
                    }

                val channel = response.bodyAsChannel()
                val buffer = ByteArray(32 * 1024)
                var totalStreamRead = 0L

                while (!channel.isClosedForRead && currentCoroutineContext().isActive) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    onChunk(buffer, read, totalBytes, isPartial)
                    totalStreamRead += read
                }

                totalStreamRead
            }
    }
}
