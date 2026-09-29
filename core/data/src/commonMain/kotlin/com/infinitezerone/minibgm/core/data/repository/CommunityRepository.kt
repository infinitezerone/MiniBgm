package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.asAppResult
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.network.BangumiCommunityService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage

/**
 * 社区数据仓库（单集吐槽、条目全站短评流、条目讨论版、讨论帖详情、表态）
 */
interface CommunityRepository {
    /** 获取单集吐槽列表 */
    suspend fun getEpisodeComments(episodeId: Long): AppResult<List<EpisodeComment>>

    /** 获取条目全站短评流 */
    suspend fun getSubjectComments(
        subjectId: Long,
        limit: Int = 20,
        offset: Int = 0,
    ): AppResult<SubjectCommentPage>

    /** 获取条目关联讨论版帖子列表 */
    suspend fun getSubjectTopics(
        subjectId: Long,
        limit: Int = 10,
        offset: Int = 0,
    ): AppResult<List<SubjectTopic>>

    /** 获取讨论帖详情（包含主楼正文、关联条目与楼层回复） */
    suspend fun getTopicDetail(
        topicId: Long,
        type: String = "subject",
    ): AppResult<TopicDetail>

    /**
     * 对楼层/吐槽添加一个表情表态（需登录态；[reactionValue] 为 bgm.tv 表情类型 id）。
     */
    suspend fun setLike(
        target: CommunityLikeTarget,
        id: Long,
        reactionValue: Int,
    ): AppResult<Unit>

    /** 取消自己在该楼层/吐槽上的表态 */
    suspend fun removeLike(
        target: CommunityLikeTarget,
        id: Long,
    ): AppResult<Unit>
}

class CommunityRepositoryImpl(
    private val communityService: BangumiCommunityService,
) : CommunityRepository {
    override suspend fun getEpisodeComments(episodeId: Long): AppResult<List<EpisodeComment>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取单集吐槽") }) {
            communityService.getEpisodeComments(episodeId)
        }

    override suspend fun getSubjectComments(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): AppResult<SubjectCommentPage> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取条目短评") }) {
            communityService.getSubjectComments(subjectId, limit, offset)
        }

    override suspend fun getSubjectTopics(
        subjectId: Long,
        limit: Int,
        offset: Int,
    ): AppResult<List<SubjectTopic>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取条目讨论版") }) {
            communityService.getSubjectTopics(subjectId, limit, offset).data
        }

    override suspend fun getTopicDetail(
        topicId: Long,
        type: String,
    ): AppResult<TopicDetail> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取讨论帖详情") }) {
            if (type == "group") {
                try {
                    communityService.getGroupTopicDetail(topicId)
                } catch (_: BgmNetworkException.NotFound) {
                    communityService.getSubjectTopicDetail(topicId)
                }
            } else {
                try {
                    communityService.getSubjectTopicDetail(topicId)
                } catch (_: BgmNetworkException.NotFound) {
                    communityService.getGroupTopicDetail(topicId)
                }
            }
        }

    override suspend fun setLike(
        target: CommunityLikeTarget,
        id: Long,
        reactionValue: Int,
    ): AppResult<Unit> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("表态") }) {
            communityService.setLike(target, id, reactionValue)
            Unit
        }

    override suspend fun removeLike(
        target: CommunityLikeTarget,
        id: Long,
    ): AppResult<Unit> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("取消表态") }) {
            communityService.removeLike(target, id)
            Unit
        }
}
