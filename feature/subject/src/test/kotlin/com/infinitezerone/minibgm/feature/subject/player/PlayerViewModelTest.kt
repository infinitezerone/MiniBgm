package com.infinitezerone.minibgm.feature.subject.player

import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val subjectId = 1001L
    private val episodeId = 2002L
    private val initialStreamUrl = "https://example.com/stream.m3u8"

    @Test
    fun initialState_matchesProvidedArguments() {
        val authRepository = FakeAuthRepository(initialLoggedIn = true)
        val collectionRepository = FakeCollectionRepository()

        val viewModel =
            PlayerViewModel(
                subjectId = subjectId,
                episodeId = episodeId,
                initialStreamUrl = initialStreamUrl,
                collectionRepository = collectionRepository,
                authRepository = authRepository,
            )

        val state = viewModel.uiState.value
        assertEquals(subjectId, state.subjectId)
        assertEquals(episodeId, state.episodeId)
        assertEquals(initialStreamUrl, state.streamUrl)
        assertFalse(state.isWatched)
        assertFalse(state.autoMarked)
    }

    @Test
    fun authenticated_markWatched_updatesStateAndEmitsEvent() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val collectionRepository = FakeCollectionRepository()

            val viewModel =
                PlayerViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    initialStreamUrl = initialStreamUrl,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                )

            viewModel.markWatched(epNumber = 5)

            val state = viewModel.uiState.value
            assertTrue(state.isWatched)
            assertTrue(state.autoMarked)

            val firstEvent = viewModel.events.first()
            assertTrue(firstEvent is PlayerUiEvent.MarkedWatched)
            assertEquals(5, (firstEvent as PlayerUiEvent.MarkedWatched).epNumber)
        }

    @Test
    fun unauthenticated_markWatched_doesNotTriggerWatchUpdate() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val collectionRepository = FakeCollectionRepository()

            val viewModel =
                PlayerViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    initialStreamUrl = initialStreamUrl,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                )

            viewModel.markWatched(epNumber = 5)

            val state = viewModel.uiState.value
            assertFalse(state.isWatched)
            assertFalse(state.autoMarked)
        }

    @Test
    fun markWatched_isIdempotent() =
        runTest {
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val collectionRepository = FakeCollectionRepository()

            val viewModel =
                PlayerViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    initialStreamUrl = initialStreamUrl,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                )

            viewModel.markWatched(epNumber = 1)
            viewModel.markWatched(epNumber = 2)

            val state = viewModel.uiState.value
            assertTrue(state.isWatched)
            assertTrue(state.autoMarked)
        }

    @Test
    fun updateStreamUrl_updatesState() {
        val authRepository = FakeAuthRepository(initialLoggedIn = true)
        val collectionRepository = FakeCollectionRepository()

        val viewModel =
            PlayerViewModel(
                subjectId = subjectId,
                episodeId = episodeId,
                initialStreamUrl = initialStreamUrl,
                collectionRepository = collectionRepository,
                authRepository = authRepository,
            )

        viewModel.updateStreamUrl("https://example.com/new.mp4")
        assertEquals("https://example.com/new.mp4", viewModel.uiState.value.streamUrl)
    }

    @Test
    fun onPlaybackError_setsErrorMessage() {
        val authRepository = FakeAuthRepository(initialLoggedIn = true)
        val collectionRepository = FakeCollectionRepository()

        val viewModel =
            PlayerViewModel(
                subjectId = subjectId,
                episodeId = episodeId,
                initialStreamUrl = initialStreamUrl,
                collectionRepository = collectionRepository,
                authRepository = authRepository,
            )

        viewModel.onPlaybackError("Network timeout")
        assertEquals("Network timeout", viewModel.uiState.value.error)
    }
}
