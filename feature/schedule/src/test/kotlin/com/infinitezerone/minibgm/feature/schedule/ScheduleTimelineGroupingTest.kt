package com.infinitezerone.minibgm.feature.schedule

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.feature.schedule.components.groupTimelineSchedules
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimelineGroupingTest {

    @Test
    fun groupTimelineSchedules_aggregatesMultipleEpisodesForSameSubject() {
        // 模拟《赛博朋克：边缘行者》10 集同时放出
        val batchEpisodes = (1..10).map { ep ->
            AirSchedule(
                bgmId = 9999L,
                title = "Cyberpunk: Edgerunners",
                titleCn = "赛博朋克：边缘行者",
                timeCst = "15:00",
                nextEpisodeNumber = ep,
            )
        }

        // 加上同一时段的另一部单集番剧
        val singleEpisodeAnime = AirSchedule(
            bgmId = 8888L,
            title = "Another Anime",
            titleCn = "普通单周番剧",
            timeCst = "15:00",
            nextEpisodeNumber = 3,
        )

        val rawList = batchEpisodes + singleEpisodeAnime
        val grouped = rawList.groupTimelineSchedules()

        // 断言：10 集赛博朋克被聚合为 1 个条目，整个时段共 2 部番
        assertEquals(2, grouped.size)

        val cyberpunkGroup = grouped.first { it.primarySchedule.bgmId == 9999L }
        assertEquals(10, cyberpunkGroup.episodes.size)
        assertEquals((1..10).toList(), cyberpunkGroup.episodes)

        val normalGroup = grouped.first { it.primarySchedule.bgmId == 8888L }
        assertEquals(listOf(3), normalGroup.episodes)
    }

    @Test
    fun groupTimelineSchedules_handlesConsecutiveTwoEpisodes() {
        // 模拟首播连播 2 集或季末连播 2 集
        val doubleAir = listOf(
            AirSchedule(bgmId = 777L, title = "芙莉莲", titleCn = "葬送的芙莉莲", nextEpisodeNumber = 1),
            AirSchedule(bgmId = 777L, title = "芙莉莲", titleCn = "葬送的芙莉莲", nextEpisodeNumber = 2),
        )

        val grouped = doubleAir.groupTimelineSchedules()
        assertEquals(1, grouped.size)
        assertEquals(listOf(1, 2), grouped.first().episodes)
    }
}
