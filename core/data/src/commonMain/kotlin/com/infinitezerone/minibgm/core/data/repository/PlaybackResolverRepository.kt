package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.resolver.countMacCmsListItems
import com.infinitezerone.minibgm.core.data.repository.resolver.detectAdParking
import com.infinitezerone.minibgm.core.data.repository.resolver.detectSearchUrlPattern
import com.infinitezerone.minibgm.core.data.repository.resolver.extractMacCmsSources
import com.infinitezerone.minibgm.core.data.repository.resolver.findHomeEntryTitle
import com.infinitezerone.minibgm.core.data.repository.resolver.macCmsProbeCandidates
import com.infinitezerone.minibgm.core.data.repository.resolver.pageOrigin
import com.infinitezerone.minibgm.core.data.repository.resolver.parseStreamManifest
import com.infinitezerone.minibgm.core.data.repository.resolver.probeSampleEpisodeUrl
import com.infinitezerone.minibgm.core.data.repository.resolver.sniffPageOrSubPages
import com.infinitezerone.minibgm.core.model.MacCmsProbeResult
import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PageInspectionResult
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleApi
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.ProbeSiteOutput
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            // 版本门控的执行侧防线：导入入口会拒收 minClientApi 超限的规则，但备份恢复、
            // 旧客户端写库等路径仍可能让高要求规则入库——宁可无候选，不可按未知语义跑
            if (rule.minClientApi > PlaybackRuleApi.SUPPORTED_RULE_API) return@withContext emptyList()
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
