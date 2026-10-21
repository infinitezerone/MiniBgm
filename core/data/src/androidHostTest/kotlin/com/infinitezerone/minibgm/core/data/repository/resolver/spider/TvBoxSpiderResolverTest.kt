package com.infinitezerone.minibgm.core.data.repository.resolver.spider

import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvBoxSpiderResolverTest {
    private class FakePageFetch(
        private val mockResponses: Map<String, String> = emptyMap(),
    ) : PageFetchService {
        var lastPostBody: String? = null
        var lastPostContentType: String? = null

        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            val content = mockResponses[url] ?: return null
            return FetchedPage(url = url, html = content, responseHeaders = emptyMap())
        }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = fetchHtml(url, requestHeaders)

        override suspend fun postBody(
            url: String,
            body: String,
            contentType: String?,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            lastPostBody = body
            lastPostContentType = contentType
            return fetchHtml(url, requestHeaders)
        }
    }

    @Test
    fun resolveSpider_executesStandardCatVodLifecycle() =
        runTest {
            val d = "$"
            val spiderJs =
                """
                var spider = {
                    init: function(ext) {
                        globalThis._inited = true;
                    },
                    search: function(wd) {
                        return {
                            list: [
                                { vod_id: "1001", vod_name: "葬送的芙莉莲" },
                                { vod_id: "1002", vod_name: "葬送的芙莉莲 迷你剧场" }
                            ]
                        };
                    },
                    detail: function(id) {
                        return {
                            list: [
                                {
                                    vod_id: id,
                                    vod_name: "葬送的芙莉莲",
                                    vod_play_from: "Age专线$d$d${d}纯净线",
                                    vod_play_url: "第1集${d}age_ep1#第2集${d}age_ep2$d$d${d}01${d}clean_ep1#02${d}clean_ep2"
                                }
                            ]
                        };
                    },
                    play: function(flag, id) {
                        if (flag === "Age专线") {
                            return {
                                parse: 0,
                                url: "https://play.age.com/" + id + ".m3u8",
                                header: { "Referer": "https://agefans.com/" }
                            };
                        } else {
                            return {
                                parse: 0,
                                url: "https://clean.example.com/" + id + ".mp4",
                                header: {}
                            };
                        }
                    }
                };
                """.trimIndent()

            val fetch = FakePageFetch()
            val sandbox = JsSpiderSandbox(fetch)
            val resolver = TvBoxSpiderResolver(sandbox, fetch)

            val rule =
                PlaybackSourceRule(
                    id = "rule_test",
                    name = "AGE动漫",
                    urlTemplate = spiderJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                )

            val sources = resolver.resolveSpider(rule, title = "葬送的芙莉莲", epNumber = 1f)

            assertEquals(2, sources.size)
            val ageSource = sources[0]
            assertEquals("https://play.age.com/age_ep1.m3u8", ageSource.url)
            assertEquals(PlaylistEntryKind.DIRECT, ageSource.kind)
            assertEquals("AGE动漫 - Age专线", ageSource.siteName)
            assertEquals("https://agefans.com/", ageSource.headers["Referer"])
            assertEquals("第 1 话", ageSource.label)
            assertEquals("葬送的芙莉莲", ageSource.matchedVodName)

            val cleanSource = sources[1]
            assertEquals("https://clean.example.com/clean_ep1.mp4", cleanSource.url)
            assertEquals("AGE动漫 - 纯净线", cleanSource.siteName)
        }

    @Test
    fun resolveSpider_supportsHostNetworkCallViaReq() =
        runTest {
            val d = "$"
            val spiderJs =
                """
                var spider = {
                    search: async function(wd) {
                        var res = await req("https://api.spider.tv/search?q=" + encodeURIComponent(wd));
                        var data = JSON.parse(res.content);
                        return { list: data.results };
                    },
                    detail: async function(id) {
                        return {
                            vod_play_from: "秒播",
                            vod_play_url: "第1集${d}stream_ep1"
                        };
                    },
                    play: async function(flag, id) {
                        return "https://cdn.spider.tv/" + id + ".m3u8";
                    }
                };
                """.trimIndent()

            val mockHttp =
                mapOf(
                    "https://api.spider.tv/search?q=%E8%8F%9C%E9%B8%9F" to """{"results":[{"vod_id":"77","vod_name":"菜鸟"}]}""",
                )
            val fetch = FakePageFetch(mockHttp)
            val sandbox = JsSpiderSandbox(fetch)
            val resolver = TvBoxSpiderResolver(sandbox, fetch)

            val rule =
                PlaybackSourceRule(
                    id = "rule_req",
                    name = "网播源",
                    urlTemplate = spiderJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                )

            val sources = resolver.resolveSpider(rule, title = "菜鸟", epNumber = 1f)

            assertEquals(1, sources.size)
            assertEquals("https://cdn.spider.tv/stream_ep1.m3u8", sources.first().url)
            assertEquals("网播源 - 秒播", sources.first().siteName)
        }

    @Test
    fun resolveSpider_handlesMalformedScriptGracefully() =
        runTest {
            val brokenJs = "function broken( { syntax error "
            val fetch = FakePageFetch()
            val sandbox = JsSpiderSandbox(fetch)
            val resolver = TvBoxSpiderResolver(sandbox, fetch)

            val rule =
                PlaybackSourceRule(
                    id = "rule_err",
                    name = "错误源",
                    urlTemplate = brokenJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                )

            val sources = resolver.resolveSpider(rule, title = "测试", epNumber = 1f)
            assertTrue(sources.isEmpty())
        }

    @Test
    fun resolveSpider_passesExtToInitAndKeepsParseOneAsPage() =
        runTest {
            val d = "$"
            val spiderJs =
                """
                var spider = {
                    init: function(ext) {
                        globalThis._ext = ext;
                    },
                    search: function(wd) {
                        return { list: [{ vod_id: "9", vod_name: "测试番" }] };
                    },
                    detail: function(id) {
                        return {
                            list: [{
                                vod_name: "测试番",
                                vod_play_from: "解析线",
                                vod_play_url: "第1集${d}ep1"
                            }]
                        };
                    },
                    play: function(flag, id) {
                        return {
                            parse: 1,
                            url: "https://page.example/" + globalThis._ext.token + "/" + id,
                            header: "Referer=https://ref.example/&User-Agent=BgmSpider"
                        };
                    }
                };
                """.trimIndent()
            val fetch = FakePageFetch()
            val resolver = TvBoxSpiderResolver(JsSpiderSandbox(fetch), fetch)
            val ext = Json.parseToJsonElement("""{"token":"abc"}""")
            val rule =
                PlaybackSourceRule(
                    id = "rule_ext",
                    name = "扩展源",
                    urlTemplate = spiderJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                    ext = ext,
                )

            val source = resolver.resolveSpider(rule, title = "测试番", epNumber = 1f).single()

            assertEquals(PlaylistEntryKind.PAGE, source.kind)
            assertEquals("https://page.example/abc/ep1", source.url)
            assertEquals("https://ref.example/", source.headers["Referer"])
            assertEquals("BgmSpider", source.headers["User-Agent"])
        }

    @Test
    fun resolveSpider_supportsSynchronousReqAndRawStringPostBody() =
        runTest {
            val d = "$"
            val spiderJs =
                """
                var spider = {
                    search: function(wd) {
                        var res = req("https://api.spider.tv/search", {
                            method: "POST",
                            body: JSON.stringify({ keyword: wd }),
                            headers: { "Content-Type": "application/json" }
                        });
                        return { list: JSON.parse(res.content).results };
                    },
                    detail: function(id) {
                        return { vod_play_from: "线", vod_play_url: "第1集${d}ep1" };
                    },
                    play: function() { return "https://cdn.example/ep1.m3u8"; }
                };
                """.trimIndent()
            val fetch = FakePageFetch(mapOf("https://api.spider.tv/search" to """{"results":[{"vod_id":"1","vod_name":"测试番"}]}"""))
            val resolver = TvBoxSpiderResolver(JsSpiderSandbox(fetch), fetch)
            val rule =
                PlaybackSourceRule(
                    id = "rule_post",
                    name = "POST源",
                    urlTemplate = spiderJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                )

            val sources = resolver.resolveSpider(rule, title = "测试番", epNumber = 1f)

            assertEquals("https://cdn.example/ep1.m3u8", sources.single().url)
            assertEquals("""{"keyword":"测试番"}""", fetch.lastPostBody)
            assertEquals("application/json", fetch.lastPostContentType)
        }

    @Test
    fun resolveSpider_exposesHtmlSelectorsAndUrlJoin() =
        runTest {
            val d = "$"
            val spiderJs =
                """
                var spider = {
                    search: function(wd) {
                        var page = '<div class="item"><a href="/vod/7">测试番</a></div>';
                        var title = pdfh(page, 'div.item&&a&&Text');
                        var href = pdfh(page, 'div.item&&a&&href');
                        var all = pdfa(page, 'div.item&&a');
                        return {
                            list: [{
                                vod_id: join("https://site.example/base/", href),
                                vod_name: title + (all.length === 1 ? "" : "-bad")
                            }]
                        };
                    },
                    detail: function(id) {
                        return { vod_play_from: "线", vod_play_url: "第1集${d}ep1" };
                    },
                    play: function() { return "https://cdn.example/ep1.m3u8"; }
                };
                """.trimIndent()
            val fetch = FakePageFetch()
            val resolver = TvBoxSpiderResolver(JsSpiderSandbox(fetch), fetch)
            val rule =
                PlaybackSourceRule(
                    id = "rule_html",
                    name = "HTML源",
                    urlTemplate = spiderJs,
                    kind = PlaybackRuleKind.SOURCE,
                    parserType = RuleParserType.SPIDER,
                )

            val sources = resolver.resolveSpider(rule, title = "测试番", epNumber = 1f)

            assertEquals(1, sources.size)
            assertEquals("https://cdn.example/ep1.m3u8", sources.single().url)
        }
}
