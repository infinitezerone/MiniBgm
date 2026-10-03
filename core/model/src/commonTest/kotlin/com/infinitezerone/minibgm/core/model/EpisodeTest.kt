package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeTest {
    @Test
    fun toEpisodeLabel_formatsStandardAndFractionalEpisodes() {
        assertEquals("1", 1f.toEpisodeLabel())
        assertEquals("12", 12f.toEpisodeLabel())
        assertEquals("6.5", 6.5f.toEpisodeLabel())
        assertEquals("1", 0f.toEpisodeLabel())
        assertEquals("1", (-1f).toEpisodeLabel())
    }

    @Test
    fun episodeProperties_prioritizesEpOverSortWhenPositive() {
        // 场景 A：常规正篇，ep 和 sort 均为 5
        val epRegular = Episode(id = 1, type = 0, ep = 5f, sort = 5f, name = "Regular")
        assertTrue(epRegular.isMain)
        assertEquals(5f, epRegular.episodeNumber)
        assertEquals(5, epRegular.episodeInt)
        assertEquals("5", epRegular.formattedNumber)
        assertEquals("Regular", epRegular.displayTitle)

        // 场景 B：系列累计 sort（如 Re:0 夺还篇：本季第 1 话，全系列累计 sort=78）
        val epFranchise = Episode(id = 2, type = 0, ep = 1f, sort = 78f)
        assertTrue(epFranchise.isMain)
        assertEquals(1f, epFranchise.episodeNumber)
        assertEquals(1, epFranchise.episodeInt)
        assertEquals("1", epFranchise.formattedNumber)
        assertEquals("第 1 话", epFranchise.displayTitle)

        // 场景 C：Bangumi 数据缺失导致 ep 为 0，应正确回退到 sort
        val epZeroEp = Episode(id = 3, type = 0, ep = 0f, sort = 7f)
        assertTrue(epZeroEp.isMain)
        assertEquals(7f, epZeroEp.episodeNumber)
        assertEquals(7, epZeroEp.episodeInt)
        assertEquals("7", epZeroEp.formattedNumber)
        assertEquals("第 7 话", epZeroEp.displayTitle)

        // 场景 D：SP 特别篇（type = 1），ep = 0，sort = 1
        val epSp = Episode(id = 4, type = 1, ep = 0f, sort = 1f)
        assertFalse(epSp.isMain)
        assertEquals(1f, epSp.episodeNumber)
        assertEquals(1, epSp.episodeInt)
        assertEquals("1", epSp.formattedNumber)
        assertEquals("第 1 话", epSp.displayTitle)
    }

    @Test
    fun displayTitle_prefersNameCnThenNameThenFallback() {
        val withCn = Episode(id = 1, type = 0, ep = 1f, name = "Name", nameCn = "中文名")
        assertEquals("中文名", withCn.displayTitle)

        val withNameOnly = Episode(id = 2, type = 0, ep = 2f, name = "Name", nameCn = "")
        assertEquals("Name", withNameOnly.displayTitle)

        val withoutName = Episode(id = 3, type = 0, ep = 3f, name = "", nameCn = "")
        assertEquals("第 3 话", withoutName.displayTitle)

        val fractionalWithoutName = Episode(id = 4, type = 0, ep = 6.5f, name = "", nameCn = "")
        assertEquals("第 6.5 话", fractionalWithoutName.displayTitle)
    }
}
