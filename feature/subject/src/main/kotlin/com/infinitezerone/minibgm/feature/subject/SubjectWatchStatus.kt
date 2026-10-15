package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeFutureAir

/**
 * 条目分集整体播放与追番状态机：
 * 严格基于客观播出事实（AniList/Bgm 播出日期）与用户打卡进度综合推导，
 * 杜绝在未开播、连载追平、全剧完结时粗暴回退至第 1 话的假续看缺陷。
 */
sealed interface SubjectWatchStatus {
    /** 全部正篇均未开播（新番预告期） */
    data class Upcoming(
        val firstEpisode: Episode,
        val airDate: String,
    ) : SubjectWatchStatus

    /** 连载中或已完结，且存在已播出但用户尚未看过的分集（黄金续看状态） */
    data class ContinueWatching(
        val nextEpisode: Episode,
        val remainingAiredEpisodes: Int,
    ) : SubjectWatchStatus

    /** 连载中，当前所有已播出的分集已全部追平，等待下一集播出 */
    data class CaughtUp(
        val lastWatchedEpisode: Episode?,
        val nextAirEpisode: Episode?,
        val nextAirDate: String?,
    ) : SubjectWatchStatus

    /** 全部分集均已播出且用户已全部看完 */
    data class Completed(
        val totalEpisodes: Int,
        val lastEpisode: Episode?,
    ) : SubjectWatchStatus

    /** 暂无分集数据或非分集类型 */
    data object None : SubjectWatchStatus
}

/**
 * 纯函数：根据分集列表与用户打卡进度，计算当前作品的追番可看状态。
 *
 * @param episodes 作品关联的所有分集（含正篇与 SP/OP/ED 等）
 * @param collection 当前用户的收藏状态记录（为 null 表示未登录或未收藏）
 * @param nowMillis 当前时间戳（毫秒），默认为系统当前时间
 */
fun computeSubjectWatchStatus(
    episodes: List<Episode>,
    collection: UserCollection?,
    nowMillis: Long = TimeUtils.nowEpochMillis(),
): SubjectWatchStatus {
    val mainEpisodes = episodes.filter { it.isMain }.sortedBy { it.sort }
    if (mainEpisodes.isEmpty()) return SubjectWatchStatus.None

    val epStatus = collection?.epStatus ?: 0
    val airedEpisodes = mainEpisodes.filter { !isEpisodeFutureAir(it, nowMillis) }

    // 1. 全部正篇分集均在未来，尚未开播
    if (airedEpisodes.isEmpty()) {
        val first = mainEpisodes.first()
        return SubjectWatchStatus.Upcoming(
            firstEpisode = first,
            airDate = first.airdate,
        )
    }

    // 2. 存在已播出且编号大于打卡进度的分集（未追平）
    val nextUnwatched = airedEpisodes.firstOrNull { it.episodeInt > epStatus }
    if (nextUnwatched != null) {
        val remaining = airedEpisodes.count { it.episodeInt >= nextUnwatched.episodeInt }
        return SubjectWatchStatus.ContinueWatching(
            nextEpisode = nextUnwatched,
            remainingAiredEpisodes = remaining,
        )
    }

    // 3. 所有已播出的分集均已看过：检查后续是否还有未播出的分集
    val futureEpisodes = mainEpisodes.filter { isEpisodeFutureAir(it, nowMillis) }
    if (futureEpisodes.isNotEmpty()) {
        val nextFuture = futureEpisodes.first()
        return SubjectWatchStatus.CaughtUp(
            lastWatchedEpisode = airedEpisodes.lastOrNull(),
            nextAirEpisode = nextFuture,
            nextAirDate = nextFuture.airdate.ifBlank { null },
        )
    }

    // 4. 所有分集均已播出且均已打卡看完
    return SubjectWatchStatus.Completed(
        totalEpisodes = mainEpisodes.size,
        lastEpisode = airedEpisodes.lastOrNull(),
    )
}
