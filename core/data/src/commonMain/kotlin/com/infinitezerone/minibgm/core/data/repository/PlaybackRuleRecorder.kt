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
 */
object PlaybackRuleRecorder {
    /** 媒体请求常常依赖这几个头才能拉流，录制时保留；其余头（Accept 等）由抓取层自行决定 */
    private val REPLAYABLE_HEADERS = setOf("referer", "user-agent", "origin")

    fun recordFromTrace(
        trace: NetworkAuditTrace,
        ruleName: String,
        title: String,
        ep: String,
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
            notes += "EXTRACT_STREAM 的 regex 留空待补：审计看不到响应正文，正则只能照着真实响应写"
        }

        return RecordedRuleDraft(
            ruleName = ruleName,
            pageUrl = entryUrl,
            steps = steps,
            mediaUrl = mediaUrl,
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
}
