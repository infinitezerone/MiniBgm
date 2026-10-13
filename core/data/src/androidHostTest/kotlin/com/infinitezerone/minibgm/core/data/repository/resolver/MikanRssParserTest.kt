package com.infinitezerone.minibgm.core.data.repository.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MikanRssParserTest {
    @Test
    fun parse_emptyOrInvalidXml_returnsEmptyList() {
        assertTrue(MikanRssParser.parse("").isEmpty())
        assertTrue(MikanRssParser.parse("   ").isEmpty())
        assertTrue(MikanRssParser.parse("<rss><channel></channel></rss>").isEmpty())
        assertTrue(MikanRssParser.parse("not an xml").isEmpty())
    }

    @Test
    fun parse_validRssWithSingleEpisodeAndBatch_parsesCorrectly() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0">
              <channel>
                <title>Mikan Project - 月光下的异世界之旅 第二幕</title>
                <item>
                  <guid isPermaLink="false">[LoliHouse] 月光下的异世界之旅 第二幕 / Tsuki ga Michibiku Isekai Douchuu S2 - 25 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕][END]</guid>
                  <link>https://mikanani.me/Home/Episode/dae57fb34d0d74287706fd70cfd0bdfec4e540e0</link>
                  <title>[LoliHouse] 月光下的异世界之旅 第二幕 / Tsuki ga Michibiku Isekai Douchuu S2 - 25 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕][END]</title>
                  <description>[LoliHouse] 月光下的异世界之旅 第二幕 / Tsuki ga Michibiku Isekai Douchuu S2 - 25 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕][END][300.37 MB]</description>
                  <torrent xmlns="https://mikanani.me/0.1/">
                    <link>https://mikanani.me/Home/Episode/dae57fb34d0d74287706fd70cfd0bdfec4e540e0</link>
                    <contentLength>314960768</contentLength>
                    <pubDate>2024-06-25T09:13:26.038</pubDate>
                  </torrent>
                  <enclosure type="application/x-bittorrent" length="314960768" url="https://mikanani.me/Download/20240625/dae57fb34d0d74287706fd70cfd0bdfec4e540e0.torrent" />
                </item>
                <item>
                  <guid isPermaLink="false">[LoliHouse] 月光下的异世界之旅 第二幕 [01-25 合集][WebRip 1080p HEVC-10bit AAC][简繁内封字幕][Fin]</guid>
                  <link>https://mikanani.me/Home/Episode/d3f81ce0e260187ae6684a35a772a38cffa1b9e1</link>
                  <title>[LoliHouse] 月光下的异世界之旅 第二幕 [01-25 合集][WebRip 1080p HEVC-10bit AAC][简繁内封字幕][Fin]</title>
                  <description>[LoliHouse] 月光下的异世界之旅 第二幕 [01-25 合集][9.0 GB]</description>
                  <enclosure type="application/x-bittorrent" length="9663676416" url="https://mikanani.me/Download/20240629/d3f81ce0e260187ae6684a35a772a38cffa1b9e1.torrent" />
                </item>
                <item>
                  <guid isPermaLink="false">【极影字幕社】★04月新番 鬼灭之刃 柱训练篇 第01话 1080P 繁日双语 MP4</guid>
                  <link>https://mikanani.me/Home/Episode/1234567890abcdef1234567890abcdef12345678</link>
                  <title>【极影字幕社】★04月新番 鬼灭之刃 柱训练篇 第01话 1080P 繁日双语 MP4</title>
                  <description>【极影字幕社】★04月新番 鬼灭之刃 柱训练篇 第01话 1080P 繁日双语 MP4</description>
                  <enclosure type="application/x-bittorrent" length="482428832" url="https://mikanani.me/Download/20240618/1234567890abcdef1234567890abcdef12345678.torrent" />
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val results = MikanRssParser.parse(xml)
        assertEquals(3, results.size)

        // 第一条：单集 25 话
        val item1 = results[0]
        assertEquals("LoliHouse", item1.fansubName)
        assertEquals(25f, item1.episodeSort)
        assertEquals("1080P", item1.resolution)
        assertEquals("dae57fb34d0d74287706fd70cfd0bdfec4e540e0", item1.infoHash)
        assertTrue(item1.magnetUrl.startsWith("magnet:?xt=urn:btih:dae57fb34d0d74287706fd70cfd0bdfec4e540e0"))
        assertEquals("300.37 MB", item1.formattedSize)
        assertEquals("2024-06-25T09:13:26.038", item1.pubDate)

        // 第二条：01-25 合集 (无单集集数)
        val item2 = results[1]
        assertEquals("LoliHouse", item2.fansubName)
        assertNull(item2.episodeSort)
        assertEquals("1080P", item2.resolution)
        assertEquals("9.0 GB", item2.formattedSize)

        // 第三条：中文方头括号与第01话
        val item3 = results[2]
        assertEquals("极影字幕社", item3.fansubName)
        assertEquals(1f, item3.episodeSort)
        assertEquals("1080P", item3.resolution)
        assertEquals("1234567890abcdef1234567890abcdef12345678", item3.infoHash)
    }

    @Test
    fun parse_handlesXmlEscapingAndCdata() {
        val xml =
            """
            <rss><channel><item>
              <title><![CDATA[[ANi] &lt;测试番剧&gt; - 03 [1080P]]]></title>
              <link>https://mikanani.me/Home/Episode/aabbccddeeff00112233445566778899aabbccdd</link>
            </item></channel></rss>
            """.trimIndent()
        val results = MikanRssParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("[ANi] <测试番剧> - 03 [1080P]", results[0].title)
        assertEquals("ANi", results[0].fansubName)
        assertEquals(3f, results[0].episodeSort)
        assertEquals("1080P", results[0].resolution)
    }

    @Test
    fun parse_handlesSteelBallRunFansubsAndRevisionAndSxxExxFormats() {
        val xml =
            """
            <rss><channel>
              <item>
                <title>【悠哈璃羽字幕社】[JOJO的奇妙冒险 飙马野郎/JoJo's Bizarre Adventure: Steel Ball Run][03][1080p HEVC][CHS_JP][简日内嵌]</title>
                <link>https://mikanani.me/Home/Episode/16cf7ce36a40333d76025c4a76ddfda5f6174781</link>
              </item>
              <item>
                <title>[桜都字幕组] 飙马野郎 JOJO的奇妙冒险 / Steel Ball Run： JoJo no Kimyou na Bouken [04v2][1080P][简繁内封]</title>
                <link>https://mikanani.me/Home/Episode/0327ac7116411c81f53636b41710246754a503b5</link>
              </item>
              <item>
                <title>[WebRip] Steel Ball Run S01E02v2 2160p NF WEB-DL</title>
                <link>https://mikanani.me/Home/Episode/534d4d025d103e1dedd5de956733a3c540328273</link>
              </item>
            </channel></rss>
            """.trimIndent()
        val results = MikanRssParser.parse(xml)
        assertEquals(3, results.size)
        assertEquals("悠哈璃羽字幕社", results[0].fansubName)
        assertEquals(3f, results[0].episodeSort)
        assertEquals("桜都字幕组", results[1].fansubName)
        assertEquals(4f, results[1].episodeSort)
        assertEquals(2f, results[2].episodeSort)
        assertEquals("4K", results[2].resolution)
    }

    @Test
    fun extractBangumiIdsAndBgmSubjectId_parsesMikanHtmlAccurately() {
        val searchHtml =
            """
            <ul class="list-inline an-ul">
              <li><a href="/Home/Bangumi/3887" target="_blank"><div class="an-text">飙马野郎</div></a></li>
              <li><a href="/Home/Bangumi/3100" target="_blank"><div class="an-text">石之海</div></a></li>
            </ul>
            """.trimIndent()
        val ids = MikanRssParser.extractBangumiIdsFromSearchHtml(searchHtml)
        assertEquals(listOf("3887", "3100"), ids)

        val bangumiHtml =
            """
            <p class="bangumi-info">Bangumi番组计划链接：<br />
            <a class="w-other-c" target="_blank" href="https://bgm.tv/subject/551918">https://bgm.tv/subject/551918</a></p>
            """.trimIndent()
        assertEquals(551918L, MikanRssParser.extractBgmSubjectIdFromBangumiHtml(bangumiHtml))
    }
}
