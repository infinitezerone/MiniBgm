package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunitySubscriptionTest {
    @Test
    fun toDiscoveredSource_convertsMacCmsSite() {
        val site =
            TvBoxSite(
                name = "测试采集站",
                type = 1,
                api = "https://example.com/api.php/provide/vod",
            )
        val source = site.toDiscoveredSource()
        assertNotNull(source)
        assertEquals("测试采集站", source.name)
        assertEquals("https://example.com/api.php/provide/vod?ac=detail&wd={title}", source.urlTemplate)
        assertEquals("TVBox MacCMS 采集源", source.description)
    }

    @Test
    fun toDiscoveredSource_convertsSiteWithWdPlaceholder() {
        val site =
            TvBoxSite(
                key = "test_key",
                api = "https://example.com/search?k={wd}",
            )
        val source = site.toDiscoveredSource()
        assertNotNull(source)
        assertEquals("test_key", source.name)
        assertEquals("https://example.com/search?k={title}", source.urlTemplate)
    }

    @Test
    fun toDiscoveredSource_handlesExtFallbackAndType3() {
        val site =
            TvBoxSite(
                name = "爬虫源",
                type = 3,
                ext = "https://example.com/spider?query={title}",
            )
        val source = site.toDiscoveredSource()
        assertNotNull(source)
        assertEquals("爬虫源", source.name)
        assertEquals("https://example.com/spider?query={title}", source.urlTemplate)
        assertEquals("TVBox 爬虫/扩展源", source.description)
    }

    @Test
    fun toDiscoveredSource_handlesQueryParamUrl() {
        val site =
            TvBoxSite(
                name = "查询源",
                api = "https://example.com/s?cat=anime",
            )
        val source = site.toDiscoveredSource()
        assertNotNull(source)
        assertEquals("https://example.com/s?cat=anime&wd={title}", source.urlTemplate)
    }

    @Test
    fun toDiscoveredSource_handlesDefaultPathUrl() {
        val site =
            TvBoxSite(
                name = "普通源",
                api = "https://example.com",
            )
        val source = site.toDiscoveredSource()
        assertNotNull(source)
        assertEquals("https://example.com/search?query={title}", source.urlTemplate)
    }

    @Test
    fun toDiscoveredSource_returnsNullForInvalidUrlOrBlankName() {
        val noUrl = TvBoxSite(name = "无链接")
        assertNull(noUrl.toDiscoveredSource())

        val invalidScheme = TvBoxSite(name = "非HTTP", api = "csp_Custom")
        assertNull(invalidScheme.toDiscoveredSource())

        val blankName = TvBoxSite(api = "https://example.com")
        assertNull(blankName.toDiscoveredSource())
    }

    @Test
    fun discoveredSource_convertsToPlaybackSourceRule() {
        val source =
            DiscoveredSource(
                name = "测试源",
                urlTemplate = "https://test.com/s?q={title}",
                description = "测试描述",
            )
        val rule = source.toPlaybackSourceRule("rule_1")
        assertEquals("rule_1", rule.id)
        assertEquals("测试源", rule.name)
        assertEquals("https://test.com/s?q={title}", rule.urlTemplate)
        assertTrue(rule.isEnabled)
    }
}
