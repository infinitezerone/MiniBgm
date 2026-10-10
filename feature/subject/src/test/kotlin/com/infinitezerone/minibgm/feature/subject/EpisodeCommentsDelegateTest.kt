package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommentReactionUser
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeCommentsDelegateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val communityRepository = FakeCommunityRepository()
    private val authRepository = FakeAuthRepository(initialProfile = UserProfile(id = 999L))

    private fun createDelegate(scope: TestScope): EpisodeCommentsDelegate =
        EpisodeCommentsDelegate(
            communityRepository = communityRepository,
            authRepository = authRepository,
            scope = scope,
        )

    @Test
    fun loadComments_success_sortsAndEmitsState() =
        runTest {
            val delegate = createDelegate(this)
            communityRepository.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(
                        EpisodeComment(id = 1, content = "第一条", floor = 1),
                        EpisodeComment(id = 2, content = "第二条", floor = 2),
                    ),
                )

            delegate.loadComments(episodeId = 100L)
            advanceUntilIdle()

            val state = delegate.state.value
            assertEquals(2, state.comments.size)
            assertEquals(2, state.commentCount)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(999L, state.currentUserId)
        }

    @Test
    fun loadComments_error_updatesErrorState() =
        runTest {
            val delegate = createDelegate(this)
            val error = RuntimeException("网络异常")
            communityRepository.getEpisodeCommentsResult =
                AppResult.Error(throwable = error, message = "网络异常")

            delegate.loadComments(episodeId = 100L)
            advanceUntilIdle()

            val state = delegate.state.value
            assertTrue(state.comments.isEmpty())
            assertEquals("网络异常", state.error)
            assertFalse(state.isLoading)
        }

    @Test
    fun setSortOrder_changesSortAlgorithm() =
        runTest {
            val delegate = createDelegate(this)
            val users1 = listOf(CommentReactionUser(id = 1), CommentReactionUser(id = 2))
            val users2 = (1..10).map { CommentReactionUser(id = it.toLong()) }
            communityRepository.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(
                        EpisodeComment(id = 1, content = "第一条", floor = 1, reactions = listOf(CommentReaction(value = 1, users = users1))),
                        EpisodeComment(id = 2, content = "第二条", floor = 2, reactions = listOf(CommentReaction(value = 1, users = users2))),
                    ),
                )

            delegate.loadComments(episodeId = 100L)
            advanceUntilIdle()

            // 热门排序（按表态总数降序）
            delegate.setSortOrder(CommentSortOrder.HOT)
            assertEquals(
                2L,
                delegate.state.value.comments[0]
                    .id,
            )
            assertEquals(
                1L,
                delegate.state.value.comments[1]
                    .id,
            )

            // 楼层倒序
            delegate.setSortOrder(CommentSortOrder.DESCENDING)
            assertEquals(
                2L,
                delegate.state.value.comments[0]
                    .id,
            )
            assertEquals(
                1L,
                delegate.state.value.comments[1]
                    .id,
            )

            // 楼层正序
            delegate.setSortOrder(CommentSortOrder.ASCENDING)
            assertEquals(
                1L,
                delegate.state.value.comments[0]
                    .id,
            )
            assertEquals(
                2L,
                delegate.state.value.comments[1]
                    .id,
            )
        }

    @Test
    fun toggleCommentReaction_optimisticUpdate_and_rollbackOnFailure() =
        runTest {
            val delegate = createDelegate(this)
            val initialComment = EpisodeComment(id = 10, content = "测试表态", floor = 1, reactions = emptyList())
            communityRepository.getEpisodeCommentsResult = AppResult.Success(listOf(initialComment))

            delegate.loadComments(episodeId = 100L)
            advanceUntilIdle()

            // 模拟失败
            val error = RuntimeException("服务器拒绝")
            communityRepository.setLikeResult = AppResult.Error(throwable = error, message = "服务器拒绝")

            delegate.toggleCommentReaction(initialComment, reactionValue = 48)

            // 协程执行完后，因失败而回滚
            advanceUntilIdle()

            val commentAfterFailure =
                delegate.state.value.comments
                    .single()
            assertTrue(commentAfterFailure.reactions.isEmpty())
        }
}
