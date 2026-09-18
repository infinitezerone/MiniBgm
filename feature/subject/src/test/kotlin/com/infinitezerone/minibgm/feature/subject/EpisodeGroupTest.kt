package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeFutureAir
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeNextToWatch
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeGroupTest {
    @Test
    fun fromType_mapsAllBangumiTypesToExpectedGroups() {
        assertEquals(EpisodeGroup.MAIN, EpisodeGroup.fromType(0))
        assertEquals("本篇", EpisodeGroup.MAIN.label)

        assertEquals(EpisodeGroup.SP, EpisodeGroup.fromType(1))
        assertEquals("特别篇", EpisodeGroup.SP.label)

        assertEquals(EpisodeGroup.OP_ED, EpisodeGroup.fromType(2))
        assertEquals(EpisodeGroup.OP_ED, EpisodeGroup.fromType(3))
        assertEquals("OP/ED", EpisodeGroup.OP_ED.label)

        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(4))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(5))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(6))
        assertEquals(EpisodeGroup.OTHER, EpisodeGroup.fromType(99))
        assertEquals("其他", EpisodeGroup.OTHER.label)
    }

    @Test
    fun isEpisodeWatched_correctlyDeterminesWatchedState() {
        val ep1 = Episode(id = 1, type = 0, ep = 1f, sort = 1f)
        val ep2 = Episode(id = 2, type = 0, ep = 2f, sort = 2f)
        val epSp = Episode(id = 3, type = 1, ep = 1f, sort = 1f)

        assertTrue(isEpisodeWatched(ep1, watchedCount = 1))
        assertTrue(isEpisodeWatched(ep1, watchedCount = 2))
        assertFalse(isEpisodeWatched(ep2, watchedCount = 1))
        assertFalse(isEpisodeWatched(epSp, watchedCount = 5))

        val epWithZeroEp = Episode(id = 4, type = 0, ep = 0f, sort = 2f)
        assertTrue(isEpisodeWatched(epWithZeroEp, watchedCount = 2))
        assertFalse(isEpisodeWatched(epWithZeroEp, watchedCount = 1))
    }

    @Test
    fun isEpisodeNextToWatch_identifiesUpcomingEpisode() {
        val ep1 = Episode(id = 1, type = 0, ep = 1f, sort = 1f)
        val ep2 = Episode(id = 2, type = 0, ep = 2f, sort = 2f)
        val ep3 = Episode(id = 3, type = 0, ep = 3f, sort = 3f)
        val epSp = Episode(id = 4, type = 1, ep = 1f, sort = 1f)

        assertTrue(isEpisodeNextToWatch(ep1, watchedCount = 0))
        assertFalse(isEpisodeNextToWatch(ep2, watchedCount = 0))

        assertFalse(isEpisodeNextToWatch(ep1, watchedCount = 1))
        assertTrue(isEpisodeNextToWatch(ep2, watchedCount = 1))
        assertFalse(isEpisodeNextToWatch(ep3, watchedCount = 1))

        assertFalse(isEpisodeNextToWatch(epSp, watchedCount = 0))

        // 关键防护验证：未播出的未来分集即使编号刚好为下一集，也决不能作为可播放的在看集
        val futureEp = Episode(id = 5, type = 0, ep = 2f, sort = 2f, airdate = "2099-01-01")
        assertFalse(isEpisodeNextToWatch(futureEp, watchedCount = 1))

        // 兼容 Bangumi ep 字段未填（为 0f）而通过 sort 排序的真实场景
        val epWithZeroEp = Episode(id = 6, type = 0, ep = 0f, sort = 2f)
        assertTrue(isEpisodeNextToWatch(epWithZeroEp, watchedCount = 1))
    }

    @Test
    fun isEpisodeFutureAir_handlesFutureAndPastDates() {
        val baseMillis = 1770000000000L
        val pastEp = Episode(id = 1, airdate = "2023-01-01")
        val futureEp = Episode(id = 2, airdate = "2099-01-01")
        val emptyAirdateEp = Episode(id = 3, airdate = "")

        assertFalse(isEpisodeFutureAir(pastEp, nowMillis = baseMillis))
        assertTrue(isEpisodeFutureAir(futureEp, nowMillis = baseMillis))
        assertFalse(isEpisodeFutureAir(emptyAirdateEp, nowMillis = baseMillis))
    }

    @Test
    fun toEpisodeLabel_formatsWholeAndFractionalEpisodes() {
        assertEquals("1", 1f.toEpisodeLabel())
        assertEquals("12", 12f.toEpisodeLabel())
        assertEquals("6.5", 6.5f.toEpisodeLabel())
        assertEquals("1", 0f.toEpisodeLabel())
        assertEquals("1", (-1f).toEpisodeLabel())
    }
}
