package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakePageFetchService(
    private val pages: Map<String, String>,
) : PageFetchService {
    val requested = mutableListOf<String>()

    override suspend fun fetchHtml(url: String): FetchedPage? {
        requested += url
        return pages[url]?.let { FetchedPage(url = url, html = it) }
    }
}

class PlaybackResolverRepositoryTest {
    private fun repository(pages: Map<String, String>) =
        PlaybackResolverRepositoryImpl(
            pageFetchService = FakePageFetchService(pages),
        )

    @Test
    fun `抽取绝对 m3u8 直链并带上页面 Referer`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://play.example.com/p/123.html" to
                            """<video src="https://cdn.example.com/hls/123/index.m3u8?token=abc"></video>""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://play.example.com/p/123.html"), epNumber = 12f, siteName = "example")

            assertEquals(1, sources.size)
            val source = sources.first()
            assertEquals(PlaylistEntryKind.DIRECT, source.kind)
            assertEquals("https://cdn.example.com/hls/123/index.m3u8?token=abc", source.url)
            assertEquals(mapOf("Referer" to "https://play.example.com/"), source.headers)
            assertEquals("第 12 话", source.label)
            assertEquals("example", source.siteName)
        }

    @Test
    fun `还原 JSON 转义与 HTML 实体中的直链`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://v.example.com/e/1" to
                            """var url = "https:\/\/cdn.example.com\/v\/ep1.mp4?a=1&amp;b=2";""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://v.example.com/e/1"))

            assertEquals(listOf("https://cdn.example.com/v/ep1.mp4?a=1&b=2"), sources.map { it.url })
        }

    @Test
    fun `根相对地址按页面站点根补全`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://s.example.com/a/b.html" to
                            """<source src="/media/ep03.mkv">""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://s.example.com/a/b.html"))

            assertEquals("https://s.example.com/media/ep03.mkv", sources.single().url)
        }

    @Test
    fun `iframe 只作为待继续解析的页面来源`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://w.example.com/watch/9" to
                            """<iframe src="https://player.example.net/embed/9"></iframe>""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://w.example.com/watch/9"))

            val source = sources.single()
            assertEquals(PlaylistEntryKind.PAGE, source.kind)
            assertEquals("https://player.example.net/embed/9", source.url)
        }

    @Test
    fun `抓不到的页面降级为空结果而不是抛错`() =
        runTest {
            val fake = FakePageFetchService(emptyMap())
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fake)

            assertTrue(repo.resolvePages(listOf("https://gone.example.com/x.html")).isEmpty())
            assertEquals(listOf("https://gone.example.com/x.html"), fake.requested)
        }

    @Test
    fun `一次调用只解析有限个页面`() =
        runTest {
            val fake = FakePageFetchService(emptyMap())
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fake)

            repo.resolvePages((1..30).map { "https://p.example.com/$it.html" })

            assertEquals(5, fake.requested.size)
        }
}
