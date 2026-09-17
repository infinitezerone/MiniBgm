package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EpisodeDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val targetEpisode = sampleEpisodeList.first()
    private val subjectId = sampleSubject.id
    private val episodeId = targetEpisode.id

    @Test
    fun initialState_loadsEpisodeFromRepositoryStream() =
        runTest {
            val subjectRepository =
                FakeSubjectRepository().apply {
                    sendEpisodes(subjectId, sampleEpisodeList)
                }
            val collectionRepository =
                FakeCollectionRepository().apply {
                    sendCollection(sampleUserCollection.copy(subjectId = subjectId, epStatus = targetEpisode.sort.toInt()))
                }
            val communityRepository = FakeCommunityRepository()

            val viewModel =
                EpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = communityRepository,
                )

            val state = viewModel.uiState.value
            assertEquals(targetEpisode, state.episode)
            assertTrue(state.isWatched)
            assertEquals(sampleEpisodeList, state.allEpisodes)
            assertFalse(state.isLoading)
        }

    @Test
    fun toggleWatched_updatesEpisodeStatusInRepository() =
        runTest {
            val subjectRepository =
                FakeSubjectRepository().apply {
                    sendEpisodes(subjectId, sampleEpisodeList)
                }
            val collectionRepository = FakeCollectionRepository()
            val communityRepository = FakeCommunityRepository()

            val viewModel =
                EpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = communityRepository,
                )

            // 初始未看过，点击打卡
            viewModel.toggleWatched(episode = targetEpisode, isWatched = true)
            assertEquals(1, collectionRepository.updateEpisodeCallCount)
            assertTrue(viewModel.uiState.value.isWatched)

            // 点击取消打卡
            viewModel.toggleWatched(episode = targetEpisode, isWatched = false)
            assertEquals(2, collectionRepository.updateEpisodeCallCount)
            assertFalse(viewModel.uiState.value.isWatched)
        }

    @Test
    fun markWatchedUpTo_callsRepositoryMarkEpisodesWatchedUpTo() =
        runTest {
            val subjectRepository =
                FakeSubjectRepository().apply {
                    sendEpisodes(subjectId, sampleEpisodeList)
                }
            val collectionRepository = FakeCollectionRepository()
            val communityRepository = FakeCommunityRepository()

            val viewModel =
                EpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = communityRepository,
                )

            viewModel.markWatchedUpTo(targetEpisode = targetEpisode)
            assertEquals(1, collectionRepository.markEpisodesWatchedUpToCallCount)
            assertTrue(viewModel.uiState.value.isWatched)
        }

    @Test
    fun loadComments_populatesCommentsInUiState() =
        runTest {
            val sampleComments =
                listOf(
                    EpisodeComment(
                        id = 1001L,
                        user = CommentUser(id = 1L, username = "test", nickname = "Tester", avatar = null),
                        content = "这集太精彩了！",
                        createdAt = 1700000000L,
                    ),
                )
            val subjectRepository =
                FakeSubjectRepository().apply {
                    sendEpisodes(subjectId, sampleEpisodeList)
                }
            val collectionRepository = FakeCollectionRepository()
            val communityRepository =
                FakeCommunityRepository().apply {
                    setEpisodeComments(episodeId, sampleComments)
                }

            val viewModel =
                EpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = communityRepository,
                )

            val state = viewModel.uiState.value
            assertEquals(sampleComments, state.comments)
            assertFalse(state.isCommentsLoading)
            assertNull(state.error)
        }

    @Test
    fun refresh_reloadsEpisodesAndComments() =
        runTest {
            val subjectRepository =
                FakeSubjectRepository().apply {
                    sendEpisodes(subjectId, sampleEpisodeList)
                }
            val collectionRepository = FakeCollectionRepository()
            val communityRepository = FakeCommunityRepository()

            val viewModel =
                EpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = communityRepository,
                )

            viewModel.refresh(isUserPullToRefresh = true)
            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
            assertNull(state.error)
        }
}
