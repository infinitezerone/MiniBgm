package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.model.SubjectTopicPage
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommunityRepositoryImplTest {
    private class FakeBangumiCommunityService : BangumiCommunityService {
        var shouldThrow = false
        var subjectTopicThrows = false
        var groupTopicThrows = false

        override suspend fun getEpisodeComments(episodeId: Long): List<EpisodeComment> {
            if (shouldThrow) throw RuntimeException("Network error")
            return listOf(
                EpisodeComment(
                    id = 101,
                    mainId = episodeId,
                    content = "精彩一集",
                    user = CommentUser(id = 1, username = "u1", nickname = "用户1"),
                ),
            )
        }

        override suspend fun getSubjectComments(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): SubjectCommentPage {
            if (shouldThrow) throw RuntimeException("Network error")
            return SubjectCommentPage(
                total = 1,
                data =
                    listOf(
                        SubjectComment(
                            id = 201,
                            rate = 9,
                            comment = "神作",
                            user = CommentUser(id = 2, username = "u2", nickname = "用户2"),
                        ),
                    ),
            )
        }

        override suspend fun getSubjectTopics(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): SubjectTopicPage {
            if (shouldThrow) throw RuntimeException("Network error")
            return SubjectTopicPage(
                total = 1,
                data =
                    listOf(
                        SubjectTopic(
                            id = 301,
                            title = "讨论贴1",
                            parentId = subjectId,
                            replyCount = 5,
                        ),
                    ),
            )
        }

        override suspend fun getSubjectTopicDetail(topicId: Long): TopicDetail {
            if (shouldThrow) throw RuntimeException("Network error")
            if (subjectTopicThrows) throw RuntimeException("Subject topic error")
            return TopicDetail(
                id = topicId,
                title = "条目讨论帖详情",
                replies =
                    listOf(
                        TopicReply(id = 1, content = "主楼内容"),
                        TopicReply(id = 2, content = "2楼回帖"),
                    ),
            )
        }

        override suspend fun getGroupTopicDetail(topicId: Long): TopicDetail {
            if (shouldThrow) throw RuntimeException("Network error")
            if (groupTopicThrows) throw RuntimeException("Group topic error")
            return TopicDetail(
                id = topicId,
                title = "小组讨论帖详情",
                replies =
                    listOf(
                        TopicReply(id = 10, content = "小组主楼内容"),
                    ),
            )
        }
    }

    @Test
    fun getEpisodeComments_successReturnsData() =
        runTest {
            val fakeService = FakeBangumiCommunityService()
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getEpisodeComments(12345L)
            assertIs<AppResult.Success<List<EpisodeComment>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("精彩一集", result.data.first().content)
        }

    @Test
    fun getEpisodeComments_errorReturnsAppResultError() =
        runTest {
            val fakeService = FakeBangumiCommunityService().apply { shouldThrow = true }
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getEpisodeComments(12345L)
            assertIs<AppResult.Error>(result)
            assertEquals("Network error", result.message)
        }

    @Test
    fun getSubjectComments_successReturnsData() =
        runTest {
            val fakeService = FakeBangumiCommunityService()
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getSubjectComments(496135L)
            assertIs<AppResult.Success<SubjectCommentPage>>(result)
            assertEquals(1, result.data.total)
            assertEquals(
                "神作",
                result.data.data
                    .first()
                    .comment,
            )
        }

    @Test
    fun getSubjectTopics_successReturnsData() =
        runTest {
            val fakeService = FakeBangumiCommunityService()
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getSubjectTopics(496135L)
            assertIs<AppResult.Success<List<SubjectTopic>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("讨论贴1", result.data.first().title)
        }

    @Test
    fun getTopicDetail_subjectTopicSuccessReturnsData() =
        runTest {
            val fakeService = FakeBangumiCommunityService()
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getTopicDetail(38323L, "subject")
            assertIs<AppResult.Success<TopicDetail>>(result)
            assertEquals("条目讨论帖详情", result.data.title)
            assertEquals("主楼内容", result.data.mainPost?.content)
            assertEquals(1, result.data.floorReplies.size)
        }

    @Test
    fun getTopicDetail_fallbackToGroupWhenSubjectNotFound() =
        runTest {
            val fakeService = FakeBangumiCommunityService().apply { subjectTopicThrows = true }
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getTopicDetail(375793L, "subject")
            assertIs<AppResult.Success<TopicDetail>>(result)
            assertEquals("小组讨论帖详情", result.data.title)
        }

    @Test
    fun getTopicDetail_bothFailedReturnsError() =
        runTest {
            val fakeService = FakeBangumiCommunityService().apply { shouldThrow = true }
            val repository = CommunityRepositoryImpl(fakeService)

            val result = repository.getTopicDetail(999999L, "subject")
            assertIs<AppResult.Error>(result)
            assertEquals("Network error", result.message)
        }
}
