package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntry
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakePlaybackResolverRepository(
    private val results: List<PlayableSource> = emptyList(),
    private val templateResults: Map<String, List<PlayableSource>> = emptyMap(),
    private val hitTitle: String = "",
    private val hitResults: List<PlayableSource> = emptyList(),
) : PlaybackResolverRepository {
    val requestedPages = mutableListOf<String>()
    val requestedTemplates = mutableListOf<String>()
    val requestedTitles = mutableListOf<String>()
    val templateHeaders = mutableMapOf<String, Map<String, String>>()

    override suspend fun resolvePages(
        pageUrls: List<String>,
        epNumber: Float,
        siteName: String,
        title: String,
    ): List<PlayableSource> {
        requestedPages += pageUrls
        requestedTitles += title
        return results
    }

    override suspend fun resolveTemplate(
        url: String,
        headers: Map<String, String>,
        epNumber: Float,
        siteName: String,
        title: String,
    ): List<PlayableSource> {
        requestedTemplates += url
        requestedTitles += title
        templateHeaders[url] = headers
        if (hitTitle.isNotEmpty() && title == hitTitle) return hitResults
        return templateResults[url].orEmpty()
    }
}

class PlayableSourceToolsTest {
    private val scheduleRepository = FakeScheduleRepository()
    private val subjectRepository = FakeSubjectRepository()
    private val settingsRepository = FakeSettingsRepository()

    private fun tools(resolver: PlaybackResolverRepository) =
        PlayableSourceTools(
            scheduleRepository = scheduleRepository,
            subjectRepository = subjectRepository,
            settingsRepository = settingsRepository,
            playbackResolverRepository = resolver,
        )

    private fun decode(result: String): PlayableEpisodeList = Json { ignoreUnknownKeys = true }.decodeFromString(result)

    private fun sendScheduleWithLinks() {
        scheduleRepository.sendSchedules(
            weekday = 5,
            schedules =
                listOf(
                    AirSchedule(
                        bgmId = 1001L,
                        title = "葬送のフリーレン",
                        titleCn = "葬送的芙莉莲",
                        weekday = 5,
                        siteLinks = listOf(SiteLink("bilibili", "哔哩哔哩", "https://www.bilibili.com/bangumi/media/md99998")),
                    ),
                ),
        )
    }

    @Test
    fun `自备片单命中时直接返回可播地址与请求头且不再解析第三方页面`() =
        runTest {
            sendScheduleWithLinks()
            settingsRepository.setPlaylists(
                listOf(
                    PlaybackPlaylist(
                        id = "pl1",
                        name = "我的片单",
                        bgmSubjectId = 1001L,
                        entries =
                            listOf(
                                PlaylistEntry(
                                    label = "12",
                                    url = "https://cdn.example.com/ep12.m3u8",
                                    headers = mapOf("Referer" to "https://example.com/"),
                                ),
                            ),
                    ),
                ),
            )
            val resolver = FakePlaybackResolverRepository()

            val result = decode(tools(resolver).findPlayableSources(subjectId = 1001L, epNumber = 12))

            assertEquals("自备片单", result.source)
            assertEquals("葬送的芙莉莲", result.title)
            val episode = result.episodes.single()
            assertEquals("https://cdn.example.com/ep12.m3u8", episode.url)
            assertEquals(PlaylistEntryKind.DIRECT, episode.kind)
            assertEquals(mapOf("Referer" to "https://example.com/"), episode.headers)
            assertTrue(resolver.requestedPages.isEmpty())
        }

    @Test
    fun `片单未命中时解析排期记录的来源页取直链`() =
        runTest {
            sendScheduleWithLinks()
            val resolver =
                FakePlaybackResolverRepository(
                    results =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/ep7.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                                label = "第 7 话",
                                episodeSort = 7f,
                                pageUrl = "https://www.bilibili.com/bangumi/media/md99998",
                                headers = mapOf("Referer" to "https://www.bilibili.com/"),
                            ),
                        ),
                )

            val result = decode(tools(resolver).findPlayableSources(subjectId = 1001L, epNumber = 7))

            assertEquals(listOf("https://www.bilibili.com/bangumi/media/md99998"), resolver.requestedPages)
            assertEquals("来源站解析", result.source)
            assertEquals("https://cdn.example.com/ep7.m3u8", result.episodes.single().url)
        }

    @Test
    fun `取源规则简体搜不到时会用繁体片名重试`() =
        runTest {
            sendScheduleWithLinks()
            settingsRepository.addPlaybackRule(
                PlaybackSourceRule(
                    id = "r-search",
                    name = "示例搜索站",
                    urlTemplate = "https://search.example.tv/?q={title}",
                    kind = PlaybackRuleKind.SOURCE,
                ),
            )
            val resolver =
                FakePlaybackResolverRepository(
                    hitTitle = "葬送的芙莉蓮",
                    hitResults =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.tv/1833/38.mp4",
                                kind = PlaylistEntryKind.DIRECT,
                                label = "第 38 话",
                                episodeSort = 38f,
                            ),
                        ),
                )

            val result = decode(tools(resolver).findPlayableSources(subjectId = 1001L, epNumber = 38))

            assertEquals(listOf("葬送的芙莉莲", "葬送的芙莉蓮"), resolver.requestedTitles)
            assertEquals("第三方接口 · 示例搜索站", result.source)
            assertEquals("https://cdn.example.tv/1833/38.mp4", result.episodes.single().url)
        }

    @Test
    fun `配置了第三方取源接口时优先用它并带上规则请求头`() =
        runTest {
            sendScheduleWithLinks()
            settingsRepository.addPlaybackRule(
                PlaybackSourceRule(
                    id = "r1",
                    name = "我的接口",
                    urlTemplate = "https://api.example.com/play?id={subjectId}&ep={ep}",
                    kind = PlaybackRuleKind.SOURCE,
                    headers = mapOf("Referer" to "https://api.example.com/"),
                ),
            )
            val resolver =
                FakePlaybackResolverRepository(
                    results = listOf(PlayableSource(url = "https://cdn.example.com/from-page.m3u8")),
                    templateResults =
                        mapOf(
                            "https://api.example.com/play?id=1001&ep=7" to
                                listOf(
                                    PlayableSource(
                                        url = "https://cdn.example.com/from-api.m3u8",
                                        headers =
                                            mapOf(
                                                "Referer" to "https://api.example.com/",
                                            ),
                                    ),
                                ),
                        ),
                )

            val result = decode(tools(resolver).findPlayableSources(subjectId = 1001L, epNumber = 7))

            assertEquals("第三方接口 · 我的接口", result.source)
            assertEquals("https://cdn.example.com/from-api.m3u8", result.episodes.single().url)
            assertTrue(resolver.requestedPages.isEmpty())
        }

    @Test
    fun `没有片单也没有来源页时明确说没有结果`() =
        runTest {
            val resolver = FakePlaybackResolverRepository()

            val result = tools(resolver).findPlayableSources(subjectId = 4004L)

            assertTrue(result.contains("No playable source found"))
            assertTrue(resolver.requestedPages.isEmpty())
        }

    @Test
    fun `排期有标题但没有记录来源页时不做站内搜索兜底`() =
        runTest {
            scheduleRepository.sendSchedules(
                weekday = 5,
                schedules =
                    listOf(
                        AirSchedule(
                            bgmId = 1002L,
                            title = "测试番剧",
                            titleCn = "测试番剧",
                            weekday = 5,
                        ),
                    ),
            )
            val resolver = FakePlaybackResolverRepository()

            val result = tools(resolver).findPlayableSources(subjectId = 1002L, epNumber = 1)

            assertTrue(result.contains("No playable source found"))
            assertTrue(resolver.requestedPages.isEmpty())
        }

    @Test
    fun `只解析出页面时不算可播地址`() =
        runTest {
            sendScheduleWithLinks()
            val resolver =
                FakePlaybackResolverRepository(
                    results = listOf(PlayableSource(url = "https://player.example.net/embed/7", kind = PlaylistEntryKind.PAGE)),
                )

            val result = tools(resolver).findPlayableSources(subjectId = 1001L, epNumber = 7)

            assertTrue(result.contains("No playable address resolved"))
        }

    @Test
    fun `整表请求返回片单全部集数`() =
        runTest {
            settingsRepository.setPlaylists(
                listOf(
                    PlaybackPlaylist(
                        id = "pl1",
                        name = "我的片单",
                        bgmSubjectId = 1001L,
                        entries =
                            listOf(
                                PlaylistEntry(label = "1", url = "https://cdn.example.com/ep1.mp4"),
                                PlaylistEntry(label = "2", url = "https://cdn.example.com/ep2.mp4"),
                            ),
                    ),
                ),
            )

            val result = decode(tools(FakePlaybackResolverRepository()).findPlayableSources(subjectId = 1001L))

            assertEquals(listOf("1", "2"), result.episodes.map { it.label })
            assertEquals(listOf(1f, 2f), result.episodes.map { it.episodeSort })
        }

    @Test
    fun `非法条目号直接拒绝`() =
        runTest {
            val result = tools(FakePlaybackResolverRepository()).findPlayableSources(subjectId = 0L)

            assertTrue(result.contains("Invalid subject ID"))
        }

    @Test
    fun `用户配置的第三方动漫站点PAGE规则命中时生成可跳转播放来源`() =
        runTest {
            sendScheduleWithLinks()
            settingsRepository.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(
                        id = "r_page",
                        name = "AGE动漫",
                        urlTemplate = "https://www.agemys.org/search?query={title}",
                        kind = PlaybackRuleKind.PAGE,
                        isEnabled = true,
                    ),
                ),
            )

            val result = decode(tools(FakePlaybackResolverRepository()).findPlayableSources(subjectId = 1001L))

            assertTrue(result.source.contains("第三方动漫站点"))
            assertEquals(1, result.episodes.size)
            assertTrue(
                result.episodes
                    .first()
                    .url
                    .contains("agemys.org"),
            )
            assertEquals(PlaylistEntryKind.PAGE, result.episodes.first().kind)
        }
}
