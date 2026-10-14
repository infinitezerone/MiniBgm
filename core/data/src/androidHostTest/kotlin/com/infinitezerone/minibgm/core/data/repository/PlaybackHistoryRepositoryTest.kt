package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.PlaybackHistoryDao
import com.infinitezerone.minibgm.core.database.entity.PlaybackHistoryEntity
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackHistoryRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeDao =
        object : PlaybackHistoryDao {
            val storage = MutableStateFlow<Map<Long, PlaybackHistoryEntity>>(emptyMap())

            override fun observeRecentHistory(limit: Int): Flow<List<PlaybackHistoryEntity>> =
                storage.map { map -> map.values.sortedByDescending { it.updatedAt }.take(limit) }

            override suspend fun getByEpisodeId(episodeId: Long): PlaybackHistoryEntity? = storage.value[episodeId]

            override suspend fun getLatestBySubjectId(subjectId: Long): PlaybackHistoryEntity? =
                storage.value.values
                    .filter { it.subjectId == subjectId }
                    .maxByOrNull { it.updatedAt }

            override suspend fun upsertHistory(history: PlaybackHistoryEntity) {
                storage.value = storage.value + (history.episodeId to history)
            }

            override suspend fun deleteByEpisodeId(episodeId: Long) {
                storage.value = storage.value - episodeId
            }

            override suspend fun deleteBySubjectId(subjectId: Long) {
                storage.value = storage.value.filterValues { it.subjectId != subjectId }
            }

            override suspend fun clearAllHistory() {
                storage.value = emptyMap()
            }

            override suspend fun trimOldHistory(keepCount: Int) {
                if (storage.value.size > keepCount) {
                    val kept =
                        storage.value.values
                            .sortedByDescending { it.updatedAt }
                            .take(keepCount)
                    storage.value = kept.associateBy { it.episodeId }
                }
            }
        }

    private val repository = PlaybackHistoryRepositoryImpl(fakeDao)

    @Test
    fun saveAndGetHistory_persistsCorrectly() =
        runTest {
            val history =
                PlaybackHistory(
                    episodeId = 101L,
                    subjectId = 1001L,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "第 1 话",
                    episodeSort = 1f,
                    coverUrl = "https://example.com/cover.jpg",
                    streamUrl = "https://example.com/video.m3u8",
                    positionMs = 120_000L,
                    durationMs = 240_000L,
                    updatedAt = 1000L,
                )

            repository.saveHistory(history)

            val fetched = repository.getHistoryByEpisode(101L)
            assertNotNull(fetched)
            assertEquals(101L, fetched.episodeId)
            assertEquals("葬送的芙莉莲", fetched.subjectName)
            assertEquals(120_000L, fetched.positionMs)
            assertEquals(0.5f, fetched.progress)
            assertEquals(false, fetched.isCompleted)

            val latest = repository.getLatestHistoryBySubject(1001L)
            assertNotNull(latest)
            assertEquals(101L, latest.episodeId)

            val recentList = repository.observeRecentHistory(10).first()
            assertEquals(1, recentList.size)
            assertEquals(101L, recentList[0].episodeId)
        }

    @Test
    fun deleteHistory_removesTargetEntry() =
        runTest {
            repository.saveHistory(
                PlaybackHistory(
                    episodeId = 101L,
                    subjectId = 1001L,
                    subjectName = "番剧 A",
                    episodeName = "第 1 话",
                    positionMs = 1000L,
                ),
            )
            repository.saveHistory(
                PlaybackHistory(
                    episodeId = 102L,
                    subjectId = 1001L,
                    subjectName = "番剧 A",
                    episodeName = "第 2 话",
                    positionMs = 2000L,
                ),
            )

            repository.deleteHistory(101L)
            assertNull(repository.getHistoryByEpisode(101L))
            assertNotNull(repository.getHistoryByEpisode(102L))

            repository.clearAllHistory()
            assertTrue(repository.observeRecentHistory(10).first().isEmpty())
        }
}
