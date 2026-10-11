package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeMikanPageFetchService(
    private val responses: Map<String, String>,
) : PageFetchService {
    val requestedUrls = mutableListOf<String>()

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        requestedUrls += url
        val html = responses.entries.firstOrNull { url.contains(it.key) }?.value ?: return null
        return FetchedPage(url = url, html = html)
    }

    override suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        requestHeaders: Map<String, String>,
    ): FetchedPage? = null
}

class MikanRepositoryTest {
    private val sampleRss =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <guid isPermaLink="false">[ANi] 胆大党 - 05 [1080P].mp4</guid>
              <link>https://mikanani.me/Home/Episode/1111111111111111111111111111111111111111</link>
              <title>[ANi] 胆大党 - 05 [1080P].mp4</title>
              <description>[ANi] 胆大党 - 05 [1080P].mp4[400 MB]</description>
              <enclosure type="application/x-bittorrent" length="419430400" url="https://mikanani.me/Download/20241011/1111111111111111111111111111111111111111.torrent" />
            </item>
            <item>
              <guid isPermaLink="false">[ANi] 胆大党 - 06 [1080P].mp4</guid>
              <link>https://mikanani.me/Home/Episode/2222222222222222222222222222222222222222</link>
              <title>[ANi] 胆大党 - 06 [1080P].mp4</title>
              <description>[ANi] 胆大党 - 06 [1080P].mp4[400 MB]</description>
              <enclosure type="application/x-bittorrent" length="419430400" url="https://mikanani.me/Download/20241018/2222222222222222222222222222222222222222.torrent" />
            </item>
            <item>
              <guid isPermaLink="false">[LoliHouse] 胆大党 [01-12 合集][1080p]</guid>
              <link>https://mikanani.me/Home/Episode/3333333333333333333333333333333333333333</link>
              <title>[LoliHouse] 胆大党 [01-12 合集][1080p]</title>
              <description>[LoliHouse] 胆大党 [01-12 合集][8.5 GB]</description>
              <enclosure type="application/x-bittorrent" length="9126805504" url="https://mikanani.me/Download/20241025/3333333333333333333333333333333333333333.torrent" />
            </item>
          </channel>
        </rss>
        """.trimIndent()

    @Test
    fun getTorrentResources_withMikanId_hitsStrategy1() =
        runTest {
            val fake = FakeMikanPageFetchService(mapOf("bangumiId=3233" to sampleRss))
            val repository = MikanRepositoryImpl(fake)

            val results = repository.getTorrentResources(title = "胆大党", mikanId = "3233", episodeSort = 5f)

            assertEquals(2, results.size)
            assertEquals(5f, results[0].episodeSort)
            assertEquals(null, results[1].episodeSort)
            assertTrue(fake.requestedUrls.first().contains("bangumiId=3233"))
        }

    @Test
    fun getTorrentResources_withoutMikanId_searchesTitle() =
        runTest {
            val fake = FakeMikanPageFetchService(mapOf("searchstr=%E8%83%86%E5%A4%A7%E5%85%9A" to sampleRss))
            val repository = MikanRepositoryImpl(fake)

            val results = repository.getTorrentResources(title = "胆大党", mikanId = null, episodeSort = 5f)

            assertEquals(2, results.size)
            assertEquals("ANi", results[0].fansubName)
            assertEquals(5f, results[0].episodeSort)
            assertEquals(null, results[1].episodeSort)
        }

    @Test
    fun getTorrentResources_fallbackToAlternativeTitles_whenPrimaryTitleNotFound() =
        runTest {
            // 主标题 "Dandadan" 无结果，日文原名 "ダンダダン" 有结果
            val fake =
                FakeMikanPageFetchService(
                    mapOf("searchstr=%E3%83%80%E3%83%B3%E3%83%80%E3%83%80%E3%83%B3" to sampleRss),
                )
            val repository = MikanRepositoryImpl(fake)

            val results =
                repository.getTorrentResources(
                    title = "UnknownTitle12345",
                    mikanId = null,
                    episodeSort = null,
                    alternativeTitles = listOf("ダンダダン", "Dandadan"),
                )

            assertEquals(3, results.size)
            assertTrue(fake.requestedUrls.any { it.contains("%E3%83%80%E3%83%B3%E3%83%80%E3%83%80%E3%83%B3") })
        }

    @Test
    fun getTorrentResources_networkFailure_failsOpenWithEmptyList() =
        runTest {
            val fake = FakeMikanPageFetchService(emptyMap())
            val repository = MikanRepositoryImpl(fake)

            val results = repository.getTorrentResources(title = "NonExistent", mikanId = null, episodeSort = 1f)

            assertTrue(results.isEmpty())
        }
}
