package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicParentSubject
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
