package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackPlaylistTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun entry(
        label: String,
        url: String,
        kind: PlaylistEntryKind = PlaylistEntryKind.DIRECT,
        headers: Map<String, String> = emptyMap(),
    ) = PlaylistEntry(label = label, url = url, kind = kind, headers = headers)

    private fun document(vararg playlists: PlaybackPlaylist) = PlaybackPlaylistDocument(playlists = playlists.toList())

    private fun playlist(
        id: String,
        vararg entries: PlaylistEntry,
        subjectId: Long = 1L,
    ) = PlaybackPlaylist(id = id, name = "列表-$id", bgmSubjectId = subjectId, entries = entries.toList())

    @Test
    fun `valid document passes through unchanged`() {
        val doc =
            document(
                playlist(
                    "a",
                    entry("01", "https://cdn.example.com/a.m3u8"),
                    entry("02", "https://example.com/p", kind = PlaylistEntryKind.PAGE),
                ),
            )
        val result = PlaybackPlaylistSchema.validate(doc)
        assertTrue(result.issues.isEmpty(), "unexpected issues: ${result.issues}")
        assertEquals(1, result.validPlaylists.size)
        assertEquals(
            2,
            result.validPlaylists
                .single()
                .entries.size,
        )
    }

    @Test
    fun `template example json itself imports cleanly`() {
        val doc = json.decodeFromString<PlaybackPlaylistDocument>(PlaybackPlaylistSchema.TEMPLATE_EXAMPLE_JSON)
        val result = PlaybackPlaylistSchema.validate(doc)
        assertTrue(result.issues.isEmpty(), "template example must be valid: ${result.issues}")
        assertEquals(1, result.validPlaylists.size)
        assertEquals(
            2,
            result.validPlaylists
                .single()
                .entries.size,
        )
    }

    @Test
    fun `unsupported schema version rejects everything`() {
        val doc = document(playlist("a", entry("01", "https://x.example.com/a")))
        val bad = doc.copy(schemaVersion = PlaybackPlaylistSchema.CURRENT_SCHEMA_VERSION + 1)
        val result = PlaybackPlaylistSchema.validate(bad)
        assertTrue(result.validPlaylists.isEmpty())
        assertEquals(1, result.issues.size)
        assertTrue(result.issues.single().contains("schemaVersion"))
    }

    @Test
    fun `non http url and blank label entries are skipped with reasons`() {
        val doc =
            document(
                playlist(
                    "a",
                    entry("01", "javascript:alert(1)"),
                    entry("", "https://x.example.com/b"),
                    entry("03", "https://x.example.com/c"),
                ),
            )
        val result = PlaybackPlaylistSchema.validate(doc)
        val kept = result.validPlaylists.single().entries
        assertEquals(listOf("03"), kept.map { it.label })
        assertEquals(2, result.issues.size)
    }

    @Test
    fun `playlist with only invalid entries is skipped`() {
        val doc = document(playlist("a", entry("01", "ftp://x.example.com/a")))
        val result = PlaybackPlaylistSchema.validate(doc)
        assertTrue(result.validPlaylists.isEmpty())
        assertTrue(result.issues.any { it.contains("没有合法条目") })
    }

    @Test
    fun `duplicate ids inside one document keep the first only`() {
        val doc =
            document(
                playlist("dup", entry("01", "https://x.example.com/1")),
                playlist("dup", entry("02", "https://x.example.com/2")),
            )
        val result = PlaybackPlaylistSchema.validate(doc)
        assertEquals(1, result.validPlaylists.size)
        assertEquals(
            listOf("01"),
            result.validPlaylists
                .single()
                .entries
                .map { it.label },
        )
        assertTrue(result.issues.any { it.contains("文档内重复") })
    }

    @Test
    fun `malformed header entries are filtered not rejecting the entry`() {
        val doc =
            document(
                playlist(
                    "a",
                    entry(
                        "01",
                        "https://x.example.com/a",
                        headers =
                            mapOf(
                                "Referer" to "https://x.example.com/",
                                "Bad:Header" to "nope",
                                "" to "blank key",
                                "Injected" to "line1\nline2",
                            ),
                    ),
                ),
            )
        val result = PlaybackPlaylistSchema.validate(doc)
        val kept =
            result.validPlaylists
                .single()
                .entries
                .single()
                .headers
        assertEquals(mapOf("Referer" to "https://x.example.com/"), kept)
        assertTrue(result.issues.any { it.contains("非法请求头") })
    }

    @Test
    fun `empty document reports issue`() {
        val result = PlaybackPlaylistSchema.validate(PlaybackPlaylistDocument())
        assertTrue(result.validPlaylists.isEmpty())
        assertTrue(result.issues.isNotEmpty())
    }

    @Test
    fun `forSubject filters unbound and foreign playlists`() {
        val playlists =
            listOf(
                playlist("a", subjectId = 10, entries = *arrayOf(entry("01", "https://x/1"))),
                playlist("b", subjectId = 20, entries = *arrayOf(entry("01", "https://x/2"))),
                playlist("c", subjectId = 0, entries = *arrayOf(entry("01", "https://x/3"))),
            )
        assertEquals(listOf("a"), playlists.forSubject(10).map { it.id })
        assertTrue(playlists.forSubject(0).isEmpty())
    }

    @Test
    fun `matchesForEpisode hits labels by numeric boundary`() {
        val playlists =
            listOf(
                playlist(
                    "a",
                    subjectId = 10,
                    entries =
                        *arrayOf(
                            entry("EP01", "https://x/1"),
                            entry("第 2 话", "https://x/2"),
                            entry("12 - 决战", "https://x/12"),
                            entry("120 总集篇", "https://x/120"),
                        ),
                ),
            )
        assertEquals(listOf("https://x/2"), playlists.matchesForEpisode(10, 2f).map { it.entry.url })
        assertEquals(listOf("https://x/1"), playlists.matchesForEpisode(10, 1f).map { it.entry.url })
        assertEquals(listOf("https://x/12"), playlists.matchesForEpisode(10, 12f).map { it.entry.url })
    }

    @Test
    fun `matchesForEpisode keeps decimal and sp variants apart`() {
        val playlists =
            listOf(
                playlist(
                    "a",
                    subjectId = 10,
                    entries = *arrayOf(entry("12.5 SP", "https://x/sp"), entry("12", "https://x/12")),
                ),
            )
        assertEquals(listOf("https://x/sp"), playlists.matchesForEpisode(10, 12.5f).map { it.entry.url })
        assertEquals(listOf("https://x/12"), playlists.matchesForEpisode(10, 12f).map { it.entry.url })
    }

    @Test
    fun `matchesForEpisode falls back to head of playlist when no label matches`() {
        val entries = (1..20).map { entry("未编号分集", "https://x/$it") }.toTypedArray()
        val playlists = listOf(playlist("a", subjectId = 10, entries = entries))

        val matches = playlists.matchesForEpisode(10, 5f, fallbackLimit = 3)

        assertEquals(listOf("https://x/1", "https://x/2", "https://x/3"), matches.map { it.entry.url })
        assertTrue(matches.all { !it.matched })
        assertTrue(matches.all { it.playlist.id == "a" })
    }

    @Test
    fun `matchesForEpisode ignores foreign subjects`() {
        val playlists = listOf(playlist("a", subjectId = 99, entries = *arrayOf(entry("01", "https://x/1"))))
        assertTrue(playlists.matchesForEpisode(10, 1f).isEmpty())
    }
}
