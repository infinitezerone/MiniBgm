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
            )
        assertEquals(0.3f, buffering.progress)
        assertEquals(1024L * 1024L, buffering.speedBytesPerSec)

        val ready =
            TorrentStreamState.Ready(
                streamUrl = "http://127.0.0.1:8899/stream",
                title = "Episode 01",
                totalBytes = 500_000_000L,
            )
        assertEquals("http://127.0.0.1:8899/stream", ready.streamUrl)
    }
}
