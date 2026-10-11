package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.repository.resolver.MikanRssParser
import com.infinitezerone.minibgm.core.model.MikanTorrentResource
import com.infinitezerone.minibgm.core.network.PageFetchService

interface MikanRepository {
    /**
     * 根据关键字（番剧名）与可选的蜜柑 ID 或集数检索原画字幕组 BT 种子与磁力资源。
     *
     * 遵循 Fail-Open 架构原则：
     * 网络超时、格式异常等故障返回空列表，绝不向 UI 层上抛未捕获异常。
     * 具备内存级轻量缓存，避免同一番剧短时间内重复发起抓取。
     * 支持日文原名、别名备选多重回退重试，保障字幕组资源检索命中率。
     */
    suspend fun getTorrentResources(
        title: String,
        mikanId: String? = null,
        episodeSort: Float? = null,
        alternativeTitles: List<String> = emptyList(),
    ): List<MikanTorrentResource>
}

class MikanRepositoryImpl(
    private val pageFetchService: PageFetchService,
) : MikanRepository {
    private val logger = bgmLogger("Bgm/Mikan")
    private val memoryCache = mutableMapOf<String, List<MikanTorrentResource>>()

    override suspend fun getTorrentResources(
        title: String,
        mikanId: String?,
        episodeSort: Float?,
        alternativeTitles: List<String>,
    ): List<MikanTorrentResource> {
        val cleanTitle = title.trim()
        val cleanMikanId = mikanId?.trim().orEmpty()
        val cleanAltTitles =
            alternativeTitles
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(cleanTitle, ignoreCase = true) }
                .distinct()

        if (cleanTitle.isBlank() && cleanMikanId.isBlank() && cleanAltTitles.isEmpty()) {
            return emptyList()
        }

        logger.d {
            "检索蜜柑原画资源: title='$cleanTitle', mikanId='$cleanMikanId', ep=$episodeSort, altCount=${cleanAltTitles.size}"
        }

        val cacheKey = "${cleanMikanId}_${cleanTitle.lowercase()}"
        val allResources =
            memoryCache[cacheKey] ?: run {
                val fetched = fetchTorrents(cleanTitle, cleanMikanId.takeIf { it.isNotBlank() }, cleanAltTitles)
                if (fetched.isNotEmpty()) {
                    memoryCache[cacheKey] = fetched
                }
                fetched
            }

        if (episodeSort == null) {
            logger.d { "条目级 Mikan 资源检索完成: 返回全量 ${allResources.size} 条资源" }
            return allResources
        }

        // 按集数过滤/加权：匹配当前集数的排前面，全季合集紧随其后，其余排除
        val targetEp = episodeSort
        val matched = allResources.filter { it.episodeSort == targetEp }
        val batches = allResources.filter { it.episodeSort == null }
        val filtered = matched + batches
        logger.d {
            "分集级 Mikan 资源检索完成: ep=$targetEp, 命中单集=${matched.size}, 合集=${batches.size}, 合计=${filtered.size} / 全量=${allResources.size}"
        }
        return filtered
    }

    private suspend fun fetchTorrents(
        title: String,
        mikanId: String?,
        alternativeTitles: List<String>,
    ): List<MikanTorrentResource> {
        // 策略 1：精准 Mikan ID 直达 RSS
        if (!mikanId.isNullOrBlank()) {
            val rssUrl = "https://mikanani.me/RSS/Bangumi?bangumiId=$mikanId"
            logger.d { "策略 1: 尝试精准 Mikan ID 直达 RSS: $rssUrl" }
            val parsed = fetchAndParseRss(rssUrl)
            if (parsed.isNotEmpty()) {
                logger.i { "策略 1 命中: Mikan ID=$mikanId 获取到 ${parsed.size} 条资源" }
                return parsed
            }
        }

        // 策略 2：番剧全名搜索 RSS
        if (title.isNotBlank()) {
            val searchUrl = "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(title)}"
            logger.d { "策略 2: 尝试主标题搜索 RSS: '$title' -> $searchUrl" }
            val parsed = fetchAndParseRss(searchUrl)
            if (parsed.isNotEmpty()) {
                logger.i { "策略 2 命中: 主标题 '$title' 获取到 ${parsed.size} 条资源" }
                return parsed
            }

            // 策略 3：去除季数后缀后简短搜索（例如 "月光下的异世界之旅 第二季" -> "月光下的异世界之旅"）
            val simplifiedTitle = simplifyTitle(title)
            if (simplifiedTitle != title && simplifiedTitle.isNotBlank()) {
                val fallbackUrl =
                    "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(simplifiedTitle)}"
                logger.d { "策略 3: 尝试简化主标题搜索 RSS: '$simplifiedTitle' -> $fallbackUrl" }
                val fallbackParsed = fetchAndParseRss(fallbackUrl)
                if (fallbackParsed.isNotEmpty()) {
                    logger.i { "策略 3 命中: 简化主标题 '$simplifiedTitle' 获取到 ${fallbackParsed.size} 条资源" }
                    return fallbackParsed
                }
            }
        }

        // 策略 4：备选标题与原名（日文原名 / 别名 / 英文名）回退搜索
        for (alt in alternativeTitles) {
            val altUrl = "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(alt)}"
            logger.d { "策略 4: 尝试备选标题搜索 RSS: '$alt' -> $altUrl" }
            val altParsed = fetchAndParseRss(altUrl)
            if (altParsed.isNotEmpty()) {
                logger.i { "策略 4 命中: 备选标题 '$alt' 获取到 ${altParsed.size} 条资源" }
                return altParsed
            }

            val simplifiedAlt = simplifyTitle(alt)
            if (simplifiedAlt != alt && simplifiedAlt.isNotBlank()) {
                val simplifiedAltUrl =
                    "https://mikanani.me/RSS/Search?searchstr=${StreamingIntentResolver.encodeQueryParameter(simplifiedAlt)}"
                logger.d { "策略 4.1: 尝试简化备选标题搜索 RSS: '$simplifiedAlt' -> $simplifiedAltUrl" }
                val simplifiedParsed = fetchAndParseRss(simplifiedAltUrl)
                if (simplifiedParsed.isNotEmpty()) {
                    logger.i { "策略 4.1 命中: 简化备选标题 '$simplifiedAlt' 获取到 ${simplifiedParsed.size} 条资源" }
                    return simplifiedParsed
                }
            }
        }

        logger.w { "蜜柑资源检索结束，所有策略均未命中任何资源 (title='$title', id='$mikanId', altCount=${alternativeTitles.size})" }
        return emptyList()
    }

    private suspend fun fetchAndParseRss(url: String): List<MikanTorrentResource> {
        val page = pageFetchService.fetchHtml(url)
        if (page == null || page.html.isBlank()) {
            logger.w { "抓取 Mikan RSS 返回空或失败: $url" }
            return emptyList()
        }
        val parsed = MikanRssParser.parse(page.html)
        if (parsed.isEmpty()) {
            logger.d { "Mikan RSS 内容解析结果为空: $url (htmlLen=${page.html.length})" }
        }
        return parsed
    }

    private fun simplifyTitle(title: String): String =
        title
            .replace(Regex("""第[一二三四五六七八九十0-9]+[季期幕]"""), "")
            .replace(Regex("""(?i)\b(?:season|s)\s*[0-9]{1,2}\b"""), "")
            .replace(Regex("""(?i)\bpart\s*[0-9]{1,2}\b"""), "")
            .replace(Regex("""[\s_\-][2-9](?:\s|$)"""), "")
            .trim()
}
