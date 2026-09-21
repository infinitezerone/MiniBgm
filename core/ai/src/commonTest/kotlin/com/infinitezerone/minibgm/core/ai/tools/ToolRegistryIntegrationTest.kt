package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.ToolRegistry
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlin.test.Test
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
                        ): List<PlayableSource> = emptyList()

                        override suspend fun resolveTemplate(
                            url: String,
                            headers: Map<String, String>,
                            epNumber: Float,
                            siteName: String,
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
                        ): List<PlayableSource> = emptyList()

                        override suspend fun resolveTemplate(
                            url: String,
                            headers: Map<String, String>,
                            epNumber: Float,
                            siteName: String,
                        ): List<PlayableSource> = emptyList()
                    },
            )

        val registry =
            ToolRegistry {
                tools(scheduleTools)
                tools(subjectTools)
                tools(collectionTools)
                tools(playableSourceTools)
                tools(communityTools)
                tools(diagnosticsTools)
            }

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

        val getScheduleDescriptor = registry.getTool("getSchedule").descriptor
        assertNotNull(getScheduleDescriptor)
        assertTrue(getScheduleDescriptor.description.contains("broadcast schedule"))

        // Community tools
        assertTrue(toolNames.contains("searchCommunitySubscriptions"))
        assertTrue(toolNames.contains("validateAndTestSubscription"))
        assertTrue(toolNames.contains("discoverCommunityPlaybackSources"))

        // Diagnostics tools
        assertTrue(toolNames.contains("inspectPageStructure"))
        assertTrue(toolNames.contains("testPlaybackRule"))
        assertTrue(toolNames.contains("proposePlaybackRule"))
        assertTrue(toolNames.contains("probeSiteAndFindSample"))
        assertTrue(toolNames.contains("traceNetworkTraffic"))

        // 找源工具：描述必须写明"返回结构化可播数据、且模型只能转述工具结果"
        assertTrue(toolNames.contains("findPlayableSources"))
        val playableDescriptor = registry.getTool("findPlayableSources").descriptor
        assertNotNull(playableDescriptor)
        assertTrue(playableDescriptor.description.contains("structured playback data"))
        assertTrue(playableDescriptor.description.contains("never invent"))
    }
}
