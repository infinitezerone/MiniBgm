package com.infinitezerone.minibgm.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.data.repository.CommunityRepository
import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommunityLikeTarget
import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讨论帖详情一次性单发事件
 */
sealed interface TopicDetailUiEvent {
    data class ShowSnackbar(
        val message: String,
    ) : TopicDetailUiEvent
}

/**
 * 一次加载会话的进度快照：只承载"加载进行到哪了"，不含数据本身。
 * 数据（[TopicDetail]）由成功响应单独写入 [loadedDetail]，失败不清空已展示内容。
 */
private data class TopicLoad(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/**
 * 讨论帖详情 ViewModel —— 响应式 UDF 迁移版：
 * 1. 对外只读 `uiState: StateFlow<TopicDetailUiState>`，状态 = `combine(加载会话, 已装载数据, 登录态)` 的投影；
 * 2. 刷新是意图（`MutableSharedFlow<Boolean>`，payload = 是否用户下拉）：意图流经
 *    `flatMapLatest(getTopicDetail as flow)` 换挡——新刷新一到，旧请求整链取消，
 *    不需要手动 Job 判空判序（refreshJob 一律不允许）；
 * 3. 已装载数据 [loadedDetail] 只在成功响应时覆盖，刷新失败保留旧值（非破坏性刷新）；
 * 4. currentUserId 是 authRepository 的仓库流，直接 `combine` 汇入，不另存副本。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicDetailViewModel(
    val topicId: Long,
    val type: String = "subject",
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository? = null,
) : ViewModel() {
    private val _events = Channel<TopicDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<TopicDetailUiEvent> = _events.receiveAsFlow()

    // ── 输入：刷新意图。payload = 是否用户下拉刷新 ──
    private val refreshIntent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** 最近一次成功装载数据；成功响应覆盖，失败与换挡都保留旧值（刷新失败不清屏） */
    private val loadedDetail = MutableStateFlow<TopicDetail?>(null)

    /** 加载会话：初始一次 + 每次刷新意图换挡重发 `getTopicDetail` */
    private val loadState: Flow<TopicLoad> =
        refreshIntent
            .onStart { emit(false) }
            .flatMapLatest { isUserPull ->
                flow { emit(communityRepository.getTopicDetail(topicId, type)) }
                    .onEach { result ->
                        if (result is AppResult.Success) loadedDetail.value = result.data
                    }.map { result ->
                        when (result) {
                            is AppResult.Success -> TopicLoad()
                            is AppResult.Error -> TopicLoad(error = result.message.ifBlank { "加载讨论帖失败" })
                            is AppResult.Loading -> TopicLoad(isLoading = true, isRefreshing = isUserPull)
                        }
                    }.onStart { emit(TopicLoad(isLoading = true, isRefreshing = isUserPull)) }
            }

    private val sortOrder = MutableStateFlow(CommentSortOrder.ASCENDING)

    val uiState: StateFlow<TopicDetailUiState> =
        combine(
            loadState,
            loadedDetail,
            authRepository?.activeUserId ?: flowOf(null),
            sortOrder,
        ) { load, detail, userId, order ->
            TopicDetailUiState(
                isLoading = load.isLoading && detail == null,
                isRefreshing = load.isRefreshing,
                topicDetail = detail,
                error = load.error,
                currentUserId = userId,
                sortOrder = order,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TopicDetailUiState())

    /** 刷新讨论帖内容与回帖流 */
    fun refresh(isUserPullToRefresh: Boolean = false) {
        refreshIntent.tryEmit(isUserPullToRefresh)
    }

    /** 切换楼层回帖排序规则 */
    fun setSortOrder(order: CommentSortOrder) {
        sortOrder.value = order
    }

    /**
     * 切换主楼（1 楼）表情表态
     */
    fun toggleMainPostReaction(reaction: CommentReaction) {
        val mainPost = loadedDetail.value?.mainPost ?: return
        toggleReaction(mainPost, reaction)
    }

    fun toggleMainPostReaction(reactionValue: Int) {
        val mainPost = loadedDetail.value?.mainPost ?: return
        toggleReaction(mainPost, reactionValue)
    }

    fun toggleReaction(
        reply: TopicReply,
        reactionValue: Int,
    ) {
        val existing = reply.reactions.find { it.value == reactionValue }
        if (existing != null) {
            toggleReaction(reply, existing)
        } else {
            toggleReaction(reply, CommentReaction(value = reactionValue, users = emptyList()))
        }
    }

    /**
     * 切换自己对某楼层某条表情表态的状态：
     * 未登录提示登录；已在该类型表态过 → 取消；否则以该表情类型加入。
     *
     * 规范第 5 条：命令密集型乐观会话——表态是"改写已装载数据 → 提交 → 失败整树回滚"
     * 的显式命令层，社区仓库无表态流可回读，无法投影化，故保留本地乐观改写
     * （经状态流声明式下发，含楼中楼递归，见 [adjustTopicReplyReactions]）；成功不整流刷新。
     */
    fun toggleReaction(
        reply: TopicReply,
        reaction: CommentReaction,
    ) {
        val userId = uiState.value.currentUserId
        if (userId == null) {
            _events.trySend(TopicDetailUiEvent.ShowSnackbar("请先登录后再表态"))
            return
        }
        val target = if (type == "group") CommunityLikeTarget.GROUP_POST else CommunityLikeTarget.SUBJECT_POST
        val reacted = reaction.users.any { it.id == userId }
        val previousDetail = loadedDetail.value
        loadedDetail.update { detail ->
            detail?.copy(
                replies = adjustTopicReplyReactions(detail.replies, reply.id, reaction.value, userId, removing = reacted),
            )
        }
        viewModelScope.launch {
            val result =
                if (reacted) {
                    communityRepository.removeLike(target, reply.id)
                } else {
                    communityRepository.setLike(target, reply.id, reaction.value)
                }
            when (result) {
                is AppResult.Success -> {
                    _events.trySend(TopicDetailUiEvent.ShowSnackbar(if (reacted) "已取消表态" else "已表态"))
                }
                is AppResult.Error -> {
                    loadedDetail.value = previousDetail
                    _events.trySend(TopicDetailUiEvent.ShowSnackbar(result.message.ifBlank { "表态失败" }))
                }
                is AppResult.Loading -> Unit
            }
        }
    }
}
