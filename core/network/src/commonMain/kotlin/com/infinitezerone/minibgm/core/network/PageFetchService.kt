package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

/** 单次页面抓取的正文上限：够覆盖播放页源码，又不至于把抓取变成下载 */
const val MAX_PAGE_BYTES: Int = 512 * 1024

/** 抓取结果：[url] 为重定向后的最终地址，由调用方回填 Referer */
data class FetchedPage(
    val url: String,
    val html: String,
)

/**
 * 第三方页面正文抓取（播放源解析用）。
 *
 * 只按调用方给定的单个 URL 取一次，不跟踪站点、不做批量爬取；
 * 正文流式读取，读满 [MAX_PAGE_BYTES] 即停，不把整页下载进内存；
 * 失败一律返回 null，由上层降级为"没有结果"，不向调用方抛业务异常。
 */
interface PageFetchService {
    /** [requestHeaders] 来自用户自备的播放规则；含 CR/LF/NUL 的头名或头值会被丢弃（防头注入） */
    suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): FetchedPage?
}

class PageFetchServiceImpl(
    private val client: HttpClient,
) : PageFetchService {
    private val logger = bgmLogger("Bgm/PageFetch")

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        val target = url.trim()
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val safeHeaders =
            requestHeaders.filter { (name, value) ->
                name.isNotBlank() && name.isSafeHeaderValue() && value.isSafeHeaderValue()
            }
        return try {
            client
                .prepareGet(target) {
                    header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                    safeHeaders.forEach { (name, value) -> header(name.trim(), value.trim()) }
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(MAX_PAGE_BYTES)
                    var offset = 0
                    while (offset < MAX_PAGE_BYTES) {
                        val read = channel.readAvailable(buffer, offset, MAX_PAGE_BYTES - offset)
                        if (read == -1) break
                        offset += read
                    }
                    if (offset == 0) {
                        null
                    } else {
                        // 重定向后的最终地址：解析层用它回填 Referer 与相对地址基准
                        FetchedPage(
                            url =
                                response.call.request.url
                                    .toString(),
                            html = buffer.decodeToString(0, offset),
                        )
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "页面抓取失败 $target: ${e.message}" }
            null
        }
    }
}

/** 头名/头值里出现 CR、LF 或 NUL 就能注入额外响应头，直接丢弃该条 */
private fun String.isSafeHeaderValue(): Boolean = all { it.code != 10 && it.code != 13 && it.code != 0 }
