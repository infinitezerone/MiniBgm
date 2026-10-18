package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParseResult
import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao
import com.infinitezerone.minibgm.core.database.entity.DanmakuEpisodeOverrideEntity
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.core.model.DanmakuMode
import com.infinitezerone.minibgm.core.model.DanmakuResult
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.MAX_PAGE_BYTES
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.testing.util.TestNetworkMonitor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDanmakuPageFetchService(
    private val responses: Map<String, String>,
) : PageFetchService {
    var callCount = 0
    val requestedUrls = mutableListOf<String>()
    val lastHeaders = mutableMapOf<String, String>()

    /** 每次请求实际带的请求头（离线策略、签名头都从这里断言） */
    val allHeaders = mutableListOf<Map<String, String>>()

    /** 弹幕正文请求实际使用的载荷预算（搜索接口不计） */
    var lastCommentMaxBytes: Int? = null

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
        maxBytes: Int,
    ): FetchedPage? {
        if (url.contains("/api/v2/comment/")) lastCommentMaxBytes = maxBytes
        return fetchHtml(url, requestHeaders)
    }

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        callCount++
        requestedUrls += url
        lastHeaders.clear()
        lastHeaders.putAll(requestHeaders)
        allHeaders += requestHeaders.toMap()
        val html = responses.entries.firstOrNull { url.contains(it.key) }?.value ?: return null
        return FetchedPage(url = url, html = html)
    }

    override suspend fun postForm(
        url: String,
        formData: Map<String, String>,
        requestHeaders: Map<String, String>,
    ): FetchedPage? = null
}

private class FakeDanmakuOverrideDao : DanmakuOverrideDao {
    val store = mutableMapOf<Long, DanmakuEpisodeOverrideEntity>()

    override suspend fun getOverride(episodeId: Long): DanmakuEpisodeOverrideEntity? = store[episodeId]

    override suspend fun upsertOverride(override: DanmakuEpisodeOverrideEntity) {
        store[override.episodeId] = override
    }

    override suspend fun deleteOverride(episodeId: Long) {
        store.remove(episodeId)
    }
}

class DanmakuRepositoryTest {
    private val sampleDandanJson =
        """
        {
          "count": 3,
          "comments": [
            {
              "cid": 101,
              "p": "12.5,1,16777215,user1",
              "m": "前方高能！"
            },
            {
              "cid": 102,
              "p": "5.0,5,16711680,user2",
              "m": "顶部弹幕注意"
            },
            {
              "cid": 103,
              "p": "20.2,4,65280,user3",
              "m": "底部弹幕提示"
            }
          ]
        }
        """.trimIndent()

    private val sampleXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <i>
          <d p="8.50000,1,25,16777215,1600000000,0,hash,101">名场面打卡</d>
          <d p="3.20000,5,25,16711680,1600000000,0,hash,102">顶部警告</d>
          <d p="15.00000,4,25,65280,1600000000,0,hash,103">底部字幕</d>
        </i>
        """.trimIndent()

    /** 两部同名番：TV 版逐话编号 + 剧场版"正片"，命中靠集数校验而非首条盲取 */
    private val multiAnimeSearchJson =
        """
        {
          "hasMore": false,
          "animes": [
            {
              "animeId": 2001,
              "animeTitle": "剧场版 葬送的芙莉莲",
              "episodes": [
                { "episodeId": 3001, "episodeTitle": "正片" }
              ]
            },
            {
              "animeId": 2002,
              "animeTitle": "葬送的芙莉莲",
              "episodes": [
                { "episodeId": 9871, "episodeTitle": "第01话 结束与开始" },
                { "episodeId": 9876, "episodeTitle": "第03话 杀人魔" },
                { "episodeId": 9899, "episodeTitle": "第12话 宛如梦想" }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun repoWith(
        fetcher: FakeDanmakuPageFetchService,
        overrideDao: FakeDanmakuOverrideDao? = null,
    ): DanmakuRepositoryImpl =
        DanmakuRepositoryImpl(
            pageFetchService = fetcher,
            danmakuOverrideDao = overrideDao,
        )

    private suspend fun DanmakuRepository.get(
        title: String,
        episodeSort: Float,
        appId: String? = null,
        appSecret: String? = null,
        bangumiEpisodeId: Long = 100L,
    ): DanmakuResult =
        getDanmaku(
            bangumiEpisodeId = bangumiEpisodeId,
            subjectId = 1L,
            title = title,
            episodeSort = episodeSort,
            episodeName = null,
            appId = appId,
            appSecret = appSecret,
        )

    @Test
    fun `DanmakuParser 正确解析弹弹play JSON格式并按时间戳升序排序`() {
        val items = DanmakuParser.parseDandanJson(sampleDandanJson)

        assertEquals(3, items.size)
        // 首条时间应为 5.0 秒 = 5000 毫秒 (TOP)
        assertEquals(5000L, items[0].timeMs)
        assertEquals("顶部弹幕注意", items[0].text)
        assertEquals(DanmakuMode.TOP, items[0].mode)
        assertEquals(16711680, items[0].color)

        // 第二条时间应为 12.5 秒 = 12500 毫秒 (SCROLL)
        assertEquals(12500L, items[1].timeMs)
        assertEquals("前方高能！", items[1].text)
        assertEquals(DanmakuMode.SCROLL, items[1].mode)

        // 第三条时间应为 20.2 秒 = 20200 毫秒 (BOTTOM)
        assertEquals(20200L, items[2].timeMs)
        assertEquals("底部弹幕提示", items[2].text)
        assertEquals(DanmakuMode.BOTTOM, items[2].mode)
    }

    @Test
    fun `DanmakuParser 截断正文必须判为解析失败而不是空列表`() {
        // 模拟 512 KB 截断：闭合括号被切掉，正文仍然"看起来像 JSON"
        val truncated = sampleDandanJson.trim().dropLast(1)

        val result = DanmakuParser.parseDandanJsonResult(truncated)

        assertTrue(result is DanmakuParseResult.Failure, "截断正文不得静默退化成空列表")
        assertTrue(
            result.reason.contains("不完整"),
            "失败原因必须能指向截断，实际=${result.reason}",
        )
    }

    @Test
    fun `DanmakuRepository 离线时请求只读缓存并允许陈旧兜底`() =
        runTest {
            val searchEpisodesJson =
                """{"animes":[{"animeId":1234,"animeTitle":"葬送的芙莉莲","episodes":[{"episodeId":9876,"episodeTitle":"第01话"}]}]}"""
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchEpisodesJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val monitor = TestNetworkMonitor().apply { setOnline(false) }
            val repo = DanmakuRepositoryImpl(pageFetchService = fake, networkMonitor = monitor)

            repo.get(title = "葬送的芙莉莲", episodeSort = 1f)

            assertTrue(fake.allHeaders.isNotEmpty())
            fake.allHeaders.forEach { headers ->
                val cacheControl = headers["Cache-Control"].orEmpty()
                assertTrue(cacheControl.contains("only-if-cached"), "离线请求必须只读缓存，实际=$cacheControl")
                assertTrue(cacheControl.contains("max-stale="), "离线请求必须允许陈旧兜底，实际=$cacheControl")
            }
        }

    @Test
    fun `DanmakuParser 正确解析标准 XML 弹幕`() {
        val items = DanmakuParser.parseXml(sampleXml)

        assertEquals(3, items.size)
        assertEquals(3200L, items[0].timeMs)
        assertEquals("顶部警告", items[0].text)
        assertEquals(DanmakuMode.TOP, items[0].mode)

        assertEquals(8500L, items[1].timeMs)
        assertEquals("名场面打卡", items[1].text)
        assertEquals(DanmakuMode.SCROLL, items[1].mode)

        assertEquals(15000L, items[2].timeMs)
        assertEquals("底部字幕", items[2].text)
        assertEquals(DanmakuMode.BOTTOM, items[2].mode)
    }

    @Test
    fun `DanmakuRepository 两段式拉取：搜索分集ID并拉取弹幕并加签`() =
        runTest {
            val searchEpisodesJson =
                """
                {
                  "hasMore": false,
                  "animes": [
                    {
                      "animeId": 1234,
                      "animeTitle": "葬送的芙莉莲",
                      "episodes": [
                        {
                          "episodeId": 9876,
                          "episodeTitle": "第01话"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()

            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchEpisodesJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val repo = repoWith(fake)

            val result =
                repo.get(
                    title = "葬送的芙莉莲",
                    episodeSort = 1f,
                    appId = "test_app",
                    appSecret = "test_secret",
                )

            assertEquals(3, result.items.size)
            assertEquals("顶部弹幕注意", result.items[0].text)
            // 分集标题与 episodeSort 对得上，自动匹配高置信
            assertEquals(DanmakuMatchState.AUTO_MATCHED, result.match?.state)
            assertEquals(9876L, result.match?.chosen?.episodeId)
            // 验证签名头已成功附加
            assertEquals("test_app", fake.lastHeaders["X-AppId"])
            assertTrue(fake.lastHeaders.containsKey("X-Timestamp"))
            assertTrue(fake.lastHeaders.containsKey("X-Signature"))
        }

    @Test
    fun `DanmakuRepository 弹幕正文必须按调用方预算请求而不是沿用页面预算`() =
        runTest {
            val searchEpisodesJson =
                """{"animes":[{"animeId":1234,"animeTitle":"葬送的芙莉莲","episodes":[{"episodeId":9876,"episodeTitle":"第01话"}]}]}"""
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchEpisodesJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )

            repoWith(fake).get(title = "葬送的芙莉莲", episodeSort = 1f)

            // 弹弹play 的 comment 接口整包返回，热门集远超 512 KB 页面预算；
            // 若沿用 MAX_PAGE_BYTES，正文会被截断成不可解析的 JSON 并被当成"这一集没有弹幕"
            val budget = fake.lastCommentMaxBytes
            assertTrue(budget != null && budget > MAX_PAGE_BYTES, "弹幕正文预算必须大于页面预算，实际=$budget")
        }

    @Test
    fun `DanmakuRepository 集数校验命中正确分集而非剧场版首集`() =
        runTest {
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to multiAnimeSearchJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val repo = repoWith(fake)

            val result = repo.get(title = "葬送的芙莉莲", episodeSort = 3f)

            // 历史盲取会命中"剧场版 正片"(列表第一个)，集数校验后应精确落在第03话
            assertEquals(DanmakuMatchState.AUTO_MATCHED, result.match?.state)
            assertEquals(9876L, result.match?.chosen?.episodeId)
            assertEquals("第03话 杀人魔", result.match?.chosen?.episodeTitle)
            assertTrue(fake.requestedUrls.last().contains("/api/v2/comment/9876"))
        }

    @Test
    fun `DanmakuRepository 无法置信匹配时回退并透出候选`() =
        runTest {
            val searchJson =
                """
                {
                  "hasMore": false,
                  "animes": [
                    {
                      "animeId": 2001,
                      "animeTitle": "某未编号番剧",
                      "episodes": [
                        { "episodeId": 4001, "episodeTitle": "正片" },
                        { "episodeId": 4002, "episodeTitle": "特典" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchJson,
                        "/api/v2/comment/4001" to sampleDandanJson,
                    ),
                )
            val repo = repoWith(fake)

            val result = repo.get(title = "某未编号番剧", episodeSort = 1f)

            assertEquals(DanmakuMatchState.FALLBACK, result.match?.state)
            assertEquals(4001L, result.match?.chosen?.episodeId)
            assertEquals(2, result.match?.candidates?.size)
        }

    @Test
    fun `DanmakuRepository 用户覆盖优先于自动匹配`() =
        runTest {
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to multiAnimeSearchJson,
                        "/api/v2/comment/3001" to sampleDandanJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val overrideDao = FakeDanmakuOverrideDao()
            val repo = repoWith(fake, overrideDao)

            // 自动匹配先命中 TV 版第01话
            val auto = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)
            assertEquals(9871L, auto.match?.chosen?.episodeId)

            // 用户纠正为剧场版"正片"并重载
            repo.saveDanmakuOverride(
                bangumiEpisodeId = 100L,
                subjectId = 1L,
                candidate =
                    DanmakuEpisodeCandidate(
                        animeId = 2001,
                        animeTitle = "剧场版 葬送的芙莉莲",
                        episodeId = 3001,
                        episodeTitle = "正片",
                    ),
            )
            val overridden = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)

            assertEquals(DanmakuMatchState.USER_OVERRIDE, overridden.match?.state)
            assertEquals(3001L, overridden.match?.chosen?.episodeId)
            assertTrue(overridden.match?.candidates?.isNotEmpty() == true)
            assertTrue(fake.requestedUrls.last().contains("/api/v2/comment/3001"))

            // 撤销覆盖后恢复自动匹配
            repo.clearDanmakuOverride(bangumiEpisodeId = 100L)
            val restored = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)
            assertEquals(DanmakuMatchState.AUTO_MATCHED, restored.match?.state)
            assertEquals(9871L, restored.match?.chosen?.episodeId)
        }

    @Test
    fun `DanmakuRepository 异常时 Fail-Open 返回空列表`() =
        runTest {
            val fake = FakeDanmakuPageFetchService(emptyMap())
            val repo = repoWith(fake)

            val result = repo.get(title = "未知番剧", episodeSort = 1f)
            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `DanmakuRepository 优先读取 SettingsRepository 配置的端点与凭据`() =
        runTest {
            val searchEpisodesJson =
                """{"animes":[{"episodes":[{"episodeId":9876}]}]}"""
            val fakeFetcher =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchEpisodesJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val fakeSettings =
                com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository(
                    initialSettings =
                        UserSettings(
                            danmakuConfig =
                                com.infinitezerone.minibgm.core.model.DanmakuConfig(
                                    endpoint = "https://custom.danmaku.net",
                                    appId = "cfg_app",
                                    appSecret = "cfg_sec",
                                ),
                        ),
                )
            val repo =
                DanmakuRepositoryImpl(
                    pageFetchService = fakeFetcher,
                    settingsRepository = fakeSettings,
                )

            val result = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)

            assertEquals(3, result.items.size)
            assertTrue(fakeFetcher.requestedUrls.first().startsWith("https://custom.danmaku.net"))
            assertEquals("cfg_app", fakeFetcher.lastHeaders["X-AppId"])
            assertTrue(fakeFetcher.lastHeaders.containsKey("X-Signature"))
        }

    @Test
    fun `DanmakuRepository 遇到 403 拦截时优雅降级并返回空列表`() =
        runTest {
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to "HTTP/1.1 403 Forbidden: Missing Authentication Headers",
                    ),
                )
            val repo = repoWith(fake)

            val result = repo.get(title = "进击的巨人", episodeSort = 1f)
            assertTrue(result.items.isEmpty())
        }

    @Test
    fun `DanmakuRepository 主标题无结果时自动回退至次要标题搜索`() =
        runTest {
            val secondarySearchJson =
                """
                {
                  "hasMore": false,
                  "animes": [
                    {
                      "animeId": 5001,
                      "animeTitle": "葬送のフリーレン",
                      "episodes": [
                        { "episodeId": 6789, "episodeTitle": "第01话" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            val primaryEncoded =
                com.infinitezerone.minibgm.core.model.PlaybackSourceRule
                    .encodeParam("芙莉莲")
            val secondaryEncoded =
                com.infinitezerone.minibgm.core.model.PlaybackSourceRule
                    .encodeParam("葬送のフリーレン")
            val fakeFetcher =
                FakeDanmakuPageFetchService(
                    mapOf(
                        // 主标题搜索返回空
                        "anime=$primaryEncoded" to """{"animes":[]}""",
                        // 次要标题搜索命中
                        "anime=$secondaryEncoded" to secondarySearchJson,
                        "/api/v2/comment/6789" to sampleDandanJson,
                    ),
                )
            val repo = repoWith(fetcher = fakeFetcher)
            val result =
                repo.getDanmaku(
                    bangumiEpisodeId = 100L,
                    subjectId = 1L,
                    title = "芙莉莲",
                    episodeSort = 1f,
                    episodeName = null,
                    secondaryTitle = "葬送のフリーレン",
                )

            assertEquals(3, result.items.size)
            assertEquals(DanmakuMatchState.AUTO_MATCHED, result.match?.state)
            assertEquals(6789L, result.match?.chosen?.episodeId)
        }

    // ---- 匹配算法：标题相似度与分集名加成 ----

    @Test
    fun `标题相似度对包含关系判定为完全匹配`() {
        val repo = repoWith(FakeDanmakuPageFetchService(emptyMap()))

        assertEquals(1.0, repo.titleSimilarity("葬送的芙莉莲", "葬送的芙莉莲"), 0.0)
        assertEquals(1.0, repo.titleSimilarity("葬送的芙莉莲", "葬送的芙莉莲 第二季"), 0.0)
        assertEquals(1.0, repo.titleSimilarity("葬送的芙莉莲 [1080p]", "葬送的芙莉莲"), 0.0)
    }

    @Test
    fun `标题相似度遇到空串直接归零`() {
        val repo = repoWith(FakeDanmakuPageFetchService(emptyMap()))

        assertEquals(0.0, repo.titleSimilarity("", "葬送的芙莉莲"), 0.0)
        assertEquals(0.0, repo.titleSimilarity("葬送的芙莉莲", "  "), 0.0)
    }

    @Test
    fun `标题相似度在无包含关系时按字符交集比例计算`() {
        val repo = repoWith(FakeDanmakuPageFetchService(emptyMap()))

        // 归一化后 {a,b,c} 与 {a,d} → 交集 1 / 最大集合 3
        assertEquals(1.0 / 3.0, repo.titleSimilarity("abc", "ad"), 1e-9)
        assertEquals(0.0, repo.titleSimilarity("abc", "xyz"), 0.0)
    }

    @Test
    fun `分集名加成仅在标题包含且名称足够长时生效`() {
        val repo = repoWith(FakeDanmakuPageFetchService(emptyMap()))

        assertEquals(0.5, repo.episodeNameBonus("第01话 结束与开始", "结束与开始"), 0.0)
        assertEquals(0.5, repo.episodeNameBonus("第 01 话 结束与开始", " 结束与开始 "), 0.0)
    }

    @Test
    fun `分集名缺失或过短时不给加成`() {
        val repo = repoWith(FakeDanmakuPageFetchService(emptyMap()))

        assertEquals(0.0, repo.episodeNameBonus("第01话 结束与开始", "不存在的分集名"), 0.0)
        assertEquals(0.0, repo.episodeNameBonus("第01话 结束与开始", null), 0.0)
        assertEquals(0.0, repo.episodeNameBonus("第01话 结束与开始", "1"), 0.0)
    }

    @Test
    fun `用户未配置端点时回退至注入的 defaultEndpoint`() =
        runTest {
            val customDefault = "https://custom-danmaku.example.com"
            val fetcher = FakeDanmakuPageFetchService(mapOf("/api/v2/search/episodes" to sampleDandanJson))
            val repo =
                DanmakuRepositoryImpl(
                    pageFetchService = fetcher,
                    defaultEndpoint = customDefault,
                )

            repo.getDanmaku(
                bangumiEpisodeId = 1L,
                subjectId = 1L,
                title = "葬送的芙莉莲",
                episodeSort = 1f,
            )

            assertTrue(fetcher.requestedUrls.isNotEmpty())
            assertTrue(fetcher.requestedUrls.first().startsWith(customDefault))
        }
}
