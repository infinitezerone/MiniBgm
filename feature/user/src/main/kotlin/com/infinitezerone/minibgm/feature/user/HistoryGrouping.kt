package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackHistory

/**
 * 观看记录的番剧聚合视图：同一 subjectId 的多集折叠成一张卡。
 *
 * 平铺按分集展示时，追一部 12 集的番会让历史列表顶部出现 12 行同一部番；
 * 聚合后一个番只占一行，展开才看分集。
 *
 * @property subjectId 番剧 ID；未知（<=0）时按分集各自成组，避免把无关记录并在一起
 * @property subjectName 番名（回退到分集名，避免空白标题）
 * @property coverUrl 封面（优先最近一集，空时回退组内其它记录）
 * @property latest 组内最近观看的一集，点击卡片按它续播
 * @property episodes 组内分集：有集数按集数升序，集数未知的按观看时间倒序
 * @property lastWatchedAt 组内最近观看时间，用于组间排序
 */
data class HistoryGroup(
    val subjectId: Long,
    val subjectName: String,
    val coverUrl: String,
    val latest: PlaybackHistory,
    val episodes: List<PlaybackHistory>,
    val lastWatchedAt: Long,
) {
    val episodeCount: Int get() = episodes.size
}

/**
 * 把按观看时间倒序的分集记录折叠成番剧分组。
 *
 * 纯函数：不依赖仓库与协程，便于直接对边界（未知 subjectId、空封面、重复集数）下断言。
 */
internal fun buildHistoryGroups(history: List<PlaybackHistory>): List<HistoryGroup> {
    if (history.isEmpty()) return emptyList()
    return history
        .groupBy { entry -> if (entry.subjectId > 0L) "s:${entry.subjectId}" else "e:${entry.episodeId}" }
        .map { (_, entries) ->
            val latest = entries.maxByOrNull { it.updatedAt } ?: entries.first()
            HistoryGroup(
                subjectId = latest.subjectId,
                subjectName =
                    entries
                        .firstNotNullOfOrNull { it.subjectName.takeIf(String::isNotBlank) }
                        ?: latest.episodeName,
                coverUrl =
                    entries
                        .map { it.coverUrl }
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty(),
                latest = latest,
                episodes =
                    entries.sortedWith(
                        compareBy<PlaybackHistory> { if (it.episodeSort > 0f) it.episodeSort else Float.MAX_VALUE }
                            .thenByDescending { it.updatedAt },
                    ),
                lastWatchedAt = latest.updatedAt,
            )
        }.sortedByDescending { it.lastWatchedAt }
}
