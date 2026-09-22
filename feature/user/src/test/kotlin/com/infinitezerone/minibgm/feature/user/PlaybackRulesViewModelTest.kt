package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntry
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
                    name = "示例采集站",
                    urlTemplate = "https://agefans.com/search?q={title}",
                    isEnabled = true,
                )
            fakeRepo.addPlaybackRule(rule)

            val viewModel = PlaybackRulesViewModel(fakeRepo)
            val state = viewModel.uiState.first { !it.isLoading }

            assertEquals(1, state.rules.size)
            assertEquals("示例采集站", state.rules.first().name)
        }

    @Test
    fun addRule_withValidInput_addsRuleAndEmitsSnackbar() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.addRule(
                name = "自定义规则A",
                urlTemplate = "https://example.com/?s={title}",
                description = "测试搜索源",
            )

            val rules = fakeRepo.playbackRules.first()
            assertEquals(1, rules.size)
            assertEquals("自定义规则A", rules.first().name)
            assertEquals("https://example.com/?s={title}", rules.first().urlTemplate)
            assertTrue(rules.first().isEnabled)

            val event = viewModel.events.first()
            assertTrue(event is PlaybackRulesUiEvent.ShowSnackbar)
            assertTrue((event as PlaybackRulesUiEvent.ShowSnackbar).message.contains("自定义规则A"))
        }

    @Test
    fun addRule_asSourceKind_parsesHeaderLines() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.addRule(
                name = "我的接口",
                urlTemplate = "https://api.example.com/play?id={subjectId}&ep={ep}",
                kind = PlaybackRuleKind.SOURCE,
                headersText = "Referer: https://api.example.com/\n\nBrokenLine\nX-Key: abc",
            )

            val rule = fakeRepo.playbackRules.first().single()
            assertEquals(PlaybackRuleKind.SOURCE, rule.kind)
            assertEquals(
                mapOf("Referer" to "https://api.example.com/", "X-Key" to "abc"),
                rule.headers,
            )
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
                name = "示例采集站 (新版)",
                urlTemplate = "https://agefans.vip/{title}",
            )

            val updated = fakeRepo.playbackRules.first().first()
            assertEquals("示例采集站 (新版)", updated.name)
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

    private fun playlistDocJson(
        id: String,
        name: String = "片单-$id",
        subjectId: Long = 123L,
    ) =
        """{"schemaVersion":1,"playlists":[{"id":"$id","name":"$name","bgmSubjectId":$subjectId,"entries":[{"label":"01","url":"https://cdn.example.com/$id.m3u8","kind":"DIRECT"}]}]}"""

    @Test
    fun initialState_loadsPlaylistsAlongsideRules() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            fakeRepo.addPlaybackRule(PlaybackSourceRule(id = "rule-1", name = "AGE", urlTemplate = "https://age.tv/{title}"))
            fakeRepo.setPlaylists(
                listOf(
                    PlaybackPlaylist(
                        id = "pl-1",
                        name = "我的片源",
                        bgmSubjectId = 123L,
                        entries = listOf(PlaylistEntry(label = "01", url = "https://cdn.example.com/a.m3u8")),
                    ),
                ),
            )

            val state = PlaybackRulesViewModel(fakeRepo).uiState.first { !it.isLoading }

            assertEquals(1, state.rules.size)
            assertEquals(listOf("pl-1"), state.playlists.map { it.id })
        }

    @Test
    fun importPlaylistsFromJson_validDocument_mergesAndReportsSummary() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.importPlaylistsFromJson(playlistDocJson("pl-1"))

            val playlists = fakeRepo.playlists.first()
            assertEquals(1, playlists.size)
            assertEquals(123L, playlists.single().bgmSubjectId)

            val event = viewModel.events.first()
            assertTrue((event as PlaybackRulesUiEvent.ShowSnackbar).message.contains("新增 1 份片单"))

            viewModel.importPlaylistsFromJson(playlistDocJson("pl-1", name = "改名后的片源"))
            assertEquals(
                "改名后的片源",
                fakeRepo.playlists
                    .first()
                    .single()
                    .name,
            )
            val second = viewModel.events.first() as PlaybackRulesUiEvent.ShowSnackbar
            assertTrue(second.message.contains("覆盖 1 份"))
        }

    @Test
    fun importPlaylistsFromJson_blankText_reportsErrorWithoutTouchingRepository() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            fakeRepo.setPlaylists(
                listOf(PlaybackPlaylist(id = "keep", name = "保留", entries = listOf(PlaylistEntry(label = "01", url = "https://x/1")))),
            )
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.importPlaylistsFromJson("   ")

            assertEquals(listOf("keep"), fakeRepo.playlists.first().map { it.id })
            assertTrue(
                (viewModel.events.first() as PlaybackRulesUiEvent.ShowSnackbar).message.contains("不能为空"),
            )
        }

    @Test
    fun importPlaylistsFromJson_undecodableText_surfacesRepositoryError() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            val viewModel = PlaybackRulesViewModel(fakeRepo)

            viewModel.importPlaylistsFromJson("{ not json }")

            assertTrue(fakeRepo.playlists.first().isEmpty())
            val event = viewModel.events.first() as PlaybackRulesUiEvent.ShowSnackbar
            assertTrue(event.message.contains("JSON 解析失败"))
        }

    @Test
    fun deletePlaylist_andClearPlaylists_updateRepository() =
        runTest {
            val fakeRepo = FakeSettingsRepository()
            fakeRepo.setPlaylists(
                listOf(
                    PlaybackPlaylist(id = "a", name = "A", entries = listOf(PlaylistEntry(label = "01", url = "https://x/1"))),
                    PlaybackPlaylist(id = "b", name = "B", entries = listOf(PlaylistEntry(label = "01", url = "https://x/2"))),
                ),
            )
            val viewModel = PlaybackRulesViewModel(fakeRepo)
            // 与界面一致：先有订阅者，StateFlow 才会取到上游快照
            viewModel.uiState.first { it.playlists.size == 2 }

            viewModel.deletePlaylist("a")
            assertEquals(listOf("b"), fakeRepo.playlists.first().map { it.id })
            assertTrue(
                ((viewModel.events.first()) as PlaybackRulesUiEvent.ShowSnackbar).message.contains("A"),
            )

            viewModel.clearPlaylists()
            assertTrue(fakeRepo.playlists.first().isEmpty())
        }

    @Test
    fun requestAiSourceSearch_emitsEventAskingAssistantToFindSources() =
        runTest {
            val viewModel = PlaybackRulesViewModel(FakeSettingsRepository())

            viewModel.requestAiSourceSearch()

            val event = viewModel.events.first()
            assertTrue(event is PlaybackRulesUiEvent.OpenAiSourceSearch)
            // 这个页面没有站点上下文，prompt 只能交代意图、并要求助手先问到地址，
            // 否则助手没有可探查的目标
            val prompt = (event as PlaybackRulesUiEvent.OpenAiSourceSearch).prompt
            assertTrue(prompt.contains("站点地址"))
        }
}
