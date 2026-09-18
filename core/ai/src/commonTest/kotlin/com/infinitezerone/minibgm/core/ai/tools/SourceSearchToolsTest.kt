package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class SourceSearchToolsTest {
    private val scheduleRepository = FakeScheduleRepository()
    private val subjectRepository = FakeSubjectRepository()
    private val tools = SourceSearchTools(scheduleRepository, subjectRepository)

    private fun decodePages(result: String): List<WatchPageDto> = Json { ignoreUnknownKeys = true }.decodeFromString(result)

    private fun subject(
        id: Long,
        nameCn: String,
    ) = Subject(id = id, name = "Subject $id", nameCn = nameCn)

    @Test
    fun `findWatchPages 返回条目页与已收录来源站页面`() =
        runTest {
            scheduleRepository.sendSchedules(
                weekday = 5,
                schedules =
                    listOf(
                        AirSchedule(
                            bgmId = 1001L,
                            title = "葬送のフリーレン",
                            titleCn = "葬送的芙莉莲",
                            weekday = 5,
                            siteLinks =
                                listOf(
                                    SiteLink("bilibili", "哔哩哔哩", "https://www.bilibili.com/bangumi/media/md99998"),
                                    SiteLink("mikan", "蜜柑计划", "https://mikanani.me/Home/Bangumi/3456"),
                                ),
                        ),
                    ),
            )

            val pages = decodePages(tools.findWatchPages(subjectId = 1001L))

            assertTrue(pages.any { it.url == "https://bgm.tv/subject/1001" })
            assertTrue(pages.any { it.url == "https://www.bilibili.com/bangumi/media/md99998" })
            assertTrue(pages.any { it.url == "https://mikanani.me/Home/Bangumi/3456" })
            // 已收录来源站时不再追加搜索页兜底
            assertTrue(pages.none { it.url.contains("search.bilibili.com") })
            assertTrue(pages.none { it.url.contains("mikanani.me/Home/Search") })
        }

    @Test
    fun `findWatchPages 话数命中时给出分集页与选话提示`() =
        runTest {
            scheduleRepository.sendSchedules(
                weekday = 3,
                schedules =
                    listOf(
                        AirSchedule(
                            bgmId = 2002L,
                            title = "SPY×FAMILY",
                            titleCn = "间谍过家家",
                            weekday = 3,
                            siteLinks = listOf(SiteLink("bilibili", "哔哩哔哩", "https://www.bilibili.com/bangumi/media/md12345")),
                        ),
                    ),
            )

            val pages = decodePages(tools.findWatchPages(subjectId = 2002L, epNumber = 7))

            assertTrue(pages.any { it.url == "https://bgm.tv/subject/2002/ep" })
            assertTrue(pages.any { it.note.contains("第 7 话") })
        }

    @Test
    fun `findWatchPages 无收录来源时回退到站点搜索页`() =
        runTest {
            subjectRepository.sendSubject(subject(id = 3003L, nameCn = "四叠叠时光机"))

            val pages = decodePages(tools.findWatchPages(subjectId = 3003L, epNumber = 3))

            val bilibili = pages.first { it.url.contains("search.bilibili.com") }
            assertTrue(bilibili.url.contains("keyword="))
            assertTrue(pages.any { it.url.contains("mikanani.me/Home/Search") })
            // 只返回页面链接：不出现常见媒体直链后缀
            assertTrue(pages.none { it.url.endsWith(".m3u8") || it.url.endsWith(".mp4") })
        }

    @Test
    fun `findWatchPages 标题未知时仍返回条目页`() =
        runTest {
            val pages = decodePages(tools.findWatchPages(subjectId = 4004L))

            assertTrue(pages.all { it.url.startsWith("https://bgm.tv") })
            assertTrue(pages.size == 1)
        }

    @Test
    fun `findWatchPages 拒绝非法条目号`() =
        runTest {
            assertTrue(tools.findWatchPages(subjectId = 0L).contains("Invalid subject ID"))
            assertTrue(tools.findWatchPages(subjectId = -7L).contains("Invalid subject ID"))
        }
}
