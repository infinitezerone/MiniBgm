package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.testing.repository.FakePlaybackHistoryRepository
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

    private fun history(
        episodeId: Long,
        positionMs: Long = 0L,
        updatedAt: Long = 0L,
    ) = PlaybackHistory(
        episodeId = episodeId,
        subjectId = 1001L,
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
