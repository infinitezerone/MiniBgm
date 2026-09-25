package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.PendingActionStore
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.PendingAction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserCollectionDto(
    val subjectId: Long,
    val title: String = "",
    val titleCn: String = "",
    val collectionType: String,
    val epStatus: Int,
    val rating: Int = 0,
    val comment: String = "",
    val updatedAt: String = "",
)

/**
 * 追番与收藏进度相关 Koog 智能体工具。
 * 包含严密的 Human-In-The-Loop (HITL) 安全机制：
 * - 只读操作（查询收藏、获取在看列表）自动安全执行；
 * - 写操作（修改条目收藏状态、更新单集打卡进度）严禁静默执行，必须生成 [PendingAction] 提案等待用户在客户端确认后方可执行。
 */
class CollectionTools(
    private val collectionRepository: CollectionRepository,
    private val json: Json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        },
    private val pendingActionStore: PendingActionStore? = null,
) : ToolSet {
    @Tool
    @LLMDescription("Query the current user's collection status and watched episode progress for an anime (READ OPERATION, auto-executes)")
    suspend fun getCollection(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
    ): String {
        if (subjectId <= 0) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        AiToolActivity.report("查询收藏状态", "条目 ID $subjectId")
        return when (val result = collectionRepository.fetchCollection(subjectId)) {
            is AppResult.Success -> {
                val collection = result.data
                if (collection == null) {
                    "Subject ID $subjectId is not currently in user's collection."
                } else {
                    val type = CollectionType.fromValue(collection.type)
                    val dto =
                        UserCollectionDto(
                            subjectId = collection.subjectId,
                            title = collection.subject?.name.orEmpty(),
                            titleCn = collection.subject?.nameCn.orEmpty(),
                            collectionType = type.name,
                            epStatus = collection.epStatus,
                            rating = collection.rate,
                            comment = collection.comment,
                            updatedAt = collection.updatedAt,
                        )
                    json.encodeToString(dto)
                }
            }
            is AppResult.Error -> {
                "Failed to fetch collection for subject ID $subjectId: ${result.throwable.message ?: "Unknown error"}"
            }
            is AppResult.Loading -> {
                "Fetching collection for subject ID $subjectId in progress, please retry."
            }
        }
    }

    @Tool
    @LLMDescription("Query list of anime currently being watched by the user (READ OPERATION, auto-executes)")
    suspend fun getWatchingList(): String {
        AiToolActivity.report("获取在看列表", "当前追番中条目")
        val collections = collectionRepository.getCollectionsByTypeStream(CollectionType.DOING).first()
        if (collections.isEmpty()) {
            return "User has no anime marked as currently watching (DOING)."
        }
        val dtos =
            collections.map {
                UserCollectionDto(
                    subjectId = it.subjectId,
                    title = it.subject?.name.orEmpty(),
                    titleCn = it.subject?.nameCn.orEmpty(),
                    collectionType = CollectionType.DOING.name,
                    epStatus = it.epStatus,
                    rating = it.rate,
                    comment = it.comment,
                    updatedAt = it.updatedAt,
                )
            }
        return json.encodeToString(dtos)
    }

    @Tool
    @LLMDescription(
        "Propose updating an anime's collection status (WISH, DOING, COLLECT, ON_HOLD, DROPPED), rating, or comment. (HITL SAFE: returns a PendingAction proposal without mutating data)",
    )
    suspend fun proposeUpdateCollection(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
        @LLMDescription("Anime title or name for human context")
        subjectTitle: String = "",
        @LLMDescription(
            "Target collection status: DOING (watching/在看), WISH (want to watch/想看), COLLECT (completed/看过), ON_HOLD (on hold/搁置), DROPPED (dropped/抛弃)",
        )
        collectionType: String,
        @LLMDescription("Optional rating score between 1 and 10")
        rating: Int? = null,
        @LLMDescription("Optional brief review comment")
        comment: String? = null,
        @LLMDescription("Whether to mark this collection as private (only visible to self)")
        isPrivate: Boolean = false,
    ): String {
        if (subjectId <= 0) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        AiToolActivity.report("生成收藏提案", "条目 $subjectId -> $collectionType")

        val trimmedType = collectionType.uppercase().trim()
        val resolvedType =
            when (trimmedType) {
                "DOING", "在看", "WATCHING" -> CollectionType.DOING
                "WISH", "想看", "WANT_TO_WATCH", "PLAN_TO_WATCH" -> CollectionType.WISH
                "COLLECT", "看过", "COMPLETED", "WATCHED" -> CollectionType.COLLECT
                "ON_HOLD", "搁置", "HOLD", "PAUSED" -> CollectionType.ON_HOLD
                "DROPPED", "抛弃", "DROP" -> CollectionType.DROPPED
                else -> {
                    return "Invalid collection type '$collectionType'. " +
                        "Valid options: DOING (在看), WISH (想看), COLLECT (看过), ON_HOLD (搁置), DROPPED (抛弃)."
                }
            }

        if (rating != null && rating !in 1..10) {
            return "Invalid rating: $rating. Rating must be an integer between 1 and 10."
        }

        val actionId = "act_coll_${TimeUtils.nowEpochMillis()}_$subjectId"
        val desc =
            "Update collection status for '$subjectTitle' (ID: $subjectId) to ${resolvedType.name}" +
                (rating?.let { ", rating: $it/10" } ?: "") +
                (comment?.let { ", comment: '$it'" } ?: "")

        val pendingAction =
            PendingAction.UpdateCollection(
                actionId = actionId,
                subjectId = subjectId,
                subjectTitle = subjectTitle,
                collectionType = resolvedType,
                rating = rating,
                comment = comment,
                isPrivate = isPrivate,
                description = desc,
            )

        val proposal =
            ActionProposal(
                status = "PENDING_CONFIRMATION",
                message = "Action proposal generated. Awaiting human confirmation before applying changes to Bangumi.",
                action = pendingAction,
            )

        pendingActionStore?.add(pendingAction)

        return json.encodeToString(proposal)
    }

    @Tool
    @LLMDescription(
        "Propose updating episode watch progress (e.g. mark episode N as watched). (HITL SAFE: returns a PendingAction proposal without mutating data)",
    )
    suspend fun proposeUpdateEpisodeProgress(
        @LLMDescription("Bangumi subject ID")
        subjectId: Long,
        @LLMDescription("Anime title or name for human context")
        subjectTitle: String = "",
        @LLMDescription("Episode number (1-based)")
        episodeNumber: Int,
        @LLMDescription("True to mark as watched, false to mark as unwatched")
        isWatched: Boolean = true,
    ): String {
        if (subjectId <= 0) {
            return "Invalid subject ID: $subjectId. Subject ID must be a positive integer."
        }
        if (episodeNumber <= 0) {
            return "Invalid episode number: $episodeNumber. Episode number must be a positive integer (1-based)."
        }
        AiToolActivity.report("生成打卡提案", "条目 $subjectId 第 $episodeNumber 集")

        val actionId = "act_ep_${TimeUtils.nowEpochMillis()}_${subjectId}_ep$episodeNumber"
        val statusText = if (isWatched) "watched" else "unwatched"
        val desc = "Mark episode $episodeNumber of '$subjectTitle' (ID: $subjectId) as $statusText"

        val pendingAction =
            PendingAction.UpdateEpisode(
                actionId = actionId,
                subjectId = subjectId,
                subjectTitle = subjectTitle,
                episodeNumber = episodeNumber,
                isWatched = isWatched,
                description = desc,
            )

        val proposal =
            ActionProposal(
                status = "PENDING_CONFIRMATION",
                message = "Action proposal generated. Awaiting human confirmation before applying changes to Bangumi.",
                action = pendingAction,
            )

        pendingActionStore?.add(pendingAction)

        return json.encodeToString(proposal)
    }
}
