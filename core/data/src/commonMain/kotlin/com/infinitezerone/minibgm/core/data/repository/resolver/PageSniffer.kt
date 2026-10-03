package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.common.ChineseConverter
import com.infinitezerone.minibgm.core.data.repository.FetchBudget
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService

/** scheme://host/ 形式的站点根，作为 Referer 与相对地址的基准 */
internal fun pageOrigin(url: String): String? {
    val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return null
    val scheme = url.substring(0, schemeEnd)
    val rest = url.substring(schemeEnd + 3)
    val host = rest.substringBefore('/').substringBefore('?')
    if (host.isBlank()) return null
    return "$scheme://$host/"
}

/** 把协议相对/根相对/相对路径补成绝对地址；无法补全时返回 null */
internal fun absoluteUrl(
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
internal suspend fun sniffPageOrSubPages(
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

internal suspend fun probeSampleEpisodeUrl(
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
