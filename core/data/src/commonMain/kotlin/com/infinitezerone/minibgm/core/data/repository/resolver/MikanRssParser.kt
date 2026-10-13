package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.MikanTorrentResource

/**
 * 蜜柑计划（Mikan Project）公开 RSS XML 解析适配器。
 * 核心解析逻辑已统一收敛至通用的 [TorrentRssParser]。
 */
object MikanRssParser {
    private val MIKAN_BANGUMI_ID_REGEX = Regex("""href=["']/Home/Bangumi/(\d+)["']""", RegexOption.IGNORE_CASE)
    private val BGM_SUBJECT_ID_REGEX = Regex("""https?://(?:bgm\.tv|bangumi\.tv|chii\.in)/subject/(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * 从 Mikan `/Home/Search` 搜索结果页 HTML 中提取候选的 Mikan 番剧词条 ID 列表（如 `["3887"]`）。
     */
    fun extractBangumiIdsFromSearchHtml(html: String): List<String> =
        MIKAN_BANGUMI_ID_REGEX
            .findAll(html)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    /**
     * 从 Mikan `/Home/Bangumi/{id}` 番剧详情页 HTML 中提取其绑定的 Bangumi Subject ID（如 `551918L`）。
     */
    fun extractBgmSubjectIdFromBangumiHtml(html: String): Long? =
        BGM_SUBJECT_ID_REGEX
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    /**
     * 解析 Mikan RSS XML 文本为 [MikanTorrentResource] 列表。
     */
    fun parse(xmlContent: String): List<MikanTorrentResource> =
        TorrentRssParser.parse(xmlContent, fallbackHost = "mikanani.me")
}
