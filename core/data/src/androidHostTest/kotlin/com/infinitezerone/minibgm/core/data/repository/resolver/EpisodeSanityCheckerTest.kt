package com.infinitezerone.minibgm.core.data.repository.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeSanityCheckerTest {
    @Test
    fun `parseSeasonNumber 能正确识别中英文季数`() {
        assertEquals(1, EpisodeSanityChecker.parseSeasonNumber("某科学的超电磁炮 第一季"))
        assertEquals(2, EpisodeSanityChecker.parseSeasonNumber("无职转生～到了异世界就拿出真本事 第二季"))
        assertEquals(2, EpisodeSanityChecker.parseSeasonNumber("无职转生 第2期"))
        assertEquals(3, EpisodeSanityChecker.parseSeasonNumber("关于我转生变成史莱姆这档事 第3季"))
        assertEquals(2, EpisodeSanityChecker.parseSeasonNumber("Frieren Season 2"))
        assertEquals(3, EpisodeSanityChecker.parseSeasonNumber("Attack on Titan S3"))
        assertEquals(2, EpisodeSanityChecker.parseSeasonNumber("间谍过家家 Part 2"))
        assertEquals(2, EpisodeSanityChecker.parseSeasonNumber("咒术回战 2"))
        assertNull(EpisodeSanityChecker.parseSeasonNumber("葬送的芙莉莲"))
    }

    @Test
    fun `isSeasonAndEditionCompatible 严格拦截串季`() {
        // 目标为第二季，候选为第一季 -> 互斥
        assertFalse(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "无职转生 第二季",
                candidateVodName = "无职转生 第一季",
            ),
        )
        // 目标为第二季，候选标为第一期 -> 互斥
        assertFalse(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "无职转生 第二季",
                candidateVodName = "无职转生 第1期",
            ),
        )
        // 目标为第二季，候选为 S3 -> 互斥
        assertFalse(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "无职转生 S2",
                candidateVodName = "无职转生 S3",
            ),
        )
        // 目标为第二季，候选同为第二季 -> 兼容
        assertTrue(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "无职转生 第二季",
                candidateVodName = "无职转生 第2季",
            ),
        )
    }

    @Test
    fun `isSeasonAndEditionCompatible 严格拦截 TV 误播剧场版或特别篇`() {
        // 目标为 TV 正片，候选为剧场版 -> 互斥
        assertFalse(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "鬼灭之刃",
                candidateVodName = "鬼灭之刃 剧场版 无限列车篇",
            ),
        )
        // 目标为 TV 正片，候选为 OVA -> 互斥
        assertFalse(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "进击的巨人",
                candidateVodName = "进击的巨人 OVA",
            ),
        )
        // 目标本身即是剧场版，候选也是剧场版 -> 兼容
        assertTrue(
            EpisodeSanityChecker.isSeasonAndEditionCompatible(
                targetTitle = "鬼灭之刃 剧场版 无限列车篇",
                candidateVodName = "鬼灭之刃 剧场版 无限列车篇",
            ),
        )
    }

    @Test
    fun `normalizeEpisodeIndex 自动纠偏连续编号`() {
        // 请求第 1 话，源站编号为 [13..24] -> 映射到 13 话
        val availableSorts = (13..24).map { it.toFloat() }
        val mapped = EpisodeSanityChecker.normalizeEpisodeIndex(1f, availableSorts)
        assertEquals(13f, mapped)

        // 请求第 2 话，源站编号为 [13..24] -> 映射到 14 话
        assertEquals(14f, EpisodeSanityChecker.normalizeEpisodeIndex(2f, availableSorts))

        // 请求第 1 话，源站存在精准的 1 话 -> 保持 1 话
        val standardSorts = (1..12).map { it.toFloat() }
        assertEquals(1f, EpisodeSanityChecker.normalizeEpisodeIndex(1f, standardSorts))
    }

    @Test
    fun `isDurationPlausibleForTvEpisode 识别异常时长`() {
        // 20 分钟 (1200秒) -> 合理
        assertTrue(EpisodeSanityChecker.isDurationPlausibleForTvEpisode(1200L))
        // 1 分 30 秒 (90秒，PV/预告片) -> 不合理
        assertFalse(EpisodeSanityChecker.isDurationPlausibleForTvEpisode(90L))
        // 2 小时 (7200秒，剧场版) -> 不合理
        assertFalse(EpisodeSanityChecker.isDurationPlausibleForTvEpisode(7200L))
        // 0 秒 (未探测到) -> 放行
        assertTrue(EpisodeSanityChecker.isDurationPlausibleForTvEpisode(0L))
    }
}
