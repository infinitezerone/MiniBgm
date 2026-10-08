package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.TopicDetail
import com.infinitezerone.minibgm.core.model.TopicReply
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder

/**
 * 讨论帖详情界面 UI 状态
 */
data class TopicDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val topicDetail: TopicDetail? = null,
    val error: String? = null,
    /** 当前登录用户 id（null = 未登录），用于判定楼层表态是否为己方 */
    val currentUserId: Long? = null,
    val sortOrder: CommentSortOrder = CommentSortOrder.ASCENDING,
) {
    /**
     * 按 [sortOrder] 排序的楼层回帖列表（包含真实楼层序号，从 #2 楼起算）。
     * 正序：2楼 -> 3楼 -> ...
     * 倒序：尾楼 -> ... -> 2楼
     * 热门：按表情表态总数降序，相同保留原楼层顺序
     */
    val sortedFloorReplies: List<Pair<Int, TopicReply>>
        get() {
            val original = topicDetail?.floorReplies.orEmpty()
            if (original.isEmpty()) return emptyList()
            val withFloors = original.mapIndexed { index, reply -> (index + 2) to reply }
            return when (sortOrder) {
                CommentSortOrder.ASCENDING -> withFloors
                CommentSortOrder.DESCENDING -> withFloors.reversed()
                CommentSortOrder.HOT -> withFloors.sortedByDescending { it.second.reactions.sumOf { r -> r.count } }
            }
        }
}
