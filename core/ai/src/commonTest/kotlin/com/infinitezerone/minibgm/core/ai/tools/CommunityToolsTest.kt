package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommunityToolsTest {
    private val fakeSettingsRepository = FakeSettingsRepository()
    private val pendingActionStore = PendingActionStore()
    private val communityTools =
        CommunityTools(
            settingsRepository = fakeSettingsRepository,
            pendingActionStore = pendingActionStore,
        )

    @Test
    fun discoverCommunityPlaybackSources_returns_message_when_sources_empty() =
        runTest {
            fakeSettingsRepository.communityDiscoveryResult = AppResult.Success(emptyList())

            val result = communityTools.discoverCommunityPlaybackSources()

            assertEquals("No community anime playback sources found or all endpoints are unreachable.", result)
            assertTrue(pendingActionStore.actions.value.isEmpty())
        }

    @Test
    fun discoverCommunityPlaybackSources_creates_proposal_when_sources_available() =
        runTest {
            val sources =
                listOf(
                    DiscoveredSource(
                        name = "AGE动漫",
                        urlTemplate = "https://agefans.com/search?q={title}",
                        isAlive = true,
                    ),
                    DiscoveredSource(
                        name = "樱花动漫",
                        urlTemplate = "https://yhdm.tv/search?q={title}",
                        isAlive = false,
                    ),
                )
            fakeSettingsRepository.communityDiscoveryResult = AppResult.Success(sources)

            val result = communityTools.discoverCommunityPlaybackSources()

            assertTrue(result.contains("import_playback_rules"))
            assertTrue(result.contains("AGE动漫"))

            val actions = pendingActionStore.actions.value
            assertEquals(1, actions.size)
            val action = actions.first()
            assertIs<PendingAction.ImportPlaybackRules>(action)
            assertEquals("社区二次元播放源", action.sourceName)
            assertEquals(1, action.rules.size)
            assertEquals("AGE动漫", action.rules.first().name)
        }

    @Test
    fun discoverCommunityPlaybackSources_handles_error_result() =
        runTest {
            fakeSettingsRepository.communityDiscoveryResult =
                AppResult.Error(IllegalStateException("Network timeout"))

            val result = communityTools.discoverCommunityPlaybackSources()

            assertTrue(result.contains("Failed to discover community playback sources: Network timeout"))
        }

    @Test
    fun discoverCommunityPlaybackSources_handles_loading_result() =
        runTest {
            fakeSettingsRepository.communityDiscoveryResult = AppResult.Loading

            val result = communityTools.discoverCommunityPlaybackSources()

            assertEquals("Community sources discovery in progress...", result)
        }
}
