package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.model.TorrentTaskStatus
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun downloadGroups_aggregatesTasksBySubject_correctly() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            // 构造两个番剧的多任务
            repository.startDownload(
                title = "葬送的芙莉莲 01",
                magnetUrl = "magnet:?xt=urn:btih:aaa",
                totalBytes = 500L * 1024 * 1024,
                subjectId = 101L,
                subjectName = "葬送的芙莉莲",
                subjectCoverUrl = "https://example.com/frieren.jpg",
                episodeId = 1001L,
                episodeSort = 1f,
                episodeName = "旅程的终点与开始",
            )
            repository.startDownload(
                title = "葬送的芙莉莲 02",
                magnetUrl = "magnet:?xt=urn:btih:bbb",
                totalBytes = 600L * 1024 * 1024,
                subjectId = 101L,
                subjectName = "葬送的芙莉莲",
                subjectCoverUrl = "https://example.com/frieren.jpg",
                episodeId = 1002L,
                episodeSort = 2f,
                episodeName = "别说魔法是骗人的",
            )
            repository.startDownload(
                title = "孤独摇滚 01",
                magnetUrl = "magnet:?xt=urn:btih:ccc",
                totalBytes = 400L * 1024 * 1024,
                subjectId = 202L,
                subjectName = "孤独摇滚！",
                subjectCoverUrl = "https://example.com/bocchi.jpg",
                episodeId = 2001L,
                episodeSort = 1f,
                episodeName = "滚动的孤独",
            )

            // 将芙莉莲第一集标记为已完成
            val taskList = repository.tasks.value
            val firstTask = taskList.first { it.episodeId == 1001L }
            repository.mutableTasks.value =
                taskList.map {
                    if (it.id == firstTask.id) {
                        it.copy(
                            status = TorrentTaskStatus.COMPLETED,
                            downloadedBytes = it.totalBytes,
                        )
                    } else {
                        it
                    }
                }

            val groups = viewModel.downloadGroups.first { it.isNotEmpty() }
            assertEquals(2, groups.size)

            val frierenGroup = groups.first { it.subjectId == 101L }
            assertEquals("葬送的芙莉莲", frierenGroup.subjectName)
            assertEquals(2, frierenGroup.totalTasks)
            assertEquals(1, frierenGroup.completedCount)
            assertEquals(1, frierenGroup.downloadingCount)
            assertFalse(frierenGroup.isAllCompleted)
            assertEquals(2, frierenGroup.sortedTasks.size)
            assertEquals(1f, frierenGroup.sortedTasks[0].episodeSort, 0.01f)
            assertEquals(2f, frierenGroup.sortedTasks[1].episodeSort, 0.01f)

            val bocchiGroup = groups.first { it.subjectId == 202L }
            assertEquals("孤独摇滚！", bocchiGroup.subjectName)
            assertEquals(1, bocchiGroup.totalTasks)
            assertEquals(0, bocchiGroup.completedCount)
            assertEquals(1, bocchiGroup.downloadingCount)
        }

    @Test
    fun taskControls_pauseResumeDelete_invokesRepository() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            val taskId =
                repository.startDownload(
                    title = "葬送的芙莉莲 01",
                    magnetUrl = "magnet:?xt=urn:btih:aaa",
                    totalBytes = 500L * 1024 * 1024,
                    subjectId = 101L,
                    subjectName = "葬送的芙莉莲",
                )

            viewModel.pauseTask(taskId)
            advanceUntilIdle()
            assertEquals(1, repository.pauseDownloadCallCount)

            viewModel.resumeTask(taskId)
            advanceUntilIdle()
            assertEquals(1, repository.resumeDownloadCallCount)

            viewModel.deleteTask(taskId)
            advanceUntilIdle()
            assertEquals(1, repository.cancelDownloadCallCount)
        }

    @Test
    fun batchControls_pauseAndResumeAll_forSubject() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            repository.startDownload(
                title = "葬送的芙莉莲 01",
                magnetUrl = "magnet:?xt=urn:btih:aaa",
                totalBytes = 500L * 1024 * 1024,
                subjectId = 101L,
            )
            repository.startDownload(
                title = "葬送的芙莉莲 02",
                magnetUrl = "magnet:?xt=urn:btih:bbb",
                totalBytes = 600L * 1024 * 1024,
                subjectId = 101L,
            )

            viewModel.pauseAllForSubject(101L)
            advanceUntilIdle()
            assertEquals(2, repository.pauseDownloadCallCount)

            viewModel.resumeAllForSubject(101L)
            advanceUntilIdle()
            assertEquals(2, repository.resumeDownloadCallCount)

            viewModel.deleteAllForSubject(101L)
            advanceUntilIdle()
            assertEquals(2, repository.cancelDownloadCallCount)
            assertTrue(repository.tasks.value.isEmpty())
        }

    @Test
    fun batchDeleteTasks_deletesSpecifiedTasks() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            val id1 = repository.startDownload(title = "T1", magnetUrl = "m1", totalBytes = 100L)
            val id2 = repository.startDownload(title = "T2", magnetUrl = "m2", totalBytes = 200L)
            val id3 = repository.startDownload(title = "T3", magnetUrl = "m3", totalBytes = 300L)

            viewModel.deleteTasks(listOf(id1, id3))
            advanceUntilIdle()

            assertEquals(2, repository.cancelDownloadCallCount)
            val remainingIds = repository.tasks.value.map { it.id }
            assertEquals(listOf(id2), remainingIds)
        }

    @Test
    fun redownloadTask_resetsDownloadedBytesAndResumes() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            val id = repository.startDownload(title = "T1", magnetUrl = "m1", totalBytes = 500L)
            viewModel.redownloadTask(id)
            advanceUntilIdle()

            assertEquals(1, repository.resumeDownloadCallCount)
            val task = repository.tasks.value.first { it.id == id }
            assertEquals(0L, task.downloadedBytes)
        }

    @Test
    fun storageUsage_isExposedCorrectly() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            advanceUntilIdle()
            val usage = viewModel.storageUsage.value
            assertEquals(500L * 1024L * 1024L, usage.downloadSizeBytes)
            assertEquals(20L * 1024L * 1024L * 1024L, usage.availableBytes)
        }

    @Test
    fun exportToGallery_whenSuccess_emitsExportSuccessEvent() =
        runTest {
            val repository = FakeTorrentRepository()
            repository.exportTaskToGalleryResult = Result.success("content://media/external/video/media/100")
            val viewModel = DownloadViewModel(repository)

            viewModel.exportToGallery("task-1")
            val event = viewModel.events.first()

            assertEquals(1, repository.exportTaskToGalleryCallCount)
            assertEquals(DownloadEvent.ExportSuccess, event)
        }

    @Test
    fun exportToGallery_whenFailure_emitsExportFailedEvent() =
        runTest {
            val repository = FakeTorrentRepository()
            repository.exportTaskToGalleryResult = Result.failure(IllegalStateException("文件未完成"))
            val viewModel = DownloadViewModel(repository)

            viewModel.exportToGallery("task-2")
            val event = viewModel.events.first()

            assertEquals(1, repository.exportTaskToGalleryCallCount)
            assertTrue(event is DownloadEvent.ExportFailed)
            assertEquals("文件未完成", (event as DownloadEvent.ExportFailed).message)
        }

    @Test
    fun exportTasksToGallery_emitsBatchExportResult() =
        runTest {
            val repository = FakeTorrentRepository()
            repository.exportTaskToGalleryResult = Result.success("content://media/external/video/media/100")
            val viewModel = DownloadViewModel(repository)

            viewModel.exportTasksToGallery(listOf("task-1", "task-2"))
            val event = viewModel.events.first()

            assertEquals(2, repository.exportTaskToGalleryCallCount)
            assertTrue(event is DownloadEvent.BatchExportResult)
            val batch = event as DownloadEvent.BatchExportResult
            assertEquals(2, batch.successCount)
            assertEquals(0, batch.failureCount)
        }

    @Test
    fun pauseTasks_and_resumeTasks_delegateToRepository() =
        runTest {
            val repository = FakeTorrentRepository()
            val viewModel = DownloadViewModel(repository)

            viewModel.pauseTasks(listOf("task-1", "task-2"))
            assertEquals(2, repository.pauseDownloadCallCount)

            viewModel.resumeTasks(listOf("task-1", "task-2"))
            assertEquals(2, repository.resumeDownloadCallCount)
        }

    @Test
    fun pauseAll_and_resumeAll_delegateToRepository() =
        runTest {
            val repository = FakeTorrentRepository()
            repository.startDownload(
                title = "Task 1",
                magnetUrl = "magnet:?xt=urn:btih:1111",
                totalBytes = 1000L,
            )
            val viewModel = DownloadViewModel(repository)

            viewModel.pauseAll()
            assertEquals(1, repository.pauseDownloadCallCount)

            viewModel.resumeAll()
            assertEquals(1, repository.resumeDownloadCallCount)
        }

    @Test
    fun cleanTorrentTitle_stripsReleaseGroupAndExtension() {
        val raw = "[LoliHouse] Oshi no Ko - 01 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv"
        val cleaned = cleanTorrentTitle(raw)
        assertEquals("Oshi no Ko - 01 [WebRip 1080p HEVC-10bit AAC ASSx2]", cleaned)

        val rawChs = "【澄空学园】葬送的芙莉莲 第01话.mp4"
        val cleanedChs = cleanTorrentTitle(rawChs)
        assertEquals("葬送的芙莉莲 第01话", cleanedChs)
    }

    @Test
    fun extractMediaTags_extractsResolutionCodecAndFormat() {
        val task =
            com.infinitezerone.minibgm.core.model.TorrentTask(
                id = "t1",
                title = "[NC-Raws] Frieren - 01 [1080p][HEVC][AAC].mkv",
                magnetUrl = "magnet:?xt=urn:btih:abc",
                savePath = "/downloads/Frieren_01.mkv",
                totalBytes = 1024L * 1024L * 1024L * 2,
            )
        val tags = extractMediaTags(task)
        assertTrue(tags.contains("1080P"))
        assertTrue(tags.contains("HEVC"))
        assertTrue(tags.contains("MKV"))
        assertTrue(tags.contains("2.0 GB"))
    }
}
