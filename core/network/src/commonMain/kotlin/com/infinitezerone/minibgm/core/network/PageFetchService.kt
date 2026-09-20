package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

/** 单次页面抓取的正文上限：够覆盖播放页源码，又不至于把抓取变成下载 */
const val MAX_PAGE_BYTES: Int = 512 * 1024

/** 抓取结果：[url] 为重定向后的最终地址，由调用方回填 Referer；[responseHeaders] 包含响应头（含合并的 Cookie） */
data class FetchedPage(
    val url: String,
    val html: String,
    val responseHeaders: Map<String, String> = emptyMap(),
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

    /** 发送表单 POST 请求并取回正文与响应头（Anime1 等播放接口用） */
    suspend fun postForm(
        url: String,
        formData: Map<String, String>,
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
                    extractFetchedPage(response)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "页面抓取失败 $target: ${e.message}" }
            null
        }
    }

    override suspend fun postForm(
        url: String,
        formData: Map<String, String>,
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
                .preparePost(target) {
                    header(HttpHeaders.Accept, "*/*")
                    header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                    safeHeaders.forEach { (name, value) -> header(name.trim(), value.trim()) }
                    setBody(
                        io.ktor.client.request.forms.FormDataContent(
                            io.ktor.http.Parameters.build {
                                formData.forEach { (k, v) -> append(k, v) }
                            },
                        ),
                    )
                }.execute { response ->
                    extractFetchedPage(response)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "表单提交失败 $target: ${e.message}" }
            null
        }
    }

    private suspend fun extractFetchedPage(response: io.ktor.client.statement.HttpResponse): FetchedPage? {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(MAX_PAGE_BYTES)
        var offset = 0
        while (offset < MAX_PAGE_BYTES) {
            val read = channel.readAvailable(buffer, offset, MAX_PAGE_BYTES - offset)
            if (read == -1) break
            offset += read
        }
        if (offset == 0) return null

        val respHeaders = mutableMapOf<String, String>()
        response.headers.forEach { name, values ->
            respHeaders[name] = values.joinToString(", ")
        }
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie)
        if (!setCookies.isNullOrEmpty()) {
            val cookieString = setCookies.joinToString("; ") { it.substringBefore(';').trim() }
            respHeaders["Cookie"] = cookieString
        }

        return FetchedPage(
            url =
                response.call.request.url
                    .toString(),
            html = buffer.decodeToString(0, offset),
            responseHeaders = respHeaders,
        )
    }
}

/** 头名/头值里出现 CR、LF 或 NUL 就能注入额外响应头，直接丢弃该条 */
private fun String.isSafeHeaderValue(): Boolean = all { it.code != 10 && it.code != 13 && it.code != 0 }
