package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.common.UserDataClearable
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import kotlinx.coroutines.CancellationException
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

    /**
     * 获取指定用户的全量收藏统计（五大分类汇总）。
     * [force] 为 true 时绕过内存缓存强制向远端同步。
     */
    suspend fun fetchCollectionCounts(
        username: String,
        force: Boolean = false,
    ): AppResult<Map<CollectionType, Int>>

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

    /**
     * 批量更新单集观看进度至目标话数（看到此集）。
     * 将小于等于目标话数的单集批量标记为已看过（status = 2）。
     * [episodeIds] 为要更新的单集 ID 列表；若为空，内部尝试从远端单集列表解析。
     */
    suspend fun markEpisodesWatchedUpTo(
        subjectId: Long,
        epNumber: Int,
        episodeIds: List<Long> = emptyList(),
    ): AppResult<Unit>

    /**
     * 登录会话建立后，把云端「在看」（动画类）收藏全量分页同步进本地 Room。
     * 时刻表「我追的」、待补更新、桌面小组件与开播提醒均消费本地收藏流，
     * 本地无数据即表现为「没有在追的番」，故会话建立时必须先执行本同步。
     */
    suspend fun syncWatchingCollections(): AppResult<Unit>
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepositoryImpl(
    private val apiService: BangumiApiService,
    private val userCollectionDao: UserCollectionDao,
    private val tokenProvider: TokenProvider,
) : CollectionRepository {
    // 活跃用户由凭据库派生（token 存在才有会话）：偏好文件不参与登录判定，
    // 避免云备份恢复出的陈旧标记让未登录设备渲染「在追/待补」内容
    private val activeUserIdFlow: Flow<Long?> =
        tokenProvider.activeUserId.distinctUntilChanged()

    override fun getCollectionStream(subjectId: Long): Flow<UserCollection?> =
        activeUserIdFlow
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    userCollectionDao
                        .getCollectionBySubjectId(userId, subjectId)
                        .map { entity -> entity?.asExternalModel() }
                }
            }.distinctUntilChanged()

    override fun getCollectionsByTypeStream(type: CollectionType): Flow<List<UserCollection>> =
        activeUserIdFlow
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    userCollectionDao
                        .getCollectionsByType(userId, type.value)
                        .map { list -> list.map { it.asExternalModel() } }
                }
            }.distinctUntilChanged()

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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "获取收藏总数失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "获取收藏总数异常：${e.message}")
        }

    private val collectionCountsCache = mutableMapOf<String, CachedCounts>()

    private data class CachedCounts(
        val counts: Map<CollectionType, Int>,
        val timestamp: Long,
    )

    override suspend fun fetchCollectionCounts(
        username: String,
        force: Boolean,
    ): AppResult<Map<CollectionType, Int>> =
        try {
            val now = TimeUtils.nowEpochMillis()
            val cached = collectionCountsCache[username]
            if (!force && cached != null && (now - cached.timestamp) < CACHE_TTL_MILLIS) {
                AppResult.Success(cached.counts)
            } else {
                val stats = apiService.getUserCollectionStats(username)
                val counts = mutableMapOf<CollectionType, Int>()
                for (group in stats) {
                    for (entry in group.collects) {
                        val type = CollectionType.fromValue(entry.status.id)
                        counts[type] = (counts[type] ?: 0) + entry.count
                    }
                }
                // 确保五大分类键值完整存在（无条目的分类置 0）
                CollectionType.entries.forEach { type ->
                    counts.putIfAbsent(type, 0)
                }
                val resultMap = counts.toMap()
                collectionCountsCache[username] = CachedCounts(resultMap, now)
                AppResult.Success(resultMap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "获取收藏统计失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "获取收藏统计异常：${e.message}")
        }

    override suspend fun fetchCollection(subjectId: Long): AppResult<UserCollection?> {
        val activeUid = tokenProvider.activeUserId.first() ?: return AppResult.Success(null)
        return try {
            val collection = apiService.getCollection(activeUid.toString(), subjectId)
            if (collection != null) {
                userCollectionDao.insertCollection(collection.asEntity(activeUid))
            }
            AppResult.Success(collection)
        } catch (e: CancellationException) {
            throw e
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
            val activeUid = tokenProvider.activeUserId.first()
            if (activeUid == null) return@withContext AppResult.Error(IllegalStateException("未登录账号，无法更新收藏"))

            // 1. 本地历史快照（绑定不可变的当前 activeUid）
            val localPrevious = userCollectionDao.getCollectionBySubjectId(activeUid, subjectId).firstOrNull()
            val resolvedSubjectType = if (subjectType > 0) subjectType else (localPrevious?.subjectType ?: 2)

            // 2. 本地优先：立即乐观写入 Room，全应用零延迟响应
            val optimisticEntity =
                UserCollectionEntity(
                    userId = activeUid,
                    subjectId = subjectId,
                    subjectType = resolvedSubjectType,
                    type = type.value,
                    epStatus = epStatus ?: (localPrevious?.epStatus ?: 0),
                    updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                )
            userCollectionDao.insertCollection(optimisticEntity)

            // 3. 远端同步与失败回滚
            try {
                apiService.updateCollection(
                    subjectId = subjectId,
                    type = type.value,
                    rate = rate,
                    comment = comment,
                    private = private,
                    epStatus = epStatus,
                )
                collectionCountsCache.clear()
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                throw e
            } catch (e: BgmNetworkException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                AppResult.Error(e, "更新收藏状态失败：${e.message}")
            } catch (e: Exception) {
                rollbackRoom(activeUid, subjectId, localPrevious)
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
            val activeUid = tokenProvider.activeUserId.first()
            if (activeUid == null) return@withContext AppResult.Error(IllegalStateException("请先在「我的」页面登录 Bangumi 账号"))

            // 1. 本地历史快照（绑定不可变的当前 activeUid）
            val localPrevious = userCollectionDao.getCollectionBySubjectId(activeUid, subjectId).firstOrNull()

            // 2. 本地优先：计算乐观状态并立即写入 Room，全应用零延迟响应
            val optimisticType = computeTargetType(localPrevious?.type ?: 0, isWatched)
            val optimisticEpStatus = computeTargetEpStatus(localPrevious?.epStatus ?: 0, epNumber, isWatched)

            userCollectionDao.insertCollection(
                UserCollectionEntity(
                    userId = activeUid,
                    subjectId = subjectId,
                    subjectType = localPrevious?.subjectType ?: 2,
                    type = optimisticType,
                    epStatus = optimisticEpStatus,
                    updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                ),
            )

            try {
                // 3. 远端单集 ID 解析（若入参缺失）
                val resolvedEpisodeId =
                    if (episodeId == null || episodeId <= 0L) {
                        resolveEpisodeId(subjectId, epNumber) ?: run {
                            rollbackRoom(activeUid, subjectId, localPrevious)
                            return@withContext AppResult.Error(
                                IllegalStateException("未找到第 $epNumber 话的单集，无法打卡"),
                            )
                        }
                    } else {
                        episodeId
                    }

                // 4. 远端打卡（消除多余 GET，支持 404 自愈）
                val previousType = localPrevious?.type ?: 0
                val isInCollection = previousType > 0 && (!isWatched || previousType != CollectionType.WISH.value)
                checkInWithSelfHealing(
                    subjectId = subjectId,
                    episodeId = resolvedEpisodeId,
                    isWatched = isWatched,
                    targetType = optimisticType,
                    isInCollection = isInCollection,
                )

                // 5. 远端打卡成功后，写入最终对齐状态
                userCollectionDao.insertCollection(
                    UserCollectionEntity(
                        userId = activeUid,
                        subjectId = subjectId,
                        subjectType = localPrevious?.subjectType ?: 2,
                        type = optimisticType,
                        epStatus = optimisticEpStatus,
                        updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                    ),
                )
                collectionCountsCache.clear()
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                throw e
            } catch (e: BgmNetworkException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                AppResult.Error(e, "打卡失败：${e.message}")
            } catch (e: Exception) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                AppResult.Error(e, "打卡异常：${e.message}")
            }
        }

    override suspend fun markEpisodesWatchedUpTo(
        subjectId: Long,
        epNumber: Int,
        episodeIds: List<Long>,
    ): AppResult<Unit> =
        withContext(NonCancellable) {
            val activeUid = tokenProvider.activeUserId.first()
            if (activeUid == null) return@withContext AppResult.Error(IllegalStateException("请先在「我的」页面登录 Bangumi 账号"))

            // 1. 本地历史快照（绑定不可变的当前 activeUid）
            val localPrevious = userCollectionDao.getCollectionBySubjectId(activeUid, subjectId).firstOrNull()

            // 2. 本地优先：计算乐观状态并立即写入 Room，全应用零延迟响应
            val optimisticType = computeTargetType(localPrevious?.type ?: 0, isWatched = true)
            val optimisticEpStatus = maxOf(localPrevious?.epStatus ?: 0, epNumber)

            userCollectionDao.insertCollection(
                UserCollectionEntity(
                    userId = activeUid,
                    subjectId = subjectId,
                    subjectType = localPrevious?.subjectType ?: 2,
                    type = optimisticType,
                    epStatus = optimisticEpStatus,
                    updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                ),
            )

            try {
                // 3. 解析需要打卡的单集 ID 列表
                val targetEpisodeIds =
                    if (episodeIds.isNotEmpty()) {
                        episodeIds
                    } else {
                        val episodes = apiService.getEpisodes(subjectId).data
                        episodes
                            .filter { (it.ep.toInt() in 1..epNumber) || (it.sort.toInt() in 1..epNumber) }
                            .map { it.id }
                    }

                // 4. 远端批量打卡（支持自愈：条目未收录时先加入在看）
                val previousType = localPrevious?.type ?: 0
                val isInCollection = previousType > 0 && previousType != CollectionType.WISH.value
                if (!isInCollection) {
                    try {
                        apiService.updateCollection(subjectId, optimisticType)
                    } catch (_: Exception) {
                        // 忽略创建在看失败，后续单集打卡如报 404 将在 catch 中触发自愈
                    }
                }

                if (targetEpisodeIds.isNotEmpty()) {
                    try {
                        apiService.updateEpisodesStatus(
                            subjectId = subjectId,
                            episodeIds = targetEpisodeIds,
                            type = 2,
                        )
                    } catch (e: BgmNetworkException.NotFound) {
                        // 自愈：条目在云端未收录，先加入在看再重试打卡
                        apiService.updateCollection(subjectId, optimisticType)
                        apiService.updateEpisodesStatus(
                            subjectId = subjectId,
                            episodeIds = targetEpisodeIds,
                            type = 2,
                        )
                    }
                }

                // 5. 远端打卡成功后，写入最终对齐状态
                userCollectionDao.insertCollection(
                    UserCollectionEntity(
                        userId = activeUid,
                        subjectId = subjectId,
                        subjectType = localPrevious?.subjectType ?: 2,
                        type = optimisticType,
                        epStatus = optimisticEpStatus,
                        updatedAt = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis()),
                    ),
                )
                collectionCountsCache.clear()
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                throw e
            } catch (e: BgmNetworkException) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                AppResult.Error(e, "批量打卡失败：${e.message}")
            } catch (e: Exception) {
                rollbackRoom(activeUid, subjectId, localPrevious)
                AppResult.Error(e, "批量打卡异常：${e.message}")
            }
        }
        existingType: Int,
        isWatched: Boolean,
    ): Int =
        if (isWatched && (existingType == 0 || existingType == CollectionType.WISH.value)) {
            CollectionType.DOING.value
        } else if (existingType > 0) {
            existingType
        } else {
            CollectionType.DOING.value
        }

    private fun computeTargetEpStatus(
        currentEpStatus: Int,
        epNumber: Int,
        isWatched: Boolean,
    ): Int =
        if (isWatched) {
            maxOf(currentEpStatus, epNumber)
        } else {
            if (epNumber >= currentEpStatus) maxOf(0, epNumber - 1) else currentEpStatus
        }

    private suspend fun rollbackRoom(
        userId: Long,
        subjectId: Long,
        snapshot: UserCollectionEntity?,
    ) {
        if (snapshot != null) {
            userCollectionDao.insertCollection(snapshot)
        } else {
            userCollectionDao.deleteBySubjectId(userId, subjectId)
        }
    }

    private suspend fun resolveEpisodeId(
        subjectId: Long,
        epNumber: Int,
    ): Long? {
        val episodes = apiService.getEpisodes(subjectId).data
        return episodes.firstOrNull { it.ep.toInt() == epNumber }?.id
            ?: episodes.firstOrNull { it.sort.toInt() == epNumber }?.id
    }

    /**
     * 执行远端打卡：
     * 若条目已在收藏中，直接发送单集打卡；若遇云端 404（多端删除或未同步），自动触发自愈先加入在看再重试打卡。
     * 若条目未在收藏中或仅为想看，先确保远端创建在看收藏，再执行打卡。
     */
    private suspend fun checkInWithSelfHealing(
        subjectId: Long,
        episodeId: Long,
        isWatched: Boolean,
        targetType: Int,
        isInCollection: Boolean,
    ) {
        val checkInType = if (isWatched) 2 else 0
        if (isInCollection) {
            try {
                apiService.updateEpisodeStatus(subjectId, episodeId, checkInType)
            } catch (e: BgmNetworkException.NotFound) {
                // 自愈：条目在云端未收录，先加入在看再重试打卡
                apiService.updateCollection(subjectId, targetType)
                apiService.updateEpisodeStatus(subjectId, episodeId, checkInType)
            }
        } else {
            // 本地无记录或原本仅为想看：先确保远端创建在看收藏，再执行单集打卡
            apiService.updateCollection(subjectId, targetType)
            apiService.updateEpisodeStatus(subjectId, episodeId, checkInType)
        }
    }

    override suspend fun syncWatchingCollections(): AppResult<Unit> =
        try {
            val activeUid =
                tokenProvider.activeUserId.first()
                    ?: return AppResult.Error(IllegalStateException("未登录账号，无法同步收藏"))
            val pageSize = 50
            // 防御服务端 total 异常：最多 20 页（1000 条）封顶
            val maxPages = 20
            var offset = 0
            var total = Int.MAX_VALUE
            var pages = 0
            val allDoingCollections = mutableListOf<UserCollectionEntity>()
            while (offset < total && pages < maxPages) {
                val page =
                    apiService.getUserCollections(
                        username = activeUid.toString(),
                        // 时刻表/待补/提醒的消费面只有动画条目，无需同步全类型
                        subjectType = 2,
                        type = CollectionType.DOING.value,
                        limit = pageSize,
                        offset = offset,
                    )
                total = page.total
                allDoingCollections.addAll(page.data.map { it.asEntity(activeUid) })
                offset += pageSize
                pages++
            }
            // 全部页面拉取成功后，单事务整体替换本地 DOING 列表，彻底消除已弃番/已看过的陈旧脏数据
            userCollectionDao.replaceCollectionsByType(
                userId = activeUid,
                type = CollectionType.DOING.value,
                collections = allDoingCollections,
            )
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "同步在看收藏失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "同步在看收藏异常：${e.message}")
        }

    override suspend fun clearUserData(userId: Long) =
        withContext(NonCancellable) {
            collectionCountsCache.clear()
            userCollectionDao.clearByUserId(userId)
        }

    override suspend fun clearAllUserData() =
        withContext(NonCancellable) {
            collectionCountsCache.clear()
            userCollectionDao.clearAll()
        }

    private companion object {
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 分钟缓存有效期
    }
}

fun UserCollectionEntity.asExternalModel(): UserCollection =
    UserCollection(
        userId = userId,
        subjectId = subjectId,
        subjectType = subjectType,
        rate = 0,
        type = type,
        comment = "",
        epStatus = epStatus,
        volStatus = 0,
        updatedAt = updatedAt,
    )

fun UserCollection.asEntity(userId: Long): UserCollectionEntity =
    UserCollectionEntity(
        userId = userId,
        subjectId = subjectId,
        subjectType = subjectType,
        type = type,
        epStatus = epStatus,
        updatedAt = updatedAt,
    )
