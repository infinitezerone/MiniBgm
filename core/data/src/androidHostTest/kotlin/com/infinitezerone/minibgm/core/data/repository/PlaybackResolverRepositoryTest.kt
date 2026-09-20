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
    private val responses: Map<String, FetchedPage> = emptyMap(),
) : PageFetchService {
    val requested = mutableListOf<String>()
    val sentHeaders = mutableMapOf<String, Map<String, String>>()
    val sentForms = mutableMapOf<String, Map<String, String>>()

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        requested += url
        sentHeaders[url] = requestHeaders
        return responses[url] ?: pages[url]?.let { FetchedPage(url = url, html = it) }
    }

    override suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        requested += url
        sentForms[url] = formData
        sentHeaders[url] = requestHeaders
        return responses[url] ?: pages[url]?.let { FetchedPage(url = url, html = it) }
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
    fun `iframe 内若包含直链则深入一级抽取`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://w.example.com/watch/9" to
                            """<iframe src="https://player.example.net/embed/9"></iframe>""",
                        "https://player.example.net/embed/9" to
                            """<video src="https://cdn.example.com/stream.m3u8"></video>""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://w.example.com/watch/9"))

            val source = sources.single()
            assertEquals(PlaylistEntryKind.DIRECT, source.kind)
            assertEquals("https://cdn.example.com/stream.m3u8", source.url)
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

    @Test
    fun `集数带 ep 前缀时按地址标注真实集数`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://v.example.com/e/1" to
                            """<a href="https://cdn.example.com/v/ep07.m3u8"></a><a href="https://cdn.example.com/v/ep12.m3u8"></a>""",
                    ),
                )

            val sources =
                repo.resolvePages(listOf("https://v.example.com/e/1"), epNumber = 12f, siteName = "example")

            assertEquals(listOf("第 7 话", "第 12 话"), sources.map { it.label })
            assertEquals(listOf(7f, 12f), sources.map { it.episodeSort })
        }

    @Test
    fun `整季请求时按地址裸数字标注分集`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://v.example.com/e/1" to
                            """var url = "https://cdn.example.com/hls/45/index.m3u8";""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://v.example.com/e/1"))

            assertEquals(listOf("第 45 话"), sources.map { it.label })
            assertEquals(listOf(45f), sources.map { it.episodeSort })
        }

    @Test
    fun `分辨率年份与编码数字不当作集数`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://v.example.com/e/1" to
                            """var url = "https://cdn.example.com/1080p/2024/h264/vod.m3u8";""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://v.example.com/e/1"))

            assertEquals(listOf(""), sources.map { it.label })
            assertEquals(listOf(0f), sources.map { it.episodeSort })
        }

    @Test
    fun `单集查询且地址无强信号时保持查询话数`() =
        runTest {
            val repo =
                repository(
                    mapOf(
                        "https://v.example.com/e/1" to
                            """var url = "https://cdn.example.com/hls/45/index.m3u8";""",
                    ),
                )

            val sources = repo.resolvePages(listOf("https://v.example.com/e/1"), epNumber = 12f)

            assertEquals(listOf("第 12 话"), sources.map { it.label })
            assertEquals(listOf(12f), sources.map { it.episodeSort })
        }

    @Test
    fun `结构化流清单按字段映射并合并规则头`() =
        runTest {
            val fetch =
                FakePageFetchService(
                    mapOf(
                        "https://api.example.com/streams" to
                            """{"streams":[{"url":"https://cdn.example.com/1.m3u8","title":"第 1 话",""" +
                            """"headers":{"Referer":"https://cdn.example.com/"}},{"url":"https://cdn.example.com/2.m3u8"}]}""",
                    ),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fetch)

            val sources =
                repo.resolveTemplate(
                    url = "https://api.example.com/streams",
                    headers = mapOf("X-Key" to "abc"),
                    epNumber = 0f,
                    siteName = "测试接口",
                )

            assertEquals(2, sources.size)
            assertEquals("第 1 话", sources[0].label)
            assertEquals("https://cdn.example.com/", sources[0].headers["Referer"])
            // 规则请求头追加在清单条目头之后（与正则路径一致：规则头优先）
            assertEquals("abc", sources[0].headers["X-Key"])
            // 无 title 的条目回退为空标签，不编造话数
            assertEquals("", sources[1].label)
        }

    @Test
    fun `空 streams 数组视为接口明确无结果`() =
        runTest {
            val fetch =
                FakePageFetchService(mapOf("https://api.example.com/empty" to """{"streams":[]}"""))
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fetch)

            val sources = repo.resolveTemplate("https://api.example.com/empty")

            assertTrue(sources.isEmpty())
        }

    @Test
    fun `无 streams 字段的 JSON 仍走正则抽取`() =
        runTest {
            val fetch =
                FakePageFetchService(
                    mapOf("https://api.example.com/legacy" to """{"url":"https://cdn.example.com/e/1.m3u8"}"""),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fetch)

            val sources = repo.resolveTemplate("https://api.example.com/legacy", epNumber = 1f)

            assertEquals(listOf("https://cdn.example.com/e/1.m3u8"), sources.map { it.url })
            assertEquals("第 1 话", sources.single().label)
        }

    @Test
    fun `清单里非法地址的条目被跳过`() =
        runTest {
            val fetch =
                FakePageFetchService(
                    mapOf(
                        "https://api.example.com/mixed" to
                            """{"streams":[{"url":"ftp://bad.example.com/x.m3u8"},{"url":"https://cdn.example.com/ok.m3u8"}]}""",
                    ),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fetch)

            val sources = repo.resolveTemplate("https://api.example.com/mixed")

            assertEquals(listOf("https://cdn.example.com/ok.m3u8"), sources.map { it.url })
        }

    @Test
    fun `模板接口请求带出自定义头并回传给播放器`() =
        runTest {
            val fetch =
                FakePageFetchService(
                    mapOf("https://api.example.com/x?q=1" to """{"url":"https://cdn.example.com/e/1.m3u8"}"""),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fetch)

            val sources =
                repo.resolveTemplate(
                    url = "https://api.example.com/x?q=1",
                    headers = mapOf("Referer" to "https://api.example.com/", "X-Key" to "abc"),
                    epNumber = 1f,
                    siteName = "测试接口",
                )

            assertEquals(
                mapOf("Referer" to "https://api.example.com/", "X-Key" to "abc"),
                fetch.sentHeaders["https://api.example.com/x?q=1"],
            )
            val source = sources.single()
            assertEquals("https://cdn.example.com/e/1.m3u8", source.url)
            assertEquals("测试接口", source.siteName)
            assertEquals("https://api.example.com/", source.headers["Referer"])
            assertEquals("abc", source.headers["X-Key"])
        }

    @Test
    fun `搜索列表页含目标分集链接时自动深入分集页抽取直链`() =
        runTest {
            val pages =
                mapOf(
                    "https://anime1.me/?s=test" to
                        """
                        <article>
                            <h2><a href="https://anime1.me/1001">测试动画 [1]</a></h2>
                            <h2><a href="https://anime1.me/1002">测试动画 [2]</a></h2>
                        </article>
                        """.trimIndent(),
                    "https://anime1.me/1002" to
                        """
                        <div class="video-container">
                            <video src="https://cdn.example.com/anime/ep2.mp4"></video>
                        </div>
                        """.trimIndent(),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = FakePageFetchService(pages))

            val sources = repo.resolvePages(listOf("https://anime1.me/?s=test"), epNumber = 2f, siteName = "Anime1")

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals(PlaylistEntryKind.DIRECT, source.kind)
            assertEquals("https://cdn.example.com/anime/ep2.mp4", source.url)
            assertEquals("第 2 话", source.label)
        }

    @Test
    fun `单集页含 Anime1 协议时自动请求 API 并携带 Cookie 与 Referer`() =
        runTest {
            val responses =
                mapOf(
                    "https://anime1.me/30193" to
                        FetchedPage(
                            url = "https://anime1.me/30193",
                            html = """<video id="vjs" data-apireq="%7B%22c%22%3A%221949%22%2C%22e%22%3A%2211%22%7D"></video>""",
                        ),
                    "https://v.anime1.me/api" to
                        FetchedPage(
                            url = "https://v.anime1.me/api",
                            html = """{"s":[{"src":"//nazuna.v.anime1.me/1949/11.mp4","type":"video/mp4"}]}""",
                            responseHeaders = mapOf("Cookie" to "e=auth123; p=sig456"),
                        ),
                )
            val fakeFetch = FakePageFetchService(pages = emptyMap(), responses = responses)
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = fakeFetch)

            val sources = repo.resolvePages(listOf("https://anime1.me/30193"), epNumber = 11f, siteName = "Anime1")

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals(PlaylistEntryKind.DIRECT, source.kind)
            assertEquals("https://nazuna.v.anime1.me/1949/11.mp4", source.url)
            assertEquals("https://anime1.me/", source.headers["Referer"])
            assertEquals("e=auth123; p=sig456", source.headers["Cookie"])
            assertEquals("第 11 话", source.label)
        }

    @Test
    fun `支持解析 MacCMS vod_play_url 剧集列表并提取目标集直链`() =
        runTest {
            val html =
                """
                var player_aaaa = {
                    "flag": "play",
                    "url": "第1集${'$'}https://cdn.example.com/1.m3u8#第2集${'$'}https://cdn.example.com/2.m3u8"
                };
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/v/1" to html)))

            val sources = repo.resolvePages(listOf("https://maccms.example.com/v/1"), epNumber = 2f, siteName = "MacCMS")

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals("https://cdn.example.com/2.m3u8", source.url)
            assertEquals("第2集", source.label)
        }

    @Test
    fun `搜索列表页严格忽略跨域社交外链`() =
        runTest {
            val pages =
                mapOf(
                    "https://anime1.me/?s=test" to
                        """
                        <article>
                            <a href="https://twitter.com/Anime1Me">Twitter</a>
                            <a href="https://t.me/anime1notify">Telegram</a>
                            <h2><a href="https://anime1.me/21133">测试动画 [01]</a></h2>
                        </article>
                        """.trimIndent(),
                    "https://anime1.me/21133" to
                        """
                        <video src="https://cdn.example.com/ep1.mp4"></video>
                        """.trimIndent(),
                )
            val fake = FakePageFetchService(pages)
            val repo = PlaybackResolverRepositoryImpl(fake)

            val sources = repo.resolvePages(listOf("https://anime1.me/?s=test"), epNumber = 1f, siteName = "Anime1")

            assertEquals(1, sources.size)
            assertEquals("https://cdn.example.com/ep1.mp4", sources.single().url)
            assertTrue("twitter" !in fake.requested.joinToString())
            assertTrue("t.me" !in fake.requested.joinToString())
        }
}
