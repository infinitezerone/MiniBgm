package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.DiscoveredSource
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.RuleParserType
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
    fun validateAndTestSubscription_reports_skipped_sites_in_proposal() =
        runTest {
            val report =
                SubscriptionValidationReport(
                    isHealthy = true,
                    subscriptionUrl = "https://example.com/tvbox.json",
                    totalRules = 1,
                    aliveRules = 1,
                    averageLatencyMs = 80L,
                    sources =
                        listOf(
                            DiscoveredSource(
                                name = "可用采集站",
                                urlTemplate = "https://good.tv/provide/vod?ac=detail&wd={title}",
                                kind = PlaybackRuleKind.SOURCE,
                                parserType = RuleParserType.MACCMS,
                            ),
                        ),
                    skippedUnsupportedSites = 3,
                    skippedMalformedSites = 1,
                )
            fakeSettingsRepository.validationReportResult = AppResult.Success(report)

            val result = communityTools.validateAndTestSubscription("https://example.com/tvbox.json")

            assertTrue(result.contains("另有 3 条为爬虫/扩展源，本应用不支持"), result)
            assertTrue(result.contains("1 条条目信息不完整"), result)

            val action = pendingActionStore.actions.value.single()
            assertIs<PendingAction.ImportPlaybackRules>(action)
            val rule = action.rules.single()
            // 接口端点必须落成取源规则，否则播放时专用解析器会被丢掉
            assertEquals(PlaybackRuleKind.SOURCE, rule.kind)
            assertEquals(RuleParserType.MACCMS, rule.parserType)
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
}
