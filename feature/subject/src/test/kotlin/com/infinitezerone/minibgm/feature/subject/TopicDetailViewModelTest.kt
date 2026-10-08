package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommentReactionUser
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicParentSubject
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TopicDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleTopicId = 38323L
    private val sampleTopicDetail =
        TopicDetail(
            id = sampleTopicId,
            title = "流媒体版本中文字幕翻译质量如何？",
            creatorId = 63429L,
            parentId = 496135L,
            replyCount = 2,
            createdAt = 1767239305L,
            creator = CommentUser(id = 63429L, username = "akb49", nickname = "老白"),
            subject = TopicParentSubject(id = 496135L, name = "ひゃくえむ。", nameCn = "百米。"),
            replies =
                listOf(
                    TopicReply(
                        id = 1001L,
                        creatorId = 63429L,
                        content = "主楼讨论正文内容",
                        creator = CommentUser(id = 63429L, username = "akb49", nickname = "老白"),
                    ),
                    TopicReply(
                        id = 1002L,
                        creatorId = 880270L,
                        content = "2楼回帖内容",
                        creator = CommentUser(id = 880270L, username = "880270", nickname = "青嵐"),
                        replies =
                            listOf(
                                TopicReply(
                                    id = 1003L,
                                    creatorId = 743041L,
                                    content = "楼中楼回复",
                                    creator = CommentUser(id = 743041L, username = "apple", nickname = "绿苹果"),
                                ),
                            ),
                    ),
                ),
        )

    @Test
    fun initialState_loadsTopicDetailSuccessfully() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    setTopicDetail(sampleTopicId, sampleTopicDetail)
                }

            val viewModel =
                TopicDetailViewModel(
                    topicId = sampleTopicId,
                    type = "subject",
                    communityRepository = communityRepository,
                )

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.error)
            assertNotNull(state.topicDetail)
            assertEquals("流媒体版本中文字幕翻译质量如何？", state.topicDetail?.title)
            assertEquals("主楼讨论正文内容", state.topicDetail?.mainPost?.content)
            assertEquals(1, state.topicDetail?.floorReplies?.size)
            assertEquals(
                1,
                state.topicDetail
                    ?.floorReplies
                    ?.first()
                    ?.replies
                    ?.size,
            )
            assertEquals(
                "楼中楼回复",
                state.topicDetail
                    ?.floorReplies
                    ?.first()
                    ?.replies
                    ?.first()
                    ?.content,
            )
        }

    @Test
    fun initialState_repositoryError_updatesErrorState() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    getTopicDetailResult = AppResult.Error(IllegalStateException("网络连接超时"))
                }

            val viewModel =
                TopicDetailViewModel(
                    topicId = sampleTopicId,
                    type = "subject",
                    communityRepository = communityRepository,
                )

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.topicDetail)
            assertEquals("网络连接超时", state.error)
        }

    @Test
    fun refresh_triggersReload() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    setTopicDetail(sampleTopicId, sampleTopicDetail)
                }

            val viewModel =
                TopicDetailViewModel(
                    topicId = sampleTopicId,
                    type = "subject",
                    communityRepository = communityRepository,
                )

            assertEquals(1, communityRepository.getTopicDetailCallCount)

            viewModel.refresh(isUserPullToRefresh = true)
            assertEquals(2, communityRepository.getTopicDetailCallCount)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    private val loggedInUserId = 880270L

    private fun loggedInViewModel(
        communityRepository: FakeCommunityRepository,
        type: String = "subject",
    ): TopicDetailViewModel =
        TopicDetailViewModel(
            topicId = sampleTopicId,
            type = type,
            communityRepository = communityRepository,
            authRepository =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile =
                        com.infinitezerone.minibgm.core.model.UserProfile(
                            id = loggedInUserId,
                            username = "me",
                            nickname = "我",
                        ),
                ),
        )

    @Test
    fun toggleReaction_notLoggedIn_emitsLoginPromptWithoutRepoCall() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel =
                TopicDetailViewModel(
                    topicId = sampleTopicId,
                    type = "subject",
                    communityRepository = communityRepository,
                    authRepository = FakeAuthRepository(initialLoggedIn = false),
                )
            advanceUntilIdle()

            viewModel.toggleReaction(sampleTopicDetail.floorReplies.first(), CommentReaction(value = 141))
            advanceUntilIdle()

            assertTrue(communityRepository.setLikeCalls.isEmpty())
            assertTrue(communityRepository.removeLikeCalls.isEmpty())
            val event = viewModel.events.first()
            assertTrue(event is TopicDetailUiEvent.ShowSnackbar && event.message.contains("登录"))
        }

    @Test
    fun toggleReaction_notReacted_addsLikeWithExistingReactionValue() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()

            val reaction =
                CommentReaction(
                    value = 141,
                    users = listOf(CommentReactionUser(id = 489240L, username = "other", nickname = "别人")),
                )
            viewModel.toggleReaction(sampleTopicDetail.floorReplies.first(), reaction)
            advanceUntilIdle()

            val call = communityRepository.setLikeCalls.single()
            assertEquals(CommunityLikeTarget.SUBJECT_POST, call.first)
            assertEquals(1002L, call.second)
            assertEquals(141, call.third)
        }

    @Test
    fun toggleReaction_alreadyReacted_removesLike() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()

            val reaction =
                CommentReaction(
                    value = 141,
                    users = listOf(CommentReactionUser(id = loggedInUserId, username = "me", nickname = "我")),
                )
            viewModel.toggleReaction(sampleTopicDetail.floorReplies.first(), reaction)
            advanceUntilIdle()

            val call = communityRepository.removeLikeCalls.single()
            assertEquals(CommunityLikeTarget.SUBJECT_POST, call.first)
            assertEquals(1002L, call.second)
            assertTrue(communityRepository.setLikeCalls.isEmpty())
        }

    @Test
    fun toggleReaction_groupTopic_usesGroupPostTarget() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel = loggedInViewModel(communityRepository, type = "group")
            advanceUntilIdle()

            viewModel.toggleReaction(
                sampleTopicDetail.floorReplies.first(),
                CommentReaction(value = 6, users = emptyList()),
            )
            advanceUntilIdle()

            assertEquals(CommunityLikeTarget.GROUP_POST, communityRepository.setLikeCalls.single().first)
        }

    @Test
    fun toggleReaction_optimisticallyUpdatesFloorWithoutRefresh() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()
            val callsAfterInit = communityRepository.getTopicDetailCallCount

            val reaction =
                CommentReaction(
                    value = 141,
                    users = listOf(CommentReactionUser(id = 489240L, username = "other", nickname = "别人")),
                )
            viewModel.toggleReaction(sampleTopicDetail.floorReplies.first(), reaction)
            advanceUntilIdle()

            val floor =
                viewModel.uiState.value.topicDetail!!
                    .floorReplies
                    .single { it.id == 1002L }
            assertEquals(1, floor.reactions.size)
            assertEquals(141, floor.reactions.single().value)
            assertTrue(
                floor.reactions
                    .single()
                    .users
                    .any { it.id == loggedInUserId },
            )
            // 兄弟楼层与楼中楼不受影响；成功后不整流刷新
            assertTrue(
                viewModel.uiState.value.topicDetail!!
                    .floorReplies
                    .last()
                    .replies
                    .single()
                    .reactions
                    .isEmpty(),
            )
            assertEquals(callsAfterInit, communityRepository.getTopicDetailCallCount)
        }

    @Test
    fun toggleReaction_nestedReply_updatesRecursively() =
        runTest {
            val communityRepository = FakeCommunityRepository().apply { setTopicDetail(sampleTopicId, sampleTopicDetail) }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()

            val nestedReply =
                sampleTopicDetail.floorReplies
                    .last()
                    .replies
                    .single()
            viewModel.toggleReaction(nestedReply, CommentReaction(value = 6, users = emptyList()))
            advanceUntilIdle()

            val updatedNested =
                viewModel.uiState.value.topicDetail!!
                    .floorReplies
                    .last()
                    .replies
                    .single { it.id == nestedReply.id }
            assertEquals(1, updatedNested.reactions.size)
            assertEquals(6, updatedNested.reactions.single().value)
            assertTrue(
                updatedNested.reactions
                    .single()
                    .users
                    .any { it.id == loggedInUserId },
            )
        }

    @Test
    fun toggleReaction_optimisticAdd_rollsBackOnError() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    setTopicDetail(sampleTopicId, sampleTopicDetail)
                    setLikeResult = AppResult.Error(IllegalStateException("boom"), "网络异常")
                }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()

            viewModel.toggleReaction(sampleTopicDetail.floorReplies.first(), CommentReaction(value = 141))
            advanceUntilIdle()

            val floor =
                viewModel.uiState.value.topicDetail!!
                    .floorReplies
                    .single { it.id == 1002L }
            assertTrue(floor.reactions.isEmpty())
            val event = viewModel.events.first()
            assertTrue(event is TopicDetailUiEvent.ShowSnackbar && event.message == "网络异常")
        }

    @Test
    fun setSortOrder_changesSortOrderAndSortsFloorRepliesCorrectly() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    val detail =
                        sampleTopicDetail.copy(
                            replies =
                                sampleTopicDetail.replies +
                                    TopicReply(
                                        id = 1004L,
                                        creatorId = 999L,
                                        content = "3楼回帖",
                                        reactions = listOf(CommentReaction(value = 1, users = listOf(CommentReactionUser(id = 1L)))),
                                    ),
                        )
                    setTopicDetail(sampleTopicId, detail)
                }
            val viewModel = TopicDetailViewModel(sampleTopicId, "subject", communityRepository)
            advanceUntilIdle()

            // 默认正序
            assertEquals(CommentSortOrder.ASCENDING, viewModel.uiState.value.sortOrder)
            val ascFloors = viewModel.uiState.value.sortedFloorReplies
            assertEquals(2, ascFloors.size)
            assertEquals(2, ascFloors[0].first) // #2 楼
            assertEquals(1002L, ascFloors[0].second.id)
            assertEquals(3, ascFloors[1].first) // #3 楼
            assertEquals(1004L, ascFloors[1].second.id)

            // 切换倒序
            viewModel.setSortOrder(CommentSortOrder.DESCENDING)
            advanceUntilIdle()
            assertEquals(CommentSortOrder.DESCENDING, viewModel.uiState.value.sortOrder)
            val descFloors = viewModel.uiState.value.sortedFloorReplies
            assertEquals(3, descFloors[0].first) // #3 楼先显示
            assertEquals(1004L, descFloors[0].second.id)
            assertEquals(2, descFloors[1].first) // #2 楼后显示
            assertEquals(1002L, descFloors[1].second.id)

            // 切换热门
            viewModel.setSortOrder(CommentSortOrder.HOT)
            advanceUntilIdle()
            assertEquals(CommentSortOrder.HOT, viewModel.uiState.value.sortOrder)
            val hotFloors = viewModel.uiState.value.sortedFloorReplies
            assertEquals(3, hotFloors[0].first) // 3楼有 1 个表态排在前面
            assertEquals(1004L, hotFloors[0].second.id)
            assertEquals(2, hotFloors[1].first)
            assertEquals(1002L, hotFloors[1].second.id)
        }

    @Test
    fun toggleMainPostReaction_optimisticallyUpdatesMainPostReactions() =
        runTest {
            val communityRepository =
                FakeCommunityRepository().apply {
                    setTopicDetail(sampleTopicId, sampleTopicDetail)
                }
            val viewModel = loggedInViewModel(communityRepository)
            advanceUntilIdle()

            viewModel.toggleMainPostReaction(44)
            advanceUntilIdle()

            val mainPost =
                viewModel.uiState.value.topicDetail
                    ?.mainPost
            assertNotNull(mainPost)
            assertEquals(1, mainPost?.reactions?.size)
            assertEquals(44, mainPost?.reactions?.first()?.value)
            assertTrue(
                mainPost
                    ?.reactions
                    ?.first()
                    ?.users
                    ?.any { it.id == loggedInUserId } == true,
            )
        }
}
