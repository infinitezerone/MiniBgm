package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.network.CommunitySubscriptionService
import com.infinitezerone.minibgm.core.testing.datastore.createTestUserPreferencesDataSource
import com.infinitezerone.minibgm.core.testing.repository.FakeSecureSecretStore
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

        override suspend fun validateAndTestSubscription(
            target: String,
        ): com.infinitezerone.minibgm.core.model.SubscriptionValidationReport =
            com.infinitezerone.minibgm.core.model.SubscriptionValidationReport(
                isHealthy = true,
                subscriptionUrl = target,
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
            val repo = SettingsRepositoryImpl(fakeDataStore, FakeSecureSecretStore(), fakeService)

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
            val repo = SettingsRepositoryImpl(fakeDataStore, FakeSecureSecretStore(), fakeService)

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
    fun importPlaybackRules_sameSourceWithDifferentIds_staysSingleEntry() =
        runTest {
            val repo =
                SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), FakeCommunitySubscriptionService())
            // 同一订阅来源：对话框导入曾经给随机 UUID，刷新给 sub_<hash>，两条 id 不同也必须落成一条
            val fromDialogImport = PlaybackSourceRule(id = "uuid-1", name = "量子资源", urlTemplate = "https://a.com/{title}")
            val fromRefresh = PlaybackSourceRule(id = "sub_abc", name = "量子资源", urlTemplate = "https://a.com/{title}")

            repo.importPlaybackRules(listOf(fromDialogImport))
            repo.importPlaybackRules(listOf(fromRefresh))

            val rules = repo.playbackRules.first()
            assertEquals(1, rules.size)
            assertEquals("uuid-1", rules.single().id)
        }

    @Test
    fun importRulesFromJson_validArray_parsesAndPersistsOnlyImportableRules() =
        runTest {
            val repo =
                SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), FakeCommunitySubscriptionService())

            val validJson =
                """
                [
                    {"id": "imported-1", "name": "BimiBimi", "urlTemplate": "https://bimibimi.net/search/{title}", "isEnabled": true}
                ]
                """.trimIndent()
            val result = repo.importRulesFromJson(validJson)

            val summary = assertIs<AppResult.Success<RuleImportSummary>>(result).data
            assertEquals(1, summary.acceptedCount)
            assertEquals(0, summary.rejectedCount)
            val rules = repo.playbackRules.first()
            assertEquals(1, rules.size)
            assertEquals("BimiBimi", rules.single().name)
        }

    @Test
    fun importRulesFromJson_malformedJson_returnsFixedParseErrorAndPersistsNothing() =
        runTest {
            val repo =
                SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), FakeCommunitySubscriptionService())

            val result = repo.importRulesFromJson("invalid json string {}")

            // 文案与旧 ViewModel 实现逐字一致，且不携带英文反序列化异常原文
            assertEquals("规则解析失败，请检查 JSON 格式", assertIs<AppResult.Error>(result).message)
            assertTrue(repo.playbackRules.first().isEmpty())
        }

    @Test
    fun refreshPlaybackSubscription_importsAllCandidateSourcesRegardlessOfPingStatus() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            fakeService.result =
                listOf(
                    DiscoveredSource(name = "存活源", urlTemplate = "https://alive.com/{title}", isAlive = true),
                    DiscoveredSource(name = "超时源", urlTemplate = "https://timeout.com/{title}", isAlive = false),
                )
            val repo = SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), fakeService)
            repo.setPlaybackSubscriptionUrl("https://example.com/sub.json")

            val refreshResult = repo.refreshPlaybackSubscription()

            val summary = assertIs<AppResult.Success<RuleImportSummary>>(refreshResult).data
            assertEquals(2, summary.acceptedCount)
            val rules = repo.playbackRules.first()
            assertEquals(2, rules.size)
            assertTrue(rules.any { it.name == "存活源" })
            assertTrue(rules.any { it.name == "超时源" })
        }

    @Test
    fun importRulesFromJson_communitySubscriptionPackage_parsesAndPersistsRules() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            fakeService.result =
                listOf(
                    DiscoveredSource(name = "包源1", urlTemplate = "https://p1.com/{title}", isAlive = true),
                    DiscoveredSource(name = "包源2", urlTemplate = "https://p2.com/{title}", isAlive = false),
                )
            val repo = SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), fakeService)

            val packageJson = """{"version": 1, "description": "测试", "sources": []}"""
            val result = repo.importRulesFromJson(packageJson)

            val summary = assertIs<AppResult.Success<RuleImportSummary>>(result).data
            assertEquals(2, summary.acceptedCount)
            val rules = repo.playbackRules.first()
            assertEquals(2, rules.size)
            assertTrue(rules.any { it.name == "包源1" })
            assertTrue(rules.any { it.name == "包源2" })
        }

    @Test
    fun playbackRules_autoHealsFromSubscriptionWhenRulesCountIsDeficient() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            fakeService.result =
                listOf(
                    DiscoveredSource(name = "自愈源A", urlTemplate = "https://heal-a.com/{title}", isAlive = true),
                    DiscoveredSource(name = "自愈源B", urlTemplate = "https://heal-b.com/{title}", isAlive = true),
                )
            val ds = createTestUserPreferencesDataSource()
            ds.setPlaybackSubscriptionUrl("https://example.com/sub.json")
            val repo = SettingsRepositoryImpl(ds, FakeSecureSecretStore(), fakeService)

            // 首次读取 playbackRules 触发自愈补齐
            val rules = repo.playbackRules.first()
            assertEquals(2, rules.size)
            assertTrue(rules.any { it.name == "自愈源A" })
            assertTrue(rules.any { it.name == "自愈源B" })
        }

    @Test
    fun aiConfigProfiles_roundTrip_saveActivateDelete() =
        runTest {
            val fakeService = FakeCommunitySubscriptionService()
            val repo = SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), fakeService)

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

    @Test
    fun developerSettings_updateAndEffectiveFlags_reflectCorrectly() =
        runTest {
            val repo =
                SettingsRepositoryImpl(createTestUserPreferencesDataSource(), FakeSecureSecretStore(), FakeCommunitySubscriptionService())

            val defaultDev = repo.developerSettings.first()
            assertEquals(false, defaultDev.isDeveloperModeEnabled)
            assertEquals(null, defaultDev.enableAiOverride)
            assertEquals(null, defaultDev.enablePlayerOverride)
            assertEquals(false, defaultDev.verboseLoggingEnabled)
            assertEquals(false, defaultDev.simulateTorrentUninstalled)
            assertEquals("", defaultDev.customMirrorPrefixOverride)
            assertTrue(repo.isAiAssistantEnabled.first())
            assertTrue(repo.isInternalPlayerEnabled.first())
            val defaultCapabilities = repo.featureCapabilities.first()
            assertTrue(defaultCapabilities.isAiAssistantEnabled)
            assertTrue(defaultCapabilities.isInternalPlayerEnabled)

            repo.setDeveloperModeEnabled(true)
            repo.setDevOverrideEnableAi(false)
            assertEquals(false, repo.isAiAssistantEnabled.first())

            repo.setDevOverrideEnablePlayer(false)
            assertEquals(false, repo.isInternalPlayerEnabled.first())
            val disabledCapabilities = repo.featureCapabilities.first()
            assertEquals(false, disabledCapabilities.isAiAssistantEnabled)
            assertEquals(false, disabledCapabilities.isInternalPlayerEnabled)

            repo.setDevOverrideEnableAi(null)
            assertTrue(repo.isAiAssistantEnabled.first())

            repo.setDevVerboseLogging(true)
            repo.setDevSimulateTorrentUninstalled(true)
            repo.setDevCustomMirrorPrefix("https://mirror.example.com")

            val updated = repo.developerSettings.first()
            assertTrue(updated.isDeveloperModeEnabled)
            assertEquals(null, updated.enableAiOverride)
            assertEquals(false, updated.enablePlayerOverride)
            assertTrue(updated.verboseLoggingEnabled)
            assertTrue(updated.simulateTorrentUninstalled)
            assertEquals("https://mirror.example.com", updated.customMirrorPrefixOverride)
        }
}
