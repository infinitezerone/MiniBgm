package com.infinitezerone.minibgm.core.data.repository.resolver

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
}
