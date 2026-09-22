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

        override suspend fun validateAndTestSubscription(url: String): com.infinitezerone.minibgm.core.model.SubscriptionValidationReport =
            com.infinitezerone.minibgm.core.model.SubscriptionValidationReport(
                isHealthy = true,
                subscriptionUrl = url,
                totalRules = 1,
                aliveRules = 1,
                sources = result,
            )
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

    @Test
    fun aiConfigProfiles_roundTrip_saveActivateDelete() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val repo = SettingsRepositoryImpl(createTestUserPreferencesDataSource(), fakeService)

            val configA =
                com.infinitezerone.minibgm.core.model.AiConfig(
                    endpoint = "https://a.example.com/v1",
                    apiKey = "ka",
                    model = "ma",
                    provider = com.infinitezerone.minibgm.core.model.AiConfig.PROVIDER_CUSTOM,
                )
            val configB = configA.copy(endpoint = "https://b.example.com/v1", apiKey = "kb", model = "mb")
            val profileA =
                com.infinitezerone.minibgm.core.model
                    .AiConfigProfile(id = "pa", name = "A", config = configA)
            val profileB =
                com.infinitezerone.minibgm.core.model
                    .AiConfigProfile(id = "pb", name = "B", config = configB)

            repo.saveAiConfigProfile(profileA)
            repo.saveAiConfigProfile(profileB)
            // 同 id 覆盖
            repo.saveAiConfigProfile(profileA.copy(name = "A2"))

            val profiles = repo.aiConfigProfiles.first()
            assertEquals(2, profiles.size)
            assertEquals("A2", profiles.first { it.id == "pa" }.name)

            // 启用 B：生效配置切换 + 标记更新
            repo.activateAiConfigProfile("pb")
            assertEquals(configB, repo.aiConfig.first())
            assertEquals("pb", repo.activeAiProfileId.first())

            // 删除启用中的方案：清除标记但生效配置保留
            repo.deleteAiConfigProfile("pb")
            assertEquals(1, repo.aiConfigProfiles.first().size)
            assertEquals("", repo.activeAiProfileId.first())
            assertEquals(configB, repo.aiConfig.first())

            // 未知 id 启用是 no-op
            repo.activateAiConfigProfile("不存在")
            assertEquals("", repo.activeAiProfileId.first())
        }
}
