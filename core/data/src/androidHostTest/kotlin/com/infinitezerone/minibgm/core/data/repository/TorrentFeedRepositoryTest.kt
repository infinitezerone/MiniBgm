package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentFeedRepositoryTest {

    private val sampleRssXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0">
          <channel>
            <title>Sample Torrent Feed</title>
            <item>
              <title>[ANi] 葬送的芙莉莲 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4</title>
              <link>magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567</link>
              <enclosure url="https://feed.example.com/download/0123456789abcdef0123456789abcdef01234567.torrent" length="350000000" type="application/x-bittorrent"/>
              <description>[333.78 MB]</description>
              <pubDate>2023-10-06T15:00:00</pubDate>
            </item>
            <item>
              <title>[LoliHouse] 葬送的芙莉莲 - 04 [1080P][WebRip].mp4</title>
              <link>https://feed.example.com/episode/abcdef0123456789abcdef0123456789abcdef01</link>
              <enclosure url="https://feed.example.com/download/abcdef0123456789abcdef0123456789abcdef01.torrent" length="450000000" type="application/x-bittorrent"/>
              <description>[429.15 MB]</description>
              <pubDate>2023-10-13T15:00:00</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private class FakeFeedFetchService(
        private val htmlToReturn: String? = null,
    ) : PageFetchService {
        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = htmlToReturn?.let { FetchedPage(url = url, html = it) }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null
    }

    @Test
    fun getTorrentResources_parsesAndFiltersByEpisode() = runTest {
        val fake = FakeFeedFetchService(sampleRssXml)
        val repository = TorrentFeedRepositoryImpl(fake)
        val rule = PlaybackSourceRule(
            id = "test-torrent-rule",
            name = "Test Torrent Feed",
            urlTemplate = "https://feed.example.com/rss?keyword={title}",
            kind = PlaybackRuleKind.TORRENT,
            parserType = RuleParserType.RSS_TORRENT,
        )

        // 请求第 3 话
        val ep3Results = repository.getTorrentResources(
            title = "葬送的芙莉莲",
            episodeSort = 3f,
            rule = rule,
        )
        assertEquals(1, ep3Results.size)
        val ep3 = ep3Results.first()
        assertEquals("ANi", ep3.fansubName)
        assertEquals(3f, ep3.episodeSort)
        assertEquals("1080P", ep3.resolution)
        assertTrue(ep3.magnetUrl.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
        assertEquals("https://feed.example.com/download/0123456789abcdef0123456789abcdef01234567.torrent", ep3.torrentUrl)
    }

    @Test
    fun getTorrentResources_failOpenOnNetworkError() = runTest {
        val fake = FakeFeedFetchService(null)
        val repository = TorrentFeedRepositoryImpl(fake)
        val rule = PlaybackSourceRule(
            id = "test-torrent-rule",
            name = "Test Torrent Feed",
            urlTemplate = "https://broken.example.com/rss",
            kind = PlaybackRuleKind.TORRENT,
            parserType = RuleParserType.RSS_TORRENT,
        )

        val results = repository.getTorrentResources(
            title = "葬送的芙莉莲",
            rule = rule,
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun getTorrentResources_returnsEmptyWhenRuleDisabledOrNotTorrentKind() = runTest {
        val fake = FakeFeedFetchService(sampleRssXml)
        val repository = TorrentFeedRepositoryImpl(fake)
        val disabledRule = PlaybackSourceRule(
            id = "test-torrent-rule",
            name = "Test Torrent Feed",
            urlTemplate = "https://feed.example.com/rss",
            kind = PlaybackRuleKind.TORRENT,
            isEnabled = false,
        )

        val results = repository.getTorrentResources(
            title = "葬送的芙莉莲",
            rule = disabledRule,
        )
        assertTrue(results.isEmpty())
    }
}
