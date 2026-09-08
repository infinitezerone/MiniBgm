package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.UserDataClearable
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface CollectionRepository : UserDataClearable {
    /** 观察指定条目的收藏状态（响应式绑定当前活跃账号） */
    fun getCollectionStream(subjectId: Long): Flow<UserCollection?>

    /** 观察指定分类（想看/在看/看过等）的收藏列表 */
    fun getCollectionsByTypeStream(type: CollectionType): Flow<List<UserCollection>>

    /** 从远端拉取指定用户的收藏列表 */
    suspend fun fetchUserCollections(
        username: String,
        subjectType: Int = 2,
        type: CollectionType? = null,
        limit: Int = 30,
        offset: Int = 0,
    ): AppResult<List<UserCollection>>

    /** 从远端获取指定用户某分类的收藏总数 */
    suspend fun fetchCollectionCount(
        username: String,
        type: CollectionType,
    ): AppResult<Int>

    /** 从远端拉取指定条目的收藏详情并更新本地 Room 缓存 */
    suspend fun fetchCollection(subjectId: Long): AppResult<UserCollection?>

    /** 更新条目收藏状态（想看/在看/看过、评分、简评等） */
    suspend fun updateCollectionStatus(
        subjectId: Long,
        type: CollectionType,
        rate: Int? = null,
        comment: String? = null,
        private: Boolean = false,
        epStatus: Int? = null,
        subjectType: Int = 0,
    ): AppResult<Unit>

    /**
     * 更新单集观看进度（看过了 / 撤销）。
     * [episodeId] 为 null 时（如时间表待补清单仅有话数、无单集 ID），
     * 按话数从远端单集列表解析真实 ID 后打卡。
     */
    suspend fun updateEpisodeStatus(
        subjectId: Long,
        episodeId: Long?,
        isWatched: Boolean,
        epNumber: Int = 1,
    ): AppResult<Unit>
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepositoryImpl(
    private val apiService: BangumiApiService,
    private val userCollectionDao: UserCollectionDao,
    private val userPreferences: UserPreferencesDataSource,
) : CollectionRepository {
    private val activeUserIdFlow: Flow<Long?> =
        userPreferences.userPreferences
            .map { it.activeUserId.takeIf { id -> id != 0L } }
            .distinctUntilChanged()

    override fun getCollectionStream(subjectId: Long): Flow<UserCollection?> =
        activeUserIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                userCollectionDao
                    .getCollectionBySubjectId(userId, subjectId)
                    .map { entity -> entity?.asExternalModel() }
            }
        }

    override fun getCollectionsByTypeStream(type: CollectionType): Flow<List<UserCollection>> =
        activeUserIdFlow.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                userCollectionDao
                    .getCollectionsByType(userId, type.value)
                    .map { list -> list.map { it.asExternalModel() } }
            }
        }

    override suspend fun fetchUserCollections(
        username: String,
        subjectType: Int,
        type: CollectionType?,
        limit: Int,
        offset: Int,
    ): AppResult<List<UserCollection>> =
        try {
            val response =
                apiService.getUserCollections(
                    username = username,
                    subjectType = subjectType,
                    type = type?.value,
                    limit = limit,
                    offset = offset,
                )
            AppResult.Success(response.data)
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "获取用户收藏失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "获取用户收藏异常：${e.message}")
        }

    override suspend fun fetchCollectionCount(
        username: String,
        type: CollectionType,
    ): AppResult<Int> =
        try {
            val response =
                apiService.getUserCollections(
                    username = username,
                    subjectType = 0,
                    type = type.value,
                    limit = 1,
                    offset = 0,
                )
            AppResult.Success(response.total)
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "获取收藏总数失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "获取收藏总数异常：${e.message}")
        }

    override suspend fun fetchCollection(subjectId: Long): AppResult<UserCollection?> {
        val activeUid = userPreferences.userPreferences.first().activeUserId
        if (activeUid == 0L) return AppResult.Success(null)
        return try {
            val collection = apiService.getCollection(activeUid.toString(), subjectId)
            if (collection != null) {
                userCollectionDao.insertCollection(collection.asEntity(activeUid))
            }
            AppResult.Success(collection)
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "获取收藏状态失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "获取收藏状态异常：${e.message}")
        }
    }

    override suspend fun updateCollectionStatus(
        subjectId: Long,
        type: CollectionType,
        rate: Int?,
        comment: String?,
        private: Boolean,
        epStatus: Int?,
        subjectType: Int,
    ): AppResult<Unit> =
        withContext(NonCancellable) {
            val activeUid = userPreferences.userPreferences.first().activeUserId
            if (activeUid == 0L) return@withContext AppResult.Error(IllegalStateException("未登录账号，无法更新收藏"))
            try {
                apiService.updateCollection(
                    subjectId = subjectId,
                    type = type.value,
                    rate = rate,
                    comment = comment,
                    private = private,
                    epStatus = epStatus,
                )
                // 写入本地 Room 数据库
                val existing = userCollectionDao.getCollectionBySubjectId(activeUid, subjectId).firstOrNull()
                val resolvedSubjectType = if (subjectType > 0) subjectType else (existing?.subjectType ?: 2)
                userCollectionDao.insertCollection(
                    UserCollectionEntity(
                        userId = activeUid,
                        subjectId = subjectId,
                        subjectType = resolvedSubjectType,
                        rate = rate ?: (existing?.rate ?: 0),
                        type = type.value,
                        comment = comment ?: (existing?.comment.orEmpty()),
                        epStatus = epStatus ?: (existing?.epStatus ?: 0),
                        volStatus = existing?.volStatus ?: 0,
                        updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                    ),
                )
                AppResult.Success(Unit)
            } catch (e: BgmNetworkException) {
                AppResult.Error(e, "更新收藏状态失败：${e.message}")
            } catch (e: Exception) {
                AppResult.Error(e, "更新收藏状态异常：${e.message}")
            }
        }

    override suspend fun updateEpisodeStatus(
        subjectId: Long,
        episodeId: Long?,
        isWatched: Boolean,
        epNumber: Int,
    ): AppResult<Unit> =
        withContext(NonCancellable) {
            val activeUid = userPreferences.userPreferences.first().activeUserId
            if (activeUid == 0L) return@withContext AppResult.Error(IllegalStateException("请先在「我的」页面登录 Bangumi 账号"))
            try {
                // 1. 获取当前远端收藏状态
                val existing =
                    try {
                        apiService.getCollection(activeUid.toString(), subjectId)
                    } catch (e: Exception) {
                        null
                    }

                val targetType = existing?.type?.takeIf { it > 0 } ?: CollectionType.DOING.value
                val targetEpStatus =
                    if (isWatched) {
                        maxOf(existing?.epStatus ?: 0, epNumber)
                    } else {
                        maxOf(0, epNumber - 1)
                    }

                if (episodeId == null || episodeId <= 0L) {
                    // 2a. 时间表待补清单等场景仅有话数、无单集 ID：
                    //    episode_id=0 的打卡会被远端拒绝，按话数从单集列表解析真实 ID
                    val episodes = apiService.getEpisodes(subjectId).data
                    val resolvedEpisodeId =
                        episodes.firstOrNull { it.ep.toInt() == epNumber }?.id
                            ?: episodes.firstOrNull { it.sort.toInt() == epNumber }?.id
                            ?: return@withContext AppResult.Error(
                                IllegalStateException("未找到第 $epNumber 话的单集，无法打卡"),
                            )
                    ensureCollectionAndCheckIn(subjectId, existing, targetType, resolvedEpisodeId, isWatched)
                } else {
                    ensureCollectionAndCheckIn(subjectId, existing, targetType, episodeId, isWatched)
                }

                // 3. 远端打卡成功后，将最新的 UserCollectionEntity 存入本地 Room 数据库
                userCollectionDao.insertCollection(
                    UserCollectionEntity(
                        userId = activeUid,
                        subjectId = subjectId,
                        subjectType = 2,
                        rate = existing?.rate ?: 0,
                        type = targetType,
                        comment = existing?.comment.orEmpty(),
                        epStatus = targetEpStatus,
                        volStatus = 0,
                        updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                    ),
                )
                AppResult.Success(Unit)
            } catch (e: BgmNetworkException) {
                AppResult.Error(e, "打卡失败：${e.message}")
            } catch (e: Exception) {
                AppResult.Error(e, "打卡异常：${e.message}")
            }
        }

    /** 确保条目已在用户收藏中（未收藏则置为在看），随后执行单集打卡（type = 2 已看过，0 撤销） */
    private suspend fun ensureCollectionAndCheckIn(
        subjectId: Long,
        existing: UserCollection?,
        targetType: Int,
        episodeId: Long,
        isWatched: Boolean,
    ) {
        if (existing == null || existing.type == 0) {
            apiService.updateCollection(
                subjectId = subjectId,
                type = targetType,
                rate = existing?.rate?.takeIf { it > 0 },
                comment = existing?.comment?.ifBlank { null },
                private = false,
            )
        }
        apiService.updateEpisodeStatus(
            subjectId = subjectId,
            episodeId = episodeId,
            type = if (isWatched) 2 else 0,
        )
    }

    override suspend fun clearUserData(userId: Long) {
        userCollectionDao.clearByUserId(userId)
    }

    override suspend fun clearAllUserData() {
        userCollectionDao.clearAll()
    }
}

fun UserCollectionEntity.asExternalModel(): UserCollection =
    UserCollection(
        userId = userId,
        subjectId = subjectId,
        subjectType = subjectType,
        rate = rate,
        type = type,
        comment = comment,
        epStatus = epStatus,
        volStatus = volStatus,
        updatedAt = updatedAt,
    )

fun UserCollection.asEntity(userId: Long): UserCollectionEntity =
    UserCollectionEntity(
        userId = userId,
        subjectId = subjectId,
        subjectType = subjectType,
        rate = rate,
        type = type,
        comment = comment,
        epStatus = epStatus,
        volStatus = volStatus,
        updatedAt = updatedAt,
    )
