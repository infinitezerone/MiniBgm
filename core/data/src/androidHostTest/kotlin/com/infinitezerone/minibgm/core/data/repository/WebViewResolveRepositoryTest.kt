package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebViewResolveRepositoryTest {
    private class FakeCaptureService(
        private val results: Map<String, List<PlayableSource>>,
    ) : WebViewCaptureService {
        val requested = mutableListOf<String>()

        override suspend fun capturePlayableSources(pageUrl: String): List<PlayableSource> {
            requested += pageUrl
            return results[pageUrl].orEmpty()
        }
    }

    private fun source(url: String) = PlayableSource(url = url)

    @Test
    fun `按站点优先级逐页捕获并命中即停`() =
        runTest {
            val scheduleRepository =
                FakeScheduleRepository().apply {
                    sendSchedules(
                        weekday = 5,
                        schedules =
                            listOf(
                                AirSchedule(
                                    bgmId = 1001L,
                                    title = "测试番剧",
                                    titleCn = "测试番剧",
                                    siteLinks =
                                        listOf(
                                            SiteLink("bilibili", "哔哩哔哩", "https://www.bilibili.com/bangumi/md1"),
                                            SiteLink("gamer", "巴哈", "https://ani.gamer.com.tw/anime1"),
                                        ),
                                ),
                            ),
                    )
                }
            val capture =
                FakeCaptureService(
                    mapOf("https://ani.gamer.com.tw/anime1" to listOf(source("https://cdn.gamer.com.tw/ep1.m3u8"))),
                )
            val repo = WebViewResolveRepositoryImpl(scheduleRepository = scheduleRepository, captureService = capture)

            val result = repo.deepResolve(1001L)

            assertTrue(result is AppResult.Success)
            assertEquals(listOf("https://cdn.gamer.com.tw/ep1.m3u8"), (result as AppResult.Success).data.map { it.url })
            // 第一页（哔哩哔哩）无结果，捕获到第二页即停
            assertEquals(2, capture.requested.size)
        }

    @Test
    fun `无记录来源页时回退到标题搜索页`() =
        runTest {
            val scheduleRepository =
                FakeScheduleRepository().apply {
                    sendSchedules(
                        weekday = 5,
                        schedules = listOf(AirSchedule(bgmId = 1002L, title = "测试番剧", titleCn = "测试番剧")),
                    )
                }
            val capture = FakeCaptureService(emptyMap())
            val repo = WebViewResolveRepositoryImpl(scheduleRepository = scheduleRepository, captureService = capture)

            repo.deepResolve(1002L)

            assertEquals(1, capture.requested.size)
            assertTrue(capture.requested.single().startsWith("https://search.bilibili.com"))
        }

    @Test
    fun `全部候选页无结果时返回空`() =
        runTest {
            val scheduleRepository =
                FakeScheduleRepository().apply {
                    sendSchedules(
                        weekday = 5,
                        schedules = listOf(AirSchedule(bgmId = 1003L, title = "测试番剧", titleCn = "测试番剧")),
                    )
                }
            val capture = FakeCaptureService(emptyMap())
            val repo = WebViewResolveRepositoryImpl(scheduleRepository = scheduleRepository, captureService = capture)

            val result = repo.deepResolve(1003L)

            assertTrue(result is AppResult.Success)
            assertTrue((result as AppResult.Success).data.isEmpty())
        }
}
