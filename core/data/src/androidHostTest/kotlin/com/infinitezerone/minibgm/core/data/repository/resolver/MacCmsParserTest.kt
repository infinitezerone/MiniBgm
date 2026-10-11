package com.infinitezerone.minibgm.core.data.repository.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacCmsParserTest {
    @Test
    fun isExplicitNonAnimeType_recognizesAnimeAndNonAnimeCategories() {
        assertFalse(isExplicitNonAnimeType("日本动漫"))
        assertFalse(isExplicitNonAnimeType("国产动漫"))
        assertFalse(isExplicitNonAnimeType("欧美动画"))
        assertFalse(isExplicitNonAnimeType("二次元新番"))
        assertFalse(isExplicitNonAnimeType("Anime"))
        assertFalse(isExplicitNonAnimeType(null))
        assertFalse(isExplicitNonAnimeType(""))

        assertTrue(isExplicitNonAnimeType("国产剧"))
        assertTrue(isExplicitNonAnimeType("大陆电视剧"))
        assertTrue(isExplicitNonAnimeType("欧美剧"))
        assertTrue(isExplicitNonAnimeType("综艺"))
        assertTrue(isExplicitNonAnimeType("动作片"))
        assertTrue(isExplicitNonAnimeType("科幻电影"))
        assertTrue(isExplicitNonAnimeType("纪录片"))
        assertTrue(isExplicitNonAnimeType("短剧"))
    }

    @Test
    fun extractMacCmsSources_filtersOutNonAnimeSeriesAndPicksAnime() {
        val json =
            """
            {
              "code": 1,
              "msg": "数据列表",
              "list": [
                {
                  "vod_id": 101,
                  "vod_name": "全职高手",
                  "type_name": "国产剧",
                  "vod_play_url": "第01集${'$'}https://tv.example.com/1.m3u8#第02集${'$'}https://tv.example.com/2.m3u8"
                },
                {
                  "vod_id": 102,
                  "vod_name": "全职高手",
                  "type_name": "国产动漫",
                  "vod_play_url": "第01集${'$'}https://anime.example.com/1.m3u8#第02集${'$'}https://anime.example.com/2.m3u8"
                }
              ]
            }
            """.trimIndent()

        val sources =
            extractMacCmsSources(
                html = json,
                pageUrl = "https://example.com/api.php",
                epNumber = 1f,
                siteName = "资源站",
                title = "全职高手",
            )

        // 应自动跳过第一条国产剧，精准采用第二条国产动漫
        assertEquals(1, sources.size)
        assertEquals("https://anime.example.com/1.m3u8", sources.first().url)
    }

    @Test
    fun extractMacCmsSources_rejectsSingleEntryIfExplicitlyNonAnime() {
        val dramaJson =
            """
            {
              "code": 1,
              "msg": "数据列表",
              "list": [
                {
                  "vod_id": 201,
                  "vod_name": "三体",
                  "type_name": "国产剧",
                  "vod_play_url": "第01集${'$'}https://tv.example.com/3body/1.m3u8"
                }
              ]
            }
            """.trimIndent()

        val sources =
            extractMacCmsSources(
                html = dramaJson,
                pageUrl = "https://example.com/api.php",
                epNumber = 1f,
                siteName = "资源站",
                title = "三体",
            )

        // 只有电视剧版时直接一票否决，不播错番
        assertTrue(sources.isEmpty())
    }

    @Test
    fun extractMacCmsSources_expandsMultipleRoutesWithRouteName() {
        val multiRouteJson =
            """
            {
              "code": 1,
              "msg": "数据列表",
              "list": [
                {
                  "vod_id": 301,
                  "vod_name": "葬送的芙莉莲",
                  "type_name": "日本动漫",
                  "vod_play_url": "第01集${'$'}https://route1.com/1.m3u8${'$'}${'$'}${'$'}第01集${'$'}https://route2.com/1.m3u8"
                }
              ]
            }
            """.trimIndent()

        val sources =
            extractMacCmsSources(
                html = multiRouteJson,
                pageUrl = "https://example.com/api.php",
                epNumber = 1f,
                siteName = "主站",
                title = "葬送的芙莉莲",
            )

        assertEquals(2, sources.size)
        assertEquals("主站 · 线路1", sources[0].siteName)
        assertEquals("https://route1.com/1.m3u8", sources[0].url)
        assertEquals("主站 · 线路2", sources[1].siteName)
        assertEquals("https://route2.com/1.m3u8", sources[1].url)
    }
}
