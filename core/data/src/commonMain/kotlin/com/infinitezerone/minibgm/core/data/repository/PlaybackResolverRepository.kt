package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.model.MacCmsProbeResult
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.ProbeSiteOutput
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 单次请求解析的页面数上限，避免一次找源退化成批量抓取 */
private const val MAX_PAGES = 5

/**
 * 一次解析调用允许发起的抓取总数上限。
 *
 * 下探是树状的（页面 → iframe → 分集链接 → 每条再进一层 iframe），只按层数或页数限制挡不住
 * 扇出：五个页面各自展开到底能到二十几次请求。额度用尽即停止下探，不再展开新的分支。
 */
internal const val MAX_FETCHES_PER_RESOLUTION = 12

/** 一次解析内共享的抓取额度 */
internal class FetchBudget(
    private val max: Int = MAX_FETCHES_PER_RESOLUTION,
) {
    var used: Int = 0
        private set

    /** 取到额度返回 true；用尽后调用方应放弃继续下探 */
    fun take(): Boolean {
        if (used >= max) return false
        used++
        return true
    }
}

/**
 * 判断两个地址是否属于同一个注册域（eTLD+1）。
 *
 * 用于分集/目录链接的下探选择：iframe 跟进不设域限制（内嵌播放器常跨注册域），
 * 但列表页挑链接必须留在本注册域内。
 * 视频常托管在另一个 CDN 域，卡相等域名会误杀正常站点。
 * 无法判定（拿不到 host）时放行：这一版只拦"顺着链接跑到别的站点"。
 */
internal fun sameRegistrableDomain(
    firstUrl: String,
    secondUrl: String,
): Boolean {
    val first = registrableDomainOf(firstUrl) ?: return true
    val second = registrableDomainOf(secondUrl) ?: return true
    return first == second
}

private val TWO_LEVEL_SUFFIXES =
    setOf(
        "com.cn",
        "net.cn",
        "org.cn",
        "gov.cn",
        "edu.cn",
        "com.tw",
        "com.hk",
        "com.mo",
        "co.uk",
        "org.uk",
        "ac.uk",
        "gov.uk",
        "co.jp",
        "or.jp",
        "ne.jp",
        "ac.jp",
        "co.kr",
        "com.br",
        "com.au",
        "com.ru",
        "com.ua",
        "co.in",
        "com.sg",
        "com.my",
    )

private fun registrableDomainOf(url: String): String? {
    val host =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore(':')
            .trim('.')
            .lowercase()
    if (host.isBlank() || host.count { it == '.' } < 1) return null
    val labels = host.split('.')
    val suffixLength =
        if (labels.size >= 3 && labels.takeLast(2).joinToString(".") in TWO_LEVEL_SUFFIXES) 3 else 2
    return labels.takeLast(suffixLength).joinToString(".")
}

/** 单个页面最多采纳的候选来源数 */
private const val MAX_CANDIDATES_PER_PAGE = 12

/** 单份流清单最多采纳的条目数（用户配置的接口可整季返回，比页面抓取宽） */
private const val MAX_MANIFEST_ENTRIES = 50

private val MEDIA_URL_REGEX =
    Regex("""https?://[^"'()\s<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts)(?:\?[^"'()\s<>]*)?""", RegexOption.IGNORE_CASE)

private val MEDIA_TAG_REGEX =
    Regex("""<(?:source|video)\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val IFRAME_TAG_REGEX =
    Regex("""<iframe\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val INVALID_MEDIA_HOSTS = setOf("nflxso.net", "netflix.com", "nflximg.net", "akamaized.net")

private fun isInvalidMediaHost(url: String): Boolean {
    val host =
        url
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
    return INVALID_MEDIA_HOSTS.any { host.endsWith(it) }
}

/** 流清单条目：Stremio `/stream` 响应的兼容子集——url 必填，title/headers 可选，未知字段忽略 */
@Serializable
internal data class StreamManifestEntry(
    val url: String = "",
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 流清单信封：`streams` 字段缺失表示响应不是清单（调用方回退正则抽取），
 * 空数组表示接口明确宣告无结果。
 */
@Serializable
internal data class StreamManifest(
    val streams: List<StreamManifestEntry>? = null,
)

/**
 * 把调用方给定的播放页解析成可播放来源。
 *
 * 只做"取一次页面 + 抽候选地址"，不跟踪站点、不执行 JS：抽不到就返回空列表，
 * 由上层降级为"没有可用结果"。
 */
interface PlaybackResolverRepository {
    /** [title] 为要的那部番的片名，列表页下探时用它排除撞号跳错番；留空则跳过该校验 */
    suspend fun resolvePages(
        pageUrls: List<String>,
        epNumber: Float = 0f,
        siteName: String = "",
        title: String = "",
    ): List<PlayableSource>

    /** 按用户自备的模板接口地址取一次并抽取可播放地址；[headers] 随请求发出并回传给播放器 */
    suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String> = emptyMap(),
        epNumber: Float = 0f,
        siteName: String = "",
        title: String = "",
    ): List<PlayableSource>

    /**
     * 按 [PlaybackSourceRule] 执行取源解析。
     * 根据规则的 [RuleParserType] 自动分发流水线（PIPELINE）、MacCMS、Stremio 或智能嗅探。
     */
    suspend fun resolveRule(
        rule: PlaybackSourceRule,
        title: String,
        epNumber: Float = 0f,
        subjectId: Long = 0L,
        episodeId: Long = 0L,
    ): List<PlayableSource> =
        resolveTemplate(
            url = rule.resolveUrl(title, if (epNumber > 0f) epNumber.toInt().toString() else "", subjectId, episodeId),
            headers = rule.headers,
            epNumber = epNumber,
            siteName = rule.name,
            title = title,
        )

    /**
     * 探测指定页面的结构特征（是否包含 video 标签、自定义属性、iframe、MacCMS 标记等）。
     */
    suspend fun inspectPage(url: String): PageInspectionResult =
        PageInspectionResult(url = url, isSuccess = false, errorMessage = "Not implemented")

    /**
     * 自主探查目标站点的可用性、标题、搜索参数模式，并尝试寻找样本播放页。
     */
    suspend fun probeSite(
        siteUrl: String,
        sampleAnime: String = "",
    ): ProbeSiteOutput = ProbeSiteOutput(siteUrl = siteUrl, isReachable = false, errorMessage = "Not implemented")

    /**
     * 探测给定域名/接口地址是否为**标准 MacCMS V10 采集接口**，并给出可直接落库的取源规则。
     *
     * 这是一条**确定性快路径**：标准采集站占这类源的大头，`/api.php/provide/vod/` 上的
     * `{"code":…,"list":[…]}` 是可判定的信号，不需要模型参与。探不到就是探不到，
     * 调用方据此引导用户改走手填或助手探查，而不是降级成嗅探。
     *
     * 判定只看两件事：响应体能解析成 JSON 且含 `list` 数组。**不校验条目内容**——
     * 采集站返回的内容与本应用无关，这里回答的只是"接口在不在"。
     */
    suspend fun probeMacCmsEndpoint(input: String): AppResult<MacCmsProbeResult> =
        AppResult.Error(IllegalStateException("Not implemented"), "站点探测不可用")

    /**
     * 对指定页面执行动态网络流量审计（运行指定时长，监控所有媒体流与关键 API 调用）。
     */
    suspend fun auditPageTraffic(
        pageUrl: String,
        durationMs: Long = 8000L,
    ): NetworkAuditTrace = NetworkAuditTrace(pageUrl = pageUrl, isReachable = false, errorMessage = "Not implemented")

    /**
     * 直读静态播放页源码归纳规则草案（一次普通抓取，不走 WebView）。
     *
     * 静态站（直链写在 HTML 里）用这一条就够了；返回 null 表示页面不可达或没有正文，
     * "取到了但没抽到媒体"会体现在草案 notes 里，由调用方决定是否改走 [auditPageTraffic]。
     */
    suspend fun recordRuleFromStaticPage(
        pageUrl: String,
        ruleName: String,
        sampleTitle: String,
        sampleEp: String = "",
    ): RecordedRuleDraft? = null
}

class PlaybackResolverRepositoryImpl(
    private val pageFetchService: PageFetchService,
    private val playbackRuleEngine: PlaybackRuleEngine = PlaybackRuleEngineImpl(pageFetchService),
    private val webViewCaptureService: WebViewCaptureService? = null,
) : PlaybackResolverRepository {
    override suspend fun resolvePages(
        pageUrls: List<String>,
        epNumber: Float,
        siteName: String,
        title: String,
    ): List<PlayableSource> =
        // 正则要扫最大 512 KB 的正文，调用方常在主线程，整段挪到 Default
        withContext(Dispatchers.Default) {
            val budget = FetchBudget()
            pageUrls
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_PAGES)
                .flatMap { page ->
                    if (!budget.take()) return@flatMap emptyList()
                    val fetched = pageFetchService.fetchHtml(page) ?: return@flatMap emptyList()
                    sniffPageOrSubPages(fetched, epNumber, siteName, pageFetchService, title, budget)
                }.distinctBy { it.url }
        }

    override suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String>,
        epNumber: Float,
        siteName: String,
        title: String,
    ): List<PlayableSource> =
        withContext(Dispatchers.Default) {
            val budget = FetchBudget()
            val fetched = pageFetchService.fetchHtml(url, headers) ?: return@withContext emptyList()
            budget.take()
            // 用户配置的取源接口优先识别结构化流清单（兼容 Stremio /stream 形态），
            // 非清单响应回退到页面正则与智能嗅探
            parseStreamManifest(fetched.html, fetched.url, epNumber, siteName, headers)?.let {
                return@withContext it
            }
            val sources = sniffPageOrSubPages(fetched, epNumber, siteName, pageFetchService, title, budget)
            sources
                .distinctBy { it.url }
                .map { source -> if (headers.isEmpty()) source else source.copy(headers = source.headers + headers) }
        }

    override suspend fun resolveRule(
        rule: PlaybackSourceRule,
        title: String,
        epNumber: Float,
        subjectId: Long,
        episodeId: Long,
    ): List<PlayableSource> =
        withContext(Dispatchers.Default) {
            when (rule.parserType) {
                RuleParserType.PIPELINE -> {
                    playbackRuleEngine.executePipeline(rule, title, epNumber, subjectId, episodeId)
                }
                RuleParserType.MACCMS -> {
                    val epStr = if (epNumber > 0f) epNumber.toInt().toString() else ""
                    val url = rule.resolveUrl(title, epStr, subjectId, episodeId)
                    val fetched = pageFetchService.fetchHtml(url, rule.headers) ?: return@withContext emptyList()
                    val sources = extractMacCmsSources(fetched.html, fetched.url, epNumber, rule.name, title)
                    if (rule.headers.isEmpty()) sources else sources.map { it.copy(headers = it.headers + rule.headers) }
                }
                RuleParserType.STREMIO -> {
                    val epStr = if (epNumber > 0f) epNumber.toInt().toString() else ""
                    val url = rule.resolveUrl(title, epStr, subjectId, episodeId)
                    val fetched = pageFetchService.fetchHtml(url, rule.headers) ?: return@withContext emptyList()
                    parseStreamManifest(fetched.html, fetched.url, epNumber, rule.name, rule.headers).orEmpty()
                }
                RuleParserType.AUTO -> {
                    val epStr = if (epNumber > 0f) epNumber.toInt().toString() else ""
                    val url = rule.resolveUrl(title, epStr, subjectId, episodeId)
                    resolveTemplate(url, rule.headers, epNumber, rule.name, title)
                }
            }
        }

    override suspend fun inspectPage(url: String): PageInspectionResult {
        val trimmed = url.trim()
        if (trimmed.isBlank() ||
            (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true))
        ) {
            return PageInspectionResult(url = url, isSuccess = false, errorMessage = "Invalid URL")
        }
        val fetched =
            pageFetchService.fetchHtml(trimmed)
                ?: return PageInspectionResult(url = url, isSuccess = false, errorMessage = "Failed to fetch page or unreachable")

        val html = fetched.html
        val titleMatch = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
        val title =
            titleMatch
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()

        val videoMatch = Regex("""<video\b([^>]*)>""", RegexOption.IGNORE_CASE).find(html)
        val hasVideo = videoMatch != null
        val videoAttrs = mutableMapOf<String, String>()
        if (videoMatch != null) {
            val attrContent = videoMatch.groupValues[1]
            Regex("""([a-zA-Z0-9_\-]+)\s*=\s*["']([^"']*)["']""").findAll(attrContent).forEach { attr ->
                videoAttrs[attr.groupValues[1]] = attr.groupValues[2]
            }
        }

        val iframes =
            Regex("""<iframe\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { it.groupValues[1] }
                .take(5)
                .toList()

        val hasMacCms = html.contains("vod_play_url") || html.contains("player_aaaa")

        return PageInspectionResult(
            url = fetched.url,
            isSuccess = true,
            title = title,
            hasVideoTag = hasVideo,
            videoAttrs = videoAttrs,
            iframeUrls = iframes,
            hasMacCmsPattern = hasMacCms,
            responseHeaders = fetched.responseHeaders,
        )
    }

    override suspend fun auditPageTraffic(
        pageUrl: String,
        durationMs: Long,
    ): NetworkAuditTrace {
        val capture =
            webViewCaptureService
                ?: return NetworkAuditTrace(
                    pageUrl = pageUrl,
                    isReachable = false,
                    errorMessage = "WebView capture service not available",
                )
        return capture.auditPageTraffic(pageUrl, durationMs)
    }

    override suspend fun recordRuleFromStaticPage(
        pageUrl: String,
        ruleName: String,
        sampleTitle: String,
        sampleEp: String,
    ): RecordedRuleDraft? {
        val fetched = pageFetchService.fetchHtml(pageUrl.trim()) ?: return null
        return withContext(Dispatchers.Default) {
            PlaybackRuleRecorder.recordFromStaticPage(
                pageUrl = fetched.url.ifBlank { pageUrl },
                html = fetched.html,
                ruleName = ruleName,
                title = sampleTitle,
                ep = sampleEp,
            )
        }
    }

    override suspend fun probeSite(
        siteUrl: String,
        sampleAnime: String,
    ): ProbeSiteOutput {
        val fetched =
            pageFetchService.fetchHtml(siteUrl)
                ?: return ProbeSiteOutput(
                    siteUrl = siteUrl,
                    isReachable = false,
                    errorMessage = "Site unreachable or request failed",
                )
        val html = fetched.html
        val title =
            Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.trim()
                .orEmpty()

        val isAdParking = detectAdParking(title, html)
        val origin = pageOrigin(fetched.url)
        val (hasSearchBox, searchUrlPattern) = detectSearchUrlPattern(html, siteUrl, origin)
        // 样本：调用方给了就用；没给就从首页挑一个**该站真实存在**的条目标题。
        // 写死一个通用番名（如「芙莉莲」）时，没收录它的站根本探不出搜索参数模式，
        // 报出来的失败原因还是误导性的——搜索参数模式是后续建规则的地基，必须用真实存在的名字去验证。
        val sampleTitle = sampleAnime.trim().ifBlank { findHomeEntryTitle(html, origin).orEmpty() }
        val sampleEpisodeUrl = probeSampleEpisodeUrl(searchUrlPattern, sampleTitle, html, origin, pageFetchService)

        return ProbeSiteOutput(
            siteUrl = fetched.url,
            isReachable = true,
            isAdParking = isAdParking,
            title = title,
            sampleEpisodeUrl = sampleEpisodeUrl,
            hasSearchBox = hasSearchBox,
            searchUrlPattern = searchUrlPattern,
            sampleTitleUsed = sampleTitle.ifBlank { null },
        )
    }

    override suspend fun probeMacCmsEndpoint(input: String): AppResult<MacCmsProbeResult> {
        val candidates = macCmsProbeCandidates(input)
        if (candidates.isEmpty()) {
            return AppResult.Error(
                IllegalArgumentException("unrecognized host: $input"),
                "没能从「$input」里认出站点域名。填域名（如 example.com）或接口地址即可。",
            )
        }
        for (candidate in candidates) {
            val fetched = pageFetchService.fetchHtml(candidate) ?: continue
            val sampleCount = countMacCmsListItems(fetched.html) ?: continue
            val endpointUrl = candidate.substringBefore('?')
            return AppResult.Success(
                MacCmsProbeResult(
                    endpointUrl = endpointUrl,
                    ruleTemplate = "$endpointUrl?ac=detail&wd={title}",
                    siteName = candidate.substringAfter("://").substringBefore('/'),
                    sampleCount = sampleCount,
                ),
            )
        }
        return AppResult.Error(
            IllegalStateException("no maccms endpoint at $input"),
            "已试过 ${candidates.size} 个标准接口地址，都没有 MacCMS 响应。该站可能不是采集站——" +
                "可改用「添加」手填，或交给助手探查。",
        )
    }
}

/**
 * 由用户输入推导待探测的标准 MacCMS 接口地址。
 *
 * 用户可能贴 `example.com`、`https://example.com`、甚至一整条规则模板——一律只取主机名，
 * 再按 MacCMS V10 的约定路径拼候选。https 优先、明文 http 回退（不少采集站只有 http，
 * 本应用已显式放开明文）。
 */
internal fun macCmsProbeCandidates(input: String): List<String> {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return emptyList()
    val withScheme =
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    val rest = withScheme.substringAfter("://", "")
    val host =
        rest
            .substringBefore('/')
            .substringBefore('?')
            .trim()
            .trimEnd('.')
    if (host.isBlank()) return emptyList()

    val preferHttps = withScheme.startsWith("https://", ignoreCase = true)
    val schemes = if (preferHttps) listOf("https", "http") else listOf("http", "https")
    return schemes.map { "$it://$host/api.php/provide/vod/?ac=list" }
}

private val probeJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/**
 * 判定响应体是否是 MacCMS 列表响应，并返回条目数；不是则返回 null。
 *
 * 只认 `{"list":[…]}` 这一结构（MacCMS V10 的固定约定）。**空数组也算命中**——
 * 接口存在但当前没内容是常态，跟"这里没有接口"是两回事，不能混为一谈。
 */
internal fun countMacCmsListItems(body: String): Int? {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.first() != '{') return null
    val root = runCatching { probeJson.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject ?: return null
    return (root["list"] as? JsonArray)?.size
}

private fun detectAdParking(
    title: String,
    html: String,
): Boolean =
    title.contains("域名出售", ignoreCase = true) ||
        title.contains("Domain for Sale", ignoreCase = true) ||
        title.contains("404 Not Found", ignoreCase = true) ||
        html.contains("该域名已过期", ignoreCase = true) ||
        (html.length < 200 && title.isEmpty())

private fun detectSearchUrlPattern(
    html: String,
    siteUrl: String,
    origin: String?,
): Pair<Boolean, String?> {
    val formMatch =
        Regex(
            """<form\b[^>]*action=["']([^"']*)["'][^>]*>(.*?)</form>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).findAll(html)
            .firstOrNull { form ->
                val content = form.groupValues[2]
                content.contains("name=\"s\"", ignoreCase = true) ||
                    content.contains("name=\"wd\"", ignoreCase = true) ||
                    content.contains("name=\"keyword\"", ignoreCase = true) ||
                    content.contains("name=\"search\"", ignoreCase = true) ||
                    content.contains("name=\"q\"", ignoreCase = true)
            }

    if (formMatch != null) {
        val action = formMatch.groupValues[1]
        val formContent = formMatch.groupValues[2]
        val inputName =
            Regex("""<input\b[^>]*name=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                .findAll(formContent)
                .map { it.groupValues[1] }
                .firstOrNull { name ->
                    name in listOf("s", "wd", "keyword", "search", "q", "query")
                } ?: "s"

        val absAction = absoluteUrl(action.ifBlank { "/" }, origin) ?: siteUrl
        val pattern =
            if (absAction.contains("?")) {
                "$absAction&$inputName={title}"
            } else {
                "$absAction?$inputName={title}"
            }
        return true to pattern
    }

    if (html.contains("?s=") || html.contains("/search/")) {
        val pattern =
            if (html.contains("?s=")) {
                "${origin ?: siteUrl}?s={title}"
            } else {
                "${origin ?: siteUrl}/search/{title}"
            }
        return true to pattern
    }

    return false to null
}

private val SKIP_SUFFIXES =
    setOf(
        ".css",
        ".js",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".ico",
        ".svg",
        ".rss",
        ".xml",
        ".woff",
        ".woff2",
        ".ttf",
        ".eot",
        ".map",
        ".json",
    )
private val NON_EPISODE_KEYWORDS =
    listOf(
        "/category/",
        "/tag/",
        "/page/",
        "/author/",
        "/notify/",
        "关于",
        "留言板",
        "login",
        "register",
        "cart",
        "checkout",
        "account",
        "/feed",
    )
private val PLAY_KEYWORDS = listOf("/watch", "/play", "/video", "/bangumi", "/view", "/anime", "?cat=")
private val NUMERIC_PAGE_REGEX = Regex("""^(?:https?://[^/]+)?/(?:archives/|p/)?\d+/?$""")

/** 站点详情页（条目页）的典型路径特征——首页上这些链接的锚文本就是站内条目名 */
private val DETAIL_LINK_KEYWORDS =
    listOf(
        "/voddetail/",
        "/vod/detail/",
        "/detail/",
        "/vod/",
        "/show/",
        "/subject/",
        "/bangumi/",
        "/anime/",
    )

/** 探查样本的合理片名长度：比这短的多是单字导航，比这长的多是整句推荐语 */
private const val MIN_SAMPLE_TITLE_LENGTH = 2
private const val MAX_SAMPLE_TITLE_LENGTH = 40

/** 锚文本命中这些词的多是站内导航位，不是条目名 */
private val NON_ENTRY_TITLE_KEYWORDS =
    listOf("首页", "主页", "排行", "排行榜", "最新", "全部", "更多", "登录", "注册", "公告", "资讯", "新闻")

/**
 * 从站点首页挑一个**真实存在**的条目标题当探查样本。
 *
 * 详情页链接的锚文本就是站内条目的名字，比编一个通用番名可靠得多——
 * 样本必须在目标站上真实存在，否则搜索页返回空列表，探不出搜索参数模式。
 */
internal fun findHomeEntryTitle(
    html: String,
    origin: String?,
): String? {
    val aTagRegex = Regex("""<a\b([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val tagRegex = Regex("""<[^>]*>""")
    val spaceRegex = Regex("""\s+""")
    val originPath = origin?.trimEnd('/')?.lowercase()
    return aTagRegex
        .findAll(html)
        .mapNotNull { match ->
            val href =
                hrefRegex
                    .find(match.groupValues[1])
                    ?.groupValues
                    ?.get(1)
                    ?.trim() ?: return@mapNotNull null
            val cleanPath =
                href
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim()
                    .lowercase()
            if (DETAIL_LINK_KEYWORDS.none { cleanPath.contains(it) }) return@mapNotNull null
            if (originPath != null && cleanPath == originPath) return@mapNotNull null
            val text = spaceRegex.replace(tagRegex.replace(match.groupValues[2], " "), " ").trim()
            text.takeIf { it.looksLikeEntryTitle() }
        }.firstOrNull()
}

private fun String.looksLikeEntryTitle(): Boolean =
    length in MIN_SAMPLE_TITLE_LENGTH..MAX_SAMPLE_TITLE_LENGTH &&
        NON_EPISODE_KEYWORDS.none { contains(it, ignoreCase = true) } &&
        NON_ENTRY_TITLE_KEYWORDS.none { contains(it) } &&
        any { it.isLetterOrDigit() } &&
        !all { it.isDigit() }

internal data class AnchorCandidate(
    val href: String,
    val isBookmark: Boolean,
    val cleanPath: String,
)

private suspend fun probeSampleEpisodeUrl(
    searchUrlPattern: String?,
    sampleAnime: String,
    html: String,
    origin: String?,
    pageFetchService: PageFetchService,
): String? {
    if (searchUrlPattern != null && sampleAnime.isNotBlank()) {
        val encodedTitle = PlaybackSourceRule.encodeParam(sampleAnime.trim())
        val testSearchUrl = searchUrlPattern.replace("{title}", encodedTitle)
        val searchPage = pageFetchService.fetchHtml(testSearchUrl)
        if (searchPage != null) {
            val candidate = findCandidateEpisodeUrl(searchPage.html, pageOrigin(searchPage.url))
            if (candidate != null) return candidate
        }
    }
    return findCandidateEpisodeUrl(html, origin)
}

internal fun findCandidateEpisodeUrl(
    html: String,
    origin: String?,
): String? {
    val aTagRegex = Regex("""<a\b([^>]*)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val relRegex = Regex("""rel=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    val validLinks =
        aTagRegex
            .findAll(html)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val href =
                    hrefRegex
                        .find(attrs)
                        ?.groupValues
                        ?.get(1)
                        ?.trim() ?: return@mapNotNull null
                val rel =
                    relRegex
                        .find(attrs)
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                        .orEmpty()
                val isBookmark = rel.contains("bookmark")
                val cleanPath =
                    href
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim()
                        .lowercase()
                AnchorCandidate(href = href, isBookmark = isBookmark, cleanPath = cleanPath)
            }.filter { link ->
                val clean = link.cleanPath
                SKIP_SUFFIXES.none { clean.endsWith(it) } &&
                    NON_EPISODE_KEYWORDS.none { clean.contains(it) } &&
                    !clean.endsWith("#") &&
                    (clean.startsWith("/") || clean.startsWith("http"))
            }.toList()

    val bookmarkMatch = validLinks.firstOrNull { it.isBookmark }
    if (bookmarkMatch != null) {
        return absoluteUrl(bookmarkMatch.href, origin)
    }

    val numericMatch = validLinks.firstOrNull { NUMERIC_PAGE_REGEX.matches(it.cleanPath) }
    if (numericMatch != null) {
        return absoluteUrl(numericMatch.href, origin)
    }

    val playKeywordMatch = validLinks.firstOrNull { link -> PLAY_KEYWORDS.any { link.cleanPath.contains(it) } }
    if (playKeywordMatch != null) {
        return absoluteUrl(playKeywordMatch.href, origin)
    }

    val fallbackMatch = validLinks.firstOrNull { it.cleanPath != "/" && it.cleanPath != origin?.lowercase() }
    return fallbackMatch?.let { absoluteUrl(it.href, origin) }
}

/**
 * 识别结构化流清单（`{"streams":[{"url","title","headers"}]}`，Stremio /stream 兼容）。
 *
 * 返回 null 表示响应不是清单（无 `streams` 字段或不是 JSON 对象），由调用方回退正则；
 * 返回空列表表示清单合法但接口明确无结果。清单条目自带请求头，规则请求头追加其后
 * （与正则路径一致：规则头优先）。
 */
internal fun parseStreamManifest(
    body: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
    ruleHeaders: Map<String, String>,
): List<PlayableSource>? {
    if (!body.trimStart().startsWith("{")) return null
    val manifest =
        runCatching { BgmHttpClient.jsonConfig.decodeFromString<StreamManifest>(body) }.getOrNull()
            ?: return null
    val streams = manifest.streams ?: return null
    return streams
        .mapNotNull { entry ->
            val candidateUrl = entry.url.trim()
            if (!candidateUrl.startsWith("http://", ignoreCase = true) &&
                !candidateUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            PlayableSource(
                url = candidateUrl,
                kind = PlaylistEntryKind.DIRECT,
                label = entry.title.ifBlank { if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "" },
                episodeSort = epNumber,
                siteName = siteName,
                pageUrl = pageUrl,
                headers = entry.headers + ruleHeaders,
            )
        }.distinctBy { it.url }
        .take(MAX_MANIFEST_ENTRIES)
}

/**
 * 从页面正文抽取候选地址。
 *
 * 先还原 JSON 里被转义的 `\/` 与 HTML 实体 `&amp;`，否则绝大多数直链都匹配不到；
 * 相对地址按页面 origin 补全，Referer 取页面站点根，播放器起播时通常需要它。
 * 分集标注优先从候选地址真实抽取（带 ep/E 前缀的数字为强信号），
 * 抽不到才回退到查询话数——避免把"查询第 N 话"当成"页面上所有候选都是第 N 话"。
 */
internal fun extractPlayableSources(
    html: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
): List<PlayableSource> {
    val normalized = html.replace("\\/", "/").replace("&amp;", "&")
    val origin = pageOrigin(pageUrl)
    val headers = origin?.let { mapOf("Referer" to it) }.orEmpty()

    fun buildSource(
        candidateUrl: String,
        kind: PlaylistEntryKind,
    ): PlayableSource {
        val fromUrl = episodeNumberFromUrl(candidateUrl, allowWeak = epNumber <= 0f)
        val sort = fromUrl ?: epNumber
        val label =
            when {
                fromUrl != null -> episodeLabelFromNumber(fromUrl)
                epNumber > 0f -> "第 ${epNumber.toInt()} 话"
                else -> ""
            }
        return PlayableSource(
            url = candidateUrl,
            kind = kind,
            label = label,
            episodeSort = sort,
            siteName = siteName,
            pageUrl = pageUrl,
            headers = headers,
        )
    }

    val direct: List<PlayableSource> =
        (
            MEDIA_URL_REGEX.findAll(normalized).map { it.value } +
                MEDIA_TAG_REGEX.findAll(normalized).map { it.groupValues[1] }
        ).mapNotNull { absoluteUrl(it, origin) }
            .filterNot { isInvalidMediaHost(it) }
            .map { buildSource(it, PlaylistEntryKind.DIRECT) }
            .toList()

    val pages: List<PlayableSource> =
        IFRAME_TAG_REGEX
            .findAll(normalized)
            .mapNotNull { absoluteUrl(it.groupValues[1], origin) }
            .filterNot { candidate -> direct.any { it.url == candidate } }
            .map { buildSource(it, PlaylistEntryKind.PAGE) }
            .toList()

    return (direct + pages).take(MAX_CANDIDATES_PER_PAGE)
}

private val URL_FILE_EXTENSION = Regex("""\.(m3u8|mp4|mkv|flv|webm|ts)$""", RegexOption.IGNORE_CASE)
private val URL_NUMBER_TOKEN = Regex("""\d+(?:\.\d+)?""")
private val URL_YEAR_TOKEN = Regex("""^(?:19|20)\d{2}$""")
private val NOISE_NUMBERS = setOf("1080", "720", "480", "360", "240", "2160", "1440", "4320", "60")

/**
 * 从候选地址抽分集号（只做展示与排序标注，不做剧集规律推算）。
 *
 * 分两级信号，避免把 CDN 路径里的编号（`/hls/123/`）误当成集数：
 * - 强信号：带 ep/E/e/p 前缀的数字（`ep12`、`E07`）——无条件采用；
 * - 弱信号：末段或路径里最后一个裸数字——仅当调用方没有指定话数（整季请求）时采用。
 * 分辨率（1080p）、编码（h264/x265）、年份与超长 ID 段一律跳过。抓不到返回 null。
 * 末段（文件名）优先于整条路径。
 */
internal fun episodeNumberFromUrl(
    url: String,
    allowWeak: Boolean,
): Float? {
    val afterScheme = url.substringAfter("://")
    val pathWithSlash = afterScheme.substringAfter('/', missingDelimiterValue = "")
    if (pathWithSlash.isBlank()) return null
    val path = pathWithSlash.substringBefore('?').substringBefore('#').replace(URL_FILE_EXTENSION, "")
    val trimmedPath = path.trim('/')
    if (trimmedPath.isBlank()) return null

    val scopes = listOf(trimmedPath.substringAfterLast('/'), trimmedPath)
    for (scope in scopes) {
        val number = scope.numberToken(strongOnly = true) ?: continue
        return number
    }
    if (!allowWeak) return null
    for (scope in scopes) {
        val number = scope.numberToken(strongOnly = false) ?: continue
        return number
    }
    return null
}

private fun String.numberToken(strongOnly: Boolean): Float? {
    for (match in URL_NUMBER_TOKEN.findAll(this).toList().asReversed()) {
        val before = getOrNull(match.range.first - 1)?.lowercaseChar()
        val value = match.value
        if (isNoiseNumber(value, before)) continue
        val number = value.toFloatOrNull() ?: continue
        if (number <= 0f || number > 9999f) continue
        val strong = before == 'e' || before == 'p'
        if (strong || !strongOnly) return number
    }
    return null
}

private fun isNoiseNumber(
    value: String,
    before: Char?,
): Boolean =
    value in NOISE_NUMBERS ||
        URL_YEAR_TOKEN.matches(value) ||
        (before == 'h' || before == 'x') &&
        (value == "264" || value == "265")

/** 分集号转展示标签：整数不带小数点，小数保留（7.5 话） */
internal fun episodeLabelFromNumber(value: Float): String {
    val text =
        if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    return "第 $text 话"
}

/** scheme://host/ 形式的站点根，作为 Referer 与相对地址的基准 */
private fun pageOrigin(url: String): String? {
    val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return null
    val scheme = url.substring(0, schemeEnd)
    val rest = url.substring(schemeEnd + 3)
    val host = rest.substringBefore('/').substringBefore('?')
    if (host.isBlank()) return null
    return "$scheme://$host/"
}

/** 把协议相对/根相对/相对路径补成绝对地址；无法补全时返回 null */
private fun absoluteUrl(
    candidate: String,
    origin: String?,
): String? {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    if (origin == null) return null
    return when {
        trimmed.startsWith("//") -> origin.substringBefore("//") + trimmed
        trimmed.startsWith("/") -> origin.substringBeforeLast("/") + trimmed
        else -> origin + trimmed
    }
}

/** 针对单页或其子页面（单集页/iframe）执行流探测 */
private suspend fun sniffPageOrSubPages(
    fetched: FetchedPage,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
    title: String = "",
    budget: FetchBudget = FetchBudget(),
): List<PlayableSource> {
    // 1. 先试条目级采集接口的剧集串（MacCMS 形态）
    sniffDirectOrProtocolStream(fetched.html, fetched.url, epNumber, siteName, title)?.let {
        return it
    }

    // 2. 常规直链与 iframe 抽取
    val extracted = extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
    if (extracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
        return extracted
    }

    // 3. 检查单层 iframe
    sniffSingleIframe(extracted, epNumber, siteName, pageFetchService, budget, title)?.let {
        return it
    }

    // 4. 若页面无直接直链且无 iframe，尝试作为搜索列表页/目录页探测分集单集页面
    sniffEpisodeLinks(fetched.html, fetched.url, epNumber, siteName, pageFetchService, title, budget)?.let {
        return it
    }

    return extracted
}

private suspend fun sniffSingleIframe(
    extracted: List<PlayableSource>,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
    budget: FetchBudget,
    title: String,
): List<PlayableSource>? {
    val iframeCandidate = extracted.firstOrNull { it.kind == PlaylistEntryKind.PAGE } ?: return null
    if (iframeCandidate.url.isBlank()) return null
    return sniffPageWithIframe(iframeCandidate.url, epNumber, siteName, pageFetchService, title, budget)
}

private suspend fun sniffEpisodeLinks(
    html: String,
    url: String,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
    title: String,
    budget: FetchBudget,
): List<PlayableSource>? {
    val epLinks = extractEpisodeLinks(html, url, epNumber)
    for (epLink in epLinks) {
        val result = sniffPageWithIframe(epLink, epNumber, siteName, pageFetchService, title, budget)
        if (result != null) return result
    }
    return null
}

private suspend fun sniffPageWithIframe(
    pageUrl: String,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
    title: String = "",
    budget: FetchBudget = FetchBudget(),
): List<PlayableSource>? {
    if (!budget.take()) return null
    val fetched = pageFetchService.fetchHtml(pageUrl) ?: return null
    if (!pageBelongsToTitle(fetched.html, title)) return null
    // 条目级采集接口的响应是 JSON，没有 <title>，页面级校验对它恒为通过，
    // 所以片名要靠 MacCMS 层自己的 vod_name 复核（这里是唯一防线）
    val direct = sniffDirectPageStreams(fetched, epNumber, siteName, pageFetchService, budget, title)
    if (direct != null) return direct

    // 内嵌播放器常是"在线播放"这类通用标题，不对它做归属校验，否则会把正常站点拦死
    // 内嵌播放器常在另一个注册域下，跟进不受域约束（媒体地址本来就常托管在 CDN）
    val iframeUrl = extractIframeUrl(fetched.html, fetched.url, epNumber, siteName) ?: return null
    if (!budget.take()) return null
    val iframeFetched = pageFetchService.fetchHtml(iframeUrl) ?: return null
    return sniffDirectPageStreams(iframeFetched, epNumber, siteName, pageFetchService, budget)
}

private val PAGE_TITLE_REGEX =
    Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)

private val PAGE_H1_REGEX =
    Regex("""<h1\b[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE)

internal fun pageTitleOf(html: String): String? {
    val raw =
        PAGE_TITLE_REGEX.find(html)?.groupValues?.get(1)
            ?: PAGE_H1_REGEX
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.replace(Regex("<[^>]+>"), "")
    return raw?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 列表页下探到的这一页是不是还要的那部番。
 *
 * 只按话数匹配链接一定会撞号：搜索无结果时站点常回落到"最新番剧"列表，
 * 那里的 `MAO摩緒 [25]` 对第 25 话同样命中，于是"成功"播出了另一部片子。
 * 站点标题普遍带季号与话数后缀，所以按归一化后的包含关系比，退让到
 * 查询片名过半数字符出现在页面标题里；任一侧拿不到标题就不拦，
 * 这一版只针对"跳错番"，不承担标题改名匹配。
 */
internal fun pageBelongsToTitle(
    html: String,
    title: String,
): Boolean {
    if (title.isBlank()) return true
    val pageTitle = pageTitleOf(html) ?: return true
    return titleMatches(pageTitle, title)
}

/**
 * 归一化后的片名比对：包含关系优先，退让到"目标片名过半数字符出现在候选里"。
 *
 * 任一侧为空视为不可判定，返回 true（不拦），这一版只负责拦"明确是另一部片"。
 */
internal fun titleMatches(
    candidate: String,
    wanted: String,
): Boolean {
    if (candidate.isBlank() || wanted.isBlank()) return true
    val page = normalizeTitleForMatch(candidate)
    val target = normalizeTitleForMatch(wanted)
    if (page.isEmpty() || target.isEmpty()) return true
    if (page.contains(target) || target.contains(page)) return true
    return page.toSet().intersect(target.toSet()).size * 2 >= target.length
}

private fun normalizeTitleForMatch(text: String): String =
    ChineseConverter
        .toTraditional(text)
        .lowercase()
        .filter { it.isLetterOrDigit() }

private suspend fun sniffDirectPageStreams(
    fetched: FetchedPage,
    epNumber: Float,
    siteName: String,
    pageFetchService: PageFetchService,
    budget: FetchBudget = FetchBudget(),
    title: String = "",
): List<PlayableSource>? {
    sniffDirectOrProtocolStream(fetched.html, fetched.url, epNumber, siteName, title)?.let {
        return it
    }
    val extracted = extractPlayableSources(fetched.html, fetched.url, epNumber, siteName)
    if (extracted.any { it.kind == PlaylistEntryKind.DIRECT }) {
        return extracted
    }
    return null
}

private fun extractIframeUrl(
    html: String,
    url: String,
    epNumber: Float,
    siteName: String,
): String? =
    extractPlayableSources(html, url, epNumber, siteName)
        .firstOrNull { it.kind == PlaylistEntryKind.PAGE && it.url.isNotBlank() }
        ?.url

private suspend fun sniffDirectOrProtocolStream(
    html: String,
    url: String,
    epNumber: Float,
    siteName: String,
    title: String = "",
): List<PlayableSource>? {
    val macCms = extractMacCmsSources(html, url, epNumber, siteName, title)
    return macCms.takeIf { it.isNotEmpty() }
}

private val MACCMS_PLAY_URL_REGEX =
    Regex(""""(?:vod_play_url|url)"\s*:\s*"([^"]*\$[a-zA-Z0-9_/:.\-%?&=#]+)"""")

private val MACCMS_VOD_NAME_REGEX =
    Regex(""""vod_name"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

/**
 * 把 MacCMS 响应按 `vod_name` 切成逐条目片段。
 *
 * 搜索接口一次返回多部片子（同名续作、剧场版、不同季），只认第一段必然出现"播错番"——
 * 与列表页下探时校验片名归属是同一类问题。没有 `vod_name` 的响应
 * （播放页内嵌串、单条目详情接口）整体作为唯一一段，行为与从前一致。
 */
internal fun splitMacCmsEntries(html: String): List<String> {
    val names = MACCMS_VOD_NAME_REGEX.findAll(html).toList()
    if (names.isEmpty()) return listOf(html)
    return names.mapIndexed { index, match ->
        val start = match.range.first
        val end = names.getOrNull(index + 1)?.range?.first ?: html.length
        html.substring(start, end)
    }
}

private fun segmentVodName(segment: String): String? = MACCMS_VOD_NAME_REGEX.find(segment)?.groupValues?.get(1)

/**
 * 抽取 MacCMS 剧集直链。
 *
 * - 多条目响应优先取 [title] 对得上的那一段（同名续作、剧场版混在搜索结果里时避免播错番）；
 *   **一段都对不上时退回原来"取第一段"的行为**——源站 `vod_name` 可能是日文原名而 App 侧是中文译名，
 *   硬拦会把本来能用的源判死，这里只做"能认出来就用对的"，不做"认不出就拒绝"；
 * - 分隔符：`$$$` 分线路、`#` 分集、`集名$地址`；集名为空时用地址里的集号定位
 *   （从地址真实抽取，不按话数猜号）；
 * - 判定扩展名前先剥 query 与 fragment，否则 `x.m3u8?sign=…` 这类带签名直链会被整条丢掉。
 */
internal fun extractMacCmsSources(
    html: String,
    pageUrl: String,
    epNumber: Float,
    siteName: String,
    title: String = "",
): List<PlayableSource> {
    // HTML 内嵌 JSON 会把 & 转义成 &amp;，不还原则直链带着 &amp; 去播放必然取不到流
    val normalized = html.replace("\\/", "/").replace("&amp;", "&")
    val segments = splitMacCmsEntries(normalized)
    val segment =
        segments.firstOrNull { entry -> segmentVodName(entry)?.let { titleMatches(it, title) } ?: true }
            ?: segments.firstOrNull()
            ?: return emptyList()

    val targetEpInt = epNumber.toInt()
    val paddedEp = targetEpInt.toString().padStart(2, '0')
    val referer = pageOrigin(pageUrl)?.let { mapOf("Referer" to it) }.orEmpty()
    val results = mutableListOf<PlayableSource>()
    MACCMS_PLAY_URL_REGEX.find(segment)?.let { match ->
        val playListStr = match.groupValues[1].replace("\\/", "/")
        for (entry in playListStr.split("$$$").flatMap { it.split('#') }) {
            val parts = entry.split('$', limit = 2)
            if (parts.size < 2) continue
            val entryLabel = parts[0].trim()
            val mediaUrl = parts[1].trim()
            if (!isPlayableMediaUrl(mediaUrl)) continue
            if (epNumber > 0f && !entryMatchesEpisode(entryLabel, mediaUrl, targetEpInt, paddedEp)) continue
            results.add(
                PlayableSource(
                    url = mediaUrl,
                    kind = PlaylistEntryKind.DIRECT,
                    label = entryLabel,
                    episodeSort = epNumber,
                    siteName = siteName,
                    pageUrl = pageUrl,
                    headers = referer,
                ),
            )
        }
    }
    return results.distinctBy { it.url }.take(MAX_MANIFEST_ENTRIES)
}

private fun isPlayableMediaUrl(url: String): Boolean = URL_FILE_EXTENSION.containsMatchIn(url.substringBefore('#').substringBefore('?'))

private fun entryMatchesEpisode(
    entryLabel: String,
    mediaUrl: String,
    targetEpInt: Int,
    paddedEp: String,
): Boolean {
    if (entryLabel.isBlank()) {
        // 采集串偶尔不给集名（形如 `$http://…`），这时只能用地址里的集号定位
        return episodeNumberFromUrl(mediaUrl, allowWeak = true)?.toInt() == targetEpInt
    }
    return entryLabel.contains("第${targetEpInt}集") ||
        entryLabel.contains("第${paddedEp}集") ||
        entryLabel.contains("第${targetEpInt}话") ||
        entryLabel.contains("第${paddedEp}话") ||
        entryLabel.contains("[$targetEpInt]") ||
        entryLabel.contains("[$paddedEp]") ||
        entryLabel == targetEpInt.toString() ||
        entryLabel == paddedEp ||
        Regex("""\b(?:ep|e)?\s*0*$targetEpInt\b""", RegexOption.IGNORE_CASE).containsMatchIn(entryLabel)
}

private val A_TAG_REGEX =
    Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)

private val IGNORED_LINK_EXTS =
    setOf(".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2")

private val IGNORED_PATH_PREFIXES =
    setOf("notify", "about", "contact", "login", "register", "faq", "donate")

internal fun extractEpisodeLinks(
    html: String,
    pageUrl: String,
    targetEp: Float,
): List<String> {
    if (targetEp <= 0f) return emptyList()
    val origin = pageOrigin(pageUrl)
    val epInt = targetEp.toInt()
    val paddedEp = epInt.toString().padStart(2, '0')
    val candidates = mutableListOf<String>()
    val categoryFallbacks = mutableListOf<String>()

    val matches = A_TAG_REGEX.findAll(html).toList()
    for (match in matches) {
        val rawHref = match.groupValues[1].trim()
        val rawTag = match.value
        val text = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
        if (rawHref.isBlank() || rawHref.startsWith("#") || rawHref.startsWith("javascript:", ignoreCase = true)) {
            continue
        }
        val lowerHref = rawHref.lowercase()
        if (IGNORED_LINK_EXTS.any { lowerHref.endsWith(it) }) continue

        val fullUrl = absoluteUrl(rawHref, origin) ?: continue
        if (fullUrl == pageUrl) continue

        // 只在本注册域内下探，挡住 Twitter / Telegram 这类社交外链；
        // 主站与 m./v. 子站分属不同 host 但同域，按 host 相等会误杀
        if (!sameRegistrableDomain(pageUrl, fullUrl)) continue

        val pathAfterOrigin = fullUrl.removePrefix(origin ?: "").trim('/')
        if (pathAfterOrigin.isBlank()) continue
        val firstSegment = pathAfterOrigin.substringBefore('/').lowercase()
        if (IGNORED_PATH_PREFIXES.contains(firstSegment)) continue

        if (firstSegment == "category" || rawTag.contains("""rel="category tag"""", ignoreCase = true)) {
            categoryFallbacks.add(fullUrl)
            continue
        }

        val bracketMatch =
            Regex("""[\[\(【]\s*(?:$epInt|$paddedEp)(?:\.0)?\s*[\]\)】]""").containsMatchIn(text)
        val episodeWordMatch =
            Regex("""第\s*(?:$epInt|$paddedEp)\s*[集话話]""").containsMatchIn(text)
        val epTokenMatch =
            Regex("""(?i)\b(?:ep|e)\s*(?:$epInt|$paddedEp)\b""").containsMatchIn(text)
        val pureNumberMatch = text == epInt.toString() || text == paddedEp || text == targetEp.toString()
        val urlEpNumber = episodeNumberFromUrl(fullUrl, allowWeak = false)

        if (bracketMatch || episodeWordMatch || epTokenMatch || pureNumberMatch || urlEpNumber == targetEp) {
            candidates.add(fullUrl)
        }
    }
    val results = candidates.distinct()
    if (results.isNotEmpty()) {
        return results.take(2)
    }
    return categoryFallbacks.distinct().take(1)
}

internal fun decodeUrlComponent(encoded: String): String {
    val sb = StringBuilder()
    var i = 0
    val len = encoded.length
    while (i < len) {
        val c = encoded[i]
        if (c == '%' && i + 2 < len) {
            val hex = encoded.substring(i + 1, i + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        } else if (c == '+') {
            sb.append(' ')
            i++
            continue
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}
