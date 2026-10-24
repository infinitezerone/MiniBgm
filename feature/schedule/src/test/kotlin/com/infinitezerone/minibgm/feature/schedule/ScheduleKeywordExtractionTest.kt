package com.infinitezerone.minibgm.feature.schedule

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.feature.schedule.components.ScheduleKeyword
import com.infinitezerone.minibgm.feature.schedule.components.extractScheduleKeywords
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 关键词提取纯函数的语义档位契约：
 * 纯函数只产出资源档位（[ScheduleKeyword.labelRes] + formatArgs），展示文案由 UI 经
 * `stringResource` 解析——这里锁定各档位的判定规则与 todayYear 时钟注入口径。
 */
class ScheduleKeywordExtractionTest {
    private fun schedule(
        titleCn: String = "",
        title: String = "",
        format: String = "",
        airDate: String = "",
        nextEpisodeNumber: Int = 0,
        ratingScore: Double = 0.0,
    ): AirSchedule =
        AirSchedule(
            bgmId = 1L,
            title = title,
            titleCn = titleCn,
            format = format,
            airDate = airDate,
            nextEpisodeNumber = nextEpisodeNumber,
            ratingScore = ratingScore,
        )

    @Test
    fun `本季新番按 todayYear 注入判定并携带月份参数`() {
        val keywords = extractScheduleKeywords(schedule(airDate = "2026-01-05"), todayYear = 2026)
        assertEquals(1, keywords.size)
        assertEquals(R.string.feature_schedule_season_format, keywords.single().labelRes)
        assertEquals(listOf<Any>(1), keywords.single().formatArgs)
    }

    @Test
    fun `非本季档期回退为年月档位`() {
        val keywords = extractScheduleKeywords(schedule(airDate = "2024-04-01"), todayYear = 2026)
        assertEquals(R.string.feature_schedule_season_year_month_format, keywords.single().labelRes)
        assertEquals(listOf<Any>(2024, 4), keywords.single().formatArgs)
    }

    @Test
    fun `超长篇按集数判定为长篇连载档位`() {
        val keywords = extractScheduleKeywords(schedule(nextEpisodeNumber = 100), todayYear = 2026)
        assertEquals(R.string.feature_schedule_tag_long_running, keywords.single().labelRes)
    }

    @Test
    fun `载体类型映射到对应资源档位`() {
        val cases =
            mapOf(
                "TV_SHORT" to R.string.feature_schedule_tag_format_tv_short,
                "TV" to R.string.feature_schedule_tag_format_tv,
                "ONA" to R.string.feature_schedule_tag_format_web,
                "OVA" to R.string.feature_schedule_tag_format_ova,
                "MOVIE" to R.string.feature_schedule_tag_format_movie,
                "SPECIAL" to R.string.feature_schedule_tag_format_special,
            )
        cases.forEach { (format, expectedRes) ->
            val keywords = extractScheduleKeywords(schedule(format = format), todayYear = 2026)
            assertEquals("format=$format", 1, keywords.size)
            assertEquals("format=$format", expectedRes, keywords.single().labelRes)
        }
    }

    @Test
    fun `季数与首播档位按标题与集数判定`() {
        val sequel = extractScheduleKeywords(schedule(titleCn = "某作品 第二季"), todayYear = 2026)
        assertEquals(R.string.feature_schedule_tag_season_2, sequel.single().labelRes)

        val premiere = extractScheduleKeywords(schedule(nextEpisodeNumber = 1), todayYear = 2026)
        assertEquals(R.string.feature_schedule_tag_premiere, premiere.single().labelRes)
    }

    @Test
    fun `题材与高分档位可叠加并去重截断`() {
        val keywords =
            extractScheduleKeywords(
                schedule(titleCn = "异世界战斗恋爱日常", ratingScore = 8.5),
                todayYear = 2026,
            )
        val expected =
            listOf(
                R.string.feature_schedule_tag_genre_isekai,
                R.string.feature_schedule_tag_genre_action,
                R.string.feature_schedule_tag_genre_romance,
                R.string.feature_schedule_tag_genre_daily,
                R.string.feature_schedule_tag_acclaimed,
            )
        assertEquals(expected.take(4), keywords.map(ScheduleKeyword::labelRes))
    }
}
