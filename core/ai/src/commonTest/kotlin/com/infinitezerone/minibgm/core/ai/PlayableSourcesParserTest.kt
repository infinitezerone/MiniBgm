package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayableSourcesParserTest {
    private val json =
        """
        {
          "subjectId": 1001,
          "title": "葬送的芙莉莲",
          "source": "自备片单",
          "episodes": [
            { "url": "https://cdn.example.com/ep12.m3u8", "kind": "DIRECT", "label": "12", "episodeSort": 12, "headers": { "Referer": "https://example.com/" } }
          ]
        }
        """.trimIndent()

    @Test
    fun `整段 JSON 直接解析为播放清单`() {
        val parsed = PlayableSourcesParser.extract(json)

        val list = assertNotNull(parsed)
        assertEquals(1001L, list.subjectId)
        assertEquals("自备片单", list.source)
        val episode = list.episodes.single()
        assertEquals("https://cdn.example.com/ep12.m3u8", episode.url)
        assertEquals(PlaylistEntryKind.DIRECT, episode.kind)
        assertEquals(mapOf("Referer" to "https://example.com/"), episode.headers)
    }

    @Test
    fun `代码块与前后说明里的 JSON 也能取出`() {
        val wrapped = "为你找到可播放来源：\n```json\n$json\n```\n请在应用内播放。"

        assertEquals(1001L, assertNotNull(PlayableSourcesParser.extract(wrapped)).subjectId)
    }

    @Test
    fun `没有可播条目时按普通文本处理`() {
        assertNull(PlayableSourcesParser.extract("""{"subjectId": 1, "title": "x", "episodes": []}"""))
        assertNull(PlayableSourcesParser.extract("没有找到可播放地址。"))
    }

    @Test
    fun `提案 JSON 不会被误认成播放清单`() {
        val proposal =
            """
            {
              "status": "PENDING_CONFIRMATION",
              "action": { "type": "update_episode", "actionId": "act_1", "subjectId": 12, "episodeNumber": 3, "isWatched": true }
            }
            """.trimIndent()

        assertNull(PlayableSourcesParser.extract(proposal))
    }
}
