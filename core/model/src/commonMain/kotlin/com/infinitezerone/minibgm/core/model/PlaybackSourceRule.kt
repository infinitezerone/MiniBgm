package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/** 规则用途：PAGE = 生成给用户跳转的页面；SOURCE = 请求后从响应里取可播放地址 */
enum class PlaybackRuleKind {
    PAGE,
    SOURCE,
}

/** 规则解析器类型 */
@Serializable
enum class RuleParserType {
    AUTO,
    MACCMS,
    STREMIO,
    PIPELINE,
}

/** 流水线单步操作类型 */
@Serializable
enum class StepAction {
    FETCH,
    EXTRACT_VARIABLE,
    EXTRACT_STREAM,
}

/**
 * 声明式流水线单步定义。
 *
 * - [action]: 操作类型（发起请求、提取变量、提取媒体直链）
 * - [urlTemplate]: 目标地址模板，支持占位符与上下文变量（如 `{title}`, `{ep}`, `{varName}`）
 * - [method]: HTTP 方法，"GET" 或 "POST"
 * - [bodyTemplate]: POST 请求体模板（如 `d={apireq}`）
 * - [headers]: 附带的固定或动态请求头
 * - [regex]: 用于从响应文本中抓取变量或直链的正则表达式。EXTRACT_VARIABLE 取第一个捕获组；
 *   EXTRACT_STREAM 为列表语义——第 1 组是直链（无组时整个匹配），可选第 2 组是集名/集号文本，
 *   引擎收集全部候选并按请求话数自动选条目，规则无需把 {ep} 锚死在正则里
 * - [variableName]: 提取到的变量存入上下文的键名（支持以 `{variableName}` 引用）
 * - [captureHeaders]: 需要从 HTTP 响应头中捕获并透传给播放器的 Header 名称列表（如 "Set-Cookie" 或 "Cookie"）
 */
@Serializable
data class PipelineStep(
    val action: StepAction,
    val urlTemplate: String = "",
    val method: String = "GET",
    val bodyTemplate: String = "",
    val headers: Map<String, String> = emptyMap(),
    val regex: String = "",
    val variableName: String = "",
    val captureHeaders: List<String> = emptyList(),
)

/**
 * 自定义番剧播放/跳转规则数据模型。
 *
 * [urlTemplate] 支持通用占位符：
 * - `{title}`: 番剧名称（将进行 URL 编码）
 * - `{ep}`: 分集编号（如 1, 2）
 * - `{subjectId}`: Bangumi 条目 ID
 * - `{episodeId}`: Bangumi 分集 ID
 *
 * [kind] 为 SOURCE 时，[headers] 会随模板请求一起发出（Referer / User-Agent 等固定头，不含登录态）。
 * 响应识别根据 [parserType] 分发：
 * - [RuleParserType.PIPELINE]: 按 [pipeline] 声明的多步骤执行
 * - [RuleParserType.MACCMS]: 按 MacCMS V10 标准接口提取
 * - [RuleParserType.STREMIO]: 按 Stremio 流清单提取
 * - [RuleParserType.AUTO]: 自动识别清单、MacCMS 及智能嗅探
 */
@Serializable
data class PlaybackSourceRule(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val isEnabled: Boolean = true,
    val description: String = "",
    val kind: PlaybackRuleKind = PlaybackRuleKind.PAGE,
    val headers: Map<String, String> = emptyMap(),
    val parserType: RuleParserType = RuleParserType.AUTO,
    val pipeline: List<PipelineStep> = emptyList(),
) {
    /**
     * 针对具体分集安全替换占位符并返回解析后的目标 URL。
     */
    fun resolveUrl(
        title: String,
        ep: String,
        subjectId: Long = 0L,
        episodeId: Long = 0L,
    ): String =
        urlTemplate
            .replace("{title}", encodeParam(title))
            .replace("{ep}", encodeParam(ep))
            .replace("{subjectId}", subjectId.toString())
            .replace("{episodeId}", episodeId.toString())

    /**
     * 规则里配置的解析方式是否真有执行路径。
     *
     * 解析器分发只发生在取源侧（[PlaybackRuleKind.SOURCE]）；[PlaybackRuleKind.PAGE] 规则会被当作
     * 跳转页候选处理，[pipeline] 与 [parserType] 到那时已经被丢掉。把非 AUTO 解析器写在 PAGE 规则上
     * （或给了 [pipeline] 却没声明 [RuleParserType.PIPELINE]）不会报错，只会静默降级成智能嗅探，
     * 因此这类组合不该被导入。
     */
    val isResolvable: Boolean
        get() =
            when {
                pipeline.isNotEmpty() -> kind == PlaybackRuleKind.SOURCE && parserType == RuleParserType.PIPELINE
                else -> parserType == RuleParserType.AUTO || kind == PlaybackRuleKind.SOURCE
            }

    companion object {
        fun encodeParam(value: String): String =
            buildString {
                for (byte in value.encodeToByteArray()) {
                    val code = byte.toInt() and 0xFF
                    if (isUnreserved(code)) {
                        append(code.toChar())
                    } else {
                        append('%')
                        val hex = code.toString(16).uppercase()
                        if (hex.length == 1) append('0')
                        append(hex)
                    }
                }
            }

        private fun isUnreserved(code: Int): Boolean =
            (code in 'a'.code..'z'.code) ||
                (code in 'A'.code..'Z'.code) ||
                (code in '0'.code..'9'.code) ||
                code == '-'.code ||
                code == '_'.code ||
                code == '.'.code ||
                code == '~'.code
    }
}

/**
 * 网页多媒体结构探测结果，供调试分析与 AI 规则生成使用。
 */
@Serializable
data class PageInspectionResult(
    val url: String,
    val isSuccess: Boolean,
    val title: String = "",
    val hasVideoTag: Boolean = false,
    val videoAttrs: Map<String, String> = emptyMap(),
    val iframeUrls: List<String> = emptyList(),
    val hasMacCmsPattern: Boolean = false,
    val responseHeaders: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * 捕获到的单次网络请求记录。
 */
@Serializable
data class CapturedNetworkCall(
    val url: String,
    val method: String = "GET",
    val requestHeaders: Map<String, String> = emptyMap(),
    val isMedia: Boolean = false,
    val isApi: Boolean = false,
)

/**
 * 页面动态网络流量审计轨迹，记录 WebView 渲染期间发生的所有核心调用。
 */
@Serializable
data class NetworkAuditTrace(
    val pageUrl: String,
    val finalUrl: String = pageUrl,
    val isReachable: Boolean = true,
    val title: String = "",
    val calls: List<CapturedNetworkCall> = emptyList(),
    val cookies: Map<String, String> = emptyMap(),
    val mediaSources: List<PlayableSource> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * 站点健康度探查与播放样本自动定位结果。
 */
@Serializable
data class ProbeSiteOutput(
    val siteUrl: String,
    val isReachable: Boolean,
    val isAdParking: Boolean = false,
    val title: String = "",
    val sampleEpisodeUrl: String? = null,
    val hasSearchBox: Boolean = false,
    val searchUrlPattern: String? = null,
    /** 本次探查实际使用的样本片名——调用方据此判断样本是否代表真实需求 */
    val sampleTitleUsed: String? = null,
    val errorMessage: String? = null,
)
