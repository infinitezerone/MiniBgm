package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.NetworkAuditTrace
import com.infinitezerone.minibgm.core.model.PipelineStep
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.StepAction
import kotlinx.serialization.Serializable

/**
 * 从一次真实网络审计归纳出的规则草案。
 *
 * **它不是一条能直接导入的规则**：[PipelineStep] 里的 `EXTRACT_STREAM` 正则刻意留空——
 * 审计只能看到"页面发出了哪些请求"，看不到响应正文，而正则必须照着响应写。
 *
 * 它的价值在于：把**真实观测到**的接口地址、方法、请求头交给调用方，
 * 而不是让模型凭空编一个接口 URL。模型要做的从"猜整条链路"缩减成"照响应补一条正则"。
 */
@Serializable
data class RecordedRuleDraft(
    val ruleName: String,
    val pageUrl: String,
    val steps: List<PipelineStep>,
    /** 本次审计直接抓到的媒体地址。非空说明这一次根本不依赖规则，但仍可当作正则线索 */
    val mediaUrl: String? = null,
    /** 重放接口取回的响应样本：有它才能写 `EXTRACT_STREAM` 的正则，而不是靠猜 */
    val apiSamples: List<ApiResponseSample> = emptyList(),
    /** 需要补全之处，逐条说清为什么——每一条都对应一处模型必须自己判断的地方 */
    val notes: List<String> = emptyList(),
)

/**
 * 规则录制器：把网络审计的观测结果归纳成可重放的流水线骨架。
 *
 * 对应业界"探索一次、录成序列、之后零 LLM 重放"里的**录制**这一半
 * （重放器是 [PlaybackRuleEngine]）。与浏览器 agent 框架不同的是，这里无法录到
 * 点击动作与请求体：`shouldInterceptRequest` 只有 URL 与方法，所以只归纳请求序列，
 * 缺的部分写进 [RecordedRuleDraft.notes] 交给调用方处理。
 *
 * 本类保持**纯归纳**——不联网、不做 IO。响应样本由调用方先经
 * [PlaybackRuleSampleReplayer] 取回再传进来（[apiSamples]），这样这一层可确定性测试。
 */
object PlaybackRuleRecorder {
    /** 媒体请求常常依赖这几个头才能拉流，录制时保留；其余头（Accept 等）由抓取层自行决定 */
    internal val REPLAYABLE_HEADERS = setOf("referer", "user-agent", "origin")

    fun recordFromTrace(
        trace: NetworkAuditTrace,
        ruleName: String,
        title: String,
        ep: String,
        apiSamples: List<ApiResponseSample> = emptyList(),
    ): RecordedRuleDraft {
        val notes = mutableListOf<String>()
        val entryUrl = trace.finalUrl.ifBlank { trace.pageUrl }
        if (!trace.isReachable) {
            notes += "审计报告页面不可达，草案仅供参考"
        }

        val steps = mutableListOf<PipelineStep>()
        if (entryUrl.isNotBlank()) {
            steps += PipelineStep(action = StepAction.FETCH, urlTemplate = parametrizeUrl(entryUrl, title))
        }

        val apiCalls = trace.calls.filter { it.isApi && !it.isMedia }
        for (call in apiCalls) {
            val isGet = call.method.equals("GET", ignoreCase = true)
            if (!isGet) {
                notes +=
                    "「${call.url.take(80)}」是 ${call.method} 请求；网络审计看不到请求体，" +
                    "必须手工补 bodyTemplate 才可能重放成功"
            }
            steps +=
                PipelineStep(
                    action = StepAction.FETCH,
                    method = call.method.uppercase(),
                    urlTemplate = parametrizeUrl(call.url, title),
                    headers = call.requestHeaders.filterKeys { it.lowercase() in REPLAYABLE_HEADERS },
                )
        }

        if (title.isNotBlank()) {
            val sample = if (ep.isNotBlank()) "（本次样本用的是第 $ep 话）" else ""
            notes += "片名已参数化为 {title}$sample；URL 里代表集数的参数请自行改成 {ep}——" +
                "哪个参数是集数无法可靠判断，代码不替你做这个猜测"
        }

        val mediaUrl =
            trace.mediaSources.firstOrNull()?.url
                ?: trace.calls.firstOrNull { it.isMedia }?.url
        val mediaHeaders =
            trace.calls
                .firstOrNull { it.isMedia }
                ?.requestHeaders
                ?.filterKeys { it.lowercase() in REPLAYABLE_HEADERS }
                .orEmpty()

        when {
            mediaUrl != null ->
                notes +=
                    "本次审计直接抓到了媒体地址，说明这条链路可以不靠规则走通；" +
                    "若仍要固化成规则，正则可照该地址在被抽页面里的上下文来写"
            apiCalls.isEmpty() ->
                notes += "审计没有抓到接口或媒体请求，这个站点很可能需要先点击/滚动才会发起请求——" +
                    "当前捕获通道是纯被动观察，拿不到这类请求"
        }

        if (steps.none { it.action == StepAction.EXTRACT_STREAM }) {
            steps += PipelineStep(action = StepAction.EXTRACT_STREAM, headers = mediaHeaders)
        }
        if (steps.size <= 1 && mediaUrl == null) {
            notes += "没有归纳出可重放的步骤，先确认审计是否跑在正确的播放页上"
        }
        if (mediaUrl != null || apiCalls.isNotEmpty()) {
            val succeeded = apiSamples.count { it.ok }
            val failed = apiSamples.size - succeeded
            notes +=
                when {
                    succeeded > 0 ->
                        "已重放 $succeeded 条接口请求并附上响应片段（apiSamples）——" +
                            "EXTRACT_STREAM 的 regex 照着片段里承载直链的字段写，不要凭印象编字段名" +
                            "；响应含多集时正则第 1 组捕获直链、第 2 组捕获集名文本，引擎会按话数自动选条目，" +
                            "不要把 {ep} 锚死在正则里" +
                            if (failed > 0) "；另有 $failed 条没取到响应，失败原因见各自的 note" else ""
                    apiSamples.isNotEmpty() ->
                        "所有接口重放都没取到响应（原因见 apiSamples[].note）：" +
                            "要么站点需要按页面上下文才认这次请求，要么该请求只能在点击后发生"
                    else ->
                        "EXTRACT_STREAM 的 regex 留空待补：没有可用响应样本，正则只能照着真实响应写"
                }
        }

        return RecordedRuleDraft(
            ruleName = ruleName,
            pageUrl = entryUrl,
            steps = steps,
            mediaUrl = mediaUrl,
            apiSamples = apiSamples,
            notes = notes,
        )
    }

    /**
     * 把 URL 里的片名换成 `{title}` 占位符，让同一条规则能复用到别的番上。
     *
     * 原文与 URL 编码两种形态都替换（站点用哪种取决于它自己的模板）。
     *
     * **话数刻意不自动替换**：判断"哪个查询参数是集数"只能靠猜——按值匹配会连 `&page=1`
     * 一起改坏，按参数名匹配又得维护一张站点命名白名单。调用方手上有真实 URL，
     * 自己把集数参数改成 `{ep}` 比代码猜可靠得多。
     */
    internal fun parametrizeUrl(
        url: String,
        title: String,
    ): String {
        if (title.isBlank()) return url
        return url
            .replace(PlaybackSourceRule.encodeParam(title), "{title}", ignoreCase = true)
            .replace(title, "{title}")
    }

    /** 静态页直读时抽媒体直链用：与抓取层/捕获层同一套扩展名，三层口径保持一致 */
    private val STATIC_MEDIA_REGEX =
        Regex(
            """https?://[^"'()\s<>]+?\.(?:m3u8|mp4|mkv|flv|webm|ts)(?:\?[^"'()\s<>]*)?""",
            RegexOption.IGNORE_CASE,
        )

    private val IFRAME_SRC_REGEX =
        Regex("""<iframe\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** 百分号编码段：整段或部分编码的直链都要能还原，非法序列原样保留 */
    private val PERCENT_ENCODED_RUN = Regex("""(?:%[0-9A-Fa-f]{2})+""")

    private fun decodePercentRuns(html: String): String =
        PERCENT_ENCODED_RUN.replace(html) { match -> percentDecode(match.value) ?: match.value }

    /** commonMain 没有现成的 percent 解码：按字节还原再按 UTF-8 解，与 encodeParam 互逆 */
    private fun percentDecode(encoded: String): String? {
        if (encoded.isEmpty() || encoded.length % 3 != 0) return null
        val bytes = ByteArray(encoded.length / 3)
        for (i in bytes.indices) {
            if (encoded[i * 3] != '%') return null
            bytes[i] = (encoded.substring(i * 3 + 1, i * 3 + 3).toIntOrNull(16) ?: return null).toByte()
        }
        return bytes.decodeToString()
    }

    /** 最多采几条媒体候选：采样是为了写正则，不是替调用方做选择 */
    private const val MAX_STATIC_MEDIA: Int = 3

    /** 媒体地址前后的源码上下文窗口（字符数）：正则照片段写，片段太长反而稀释信号 */
    private const val STATIC_CONTEXT_CHARS: Int = 200

    /**
     * 直读静态页源码归纳规则草案——WebView 审计的便宜替代。
     *
     * 适用面：直链或接口地址就写在 HTML 里的站点（MacCMS 模板站是大头）。
     * 一次普通抓取就拿到了响应正文，`EXTRACT_STREAM` 的正则照源码上下文写即可，
     * 不需要重放取样。页面由 JS 渲染或直链只在运行时出现时抽不到东西，
     * notes 会明确说明，让调用方改走网络审计。
     *
     * 与 [recordFromTrace] 一样保持**纯归纳**——不联网、不做 IO。
     */
    fun recordFromStaticPage(
        pageUrl: String,
        html: String,
        ruleName: String,
        title: String,
        ep: String = "",
    ): RecordedRuleDraft {
        val notes = mutableListOf<String>()
        val entryUrl = pageUrl.trim()
        val steps = mutableListOf<PipelineStep>()
        if (entryUrl.isNotBlank()) {
            steps += PipelineStep(action = StepAction.FETCH, urlTemplate = parametrizeUrl(entryUrl, title))
        }

        var mediaUrls =
            STATIC_MEDIA_REGEX
                .findAll(html)
                .map { it.value }
                .distinct()
                .toList()
        var mediaSource = html
        var percentDecoded = false
        if (mediaUrls.isEmpty()) {
            // MacCMS 播放器配置常把直链整段百分号编码后写进源码（实测 moonci 类站点），解码一遍再抽
            val decodedHtml = decodePercentRuns(html)
            val decodedUrls =
                STATIC_MEDIA_REGEX
                    .findAll(decodedHtml)
                    .map { it.value }
                    .distinct()
                    .take(MAX_STATIC_MEDIA)
                    .toList()
            if (decodedUrls.isNotEmpty()) {
                mediaUrls = decodedUrls
                mediaSource = decodedHtml
                percentDecoded = true
            }
        }
        val candidates = mediaUrls.take(MAX_STATIC_MEDIA)
        val mediaUrl = candidates.firstOrNull()

        if (mediaUrl != null) {
            steps +=
                PipelineStep(
                    action = StepAction.EXTRACT_STREAM,
                    // 静态路径没有捕获到的真实请求头；Referer 用播放页地址是来源校验的常规要求
                    headers = entryUrl.takeIf { it.isNotBlank() }?.let { mapOf("Referer" to it) }.orEmpty(),
                )
            if (percentDecoded) {
                notes +=
                    "直链在源码里是百分号编码形态（MacCMS 播放器配置常见），解码后得到：" +
                    candidates.joinToString("、") + "——EXTRACT_STREAM 的正则写解码后的明文形态，照下面的上下文写"
            } else {
                notes +=
                    "静态页源码里直接抽到了媒体地址（${candidates.joinToString("、")}）——" +
                    "正则照下面的源码上下文写，不要凭印象编字段名"
            }
            if (candidates.size > 1) {
                notes +=
                    "源码里有多条媒体地址（可能对应不同分集或线路）：正则第 1 组捕获直链、第 2 组捕获集名或编号文本，" +
                    "引擎会按请求话数自动选条目；不要只锚定当前样本那一集"
            }
            notes += "媒体地址的源码上下文：${contextAround(mediaSource, mediaUrl)}"
        } else {
            notes +=
                "静态 HTML 里没有直链：页面很可能由 JS 渲染或直链由接口在运行时返回——" +
                "改用 traceNetworkTraffic 做网络审计，静态路径对这类站点无能为力"
        }

        if (html.contains("vod_play_url") || html.contains("player_aaaa")) {
            notes +=
                "检测到 MacCMS 特征（vod_play_url / player_aaaa）：优先考虑 parserType=MACCMS 的规则，" +
                "引擎有现成的标准接口解析，pipeline 反而绕远。注意部分站点会关闭开放接口（接口返回 closed），" +
                "此时从 player_aaaa 配置里解码直链，解不出明文再转网络审计"
        }

        val iframes =
            IFRAME_SRC_REGEX
                .findAll(html)
                .map { it.groupValues[1] }
                .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                .distinct()
                .take(MAX_STATIC_MEDIA)
                .toList()
        if (iframes.isNotEmpty()) {
            notes +=
                "页面内嵌 iframe 播放器（${iframes.joinToString("、")}）：真正承载播放器的可能是其中之一，" +
                "可对它再走一次静态直读"
        }

        if (title.isNotBlank()) {
            val sample = if (ep.isNotBlank()) "（本次样本用的是第 $ep 话）" else ""
            notes += "片名已参数化为 {title}$sample；URL 里代表集数的参数请自行改成 {ep}——" +
                "哪个参数是集数无法可靠判断，代码不替你做这个猜测"
        }

        if (steps.none { it.action == StepAction.EXTRACT_STREAM }) {
            steps += PipelineStep(action = StepAction.EXTRACT_STREAM)
        }
        if (steps.size <= 1 && mediaUrl == null) {
            notes += "没有归纳出可重放的步骤，先确认这个地址是不是真的播放页"
        }

        return RecordedRuleDraft(
            ruleName = ruleName,
            pageUrl = entryUrl,
            steps = steps,
            mediaUrl = mediaUrl,
            apiSamples = emptyList(),
            notes = notes,
        )
    }

    /** 媒体地址在页面源码里的上下文窗口，供模型照着写 EXTRACT_STREAM 正则 */
    private fun contextAround(
        html: String,
        needle: String,
    ): String {
        val index = html.indexOf(needle)
        if (index < 0) return "（源码里没定位到该地址）"
        val start = (index - STATIC_CONTEXT_CHARS).coerceAtLeast(0)
        val end = (index + needle.length + STATIC_CONTEXT_CHARS).coerceAtMost(html.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < html.length) "…" else ""
        val context = html.substring(start, end).replace('\n', ' ').replace('\r', ' ')
        return "$prefix$context$suffix"
    }
}
