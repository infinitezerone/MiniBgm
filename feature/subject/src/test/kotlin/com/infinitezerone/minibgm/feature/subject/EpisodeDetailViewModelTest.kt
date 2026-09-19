package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommentReactionUser
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun createEpisodeDetailViewModel(
        subjectId: Long,
        episodeId: Long,
        subjectRepository: com.infinitezerone.minibgm.core.data.repository.SubjectRepository,
        collectionRepository: com.infinitezerone.minibgm.core.data.repository.CollectionRepository,
        communityRepository: com.infinitezerone.minibgm.core.data.repository.CommunityRepository,
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
    ): EpisodeDetailViewModel =
        EpisodeDetailViewModel(
            subjectId = subjectId,
            episodeId = episodeId,
            subjectRepository = subjectRepository,
            collectionRepository = collectionRepository,
            communityRepository = communityRepository,
            authRepository = authRepository,
        )

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
                createEpisodeDetailViewModel(
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
                createEpisodeDetailViewModel(
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
                createEpisodeDetailViewModel(
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
                createEpisodeDetailViewModel(
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
                createEpisodeDetailViewModel(
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

    @Test
    fun unauthenticated_toggleWatched_showsLoginPromptDialog() =
        runTest {
            val subjectRepository = FakeSubjectRepository().apply { sendEpisodes(subjectId, sampleEpisodeList) }
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = false)

            val viewModel =
                createEpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = FakeCommunityRepository(),
                    authRepository = authRepository,
                )

            assertFalse(viewModel.uiState.value.showLoginPromptDialog)

            viewModel.toggleWatched(targetEpisode, true)

            assertTrue(viewModel.uiState.value.showLoginPromptDialog)
            assertEquals(0, collectionRepository.updateEpisodeCallCount)

            viewModel.dismissLoginPrompt()
            assertFalse(viewModel.uiState.value.showLoginPromptDialog)
        }

    @Test
    fun unauthenticated_markWatchedUpTo_showsLoginPromptDialog() =
        runTest {
            val subjectRepository = FakeSubjectRepository().apply { sendEpisodes(subjectId, sampleEpisodeList) }
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = false)

            val viewModel =
                createEpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = collectionRepository,
                    communityRepository = FakeCommunityRepository(),
                    authRepository = authRepository,
                )

            viewModel.markWatchedUpTo(targetEpisode)

            assertTrue(viewModel.uiState.value.showLoginPromptDialog)
            assertEquals(0, collectionRepository.markEpisodesWatchedUpToCallCount)

            val url = viewModel.beginLogin()
            assertTrue(url.isNotBlank())
            assertFalse(viewModel.uiState.value.showLoginPromptDialog)
        }

    private val loggedInUserId = 929189L

    private val sampleComment =
        EpisodeComment(
            id = 2038019L,
            mainId = 1348301L,
            creatorId = loggedInUserId,
            content = "这帧数太棒了",
            user = CommentUser(id = loggedInUserId, username = "tester", nickname = "测试用户"),
        )

    @Test
    fun toggleCommentReaction_notReacted_addsLikeWithReactionValue() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setEpisodeComments(episodeId, listOf(sampleComment)) }
            val subjectRepository =
                FakeSubjectRepository().apply { sendEpisodes(subjectId, sampleEpisodeList) }
            val authRepository =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = UserProfile(id = loggedInUserId, username = "tester", nickname = "测试用户"),
                )
            val viewModel =
                createEpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepository,
                    authRepository = authRepository,
                )
            advanceUntilIdle()

            val reaction =
                CommentReaction(value = 141, users = listOf(CommentReactionUser(id = 1L, username = "u1", nickname = "u1")))
            viewModel.toggleCommentReaction(sampleComment, reaction)
            advanceUntilIdle()

            val call = communityRepository.setLikeCalls.single()
            assertEquals(CommunityLikeTarget.EPISODE_COMMENT, call.first)
            assertEquals(2038019L, call.second)
            assertEquals(141, call.third)
        }

    @Test
    fun toggleCommentReaction_alreadyReacted_removesLike() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setEpisodeComments(episodeId, listOf(sampleComment)) }
            val subjectRepository =
                FakeSubjectRepository().apply { sendEpisodes(subjectId, sampleEpisodeList) }
            val authRepository =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = UserProfile(id = loggedInUserId, username = "tester", nickname = "测试用户"),
                )
            val viewModel =
                createEpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepository,
                    authRepository = authRepository,
                )
            advanceUntilIdle()

            val reaction =
                CommentReaction(
                    value = 141,
                    users = listOf(CommentReactionUser(id = loggedInUserId, username = "tester", nickname = "测试用户")),
                )
            viewModel.toggleCommentReaction(sampleComment, reaction)
            advanceUntilIdle()

            val call = communityRepository.removeLikeCalls.single()
            assertEquals(CommunityLikeTarget.EPISODE_COMMENT, call.first)
            assertEquals(2038019L, call.second)
        }

    @Test
    fun toggleCommentReaction_notLoggedIn_emitsLoginPromptWithoutRepoCall() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setEpisodeComments(episodeId, listOf(sampleComment)) }
            val subjectRepository =
                FakeSubjectRepository().apply { sendEpisodes(subjectId, sampleEpisodeList) }
            val viewModel =
                createEpisodeDetailViewModel(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    subjectRepository = subjectRepository,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepository,
                    authRepository = FakeAuthRepository(initialLoggedIn = false),
                )
            advanceUntilIdle()

            viewModel.toggleCommentReaction(sampleComment, CommentReaction(value = 141))
            advanceUntilIdle()

            assertTrue(communityRepository.setLikeCalls.isEmpty())
            val event = viewModel.events.first()
            assertTrue(event is EpisodeDetailUiEvent.ShowSnackbar && event.message.contains("登录"))
        }
}
