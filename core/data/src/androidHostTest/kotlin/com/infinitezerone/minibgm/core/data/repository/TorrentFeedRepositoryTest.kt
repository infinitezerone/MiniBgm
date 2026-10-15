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
    private val sampleRssXml =
        """
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
        private val htmlForUrl: ((String) -> String?)? = null,
    ) : PageFetchService {
        val requestedUrls = mutableListOf<String>()

        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            requestedUrls += url
            val html = htmlForUrl?.invoke(url) ?: htmlToReturn
            return html?.let { FetchedPage(url = url, html = it) }
        }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null
    }

    @Test
    fun getTorrentResources_parsesAndFiltersByEpisode() =
        runTest {
            val fake = FakeFeedFetchService(sampleRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            // 请求第 3 话
            val ep3Results =
                repository.getTorrentResources(
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
    fun getTorrentResources_returnsEmptyWhenEpisodeMissesInsteadOfFullFeed() =
        runTest {
            // Feed 里只有第 3、4 话，没有任何合集项
            val fake = FakeFeedFetchService(sampleRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "葬送的芙莉莲",
                    episodeSort = 5f,
                    rule = rule,
                )

            // 请求第 5 话时 Feed 只有 3、4 话：绝不能回退成整份 Feed 让用户误点播错集
            assertTrue(results.isEmpty())
        }

    @Test
    fun getTorrentResources_fallsBackToCollectionItemsWhenEpisodeMisses() =
        runTest {
            val collectionRssXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Sample Torrent Feed</title>
                    <item>
                      <title>[LoliHouse] 葬送的芙莉莲 全集 01-28 [1080P][BDRip]</title>
                      <link>magnet:?xt=urn:btih:1111111111111111111111111111111111111111</link>
                      <enclosure url="https://feed.example.com/download/collection.torrent" length="12000000000" type="application/x-bittorrent"/>
                      <description>[11.2 GB]</description>
                    </item>
                    <item>
                      <title>[ANi] 葬送的芙莉莲 - 03 [1080P].mp4</title>
                      <link>magnet:?xt=urn:btih:2222222222222222222222222222222222222222</link>
                      <enclosure url="https://feed.example.com/download/03.torrent" length="350000000" type="application/x-bittorrent"/>
                    </item>
                  </channel>
                </rss>
                """.trimIndent()
            val fake = FakeFeedFetchService(collectionRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "葬送的芙莉莲",
                    episodeSort = 5f,
                    rule = rule,
                )

            // 未命中单集，但存在合集项：只回退合集（episodeSort == null），不得带上第 3 话的单集条目
            assertEquals(1, results.size)
            assertEquals(null, results.first().episodeSort)
        }

    @Test
    fun getTorrentResources_failOpenOnNetworkError() =
        runTest {
            val fake = FakeFeedFetchService(null)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://broken.example.com/rss",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "葬送的芙莉莲",
                    rule = rule,
                )
            assertTrue(results.isEmpty())
        }

    @Test
    fun getTorrentResources_returnsEmptyWhenRuleDisabledOrNotTorrentKind() =
        runTest {
            val fake = FakeFeedFetchService(sampleRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val disabledRule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss",
                    kind = PlaybackRuleKind.TORRENT,
                    isEnabled = false,
                )

            val results =
                repository.getTorrentResources(
                    title = "葬送的芙莉莲",
                    rule = disabledRule,
                )
            assertTrue(results.isEmpty())
        }

    @Test
    fun getTorrentResources_fallsBackToAlternativeTitlesWhenPrimarySearchIsEmpty() =
        runTest {
            val emptyRssXml = """<?xml version="1.0"?><rss version="2.0"><channel></channel></rss>"""
            val fake =
                FakeFeedFetchService(
                    htmlForUrl = { url ->
                        // 站点按日文原名索引：中文主标题检索为空，别名才命中
                        if (url.contains("UnknownTitle12345")) emptyRssXml else sampleRssXml
                    },
                )
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "UnknownTitle12345",
                    episodeSort = 3f,
                    alternativeTitles = listOf("葬送的芙莉莲"),
                    rule = rule,
                )

            // 主标题检索为空 → 回退别名后命中第 3 话
            assertEquals(1, results.size)
            assertEquals(3f, results.first().episodeSort)
            assertEquals(2, fake.requestedUrls.size)
            assertTrue(fake.requestedUrls.first().contains("UnknownTitle12345"))
            assertTrue(fake.requestedUrls[0] != fake.requestedUrls[1])
        }

    @Test
    fun getTorrentResources_neverQueriesAlternativeTitlesWhenPrimaryHits() =
        runTest {
            val fake = FakeFeedFetchService(sampleRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "葬送的芙莉莲",
                    episodeSort = 3f,
                    alternativeTitles = listOf("Sousou no Frieren", "葬送のフリーレン"),
                    rule = rule,
                )

            assertEquals(1, results.size)
            // 主标题命中即返回，别名回退不得产生额外网络请求
            assertEquals(1, fake.requestedUrls.size)
        }

    @Test
    fun getTorrentResources_queriesFixedFeedUrlOnlyOnceAcrossTitleAliases() =
        runTest {
            val fake = FakeFeedFetchService(null)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-fixed-rule",
                    name = "Fixed Torrent Feed",
                    // 不含 {title} 占位符：所有候选标题都会解析成同一个 URL
                    urlTemplate = "https://feed.example.com/rss",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            val results =
                repository.getTorrentResources(
                    title = "主标题",
                    episodeSort = 1f,
                    alternativeTitles = listOf("别名一", "别名二"),
                    rule = rule,
                )

            assertTrue(results.isEmpty())
            assertEquals(1, fake.requestedUrls.size)
        }

    @Test
    fun getTorrentResources_evictsOldestEntryBeyondCacheCapacity() =
        runTest {
            val fake = FakeFeedFetchService(sampleRssXml)
            val repository = TorrentFeedRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "test-torrent-rule",
                    name = "Test Torrent Feed",
                    urlTemplate = "https://feed.example.com/rss?keyword={title}",
                    kind = PlaybackRuleKind.TORRENT,
                    parserType = RuleParserType.RSS_TORRENT,
                )

            // 缓存上限 32：写入 40 个不同关键词，最旧的 8 条必须被淘汰
            repeat(40) { index ->
                repository.getTorrentResources(
                    title = "title-$index",
                    episodeSort = 3f,
                    rule = rule,
                )
            }
            assertEquals(40, fake.requestedUrls.size)

            // 最旧条目已淘汰 → 重新触网
            repository.getTorrentResources(title = "title-0", episodeSort = 3f, rule = rule)
            assertEquals(41, fake.requestedUrls.size)

            // 最新条目仍在缓存 → 不触网
            repository.getTorrentResources(title = "title-39", episodeSort = 3f, rule = rule)
            assertEquals(41, fake.requestedUrls.size)
        }
}
