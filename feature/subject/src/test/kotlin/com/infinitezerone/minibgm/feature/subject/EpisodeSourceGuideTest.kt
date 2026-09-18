package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSourceGuideTest {
    private val sampleSubject =
        Subject(
            id = 412435L,
            name = "葬送のフリーレン",
            nameCn = "葬送的芙莉莲",
        )

    @Test
    fun bilibiliKeyword_forMainEpisode_formatsTitleAndEpisodeCorrectly() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f, name = "死者の眠る里", nameCn = "死亡与安宁")
        val num = if (ep5.ep > 0f) ep5.ep else ep5.sort
        val epLabel = "第 ${num.toEpisodeLabel()} 话"
        val keyword = "${sampleSubject.displayName} $epLabel"

        assertEquals("葬送的芙莉莲 第 5 话", keyword)

        val target = StreamingIntentResolver.buildBilibiliSearchTarget(keyword)
        assertTrue(target.isSearch)
        assertEquals("哔哩哔哩", target.appName)
        assertEquals("bilibili", target.siteName)
        assertEquals(4, target.packageNames.size)
        assertTrue(target.deepLinkUri?.startsWith("bilibili://search?keyword=") == true)
        assertTrue(target.webFallbackUrl.startsWith("https://search.bilibili.com/all?keyword="))
    }

    @Test
    fun bilibiliKeyword_forSpEpisode_formatsGroupLabelAndSort() {
        val sp1 = Episode(id = 2001L, type = 1, ep = 0f, sort = 1f, name = "●●の魔法")
        val group = EpisodeGroup.fromType(sp1.type)
        val epLabel = "${group.label} ${sp1.sort.toInt()}"
        val keyword = "${sampleSubject.displayName} $epLabel"

        assertEquals("葬送的芙莉莲 特别篇 1", keyword)

        val target = StreamingIntentResolver.buildBilibiliSearchTarget(keyword)
        assertTrue(target.deepLinkUri?.contains("bilibili://search") == true)
    }

    @Test
    fun mikanUrl_withPreciseMikanId_navigatesDirectlyToBangumiPage() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f)
        val num = if (ep5.ep > 0f) ep5.ep else ep5.sort
        val keyword = "${sampleSubject.displayName} ${num.toEpisodeLabel()}".trim()

        val url = StreamingIntentResolver.buildMikanUrl(mikanId = "3233", keyword = keyword)
        assertEquals("https://mikanani.me/Home/Bangumi/3233", url)
    }

    @Test
    fun mikanUrl_withoutMikanId_fallsBackToEpisodeKeywordSearch() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f)
        val num = if (ep5.ep > 0f) ep5.ep else ep5.sort
        val keyword = "${sampleSubject.displayName} ${num.toEpisodeLabel()}".trim()

        val url = StreamingIntentResolver.buildMikanUrl(mikanId = null, keyword = keyword)
        assertTrue(url.startsWith("https://mikanani.me/Home/Search?searchstr="))
        // 确保包含 UTF-8 百分号编码后的关键词
        val encodedPart = url.substringAfter("searchstr=")
        assertTrue(encodedPart.isNotEmpty())
        assertTrue(encodedPart.contains("%"))
    }

    @Test
    fun fractionalEpisodeLabel_formatsCorrectlyInKeywords() {
        val epRecap = Episode(id = 1006L, type = 0, ep = 6.5f, sort = 6.5f)
        val num = if (epRecap.ep > 0f) epRecap.ep else epRecap.sort
        val epLabel = "第 ${num.toEpisodeLabel()} 话"

        assertEquals("第 6.5 话", epLabel)
        assertEquals("葬送的芙莉莲 第 6.5 话", "${sampleSubject.displayName} $epLabel")
    }

    @Test
    fun formatEpisodeGuideHeader_withMainEpisodeAndChineseTitle_formatsSubjectEpAndTitle() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f, name = "死者の眠る里", nameCn = "死亡与安宁")
        val header =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, ep5)
        assertEquals("《葬送的芙莉莲》 第 5 话 · 死亡与安宁", header)
    }

    @Test
    fun formatEpisodeGuideHeader_withoutEpisodeTitle_formatsSubjectAndEpisodeOnly() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f)
        val header =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, ep5)
        assertEquals("《葬送的芙莉莲》 第 5 话", header)
    }

    @Test
    fun formatEpisodeGuideHeader_withSpEpisode_formatsGroupLabelSortAndTitle() {
        val sp1 = Episode(id = 2001L, type = 1, ep = 0f, sort = 1f, name = "●●の魔法")
        val header =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, sp1)
        assertEquals("《葬送的芙莉莲》 特别篇 1 · ●●の魔法", header)
    }

    @Test
    fun formatEpisodeGuideHeader_withFractionalEpisode_formatsFractionalSort() {
        val epRecap = Episode(id = 1006L, type = 0, ep = 6.5f, sort = 6.5f, name = "总集篇")
        val header =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, epRecap)
        assertEquals("《葬送的芙莉莲》 第 6.5 话 · 总集篇", header)
    }

    @Test
    fun formatEpisodeGuideHeader_withRedundantEpisodeTitle_avoidsDuplicateLabel() {
        // 当单集名称仅为 "第5话"、"5" 或 "第5集" 时，避免拼接出 "第 5 话 · 第5话" 的重复标题
        val ep5WithDuplicateName = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f, name = "第5话", nameCn = "")
        val header1 =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, ep5WithDuplicateName)
        assertEquals("《葬送的芙莉莲》 第 5 话", header1)

        val ep5WithNumberOnly = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f, name = "5", nameCn = "")
        val header2 =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader(sampleSubject.displayName, ep5WithNumberOnly)
        assertEquals("《葬送的芙莉莲》 第 5 话", header2)
    }

    @Test
    fun formatEpisodeGuideHeader_withBlankDisplayName_formatsEpisodeWithoutEmptyBrackets() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f, name = "死者の眠る里", nameCn = "死亡与安宁")
        val header =
            com.infinitezerone.minibgm.feature.subject.components
                .formatEpisodeGuideHeader("", ep5)
        assertEquals("第 5 话 · 死亡与安宁", header)
    }

    @Test
    fun mikanKeyword_forMainEpisode_formatsTitleAndNumberConsistently() {
        val ep5 = Episode(id = 1005L, type = 0, ep = 5f, sort = 5f)
        val num = if (ep5.ep > 0f) ep5.ep else ep5.sort
        val keyword = "${sampleSubject.displayName} ${num.toEpisodeLabel()}".trim()
        assertEquals("葬送的芙莉莲 5", keyword)

        val url = StreamingIntentResolver.buildMikanUrl(mikanId = null, keyword = keyword)
        val expectedEncoded = StreamingIntentResolver.encodeQueryParameter("葬送的芙莉莲 5")
        assertTrue(url.contains(expectedEncoded))
    }

    @Test
    fun isLikelyMediaStream_detectsDirectVideoFormats() {
        assertTrue(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://example.com/anime/ep1.m3u8",
            ),
        )
        assertTrue(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://cdn.test.org/stream/video.mp4?token=abc123xyz",
            ),
        )
        assertTrue(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://cdn.test.org/stream/video.flv",
            ),
        )
        assertTrue(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://api.stream.com/v1/play?sign=123",
                description = "4K 直链解析",
            ),
        )
    }

    @Test
    fun isLikelyMediaStream_rejectsStandardWebPages() {
        org.junit.Assert.assertFalse(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://search.bilibili.com/all?keyword=test",
            ),
        )
        org.junit.Assert.assertFalse(
            com.infinitezerone.minibgm.feature.subject.components.isLikelyMediaStream(
                "https://mikanani.me/Home/Search?searchstr=frieren",
            ),
        )
    }
}
