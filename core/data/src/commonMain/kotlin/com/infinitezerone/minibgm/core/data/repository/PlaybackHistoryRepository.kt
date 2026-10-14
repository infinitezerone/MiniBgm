package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.UserDataClearable
import com.infinitezerone.minibgm.core.database.dao.PlaybackHistoryDao
import com.infinitezerone.minibgm.core.database.entity.PlaybackHistoryEntity
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观看记录仓库：按分集聚合管理用户在播放器中的观看历史与续播断点。
 */
interface PlaybackHistoryRepository : UserDataClearable {
    /** 观察最近观看记录（按时间倒序） */
    fun observeRecentHistory(limit: Int = 100): Flow<List<PlaybackHistory>>

    /** 按分集 ID 获取最近观看断点 */
    suspend fun getHistoryByEpisode(episodeId: Long): PlaybackHistory?

    /** 按番剧 ID 获取该番剧下最近观看的一集记录 */
    suspend fun getLatestHistoryBySubject(subjectId: Long): PlaybackHistory?

    /** 写入或更新观看记录（自动触发容量修剪） */
    suspend fun saveHistory(history: PlaybackHistory)

    /** 删除单条分集观看记录 */
    suspend fun deleteHistory(episodeId: Long)

    /** 删除指定番剧的所有分集观看记录 */
    suspend fun deleteHistoryBySubject(subjectId: Long)

    /** 清空全部观看历史 */
    suspend fun clearAllHistory()
}

class PlaybackHistoryRepositoryImpl(
    private val playbackHistoryDao: PlaybackHistoryDao,
) : PlaybackHistoryRepository {
    override suspend fun clearUserData(userId: Long) {
        clearAllHistory()
    }

    override suspend fun clearAllUserData() {
        clearAllHistory()
    }

    override fun observeRecentHistory(limit: Int): Flow<List<PlaybackHistory>> =
        playbackHistoryDao.observeRecentHistory(limit).map { entities ->
            entities.map { it.toModel() }
        }

    override suspend fun getHistoryByEpisode(episodeId: Long): PlaybackHistory? = playbackHistoryDao.getByEpisodeId(episodeId)?.toModel()

    override suspend fun getLatestHistoryBySubject(subjectId: Long): PlaybackHistory? =
        playbackHistoryDao.getLatestBySubjectId(subjectId)?.toModel()

    override suspend fun saveHistory(history: PlaybackHistory) {
        if (history.episodeId <= 0L) return
        playbackHistoryDao.upsertHistory(history.toEntity())
        playbackHistoryDao.trimOldHistory(keepCount = 200)
    }

    override suspend fun deleteHistory(episodeId: Long) {
        if (episodeId <= 0L) return
        playbackHistoryDao.deleteByEpisodeId(episodeId)
    }

    override suspend fun deleteHistoryBySubject(subjectId: Long) {
        if (subjectId <= 0L) return
        playbackHistoryDao.deleteBySubjectId(subjectId)
    }

    override suspend fun clearAllHistory() {
        playbackHistoryDao.clearAllHistory()
    }
}

private fun PlaybackHistoryEntity.toModel(): PlaybackHistory =
    PlaybackHistory(
        episodeId = episodeId,
        subjectId = subjectId,
        subjectName = subjectName,
        episodeName = episodeName,
        episodeSort = episodeSort,
        coverUrl = coverUrl,
        streamUrl = streamUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

private fun PlaybackHistory.toEntity(): PlaybackHistoryEntity =
    PlaybackHistoryEntity(
        episodeId = episodeId,
        subjectId = subjectId,
        subjectName = subjectName,
        episodeName = episodeName,
        episodeSort = episodeSort,
        coverUrl = coverUrl,
        streamUrl = streamUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
    )
