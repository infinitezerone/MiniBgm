package com.infinitezerone.minibgm.core.data.repository.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BT RSS 解析器的纯逻辑回归测试。
 *
 * 重点覆盖 [TorrentRssParser.extractEpisodeSort] 的四条提取路径与两条排除路径：
 * 合集/季度包必须返回 null，否则单集选集会被大包顶掉。
 */
class TorrentRssParserTest {
    // ---- 排除路径：合集关键词 ----

    @Test
    fun `合集与全集关键词一律不解析出集数`() {
        assertNull(TorrentRssParser.extractEpisodeSort("[Lilith-Raws] 葬送的芙莉莲 合集 [1080p]"))
        assertNull(TorrentRssParser.extractEpisodeSort("【樱都字幕组】某番 全集 1080P"))
        assertNull(TorrentRssParser.extractEpisodeSort("[Nekomoe kissaten][Anime][Fin][1080p]"))
        assertNull(TorrentRssParser.extractEpisodeSort("Anime Fin) 1080p"))
    }

    // ---- 排除路径：集数区间 ----

    @Test
    fun `集数区间表达同样视为合集`() {
        assertNull(TorrentRssParser.extractEpisodeSort("Anime [01-12][1080p]"))
        assertNull(TorrentRssParser.extractEpisodeSort("Anime EP01-12 BDRip"))
        assertNull(TorrentRssParser.extractEpisodeSort("Anime 01-12 1080p"))
    }

    // ---- 提取路径 1：横杠分隔 ----

    @Test
    fun `横杠分隔的集数可被提取`() {
        assertEquals(25f, TorrentRssParser.extractEpisodeSort("Anime - 25 [1080p]"))
        assertEquals(1f, TorrentRssParser.extractEpisodeSort("Anime - 01 "))
    }

    @Test
    fun `横杠集数支持版本后缀与小数`() {
        assertEquals(9f, TorrentRssParser.extractEpisodeSort("Anime - 09v2"))
        assertEquals(6.5f, TorrentRssParser.extractEpisodeSort("Anime - 6.5 [1080p]"))
    }

    // ---- 提取路径 2：中文话集 ----

    @Test
    fun `中文话集表达可被提取`() {
        assertEquals(1f, TorrentRssParser.extractEpisodeSort("葬送的芙莉莲 第01话 结束与开始"))
        assertEquals(25f, TorrentRssParser.extractEpisodeSort("某番 第 25 集"))
        assertEquals(12.5f, TorrentRssParser.extractEpisodeSort("某番 第12.5話"))
    }

    // ---- 提取路径 3：SxxExx / EPxx ----

    @Test
    fun `SxxExx 与 EP 前缀可被提取`() {
        assertEquals(3f, TorrentRssParser.extractEpisodeSort("Anime S01E03 1080p"))
        assertEquals(4f, TorrentRssParser.extractEpisodeSort("Anime S2E04"))
        assertEquals(25f, TorrentRssParser.extractEpisodeSort("Anime EP25"))
        assertEquals(8f, TorrentRssParser.extractEpisodeSort("Anime ep 08"))
        assertEquals(3f, TorrentRssParser.extractEpisodeSort("Anime E03"))
    }

    // ---- 提取路径 4：方括号数字，含分辨率排除 ----

    @Test
    fun `方括号数字可被提取`() {
        assertEquals(25f, TorrentRssParser.extractEpisodeSort("Anime [25]"))
        assertEquals(9f, TorrentRssParser.extractEpisodeSort("Anime 【09】"))
        assertEquals(3f, TorrentRssParser.extractEpisodeSort("Anime [03v2]"))
    }

    @Test
    fun `分辨率与编码数字不会被误判成集数`() {
        assertNull(TorrentRssParser.extractEpisodeSort("Anime [720]"))
        assertNull(TorrentRssParser.extractEpisodeSort("Anime 1080p [264]"))
    }

    @Test
    fun `跳过分辨率后仍能取到后面的真实集数`() {
        assertEquals(12f, TorrentRssParser.extractEpisodeSort("Anime [720] [12]"))
    }

    // ---- 无匹配 ----

    @Test
    fun `无法识别的标题返回 null`() {
        assertNull(TorrentRssParser.extractEpisodeSort("Anime Movie 剧场版"))
        assertNull(TorrentRssParser.extractEpisodeSort(""))
    }

    // ---- 整体解析 ----

    @Test
    fun `parse 解析标准 BT RSS 并提取磁力链与元数据`() {
        val xml =
            """
            <rss><channel>
            <item>
              <title>[Lilith-Raws] 葬送的芙莉莲 - 25 [1080p][Baha][WEB-DL][AAC AVC][CHT].mp4</title>
              <link>https://example.com/Download/20240101/0123456789abcdef0123456789abcdef01234567.torrent</link>
              <description>[5.2 GB]</description>
              <pubDate>Mon, 01 Jan 2024 00:00:00 +0800</pubDate>
            </item>
            </channel></rss>
            """.trimIndent()

        val result = TorrentRssParser.parse(xml)

        assertEquals(1, result.size)
        val item = result.first()
        assertEquals("Lilith-Raws", item.fansubName)
        assertEquals(25f, item.episodeSort)
        assertEquals("1080P", item.resolution)
        assertEquals("0123456789abcdef0123456789abcdef01234567", item.infoHash)
        assertEquals(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            item.magnetUrl,
        )
        assertEquals("https://example.com/Download/20240101/0123456789abcdef0123456789abcdef01234567.torrent", item.torrentUrl)
        assertEquals("5.2 GB", item.formattedSize)
    }

    @Test
    fun `parse 合成磁链只含 infohash 严禁注入公共 Tracker`() {
        val xml =
            """
            <item>
              <title>Anime - 01 [1080p]</title>
              <link>https://example.com/0123456789abcdef0123456789abcdef01234567</link>
            </item>
            """.trimIndent()

        val magnet = TorrentRssParser.parse(xml).first().magnetUrl

        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", magnet)
        assertFalse(magnet.contains("&tr="), "合成磁链严禁注入硬编码公共 Tracker：$magnet")
        assertFalse(magnet.contains("announce"), "合成磁链严禁携带公共 Tracker 端点：$magnet")
    }

    @Test
    fun `parse 对空与残缺输入 fail-open 返回空列表`() {
        assertTrue(TorrentRssParser.parse("").isEmpty())
        assertTrue(TorrentRssParser.parse("   ").isEmpty())
        assertTrue(TorrentRssParser.parse("<rss></rss>").isEmpty())
        assertTrue(TorrentRssParser.parse("<item><description>无标题</description></item>").isEmpty())
    }

    @Test
    fun `parse 对无 hash 条目回退到 fallbackHost 拼种子直链`() {
        val xml =
            """
            <item>
              <title>Anime - 01 [1080p]</title>
              <link>https://example.com/0123456789abcdef0123456789abcdef01234567</link>
            </item>
            """.trimIndent()

        val result = TorrentRssParser.parse(xml, fallbackHost = "fallback.example")

        assertEquals(1, result.size)
        assertEquals(
            "https://fallback.example/Download/0123456789abcdef0123456789abcdef01234567.torrent",
            result.first().torrentUrl,
        )
    }
}
