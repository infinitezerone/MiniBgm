package com.infinitezerone.minibgm.core.data.repository.resolver.spider

import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.RuleParserType
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvBoxSpiderResolverTest {
    private class FakePageFetch(
        private val mockResponses: Map<String, String> = emptyMap(),
    ) : PageFetchService {
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
}
