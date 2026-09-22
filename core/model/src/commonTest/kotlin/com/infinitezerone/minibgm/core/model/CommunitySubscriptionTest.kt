package com.infinitezerone.minibgm.core.model

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
    fun convert_reportsSpiderSitesAsUnsupported() {
        // type 3 需要 jar/js 爬虫实现，本应用没有执行路径
        val spider =
            TvBoxSite(
                name = "爬虫源",
                type = 3,
                ext = "https://example.com/spider?query={title}",
            )
        assertIs<TvBoxSiteConversion.Unsupported>(spider.convert())

        // 端点指向脚本资源（TVBox 扩展源的常见形态）同样是废规则
        val scriptEndpoint =
            TvBoxSite(
                name = "脚本源",
                type = 3,
                api = "https://example.com/js/spider.js",
            )
        assertIs<TvBoxSiteConversion.Unsupported>(scriptEndpoint.convert())

        val jsWithoutSpiderType =
            TvBoxSite(
                name = "以脚本充当接口",
                api = "https://example.com/spider.min.js",
            )
        assertIs<TvBoxSiteConversion.Unsupported>(jsWithoutSpiderType.convert())

        val cspScheme = TvBoxSite(name = "私有爬虫协议", api = "csp_Custom")
        assertIs<TvBoxSiteConversion.Malformed>(cspScheme.convert())
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
}
