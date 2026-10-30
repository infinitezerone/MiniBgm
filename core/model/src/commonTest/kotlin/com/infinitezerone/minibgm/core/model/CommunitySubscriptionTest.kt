package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommunitySubscriptionTest {
    private fun assertSupported(site: TvBoxSite): DiscoveredSource {
        val conversion = site.convert()
        assertIs<TvBoxSiteConversion.Supported>(conversion)
        return conversion.source
    }

    @Test
    fun convert_declaresMacCmsSiteAsSourceRuleWithMacCmsParser() {
        val site =
            TvBoxSite(
                name = "测试采集站",
                type = 1,
                api = "https://example.com/api.php/provide/vod",
            )
        val source = assertSupported(site)
        assertEquals("测试采集站", source.name)
        assertEquals("https://example.com/api.php/provide/vod?ac=detail&wd={title}", source.urlTemplate)
        assertEquals("TVBox MacCMS 采集源", source.description)
        // 接口端点必须是取源规则：标成 PAGE 会让播放时丢掉 MACCMS 解析器、静默降级成嗅探
        assertEquals(PlaybackRuleKind.SOURCE, source.kind)
        assertEquals(RuleParserType.MACCMS, source.parserType)
    }

    @Test
    fun convert_declaresMacCmsCompatSiteAsSourceRuleWithMacCmsParser() {
        val site =
            TvBoxSite(
                name = "兼容采集站",
                type = 2,
                api = "https://example.com/api.php/provide/vod",
            )
        val source = assertSupported(site)
        assertEquals("兼容采集站", source.name)
        assertEquals("https://example.com/api.php/provide/vod?ac=detail&wd={title}", source.urlTemplate)
        assertEquals("TVBox MacCMS 采集源", source.description)
        assertEquals(PlaybackRuleKind.SOURCE, source.kind)
        assertEquals(RuleParserType.MACCMS, source.parserType)

        val siteWithAc =
            TvBoxSite(
                name = "带ac兼容站",
                type = 2,
                api = "https://example.com/api.php/provide/vod?ac=detail",
            )
        val sourceWithAc = assertSupported(siteWithAc)
        assertEquals("https://example.com/api.php/provide/vod?ac=detail&wd={title}", sourceWithAc.urlTemplate)
    }

    @Test
    fun convert_keepsUnknownSiteOnAutoParserButStillSource() {
        val site =
            TvBoxSite(
                name = "未知形态源",
                type = 4,
                api = "https://example.com/api",
            )
        val source = assertSupported(site)
        assertEquals(PlaybackRuleKind.SOURCE, source.kind)
        assertEquals(RuleParserType.AUTO, source.parserType)
    }

    @Test
    fun convert_convertsSiteWithWdPlaceholder() {
        val site =
            TvBoxSite(
                key = "test_key",
                api = "https://example.com/search?k={wd}",
            )
        val source = assertSupported(site)
        assertEquals("test_key", source.name)
        assertEquals("https://example.com/search?k={title}", source.urlTemplate)
    }

    @Test
    fun convert_supportsJsSpiderSitesWithSpiderParser() {
        val jsSite =
            TvBoxSite(
                name = "JS爬虫源",
                type = 3,
                api = "https://example.com/js/spider.js",
            )
        val source = assertSupported(jsSite)
        assertEquals(RuleParserType.SPIDER, source.parserType)
        assertEquals(PlaybackRuleKind.SOURCE, source.kind)
        assertEquals("https://example.com/js/spider.js", source.urlTemplate)

        val cspSite =
            TvBoxSite(
                name = "CSP扩展源",
                type = 3,
                api = "csp_Age",
                ext = JsonPrimitive("https://example.com/age.js"),
            )
        val cspSource = assertSupported(cspSite)
        assertEquals(RuleParserType.SPIDER, cspSource.parserType)
        assertEquals("https://example.com/age.js", cspSource.urlTemplate)
        kotlin.test.assertNull(cspSource.ext)

        // jar 仍然不支持
        val jarSite =
            TvBoxSite(
                name = "JAR源",
                type = 3,
                api = "https://example.com/spider.jar",
            )
        assertIs<TvBoxSiteConversion.Unsupported>(jarSite.convert())

        val cspSchemeWithoutScript = TvBoxSite(name = "私有爬虫协议无扩展", api = "csp_Custom")
        assertIs<TvBoxSiteConversion.Malformed>(cspSchemeWithoutScript.convert())
    }

    @Test
    fun convert_preservesSpiderExtAcrossRuleConversion() {
        val ext = Json.parseToJsonElement("""{"token":"abc","base":"https://api.example.com"}""")
        val site =
            TvBoxSite(
                name = "带扩展参数的爬虫",
                type = 3,
                api = "https://example.com/spider.js",
                ext = ext,
            )

        val source = assertSupported(site)
        assertEquals(ext, source.ext)
        assertEquals(ext, source.toPlaybackSourceRule("rule_ext").ext)

        val parsed =
            Json.decodeFromString<TvBoxConfig>(
                """{"sites":[{"name":"对象扩展","type":3,"api":"https://example.com/spider.js","ext":{"token":"abc"}}]}""",
            )
        val parsedExt = assertSupported(parsed.sites.single()).ext as JsonObject
        assertEquals(JsonPrimitive("abc"), parsedExt["token"])
    }

    @Test
    fun convert_convertsQueryParamUrl() {
        val site =
            TvBoxSite(
                name = "查询源",
                api = "https://example.com/s?cat=anime",
            )
        val source = assertSupported(site)
        assertEquals("https://example.com/s?cat=anime&wd={title}", source.urlTemplate)
    }

    @Test
    fun convert_convertsDefaultPathUrl() {
        val site =
            TvBoxSite(
                name = "普通源",
                api = "https://example.com",
            )
        val source = assertSupported(site)
        assertEquals("https://example.com/search?query={title}", source.urlTemplate)
    }

    @Test
    fun convert_reportsMalformedWhenUrlOrNameMissing() {
        val noUrl = TvBoxSite(name = "无链接")
        assertIs<TvBoxSiteConversion.Malformed>(noUrl.convert())

        val blankName = TvBoxSite(api = "https://example.com")
        assertIs<TvBoxSiteConversion.Malformed>(blankName.convert())
    }

    @Test
    fun discoveredSource_convertsToPlaybackSourceRulePreservingKind() {
        val source =
            DiscoveredSource(
                name = "测试源",
                urlTemplate = "https://test.com/s?q={title}",
                description = "测试描述",
                kind = PlaybackRuleKind.SOURCE,
                parserType = RuleParserType.MACCMS,
            )
        val rule = source.toPlaybackSourceRule("rule_1")
        assertEquals("rule_1", rule.id)
        assertEquals("测试源", rule.name)
        assertEquals("https://test.com/s?q={title}", rule.urlTemplate)
        assertTrue(rule.isEnabled)
        assertEquals(PlaybackRuleKind.SOURCE, rule.kind)
        assertEquals(RuleParserType.MACCMS, rule.parserType)
        assertTrue(rule.isResolvable)
    }

    @Test
    fun discoveredSource_defaultsToPageRuleForPlainSearchTemplates() {
        val source =
            DiscoveredSource(
                name = "网页搜索模板",
                urlTemplate = "https://test.com/search?q={title}",
            )
        val rule = source.toPlaybackSourceRule("rule_2")
        assertEquals(PlaybackRuleKind.PAGE, rule.kind)
        assertEquals(RuleParserType.AUTO, rule.parserType)
    }

    @Test
    fun extractSubscriptionCandidate_extractsJsonAndTvBoxUrlsFromMixedText() {
        val mixed1 = "分享一个新规则订阅：https://example.com/tvbox/rules.json，大家快试试"
        assertEquals("https://example.com/tvbox/rules.json", extractSubscriptionCandidate(mixed1))

        val mixed2 = "采集接口在此: http://vod.example.org/api.php/provide/vod/。"
        assertEquals("http://vod.example.org/api.php/provide/vod/", extractSubscriptionCandidate(mixed2))

        val plainUrl = "https://raw.githubusercontent.com/user/repo/main/sub.json"
        assertEquals(plainUrl, extractSubscriptionCandidate(plainUrl))
    }

    @Test
    fun extractSubscriptionCandidate_ignoresBangumiAndBilibiliDomains() {
        val bgmUrl = "【Bangumi条目】https://bgm.tv/subject/364450 推荐观看"
        kotlin.test.assertNull(extractSubscriptionCandidate(bgmUrl))

        val biliUrl = "https://www.bilibili.com/video/BV1xx411c7mD"
        kotlin.test.assertNull(extractSubscriptionCandidate(biliUrl))
    }

    @Test
    fun extractSubscriptionCandidate_ignoresPlainTextWithoutUrl() {
        kotlin.test.assertNull(extractSubscriptionCandidate("这是一段完全不含网络链接的纯文本"))
        kotlin.test.assertNull(extractSubscriptionCandidate("   "))
    }

    @Test
    fun extractSubscriptionCandidate_acceptsSingleStandaloneUrl() {
        val standalone = "https://api.my-custom-parser.org/anime/search"
        assertEquals(standalone, extractSubscriptionCandidate(standalone))
    }

    @Test
    fun stableRuleId_isDeterministicPerSourceName() {
        val first = DiscoveredSource(name = "量子资源", urlTemplate = "https://a.example.tv/{title}")
        val second = DiscoveredSource(name = "量子资源", urlTemplate = "https://a.example.tv/{title}")
        val renamed = DiscoveredSource(name = "量子资源 v2", urlTemplate = "https://a.example.tv/{title}")

        // 同一来源反复导入必须落到同一个 id 上，否则规则列表会随每次导入增殖
        assertEquals(first.stableRuleId(), second.stableRuleId())
        assertTrue(first.stableRuleId().startsWith("sub_"))
        kotlin.test.assertNotEquals(first.stableRuleId(), renamed.stableRuleId())
    }
}
