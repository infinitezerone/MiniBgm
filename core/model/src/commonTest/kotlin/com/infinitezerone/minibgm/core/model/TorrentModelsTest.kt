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
}
