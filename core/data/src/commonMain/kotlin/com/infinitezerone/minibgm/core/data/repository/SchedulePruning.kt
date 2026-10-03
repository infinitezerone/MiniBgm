package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity

/**
 * 判定一条 `bgm_data` 来源的条目是否已过期、可从时刻表清理（stale prunable）：
 * 本周及未来都不会再有任何播出事件，该从时刻表剔除。
 * 非 `bgm_data` 来源（如 AniList 名单）一律不判，直接返回 false。
 *
 * 两支判据：**有事件**时看是否已播完全部集数、或总放送周期已过；**无事件**时看距最后事件／开播日
 * 是否已过去足够久（14 天）。
 *
 * 刻意做成**无依赖的顶层纯函数**，而不是 [ScheduleRepositoryImpl] 的 private 成员：它只依赖传入的
 * 实体、事件与当前时刻，抽出来才能逐分支钉住。留在类里当私有成员时行覆盖率只有 51.9%，
 * 在 CRAP 门禁（阈值 30，CC=26）下是 DANGER。
 */
internal fun isStalePrunableSchedule(
    entity: AirScheduleEntity,
    events: List<AirEventEntity>,
    nowMillis: Long,
): Boolean {
    if (entity.source != AirScheduleEntity.SOURCE_BGM_DATA) return false
    val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(nowMillis)
    if (events.isNotEmpty()) {
        val hasActiveOrFutureEvent =
            events.any { event ->
                val millis = TimeUtils.epochMillisOfIso(event.airAtUtc) ?: 0L
                millis >= weekStartMillis
            }
        if (hasActiveOrFutureEvent) return false

        // 没有本周或未来事件：仅当确已播完全部集数或总放送周期已结束时才视为过期条目
        if (entity.totalEpisodes == 1) return true
        if (entity.totalEpisodes > 1) {
            if (events.size >= entity.totalEpisodes) return true
            val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc)
            if (beginMillis != null && beginMillis + entity.totalEpisodes * WEEK_MILLIS < weekStartMillis) {
                return true
            }
            return false
        }
        // totalEpisodes <= 0
        val latestEventMillis = events.maxOfOrNull { TimeUtils.epochMillisOfIso(it.airAtUtc) ?: 0L } ?: 0L
        return latestEventMillis < weekStartMillis - 14 * DAY_MILLIS
    }

    // events 为空
    if (entity.totalEpisodes == 1) {
        val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: return true
        return beginMillis < weekStartMillis
    }
    if (entity.totalEpisodes > 1) {
        val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc)
        if (beginMillis != null && beginMillis + entity.totalEpisodes * WEEK_MILLIS < weekStartMillis) {
            return true
        }
        return false
    }
    val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: return false
    return beginMillis < weekStartMillis - 14 * DAY_MILLIS
}
