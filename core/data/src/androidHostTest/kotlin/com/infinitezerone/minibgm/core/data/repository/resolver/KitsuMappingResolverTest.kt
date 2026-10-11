package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeKitsuPageFetchService(
    private val responses: Map<String, String>,
) : PageFetchService {
    var callCount = 0
    val requestedUrls = mutableListOf<String>()

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        callCount++
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

private class FakeAniListMappingDao(
    private val byBgmId: List<AniListBgmMappingEntity> = emptyList(),
) : AniListMappingDao {
    override suspend fun getMappingsByAniListIds(anilistIds: List<Long>): List<AniListBgmMappingEntity> = emptyList()

    override suspend fun getMappingsByBgmId(bgmId: Long): List<AniListBgmMappingEntity> = byBgmId

    override suspend fun upsertMappings(mappings: List<AniListBgmMappingEntity>) = Unit

    override suspend fun getMonthEtag(monthKey: String): BangumiDataMonthEtagEntity? = null

    override suspend fun upsertMonthEtag(etag: BangumiDataMonthEtagEntity) = Unit
}

private fun mappingRow(
    bgmId: Long,
    sitesJson: String,
): AniListBgmMappingEntity =
    AniListBgmMappingEntity(
        anilistId = bgmId + 1000,
        bgmId = bgmId,
        sitesJson = sitesJson,
        title = "t",
        titleCn = "t",
        beginIso = "",
        endIso = "",
        monthKey = "",
        updatedAt = 0L,
    )

class KitsuMappingResolverTest {
    private val sampleKitsuJson =
        """
        {
          "data": [
            {
              "id": "46474",
              "type": "anime",
              "attributes": {
                "canonicalTitle": "Sousou no Frieren",
                "titles": {
                  "en": "Frieren: Beyond Journey's End",
                  "en_jp": "Sousou no Frieren",
                  "ja_jp": "葬送のフリーレン",
                  "zh_cn": "葬送的芙莉蓮"
                },
                "subtype": "TV",
                "episodeCount": 28
              }
            },
            {
              "id": "49240",
              "type": "anime",
              "attributes": {
                "canonicalTitle": "Sousou no Frieren 2nd Season",
                "titles": {
                  "en": "Frieren: Beyond Journey's End Season 2",
                  "ja_jp": "葬送のフリーレン 第２期"
                },
                "subtype": "TV",
                "episodeCount": 10
              }
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `精准匹配 canonicalTitle 返回对应 Kitsu ID`() =
        runTest {
            val fake = FakeKitsuPageFetchService(mapOf("filter" to sampleKitsuJson))
            val resolver = KitsuMappingResolverImpl(fake)

            val id = resolver.resolveKitsuId("Sousou no Frieren")
            assertEquals("46474", id)
            assertEquals(1, fake.callCount)
        }

    @Test
    fun `别名精准匹配日文与中文标题返回对应 Kitsu ID`() =
        runTest {
            val fake = FakeKitsuPageFetchService(mapOf("filter" to sampleKitsuJson))
            val resolver = KitsuMappingResolverImpl(fake)

            val idJa = resolver.resolveKitsuId("葬送のフリーレン")
            assertEquals("46474", idJa)

            val idCn = resolver.resolveKitsuId("葬送的芙莉蓮")
            assertEquals("46474", idCn)

            val idS2 = resolver.resolveKitsuId("葬送のフリーレン 第２期")
            assertEquals("49240", idS2)
        }

    @Test
    fun `内存缓存生效避免同一番剧重复发起网络请求`() =
        runTest {
            val fake = FakeKitsuPageFetchService(mapOf("filter" to sampleKitsuJson))
            val resolver = KitsuMappingResolverImpl(fake)

            val id1 = resolver.resolveKitsuId("Sousou no Frieren")
            val id2 = resolver.resolveKitsuId("Sousou no Frieren")
            val id3 = resolver.resolveKitsuId("  sousou no frieren  ")

            assertEquals("46474", id1)
            assertEquals("46474", id2)
            assertEquals("46474", id3)
            assertEquals(1, fake.callCount)
        }

    @Test
    fun `未匹配到条目或网络异常时 Fail-Open 返回 null`() =
        runTest {
            val fake =
                FakeKitsuPageFetchService(
                    mapOf(
                        "empty" to """{"data":[]}""",
                        "corrupted" to "<html>502 Bad Gateway</html>",
                    ),
                )
            val resolver = KitsuMappingResolverImpl(fake)

            assertNull(resolver.resolveKitsuId(""))
            assertNull(resolver.resolveKitsuId("   "))
            assertNull(resolver.resolveKitsuId("unknown"))
        }

    @Test
    fun `搜索无精确匹配时不采信排名首条`() =
        runTest {
            val fake =
                FakeKitsuPageFetchService(
                    mapOf(
                        "filter" to
                            """
                            {
                              "data": [
                                {
                                  "id": "11111",
                                  "type": "anime",
                                  "attributes": {
                                    "canonicalTitle": "别的番",
                                    "titles": { "ja_jp": "某某别的东西" }
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                    ),
                )
            val resolver = KitsuMappingResolverImpl(fake)

            // 请求标题与任何候选都不精确相等：宁可 null 也不猜首条——
            // 猜错会拼出"合法但指向别的番"的 stream 地址
            assertNull(resolver.resolveKitsuId("葬送的芙莉莲"))
            assertEquals(1, fake.callCount)
        }

    @Test
    fun `sites 桥命中时优先确定性映射且零网络请求`() =
        runTest {
            val fake = FakeKitsuPageFetchService(emptyMap())
            val dao =
                FakeAniListMappingDao(
                    listOf(
                        mappingRow(
                            bgmId = 404724L,
                            sitesJson =
                                """[{"siteName":"bilibili","displayName":"哔哩哔哩","playUrl":"https://www.bilibili.com/bangumi/media/md28235523"},
                                    |{"siteName":"kitsu","displayName":"Kitsu","playUrl":"https://kitsu.io/anime/46474"}]
                                """.trimMargin(),
                        ),
                    ),
                )
            val resolver = KitsuMappingResolverImpl(fake, dao)

            val id = resolver.resolveKitsuId("葬送的芙莉莲", subjectId = 404724L)

            assertEquals("46474", id)
            assertEquals(0, fake.callCount)
        }

    @Test
    fun `sites 桥未命中时回退标题搜索`() =
        runTest {
            val fake = FakeKitsuPageFetchService(mapOf("filter" to sampleKitsuJson))
            val dao = FakeAniListMappingDao(emptyList())
            val resolver = KitsuMappingResolverImpl(fake, dao)

            val id = resolver.resolveKitsuId("Sousou no Frieren", subjectId = 99999L)

            assertEquals("46474", id)
            assertEquals(1, fake.callCount)
        }

    @Test
    fun `sites 桥里 kitsu 链接异常时忽略该行继续找`() =
        runTest {
            val fake = FakeKitsuPageFetchService(mapOf("filter" to sampleKitsuJson))
            val dao =
                FakeAniListMappingDao(
                    listOf(
                        mappingRow(
                            bgmId = 404724L,
                            sitesJson =
                                """[{"siteName":"kitsu","displayName":"Kitsu","playUrl":"https://kitsu.io/anime/not-a-number"}]""",
                        ),
                    ),
                )
            val resolver = KitsuMappingResolverImpl(fake, dao)

            val id = resolver.resolveKitsuId("Sousou no Frieren", subjectId = 404724L)

            assertEquals("46474", id)
            assertEquals(1, fake.callCount)
        }
}
