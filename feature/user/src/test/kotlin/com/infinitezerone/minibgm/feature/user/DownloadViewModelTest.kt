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
}
