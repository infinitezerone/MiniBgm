package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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
 * 失败一律返回 null，由上层降级为"没有结果"，不向调用方抛业务异常。
 */
interface PageFetchService {
    suspend fun fetchHtml(url: String): FetchedPage?
}

class PageFetchServiceImpl(
    private val client: HttpClient,
) : PageFetchService {
    private val logger = bgmLogger("Bgm/PageFetch")

    override suspend fun fetchHtml(url: String): FetchedPage? {
        val target = url.trim()
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return try {
            val bytes =
                client
                    .get(target) {
                        header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                        header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                    }.body<ByteArray>()
            if (bytes.isEmpty()) return null
            FetchedPage(
                url = target,
                html = bytes.decodeToString(0, minOf(bytes.size, MAX_PAGE_BYTES)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "页面抓取失败 $target: ${e.message}" }
            null
        }
    }
}
