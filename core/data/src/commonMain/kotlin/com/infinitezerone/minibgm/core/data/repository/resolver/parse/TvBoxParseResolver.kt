package com.infinitezerone.minibgm.core.data.repository.resolver.parse

import com.dokar.quickjs.QuickJs
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.WebViewCaptureService
import com.infinitezerone.minibgm.core.data.repository.resolver.extractPlayableSources
import com.infinitezerone.minibgm.core.data.repository.resolver.isPlayableMediaUrl
import com.infinitezerone.minibgm.core.data.repository.resolver.spider.JsSpiderSandbox
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.TvBoxParse
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * TVBox 聚合解析器接口。
 */
interface TvBoxParseResolver {
    suspend fun resolveParse(
        targetUrl: String,
        epNumber: Float = 0f,
        siteName: String = "",
        candidateParses: List<TvBoxParse> = emptyList(),
    ): List<PlayableSource>
}

/**
 * TVBox 标准聚合解析调度器实现。
 *
 * 核心调度流水线：
 * 1. 目标地址若本身已是媒体直链，直接返回；
 * 2. 依次匹配与试探可用的 [TvBoxParse]：
 *    - type 1 (JSON 接口)：直接请求解析端点，反序列化获取直链与自定义 headers；
 *    - type 0 (网页解析) / 2 (嗅探解析)：先走轻量静态 HTML 抽取，未命中再调用无头 WebView 深度拦截媒体流；
 *    - type 3 (脚本解析)：委托 [JsSpiderSandbox] 执行 JS 扩展解析；
 * 3. 单次解析施加超时看门狗保护，防止恶意或已失效解析服务挂起客户端。
 */
class TvBoxParseResolverImpl(
    private val pageFetchService: PageFetchService,
    private val webViewCaptureService: WebViewCaptureService? = null,
    private val jsSpiderSandbox: JsSpiderSandbox? = null,
) : TvBoxParseResolver {
    private val logger = bgmLogger("Bgm/TvBoxParseResolver")

    override suspend fun resolveParse(
        targetUrl: String,
        epNumber: Float,
        siteName: String,
        candidateParses: List<TvBoxParse>,
    ): List<PlayableSource> {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return emptyList()

        if (isPlayableMediaUrl(trimmed)) {
            return listOf(
                PlayableSource(
                    url = trimmed,
                    kind = PlaylistEntryKind.DIRECT,
                    label = if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "",
                    episodeSort = epNumber,
                    siteName = siteName,
                    pageUrl = trimmed,
                ),
            )
        }

        if (candidateParses.isEmpty()) {
            return fallbackSniff(trimmed, epNumber, siteName)
        }

        // 依次遍历候选解析器（通常默认第一条为主力解析）
        for (parse in candidateParses) {
            val result =
                withTimeoutOrNull(PARSE_TIMEOUT_MS) {
                    when (parse.type) {
                        TYPE_JSON -> resolveJsonParse(trimmed, epNumber, siteName, parse)
                        TYPE_WEB, TYPE_SNIFF -> resolveWebSniffParse(trimmed, epNumber, siteName, parse)
                        TYPE_SPIDER -> resolveScriptParse(trimmed, epNumber, siteName, parse)
                        else -> resolveJsonParse(trimmed, epNumber, siteName, parse)
                    }
                }
            if (!result.isNullOrEmpty()) {
                logger.d { "TVBox 解析成功 [${parse.name}]: $trimmed -> ${result.first().url}" }
                return result
            }
        }

        return fallbackSniff(trimmed, epNumber, siteName)
    }

    private suspend fun resolveJsonParse(
        targetUrl: String,
        epNumber: Float,
        siteName: String,
        parse: TvBoxParse,
    ): List<PlayableSource> {
        val reqUrl = buildParseRequestUrl(parse.url, targetUrl)
        return runCatching {
            val fetched = pageFetchService.fetchHtml(reqUrl, parse.header) ?: return@runCatching emptyList()
            val text = fetched.html.trim()
            if (!text.startsWith("{")) return@runCatching emptyList()

            val root = BgmHttpClient.jsonConfig.parseToJsonElement(text) as? JsonObject ?: return@runCatching emptyList()
            // 兼容 { "url": "..." }、{ "data": { "url": "..." } }、{ "data": "..." }
            val rawUrl =
                (root["url"] as? JsonPrimitive)?.contentOrNull
                    ?: ((root["data"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
                    ?: (root["data"] as? JsonPrimitive)?.contentOrNull
                    ?: return@runCatching emptyList()

            val headerMap = mutableMapOf<String, String>()
            val headerObj = root["header"] as? JsonObject ?: (root["headers"] as? JsonObject)
            headerObj?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { headerMap[k] = it }
            }

            listOf(
                PlayableSource(
                    url = rawUrl,
                    kind = PlaylistEntryKind.DIRECT,
                    label = if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "",
                    episodeSort = epNumber,
                    siteName = siteName.ifBlank { parse.name },
                    pageUrl = targetUrl,
                    headers = headerMap + parse.header,
                ),
            )
        }.getOrDefault(emptyList())
    }

    private suspend fun resolveWebSniffParse(
        targetUrl: String,
        epNumber: Float,
        siteName: String,
        parse: TvBoxParse,
    ): List<PlayableSource> {
        val reqUrl = buildParseRequestUrl(parse.url, targetUrl)
        // 1. 尝试静态秒提
        val staticFetched = pageFetchService.fetchHtml(reqUrl, parse.header)
        if (staticFetched != null) {
            val direct =
                extractPlayableSources(staticFetched.html, reqUrl, epNumber, siteName)
                    .filter { it.kind == PlaylistEntryKind.DIRECT }
            if (direct.isNotEmpty()) return direct
        }

        // 2. 深度 WebView 嗅探
        if (webViewCaptureService != null) {
            val captured =
                webViewCaptureService
                    .capturePlayableSources(reqUrl)
                    .filter { it.kind == PlaylistEntryKind.DIRECT }
            if (captured.isNotEmpty()) {
                return captured.map { it.copy(siteName = siteName.ifBlank { parse.name }) }
            }
        }

        return emptyList()
    }

    private suspend fun resolveScriptParse(
        targetUrl: String,
        epNumber: Float,
        siteName: String,
        parse: TvBoxParse,
    ): List<PlayableSource> {
        val sandbox = jsSpiderSandbox ?: return emptyList()
        val script = pageFetchService.fetchHtml(parse.url)?.html ?: return emptyList()
        return runCatching {
            sandbox.runInSandbox(script) { qjs: QuickJs ->
                val res = qjs.evaluate<Any?>("__bgm_call('play', '', '$targetUrl');")
                val resStr = res?.toString().orEmpty()
                if (resStr.contains("http")) {
                    val urlMatch = Regex("""https?://[^\s"',}]+""").find(resStr)?.value
                    if (urlMatch != null) {
                        listOf(
                            PlayableSource(
                                url = urlMatch,
                                kind = PlaylistEntryKind.DIRECT,
                                label = if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "",
                                episodeSort = epNumber,
                                siteName = siteName.ifBlank { parse.name },
                                pageUrl = targetUrl,
                                headers = parse.header,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fallbackSniff(
        targetUrl: String,
        epNumber: Float,
        siteName: String,
    ): List<PlayableSource> {
        val fetched = pageFetchService.fetchHtml(targetUrl) ?: return emptyList()
        val direct =
            extractPlayableSources(fetched.html, targetUrl, epNumber, siteName)
                .filter { it.kind == PlaylistEntryKind.DIRECT }
        if (direct.isNotEmpty()) return direct

        if (webViewCaptureService != null) {
            return webViewCaptureService
                .capturePlayableSources(targetUrl)
                .filter { it.kind == PlaylistEntryKind.DIRECT }
        }
        return emptyList()
    }

    private fun buildParseRequestUrl(
        parseUrl: String,
        targetUrl: String,
    ): String {
        val trimmed = parseUrl.trim()
        return when {
            trimmed.contains("{url}") -> trimmed.replace("{url}", PlaybackSourceRule.encodeParam(targetUrl))
            trimmed.endsWith("=") || trimmed.endsWith("?") -> "$trimmed$targetUrl"
            trimmed.contains("?") -> "$trimmed&url=$targetUrl"
            else -> "$trimmed?url=$targetUrl"
        }
    }

    companion object {
        private const val PARSE_TIMEOUT_MS = 8000L
        private const val TYPE_WEB = 0
        private const val TYPE_JSON = 1
        private const val TYPE_SNIFF = 2
        private const val TYPE_SPIDER = 3
    }
}
