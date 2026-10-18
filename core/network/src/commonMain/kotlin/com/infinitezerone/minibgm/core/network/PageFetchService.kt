package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.BgmDispatchers
import com.infinitezerone.minibgm.core.common.bgmLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 单次页面抓取的正文上限：够覆盖播放页源码，又不至于把抓取变成下载 */
const val MAX_PAGE_BYTES: Int = 512 * 1024

/** 手动跟随 30x 的最大跳数；超过按不可信处理 */
private const val MAX_REDIRECTS = 5

/**
 * 一条重定向链的总预算。
 *
 * 单跳超时只能约束一次请求；6 跳各卡在超时边界时，调用方要干等 6 倍。
 * 超预算按"抓取失败"降级，与逐跳失败同一出口。
 */
private const val MAX_REDIRECT_CHAIN_MILLIS = 30_000L

/** 抓取结果：[url] 为重定向后的最终地址，由调用方回填 Referer；[responseHeaders] 包含响应头（含合并的 Cookie） */
data class FetchedPage(
    val url: String,
    val html: String,
    val responseHeaders: Map<String, String> = emptyMap(),
)

/**
 * 直链首包探测结论。
 *
 * 与 [FetchedPage] 的区别：探测**不读正文**，只取响应头（状态码 + Content-Type），
 * 用于回答"抽到的地址是不是真的能播"，而不是"页面里有什么"。
 */
sealed interface StreamProbe {
    /** 拿到响应：状态码与非纯文本的 Content-Type（已去掉 `;charset=` 之后的部分） */
    data class Responded(
        val status: Int,
        val contentType: String?,
    ) : StreamProbe

    /** 请求失败（不可达 / 被拒绝 / 超时） */
    data class Failed(
        val reason: String,
    ) : StreamProbe

    /** 该 PageFetchService 实现不支持探测（测试替身等），调用方不应据此下结论 */
    data object Unsupported : StreamProbe
}

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

    /**
     * 带**载荷预算**的抓取。[maxBytes] 由调用方声明，不是全局常量：
     * HTML 页面用 [MAX_PAGE_BYTES] 防止把抓取变成下载；协议型 JSON（弹幕正文等）必须给出足够大的预算，
     * 否则会被静默截断成"解析失败 = 这一集没有数据"。
     *
     * 默认实现忽略预算（测试替身不必关心容量），生产实现按预算截断正文并保留原语义。
     */
    suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
        maxBytes: Int,
    ): FetchedPage? = fetchHtml(url, requestHeaders)

    /** 发送表单 POST 请求并取回正文与响应头（部分播放接口用） */
    suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        requestHeaders: Map<String, String> = emptyMap(),
    ): FetchedPage?

    /**
     * 直链首包探测：只取响应头，`Range: bytes=0-2047` 顺带限制服务端回包体积。
     *
     * 默认返回 [StreamProbe.Unsupported]，这样测试替身与不需要该能力的实现不必被迫实现，
     * 调用方也能区分"探测不了"与"探测到不能播"。
     */
    suspend fun probeStream(
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): StreamProbe = StreamProbe.Unsupported
}

internal class PageFetchServiceImpl(
    /** 禁止自动跟随 30x 的 client：每一跳都要先过 [UrlSafety]，防止规则页跳进内网 */
    private val untrustedClient: HttpClient,
    /** 单条重定向链的总预算；测试注入小值验证超时降级 */
    private val redirectChainMillis: Long = MAX_REDIRECT_CHAIN_MILLIS,
    /** 正文读取/解码的调度器：绝不留在调用方（可能是 Main）线程上 */
    private val dispatchers: BgmDispatchers = BgmDispatchers(),
) : PageFetchService {
    private val logger = bgmLogger("Bgm/PageFetch")

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? = fetchHtml(url, requestHeaders, MAX_PAGE_BYTES)

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
        maxBytes: Int,
    ): FetchedPage? {
        val target = url.trim()
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return null
        }
        // 规则页抓取是"第三方内容驱动"的请求：本机/私网目标一律拒绝，避免恶意订阅扫内网
        if (!UrlSafety.isAllowedRemoteUrl(target)) return null
        val safeHeaders =
            requestHeaders.filter { (name, value) ->
                name.isNotBlank() && name.isSafeHeaderValue() && value.isSafeHeaderValue()
            }
        return try {
            val response =
                getFollowingSafeRedirects(
                    startUrl = target,
                    headers = safeHeaders,
                    accept = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
                ) ?: return null
            extractFetchedPage(response, maxBytes.coerceAtLeast(1))
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
        // 规则页抓取是"第三方内容驱动"的请求：本机/私网目标一律拒绝，避免恶意订阅扫内网
        if (!UrlSafety.isAllowedRemoteUrl(target)) return null
        val safeHeaders =
            requestHeaders.filter { (name, value) ->
                name.isNotBlank() && name.isSafeHeaderValue() && value.isSafeHeaderValue()
            }
        return try {
            withWallClockTimeout(redirectChainMillis) {
                var current = target
                var currentMethod = HttpMethod.Post
                var redirects = 0
                while (true) {
                    if (!UrlSafety.isAllowedRemoteUrl(current)) return@withWallClockTimeout null
                    val response =
                        if (currentMethod == HttpMethod.Post) {
                            untrustedClient.post(current) {
                                header(HttpHeaders.Accept, "*/*")
                                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                                applyRuleHeaders(safeHeaders, target, current)
                                setBody(
                                    FormDataContent(
                                        Parameters.build {
                                            formData.forEach { (k, v) -> append(k, v) }
                                        },
                                    ),
                                )
                            }
                        } else {
                            untrustedClient.get(current) {
                                header(HttpHeaders.Accept, "*/*")
                                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                                applyRuleHeaders(safeHeaders, target, current)
                            }
                        }
                    val location = response.headers[HttpHeaders.Location]
                    if (response.status.value in 300..399 && location != null) {
                        response.bodyAsChannel().cancel()
                        if (++redirects > MAX_REDIRECTS) return@withWallClockTimeout null
                        current = UrlSafety.resolveRedirect(current, location) ?: return@withWallClockTimeout null
                        // 307/308 保持原方法与 body；301/302/303 按浏览器惯例降级为 GET
                        if (response.status.value != 307 && response.status.value != 308) {
                            currentMethod = HttpMethod.Get
                        }
                        continue
                    }
                    return@withWallClockTimeout extractFetchedPage(response)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (e: TimeoutCancellationException) {
            // 外层若已取消（调用方自己的 withTimeout），超时只是取消的副产物，必须继续上抛
            currentCoroutineContext().ensureActive()
            logger.w { "表单提交重定向链超时 $target" }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "表单提交失败 $target: ${e.message}" }
            null
        }
    }

    override suspend fun probeStream(
        url: String,
        requestHeaders: Map<String, String>,
    ): StreamProbe {
        val target = url.trim()
        if (!target.startsWith("http://", ignoreCase = true) &&
            !target.startsWith("https://", ignoreCase = true)
        ) {
            return StreamProbe.Failed("不是 http(s) 地址")
        }
        val safeHeaders =
            requestHeaders.filter { (name, value) ->
                name.isNotBlank() && name.isSafeHeaderValue() && value.isSafeHeaderValue()
            }
        return try {
            // 探测不套 UrlSafety：局域网/NAS 直链是合法目标（见 UrlSafety KDoc）。
            // 但 30x 必须自己跟：共享 client 的自动重定向会跨 host 带走规则头，
            // 且无法给整条链设预算。
            withWallClockTimeout(redirectChainMillis) {
                var current = target
                var redirects = 0
                while (true) {
                    val response =
                        untrustedClient
                            .prepareGet(current) {
                                // 探测的是媒体直链，Accept 不能沿用 text/html，否则 CDN 可能回一个错误页
                                header(HttpHeaders.Accept, "*/*")
                                header(HttpHeaders.Range, "bytes=0-2047")
                                applyRuleHeaders(safeHeaders, target, current)
                            }.execute { it }
                    val location = response.headers[HttpHeaders.Location]
                    if (response.status.value !in 300..399 || location == null) {
                        response.bodyAsChannel().cancel()
                        return@withWallClockTimeout StreamProbe.Responded(
                            status = response.status.value,
                            contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim(),
                        )
                    }
                    response.bodyAsChannel().cancel()
                    if (++redirects > MAX_REDIRECTS) return@withWallClockTimeout StreamProbe.Failed("重定向次数过多")
                    val next =
                        UrlSafety.resolveRedirect(current, location)
                            ?: return@withWallClockTimeout StreamProbe.Failed("重定向目标不可信")
                    if (!next.startsWith("http://", ignoreCase = true) &&
                        !next.startsWith("https://", ignoreCase = true)
                    ) {
                        return@withWallClockTimeout StreamProbe.Failed("重定向目标不可信")
                    }
                    current = next
                }
                @Suppress("UNREACHABLE_CODE")
                StreamProbe.Failed("探测请求异常")
            }
        } catch (e: TimeoutCancellationException) {
            // 外层若已取消（调用方自己的 withTimeout），超时只是取消的副产物，必须继续上抛
            currentCoroutineContext().ensureActive()
            logger.w { "首包探测重定向链超时 $target" }
            StreamProbe.Failed("重定向链超时")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "首包探测失败 $target: ${e.message}" }
            StreamProbe.Failed(e.message ?: "探测请求异常")
        }
    }

    /**
     * GET 30x 逐跳跟随：每一跳都重新过 [UrlSafety]，最多 [MAX_REDIRECTS] 跳。
     * 返回 null 表示目标不可信/跳转过多，调用方按"没有结果"降级。
     */
    private suspend fun getFollowingSafeRedirects(
        startUrl: String,
        headers: Map<String, String>,
        accept: String,
    ): HttpResponse? =
        try {
            withWallClockTimeout(redirectChainMillis) {
                var current = startUrl
                var redirects = 0
                while (true) {
                    if (!UrlSafety.isAllowedRemoteUrl(current)) return@withWallClockTimeout null
                    val response =
                        untrustedClient.get(current) {
                            header(HttpHeaders.Accept, accept)
                            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
                            applyRuleHeaders(headers, startUrl, current)
                        }
                    val location = response.headers[HttpHeaders.Location]
                    if (response.status.value !in 300..399 || location == null) {
                        return@withWallClockTimeout response
                    }
                    response.bodyAsChannel().cancel()
                    if (++redirects > MAX_REDIRECTS) return@withWallClockTimeout null
                    current = UrlSafety.resolveRedirect(current, location) ?: return@withWallClockTimeout null
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (e: TimeoutCancellationException) {
            // 外层若已取消（调用方自己的 withTimeout），超时只是取消的副产物，必须继续上抛
            currentCoroutineContext().ensureActive()
            logger.w { "页面抓取重定向链超时 $startUrl" }
            null
        }

    /**
     * 读取正文并解码。
     *
     * 必须显式切到 [BgmDispatchers.default]：Ktor 只负责把网络收包放到引擎线程，
     * `readAvailable` 的续体和 `decodeToString` 仍会回到调用方调度器——调用方是
     * `viewModelScope`（Main）时，几百 KB 的正文解码就落在主线程上。
     */
    private suspend fun extractFetchedPage(
        response: HttpResponse,
        maxBytes: Int = MAX_PAGE_BYTES,
    ): FetchedPage? =
        withContext(dispatchers.default) {
            // 初始 URL 过了闸不代表最终 URL 也安全：30x 可能跳到私网
            if (!UrlSafety.isAllowedRemoteUrl(
                    response.call.request.url
                        .toString(),
                )
            ) {
                return@withContext null
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(maxBytes)
            var offset = 0
            while (offset < maxBytes) {
                val read = channel.readAvailable(buffer, offset, maxBytes - offset)
                if (read == -1) break
                offset += read
            }
            if (offset == 0) return@withContext null

            val respHeaders = mutableMapOf<String, String>()
            response.headers.forEach { name, values ->
                respHeaders[name] = values.joinToString(", ")
            }
            val setCookies = response.headers.getAll(HttpHeaders.SetCookie)
            if (!setCookies.isNullOrEmpty()) {
                val cookieString = setCookies.joinToString("; ") { it.substringBefore(';').trim() }
                respHeaders["Cookie"] = cookieString
            }

            FetchedPage(
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

/** 跨 host 跳转时必须丢下的凭据类头：规则作者自备的凭据只该发给目标站，不能跟着 30x 走 */
private val CREDENTIAL_HEADERS =
    setOf(
        HttpHeaders.Authorization.lowercase(),
        HttpHeaders.Cookie.lowercase(),
        "proxy-authorization",
        "cookie2",
    )

/**
 * 把规则自带的请求头写到当前跳。
 *
 * 同 host 全量保留；跨 host 只保留非凭据头（Referer/UA 等），
 * 避免一次 30x 就把 Authorization/Cookie 交给第三方。
 */
private fun HttpRequestBuilder.applyRuleHeaders(
    headers: Map<String, String>,
    originUrl: String,
    targetUrl: String,
) {
    val sameHost = hostOf(originUrl)?.equals(hostOf(targetUrl), ignoreCase = true) == true
    headers.forEach { (name, value) ->
        if (!sameHost && name.trim().lowercase() in CREDENTIAL_HEADERS) return@forEach
        header(name.trim(), value.trim())
    }
}

private fun hostOf(url: String): String? = runCatching { Url(url).host }.getOrNull()
