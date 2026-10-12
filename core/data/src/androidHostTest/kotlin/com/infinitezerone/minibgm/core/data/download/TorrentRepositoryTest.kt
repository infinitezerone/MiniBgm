package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentRepositoryTest {
    @Test
    fun fakeTorrentRepository_taskLifecycle_managesStateCorrectly() =
        runTest {
            val repository = FakeTorrentRepository()

            val taskId =
                repository.startDownload(
                    title = "葬送的芙莉莲 01",
                    magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef",
                    torrentUrl = "https://mikanani.me/Download/20231001/01.torrent",
                    totalBytes = 450L * 1024 * 1024,
                )

            assertEquals(1, repository.startDownloadCallCount)
            assertEquals(1, repository.tasks.value.size)
            assertEquals(
                TorrentTaskStatus.DOWNLOADING,
                repository.tasks.value
                    .first()
                    .status,
            )

            repository.pauseDownload(taskId)
            assertEquals(1, repository.pauseDownloadCallCount)
            assertEquals(
                TorrentTaskStatus.PAUSED,
                repository.tasks.value
                    .first()
                    .status,
            )

            repository.resumeDownload(taskId)
            assertEquals(1, repository.resumeDownloadCallCount)
            assertEquals(
                TorrentTaskStatus.DOWNLOADING,
                repository.tasks.value
                    .first()
                    .status,
            )

            val torrentResult = repository.downloadTorrentFile("https://mikanani.me/Download/1.torrent", "ep1")
            assertTrue(torrentResult.isSuccess)
            assertEquals(1, repository.downloadTorrentFileCallCount)

            repository.cancelDownload(taskId)
            assertEquals(1, repository.cancelDownloadCallCount)
            assertTrue(repository.tasks.value.isEmpty())
        }
}
