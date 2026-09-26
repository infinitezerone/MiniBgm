package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeStreamResolverTest {
    private fun sourceRule(urlTemplate: String = "https://api.example.tv/search?wd={title}") =
        PlaybackSourceRule(
            id = "r1",
            name = "示例接口",
            urlTemplate = urlTemplate,
            kind = PlaybackRuleKind.SOURCE,
        )

    private fun direct(url: String) = PlayableSource(url = url, kind = PlaylistEntryKind.DIRECT)

    @Test
    fun buildCandidates_sourcePutsPlainTitlesBeforeEpisodeSuffixes() {
        val candidates =
            EpisodeStreamResolver.buildRuleQueryCandidates(
                rule = sourceRule(),
                baseTitles = listOf("芙莉莲", "葬送的芙莉蓮"),
                epSort = 7f,
            )

        assertEquals(
            listOf("芙莉莲", "葬送的芙莉蓮", "芙莉莲 07", "芙莉莲 7", "葬送的芙莉蓮 07", "葬送的芙莉蓮 7"),
            candidates,
        )
    }

    @Test
    fun buildCandidates_templateWithEpPlaceholderSkipsEpisodeSuffixes() {
        val rule =
            PlaybackSourceRule(
                id = "r2",
                name = "示例网页",
                urlTemplate = "https://site.example.com/{title}/{ep}",
                kind = PlaybackRuleKind.PAGE,
            )
        val candidates =
            EpisodeStreamResolver.buildRuleQueryCandidates(
                rule = rule,
                baseTitles = listOf("芙莉莲"),
                epSort = 7f,
            )

        // 模板含 {ep}：集号由 URL 承载，不再生成「标题 集号」关键词组合
        assertEquals(listOf("芙莉莲"), candidates)
    }

    @Test
    fun buildCandidates_zeroEpSortSkipsEpisodeSuffixes() {
        val candidates =
            EpisodeStreamResolver.buildRuleQueryCandidates(
                rule = sourceRule(),
                baseTitles = listOf("芙莉莲"),
                epSort = 0f,
            )

        assertEquals(listOf("芙莉莲"), candidates)
    }

    @Test
    fun probeRule_stopsAtFirstCandidateWithDirectSource() =
        runTest {
            val stub =
                object : PlaybackResolverRepository {
                    val titles = mutableListOf<String>()

                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> {
                        titles += title
                        return if (title == "繁体") listOf(direct("https://cdn.example.tv/ok.m3u8")) else emptyList()
                    }
                }

            val outcome =
                EpisodeStreamResolver(stub).probeRule(
                    rule = sourceRule(),
                    baseTitles = listOf("简体", "繁体", "别名", "原名"),
                    epSort = 0f,
                    subjectId = 1001L,
                )

            assertNotNull(outcome)
            assertTrue(outcome.sources.isNotEmpty())
            assertEquals("繁体", outcome.matchedTitle)
            assertEquals(2, outcome.attempted)
            assertEquals(4, outcome.attemptTotal)
            assertEquals(false, outcome.timedOut)
            assertEquals("https://cdn.example.tv/ok.m3u8", outcome.directSource?.url)
        }

    @Test
    fun probeRule_reportsExhaustedWhenOnlyPageKindResults() =
        runTest {
            val stub =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = listOf(PlayableSource(url = "https://site.example.com/play", kind = PlaylistEntryKind.PAGE))
                }

            val outcome =
                EpisodeStreamResolver(stub).probeRule(
                    rule = sourceRule(),
                    baseTitles = listOf("芙莉莲"),
                    epSort = 1f,
                    subjectId = 1001L,
                )

            // 只有 PAGE 结果不算命中：继续试完所有候选（纯标题 + 集号组合共 3 个）后如实返回未命中
            assertNotNull(outcome)
            assertTrue(outcome.sources.isEmpty())
            assertEquals(3, outcome.attempted)
            assertEquals(3, outcome.attemptTotal)
            assertNull(outcome.directSource)
        }

    @Test
    fun probeRule_timesOutWhenRequestsHang() =
        runTest {
            val stub =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> {
                        delay(60_000)
                        return emptyList()
                    }
                }

            val progress = mutableListOf<Pair<Int, Int>>()
            val outcome =
                EpisodeStreamResolver(stub).probeRule(
                    rule = sourceRule(),
                    baseTitles = listOf("a", "b", "c"),
                    epSort = 0f,
                    subjectId = 1001L,
                    timeoutMs = 250L,
                    onProgress = { attempted, total -> progress += attempted to total },
                )

            assertNull(outcome)
            assertTrue(progress.isNotEmpty())
        }
}
