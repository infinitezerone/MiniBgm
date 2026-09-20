package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.SubscriptionValidationReport
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
    fun searchCommunitySubscriptions_returns_message_when_candidates_empty() =
        runTest {
            fakeSettingsRepository.communitySearchResult = AppResult.Success(emptyList())

            val result = communityTools.searchCommunitySubscriptions("test")

            assertTrue(result.contains("No verified community subscriptions found"))
        }

    @Test
    fun searchCommunitySubscriptions_returns_formatted_list_when_candidates_exist() =
        runTest {
            fakeSettingsRepository.communitySearchResult =
                AppResult.Success(
                    listOf(
                        DiscoveredSubscriptionCandidate(
                            name = "bangumi-rules",
                            subscriptionUrl = "https://example.com/rules.json",
                            description = "开源番剧规则",
                            sourceCount = 3,
                            aliveCount = 2,
                            averageLatencyMs = 150L,
                            sampleSources = listOf("源A", "源B"),
                        ),
                    ),
                )

            val result = communityTools.searchCommunitySubscriptions("bangumi-rules")

            assertTrue(result.contains("Found 1 verified subscription candidates"))
            assertTrue(result.contains("bangumi-rules"))
            assertTrue(result.contains("Live rules: 2/3"))
            assertTrue(result.contains("Avg latency: 150ms"))
        }

    @Test
    fun validateAndTestSubscription_creates_proposal_when_healthy() =
        runTest {
            val report =
                SubscriptionValidationReport(
                    isHealthy = true,
                    subscriptionUrl = "https://example.com/rules.json",
                    totalRules = 2,
                    aliveRules = 1,
                    averageLatencyMs = 120L,
                    sources =
                        listOf(
                            DiscoveredSource(
                                name = "示例动漫源A",
                                urlTemplate = "https://example-a.org/search?q={title}",
                                isAlive = true,
                                latencyMs = 120L,
                            ),
                            DiscoveredSource(
                                name = "示例动漫源B",
                                urlTemplate = "https://example-b.org/search?q={title}",
                                isAlive = false,
                                latencyMs = 9999L,
                            ),
                        ),
                )
            fakeSettingsRepository.validationReportResult = AppResult.Success(report)

            val result = communityTools.validateAndTestSubscription("https://example.com/rules.json")

            assertTrue(result.contains("import_playback_rules"))
            assertTrue(result.contains("示例动漫源A"))

            val actions = pendingActionStore.actions.value
            assertEquals(1, actions.size)
            val action = actions.first()
            assertIs<PendingAction.ImportPlaybackRules>(action)
            assertEquals(1, action.rules.size)
            assertEquals("示例动漫源A", action.rules.first().name)
        }

    @Test
    fun validateAndTestSubscription_returns_failure_when_unhealthy() =
        runTest {
            val report =
                SubscriptionValidationReport(
                    isHealthy = false,
                    subscriptionUrl = "https://broken.com/rules.json",
                    errorMessage = "HTTP 404 Not Found",
                )
            fakeSettingsRepository.validationReportResult = AppResult.Success(report)

            val result = communityTools.validateAndTestSubscription("https://broken.com/rules.json")

            assertTrue(result.contains("Subscription validation failed: HTTP 404 Not Found"))
            assertTrue(pendingActionStore.actions.value.isEmpty())
        }

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
                        name = "示例动漫源A",
                        urlTemplate = "https://example-a.org/search?q={title}",
                        isAlive = true,
                    ),
                    DiscoveredSource(
                        name = "示例动漫源B",
                        urlTemplate = "https://example-b.org/search?q={title}",
                        isAlive = false,
                    ),
                )
            fakeSettingsRepository.communityDiscoveryResult = AppResult.Success(sources)

            val result = communityTools.discoverCommunityPlaybackSources()

            assertTrue(result.contains("import_playback_rules"))
            assertTrue(result.contains("示例动漫源A"))

            val actions = pendingActionStore.actions.value
            assertEquals(1, actions.size)
            val action = actions.first()
            assertIs<PendingAction.ImportPlaybackRules>(action)
            assertEquals("社区二次元播放源", action.sourceName)
            assertEquals(1, action.rules.size)
            assertEquals("示例动漫源A", action.rules.first().name)
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
