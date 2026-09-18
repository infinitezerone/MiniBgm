package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackRulesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_loadsRulesFromSettingsRepository() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule-1",
                    name = "AGE动漫",
                    urlTemplate = "https://agefans.com/search?q={title}",
                    isEnabled = true,
                )
            fakeRepo.addPlaybackRule(rule)

            val viewModel = PlaybackRulesViewModel(fakeRepo)
            val state = viewModel.uiState.first { !it.isLoading }

            assertEquals(1, state.rules.size)
            assertEquals("AGE动漫", state.rules.first().name)
        }

    @Test
    fun addRule_withValidInput_addsRuleAndEmitsSnackbar() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.addRule(
                name = "Anime1",
                urlTemplate = "https://anime1.me/?s={title}",
                description = "繁体搜索源",
            )

            val rules = fakeRepo.playbackRules.first()
            assertEquals(1, rules.size)
            assertEquals("Anime1", rules.first().name)
            assertEquals("https://anime1.me/?s={title}", rules.first().urlTemplate)
            assertTrue(rules.first().isEnabled)

            val event = viewModel.events.first()
            assertTrue(event is PlaybackRulesUiEvent.ShowSnackbar)
            assertTrue((event as PlaybackRulesUiEvent.ShowSnackbar).message.contains("Anime1"))
        }

    @Test
    fun addRule_withBlankInput_emitsWarningSnackbar() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.addRule(name = "   ", urlTemplate = "")

            val rules = fakeRepo.playbackRules.first()
            assertTrue(rules.isEmpty())

            val event = viewModel.events.first()
            assertTrue(event is PlaybackRulesUiEvent.ShowSnackbar)
            assertTrue((event as PlaybackRulesUiEvent.ShowSnackbar).message.contains("不能为空"))
        }

    @Test
    fun updateRule_updatesExistingRule() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule-1",
                    name = "AGE",
                    urlTemplate = "https://age.tv/{title}",
                    isEnabled = true,
                )
            fakeRepo.addPlaybackRule(rule)

            val viewModel = PlaybackRulesViewModel(fakeRepo)
            viewModel.updateRule(
                id = "rule-1",
                name = "AGE动漫 (新版)",
                urlTemplate = "https://agefans.vip/{title}",
            )

            val updated = fakeRepo.playbackRules.first().first()
            assertEquals("AGE动漫 (新版)", updated.name)
            assertEquals("https://agefans.vip/{title}", updated.urlTemplate)
        }

    @Test
    fun deleteRule_removesRule() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule-1",
                    name = "AGE",
                    urlTemplate = "https://age.tv/{title}",
                    isEnabled = true,
                )
            fakeRepo.addPlaybackRule(rule)

            val viewModel = PlaybackRulesViewModel(fakeRepo)
            viewModel.deleteRule("rule-1")

            val rules = fakeRepo.playbackRules.first()
            assertTrue(rules.isEmpty())
        }

    @Test
    fun toggleRule_changesEnabledStatus() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule-1",
                    name = "AGE",
                    urlTemplate = "https://age.tv/{title}",
                    isEnabled = true,
                )
            fakeRepo.addPlaybackRule(rule)

            val viewModel = PlaybackRulesViewModel(fakeRepo)
            viewModel.toggleRule("rule-1", false)

            val updated = fakeRepo.playbackRules.first().first()
            assertFalse(updated.isEnabled)
        }

    @Test
    fun importRulesFromJson_validJson_importsRules() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            val json =
                """
                [
                    {
                        "id": "imported-1",
                        "name": "BimiBimi",
                        "urlTemplate": "https://bimibimi.net/search/{title}",
                        "isEnabled": true
                    }
                ]
                """.trimIndent()

            viewModel.importRulesFromJson(json)

            val rules = fakeRepo.playbackRules.first()
            assertEquals(1, rules.size)
            assertEquals("BimiBimi", rules.first().name)
        }

    @Test
    fun importRulesFromJson_invalidJson_emitsErrorSnackbar() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.importRulesFromJson("invalid json string {}")

            val rules = fakeRepo.playbackRules.first()
            assertTrue(rules.isEmpty())

            val event = viewModel.events.first()
            assertTrue(event is PlaybackRulesUiEvent.ShowSnackbar)
            assertTrue((event as PlaybackRulesUiEvent.ShowSnackbar).message.contains("解析失败"))
        }
}
