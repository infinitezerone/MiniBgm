package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
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
) : AssetDownloadService {
    private val logger = bgmLogger("Bgm/AssetDownloadService")

    override suspend fun streamAsset(
        url: String,
        rangeStart: Long,
        onChunk: suspend (bytes: ByteArray, length: Int, totalBytes: Long, isPartial: Boolean) -> Unit,
    ): Long {
        val isLoopback = isLoopbackHttpUrl(url)
        // 回环流（如 librqbit 的 http://127.0.0.1:<port>/...）由平台 RoutingEngine 分流到
        // 纯流式 CIO，本服务不再自建 client、也不再看 engine 类名做特判；这里只负责
        // 下载语义（Range / 无限请求预算 / 回环读取超时放宽）。
        return client
            .prepareGet(url) {
                if (rangeStart > 0L) {
                    header(HttpHeaders.Range, "bytes=$rangeStart-")
                }
                timeout {
                    // 必须用 INFINITE_TIMEOUT_MS 而不是 null：Ktor 的 HttpTimeout 插件会把
                    // per-request 的 null 回填成 client 级默认值（这里 30s），否则大文件下载
                    // 会在第 30 秒被请求预算掐断。真正的停滞由下面的 socketTimeout 兜底。
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
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
