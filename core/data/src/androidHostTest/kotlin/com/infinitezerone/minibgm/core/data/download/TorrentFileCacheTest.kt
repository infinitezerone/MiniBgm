package com.infinitezerone.minibgm.core.data.download

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 种子缓存载荷校验回归：镜像返回 HTTP 200 的 HTML 错误页时，`AssetDownloader` 只按字节数
 * 判定成功并原子改名落盘，若缓存命中只看「文件名 + 非空」，坏种子会被永久复用。
 */
class TorrentFileCacheTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir =
            File.createTempFile("torrent_cache_test_", "").apply {
                delete()
                mkdirs()
            }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun fileWith(
        name: String,
        content: String,
    ): File =
        File(tempDir, name).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.ISO_8859_1)
        }

    @Test
    fun isValidPayload_rejectsHtmlErrorPage() {
        val html = fileWith("bad.torrent", "<html><body>404 Not Found</body></html>")

        assertFalse(TorrentFileCache.isValidPayload(html))
    }

    @Test
    fun isValidPayload_rejectsJsonErrorBodyAndEmptyFile() {
        assertFalse(TorrentFileCache.isValidPayload(fileWith("json.torrent", "{\"error\":\"not found\"}")))
        assertFalse(TorrentFileCache.isValidPayload(fileWith("empty.torrent", "")))
        assertFalse(TorrentFileCache.isValidPayload(File(tempDir, "missing.torrent")))
    }

    @Test
    fun isValidPayload_acceptsBencodedTorrent() {
        val valid =
            fileWith(
                "good.torrent",
                "d8:announce14:http://tracker4:infod6:lengthi1e4:name3:abcee",
            )

        assertTrue(TorrentFileCache.isValidPayload(valid))
    }

    @Test
    fun isUsableCachedFile_deletesCorruptFileAndReportsUnusable() {
        val corrupt = fileWith("corrupt.torrent", "<html>mirror error</html>")

        assertFalse(TorrentFileCache.isUsableCachedFile(corrupt))
        assertFalse(corrupt.exists(), "坏缓存必须就地删除，否则会随 DB 路径被永久复用")
    }

    @Test
    fun isUsableCachedFile_keepsValidFile() {
        val valid =
            fileWith(
                "keep.torrent",
                "d8:announce14:http://tracker4:infod6:lengthi1e4:name3:abcee",
            )

        assertTrue(TorrentFileCache.isUsableCachedFile(valid))
        assertTrue(valid.exists())
    }

    @Test
    fun cacheKey_prefersInfoHashOverTitleAndUrl() {
        val fromHash =
            TorrentFileCache.cacheKey(
                magnetUrl = "magnet:?xt=urn:btih:AAAABBBBCCCCDDDD&dn=Some.Title",
                torrentUrl = "https://example.com/a.torrent",
                title = "同名标题",
            )
        val sameHashOtherTitle =
            TorrentFileCache.cacheKey(
                magnetUrl = "magnet:?xt=urn:btih:aaaabbbbccccdddd",
                torrentUrl = null,
                title = "另一个标题",
            )

        assertEquals("hash_aaaabbbbccccdddd", fromHash)
        assertEquals(fromHash, sameHashOtherTitle, "同一 infoHash 必须映射到同一缓存键（大小写无关）")
    }

    @Test
    fun cacheKey_fallsBackToTorrentUrlThenTitle_andSanitizesIllegalChars() {
        val byUrl =
            TorrentFileCache.cacheKey(
                magnetUrl = "",
                torrentUrl = "https://example.com/a.torrent",
                title = "标题",
            )
        assertTrue(byUrl.startsWith("url_"), "无 infoHash 时应退化到种子直链指纹")

        val byTitle =
            TorrentFileCache.cacheKey(
                magnetUrl = "",
                torrentUrl = null,
                title = "Fate/stay night: 合集",
            )
        assertEquals("title_Fate_stay night_ 合集", byTitle)
    }

    @Test
    fun cacheKey_urlFingerprint_isStableAndCollisionResistant() {
        val first = TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = "https://example.com/a.torrent", title = "t")
        val again = TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = "https://example.com/a.torrent", title = "另一个标题")
        val other = TorrentFileCache.cacheKey(magnetUrl = "", torrentUrl = "https://example.com/b.torrent", title = "t")

        // 同一 URL 必须稳定命中同一键（跨标题、跨会话），否则缓存永不命中
        assertEquals(first, again)
        assertTrue(first != other, "不同种子直链必须映射到不同缓存键")
        assertTrue(
            Regex("^url_[0-9a-f]{32,}$").matches(first),
            "直链指纹必须是定长十六进制摘要，而不是 32 位 hashCode（会碰撞成同一缓存文件）",
        )
    }
}
