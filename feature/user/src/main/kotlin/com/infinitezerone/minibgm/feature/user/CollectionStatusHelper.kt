package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection

/**
 * 收藏条目的连载与囤番筛选维度（全部、连载中、已完结、囤番中）
 */
enum class CollectionAirFilter(
    val label: String,
) {
    ALL("全部"),
    AIRING("连载中"),
    FINISHED("已完结"),
    BINGE("囤番中"),
}

/**
 * 判断条目是否已完结（全剧终）。
 * 纯本地计算，基于首播日期、总集数与已看状态，零外部网络 API 调用。
 */
fun UserCollection.isFinished(): Boolean {
    // 1. 若处于「看过」分类，则判定为已完结
    if (type == CollectionType.COLLECT.value) return true

    val total = subject?.totalEpisodes?.takeIf { it > 0 } ?: subject?.eps?.takeIf { it > 0 } ?: 0
    // 2. 若总集数确定且用户已看完全部集数
    if (total > 0 && epStatus >= total) return true

    // 3. 针对动画/剧集：基于首播日与总集数通过 TimeUtils 推算当前已播话数
    val airDateStr = subject?.date?.ifBlank { subject?.airDate }.orEmpty()
    if (airDateStr.isNotBlank() && total > 0) {
        val aired = TimeUtils.calculateCurrentEpisode(airDateStr)
        if (aired >= total) return true
    }

    return false
}

/**
 * 获取当前已播话数（封顶总话数）
 */
fun UserCollection.currentAiredEpisode(): Int {
    val total = subject?.totalEpisodes?.takeIf { it > 0 } ?: subject?.eps?.takeIf { it > 0 } ?: 0
    val airDateStr = subject?.date?.ifBlank { subject?.airDate }.orEmpty()
    if (airDateStr.isBlank()) return if (total > 0) total else 0
    val aired = TimeUtils.calculateCurrentEpisode(airDateStr)
    return if (total > 0) aired.coerceAtMost(total) else aired
}

/**
 * 获取展示在条目卡片上的状态徽章文案
 */
fun UserCollection.airStatusBadge(isBinge: Boolean): String {
    val total = subject?.totalEpisodes?.takeIf { it > 0 } ?: subject?.eps?.takeIf { it > 0 } ?: 0
    if (isFinished()) {
        return if (total > 0) "已完结 · 全 $total 话" else "已完结"
    }
    if (isBinge) return "囤番中"
    val aired = currentAiredEpisode()
    return if (total > 0 && aired > 0) {
        "连载至 $aired/$total 话"
    } else if (aired > 0) {
        "连载至第 $aired 话"
    } else {
        "连载中"
    }
}
