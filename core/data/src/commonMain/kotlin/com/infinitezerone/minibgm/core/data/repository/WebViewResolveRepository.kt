package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.asAppResult
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import kotlinx.coroutines.flow.first

/** 单次深度解析最多尝试的页面数（WebView 会话昂贵，逐页试、命中即停） */
private const val MAX_DEEP_PAGES = 3

/**
 * WebView 深度解析服务接口（第 5 档：确定性运行时捕获）。
 * 实现在 :core:webview（Android-only），经 DI 注入；捕获日志即真值。
 */
interface WebViewCaptureService {
    suspend fun capturePlayableSources(pageUrl: String): List<PlayableSource>
}

/**
 * WebView 深度解析仓库：为指定条目挑选候选页并逐页捕获，命中即停。
 *
 * 候选页与 AI 找源工具同源：排期记录的来源站页优先，没有记录时退到 B 站搜索页；
 * 失败一律返回错误/空结果，不向调用方抛业务异常。
 */
interface WebViewResolveRepository {
    suspend fun deepResolve(subjectId: Long): AppResult<List<PlayableSource>>
}

class WebViewResolveRepositoryImpl(
    private val scheduleRepository: ScheduleRepository,
    private val captureService: WebViewCaptureService,
) : WebViewResolveRepository {
    override suspend fun deepResolve(subjectId: Long): AppResult<List<PlayableSource>> =
        asAppResult(errorMessage = { it.message ?: "WebView 深度解析失败" }) {
            val schedule = scheduleRepository.getAllSchedulesStream().first().firstOrNull { it.bgmId == subjectId }
            val pages = candidatePages(subjectId, schedule)
            var lastError: BgmNetworkException? = null
            for (page in pages) {
                val sources =
                    try {
                        captureService.capturePlayableSources(page)
                    } catch (e: BgmNetworkException) {
                        lastError = e
                        emptyList()
                    }
                if (sources.isNotEmpty()) return@asAppResult sources
            }
            emptyList()
        }

    private fun candidatePages(
        subjectId: Long,
        schedule: com.infinitezerone.minibgm.core.model.AirSchedule?,
    ): List<String> {
        val recorded =
            schedule
                ?.siteLinks
                .orEmpty()
                .filter { it.playUrl.isNotBlank() }
                .sortedBySitePriority()
                .map { it.playUrl }
        if (recorded.isNotEmpty()) return recorded.take(MAX_DEEP_PAGES)
        val title = schedule?.titleCn.orEmpty().ifBlank { schedule?.title.orEmpty() }
        if (title.isBlank()) return emptyList()
        return listOf(StreamingIntentResolver.buildBilibiliSearchTarget(title).webFallbackUrl)
    }
}
