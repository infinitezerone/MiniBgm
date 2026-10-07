package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.runCatchingCancellable
import com.infinitezerone.minibgm.core.common.toSecureUrl
import com.infinitezerone.minibgm.core.data.search.SearchAliasIndex
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.AirScheduleEvent
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.network.AniListAiringEpisode
import com.infinitezerone.minibgm.core.network.AniListMediaSchedule
import com.infinitezerone.minibgm.core.network.AniListWeeklyScheduleItem
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotDto
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotItemDto
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotResult
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotService
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

// 时间常量 HOUR_MILLIS / DAY_MILLIS / WEEK_MILLIS 已随纯辅助函数提升为 internal 顶层，
// 见同包 ScheduleMappers.kt；同包引用照常解析。

interface ScheduleRepository {
    fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>>

    /**
     * 全量排期流（单流监听，避免按天拆流导致的重复查询与高频重组）。
     * 全量刷新管线进行期间扣住中间态，仅在管线完成后以最新完整数据对外发流。
     */
    fun getAllSchedulesStream(): Flow<List<AirSchedule>>

    /** 全量逐话播出事件流（用于按真实自然日精准匹配单集与播出时刻） */
    fun getAllAirEventsStream(): Flow<List<AirScheduleEvent>>

    /**
     * 查询指定条目（通常为"我追的"）在时间窗口内的播出事件，
     * 窗口范围从当前时间回溯 [lookbackHours] 小时至未来 [hoursAhead] 小时，
     * 按播出时间升序；同话多源时按可信度去重（actual/scheduled 优先于 predicted）。
     */
    suspend fun getUpcomingAiringForSubjects(
        subjectIds: List<Long>,
        hoursAhead: Long = 24,
        lookbackHours: Long = 0,
    ): List<UpcomingAiring>

    /**
     * 查询 [fromUtcIso, toUtcIso] 内确有播出事件的条目名单，按 Bangumi 评分降序。
     *
     * 与 [getUpcomingAiringForSubjects] 的区别：不限定"我追的"，且接受任意绝对时间窗而非相对小时数。
     * 季度导视用它判断"本季在播"——长期连载番（名侦探柯南等）的首播日远在当季之前，
     * Bangumi 的 `air_date` 区间过滤永远捞不到它们，只有真实播出事件能证明它本季仍在播。
     *
     * 注意：事件数据是滚动快照，只覆盖"当前 ± 数周/数季"，超出范围的**历史季度**会返回空，
     * 调用方需自行判断该季度是否落在快照覆盖期内。
     */
    suspend fun getSchedulesAiringBetween(
        fromUtcIso: String,
        toUtcIso: String,
    ): List<AirSchedule>

    /**
     * 全量刷新管线：
     * 下载放送时刻表快照（minibgm-schedule-data CI 定时扫描产物），
     * 期间对外流被闸门扣住，全部完成后才以最终状态对外发一次。
     *
     * [force] = false 时按 [REFRESH_THROTTLE_MILLIS] 节流：距上次成功同步不足阈值直接返回，
     * 页面重建（冷启动/切 Tab）不应重复跑全量管线；下拉刷新等用户显式动作传 true。
     */
    suspend fun refreshAllSchedules(force: Boolean = false): AppResult<Unit>

    /**
     * 后台 / 手动同步：
     * 下载放送时刻表快照（minibgm-schedule-data CI 快照）补全播放源与中文名，
     * 并仲裁回写逐话播出事实。
     */
    suspend fun syncBangumiData(): AppResult<Unit>

    /**
     * 本地别名词典容错搜索（UX_REMEDIATION 06-A）：
     * 对 Room 缓存的排期条目（bangumi-data 已同步窗口内）按归一化名称做容错匹配。
     * 供搜索页离线降级与别名兜底使用。
     */
    suspend fun searchLocalSubjects(
        query: String,
        limit: Int = 8,
    ): List<com.infinitezerone.minibgm.core.model.LocalSubjectMatch>

    /** 放送时刻表默认筛选：false 为全部，true 为仅展示我追的番 */
    suspend fun getScheduleDefaultOnlyWatching(): Boolean

    /** 持久化放送时刻表默认筛选 */
    suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean)

    /**
     * 获取指定年份与季度的静态全量番剧列表（全格式、含里番，优先本地缓存与 CDN 静态源）。
     */
    suspend fun getSeasonAnimeList(
        year: Int,
        seasonKey: String,
    ): List<com.infinitezerone.minibgm.core.model.Subject>
}

internal class ScheduleRepositoryImpl(
    private val scheduleDao: AirScheduleDao,
    private val airEventDao: AirEventDao,
    private val anilistMappingDao: AniListMappingDao,
    private val snapshotService: ScheduleSnapshotService,
    private val userPreferences: UserPreferencesDataSource,
    private val collectionRepository: CollectionRepository? = null,
    private val json: Json = BgmHttpClient.jsonConfig,
) : ScheduleRepository {
    private val seasonalCache = mutableMapOf<String, List<com.infinitezerone.minibgm.core.model.Subject>>()
    private val seasonalCacheMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun getSeasonAnimeList(
        year: Int,
        seasonKey: String,
    ): List<com.infinitezerone.minibgm.core.model.Subject> {
        val cacheKey = "$year-$seasonKey"
        seasonalCacheMutex.withLock {
            val cached = seasonalCache[cacheKey]
            if (cached != null) return cached
        }
        val snapshot = snapshotService.getSeasonSnapshot(year, seasonKey) ?: return emptyList()
        val subjects = snapshot.items.mapNotNull { it.toSubject() }
        seasonalCacheMutex.withLock {
            seasonalCache[cacheKey] = subjects
        }
        return subjects
    }

    override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> =
        scheduleDao.getSchedulesByWeekday(weekday).map { entities ->
            val nowMillis = TimeUtils.nowEpochMillis()
            entities
                .filter { it.bgmId > 0 && it.isActiveForSchedule(nowMillis) }
                .map { it.toModel(json) }
        }

    override fun getAllAirEventsStream(): Flow<List<AirScheduleEvent>> =
        airEventDao.getAllAirEventsStream().map { list ->
            list.map { AirScheduleEvent(it.subjectId, it.episode, it.airAtUtc, it.kind) }
        }

    /**
     * 同步闸门：全量刷新管线进行中为 true。
     * 闸门激活期间仅扣留管线产生的中间态发射；本地首帧（已有缓存）坚决第一时间直发，
     * 确保冷启动与离线场景 0ms 显示内容，管线放开后以最新状态对外发一次。
     */
    private val scheduleHoldGate = MutableStateFlow(false)

    /** 管线锁：UI 刷新与后台 worker 并发时后者排队，避免闸门被先完成的一方提前放开。 */
    private val refreshPipelineMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllSchedulesStream(): Flow<List<AirSchedule>> =
        channelFlow {
            val daoFlow =
                scheduleDao.getAllSchedules().map { entities ->
                    val nowMillis = TimeUtils.nowEpochMillis()
                    entities
                        .filter { it.isActiveForSchedule(nowMillis) }
                        .map { it.toModel(json) }
                }

            var hasEmitted = false
            var pendingValue: List<AirSchedule>? = null
            val mutex = Mutex()

            launch {
                scheduleHoldGate.collect { holding ->
                    mutex.withLock {
                        if (!holding && pendingValue != null) {
                            val toSend = pendingValue!!
                            pendingValue = null
                            send(toSend)
                        }
                    }
                }
            }

            daoFlow.collect { list ->
                mutex.withLock {
                    val isHolding = scheduleHoldGate.value
                    if (!hasEmitted) {
                        // 首帧（本地已有缓存）无论是否处于刷新期，坚决第一时间发给 UI，确保 0ms 离线缓存呈现
                        hasEmitted = true
                        pendingValue = null
                        send(list)
                    } else if (isHolding) {
                        // 刷新管线进行中且已有首帧：扣留中间态，待管线放开闸门时一次性发最新状态
                        pendingValue = list
                    } else {
                        // 正常非刷新期：直接发流
                        pendingValue = null
                        send(list)
                    }
                }
            }
        }

    override suspend fun getUpcomingAiringForSubjects(
        subjectIds: List<Long>,
        hoursAhead: Long,
        lookbackHours: Long,
    ): List<UpcomingAiring> {
        if (subjectIds.isEmpty()) return emptyList()
        val nowMillis = TimeUtils.nowEpochMillis()
        val fromIso = TimeUtils.isoUtcFromEpochMillis(nowMillis - lookbackHours * HOUR_MILLIS)
        val toIso = TimeUtils.isoUtcFromEpochMillis(nowMillis + hoursAhead * HOUR_MILLIS)
        val titles = scheduleDao.getSchedulesByIds(subjectIds).associateBy { it.bgmId }
        val storedEvents = airEventDao.getUpcomingEvents(subjectIds, fromIso, toIso)
        if (storedEvents.isNotEmpty()) {
            return resolveFromStoredEvents(storedEvents, titles)
        }
        return resolveFromSchedulesFallback(subjectIds, titles, nowMillis, hoursAhead, lookbackHours)
    }

    override suspend fun getSchedulesAiringBetween(
        fromUtcIso: String,
        toUtcIso: String,
    ): List<AirSchedule> {
        val subjectIds = airEventDao.getSubjectIdsWithEventsBetween(fromUtcIso, toUtcIso)
        if (subjectIds.isEmpty()) return emptyList()
        return scheduleDao
            .getSchedulesByIds(subjectIds)
            .filter { it.bgmId > 0 }
            .sortedByDescending { it.ratingScore }
            .map { it.toModel(json) }
    }

    private fun resolveFromStoredEvents(
        storedEvents: List<AirEventEntity>,
        titles: Map<Long, AirScheduleEntity>,
    ): List<UpcomingAiring> =
        storedEvents
            // 同话多源去重：actual/scheduled 优先于 predicted
            .groupBy { it.subjectId to it.episode }
            .map { (_, sameEpisode) ->
                sameEpisode.minWithOrNull(
                    compareBy(
                        { AirEventKind.rank(it.kind) },
                        { TimeUtils.epochMillisOfIso(it.airAtUtc) ?: Long.MAX_VALUE },
                    ),
                )!!
            }.sortedWith(compareBy { TimeUtils.epochMillisOfIso(it.airAtUtc) ?: Long.MAX_VALUE })
            .mapNotNull { event ->
                val subject = titles[event.subjectId] ?: return@mapNotNull null
                UpcomingAiring(
                    subjectId = event.subjectId,
                    title = subject.title,
                    titleCn = subject.titleCn,
                    episode = event.episode,
                    airAtUtc = event.airAtUtc,
                    kind = event.kind,
                    coverUrl = subject.coverUrl,
                    totalEpisodes = subject.totalEpisodes,
                )
            }

    private fun resolveFromSchedulesFallback(
        subjectIds: List<Long>,
        titles: Map<Long, AirScheduleEntity>,
        nowMillis: Long,
        hoursAhead: Long,
        lookbackHours: Long,
    ): List<UpcomingAiring> {
        // 快速路径：若 air_events 暂无事件，直接根据 air_schedules 单表实体秒级生成
        val fromMillis = nowMillis - lookbackHours * HOUR_MILLIS
        val toMillis = nowMillis + hoursAhead * HOUR_MILLIS
        return subjectIds
            .mapNotNull { subId ->
                val entity = titles[subId] ?: return@mapNotNull null
                val airUtc = entity.nextEpisodeAtUtc.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val airMillis = TimeUtils.epochMillisOfIso(airUtc) ?: return@mapNotNull null
                if (airMillis in fromMillis..toMillis) {
                    UpcomingAiring(
                        subjectId = entity.bgmId,
                        title = entity.title,
                        titleCn = entity.titleCn,
                        episode = entity.nextEpisode,
                        airAtUtc = airUtc,
                        kind = entity.nextEpisodeKind.ifBlank { AirEventKind.SCHEDULED },
                        coverUrl = entity.coverUrl,
                        totalEpisodes = entity.totalEpisodes,
                    )
                } else {
                    null
                }
            }.sortedBy { TimeUtils.epochMillisOfIso(it.airAtUtc) ?: Long.MAX_VALUE }
    }

    override suspend fun refreshAllSchedules(force: Boolean): AppResult<Unit> {
        if (!force) {
            val lastSync = userPreferences.userPreferences.firstOrNull()?.bangumiDataLastSyncTimestamp ?: 0L
            if (TimeUtils.nowEpochMillis() - lastSync < REFRESH_THROTTLE_MILLIS) {
                return AppResult.Success(Unit)
            }
        }
        // 管线锁包住“置闸门 → 跑管线 → 放闸门”整段：并发调用只会串行跑，不会提前放开
        return refreshPipelineMutex.withLock {
            scheduleHoldGate.value = true
            try {
                syncBangumiData()
            } finally {
                scheduleHoldGate.value = false
            }
        }
    }

    override suspend fun syncBangumiData(): AppResult<Unit> =
        try {
            // 逐话事件同步与仲裁（全量管线中唯一一次）：快照直拉 + 名单发现 + 排期回写全部收口
            runCatchingCancellable { syncAirEvents() }
            userPreferences.setBangumiDataLastSyncTimestamp(TimeUtils.nowEpochMillis())
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("同步番组数据"))
        }

    override suspend fun searchLocalSubjects(
        query: String,
        limit: Int,
    ): List<com.infinitezerone.minibgm.core.model.LocalSubjectMatch> {
        if (query.isBlank()) return emptyList()
        val entities = scheduleDao.getAllSchedulesList()
        val index =
            SearchAliasIndex(
                entries =
                    entities
                        .filter { it.bgmId > 0 }
                        .map { entity ->
                            SearchAliasIndex.Entry(
                                subjectId = entity.bgmId,
                                title = entity.title,
                                titleCn = entity.titleCn,
                            )
                        },
            )
        return index.search(query, limit)
    }

    /**
     * 同步全量条目的播出事件并仲裁时刻表：
     * 1) 下载放送时刻表快照（minibgm-schedule-data，CI 定时扫描 AniList 的产物）；
     * 2) 快照投喂名单发现与本地/快照映射（映射不上直接丢弃，不生成占位条目）；
     * 3) 仲裁回写条目的 next* 字段与 weekday，自动回补高清封面，并剔除已完结僵尸条目。
     * 快照下载失败时保持本地现状（fail-open），等待下一班 CI / 刷新。
     */
    private suspend fun syncAirEvents() {
        val cachedEtag =
            runCatchingCancellable {
                userPreferences.userPreferences
                    .firstOrNull()
                    ?.scheduleSnapshotEtag
                    ?.ifBlank { null }
            }.getOrNull()
        val snapshotResult =
            runCatchingCancellable { snapshotService.getSnapshot(cachedEtag) }.getOrNull()
                ?: return
        val snapshot =
            when (snapshotResult) {
                is ScheduleSnapshotResult.NotModified -> {
                    // 远端快照未变动：304 节省 100% 流量与数据库重写开销
                    userPreferences.setBangumiDataLastSyncTimestamp(TimeUtils.nowEpochMillis())
                    return
                }
                is ScheduleSnapshotResult.Modified -> {
                    val etag = snapshotResult.etag
                    if (!etag.isNullOrBlank()) {
                        userPreferences.setScheduleSnapshotEtag(etag)
                    }
                    snapshotResult.snapshot
                }
            }
        val baseEntities = scheduleDao.getAllSchedulesList()
        val nowMillis = TimeUtils.nowEpochMillis()
        val resolution = resolveWeeklyAiringSchedules(baseEntities, nowMillis, snapshot)
        if (resolution.entities.isEmpty()) return

        val trackingSubjectIds =
            collectionRepository
                ?.getCollectionsByTypeStream(CollectionType.DOING)
                ?.firstOrNull()
                ?.map { it.subjectId }
                ?.toSet()

        // 名单裁剪：只有"本周 AniList 在播"或"用户在追"的条目留在时刻表。
        // 删日历后不再有官方名单，靠这一步把遗留 official 行、往季已完结番剔除；
        // 周排期拉取失败（roster 为空）时不裁剪，避免网络抖动清空时刻表。
        val tracked = trackingSubjectIds.orEmpty()
        val entities =
            if (resolution.rosterAnilistIds.isNotEmpty()) {
                resolution.entities.filter { entity ->
                    (entity.anilistId?.let { it in resolution.rosterAnilistIds } == true) || entity.bgmId in tracked
                }
            } else {
                resolution.entities
            }
        if (entities.isEmpty()) return

        val keepIds = entities.map { it.bgmId }.toSet()
        val cutoffDate = TimeUtils.formatEpochSecondsToDate((nowMillis - ROSTER_LOOKBACK_DAYS * DAY_MILLIS) / 1000)

        val targets =
            if (!trackingSubjectIds.isNullOrEmpty()) {
                entities.filter {
                    it.bgmId in trackingSubjectIds ||
                        it.source == AirScheduleEntity.SOURCE_BGM_DATA ||
                        it.coverUrl.isBlank()
                }
            } else {
                entities
            }

        // 1. 快照自带逐话真值与高清封面（拆季偏移在此生效）
        val (anilistEvents, _, anilistCovers) = fetchAnilistAirEvents(targets, nowMillis, snapshot)
        // 单一事务原子同步播出事件，杜绝中间态导致 UI 重组闪烁
        airEventDao.syncAirEventsTransaction(
            keepSubjectIds = keepIds.toList(),
            eventsGroupedBySubject = anilistEvents.groupBy { it.subjectId },
        )

        // 3. 仲裁回写：next* 字段取未来最近一话（或刚播出的上一话），回补 AniList 高清封面，并剔除已完结僵尸条目
        val allEvents = airEventDao.getAllAirEvents().groupBy { it.subjectId }
        val entitiesWithCovers =
            if (anilistCovers.isNotEmpty()) {
                entities.map { entity ->
                    val anilistCover = anilistCovers[entity.bgmId]
                    if (entity.coverUrl.isBlank() && !anilistCover.isNullOrBlank()) {
                        entity.copy(coverUrl = anilistCover)
                    } else {
                        entity
                    }
                }
            } else {
                entities
            }

        // 剔除已完结或在未来无任何播出事件的 bgm_data 过期条目
        val (activeEntities, zombieEntities) =
            entitiesWithCovers.partition { entity ->
                !isStalePrunableSchedule(entity, allEvents[entity.bgmId].orEmpty(), nowMillis)
            }

        val reconciled = reconcileScheduleEntities(activeEntities, allEvents, nowMillis)
        // 单一事务原子同步时刻表条目
        scheduleDao.syncSchedulesTransaction(
            keepBgmIds = keepIds.toList(),
            cutoffDate = cutoffDate,
            zombieBgmIds = zombieEntities.map { it.bgmId },
            reconciledSchedules = reconciled,
        )
    }

    private suspend fun fetchAnilistAirEvents(
        entities: List<AirScheduleEntity>,
        nowMillis: Long,
        snapshot: ScheduleSnapshotDto,
    ): Triple<List<AirEventEntity>, Set<Long>, Map<Long, String>> {
        val withAnilistId = entities.filter { it.anilistId != null }
        val schedulesByAnilistId =
            snapshot.items.associate { item ->
                item.anilistId to
                    AniListMediaSchedule(
                        episodes = item.episodes.map { AniListAiringEpisode(episode = it.n, airAtEpochSeconds = it.t) },
                        coverUrl = item.coverUrl,
                    )
            }
        val anilistEvents = mutableListOf<AirEventEntity>()
        val coveredSubjects = mutableSetOf<Long>()
        val coversBySubjectId = mutableMapOf<Long, String>()

        for (entity in withAnilistId) {
            val anilistId = entity.anilistId ?: continue
            val mediaSchedule = schedulesByAnilistId[anilistId] ?: continue
            val coverUrl = mediaSchedule.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                coversBySubjectId[entity.bgmId] = coverUrl.toSecureUrl()
            }
            val episodes = mediaSchedule.episodes
            if (episodes.isEmpty()) continue

            // 拆季偏移推导（锚点只信真实 Bangumi 开播日期）：
            // 当季绝大多数条目在 AniList 为独立条目（从第 1 话开始），默认 offset = 0；
            // 若 AniList 话数从 >1 开始，且首个样本就在 Bangumi 开播日附近（±14天），
            // 则表明 AniList 顺延了上半季话数，其实际正对应本季第 1 话（如 AniList 13 话对齐 Bangumi 下半季第 1 话）。
            // airDate 未知（如快照 tier-0 新建实体）时强制 offset = 0 信任 AniList 编号，
            // 绝不用条目自身的播出时间当锚（那会把"第 N 话"系统性错算成"第 1 话"），
            // 也不因首播日跨度大而把整部番剧跳过，播出时刻始终从真实单集时间戳直接解析。
            // airDate 由 enrichMissingMetadata 按 Bangumi 条目日期回填，下一轮刷新起生效。
            val anchorMillis = TimeUtils.epochMillisOfIso(entity.airDate)

            val anchorEp =
                if (anchorMillis != null) {
                    episodes.minByOrNull { abs(it.airAtEpochSeconds * 1000 - anchorMillis) }
                } else {
                    null
                }

            val offset =
                if (anchorMillis != null && anchorEp != null && anchorEp.episode > 1) {
                    val daysDiff = abs(anchorEp.airAtEpochSeconds * 1000 - anchorMillis) / DAY_MILLIS
                    if (daysDiff <= 14) {
                        anchorEp.episode - 1
                    } else {
                        val estimatedEp1Millis = anchorEp.airAtEpochSeconds * 1000 - (anchorEp.episode - 1L) * WEEK_MILLIS
                        val diffMillis = anchorMillis - estimatedEp1Millis
                        if (diffMillis > 14 * DAY_MILLIS) {
                            val offsetWeeks = kotlin.math.round(diffMillis.toDouble() / WEEK_MILLIS).toInt()
                            if (offsetWeeks in 1..<anchorEp.episode) offsetWeeks else 0
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }

            for (episode in episodes) {
                val bgmEpisode = episode.episode - offset
                if (bgmEpisode < 1) continue
                if (entity.totalEpisodes > 0 && bgmEpisode > entity.totalEpisodes) continue
                val airAtMillis = episode.airAtEpochSeconds * 1000
                val kind = if (airAtMillis <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED
                anilistEvents +=
                    AirEventEntity(
                        subjectId = entity.bgmId,
                        episode = bgmEpisode,
                        airAtUtc = TimeUtils.isoUtcFromEpochMillis(airAtMillis),
                        kind = kind,
                        source = EVENT_SOURCE_ANILIST,
                    )
            }
            coveredSubjects += entity.bgmId
        }
        return Triple(anilistEvents, coveredSubjects, coversBySubjectId)
    }

    /**
     * 用快照数据把全网在播条目补齐/绑定到本地时刻表。
     *
     * anilistId → bgmId 映射（映射不上直接丢弃，不生成占位条目）：
     * 0) 快照自带 bgmId（CI 侧已由 bangumi-data 桥接或搜索兜底完成解析）；
     * 1) 本地已有但尚未绑定 anilistId 的条目，按归一化标题精确匹配；
     * 2) 本地映射缓存（历史已保存的映射）；
     * 全部落空即丢弃该条目（零网络额外开销，fail-open）。
     */
    private suspend fun resolveWeeklyAiringSchedules(
        currentEntities: List<AirScheduleEntity>,
        nowMillis: Long,
        snapshot: ScheduleSnapshotDto,
    ): WeeklyResolution {
        // 季度时刻表与逐话事件窗口（前 30 天 + 今天 + 后 120 天，覆盖当季全量在播与待播集数）
        val windowStartSeconds = (nowMillis - 30 * DAY_MILLIS) / 1000
        val windowEndSeconds = (nowMillis + 120 * DAY_MILLIS) / 1000
        val weeklyItems =
            snapshot.items
                .flatMap { item ->
                    item.episodes
                        .filter { it.t in windowStartSeconds..windowEndSeconds }
                        .map { ep ->
                            AniListWeeklyScheduleItem(
                                anilistId = item.anilistId,
                                episode = ep.n,
                                airAtEpochSeconds = ep.t,
                                titleNative = item.title,
                                coverUrl = item.coverUrl,
                                format = item.format,
                                startYear = item.startYear,
                                startMonth = item.startMonth,
                                isAdult = item.isAdult,
                            )
                        }
                }

        if (weeklyItems.isEmpty()) return WeeklyResolution(currentEntities, emptySet())

        val state =
            WeeklyResolutionState(
                entitiesByAnilistId =
                    currentEntities
                        .filter { it.anilistId != null }
                        .associateBy { it.anilistId!! }
                        .toMutableMap(),
                entitiesByBgmId = currentEntities.associateBy { it.bgmId }.toMutableMap(),
                mappingCache = loadMappingCache(weeklyItems.map { it.anilistId }),
            )
        val snapshotItemByAnilistId = snapshot.items.associateBy { it.anilistId }

        for (item in weeklyItems) {
            val ctx = weeklyItemContextOf(item, snapshotItemByAnilistId[item.anilistId])

            // A) 该 anilistId 已绑定到正式条目：只补元数据，不重新绑定
            val alreadyMapped = state.entitiesByAnilistId[item.anilistId]
            if (alreadyMapped != null && alreadyMapped.bgmId > 0) {
                refreshAlreadyBoundEntity(ctx, alreadyMapped, state, nowMillis)
                continue
            }

            // 上一轮的占位条目：本次先移除，映射成功则升级为正式条目，失败则随裁剪删除
            if (alreadyMapped != null) {
                state.entitiesByAnilistId.remove(item.anilistId)
                state.entitiesByBgmId.remove(alreadyMapped.bgmId)
            }

            // B) 快照自带 bgmId（CI 侧桥接/搜索已解析）：最优路径，零额外请求；否则回落到本地历史映射缓存
            if (bindBySnapshotMapping(ctx, state, nowMillis)) continue

            // C) 本地标题精确匹配：只考虑尚未绑定 anilistId 的条目
            if (bindByLocalTitleMatch(ctx, currentEntities, state, nowMillis)) continue

            // D) 均未映射：直接跳过（CI 侧未收录条目，客户端不发起实时搜索/探测，不生成占位条目）
        }

        // 保持在播状态或在未来仍有播出事件的条目名单，防止单周停更被误删
        val allActiveAnilistIds =
            snapshot.items
                .filter { item ->
                    item.status != "FINISHED" || item.episodes.any { it.t >= windowStartSeconds }
                }.map { it.anilistId }
                .toSet()

        persistWeeklyResolution(state)
        return WeeklyResolution(
            entities = state.entitiesByBgmId.values.toList(),
            rosterAnilistIds = allActiveAnilistIds.ifEmpty { weeklyItems.map { it.anilistId }.toSet() },
        )
    }

    /**
     * [resolveWeeklyAiringSchedules] 的结果：本时刻表的最新条目，以及本周 AniList 名单里的 anilistId。
     * 后者用于把本地表裁剪成"真正在播"的集合（删日历后不再有官方名单）。
     */
    private data class WeeklyResolution(
        val entities: List<AirScheduleEntity>,
        val rosterAnilistIds: Set<Long>,
    )

    /**
     * 一轮周排期解析的**可变状态**：三张索引表 + 三个待落库列表。
     *
     * 拎成独立对象，是为了让主流程与三条绑定策略共享同一份可变状态；否则每个策略都要接六七个参数、
     * 还容易把顺序传错。它私有于本类，生命周期止于一次解析。
     */
    private class WeeklyResolutionState(
        val entitiesByAnilistId: MutableMap<Long, AirScheduleEntity>,
        val entitiesByBgmId: MutableMap<Long, AirScheduleEntity>,
        val mappingCache: MutableMap<Long, AniListBgmMappingEntity>,
        val newlyInserted: MutableList<AirScheduleEntity> = mutableListOf(),
        val mappingsToPersist: MutableList<AniListBgmMappingEntity> = mutableListOf(),
    )

    /**
     * 单条 AniList 名单条目 + 它在快照里的对应信息（快照里可能没有这一条）。
     *
     * 三条绑定策略都要用到这几个字段，所以预先算好一遍，避免在每个分支里重复取。
     */
    private class WeeklyItemContext(
        val item: AniListWeeklyScheduleItem,
        val sItem: ScheduleSnapshotItemDto?,
        val sitesJson: String,
        val coverUrl: String,
        val titleCn: String,
        val airDate: String,
    )

    /** 组装 [WeeklyItemContext]；播放源序列化与首播日兜底都收在这里 */
    private fun weeklyItemContextOf(
        item: AniListWeeklyScheduleItem,
        sItem: ScheduleSnapshotItemDto?,
    ): WeeklyItemContext {
        val sites = sItem?.sites?.mapNotNull { resolveSiteLink(it.site, it.id, it.url) }.orEmpty()
        return WeeklyItemContext(
            item = item,
            sItem = sItem,
            sitesJson = if (sites.isNotEmpty()) json.encodeToString(sites) else "[]",
            coverUrl = item.coverUrl.orEmpty(),
            titleCn = sItem?.titleCn.orEmpty(),
            airDate = snapshotAirDateOf(sItem),
        )
    }

    /**
     * 策略 A：该 anilistId 已绑定到正式条目（`bgmId > 0`），只做元数据补全、不重新绑定。
     *
     * 无论是否有字段需要更新，都要补一条本话事件——事件是排期的第一手事实，与元数据是否变化无关。
     */
    private fun refreshAlreadyBoundEntity(
        ctx: WeeklyItemContext,
        existing: AirScheduleEntity,
        state: WeeklyResolutionState,
        nowMillis: Long,
    ) {
        val mergedSites = mergeSites(existing.sitesJson, ctx.sitesJson)
        val targetTitleCn = if (ctx.titleCn.isNotBlank()) ctx.titleCn else existing.titleCn
        val targetCoverUrl = if (existing.coverUrl.isBlank() && ctx.coverUrl.isNotBlank()) ctx.coverUrl else existing.coverUrl
        val targetAirDate =
            if (existing.airDate.isBlank() && ctx.airDate.isNotBlank()) {
                ctx.airDate.substringBefore("T")
            } else {
                existing.airDate
            }
        val targetBroadcastRule = buildBroadcastRule(ctx.item.isAdult, ctx.item.format)
        val needsUpdate =
            targetCoverUrl != existing.coverUrl ||
                targetTitleCn != existing.titleCn ||
                targetAirDate != existing.airDate ||
                mergedSites != existing.sitesJson ||
                (targetBroadcastRule.isNotBlank() && targetBroadcastRule != existing.broadcastRule)

        if (needsUpdate) {
            val updated =
                existing.copy(
                    coverUrl = targetCoverUrl,
                    titleCn = targetTitleCn,
                    airDate = targetAirDate,
                    sitesJson = mergedSites,
                    broadcastRule = if (targetBroadcastRule.isNotBlank()) targetBroadcastRule else existing.broadcastRule,
                )
            state.entitiesByAnilistId[ctx.item.anilistId] = updated
            state.entitiesByBgmId[updated.bgmId] = updated
            state.newlyInserted += updated
        }
    }

    /**
     * 策略 B：按快照自带 `bgmId`（CI 侧桥接/搜索已解析，零额外请求）或本地历史映射缓存绑定。
     *
     * @return true 表示该条目已被处理，调用方应 `continue`
     */
    private fun bindBySnapshotMapping(
        ctx: WeeklyItemContext,
        state: WeeklyResolutionState,
        nowMillis: Long,
    ): Boolean {
        val snapshotMapping =
            ctx.sItem?.bgmId?.let { bgmId ->
                AniListBgmMappingEntity(
                    anilistId = ctx.item.anilistId,
                    bgmId = bgmId,
                    sitesJson = ctx.sitesJson,
                    title = ctx.item.titleNative,
                    titleCn = ctx.titleCn,
                    beginIso = ctx.airDate,
                    endIso = "",
                    monthKey = "",
                    updatedAt = nowMillis,
                )
            }
        val mapping = snapshotMapping ?: state.mappingCache[ctx.item.anilistId] ?: return false
        state.mappingCache[ctx.item.anilistId] = mapping
        state.mappingsToPersist += mapping
        val existing = state.entitiesByBgmId[mapping.bgmId]
        val targetTitleCn = if (ctx.titleCn.isNotBlank()) ctx.titleCn else existing?.titleCn?.ifBlank { mapping.titleCn }.orEmpty()
        val targetSites = mergeSites(existing?.sitesJson.orEmpty(), ctx.sitesJson.ifBlank { mapping.sitesJson })
        val targetBroadcastRule = buildBroadcastRule(ctx.item.isAdult, ctx.item.format)
        val entity =
            existing?.copy(
                anilistId = ctx.item.anilistId,
                coverUrl = existing.coverUrl.ifBlank { ctx.item.coverUrl.orEmpty() },
                titleCn = targetTitleCn,
                airDate = existing.airDate.ifBlank { mapping.beginIso.substringBefore("T") },
                sitesJson = targetSites,
                broadcastRule = if (targetBroadcastRule.isNotBlank()) targetBroadcastRule else existing.broadcastRule,
            ) ?: mapping.copy(titleCn = targetTitleCn, sitesJson = targetSites).toAirScheduleEntity(ctx.item, nowMillis)
        state.entitiesByBgmId[entity.bgmId] = entity
        state.entitiesByAnilistId[ctx.item.anilistId] = entity
        state.newlyInserted += entity
        return true
    }

    /**
     * 策略 C：本地标题精确匹配绑定。只考虑尚未绑定 `anilistId` 的条目，避免抢走别人的条目。
     *
     * @return true 表示该条目已被处理，调用方应 `continue`
     */
    private fun bindByLocalTitleMatch(
        ctx: WeeklyItemContext,
        currentEntities: List<AirScheduleEntity>,
        state: WeeklyResolutionState,
        nowMillis: Long,
    ): Boolean {
        val localMatch =
            currentEntities.firstOrNull { entity ->
                entity.bgmId > 0 &&
                    entity.anilistId == null &&
                    (titlesRoughlyEqual(entity.title, ctx.item.titleNative) || titlesRoughlyEqual(entity.titleCn, ctx.item.titleNative))
            } ?: return false
        val targetTitleCn = if (ctx.titleCn.isNotBlank()) ctx.titleCn else localMatch.titleCn
        val targetBroadcastRule = buildBroadcastRule(ctx.item.isAdult, ctx.item.format)
        val updated =
            localMatch.copy(
                anilistId = ctx.item.anilistId,
                coverUrl = localMatch.coverUrl.ifBlank { ctx.item.coverUrl.orEmpty() },
                titleCn = targetTitleCn,
                airDate = localMatch.airDate.ifBlank { ctx.airDate.substringBefore("T") },
                sitesJson = mergeSites(localMatch.sitesJson, ctx.sitesJson),
                broadcastRule = if (targetBroadcastRule.isNotBlank()) targetBroadcastRule else localMatch.broadcastRule,
            )
        state.entitiesByBgmId[updated.bgmId] = updated
        state.entitiesByAnilistId[ctx.item.anilistId] = updated
        state.newlyInserted += updated
        state.mappingsToPersist += updated.toAniListBgmMapping(ctx.item.anilistId, nowMillis)
        return true
    }

    /** 把一轮解析攒下的三类变更落库；空列表不触发写库，避免无谓事务 */
    private suspend fun persistWeeklyResolution(state: WeeklyResolutionState) {
        if (state.mappingsToPersist.isNotEmpty()) {
            runCatching { anilistMappingDao.upsertMappings(state.mappingsToPersist.distinctBy { it.anilistId }) }
        }
        if (state.newlyInserted.isNotEmpty()) {
            scheduleDao.insertSchedules(state.newlyInserted)
        }
    }

    private suspend fun loadMappingCache(anilistIds: List<Long>): MutableMap<Long, AniListBgmMappingEntity> =
        runCatching { anilistMappingDao.getMappingsByAniListIds(anilistIds.distinct()) }
            .getOrElse { emptyList() }
            .associateByTo(mutableMapOf<Long, AniListBgmMappingEntity>()) { it.anilistId }

    // titlesRoughlyEqual / canonicalTitleKey / chineseNumberToArabic 已提升为同包顶层，见 ScheduleTitleMatcher.kt；
    // mergeSites / snapshotAirDateOf / resolveSiteLink 与实体↔模型映射扩展见 ScheduleMappers.kt。

    private fun reconcileScheduleEntities(
        entities: List<AirScheduleEntity>,
        allEvents: Map<Long, List<AirEventEntity>>,
        nowMillis: Long,
    ): List<AirScheduleEntity> {
        val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(nowMillis)
        val weekEndMillis = TimeUtils.cstWeekEndEpochMillis(nowMillis)

        return entities.map { entity ->
            val events =
                allEvents[entity.bgmId]
                    .orEmpty()
                    .mapNotNull { event ->
                        val millis = TimeUtils.epochMillisOfIso(event.airAtUtc) ?: return@mapNotNull null
                        event to millis
                    }
            if (events.isEmpty()) {
                return@map entity.copy(
                    nextEpisode = 0,
                    nextEpisodeAtUtc = "",
                    nextEpisodeKind = "",
                )
            }

            // 本周排期优先：在周一至周日的每周日历视图中，卡片话数应代表本周对应星期几播出的集数。
            // 避免因当天某一话播出后（如周三凌晨 00:35 播完），就过早切换到下周话数（第 12 话），
            // 导致周三白天时间线呈现出“00:35 已播 · 第 12 话”的集数倒挂错位。
            val thisWeekEvents = events.filter { it.second in weekStartMillis..weekEndMillis }
            val selected =
                if (thisWeekEvents.isNotEmpty()) {
                    // 本周内优先取尚未播出的最早一话；若本周都已播出，取本周已播出的最新一话
                    thisWeekEvents.filter { it.second > nowMillis }.minByOrNull { it.second }
                        ?: thisWeekEvents.maxByOrNull { it.second }
                } else {
                    // 本周无播出（如停播周、下周首播或已完结）：优先取未来最近一话，否则取历史最后一话
                    events.filter { it.second > nowMillis }.minByOrNull { it.second }
                        ?: events.maxByOrNull { it.second }
                }

            if (selected != null) {
                val airUtc = selected.first.airAtUtc
                val cstTime = TimeUtils.formatToCstTime(airUtc)
                val jstTime = TimeUtils.formatToJstTime(airUtc)
                val kind = if (selected.second <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED
                entity.copy(
                    weekday = TimeUtils.cstWeekdayOfEpoch(selected.second),
                    timeCst = cstTime,
                    timeJst = jstTime,
                    sortMinutes = TimeUtils.parseTimeToMinutes(cstTime),
                    nextEpisode = selected.first.episode,
                    nextEpisodeAtUtc = airUtc,
                    nextEpisodeKind = kind,
                )
            } else {
                entity.copy(
                    nextEpisode = 0,
                    nextEpisodeAtUtc = "",
                    nextEpisodeKind = "",
                )
            }
        }
    }

    override suspend fun getScheduleDefaultOnlyWatching(): Boolean =
        userPreferences.userPreferences.firstOrNull()?.scheduleDefaultOnlyWatching ?: false

    override suspend fun setScheduleDefaultOnlyWatching(onlyWatching: Boolean) {
        userPreferences.setScheduleDefaultOnlyWatching(onlyWatching)
    }

    private fun AirScheduleEntity.isActiveForSchedule(nowMillis: Long): Boolean {
        val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(nowMillis)
        val nextMillis = TimeUtils.epochMillisOfIso(nextEpisodeAtUtc)

        // 1. 若条目有排期且在当前周或未来：必然处于活跃状态
        if (nextMillis != null && nextMillis >= weekStartMillis) {
            return true
        }

        val beginMillis =
            TimeUtils.epochMillisOfIso(beginUtc)
                ?: TimeUtils.epochMillisOfIso(airDate)

        // 2. 短篇/特别篇（总集数 1..3 话）：播出周期极短，若开播日 + 总集数 * 7天 已经早于当前周，判定为已完结
        if (isFinishedShortSeries(weekStartMillis, beginMillis)) {
            return false
        }

        // 3. 已播完最终话：若当前已播集数已达总集数，且最后一集在过去周已播完，判定为已完结
        if (isFinalEpisodeAlreadyAired(weekStartMillis, nextMillis)) {
            return false
        }

        // 4. 若条目最近一集已播完（ACTUAL），且已超过两周没有任何新集数排期：
        if (isStaleWithoutFutureSchedule(weekStartMillis, nextMillis, beginMillis)) {
            return false
        }

        // 5. totalEpisodes <= 0 或暂无排期事件的条目：
        if (totalEpisodes <= 0) {
            if (source == AirScheduleEntity.SOURCE_OFFICIAL) {
                return true // 官方日历长篇连载（如柯南、海贼王）安全保留
            }
            // bgm_data 无话数条目（跨季长档/网播分段番）：收录窗口（370 天）内默认显示，
            // 否则这类番开播 14 天后就会从时刻表消失；但已知"下一话"时刻停在 14 天前
            // 仍未前进（同步管线多轮未喂进新事件）时视为停更隐藏
            if (beginMillis == null || beginMillis < nowMillis - ROSTER_LOOKBACK_DAYS * DAY_MILLIS) {
                return false
            }
            return nextMillis == null || nextMillis >= nowMillis - 14 * DAY_MILLIS
        }

        // 6. 普通在播季度番，默认在总播映生命周期内保持活跃
        return beginMillis == null || beginMillis + totalEpisodes * WEEK_MILLIS >= weekStartMillis
    }

    private fun AirScheduleEntity.isFinishedShortSeries(
        weekStartMillis: Long,
        beginMillis: Long?,
    ): Boolean = totalEpisodes in 1..3 && beginMillis != null && beginMillis + totalEpisodes * WEEK_MILLIS < weekStartMillis

    private fun AirScheduleEntity.isFinalEpisodeAlreadyAired(
        weekStartMillis: Long,
        nextMillis: Long?,
    ): Boolean =
        totalEpisodes > 0 &&
            nextEpisode >= totalEpisodes &&
            nextEpisodeKind == AirEventKind.ACTUAL &&
            nextMillis != null &&
            nextMillis < weekStartMillis

    private fun AirScheduleEntity.isStaleWithoutFutureSchedule(
        weekStartMillis: Long,
        nextMillis: Long?,
        beginMillis: Long?,
    ): Boolean {
        if (nextEpisodeKind != AirEventKind.ACTUAL || nextMillis == null) return false
        if (nextMillis >= weekStartMillis - 14 * DAY_MILLIS) return false
        return !isStillWithinAiringWindow(weekStartMillis, beginMillis)
    }

    private fun AirScheduleEntity.isStillWithinAiringWindow(
        weekStartMillis: Long,
        beginMillis: Long?,
    ): Boolean {
        if (totalEpisodes <= 0 || nextEpisode >= totalEpisodes || beginMillis == null) return false
        return beginMillis + totalEpisodes * WEEK_MILLIS >= weekStartMillis
    }

    private companion object {
        const val ANILIST_SITE = "anilist"
        const val BILIBILI_SITE = "bilibili"
        const val EVENT_SOURCE_ANILIST = "anilist"
        const val EVENT_SOURCE_BGM_DATA = "bgm_data"
        const val ROSTER_LOOKBACK_DAYS = 370L

        // 标题归一化用的 TITLE_NOISE_REGEX / ROMAN_NUMERAL_REGEX / ROMAN_NUMERALS
        // 已随 canonicalTitleKey 移至 ScheduleTitleMatcher.kt；
        // SITE_RESOLVERS / buildBilibiliUrl 已随 resolveSiteLink 移至 ScheduleMappers.kt。

        /** 非强制刷新的节流阈值：12 小时内不重跑全量管线，杜绝后台无谓唤醒与高频网络请求 */
        const val REFRESH_THROTTLE_MILLIS = 12L * 60L * 60L * 1000L
    }
}

// isStalePrunableSchedule 顶层谓词已移至同包 SchedulePruning.kt（语义与命名不变）。
