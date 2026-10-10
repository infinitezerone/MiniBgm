package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单集吐槽排序算法（按热门 / 楼层正序 / 楼层倒序） */
fun sortEpisodeComments(
    comments: List<EpisodeComment>,
    sortOrder: CommentSortOrder,
): List<EpisodeComment> =
    when (sortOrder) {
        CommentSortOrder.HOT ->
            comments.sortedWith(
                compareByDescending<EpisodeComment> { it.reactions.sumOf { r -> r.count } }
                    .thenBy { if (it.floor > 0) it.floor else Int.MAX_VALUE },
            )
        CommentSortOrder.ASCENDING ->
            comments.sortedBy { if (it.floor > 0) it.floor else Int.MAX_VALUE }
        CommentSortOrder.DESCENDING ->
            comments.sortedByDescending { it.floor }
    }

/**
 * 评论区 UI 状态收敛模型（由 [EpisodeCommentsDelegate] 管理）
 */
data class EpisodeCommentsState(
    val currentEpisodeId: Long = 0L,
    val comments: List<EpisodeComment> = emptyList(),
    val rawComments: List<EpisodeComment> = emptyList(),
    val commentCount: Int = 0,
    val sortOrder: CommentSortOrder = CommentSortOrder.HOT,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserId: Long? = null,
    val hasSettled: Boolean = false,
)

/**
 * 单集吐槽与评论业务 Delegate（单集详情页与播放器页面共用）：
 * 统一管理单集短评拉取、楼层兜底格式化、排序重整、鉴权守卫、乐观表态更新与网络失败回滚。
 */
class EpisodeCommentsDelegate(
    private val communityRepository: CommunityRepository?,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(EpisodeCommentsState())
    val state: StateFlow<EpisodeCommentsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** 拉取指定分集的吐槽短评列表 */
    fun loadComments(
        episodeId: Long,
        force: Boolean = false,
    ) {
        if (episodeId <= 0L || communityRepository == null) return
        val isDifferentEpisode = episodeId != _state.value.currentEpisodeId
        if (!force && !isDifferentEpisode && (loadJob?.isActive == true || _state.value.rawComments.isNotEmpty())) return

        loadJob?.cancel()
        loadJob =
            scope.launch {
                val uid = authRepository.activeUserId.firstOrNull()
                _state.update {
                    it.copy(
                        currentEpisodeId = episodeId,
                        isLoading = true,
                        error = null,
                        currentUserId = uid,
                        comments = if (isDifferentEpisode) emptyList() else it.comments,
                        rawComments = if (isDifferentEpisode) emptyList() else it.rawComments,
                    )
                }
                when (val result = communityRepository.getEpisodeComments(episodeId)) {
                    is AppResult.Success -> {
                        val parsed =
                            result.data.mapIndexed { index, comment ->
                                if (comment.floor <= 0) comment.copy(floor = index + 1) else comment
                            }
                        _state.update { current ->
                            current.copy(
                                currentEpisodeId = episodeId,
                                rawComments = parsed,
                                comments = sortEpisodeComments(parsed, current.sortOrder),
                                commentCount = parsed.size,
                                isLoading = false,
                                error = null,
                                hasSettled = true,
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _state.update { current ->
                            current.copy(
                                currentEpisodeId = episodeId,
                                isLoading = false,
                                error = result.message.ifBlank { "加载评论失败" },
                                hasSettled = true,
                            )
                        }
                    }
                    is AppResult.Loading -> Unit
                }
            }
    }

    /** 切换排序方式 */
    fun setSortOrder(order: CommentSortOrder) {
        _state.update { current ->
            current.copy(
                sortOrder = order,
                comments = sortEpisodeComments(current.rawComments, order),
            )
        }
    }

    /** 切换表态状态（按已有 Reaction） */
    fun toggleCommentReaction(
        comment: EpisodeComment,
        reaction: CommentReaction,
    ) {
        val userId = _state.value.currentUserId
        if (userId == null) {
            onMessage("请先登录后再表态")
            return
        }
        val reacted = reaction.users.any { it.id == userId }
        val previousRaw = _state.value.rawComments
        val previousSorted = _state.value.comments

        // 乐观更新
        val updatedRaw =
            previousRaw.map { c ->
                if (c.id == comment.id) {
                    c.copy(reactions = adjustReactions(c.reactions, reaction.value, userId, removing = reacted))
                } else {
                    c
                }
            }
        _state.update { current ->
            current.copy(
                rawComments = updatedRaw,
                comments = sortEpisodeComments(updatedRaw, current.sortOrder),
            )
        }

        scope.launch {
            val result =
                withContext(NonCancellable) {
                    if (reacted) {
                        communityRepository?.removeLike(CommunityLikeTarget.EPISODE_COMMENT, comment.id)
                            ?: AppResult.Error(IllegalStateException("No community repository"))
                    } else {
                        communityRepository?.setLike(CommunityLikeTarget.EPISODE_COMMENT, comment.id, reaction.value)
                            ?: AppResult.Error(IllegalStateException("No community repository"))
                    }
                }
            when (result) {
                is AppResult.Success -> {
                    onMessage(if (reacted) "已取消表态" else "已表态")
                }
                is AppResult.Error -> {
                    // 回滚原状态
                    _state.update { current ->
                        current.copy(
                            rawComments = previousRaw,
                            comments = previousSorted,
                        )
                    }
                    onMessage(result.message.ifBlank { "表态失败" })
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    /** 切换表态状态（按表情数值） */
    fun toggleCommentReaction(
        comment: EpisodeComment,
        reactionValue: Int,
    ) {
        val existing = comment.reactions.firstOrNull { it.value == reactionValue }
        toggleCommentReaction(comment, existing ?: CommentReaction(value = reactionValue))
    }
}
