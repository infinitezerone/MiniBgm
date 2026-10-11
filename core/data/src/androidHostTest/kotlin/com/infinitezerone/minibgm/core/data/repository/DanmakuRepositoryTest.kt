package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.data.repository.resolver.DanmakuParser
import com.infinitezerone.minibgm.core.model.DanmakuMode
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
            val repo = DanmakuRepositoryImpl(fake)

            val items =
                repo.getDanmaku(
                    title = "葬送的芙莉莲",
                    episodeSort = 1f,
                    appId = "test_app",
                    appSecret = "test_secret",
                )

            assertEquals(3, items.size)
            assertEquals("顶部弹幕注意", items[0].text)
            // 验证签名头已成功附加
            assertEquals("test_app", fake.lastHeaders["X-AppId"])
            assertTrue(fake.lastHeaders.containsKey("X-Timestamp"))
            assertTrue(fake.lastHeaders.containsKey("X-Signature"))
        }

    @Test
    fun `DanmakuRepository 内存缓存生效`() =
        runTest {
            val searchEpisodesJson =
                """{"animes":[{"episodes":[{"episodeId":9876}]}]}"""
            val fake =
                FakeDanmakuPageFetchService(
                    mapOf(
                        "/api/v2/search/episodes" to searchEpisodesJson,
                        "/api/v2/comment/9876" to sampleDandanJson,
                    ),
                )
            val repo = DanmakuRepositoryImpl(fake)

            val first = repo.getDanmaku("葬送的芙莉莲", 1f)
            val second = repo.getDanmaku("葬送的芙莉莲", 1f)

            assertEquals(3, first.size)
            assertEquals(3, second.size)
            assertEquals(2, fake.callCount) // 第一次搜索 + 拉取 = 2 次，第二次直接走内存缓存
        }

    @Test
    fun `DanmakuRepository 异常时 Fail-Open 返回空列表`() =
        runTest {
            val fake = FakeDanmakuPageFetchService(emptyMap())
            val repo = DanmakuRepositoryImpl(fake)

            val items = repo.getDanmaku("未知番剧", 1f)
            assertTrue(items.isEmpty())
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
            val repo = DanmakuRepositoryImpl(fakeFetcher, fakeSettings)

            val items = repo.getDanmaku("葬送的芙莉莲", 1f)

            assertEquals(3, items.size)
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
            val repo = DanmakuRepositoryImpl(fake)

            val items = repo.getDanmaku("进击的巨人", 1f)
            assertTrue(items.isEmpty())
        }
}
