package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.tool.BgmToolRegistry
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolRegistryIntegrationTest {
    @Test
    fun toolRegistry_registers_all_tools_from_toolsets() {
        val scheduleTools = ScheduleTools(FakeScheduleRepository())
        val subjectTools = SubjectTools(FakeSearchRepository(), FakeSubjectRepository())
        val collectionTools = CollectionTools(FakeCollectionRepository())
        val playableSourceTools =
            PlayableSourceTools(
                scheduleRepository = FakeScheduleRepository(),
                subjectRepository = FakeSubjectRepository(),
                settingsRepository = FakeSettingsRepository(),
                playbackResolverRepository =
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
                        ): List<PlayableSource> = emptyList()
                    },
            )

        val communityTools =
            CommunityTools(
                settingsRepository = FakeSettingsRepository(),
            )

        val diagnosticsTools =
            PlaybackRuleDiagnosticsTools(
                playbackResolverRepository =
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
                        ): List<PlayableSource> = emptyList()
                    },
            )

        val registry =
            BgmToolRegistry(
                listOf(
                    scheduleTools.tools(),
                    subjectTools.tools(),
                    collectionTools.tools(),
                    playableSourceTools.tools(),
                    communityTools.tools(),
                    diagnosticsTools.tools(),
                ).flatten(),
            )

        val toolNames = registry.tools.map { it.name }
        // Schedule tools
        assertTrue(toolNames.contains("getSchedule"))
        assertTrue(toolNames.contains("getNextEpisodeAiring"))

        // Subject tools
        assertTrue(toolNames.contains("searchAnime"))
        assertTrue(toolNames.contains("getSubjectDetail"))
        assertTrue(toolNames.contains("getSubjectEpisodes"))

        // Collection tools
        assertTrue(toolNames.contains("getCollection"))
        assertTrue(toolNames.contains("getWatchingList"))
        assertTrue(toolNames.contains("proposeUpdateCollection"))
        assertTrue(toolNames.contains("proposeUpdateEpisodeProgress"))

        val getScheduleTool = registry.getTool("getSchedule")
        assertNotNull(getScheduleTool)
        assertTrue(getScheduleTool.description.contains("broadcast schedule"))

        // Community tools：只允许"验证用户给出的地址"，不得存在任何自行检索社区的入口
        assertTrue(toolNames.contains("validateAndTestSubscription"))
        assertFalse(
            toolNames.contains("searchCommunitySubscriptions"),
            "模型不得自行检索社区源（目录属社区、机制属 App）",
        )
        assertFalse(
            toolNames.contains("discoverCommunityPlaybackSources"),
            "不得存在无参动态发现入口，缺地址时只能向用户索要",
        )

        // Diagnostics tools
        assertTrue(toolNames.contains("inspectPageStructure"))
        assertTrue(toolNames.contains("testPlaybackRule"))
        assertTrue(toolNames.contains("proposePlaybackRule"))
        assertTrue(toolNames.contains("probeSiteAndFindSample"))
        assertTrue(toolNames.contains("traceNetworkTraffic"))
        assertTrue(toolNames.contains("recordPlaybackRuleFromStaticPage"))

        // 找源工具：描述必须写明"返回结构化可播数据、且模型只能转述工具结果"
        assertTrue(toolNames.contains("findPlayableSources"))
        val playableTool = registry.getTool("findPlayableSources")
        assertNotNull(playableTool)
        assertTrue(playableTool.description.contains("structured playback data"))
        assertTrue(playableTool.description.contains("never invent"))
    }
}
