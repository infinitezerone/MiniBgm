package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PipelineStep
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.StepAction
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 声明式播放源规则执行引擎。
 *
 * 负责执行 [PlaybackSourceRule] 中配置的 [PipelineStep] 流水线，
 * 依次进行 HTTP 请求调度、变量提取、Cookie/Header 捕获与最终直链组装。
 */
interface PlaybackRuleEngine {
    /**
     * 执行指定规则的声明式流水线。
     *
     * @param rule 待执行的规则
     * @param title 番剧标题
     * @param epNumber 分集号（0f 表示未指定或整季）
     * @param subjectId Bangumi 条目 ID
     * @param episodeId Bangumi 分集 ID
     * @return 解析到的可播放来源列表，若执行失败或条件不满足返回空列表
     */
    suspend fun executePipeline(
        rule: PlaybackSourceRule,
        title: String,
        epNumber: Float = 0f,
        subjectId: Long = 0L,
        episodeId: Long = 0L,
    ): List<PlayableSource>
}

class PlaybackRuleEngineImpl(
    private val pageFetchService: PageFetchService,
) : PlaybackRuleEngine {
    override suspend fun executePipeline(
        rule: PlaybackSourceRule,
        title: String,
        epNumber: Float,
        subjectId: Long,
        episodeId: Long,
    ): List<PlayableSource> =
        withContext(Dispatchers.Default) {
            if (rule.pipeline.isEmpty()) return@withContext emptyList()
            // 步骤由规则自带，但请求总数仍要设上限：额度用尽即判这次解析无结果
            val budget = FetchBudget()

            val epInt = epNumber.toInt()
            val epStr = if (epNumber > 0f) epInt.toString() else ""
            val paddedEp = if (epNumber > 0f) epInt.toString().padStart(2, '0') else ""

            val variables =
                mutableMapOf(
                    "title" to PlaybackSourceRule.encodeParam(title),
                    "rawTitle" to title,
                    "ep" to epStr,
                    "paddedEp" to paddedEp,
                    "subjectId" to subjectId.toString(),
                    "episodeId" to episodeId.toString(),
                )

            var lastHtml = ""
            var lastUrl = ""
            val capturedHeaders = mutableMapOf<String, String>()

            for (step in rule.pipeline) {
                when (step.action) {
                    StepAction.FETCH -> {
                        if (!budget.take()) return@withContext emptyList()
                        val rawUrl = if (step.urlTemplate.isNotBlank()) step.urlTemplate else rule.urlTemplate
                        val resolvedUrl = replacePlaceholders(rawUrl, variables)
                        if (resolvedUrl.isBlank()) return@withContext emptyList()

                        val stepHeaders = step.headers.mapValues { replacePlaceholders(it.value, variables) }
                        val mergedRequestHeaders = rule.headers + stepHeaders

                        val fetchedPage: FetchedPage? =
                            if (step.method.equals("POST", ignoreCase = true)) {
                                val body = replacePlaceholders(step.bodyTemplate, variables)
                                val formMap = parseFormData(body)
                                pageFetchService.postForm(
                                    url = resolvedUrl,
                                    formData = formMap,
                                    requestHeaders = mergedRequestHeaders,
                                )
                            } else {
                                pageFetchService.fetchHtml(
                                    url = resolvedUrl,
                                    requestHeaders = mergedRequestHeaders,
                                )
                            }

                        if (fetchedPage == null) return@withContext emptyList()

                        lastHtml = fetchedPage.html
                        lastUrl = fetchedPage.url
                        variables["pageUrl"] = lastUrl

                        for (headerName in step.captureHeaders) {
                            val captured = captureResponseHeader(fetchedPage, headerName) ?: continue
                            capturedHeaders[captured.first] = captured.second
                            // 原名与规范名都注册，模板里写 {Set-Cookie} 或 {Cookie} 都能取到值
                            variables[headerName] = captured.second
                            variables[captured.first] = captured.second
                        }
                    }

                    StepAction.EXTRACT_VARIABLE -> {
                        if (step.regex.isBlank() || step.variableName.isBlank()) continue
                        val regex = Regex(step.regex, RegexOption.IGNORE_CASE)
                        val match = regex.find(lastHtml) ?: return@withContext emptyList()
                        val rawValue = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                        val finalValue = if (rawValue.contains('%')) decodeUrlComponent(rawValue) else rawValue
                        variables[step.variableName] = finalValue
                    }

                    StepAction.EXTRACT_STREAM -> {
                        if (step.regex.isBlank()) return@withContext emptyList()
                        val regex = Regex(step.regex, RegexOption.IGNORE_CASE)
                        val match = regex.find(lastHtml) ?: return@withContext emptyList()
                        val rawStream = (if (match.groupValues.size > 1) match.groupValues[1] else match.value).replace("\\/", "/")

                        val streamUrl = normalizeStreamUrl(rawStream, lastUrl) ?: return@withContext emptyList()

                        val finalHeaders = mutableMapOf<String, String>()
                        finalHeaders.putAll(rule.headers)
                        finalHeaders.putAll(step.headers.mapValues { replacePlaceholders(it.value, variables) })
                        finalHeaders.putAll(capturedHeaders)

                        val label = if (epNumber > 0f) "第 $epInt 话" else ""
                        return@withContext listOf(
                            PlayableSource(
                                url = streamUrl,
                                kind = PlaylistEntryKind.DIRECT,
                                label = label,
                                episodeSort = epNumber,
                                siteName = rule.name,
                                pageUrl = lastUrl,
                                headers = finalHeaders,
                            ),
                        )
                    }
                }
            }

            return@withContext emptyList()
        }

    /**
     * 从响应里取一个头，并规范化成"能作为请求头发出去"的形态。
     *
     * `Set-Cookie` 是唯一需要改名的：它是响应头，原样当请求头发出去无效——头名不对，
     * 值里还带 `Path`/`HttpOnly`/`Expires` 等属性。抓取层已经把多个 Set-Cookie 归并成
     * `Cookie: a=1; b=2`（见 [FetchedPage.responseHeaders] 的约定），优先用那个；
     * 拿不到时退而自行提取，不依赖抓取实现的具体细节。
     */
    private fun captureResponseHeader(
        page: FetchedPage,
        headerName: String,
    ): Pair<String, String>? {
        fun headerValueOf(name: String): String? =
            page.responseHeaders.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }

        if (!headerName.equals("Set-Cookie", ignoreCase = true)) {
            return headerValueOf(headerName)?.let { headerName to it }
        }
        val normalized = headerValueOf("Cookie") ?: headerValueOf("Set-Cookie")?.let(::cookiePairsOf)
        return normalized?.let { "Cookie" to it }
    }

    private fun replacePlaceholders(
        template: String,
        variables: Map<String, String>,
    ): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    private fun parseFormData(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in body.split('&')) {
            val parts = pair.split('=', limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0].trim()
                val value = if (parts.size > 1) parts[1].trim() else ""
                if (key.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    private fun normalizeStreamUrl(
        streamUrl: String,
        baseUrl: String,
    ): String? {
        val trimmed = streamUrl.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        if (baseUrl.isNotBlank() && baseUrl.contains("://")) {
            val scheme = baseUrl.substringBefore("://")
            val host = baseUrl.substringAfter("://").substringBefore('/')
            return if (trimmed.startsWith("/")) {
                "$scheme://$host$trimmed"
            } else {
                "$scheme://$host/$trimmed"
            }
        }
        return null
    }
}

/**
 * 从 `Set-Cookie` 原始值里提取可回传的 `name=value` 集合。
 *
 * 多个 cookie 常被合并成一行（换行或 `", "` 分隔），而属性里也含逗号
 * （`Expires=Wed, 21 Oct 2025 07:28:00 GMT`），所以不能简单按逗号切。
 * 这里逐段用 `name=value` 的形态去匹配，匹配不上的属性碎片自然被丢掉。
 */
private val COOKIE_PAIR_REGEX = Regex("""([A-Za-z0-9_\-!#%&'*+.^`|~]+)\s*=\s*([^;,\s]+)""")

internal fun cookiePairsOf(rawSetCookie: String): String? =
    rawSetCookie
        .split('\n', ',')
        .mapNotNull { segment ->
            COOKIE_PAIR_REGEX.find(segment)?.let { "${it.groupValues[1]}=${it.groupValues[2]}" }
        }.distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("; ")
