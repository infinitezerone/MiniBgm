package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
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

private class FakeMikanMappingDao(
    initialRows: List<AniListBgmMappingEntity> = emptyList(),
) : AniListMappingDao {
    val rows = initialRows.toMutableList()

    override suspend fun getMappingsByAniListIds(anilistIds: List<Long>): List<AniListBgmMappingEntity> =
        rows.filter { it.anilistId in anilistIds }

    override suspend fun getMappingsByBgmId(bgmId: Long): List<AniListBgmMappingEntity> = rows.filter { it.bgmId == bgmId }

    override suspend fun upsertMappings(mappings: List<AniListBgmMappingEntity>) {
        for (m in mappings) {
            rows.removeAll { it.anilistId == m.anilistId }
            rows += m
        }
    }

    override suspend fun getMonthEtag(monthKey: String): BangumiDataMonthEtagEntity? = null

    override suspend fun upsertMonthEtag(etag: BangumiDataMonthEtagEntity) = Unit
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

    @Test
    fun getTorrentResources_steelBallRunSplitStage_discoversMikanIdViaPrequelAndPersistsToDb() =
        runTest {
            val sbrFullRss =
                """
                <rss version="2.0"><channel>
                  <item>
                    <title>[沸班亚马制作组] 飙马野郎 JOJO的奇妙冒险 第二&amp;第三赛段 - 02 [1080p]</title>
                    <link>https://mikanani.me/Home/Episode/541292c27f7216c5ab41780049ca8d0d9c7a3b74</link>
                  </item>
                  <item>
                    <title>[桜都字幕组] 飙马野郎 JOJO的奇妙冒险 / Steel Ball Run [04][1080P][简繁内封]</title>
                    <link>https://mikanani.me/Home/Episode/0327ac7116411c81f53636b41710246754a503b5</link>
                  </item>
                </channel></rss>
                """.trimIndent()

            val fakeFetch =
                FakeMikanPageFetchService(
                    mapOf(
                        "Home/Search" to """<a href="/Home/Bangumi/3887">飙马野郎 JOJO的奇妙冒险 第一赛段</a>""",
                        "Home/Bangumi/3887" to """<a href="https://bgm.tv/subject/551918">https://bgm.tv/subject/551918</a>""",
                        "bangumiId=3887" to sbrFullRss,
                    ),
                )
            val fakeDao = FakeMikanMappingDao()
            val repository = MikanRepositoryImpl(fakeFetch, fakeDao)

            // 首次进入《飙马野郎 第二&第三赛段》(subjectId=639938，前传=551918)，点开第 3 集（ep=3, sort=4）
            val ep4Results =
                repository.getTorrentResources(
                    title = "飙马野郎 JOJO的奇妙冒险 第二&第三赛段",
                    subjectId = 639938L,
                    relatedSubjectIds = listOf(551918L),
                    episodeSort = 4f,
                    alternativeEpisodeSort = 3f,
                )

            assertEquals(1, ep4Results.size)
            assertEquals("桜都字幕组", ep4Results.first().fansubName)
            assertEquals(4f, ep4Results.first().episodeSort)
            // 验证首次探测过程只发了 3 次请求（1. 搜首词块 "飙马野郎" -> 2. 校验 Bangumi/3887 -> 3. 拉取 bangumiId=3887 RSS），无多余白跑请求
            assertEquals(
                listOf(
                    "https://mikanani.me/Home/Search?searchstr=%E9%A3%99%E9%A9%AC%E9%87%8E%E9%83%8E",
                    "https://mikanani.me/Home/Bangumi/3887",
                    "https://mikanani.me/RSS/Bangumi?bangumiId=3887",
                ),
                fakeFetch.requestedUrls,
            )

            // 验证发现的 mikanId=3887 已持久化写入本地数据库，后续新实例无需再跑 /Home/Search 即可直达
            val persistedRows = fakeDao.getMappingsByBgmId(639938L)
            assertEquals(1, persistedRows.size)
            assertTrue(persistedRows.first().sitesJson.contains("3887"))

            val secondFetch = FakeMikanPageFetchService(mapOf("bangumiId=3887" to sbrFullRss))
            val newSessionRepo = MikanRepositoryImpl(secondFetch, fakeDao)
            val ep2Results =
                newSessionRepo.getTorrentResources(
                    title = "飙马野郎 JOJO的奇妙冒险 第二&第三赛段",
                    subjectId = 639938L,
                    episodeSort = 2f,
                    alternativeEpisodeSort = 1f,
                )
            assertEquals(1, ep2Results.size)
            assertEquals(2f, ep2Results.first().episodeSort)
            assertEquals(listOf("https://mikanani.me/RSS/Bangumi?bangumiId=3887"), secondFetch.requestedUrls)
        }

    @Test
    fun getTorrentResources_progressiveSearch_doesNotShortCircuitOnPartialFullTitleMatch() =
        runTest {
            val partialRss =
                """
                <rss version="2.0"><channel>
                  <item>
                    <title>[沸班亚马制作组] 飙马野郎 JOJO的奇妙冒险 第二&amp;第三赛段 - 02 [1080p]</title>
                    <link>https://mikanani.me/Home/Episode/541292c27f7216c5ab41780049ca8d0d9c7a3b74</link>
                  </item>
                </channel></rss>
                """.trimIndent()
            val simplifiedRss =
                """
                <rss version="2.0"><channel>
                  <item>
                    <title>[桜都字幕组] 飙马野郎 JOJO的奇妙冒险 [03][1080P]</title>
                    <link>https://mikanani.me/Home/Episode/16cf7ce36a40333d76025c4a76ddfda5f6174781</link>
                  </item>
                  <item>
                    <title>[桜都字幕组] 飙马野郎 JOJO的奇妙冒险 [04][1080P]</title>
                    <link>https://mikanani.me/Home/Episode/0327ac7116411c81f53636b41710246754a503b5</link>
                  </item>
                </channel></rss>
                """.trimIndent()

            val fakeFetch =
                FakeMikanPageFetchService(
                    mapOf(
                        "%E7%AC%AC%E4%BA%8C" to partialRss, // 含“第二&第三赛段”的全名仅命中第 2 话
                        "%E9%A3%99%E9%A9%AC%E9%87%8E%E9%83%8E%20JOJO%E7%9A%84%E5%A5%87%E5%A6%99%E5%86%92%E9%99%A9" to simplifiedRss,
                    ),
                )
            val repository = MikanRepositoryImpl(fakeFetch)

            val results =
                repository.getTorrentResources(
                    title = "飙马野郎 JOJO的奇妙冒险 第二&第三赛段",
                    episodeSort = 4f,
                    alternativeEpisodeSort = 3f,
                )

            assertEquals(1, results.size)
            assertEquals(4f, results.first().episodeSort)
        }
}
