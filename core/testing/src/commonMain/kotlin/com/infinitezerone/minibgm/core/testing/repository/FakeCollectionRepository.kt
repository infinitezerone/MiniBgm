package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCollectionRepository : CollectionRepository {
    private val collectionsState = MutableStateFlow<Map<Long, UserCollection>>(emptyMap())
    private val trackingFootprintState = MutableStateFlow<TrackingFootprint?>(null)
    private val bingeSubjectIdsState = MutableStateFlow<Set<Long>>(emptySet())

    var fetchUserCollectionsResult: AppResult<List<UserCollection>>? = null
    var fetchUserCollectionsCallCount: Int = 0
        private set
    var updateCollectionResult: AppResult<Unit>? = null
    var updateCollectionCallCount: Int = 0
        private set
    var updateEpisodeResult: AppResult<Unit>? = null
    var updateEpisodeCallCount: Int = 0
        private set
    var markEpisodesWatchedUpToCallCount: Int = 0
        private set
    var syncWatchingResult: AppResult<Unit>? = null
    var syncWatchingCallCount: Int = 0
        private set
    var clearUserDataCallCount: Int = 0
        private set
    var fetchCollectionCountsCallCount: Int = 0
        private set
    var fetchCollectionCountsResult: AppResult<Map<CollectionType, Int>>? = null

    fun sendCollection(collection: UserCollection) {
        collectionsState.value = collectionsState.value + (collection.subjectId to collection)
    }

    fun sendTrackingFootprint(footprint: TrackingFootprint?) {
        trackingFootprintState.value = footprint
    }

    override fun getCollectionStream(subjectId: Long): Flow<UserCollection?> = collectionsState.map { it[subjectId] }

    override fun observeTrackingFootprint(): Flow<TrackingFootprint?> = trackingFootprintState

    override fun getCollectionsByTypeStream(type: CollectionType): Flow<List<UserCollection>> =
        collectionsState.map { it.values.filter { col -> col.type == type.value } }

    override fun getBingeSubjectIdsStream(): Flow<Set<Long>> = bingeSubjectIdsState

    override suspend fun toggleBingeSubject(subjectId: Long) {
        val current = bingeSubjectIdsState.value
        bingeSubjectIdsState.value = if (current.contains(subjectId)) current - subjectId else current + subjectId
    }

    override suspend fun fetchUserCollections(
        username: String,
        subjectType: Int,
        type: CollectionType?,
        limit: Int,
        offset: Int,
    ): AppResult<List<UserCollection>> {
        fetchUserCollectionsCallCount++
        fetchUserCollectionsResult?.let { return it }
        val filtered =
            collectionsState.value.values.filter { col ->
                (type == null || col.type == type.value) &&
                    (subjectType == 0 || col.subjectType == subjectType)
            }
        val paged = if (limit > 0) filtered.drop(offset).take(limit) else filtered
        return AppResult.Success(paged)
    }

    override suspend fun fetchCollectionCount(
        username: String,
        type: CollectionType,
    ): AppResult<Int> {
        val count = collectionsState.value.values.count { it.type == type.value }
        return AppResult.Success(count)
    }

    override suspend fun fetchCollectionCounts(
        username: String,
        force: Boolean,
    ): AppResult<Map<CollectionType, Int>> {
        fetchCollectionCountsCallCount++
        fetchCollectionCountsResult?.let { return it }
        val counts =
            CollectionType.entries.associateWith { type ->
                collectionsState.value.values.count { it.type == type.value }
            }
        return AppResult.Success(counts)
    }

    override suspend fun fetchCollection(
        subjectId: Long,
        force: Boolean,
    ): AppResult<UserCollection?> = AppResult.Success(collectionsState.value[subjectId])

    override suspend fun updateCollectionStatus(
        subjectId: Long,
        type: CollectionType,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
        subjectType: Int,
        tags: List<String>?,
    ): AppResult<Unit> {
        updateCollectionCallCount++
        updateCollectionResult?.let { return it }
        val current = collectionsState.value[subjectId]
        val resolvedSubjectType = if (subjectType > 0) subjectType else (current?.subjectType ?: 2)
        val updated =
            current?.copy(
                type = type.value,
                rate = rate ?: current.rate,
                comment = comment ?: current.comment,
                epStatus = epStatus ?: current.epStatus,
                tags = tags ?: current.tags,
                subjectType = resolvedSubjectType,
            ) ?: UserCollection(
                subjectId = subjectId,
                subjectType = resolvedSubjectType,
                type = type.value,
                rate = rate ?: 0,
                comment = comment.orEmpty(),
                epStatus = epStatus ?: 0,
            )
        collectionsState.value = collectionsState.value + (subjectId to updated)
        return AppResult.Success(Unit)
    }

    override suspend fun updateEpisodeStatus(
        subjectId: Long,
        episodeId: Long?,
        isWatched: Boolean,
        epNumber: Int,
    ): AppResult<Unit> {
        updateEpisodeCallCount++
        updateEpisodeResult?.let { return it }
        // 与真实仓库一致（本地优先写库 + 流回读）：更新收藏流的类型推进与进度推进，
        // 调用方（如 ScheduleViewModel）经流回读写后状态
        val current = collectionsState.value[subjectId]
        val resolvedType =
            when {
                isWatched && (current == null || current.type == 0 || current.type == CollectionType.WISH.value) ->
                    CollectionType.DOING.value
                current != null && current.type > 0 -> current.type
                else -> CollectionType.DOING.value
            }
        val resolvedEpStatus =
            if (isWatched) {
                maxOf(current?.epStatus ?: 0, epNumber)
            } else if (epNumber >= (current?.epStatus ?: 0)) {
                maxOf(0, epNumber - 1)
            } else {
                current?.epStatus ?: 0
            }
        val updated =
            current?.copy(
                type = resolvedType,
                epStatus = resolvedEpStatus,
            ) ?: UserCollection(
                subjectId = subjectId,
                subjectType = 2,
                type = resolvedType,
                epStatus = resolvedEpStatus,
            )
        collectionsState.value = collectionsState.value + (subjectId to updated)
        return AppResult.Success(Unit)
    }

    override suspend fun markEpisodesWatchedUpTo(
        subjectId: Long,
        epNumber: Int,
        episodeIds: List<Long>,
    ): AppResult<Unit> {
        markEpisodesWatchedUpToCallCount++
        val current = collectionsState.value[subjectId]
        val updated =
            current?.copy(
                epStatus = maxOf(current.epStatus, epNumber),
                type = if (current.type == 0 || current.type == CollectionType.WISH.value) CollectionType.DOING.value else current.type,
            ) ?: UserCollection(
                userId = 0L,
                subjectId = subjectId,
                subjectType = 2,
                type = CollectionType.DOING.value,
                rate = 0,
                comment = "",
                epStatus = epNumber,
            )
        collectionsState.value = collectionsState.value + (subjectId to updated)
        return AppResult.Success(Unit)
    }

    var revertEpisodesWatchedCallCount: Int = 0
        private set
    var revertEpisodesWatchedResult: AppResult<Unit>? = null

    override suspend fun revertEpisodesWatched(
        subjectId: Long,
        targetEpStatus: Int,
        targetType: CollectionType?,
        undoneEpisodeIds: List<Long>,
    ): AppResult<Unit> {
        revertEpisodesWatchedCallCount++
        revertEpisodesWatchedResult?.let { return it }
        val current = collectionsState.value[subjectId]
        val updated =
            current?.copy(
                epStatus = targetEpStatus,
                type = targetType?.value ?: current.type,
            )
        if (targetType == null && targetEpStatus <= 0) {
            collectionsState.value = collectionsState.value - subjectId
        } else if (updated != null) {
            collectionsState.value = collectionsState.value + (subjectId to updated)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun syncWatchingCollections(force: Boolean): AppResult<Unit> {
        syncWatchingCallCount++
        syncWatchingResult?.let { return it }
        return AppResult.Success(Unit)
    }

    override suspend fun clearUserData(userId: Long) {
        clearUserDataCallCount++
        collectionsState.value = collectionsState.value.filterValues { it.userId != userId }
    }

    override suspend fun clearAllUserData() {
        clearUserDataCallCount++
        collectionsState.value = emptyMap()
    }
}
