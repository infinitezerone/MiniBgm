package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopic
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCommunityRepository : CommunityRepository {
    private val episodeCommentsState = MutableStateFlow<Map<Long, List<EpisodeComment>>>(emptyMap())
    private val subjectCommentsState = MutableStateFlow<Map<Long, SubjectCommentPage>>(emptyMap())
    private val subjectTopicsState = MutableStateFlow<Map<Long, List<SubjectTopic>>>(emptyMap())

    var getEpisodeCommentsResult: AppResult<List<EpisodeComment>>? = null
    var getEpisodeCommentsCallCount: Int = 0
        private set
    var getSubjectCommentsResult: AppResult<SubjectCommentPage>? = null
    var getSubjectCommentsCallCount: Int = 0
        private set
    var getSubjectTopicsResult: AppResult<List<SubjectTopic>>? = null
    var getSubjectTopicsCallCount: Int = 0
        private set

    fun setEpisodeComments(
        episodeId: Long,
        comments: List<EpisodeComment>,
    ) {
        episodeCommentsState.value = episodeCommentsState.value + (episodeId to comments)
    }

    fun setSubjectComments(
        subjectId: Long,
        page: SubjectCommentPage,
    ) {
        subjectCommentsState.value = subjectCommentsState.value + (subjectId to page)
    }

    fun setSubjectTopics(
        subjectId: Long,
        topics: List<SubjectTopic>,
    ) {
        subjectTopicsState.value = subjectTopicsState.value + (subjectId to topics)
    }

    override suspend fun getEpisodeComments(episodeId: Long): AppResult<List<EpisodeComment>> {
        getEpisodeCommentsCallCount++
        getEpisodeCommentsResult?.let { return it }
        return AppResult.Success(episodeCommentsState.value[episodeId].orEmpty())
    }

    override suspend fun getSubjectComments(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): AppResult<SubjectCommentPage> {
        getSubjectCommentsCallCount++
        getSubjectCommentsResult?.let { return it }
        return AppResult.Success(subjectCommentsState.value[subjectId] ?: SubjectCommentPage())
    }

    override suspend fun getSubjectTopics(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): AppResult<List<SubjectTopic>> {
        getSubjectTopicsCallCount++
        getSubjectTopicsResult?.let { return it }
        return AppResult.Success(subjectTopicsState.value[subjectId].orEmpty())
    }
}
