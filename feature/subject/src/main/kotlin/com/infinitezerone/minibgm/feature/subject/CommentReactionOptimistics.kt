package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.CommentReaction
import com.infinitezerone.minibgm.core.model.CommentReactionUser
import com.infinitezerone.minibgm.core.model.TopicReply

/**
 * 表态的本地乐观口径（单集吐槽与讨论帖楼层共用）：
 * [removing] = true 时把 [userId] 从对应 value 的 users 中摘除（空组整体移除），
 * false 时把 [userId] 并入对应 value 的 users（无该 value 组则新建）。
 * 只做纯列表变换，供 ViewModel 经状态流声明式下发，绝不原地改入参。
 */
internal fun adjustReactions(
    reactions: List<CommentReaction>,
    reactionValue: Int,
    userId: Long,
    removing: Boolean,
): List<CommentReaction> {
    val self = CommentReactionUser(id = userId)
    return if (removing) {
        reactions.mapNotNull { reaction ->
            if (reaction.value != reactionValue) {
                reaction
            } else {
                val users = reaction.users.filterNot { it.id == userId }
                if (users.isEmpty()) null else reaction.copy(users = users)
            }
        }
    } else {
        val index = reactions.indexOfFirst { it.value == reactionValue }
        if (index < 0) {
            reactions + CommentReaction(value = reactionValue, users = listOf(self))
        } else {
            val target = reactions[index]
            if (target.users.any { it.id == userId }) {
                reactions
            } else {
                reactions.toMutableList().apply { this[index] = target.copy(users = target.users + self) }
            }
        }
    }
}

/**
 * 递归调整楼层树（含楼中楼）中 [replyId] 的表态；未命中时原样返回原元素，
 * 命中路径上的祖先节点逐层 copy，其余分支保持同一实例。
 */
internal fun adjustTopicReplyReactions(
    replies: List<TopicReply>,
    replyId: Long,
    reactionValue: Int,
    userId: Long,
    removing: Boolean,
): List<TopicReply> =
    replies.map { reply ->
        if (reply.id != replyId) {
            if (reply.replies.isEmpty()) {
                reply
            } else {
                val children = adjustTopicReplyReactions(reply.replies, replyId, reactionValue, userId, removing)
                if (children === reply.replies) reply else reply.copy(replies = children)
            }
        } else {
            reply.copy(reactions = adjustReactions(reply.reactions, reactionValue, userId, removing))
        }
    }
