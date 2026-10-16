package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentModelsTest {
    @Test
    fun torrentTask_progressAndFormatting_calculatesCorrectly() {
        val task =
            TorrentTask(
                id = "task-1",
                title = "Frieren - 01",
                magnetUrl = "magnet:?xt=urn:btih:abcdef123456",
                totalBytes = 1000L * 1024 * 1024,
                downloadedBytes = 500L * 1024 * 1024,
                downloadSpeedBytesPerSec = 2L * 1024 * 1024,
                status = TorrentTaskStatus.DOWNLOADING,
            )

        assertEquals(0.5f, task.progress, 0.001f)
        assertEquals(50, task.percentage)
        assertFalse(task.isCompleted)
        assertEquals("2.0 MB/s", task.formattedDownloadSpeed)
        assertEquals(250L, task.etaSeconds)
        assertTrue(task.formattedProgress.contains("500.0 MB / 1000.0 MB"))
    }

    @Test
    fun torrentTask_completedStatus_reportsTrue() {
        val task =
            TorrentTask(
                id = "task-2",
                title = "Frieren - 02",
                magnetUrl = "magnet:?xt=urn:btih:123456abcdef",
                status = TorrentTaskStatus.COMPLETED,
            )

        assertTrue(task.isCompleted)
        assertEquals(-1L, task.etaSeconds)
    }

    @Test
    fun torrentAppInfo_playerFlag_distinguishesType() {
        val player = TorrentAppInfo(name = "mpv", packageName = "is.xyz.mpv", isPlayer = true)
        val downloader = TorrentAppInfo(name = "迅雷", packageName = "com.xunlei.downloadprovider", isPlayer = false)

        assertTrue(player.isPlayer)
        assertFalse(downloader.isPlayer)
    }

    @Test
    fun torrentStreamState_stateVariants_instantiateCorrectly() {
        val pluginReq = TorrentStreamState.PluginRequired(estimatedSizeBytes = 3_000_000L)
        assertEquals(3_000_000L, pluginReq.estimatedSizeBytes)

        val buffering =
            TorrentStreamState.Buffering(
                progress = 0.3f,
                bufferedBytes = 300L,
                targetBytes = 1000L,
                speedBytesPerSec = 1024L * 1024L,
                connectedPeers = 12,
            )
        assertEquals(0.3f, buffering.progress)
        assertEquals(1024L * 1024L, buffering.speedBytesPerSec)
        assertEquals(12, buffering.connectedPeers)

        val ready =
            TorrentStreamState.Ready(
                streamUrl = "http://127.0.0.1:8899/stream",
                title = "Episode 01",
                totalBytes = 500_000_000L,
                connectedPeers = 5,
            )
        assertEquals("http://127.0.0.1:8899/stream", ready.streamUrl)
        assertEquals(5, ready.connectedPeers)
    }

    @Test
    fun subjectDownloadGroup_statusCountsAndErrorSummary_aggregatesCorrectly() {
        val group =
            SubjectDownloadGroup(
                subjectId = 100L,
                subjectName = "黑色五叶草",
                subjectCoverUrl = "",
                tasks =
                    listOf(
                        TorrentTask(
                            id = "1",
                            title = "EP 01",
                            magnetUrl = "magnet:?xt=urn:btih:01",
                            status = TorrentTaskStatus.COMPLETED,
                            episodeSort = 1f,
                        ),
                        TorrentTask(
                            id = "2",
                            title = "EP 02",
                            magnetUrl = "magnet:?xt=urn:btih:02",
                            status = TorrentTaskStatus.RESOLVING,
                            episodeSort = 2f,
                        ),
                        TorrentTask(
                            id = "3",
                            title = "EP 03",
                            magnetUrl = "magnet:?xt=urn:btih:03",
                            status = TorrentTaskStatus.ERROR,
                            errorMessage = "连接做种节点超时（暂无可用源），请点击重试或更换其他资源",
                            episodeSort = 3f,
                        ),
                        TorrentTask(
                            id = "4",
                            title = "EP 04",
                            magnetUrl = "magnet:?xt=urn:btih:04",
                            status = TorrentTaskStatus.QUEUED,
                            episodeSort = 4f,
                        ),
                        TorrentTask(
                            id = "5",
                            title = "EP 05",
                            magnetUrl = "magnet:?xt=urn:btih:05",
                            status = TorrentTaskStatus.PAUSED,
                            episodeSort = 5f,
                        ),
                    ),
            )

        assertEquals(5, group.totalTasks)
        assertEquals(1, group.completedCount)
        assertEquals(0, group.downloadingCount)
        assertEquals(1, group.resolvingCount)
        assertEquals(1, group.queuedCount)
        assertEquals(1, group.pausedCount)
        assertEquals(1, group.errorCount)
        assertEquals(2, group.activeTaskCount)
        assertEquals("连接做种节点超时（暂无可用源），请点击重试或更换其他资源", group.firstErrorMessage)
    }

    @Test
    fun torrentEpisodeKey_prefersEpisodeIdThenSortThenName() {
        // id 最可靠（跨重启稳定），有 id 时忽略集数与分集名
        assertEquals("id:12345", torrentEpisodeKey(episodeId = 12345L, episodeSort = 7f, episodeName = "第七话"))
        // 无 id 退化到集数（保留半集/总集篇的小数字号）
        assertEquals("sort:7.5", torrentEpisodeKey(episodeId = 0L, episodeSort = 7.5f, episodeName = "第七点五话"))
        // 全无则退化到分集名（去空白）
        assertEquals("name:第七话", torrentEpisodeKey(episodeId = 0L, episodeSort = 0f, episodeName = "  第七话  "))
    }

    @Test
    fun torrentTask_episodeKey_isolatesEpisodesSharingOneMagnet() {
        val sharedMagnet = "magnet:?xt=urn:btih:aaaabbbbccccdddd"
        val first =
            TorrentTask(
                id = "1",
                title = "合集 BD",
                magnetUrl = sharedMagnet,
                episodeId = 101L,
                episodeSort = 1f,
                episodeName = "第一话",
            )
        val second =
            TorrentTask(
                id = "2",
                title = "合集 BD",
                magnetUrl = sharedMagnet,
                episodeId = 102L,
                episodeSort = 2f,
                episodeName = "第二话",
            )
        val sameEpisodeOtherTask =
            TorrentTask(
                id = "3",
                title = "合集 BD",
                magnetUrl = sharedMagnet,
                episodeId = 101L,
                episodeSort = 1f,
                episodeName = "第一话",
            )

        assertEquals("id:101", first.episodeKey)
        // 同一磁力（infoHash 相同）的不同分集必须是不同的键，否则会复用上一集已落盘的文件
        assertTrue(first.episodeKey != second.episodeKey)
        assertEquals(first.episodeKey, sameEpisodeOtherTask.episodeKey)
    }

    @Test
    fun torrentMediaResource_dedupeKey_fallsBackWhenHashMissing() {
        // 有 infoHash 时以 infoHash 为键，磁力链接差异不影响判重
        val hashed =
            TorrentMediaResource(
                title = "Frieren - 01 [1080P]",
                magnetUrl = "magnet:?xt=urn:btih:aaaa",
                torrentUrl = "https://example.com/1.torrent",
                infoHash = "aaaa",
            )
        assertEquals("aaaa", hashed.dedupeKey)

        // 无 40 位 hash 的 .torrent 直链条目：infoHash/magnetUrl 全空，必须回退到种子直链，
        // 否则同一 Feed 的所有条目会退化成同一个空串键并被 distinctBy 静默丢弃
        val torrentOnlyFirst =
            TorrentMediaResource(title = "Frieren - 01 [1080P]", torrentUrl = "https://example.com/1.torrent")
        val torrentOnlySecond =
            TorrentMediaResource(title = "Frieren - 02 [1080P]", torrentUrl = "https://example.com/2.torrent")
        assertEquals("https://example.com/1.torrent", torrentOnlyFirst.dedupeKey)
        assertTrue(torrentOnlyFirst.dedupeKey != torrentOnlySecond.dedupeKey)
        assertEquals(
            2,
            listOf(torrentOnlyFirst, torrentOnlySecond).distinctBy { it.dedupeKey }.size,
        )

        // 连种子直链都没有时最后回退到标题，保证键永不为空
        assertEquals("Frieren - 03", TorrentMediaResource(title = "  Frieren - 03  ").dedupeKey)
    }

    @Test
    fun magnetInfoHash_isSingleSourceOfTruthAndNeverReturnsSentinel() {
        assertEquals(
            "0123456789abcdef0123456789abcdef01234567",
            magnetInfoHash("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
        )
        // 自带 Tracker 声明的磁力：hash 必须止步于 `&`，否则会被拼进缓存键与会话身份
        assertEquals(
            "abc123",
            magnetInfoHash("magnet:?xt=urn:btih:abc123&dn=x&tr=udp%3A%2F%2Ftracker.example%3A80"),
        )
        // RSS/HTML 里常见的首尾空白必须裁掉，否则同一磁力会算出两个身份
        assertEquals("abc123", magnetInfoHash("  magnet:?xt=urn:btih:abc123  "))
        // RFC 4648 / BEP 0009 标准 32 位 Base32 BTIH 自动归一化为 40 位十六进制
        assertEquals(
            "9c1e52f4598f8285265e8de56d7bcfa4a7554baf",
            magnetInfoHash("magnet:?xt=urn:btih:TQPFF5CZR6BIKJS6RXSW266PUSTVKS5P&dn=test"),
        )
        assertEquals(
            "4f7c8c96896666977f76ccc283860841eb21c045",
            magnetInfoHash("magnet:?xt=urn:btih:J56IZFUJMZTJO73WZTBIHBQIIHVSDQCF"),
        )
        // 解析不出统一空串：严禁哨兵值——引擎曾用 "unknown_hash"，
        // 会让两个互不相干的未知磁力被判成同一会话/同一缓存
        assertEquals("", magnetInfoHash(""))
        assertEquals("", magnetInfoHash("https://example.com/1.torrent"))
        assertEquals("", magnetInfoHash("magnet:?dn=nohash"))
    }

    @Test
    fun torrentMediaResource_effectiveMagnetUrl_and_channelFlags() {
        val hashOnly = TorrentMediaResource(title = "Test", infoHash = "0123456789abcdef0123456789abcdef01234567")
        assertEquals(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            hashOnly.effectiveMagnetUrl,
        )
        assertTrue(hashOnly.hasMagnet)
        assertFalse(hashOnly.hasTorrent)
        assertFalse(hashOnly.isDualSource)
        assertTrue(hashOnly.hasPlayableSource)

        val dual =
            TorrentMediaResource(
                title = "Test",
                magnetUrl = "magnet:?xt=urn:btih:abc",
                torrentUrl = "https://example.com/1.torrent",
            )
        assertTrue(dual.hasMagnet)
        assertTrue(dual.hasTorrent)
        assertTrue(dual.isDualSource)
        assertTrue(dual.hasPlayableSource)
    }

    @Test
    fun torrentMediaResource_mergeAndDeduplicate_mergesCrossSiteReleases() {
        // 源 A（如 Mikan）：有磁力和 infoHash，无种子直链，无大小（0L）
        val fromMikan =
            TorrentMediaResource(
                title = "[LoliHouse] JoJo's Bizarre Adventure - SBR - 04 [WebRip 1080p HEVC-10bit AAC] [简繁内封]",
                fansubName = "LoliHouse",
                episodeSort = 4f,
                resolution = "1080P",
                magnetUrl = "magnet:?xt=urn:btih:4f7c8c96896666977f76ccc283860841eb21c045",
                infoHash = "4f7c8c96896666977f76ccc283860841eb21c045",
                contentLength = 0L,
                formattedSize = "",
            )

        // 源 B（如 Bangumi.moe）：标题相同，但无 infoHash，只有种子直链和真实大小
        val fromBangumiMoe =
            TorrentMediaResource(
                title = "[LoliHouse] JoJo's Bizarre Adventure - SBR - 04 [WebRip 1080p HEVC-10bit AAC] [简繁内封].mp4",
                fansubName = "LoliHouse",
                episodeSort = 4f,
                resolution = "1080P",
                torrentUrl = "https://bangumi.moe/download/torrent/12345.torrent",
                contentLength = 471859200L,
                formattedSize = "450.0 MB",
                pubDate = "2026-10-16 07:02:00",
            )

        val merged = listOf(fromMikan, fromBangumiMoe).mergeAndDeduplicate()

        assertEquals(1, merged.size)
        val result = merged.first()
        assertEquals("4f7c8c96896666977f76ccc283860841eb21c045", result.infoHash)
        assertEquals("magnet:?xt=urn:btih:4f7c8c96896666977f76ccc283860841eb21c045", result.magnetUrl)
        assertEquals("https://bangumi.moe/download/torrent/12345.torrent", result.torrentUrl)
        assertEquals(471859200L, result.contentLength)
        assertEquals("450.0 MB", result.formattedSize)
        assertTrue(result.isDualSource)
    }

    @Test
    fun extractReleaseTags_extractsSubtitleAndQualityTags() {
        val tags = extractReleaseTags("[LoliHouse] SBR - 04 [WebRip 1080p HEVC-10bit AAC] [简繁内封] [v2]")
        assertTrue(tags.contains("简繁"))
        assertTrue(tags.contains("内封"))
        assertTrue(tags.contains("HEVC"))
        assertTrue(tags.contains("10bit"))
        assertTrue(tags.contains("v2修正"))
    }

    @Test
    fun formatPubDateShort_formatsIsoAndRfc822Dates() {
        assertEquals("10-16 07:02", formatPubDateShort("2026-10-16T07:02:15Z"))
        assertEquals("10-16 07:02", formatPubDateShort("2026-10-16 07:02:15"))
        assertEquals("10-16 07:02", formatPubDateShort("Thu, 16 Oct 2026 07:02:15 GMT"))
    }
}
