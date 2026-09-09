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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

sealed interface BangumiDataResult {
    data class Success(
        val items: List<BangumiDataItem>,
        val etag: String?,
    ) : BangumiDataResult

    data object NotModified : BangumiDataResult
}

interface BangumiDataService {
    suspend fun getBangumiData(etag: String? = null): BangumiDataResult

    /** 获取近期在播季度的月份切片数据（默认覆盖过去 4 个月到未来 1 个月，仅 ~50KB） */
    suspend fun getRecentBangumiData(
        year: Int,
        month: Int,
        lookbackMonths: Int = 4,
        aheadMonths: Int = 1,
        etag: String? = null,
    ): BangumiDataResult = getBangumiData(etag)
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

    override suspend fun getRecentBangumiData(
        year: Int,
        month: Int,
        lookbackMonths: Int,
        aheadMonths: Int,
        etag: String?,
    ): BangumiDataResult =
        coroutineScope {
            val targetMonths = mutableListOf<Pair<Int, Int>>()
            for (offset in -lookbackMonths..aheadMonths) {
                var targetYear = year
                var targetMonth = month + offset
                while (targetMonth < 1) {
                    targetMonth += 12
                    targetYear -= 1
                }
                while (targetMonth > 12) {
                    targetMonth -= 12
                    targetYear += 1
                }
                targetMonths.add(targetYear to targetMonth)
            }

            val deferreds =
                targetMonths.map { (y, m) ->
                    async {
                        fetchMonthItems(y, m)
                    }
                }

            val items = deferreds.awaitAll().flatten().distinctBy { it.bgmSubjectId ?: it.title }
            if (items.isEmpty() && !etag.isNullOrBlank()) {
                BangumiDataResult.NotModified
            } else {
                val first = targetMonths.first()
                val last = targetMonths.last()
                val compositeEtag = "W/\"items-${first.first}${first.second}-${last.first}${last.second}\""
                BangumiDataResult.Success(items, compositeEtag)
            }
        }

    private suspend fun fetchMonthItems(
        year: Int,
        month: Int,
    ): List<BangumiDataItem> {
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
                    }
                if (response.status == HttpStatusCode.OK) {
                    return response.body<List<BangumiDataItem>>()
                }
                if (response.status == HttpStatusCode.NotFound) {
                    return emptyList()
                }
            } catch (_: Throwable) {
                // 尝试下一个 CDN 节点
            }
        }
        return emptyList()
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
