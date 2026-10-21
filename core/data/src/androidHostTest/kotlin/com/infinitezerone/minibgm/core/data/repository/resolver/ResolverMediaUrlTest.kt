package com.infinitezerone.minibgm.core.data.repository.resolver

import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ResolverMediaUrlTest {
    @Test
    fun `extractPlayableSources_提取常规m3u8直链`() {
        val html =
            """
            <div>
                <a href="https://example.com/play/1.m3u8">播放</a>
            </div>
            """.trimIndent()

        val sources = extractPlayableSources(html, "https://example.com/anime/1", 1f, "测试源")
        assertEquals(1, sources.size)
        assertEquals("https://example.com/play/1.m3u8", sources.first().url)
        assertEquals(PlaylistEntryKind.DIRECT, sources.first().kind)
    }

    @Test
    fun `extractPlayableSources_提取DPlayer配置直链`() {
        val html =
            """
            <script>
                const dp = new DPlayer({
                    container: document.getElementById('dplayer'),
                    video: {
                        url: 'https://cdn.example.com/anime/ep01.m3u8',
                        type: 'auto'
                    }
                });
            </script>
            """.trimIndent()

        val sources = extractPlayableSources(html, "https://example.com/watch", 1f, "DPlayer源")
        assertTrue(sources.any { it.url == "https://cdn.example.com/anime/ep01.m3u8" && it.kind == PlaylistEntryKind.DIRECT })
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `extractPlayableSources_提取MacCMS带Base64混淆的player_aaaa配置`() {
        val rawPlayUrl = "https://cdn.example.com/2026/ep02.m3u8"
        val b64 = Base64.Default.encode(rawPlayUrl.encodeToByteArray())
        val html =
            """
            <script>
                var player_aaaa = {
                    "flag": "mac_m3u8",
                    "encrypt": 1,
                    "trysee": 0,
                    "url": "$b64",
                    "link": "/index.php"
                };
            </script>
            """.trimIndent()

        val sources = extractPlayableSources(html, "https://example.com/play/2", 2f, "MacCMS播放页")
        assertTrue(sources.any { it.url == rawPlayUrl && it.kind == PlaylistEntryKind.DIRECT })
    }

    @Test
    fun `extractPlayableSources_从Iframe参数中直接解出直链并提权为DIRECT`() {
        val html =
            """
            <iframe src="https://player.example.com/share.php?url=https%3A%2F%2Fmedia.example.com%2Fstream.m3u8"></iframe>
            """.trimIndent()

        val sources = extractPlayableSources(html, "https://example.com/play/3", 3f, "Iframe源")
        assertTrue(sources.any { it.url == "https://media.example.com/stream.m3u8" && it.kind == PlaylistEntryKind.DIRECT })
    }
}
