package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.data.cache.DanmakuDiskCache
import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.database.dao.DanmakuOverrideDao
import com.infinitezerone.minibgm.core.database.entity.DanmakuEpisodeOverrideEntity
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.core.model.DanmakuMode
import com.infinitezerone.minibgm.core.model.DanmakuResult
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
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

    override suspend fun fetchHtml(
        url: String,
        requestHeaders: Map<String, String>,
    ): FetchedPage? {
        callCount++
        requestedUrls += url
        lastHeaders.clear()
        lastHeaders.putAll(requestHeaders)
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

private class FakeDanmakuDiskCache : DanmakuDiskCache {
    val store = mutableMapOf<String, List<DanmakuItem>>()
    var getCallCount = 0
    var putCallCount = 0

    override suspend fun get(cacheKey: String): List<DanmakuItem>? {
        getCallCount++
        return store[cacheKey]
    }

    override suspend fun put(
        cacheKey: String,
        items: List<DanmakuItem>,
    ) {
        putCallCount++
        store[cacheKey] = items
    }

    override suspend fun clear() {
        store.clear()
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
        danmakuDiskCache: DanmakuDiskCache? = null,
    ): DanmakuRepositoryImpl =
        DanmakuRepositoryImpl(
            pageFetchService = fetcher,
            danmakuOverrideDao = overrideDao,
            danmakuDiskCache = danmakuDiskCache,
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
    fun `DanmakuRepository 内存缓存生效且覆盖切换不串缓存`() =
        runTest {
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to multiAnimeSearchJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                        "/api/v2/comment/9871" to sampleDandanJson,
                    ),
                )
            val overrideDao = FakeDanmakuOverrideDao()
            val repo = repoWith(fake, overrideDao)

            val first = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)
            val second = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)
            assertEquals(3, first.items.size)
            assertEquals(3, second.items.size)
            // 弹幕正文按已解析分集 id 缓存：第二次只重复搜索，正文直接命中内存
            assertEquals(3, fake.callCount)

            // 切换覆盖 → 新分集新缓存键，不会串到旧分集的弹幕
            repo.saveDanmakuOverride(
                bangumiEpisodeId = 100L,
                subjectId = 1L,
                candidate =
                    DanmakuEpisodeCandidate(
                        animeId = 2002,
                        animeTitle = "葬送的芙莉莲",
                        episodeId = 9876,
                        episodeTitle = "第03话 杀人魔",
                    ),
            )
            val switched = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)
            assertEquals(9876L, switched.match?.chosen?.episodeId)
            assertTrue(fake.requestedUrls.last().contains("/api/v2/comment/9876"))
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
    fun `DanmakuRepository 磁盘缓存命中时跳过分集弹幕拉取直接返回`() =
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
                        { "episodeId": 9876, "episodeTitle": "第01话" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            val fakeFetcher =
                FakeDanmakuPageFetchService(
                    mapOf("/api/v2/search/episodes" to searchEpisodesJson),
                )
            val fakeDiskCache = FakeDanmakuDiskCache()
            val cachedDanmaku =
                listOf(
                    DanmakuItem(timeMs = 12000L, text = "磁盘缓存弹幕", mode = DanmakuMode.SCROLL, color = 0xFFFFFF),
                )
            fakeDiskCache.put("https://api.dandanplay.net_ep9876", cachedDanmaku)

            val repo = repoWith(fetcher = fakeFetcher, danmakuDiskCache = fakeDiskCache)
            val result = repo.get(title = "葬送的芙莉莲", episodeSort = 1f)

            assertEquals(1, result.items.size)
            assertEquals("磁盘缓存弹幕", result.items[0].text)
            // 验证未向网络请求 /api/v2/comment/9876
            assertTrue(fakeFetcher.requestedUrls.none { it.contains("/api/v2/comment") })
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
