package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.repository.PlaybackHistoryRepository
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePlaybackHistoryRepository : PlaybackHistoryRepository {
    private val historyState = MutableStateFlow<Map<Long, PlaybackHistory>>(emptyMap())

    override fun observeRecentHistory(limit: Int): Flow<List<PlaybackHistory>> =
        historyState.map { map ->
            map.values.sortedByDescending { it.updatedAt }.take(limit)
        }

    override suspend fun getHistoryByEpisode(episodeId: Long): PlaybackHistory? = historyState.value[episodeId]

    override suspend fun getLatestHistoryBySubject(subjectId: Long): PlaybackHistory? =
        historyState.value.values
            .filter { it.subjectId == subjectId }
            .maxByOrNull { it.updatedAt }

    override suspend fun saveHistory(history: PlaybackHistory) {
        historyState.value = historyState.value + (history.episodeId to history)
    }

    override suspend fun deleteHistory(episodeId: Long) {
        historyState.value = historyState.value - episodeId
    }

    override suspend fun deleteHistoryBySubject(subjectId: Long) {
        historyState.value = historyState.value.filterValues { it.subjectId != subjectId }
    }

    override suspend fun clearAllHistory() {
        historyState.value = emptyMap()
    }

    override suspend fun clearUserData(userId: Long) {
        clearAllHistory()
    }

    override suspend fun clearAllUserData() {
        clearAllHistory()
    }
}
