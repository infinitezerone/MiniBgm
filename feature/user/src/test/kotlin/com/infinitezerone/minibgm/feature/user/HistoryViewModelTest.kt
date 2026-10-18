package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.testing.repository.FakePlaybackHistoryRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun playbackHistory_isProjectedIntoUiStateByRecentWatchOrder() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, positionMs = 120_000L, updatedAt = 1_000L))
            historyRepo.saveHistory(history(episodeId = 102L, positionMs = 30_000L, updatedAt = 2_000L))

            val viewModel = HistoryViewModel(historyRepo)
            val state = viewModel.uiState.first { it.history.isNotEmpty() }

            // 最近观看的在最前：102 后写，应排在 101 之前
            assertEquals(listOf(102L, 101L), state.history.map { it.episodeId })
            assertEquals(0.5f, state.history.single { it.episodeId == 101L }.progress)
        }

    @Test
    fun deleteHistory_removesOnlyTargetEpisodeAndEmitsResourceSnackbar() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, updatedAt = 1_000L))
            historyRepo.saveHistory(history(episodeId = 102L, updatedAt = 2_000L))

            val viewModel = HistoryViewModel(historyRepo)
            viewModel.deleteHistory(101L)
            advanceUntilIdle()

            assertNull(historyRepo.getHistoryByEpisode(101L))
            assertEquals(102L, historyRepo.getHistoryByEpisode(102L)?.episodeId)

            // 提示文案必须走资源体系：message 留空、messageRes 有效
            val event = viewModel.events.first()
            assertIs<HistoryUiEvent.ShowSnackbar>(event)
            assertEquals(R.string.feature_user_history_deleted, event.messageRes)
            assertNull(event.message)
        }

    @Test
    fun deleteHistory_ignoresNonPositiveEpisodeId() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, updatedAt = 1_000L))

            val viewModel = HistoryViewModel(historyRepo)
            viewModel.deleteHistory(0L)
            advanceUntilIdle()

            assertEquals(101L, historyRepo.getHistoryByEpisode(101L)?.episodeId)
        }

    @Test
    fun clearPlaybackHistory_emptiesRepository() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, updatedAt = 1_000L))
            historyRepo.saveHistory(history(episodeId = 102L, updatedAt = 2_000L))

            val viewModel = HistoryViewModel(historyRepo)
            viewModel.clearPlaybackHistory()
            advanceUntilIdle()

            assertTrue(historyRepo.observeRecentHistory(10).first().isEmpty())
            val event = viewModel.events.first()
            assertIs<HistoryUiEvent.ShowSnackbar>(event)
            assertEquals(R.string.feature_user_history_cleared, event.messageRes)
        }

    @Test
    fun uiState_groupsEpisodesBySubject_andExpansionIsHeldInUiState() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, subjectId = 7L, updatedAt = 1_000L))
            historyRepo.saveHistory(history(episodeId = 102L, subjectId = 7L, updatedAt = 2_000L))
            historyRepo.saveHistory(history(episodeId = 201L, subjectId = 9L, updatedAt = 500L))

            val viewModel = HistoryViewModel(historyRepo)
            val initial = viewModel.uiState.first { it.groups.isNotEmpty() }
            assertEquals(2, initial.groups.size)
            assertEquals(
                102L,
                initial.groups
                    .first()
                    .latest.episodeId,
            )
            assertTrue(initial.expandedSubjectIds.isEmpty())

            viewModel.toggleSubjectExpanded(7L)
            val expanded = viewModel.uiState.first { 7L in it.expandedSubjectIds }
            assertEquals(setOf(7L), expanded.expandedSubjectIds)

            viewModel.toggleSubjectExpanded(7L)
            val collapsed = viewModel.uiState.first { 7L !in it.expandedSubjectIds }
            assertTrue(collapsed.expandedSubjectIds.isEmpty())
        }

    @Test
    fun deleteSubjectHistory_removesEveryEpisodeOfThatSubjectAndEmitsSnackbar() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(history(episodeId = 101L, subjectId = 7L, updatedAt = 1_000L))
            historyRepo.saveHistory(history(episodeId = 102L, subjectId = 7L, updatedAt = 2_000L))
            historyRepo.saveHistory(history(episodeId = 201L, subjectId = 9L, updatedAt = 500L))

            val viewModel = HistoryViewModel(historyRepo)
            viewModel.deleteSubjectHistory(7L)
            advanceUntilIdle()

            assertNull(historyRepo.getHistoryByEpisode(101L))
            assertNull(historyRepo.getHistoryByEpisode(102L))
            assertEquals(201L, historyRepo.getHistoryByEpisode(201L)?.episodeId)

            val event = viewModel.events.first()
            assertIs<HistoryUiEvent.ShowSnackbar>(event)
            assertEquals(R.string.feature_user_history_subject_deleted, event.messageRes)
        }

    @Test
    fun healMissingCovers_recoversBlankCoverFromSubjectRepository() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(
                history(episodeId = 101L, subjectId = 7L, updatedAt = 1_000L).copy(coverUrl = ""),
            )
            val subjectRepo = FakeSubjectRepository()
            subjectRepo.sendSubject(
                Subject(
                    id = 7L,
                    name = "葬送的芙莉莲",
                    images = SubjectImages(large = "https://example.com/frieren.jpg"),
                ),
            )

            val viewModel = HistoryViewModel(historyRepo, subjectRepo)
            advanceUntilIdle()

            val healed = requireNotNull(historyRepo.getHistoryByEpisode(101L))
            assertEquals("https://example.com/frieren.jpg", healed.coverUrl)
        }

    private fun history(
        episodeId: Long,
        subjectId: Long = 1001L,
        positionMs: Long = 0L,
        updatedAt: Long = 0L,
    ) = PlaybackHistory(
        episodeId = episodeId,
        subjectId = subjectId,
        subjectName = "葬送的芙莉莲",
        episodeName = "第 1 话",
        episodeSort = 1f,
        coverUrl = "https://example.com/cover.jpg",
        streamUrl = "https://example.com/video.m3u8",
        positionMs = positionMs,
        durationMs = 240_000L,
        updatedAt = updatedAt,
    )
}
