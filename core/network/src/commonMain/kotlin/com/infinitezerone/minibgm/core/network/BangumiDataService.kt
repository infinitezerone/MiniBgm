package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.model.BangumiDataItem
import com.infinitezerone.minibgm.core.model.BangumiDataRoot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

sealed interface BangumiDataResult {
    data class Success(
        val items: List<BangumiDataItem>,
        val etag: String?,
    ) : BangumiDataResult

    data object NotModified : BangumiDataResult
}

/** 单个月份切片的拉取结果：区分"未变更"与"该月不存在"，调用方据此决定是否复用本地缓存。 */
sealed interface BangumiDataMonthResult {
    data class Success(
        val items: List<BangumiDataItem>,
        val etag: String?,
    ) : BangumiDataMonthResult

    /** 带 If-None-Match 命中 304：内容未变，应复用本地已缓存的映射行。 */
    data object NotModified : BangumiDataMonthResult

    /** 该月切片不存在（未来月份）或所有 CDN 均不可达。 */
    data object NotFound : BangumiDataMonthResult
}

interface BangumiDataService {
    suspend fun getBangumiData(etag: String? = null): BangumiDataResult

    /**
     * 按需拉取单个月份切片（`data/items/YYYY/MM.json`，~几 KB 压缩），带 ETag 条件请求。
     * 时刻表映射只用得到在播番所在的那几个月，不需要固定窗口或全量下载。
     *
     * @param etag 上次该月的 ETag；命中返回 [BangumiDataMonthResult.NotModified]（0 字节）
     */
    suspend fun getMonthItems(
        year: Int,
        month: Int,
        etag: String? = null,
    ): BangumiDataMonthResult = BangumiDataMonthResult.NotFound
}

class BangumiDataServiceImpl(
    private val client: HttpClient,
    private val cdnUrls: List<String> = DEFAULT_CDN_URLS,
    private val cdnBases: List<String> = DEFAULT_CDN_BASES,
) : BangumiDataService {
    constructor(client: HttpClient, cdnUrl: String) : this(client, listOf(cdnUrl), DEFAULT_CDN_BASES)

    override suspend fun getBangumiData(etag: String?): BangumiDataResult {
        var lastException: Throwable? = null
        for (url in cdnUrls) {
            try {
                val response =
                    client.get(url) {
                        timeout {
                            requestTimeoutMillis = 60_000
                            connectTimeoutMillis = 15_000
                            socketTimeoutMillis = 60_000
                        }
                        if (!etag.isNullOrBlank()) {
                            header(HttpHeaders.IfNoneMatch, etag)
                        }
                    }

                if (response.status == HttpStatusCode.NotModified) {
                    return BangumiDataResult.NotModified
                }

                val newEtag = response.headers[HttpHeaders.ETag]
                val root: BangumiDataRoot = response.body()
                return BangumiDataResult.Success(root.items, newEtag)
            } catch (e: Throwable) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Failed to fetch bangumi-data from CDN endpoints")
    }

    override suspend fun getMonthItems(
        year: Int,
        month: Int,
        etag: String?,
    ): BangumiDataMonthResult {
        val monthStr = month.toString().padStart(2, '0')
        val path = "data/items/$year/$monthStr.json"
        for (base in cdnBases) {
            try {
                val url = if (base.endsWith("/")) "$base$path" else "$base/$path"
                val response =
                    client.get(url) {
                        timeout {
                            requestTimeoutMillis = 15_000
                            connectTimeoutMillis = 10_000
                            socketTimeoutMillis = 15_000
                        }
                        if (!etag.isNullOrBlank()) {
                            header(HttpHeaders.IfNoneMatch, etag)
                        }
                    }
                when (response.status) {
                    HttpStatusCode.NotModified -> return BangumiDataMonthResult.NotModified
                    HttpStatusCode.NotFound -> return BangumiDataMonthResult.NotFound
                    HttpStatusCode.OK -> {
                        val newEtag = response.headers[HttpHeaders.ETag]
                        return BangumiDataMonthResult.Success(response.body(), newEtag)
                    }
                    else -> Unit
                }
            } catch (_: Throwable) {
                // 尝试下一个 CDN 节点
            }
        }
        return BangumiDataMonthResult.NotFound
    }

    companion object {
        val DEFAULT_CDN_URLS =
            listOf(
                "https://cdn.jsdelivr.net/npm/bangumi-data@latest/dist/data.json",
                "https://fastly.jsdelivr.net/npm/bangumi-data@latest/dist/data.json",
                "https://gcore.jsdelivr.net/npm/bangumi-data@latest/dist/data.json",
            )

        val DEFAULT_CDN_BASES =
            listOf(
                "https://cdn.jsdelivr.net/gh/bangumi-data/bangumi-data@master/",
                "https://fastly.jsdelivr.net/gh/bangumi-data/bangumi-data@master/",
                "https://gcore.jsdelivr.net/gh/bangumi-data/bangumi-data@master/",
                "https://unpkg.com/bangumi-data@latest/",
            )
    }
}
