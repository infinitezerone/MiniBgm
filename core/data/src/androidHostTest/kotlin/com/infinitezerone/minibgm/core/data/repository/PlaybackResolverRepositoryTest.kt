package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.MacCmsProbeResult
import com.infinitezerone.minibgm.core.model.PipelineStep
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.model.StepAction
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    @Test
    fun `macCms 探测候选地址只取主机名且 https 优先`() {
        assertEquals(
            listOf(
                "https://example.com/api.php/provide/vod/?ac=list",
                "http://example.com/api.php/provide/vod/?ac=list",
            ),
            macCmsProbeCandidates("example.com"),
        )
        // 贴整条模板/接口地址也只看主机名——接口路径是约定值，不靠用户填
        assertEquals(
            macCmsProbeCandidates("example.com")[0],
            macCmsProbeCandidates("https://example.com/api.php/provide/vod/?ac=detail&wd={title}")[0],
        )
        assertTrue(macCmsProbeCandidates("http://example.com")[0].startsWith("http://"))
        // 认不出主机名时不发无谓请求
        assertTrue(macCmsProbeCandidates("   ").isEmpty())
    }

    @Test
    fun `countMacCmsListItems 只认 list 数组`() {
        assertEquals(2, countMacCmsListItems("""{"code":1,"list":[{"vod_id":1},{"vod_id":2}]}"""))
        // 空数组也算接口存在：接口在但当前没内容是常态
        assertEquals(0, countMacCmsListItems("""{"code":1,"list":[]}"""))
        // 反爬 HTML / 结构不符 / 空体一律不算命中
        assertNull(countMacCmsListItems("<!DOCTYPE html><html><body>hi</body></html>"))
        assertNull(countMacCmsListItems("""{"code":1,"data":[]}"""))
        assertNull(countMacCmsListItems(""))
    }

    @Test
    fun `probeMacCmsEndpoint 命中即给出取源模板`() =
        runTest {
            val repo =
                PlaybackResolverRepositoryImpl(
                    pageFetchService =
                        FakePageFetchService(
                            mapOf(
                                "https://cms.example.tv/api.php/provide/vod/?ac=list" to
                                    """{"code":1,"msg":"数据列表","list":[{"vod_id":1}]}""",
                            ),
                        ),
                )
            val result = repo.probeMacCmsEndpoint("cms.example.tv")
            assertIs<AppResult.Success<MacCmsProbeResult>>(result)
            val probed = result.data
            assertEquals("https://cms.example.tv/api.php/provide/vod/", probed.endpointUrl)
            // 探测用 ac=list，落库模板必须是 ac=detail&wd={title}
            assertEquals(
                "https://cms.example.tv/api.php/provide/vod/?ac=detail&wd={title}",
                probed.ruleTemplate,
            )
            assertEquals("cms.example.tv", probed.siteName)
            assertEquals(1, probed.sampleCount)
        }

    @Test
    fun `probeMacCmsEndpoint 探不到时如实说探不到`() =
        runTest {
            val repo =
                PlaybackResolverRepositoryImpl(
                    pageFetchService =
                        FakePageFetchService(
                            mapOf(
                                "https://blog.example.com/api.php/provide/vod/?ac=list" to
                                    "<!DOCTYPE html><html><body>hi</body></html>",
                            ),
                        ),
                )
            val result = repo.probeMacCmsEndpoint("blog.example.com")
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.contains("不是采集站"), result.message)
        }

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
                    "https://example.tv/?s=test" to
                        """
                        <article>
                            <h2><a href="https://example.tv/1001">测试动画 [1]</a></h2>
                            <h2><a href="https://example.tv/1002">测试动画 [2]</a></h2>
                        </article>
                        """.trimIndent(),
                    "https://example.tv/1002" to
                        """
                        <div class="video-container">
                            <video src="https://cdn.example.com/anime/ep2.mp4"></video>
                        </div>
                        """.trimIndent(),
                )
            val repo = PlaybackResolverRepositoryImpl(pageFetchService = FakePageFetchService(pages))

            val sources = repo.resolvePages(listOf("https://example.tv/?s=test"), epNumber = 2f, siteName = "示例站")

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals(PlaylistEntryKind.DIRECT, source.kind)
            assertEquals("https://cdn.example.com/anime/ep2.mp4", source.url)
            assertEquals("第 2 话", source.label)
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
    fun `支持解析标准 MacCMS vod_play_url 多播放源分隔符与零填充集数`() =
        runTest {
            val html =
                """
                {
                    "vod_name": "测试动画",
                    "vod_play_url": "第01集${'$'}https://line1.example.com/1.m3u8#第02集${'$'}https://line1.example.com/2.m3u8${'$'}${'$'}${'$'}第01集${'$'}https://line2.example.com/1.m3u8#第02集${'$'}https://line2.example.com/2.m3u8"
                }
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/v/1" to html)))

            val sources = repo.resolvePages(listOf("https://maccms.example.com/v/1"), epNumber = 2f, siteName = "MacCMS")

            assertEquals(2, sources.size)
            assertEquals("https://line1.example.com/2.m3u8", sources[0].url)
            assertEquals("https://line2.example.com/2.m3u8", sources[1].url)
            assertEquals("第02集", sources[0].label)
        }

    @Test
    fun `MacCMS 带签名参数的直链不被丢弃`() =
        runTest {
            val html =
                """
                {
                    "vod_name": "测试动画",
                    "vod_play_url": "第01集${'$'}https://cdn.example.com/1.m3u8?sign=a1#第02集${'$'}https://cdn.example.com/2.m3u8?sign=b2"
                }
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/v/1" to html)))

            val sources = repo.resolvePages(listOf("https://maccms.example.com/v/1"), epNumber = 2f, siteName = "MacCMS")

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals("https://cdn.example.com/2.m3u8?sign=b2", source.url)
            assertEquals("第02集", source.label)
        }

    @Test
    fun `MacCMS 多条目响应优先取片名对得上的那一条`() =
        runTest {
            val html =
                """
                {
                    "list": [
                        {"vod_name": "无关的片子", "vod_play_url": "第01集${'$'}https://other.example.com/1.m3u8#第02集${'$'}https://other.example.com/2.m3u8"},
                        {"vod_name": "测试动画", "vod_play_url": "第01集${'$'}https://want.example.com/1.m3u8#第02集${'$'}https://want.example.com/2.m3u8"}
                    ]
                }
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/api?wd=x" to html)))

            val sources =
                repo.resolvePages(
                    listOf("https://maccms.example.com/api?wd=x"),
                    epNumber = 2f,
                    siteName = "MacCMS",
                    title = "测试动画",
                )

            assertEquals(1, sources.size)
            assertEquals("https://want.example.com/2.m3u8", sources.single().url)
        }

    @Test
    fun `MacCMS 集名为空时按地址里的集号定位`() =
        runTest {
            val html =
                """
                {
                    "vod_name": "测试动画",
                    "vod_play_url": "${'$'}https://cdn.example.com/a/1.m3u8#${'$'}https://cdn.example.com/a/2.m3u8"
                }
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/v/1" to html)))

            val sources = repo.resolvePages(listOf("https://maccms.example.com/v/1"), epNumber = 2f, siteName = "MacCMS")

            assertEquals(1, sources.size)
            assertEquals("https://cdn.example.com/a/2.m3u8", sources.single().url)
        }

    @Test
    fun `MacCMS 直链里的 HTML 实体在抽取前还原`() =
        runTest {
            val html =
                """
                {
                    "vod_name": "测试动画",
                    "vod_play_url": "第01集${'$'}https://cdn.example.com/1.m3u8?token=a&amp;exp=1"
                }
                """.trimIndent()
            val repo = PlaybackResolverRepositoryImpl(FakePageFetchService(mapOf("https://maccms.example.com/v/1" to html)))

            val sources = repo.resolvePages(listOf("https://maccms.example.com/v/1"), epNumber = 1f, siteName = "MacCMS")

            assertEquals("https://cdn.example.com/1.m3u8?token=a&exp=1", sources.single().url)
        }

    @Test
    fun `搜索列表页严格忽略跨域社交外链`() =
        runTest {
            val pages =
                mapOf(
                    "https://example.tv/?s=test" to
                        """
                        <article>
                            <a href="https://twitter.com/ExampleSite">Twitter</a>
                            <a href="https://t.me/examplesite">Telegram</a>
                            <h2><a href="https://example.tv/21133">测试动画 [01]</a></h2>
                        </article>
                        """.trimIndent(),
                    "https://example.tv/21133" to
                        """
                        <video src="https://cdn.example.com/ep1.mp4"></video>
                        """.trimIndent(),
                )
            val fake = FakePageFetchService(pages)
            val repo = PlaybackResolverRepositoryImpl(fake)

            val sources = repo.resolvePages(listOf("https://example.tv/?s=test"), epNumber = 1f, siteName = "示例站")

            assertEquals(1, sources.size)
            assertEquals("https://cdn.example.com/ep1.mp4", sources.single().url)
            assertTrue("twitter" !in fake.requested.joinToString())
            assertTrue("t.me" !in fake.requested.joinToString())
        }

    @Test
    fun `通过声明式流水线 PIPELINE 规则提取两步交互直链与捕获 Header`() =
        runTest {
            val responses =
                mapOf(
                    "https://example.com/watch?t=test&ep=1" to
                        FetchedPage(
                            url = "https://example.com/watch?t=test&ep=1",
                            html = """<div id="player" data-token="secret_token_123"></div>""",
                        ),
                    "https://api.example.com/get-stream" to
                        FetchedPage(
                            url = "https://api.example.com/get-stream",
                            html = """{"stream":"https://cdn.example.com/stream/ep1.m3u8"}""",
                            responseHeaders = mapOf("Set-Cookie" to "auth=xyz789"),
                        ),
                )
            val fake = FakePageFetchService(pages = emptyMap(), responses = responses)
            val repo = PlaybackResolverRepositoryImpl(fake)

            val pipelineRule =
                PlaybackSourceRule(
                    id = "rule_pipeline",
                    name = "测试流水线源",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    headers = mapOf("Referer" to "https://example.com/"),
                    pipeline =
                        listOf(
                            PipelineStep(
                                action = StepAction.FETCH,
                                urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                            ),
                            PipelineStep(
                                action = StepAction.EXTRACT_VARIABLE,
                                regex = """data-token\s*=\s*["']([^"']+)["']""",
                                variableName = "token",
                            ),
                            PipelineStep(
                                action = StepAction.FETCH,
                                method = "POST",
                                urlTemplate = "https://api.example.com/get-stream",
                                bodyTemplate = "token={token}",
                                captureHeaders = listOf("Set-Cookie"),
                            ),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"stream"\s*:\s*"([^"]+)"""",
                            ),
                        ),
                )

            val sources = repo.resolveRule(rule = pipelineRule, title = "test", epNumber = 1f)

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals("https://cdn.example.com/stream/ep1.m3u8", source.url)
            assertEquals("第 1 话", source.label)
            assertEquals("https://example.com/", source.headers["Referer"])
            // Set-Cookie 是响应头，回传时必须按 Cookie 语义规范化，不能沿用原头名
            assertEquals("auth=xyz789", source.headers["Cookie"])
            assertEquals(null, source.headers["Set-Cookie"])
        }

    @Test
    fun `捕获 Set-Cookie 时剥掉属性再以 Cookie 头发给播放器`() =
        runTest {
            val responses =
                mapOf(
                    "https://example.com/watch" to
                        FetchedPage(url = "https://example.com/watch", html = """<div data-token="t1"></div>"""),
                    "https://api.example.com/token" to
                        FetchedPage(
                            url = "https://api.example.com/token",
                            html = """{"stream":"https://cdn.example.com/a.m3u8"}""",
                            responseHeaders =
                                mapOf(
                                    "Set-Cookie" to
                                        "SESS=abc123; Path=/; HttpOnly, TOKEN=def456; " +
                                        "Expires=Wed, 21 Oct 2026 07:28:00 GMT",
                                ),
                        ),
                )
            val repo =
                PlaybackResolverRepositoryImpl(FakePageFetchService(pages = emptyMap(), responses = responses))
            val rule =
                PlaybackSourceRule(
                    id = "rule_cookie",
                    name = "测试采集",
                    urlTemplate = "https://example.com/watch",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH, urlTemplate = "https://example.com/watch"),
                            PipelineStep(
                                action = StepAction.FETCH,
                                method = "POST",
                                urlTemplate = "https://api.example.com/token",
                                bodyTemplate = "k=v",
                                captureHeaders = listOf("Set-Cookie"),
                            ),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"stream"\s*:\s*"([^"]+)"""",
                            ),
                        ),
                )

            val source = repo.resolveRule(rule = rule, title = "t", epNumber = 1f).single()

            assertEquals("https://cdn.example.com/a.m3u8", source.url)
            // 只保留 name=value：Path/HttpOnly 被丢，Expires 里的逗号不会把后面的 cookie 切坏
            assertEquals("SESS=abc123; TOKEN=def456", source.headers["Cookie"])
            assertEquals(null, source.headers["Set-Cookie"])
        }

    @Test
    fun `PIPELINE 规则可复现含 POST 与 Cookie 的三步取流流程`() =
        runTest {
            val responses =
                mapOf(
                    "https://example.com/ep/1" to
                        FetchedPage(
                            url = "https://example.com/ep/1",
                            html = """<div id="player" data-apireq="%7B%22id%22%3A%22123%22%7D"></div>""",
                        ),
                    "https://api.example.com/stream" to
                        FetchedPage(
                            url = "https://api.example.com/stream",
                            html = """{"s":[{"src":"//cdn.example.com/hls/1.m3u8"}]}""",
                            responseHeaders = mapOf("Set-Cookie" to "SESS=s1; Path=/; HttpOnly"),
                        ),
                )
            val fake = FakePageFetchService(pages = emptyMap(), responses = responses)
            val repo = PlaybackResolverRepositoryImpl(fake)

            val rule =
                PlaybackSourceRule(
                    id = "rule_three_step",
                    name = "测试采集",
                    urlTemplate = "https://example.com/ep/{ep}",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH, urlTemplate = "https://example.com/ep/{ep}"),
                            PipelineStep(
                                action = StepAction.EXTRACT_VARIABLE,
                                regex = """data-apireq\s*=\s*["']([^"']+)["']""",
                                variableName = "apireq",
                            ),
                            PipelineStep(
                                action = StepAction.FETCH,
                                method = "POST",
                                urlTemplate = "https://api.example.com/stream",
                                bodyTemplate = "d={apireq}",
                                headers = mapOf("Referer" to "{pageUrl}"),
                                captureHeaders = listOf("Set-Cookie"),
                            ),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"src"\s*:\s*"([^"]+)"""",
                                headers = mapOf("Referer" to "https://example.com/"),
                            ),
                        ),
                )

            val source = repo.resolveRule(rule = rule, title = "t", epNumber = 1f).single()

            assertEquals("https://cdn.example.com/hls/1.m3u8", source.url)
            assertEquals("SESS=s1", source.headers["Cookie"])
            assertEquals("https://example.com/", source.headers["Referer"])
            // 步骤 1 抽到的是 URL 编码串，引擎应解码后再注入 POST 表单
            assertEquals("""{"id":"123"}""", fake.sentForms["https://api.example.com/stream"]?.get("d"))
        }

    @Test
    fun `通过 resolveRule 分发 MACCMS 规则并附加规则头`() =
        runTest {
            val html =
                """
                {
                    "vod_name": "测试",
                    "vod_play_url": "第01集${'$'}https://line.example.com/1.m3u8#第02集${'$'}https://line.example.com/2.m3u8"
                }
                """.trimIndent()
            val fake = FakePageFetchService(mapOf("https://maccms.example.com/api?title=test" to html))
            val repo = PlaybackResolverRepositoryImpl(fake)

            val macRule =
                PlaybackSourceRule(
                    id = "rule_mac",
                    name = "测试采集",
                    urlTemplate = "https://maccms.example.com/api?title={title}",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.MACCMS,
                    headers = mapOf("X-Source" to "minibgm"),
                )

            val sources = repo.resolveRule(rule = macRule, title = "test", epNumber = 2f)

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals("https://line.example.com/2.m3u8", source.url)
            assertEquals("第02集", source.label)
            assertEquals("minibgm", source.headers["X-Source"])
        }

    @Test
    fun `EXTRACT_STREAM 列表语义按第 2 组集名选中目标话`() =
        runTest {
            val html =
                """
                {"list":[
                    {"u":"https://cdn.example.com/hls/1080/1.m3u8","t":"第01话"},
                    {"u":"https://cdn.example.com/hls/1080/2.m3u8","t":"第02话"},
                    {"u":"https://cdn.example.com/hls/1080/3.m3u8","t":"第03话"}
                ]}
                """.trimIndent()
            val fake = FakePageFetchService(mapOf("https://example.com/api?ep=2" to html))
            val repo = PlaybackResolverRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "rule_list",
                    name = "列表语义源",
                    urlTemplate = "https://example.com/api?ep={ep}",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH, urlTemplate = "https://example.com/api?ep={ep}"),
                            // 集名标注候选地址里的 1080 是分辨率噪声——选中必须靠第 2 组集名，不是按话数猜号
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"u":"([^"]+)","t":"([^"]+)"""",
                            ),
                        ),
                )

            val sources = repo.resolveRule(rule = rule, title = "test", epNumber = 2f)

            assertEquals(1, sources.size)
            val source = sources.single()
            assertEquals("https://cdn.example.com/hls/1080/2.m3u8", source.url)
            assertEquals("第02话", source.label)
            assertEquals(2f, source.episodeSort)
        }

    @Test
    fun `EXTRACT_STREAM 整季请求返回全部候选并带真实集号`() =
        runTest {
            val html =
                """
                {"list":[
                    {"u":"https://cdn.example.com/hls/1.m3u8","t":"第01话"},
                    {"u":"https://cdn.example.com/hls/2.m3u8","t":"第02话"},
                    {"u":"https://cdn.example.com/hls/2.5.m3u8","t":"第2.5话"}
                ]}
                """.trimIndent()
            val fake = FakePageFetchService(mapOf("https://example.com/api" to html))
            val repo = PlaybackResolverRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "rule_list_all",
                    name = "整季源",
                    urlTemplate = "https://example.com/api",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH, urlTemplate = "https://example.com/api"),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"u":"([^"]+)","t":"([^"]+)"""",
                            ),
                        ),
                )

            val sources = repo.resolveRule(rule = rule, title = "test", epNumber = 0f)

            assertEquals(3, sources.size)
            assertEquals(listOf("第01话", "第02话", "第2.5话"), sources.map { it.label })
            assertEquals(listOf(1f, 2f, 2.5f), sources.map { it.episodeSort })
        }

    @Test
    fun `EXTRACT_STREAM 无集名标注时保留首个候选兜底`() =
        runTest {
            // 兼容旧形态：正则没有第 2 组、地址也没有强信号——行为应与列表语义引入前一致（取首个）
            val html = """{"a":{"src":"https://cdn.example.com/a1.m3u8"},"b":{"src":"https://cdn.example.com/b2.m3u8"}}"""
            val fake = FakePageFetchService(mapOf("https://example.com/play" to html))
            val repo = PlaybackResolverRepositoryImpl(fake)
            val rule =
                PlaybackSourceRule(
                    id = "rule_legacy",
                    name = "旧形态源",
                    urlTemplate = "https://example.com/play",
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.PIPELINE,
                    pipeline =
                        listOf(
                            PipelineStep(action = StepAction.FETCH, urlTemplate = "https://example.com/play"),
                            PipelineStep(
                                action = StepAction.EXTRACT_STREAM,
                                regex = """"src":"([^"]+)"""",
                            ),
                        ),
                )

            val sources = repo.resolveRule(rule = rule, title = "t", epNumber = 1f)

            assertEquals(1, sources.size)
            assertEquals("https://cdn.example.com/a1.m3u8", sources.single().url)
            assertEquals("第 1 话", sources.single().label)
        }

    @Test
    fun `episodeNumberFromLabel 覆盖中文章节括号与纯数字形态`() {
        assertEquals(3f, episodeNumberFromLabel("第03集"))
        assertEquals(12f, episodeNumberFromLabel("[12]"))
        assertEquals(7f, episodeNumberFromLabel("EP07"))
        assertEquals(7.5f, episodeNumberFromLabel("第7.5话"))
        assertEquals(2f, episodeNumberFromLabel("2"))
        assertEquals(null, episodeNumberFromLabel("1080"))
        assertEquals(null, episodeNumberFromLabel("预告"))
        assertEquals(null, episodeNumberFromLabel(""))
    }

    @Test
    fun `inspectPage 成功提取 video 属性与 iframe 及 MacCMS`() =
        runTest {
            val html =
                """
                <!DOCTYPE html>
                <html>
                <head><title>测试视频详情页</title></head>
                <body>
                    <video src="https://cdn.example.com/play.mp4" data-apireq="sample_val" controls></video>
                    <iframe src="https://embed.example.com/player?id=123"></iframe>
                    <script>var player_aaaa = {"flag":"play"};</script>
                </body>
                </html>
                """.trimIndent()
            val fake = FakePageFetchService(mapOf("https://example.com/detail" to html))
            val repo = PlaybackResolverRepositoryImpl(fake)

            val result = repo.inspectPage("https://example.com/detail")

            assertTrue(result.isSuccess)
            assertEquals("测试视频详情页", result.title)
            assertTrue(result.hasVideoTag)
            assertEquals("sample_val", result.videoAttrs["data-apireq"])
            assertEquals("https://cdn.example.com/play.mp4", result.videoAttrs["src"])
            assertEquals(listOf("https://embed.example.com/player?id=123"), result.iframeUrls)
            assertTrue(result.hasMacCmsPattern)
        }

    @Test
    fun `inspectPage 对无效 URL 与抓取失败优雅返回错误`() =
        runTest {
            val fake = FakePageFetchService(emptyMap())
            val repo = PlaybackResolverRepositoryImpl(fake)

            val invalid = repo.inspectPage("not-a-url")
            assertEquals(false, invalid.isSuccess)
            assertEquals("Invalid URL", invalid.errorMessage)

            val failed = repo.inspectPage("https://example.com/404")
            assertEquals(false, failed.isSuccess)
            assertTrue(failed.errorMessage?.contains("Failed to fetch") == true)
        }

    @Test
    fun `probeSite 成功探查带搜索框站点与样本播放页`() =
        runTest {
            val homeHtml =
                """
                <!DOCTYPE html>
                <html>
                <head><title>AnimeSite 动漫主页</title></head>
                <body>
                    <form action="/search" method="get">
                        <input type="text" name="keyword" placeholder="搜索番剧" />
                    </form>
                    <div class="recent">
                        <a href="/watch/101">最新连载 01</a>
                    </div>
                </body>
                </html>
                """.trimIndent()
            val searchHtml =
                """
                <div class="results">
                    <a href="/watch/202">葬送的芙莉莲 第 1 话</a>
                </div>
                """.trimIndent()
            val fake =
                FakePageFetchService(
                    mapOf(
                        "https://anime.example.com/" to homeHtml,
                        "https://anime.example.com/search?keyword=%E8%8A%99%E8%8E%89%E8%8E%B2" to searchHtml,
                    ),
                )
            val repo = PlaybackResolverRepositoryImpl(fake)

            val probe = repo.probeSite("https://anime.example.com/", "芙莉莲")

            assertTrue(probe.isReachable)
            assertEquals(false, probe.isAdParking)
            assertEquals("AnimeSite 动漫主页", probe.title)
            assertTrue(probe.hasSearchBox)
            assertEquals("https://anime.example.com/search?keyword={title}", probe.searchUrlPattern)
            assertEquals("https://anime.example.com/watch/202", probe.sampleEpisodeUrl)
        }

    @Test
    fun `probeSite 识别停放域名与不可达站点`() =
        runTest {
            val parkingHtml =
                """
                <html><head><title>Domain for Sale - 域名出售</title></head><body>This domain is expired.</body></html>
                """.trimIndent()
            val fake = FakePageFetchService(mapOf("https://parking.example.com" to parkingHtml))
            val repo = PlaybackResolverRepositoryImpl(fake)

            val parking = repo.probeSite("https://parking.example.com")
            assertTrue(parking.isReachable)
            assertTrue(parking.isAdParking)

            val unreachable = repo.probeSite("https://down.example.com")
            assertEquals(false, unreachable.isReachable)
            assertTrue(unreachable.errorMessage?.contains("Site unreachable") == true)
        }

    @Test
    fun `findCandidateEpisodeUrl 忽略带版本号样式表与脚本并优先提取数字详情页`() {
        val html =
            """
            <html>
            <head>
              <link rel="stylesheet" href="https://example.tv/wp-content/themes/twentyten/style.css?ver=20190507" type="text/css" />
              <script src="https://example.tv/wp-includes/js/jquery.js?ver=3.7.1"></script>
            </head>
            <body>
              <a href="/category/all">所有动画</a>
              <a href="/about.html">关于我们</a>
              <a href="https://example.tv/30194">葬送的芙莉莲 [01]</a>
            </body>
            </html>
            """.trimIndent()

        val candidate = findCandidateEpisodeUrl(html, "https://example.tv")
        assertEquals("https://example.tv/30194", candidate)
    }

    @Test
    fun `findCandidateEpisodeUrl 正确识别 rel 在 href 前方的 bookmark 属性`() {
        val html =
            """
            <article>
              <h2 class="entry-title"><a rel="bookmark" href="https://example.tv/30194">葬送的芙莉莲 [01]</a></h2>
            </article>
            """.trimIndent()

        val candidate = findCandidateEpisodeUrl(html, "https://example.tv")
        assertEquals("https://example.tv/30194", candidate)
    }

    @Test
    fun `findHomeEntryTitle 从详情链接锚文本抽条目名并跳过导航与超长文本`() {
        val html =
            """
            <html><body>
              <a href="/voddetail/1.html">首页</a>
              <a href="/about.html">关于我们</a>
              <a href="/voddetail/2.html">12</a>
              <a href="/voddetail/3.html">${"推".repeat(50)}</a>
              <a href="/voddetail/4.html">葬送的芙莉莲</a>
            </body></html>
            """.trimIndent()

        assertEquals("葬送的芙莉莲", findHomeEntryTitle(html, "https://example.tv"))
    }

    @Test
    fun `probeSite 未给样本时从首页抽真实条目当样本`() =
        runTest {
            val homeHtml =
                """
                <html>
                <head><title>AnimeSite 动漫主页</title></head>
                <body>
                    <form action="/search" method="get">
                        <input type="text" name="keyword" placeholder="搜索番剧" />
                    </form>
                    <a href="/voddetail/123.html">葬送的芙莉莲</a>
                    <a href="/voddetail/456.html">孤独摇滚</a>
                </body>
                </html>
                """.trimIndent()
            val searchHtml =
                """
                <div class="results">
                    <a href="/watch/202">葬送的芙莉莲 第 1 话</a>
                </div>
                """.trimIndent()
            val fake =
                FakePageFetchService(
                    mapOf(
                        "https://anime.example.com/" to homeHtml,
                        "https://anime.example.com/search?keyword=${PlaybackSourceRule.encodeParam("葬送的芙莉莲")}" to searchHtml,
                    ),
                )
            val repo = PlaybackResolverRepositoryImpl(fake)

            val probe = repo.probeSite("https://anime.example.com/")

            // 样本必须是该站真实存在的条目：写死的通用番名在没收录它的站上探不出搜索参数模式
            assertEquals("葬送的芙莉莲", probe.sampleTitleUsed)
            assertEquals("https://anime.example.com/watch/202", probe.sampleEpisodeUrl)
        }
}
