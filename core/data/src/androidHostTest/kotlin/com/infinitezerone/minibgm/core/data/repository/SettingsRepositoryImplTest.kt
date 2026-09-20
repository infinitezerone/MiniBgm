package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.CommunitySubscriptionService
import com.infinitezerone.minibgm.core.testing.datastore.createTestUserPreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsRepositoryImplTest {
    private class FakeCommunitySubscriptionService : CommunitySubscriptionService {
        var result =
            listOf(
                DiscoveredSource(
                    name = "示例动漫源",
                    urlTemplate = "https://example.com/search?q={title}",
                    description = "优质二次元",
                    latencyMs = 120L,
                    isAlive = true,
                ),
            )

        override suspend fun searchSubscriptions(
            keywords: String,
        ): List<com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate> =
            listOf(
                com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate(
                    name = "示例订阅",
                    subscriptionUrl = "https://example.com/rules.json",
                    sourceCount = 1,
                    aliveCount = 1,
                ),
            )

        override suspend fun validateAndTestSubscription(url: String): com.infinitezerone.minibgm.core.model.SubscriptionValidationReport =
            com.infinitezerone.minibgm.core.model.SubscriptionValidationReport(
                isHealthy = true,
                subscriptionUrl = url,
                totalRules = 1,
                aliveRules = 1,
                sources = result,
            )

        override suspend fun fetchAndTestCommunitySources(customSubscriptionUrl: String?): List<DiscoveredSource> = result
    }

    @Test
    fun discoverCommunityPlaybackSources_delegatesToService() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val fakeDataStore = createTestUserPreferencesDataSource()
            val repo = SettingsRepositoryImpl(fakeDataStore, fakeService)

            val result = repo.discoverCommunityPlaybackSources()

            assertIs<AppResult.Success<List<DiscoveredSource>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("示例动漫源", result.data.first().name)
        }

    @Test
    fun searchCommunitySubscriptions_delegatesToService() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val fakeDataStore = createTestUserPreferencesDataSource()
            val repo = SettingsRepositoryImpl(fakeDataStore, fakeService)

            val result = repo.searchCommunitySubscriptions("test")

            assertIs<AppResult.Success<List<com.infinitezerone.minibgm.core.model.DiscoveredSubscriptionCandidate>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("示例订阅", result.data.first().name)
        }

    @Test
    fun validateAndTestSubscription_delegatesToService() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val fakeDataStore = createTestUserPreferencesDataSource()
            val repo = SettingsRepositoryImpl(fakeDataStore, fakeService)

            val result = repo.validateAndTestSubscription("https://example.com/rules.json")

            assertIs<AppResult.Success<com.infinitezerone.minibgm.core.model.SubscriptionValidationReport>>(result)
            assertTrue(result.data.isHealthy)
            assertEquals(1, result.data.aliveRules)
        }

    @Test
    fun importPlaybackRules_mergesNewRulesWithoutDuplication() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val fakeDataStore = createTestUserPreferencesDataSource()
            val repo = SettingsRepositoryImpl(fakeDataStore, fakeService)

            val rule1 = PlaybackSourceRule(id = "1", name = "规则1", urlTemplate = "https://a.com/{title}")
            val rule2 = PlaybackSourceRule(id = "2", name = "规则2", urlTemplate = "https://b.com/{title}")

            repo.importPlaybackRules(listOf(rule1))
            val rulesAfterFirstImport = repo.playbackRules.first()
            assertEquals(1, rulesAfterFirstImport.size)

            // 重复导入 rule1，并追加 rule2
            repo.importPlaybackRules(listOf(rule1, rule2))
            val rulesAfterSecondImport = repo.playbackRules.first()
            assertEquals(2, rulesAfterSecondImport.size)
            assertTrue(rulesAfterSecondImport.any { it.id == "1" })
            assertTrue(rulesAfterSecondImport.any { it.id == "2" })
        }
}
