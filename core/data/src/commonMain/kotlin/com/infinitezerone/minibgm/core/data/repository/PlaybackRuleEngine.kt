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
                            val matchedValue =
                                fetchedPage.responseHeaders.entries
                                    .firstOrNull { it.key.equals(headerName, ignoreCase = true) }
                                    ?.value
                            if (!matchedValue.isNullOrBlank()) {
                                capturedHeaders[headerName] = matchedValue
                                variables[headerName] = matchedValue
                            }
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
