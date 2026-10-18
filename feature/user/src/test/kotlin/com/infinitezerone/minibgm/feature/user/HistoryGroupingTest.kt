package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.PlaybackHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupingTest {
    @Test
    fun groups_episodes_of_same_subject_and_picks_the_latest_as_resume_target() {
        val groups =
            buildHistoryGroups(
                listOf(
                    history(episodeId = 101L, subjectId = 7L, episodeSort = 1f, updatedAt = 1_000L),
                    history(episodeId = 103L, subjectId = 7L, episodeSort = 3f, updatedAt = 3_000L),
                    history(episodeId = 102L, subjectId = 7L, episodeSort = 2f, updatedAt = 2_000L),
                    history(episodeId = 201L, subjectId = 9L, episodeSort = 1f, updatedAt = 500L),
                ),
            )

        assertEquals(2, groups.size)
        // 组间按最近观看时间倒序
        assertEquals(listOf(7L, 9L), groups.map { it.subjectId })
        assertEquals(103L, groups[0].latest.episodeId)
        // 组内按集数升序，方便选集
        assertEquals(listOf(101L, 102L, 103L), groups[0].episodes.map { it.episodeId })
        assertEquals(3, groups[0].episodeCount)
        assertEquals(3_000L, groups[0].lastWatchedAt)
    }

    @Test
    fun unknown_subject_id_keeps_records_separate() {
        val groups =
            buildHistoryGroups(
                listOf(
                    history(episodeId = 1L, subjectId = 0L, updatedAt = 1_000L),
                    history(episodeId = 2L, subjectId = 0L, updatedAt = 2_000L),
                ),
            )

        assertEquals(2, groups.size)
        assertEquals(listOf(2L, 1L), groups.map { it.latest.episodeId })
    }

    @Test
    fun blank_subject_name_falls_back_to_episode_name_and_cover_falls_back_to_any_non_blank() {
        val groups =
            buildHistoryGroups(
                listOf(
                    history(
                        episodeId = 1L,
                        subjectId = 5L,
                        subjectName = "  ",
                        episodeName = "第 1 话",
                        coverUrl = "",
                        updatedAt = 2_000L,
                    ),
                    history(
                        episodeId = 2L,
                        subjectId = 5L,
                        subjectName = "",
                        episodeName = "第 2 话",
                        coverUrl = "https://example.com/cover.jpg",
                        updatedAt = 1_000L,
                    ),
                ),
            )

        assertEquals(1, groups.size)
        assertEquals("第 1 话", groups[0].subjectName)
        assertEquals("https://example.com/cover.jpg", groups[0].coverUrl)
    }

    private fun history(
        episodeId: Long,
        subjectId: Long,
        episodeSort: Float = 1f,
        subjectName: String = "葬送的芙莉莲",
        episodeName: String = "第 1 话",
        coverUrl: String = "https://example.com/cover.jpg",
        updatedAt: Long = 0L,
    ) = PlaybackHistory(
        episodeId = episodeId,
        subjectId = subjectId,
        subjectName = subjectName,
        episodeName = episodeName,
        episodeSort = episodeSort,
        coverUrl = coverUrl,
        streamUrl = "https://example.com/video.m3u8",
        positionMs = 60_000L,
        durationMs = 240_000L,
        updatedAt = updatedAt,
    )
}
