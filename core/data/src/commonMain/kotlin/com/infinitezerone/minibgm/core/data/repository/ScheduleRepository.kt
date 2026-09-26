package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.BgmImageUtils
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.runCatchingCancellable
import com.infinitezerone.minibgm.core.data.search.SearchAliasIndex
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.BangumiDataItem
import com.infinitezerone.minibgm.core.model.BangumiDataSite
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.network.AniListService
import com.infinitezerone.minibgm.core.network.AniListWeeklyScheduleItem
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiDataMonthResult
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import com.infinitezerone.minibgm.core.network.BilibiliService
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

interface ScheduleRepository {
    fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>>

    /**
     * 全量排期流（单流监听，避免按天拆流导致的重复查询与高频重组）。
     * 全量刷新管线进行期间扣住中间态，仅在管线完成后以最新完整数据对外发流。
     */
    fun getAllSchedulesStream(): Flow<List<AirSchedule>>

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
     * 全量刷新管线（UX_REMEDIATION 诉求：所有数据源获取完再更新 UI 列表）：
     * 拉齐 AniList 周排期（名单发现 + 逐话真值）、bangumi-data 播放源与事件仲裁，
     * 期间对外流被闸门扣住，全部完成后才以最终状态对外发一次。
     * 各数据源失败不互相中断，错误信息聚合返回。
     *
     * [force] = false 时按 [REFRESH_THROTTLE_MILLIS] 节流：距上次成功同步不足阈值直接返回，
     * 页面重建（冷启动/切 Tab）不应重复跑全量管线；下拉刷新等用户显式动作传 true。
     */
    suspend fun refreshAllSchedules(force: Boolean = false): AppResult<Unit>

    /**
     * 后台 / 手动同步：
     * 1) 按条目 begin 月按需拉取 bangumi-data 月切片补全播放源/中文名；
     * 2) AniList 周排期发现新番、补全逐话真值并仲裁回写。
     */
    suspend fun syncBangumiData(force: Boolean = false): AppResult<Unit>

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
}

class ScheduleRepositoryImpl(
    private val apiService: BangumiApiService,
    private val dataService: BangumiDataService,
    private val scheduleDao: AirScheduleDao,
    private val airEventDao: AirEventDao,
    private val anilistMappingDao: AniListMappingDao,
    private val anilistService: AniListService,
    private val bilibiliService: BilibiliService,
    private val userPreferences: UserPreferencesDataSource,
    private val collectionRepository: CollectionRepository? = null,
    private val json: Json = BgmHttpClient.jsonConfig,
) : ScheduleRepository {
    override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> =
        scheduleDao.getSchedulesByWeekday(weekday).map { entities ->
            val nowMillis = TimeUtils.nowEpochMillis()
            entities
                .filter { it.isActiveForSchedule(nowMillis) }
                .map { it.toModel(json) }
        }

    /**
     * 同步闸门：全量刷新管线进行中为 true。
     * 闸门激活期间仅扣留管线产生的中间态发射；本地首帧（已有缓存）坚决第一时间直发，
     * 确保冷启动与离线场景 0ms 显示内容，管线放开后以最新状态对外发一次。
     */
    private val scheduleHoldGate = MutableStateFlow(false)

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
        scheduleHoldGate.value = true
        try {
            // 唯一数据源：AniList 周排期（名单发现 + 逐话真值）+ 按需 bangumi-data 月切片
            return syncBangumiData()
        } finally {
            scheduleHoldGate.value = false
        }
    }

    override suspend fun syncBangumiData(force: Boolean): AppResult<Unit> =
        try {
            val existingEntities = scheduleDao.getAllSchedulesList()

            // bangumi-data 不再作为名单来源（也不再有固定窗口）：名单由官方日历 + AniList 周排期负责，
            // 这里只为缺播放源/中文名的条目，按其 begin 月按需补全（ETag 条件请求，未变即 0 字节）。
            val enriched = enrichFromBangumiDataMonths(existingEntities)
            if (enriched != existingEntities) {
                scheduleDao.insertSchedules(enriched)
            }
            userPreferences.setBangumiDataLastSyncTimestamp(TimeUtils.nowEpochMillis())

            // 逐话事件同步与仲裁（全量管线中唯一一次）：名单发现 + 排期回写 + 元数据回补都在这里收口
            runCatchingCancellable { syncAirEvents() }

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
                    entities.map { entity ->
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
     * 为缺播放源的条目按其 `begin` 月（±1 月容忍 begin 的时区/跨月错位）按需拉取 bangumi-data 月切片，
     * 补全 sites / 中文名 / anilistId。每月 ETag 持久化，未变时条件请求命中 304、零解析。
     */
    private suspend fun enrichFromBangumiDataMonths(entities: List<AirScheduleEntity>): List<AirScheduleEntity> {
        val needs = entities.filter { it.sitesJson.isBlank() || it.sitesJson == "[]" }
        if (needs.isEmpty()) return entities

        val nowMillis = TimeUtils.nowEpochMillis()
        val months =
            needs
                .mapNotNull { monthKeyOfAirDate(it.airDate) }
                .flatMap { key ->
                    val year = key.substring(0, 4).toIntOrNull() ?: return@flatMap emptyList()
                    val month = key.substring(5, 7).toIntOrNull() ?: return@flatMap emptyList()
                    listOf(shiftMonth(year, month, -1), year to month, shiftMonth(year, month, 1))
                }.distinct()

        val itemsByBgmId = mutableMapOf<Long, BangumiDataItem>()
        for ((year, month) in months) {
            val key = monthKey(year, month)
            val etag = runCatching { anilistMappingDao.getMonthEtag(key) }.getOrNull()?.etag
            val result =
                runCatchingCancellable { dataService.getMonthItems(year, month, etag) }.getOrNull()
                    ?: continue
            if (result is BangumiDataMonthResult.Success) {
                result.etag?.takeIf { it.isNotBlank() }?.let {
                    runCatching { anilistMappingDao.upsertMonthEtag(BangumiDataMonthEtagEntity(key, it, nowMillis)) }
                }
                result.items.forEach { item -> item.bgmSubjectId?.let { itemsByBgmId[it] = item } }
            }
        }
        if (itemsByBgmId.isEmpty()) return entities

        return entities.map { entity ->
            val item = itemsByBgmId[entity.bgmId] ?: return@map entity
            entity.copy(
                titleCn = entity.titleCn.ifBlank { item.chineseTitle },
                sitesJson = mergeSites(entity.sitesJson, json.encodeToString(item.sites.mapNotNull { resolveSiteLink(it) })),
                anilistId =
                    entity.anilistId
                        ?: item.sites
                            .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                            ?.id
                            ?.toLongOrNull(),
            )
        }
    }

    /** 从 `airDate`（`YYYY-MM-DD`）解析出 `YYYY-MM`；非法/缺失返回 null。 */
    private fun monthKeyOfAirDate(airDate: String): String? {
        val date = airDate.substringBefore("T")
        if (date.length < 7) return null
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        val month = date.substring(5, 7).toIntOrNull() ?: return null
        if (year <= 0 || month !in 1..12) return null
        return monthKey(year, month)
    }

    /**
     * 同步全量条目的播出事件并仲裁时刻表：
     * 1) 从 AniList 获取逐话真值与高清封面（日番主流，精确到秒级）；
     * 2) AniList 未覆盖但存在 B站源的条目由 BilibiliService 获取逐话真值（国创/B站独播）；
     * 3) 仲裁回写条目的 next* 字段与 weekday，自动回补 AniList 高清封面，并剔除已完结僵尸条目。
     */
    private suspend fun syncAirEvents() {
        val baseEntities = scheduleDao.getAllSchedulesList()
        val nowMillis = TimeUtils.nowEpochMillis()
        val entities = resolveWeeklyAiringSchedules(baseEntities, nowMillis)
        if (entities.isEmpty()) return

        // 事件与名单裁剪：清理已消失条目的事件与过期网播名单
        val keepIds = entities.map { it.bgmId }.toSet()
        airEventDao.deleteEventsNotIn(keepIds.toList())
        // 清除所有历史遗留预测事件（彻底废弃 PREDICTED 假数据）
        airEventDao.deleteAllPredictedEvents()

        val cutoffDate = TimeUtils.formatEpochSecondsToDate((nowMillis - ROSTER_LOOKBACK_DAYS * DAY_MILLIS) / 1000)
        scheduleDao.deleteStaleBgmDataSchedules(cutoffDate)

        // 用户在看收藏过滤：有在看数据时仅对在看条目与缺少封面的条目发起精准排期校验
        val trackingSubjectIds =
            collectionRepository
                ?.getCollectionsByTypeStream(CollectionType.DOING)
                ?.firstOrNull()
                ?.map { it.subjectId }
                ?.toSet()

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

        // 1. AniList 逐话真值与高清封面同步
        val (anilistEvents, coveredSubjects, anilistCovers) = fetchAnilistAirEvents(targets, nowMillis)
        if (anilistEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(anilistEvents)
        }

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

        // 剔除已完结或在未来无任何播出事件的 bgm_data 僵尸条目
        val (activeEntities, zombieEntities) =
            entitiesWithCovers.partition { entity ->
                !isZombieBgmDataSchedule(entity, allEvents[entity.bgmId].orEmpty(), nowMillis)
            }
        if (zombieEntities.isNotEmpty()) {
            scheduleDao.deleteBgmDataSchedulesByIds(zombieEntities.map { it.bgmId })
        }

        val reconciled = reconcileScheduleEntities(activeEntities, allEvents, nowMillis)
        scheduleDao.insertSchedules(enrichMissingMetadata(reconciled))
    }

    private suspend fun fetchAnilistAirEvents(
        entities: List<AirScheduleEntity>,
        nowMillis: Long,
    ): Triple<List<AirEventEntity>, Set<Long>, Map<Long, String>> {
        val withAnilistId = entities.filter { it.anilistId != null }
        val schedulesByAnilistId =
            runCatching {
                anilistService.getMediaSchedules(
                    anilistIds = withAnilistId.mapNotNull { it.anilistId },
                )
            }.getOrElse { emptyMap() }
        val anilistEvents = mutableListOf<AirEventEntity>()
        val coveredSubjects = mutableSetOf<Long>()
        val coversBySubjectId = mutableMapOf<Long, String>()

        for (entity in withAnilistId) {
            val anilistId = entity.anilistId ?: continue
            val mediaSchedule = schedulesByAnilistId[anilistId] ?: continue
            val coverUrl = mediaSchedule.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                coversBySubjectId[entity.bgmId] = BgmImageUtils.toSecureUrl(coverUrl)
            }
            val episodes = mediaSchedule.episodes
            if (episodes.isEmpty()) continue

            // 拆季偏移推导：
            // 当季绝大多数条目在 AniList 为独立条目（从第 1 话开始），默认 offset = 0；
            // 若 AniList 话数从 >1 开始，且首个样本就在 Bangumi 开播日附近（±14天），
            // 则表明 AniList 顺延了上半季话数，其实际正对应本季第 1 话（如 AniList 13 话对齐 Bangumi 下半季第 1 话）。
            // 绝不因首播日跨度大而把整部番剧跳过，播出时刻始终从真实单集时间戳直接解析。
            val anchorMillis =
                TimeUtils.epochMillisOfIso(entity.airDate)
                    ?: TimeUtils.epochMillisOfIso(entity.beginUtc)

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
                        val offsetWeeks = ((anchorMillis - estimatedEp1Millis) / WEEK_MILLIS).toInt()
                        if (offsetWeeks in 1..<anchorEp.episode) offsetWeeks else 0
                    }
                } else {
                    0
                }

            for (episode in episodes) {
                val bgmEpisode = episode.episode - offset
                if (bgmEpisode < 1) continue
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
     * 用 AniList 当周排期把全网在播条目补齐/绑定到本地时刻表。
     *
     * anilistId → bgmId 三级降级，命中即回写 [AniListBgmMappingEntity]：
     * 1) 本地已有但尚未绑定 anilistId 的条目，按归一化标题精确匹配（不做包含匹配，避免跨季误配）；
     * 2) 映射缓存；未命中则按条目 startDate（±1 月）按需拉取 bangumi-data 月切片，用 sites 桥解析；
     * 3) 仍无结果时用 bgm.tv 官方搜索按日文原名兜底，且只在候选唯一时接受（绝不取首条）。
     */
    private suspend fun resolveWeeklyAiringSchedules(
        currentEntities: List<AirScheduleEntity>,
        nowMillis: Long,
    ): List<AirScheduleEntity> {
        val weekStartSeconds = TimeUtils.cstWeekStartEpochMillis(nowMillis) / 1000
        val weekEndSeconds = TimeUtils.cstWeekEndEpochMillis(nowMillis) / 1000
        val weeklyItems =
            runCatchingCancellable {
                anilistService.getWeeklyAiringSchedule(weekStartSeconds, weekEndSeconds)
            }.getOrElse { emptyList() }

        if (weeklyItems.isEmpty()) return currentEntities

        val entitiesByAnilistId =
            currentEntities
                .filter { it.anilistId != null }
                .associateBy { it.anilistId!! }
                .toMutableMap()
        val entitiesByBgmId = currentEntities.associateBy { it.bgmId }.toMutableMap()
        val newlyInserted = mutableListOf<AirScheduleEntity>()
        val newEvents = mutableListOf<AirEventEntity>()
        val mappingsToPersist = mutableListOf<AniListBgmMappingEntity>()
        val fetchedMonths = mutableSetOf<String>()
        val mappingCache = loadMappingCache(weeklyItems.map { it.anilistId })

        for (item in weeklyItems) {
            if (entitiesByAnilistId[item.anilistId] != null) continue

            // 1) 本地标题精确匹配：只考虑尚未绑定 anilistId 的条目
            val localMatch =
                currentEntities.firstOrNull { entity ->
                    entity.anilistId == null && titlesRoughlyEqual(entity.title, item.titleNative)
                }
            if (localMatch != null) {
                val updated = localMatch.copy(anilistId = item.anilistId)
                entitiesByBgmId[updated.bgmId] = updated
                entitiesByAnilistId[item.anilistId] = updated
                newlyInserted += updated
                mappingsToPersist += updated.toAniListBgmMapping(item.anilistId, nowMillis)
                newEvents += item.toAirEventEntity(updated.bgmId, nowMillis)
                continue
            }

            // 2) 映射缓存 / bangumi-data 月切片
            val mapping =
                mappingCache[item.anilistId]
                    ?: resolveMappingFromMonth(item, nowMillis, fetchedMonths, mappingCache)
            if (mapping != null) {
                mappingCache[item.anilistId] = mapping
                mappingsToPersist += mapping
                val existing = entitiesByBgmId[mapping.bgmId]
                val entity =
                    existing?.copy(
                        anilistId = item.anilistId,
                        sitesJson = mergeSites(existing.sitesJson, mapping.sitesJson),
                    ) ?: mapping.toAirScheduleEntity(item, nowMillis)
                entitiesByBgmId[entity.bgmId] = entity
                entitiesByAnilistId[item.anilistId] = entity
                newlyInserted += entity
                newEvents += item.toAirEventEntity(entity.bgmId, nowMillis)
                continue
            }

            // 3) bgm.tv 实时搜索兜底：唯一候选才接受
            val subject = searchUniqueSubject(item) ?: continue
            val searched = subject.toAniListBgmMapping(item.anilistId, nowMillis)
            mappingCache[item.anilistId] = searched
            mappingsToPersist += searched
            val existing = entitiesByBgmId[searched.bgmId]
            val entity =
                existing?.copy(
                    anilistId = item.anilistId,
                    sitesJson = mergeSites(existing.sitesJson, searched.sitesJson),
                ) ?: searched.toAirScheduleEntity(item, nowMillis)
            entitiesByBgmId[entity.bgmId] = entity
            entitiesByAnilistId[item.anilistId] = entity
            newlyInserted += entity
            newEvents += item.toAirEventEntity(entity.bgmId, nowMillis)
        }

        if (mappingsToPersist.isNotEmpty()) {
            runCatching { anilistMappingDao.upsertMappings(mappingsToPersist.distinctBy { it.anilistId }) }
        }
        if (newlyInserted.isNotEmpty()) {
            scheduleDao.insertSchedules(newlyInserted)
        }
        if (newEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(newEvents)
        }
        return entitiesByBgmId.values.toList()
    }

    private suspend fun loadMappingCache(anilistIds: List<Long>): MutableMap<Long, AniListBgmMappingEntity> =
        runCatching { anilistMappingDao.getMappingsByAniListIds(anilistIds.distinct()) }
            .getOrElse { emptyList() }
            .associateByTo(mutableMapOf<Long, AniListBgmMappingEntity>()) { it.anilistId }

    /**
     * 按条目 startDate（含前后各一个月，容忍 `begin` 的时区/跨月错位）按需拉取 bangumi-data 月切片，
     * 用 `sites` 里的 anilist↔bangumi 桥解析映射。同月只拉一次，拉到的映射回填内存缓存供后续条目复用。
     */
    private suspend fun resolveMappingFromMonth(
        item: AniListWeeklyScheduleItem,
        nowMillis: Long,
        fetchedMonths: MutableSet<String>,
        mappingCache: MutableMap<Long, AniListBgmMappingEntity>,
    ): AniListBgmMappingEntity? {
        val year = item.startYear
        val month = item.startMonth
        if (year <= 0 || month !in 1..12) return null

        for (offset in intArrayOf(-1, 0, 1)) {
            val (targetYear, targetMonth) = shiftMonth(year, month, offset)
            val key = monthKey(targetYear, targetMonth)
            if (fetchedMonths.add(key)) {
                val etag = runCatching { anilistMappingDao.getMonthEtag(key) }.getOrNull()?.etag
                when (
                    val result =
                        runCatchingCancellable {
                            dataService.getMonthItems(targetYear, targetMonth, etag)
                        }.getOrNull()
                ) {
                    is BangumiDataMonthResult.Success -> {
                        result.etag?.takeIf { it.isNotBlank() }?.let {
                            runCatching { anilistMappingDao.upsertMonthEtag(BangumiDataMonthEtagEntity(key, it, nowMillis)) }
                        }
                        val mappings = result.items.mapNotNull { it.toAniListBgmMapping(key, nowMillis) }
                        if (mappings.isNotEmpty()) {
                            runCatching { anilistMappingDao.upsertMappings(mappings) }
                            mappings.forEach { mappingCache[it.anilistId] = it }
                        }
                    }
                    BangumiDataMonthResult.NotModified -> Unit
                    BangumiDataMonthResult.NotFound, null -> Unit
                }
            }
            mappingCache[item.anilistId]?.let { return it }
        }
        return null
    }

    /** bgm.tv 官方搜索兜底：返回空、或存在多个候选时一律不绑定，宁可缺失也不写错映射。 */
    private suspend fun searchUniqueSubject(item: AniListWeeklyScheduleItem): Subject? {
        val query = item.titleNative.ifBlank { item.titleRomaji }
        if (query.isBlank()) return null
        val list =
            runCatchingCancellable { apiService.searchSubjects(query, type = 2) }
                .getOrNull()
                ?.list
                .orEmpty()
        if (list.isEmpty()) return null
        if (list.size == 1) return list.first()
        val normalized = normalizeTitle(query)
        return list
            .filter { normalizeTitle(it.name) == normalized || normalizeTitle(it.nameCn) == normalized }
            .singleOrNull()
    }

    private fun shiftMonth(
        year: Int,
        month: Int,
        offset: Int,
    ): Pair<Int, Int> {
        var y = year
        var m = month + offset
        while (m < 1) {
            m += 12
            y -= 1
        }
        while (m > 12) {
            m -= 12
            y += 1
        }
        return y to m
    }

    private fun monthKey(
        year: Int,
        month: Int,
    ): String = "$year-${month.toString().padStart(2, '0')}"

    private fun titlesRoughlyEqual(
        a: String,
        b: String,
    ): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return normalizeTitle(a) == normalizeTitle(b)
    }

    /** 归一化片名：大小写 + 全/半角连接符、中点、空白一律抹平，仅用于"精确"比较 */
    private fun normalizeTitle(value: String): String = value.lowercase().replace(TITLE_NOISE_REGEX, "")

    private fun mergeSites(
        existing: String,
        incoming: String,
    ): String = if (existing.isBlank() || existing == "[]") incoming else existing

    private fun AniListWeeklyScheduleItem.toAirEventEntity(
        subjectId: Long,
        nowMillis: Long,
    ): AirEventEntity {
        val airMillis = airAtEpochSeconds * 1000
        return AirEventEntity(
            subjectId = subjectId,
            episode = episode,
            airAtUtc = TimeUtils.isoUtcFromEpochMillis(airMillis),
            kind = if (airMillis <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED,
            source = EVENT_SOURCE_ANILIST,
        )
    }

    private fun AniListBgmMappingEntity.toAirScheduleEntity(
        item: AniListWeeklyScheduleItem,
        nowMillis: Long,
    ): AirScheduleEntity {
        val airMillis = item.airAtEpochSeconds * 1000
        val isoUtc = TimeUtils.isoUtcFromEpochMillis(airMillis)
        val cstTime = TimeUtils.formatToCstTime(isoUtc)
        val jstTime = TimeUtils.formatToJstTime(isoUtc)
        val kind = if (airMillis <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED
        return AirScheduleEntity(
            bgmId = bgmId,
            title = title,
            titleCn = titleCn.ifBlank { title },
            coverUrl = item.coverUrl.orEmpty(),
            ratingScore = 0.0,
            airDate = beginIso.substringBefore("T"),
            beginAtUtc = beginIso.ifBlank { isoUtc },
            sortMinutes = TimeUtils.parseTimeToMinutes(cstTime),
            weekday = TimeUtils.cstWeekdayOfEpoch(airMillis),
            timeCst = cstTime,
            timeJst = jstTime,
            sitesJson = sitesJson,
            anilistId = anilistId,
            source = AirScheduleEntity.SOURCE_BGM_DATA,
            nextEpisode = item.episode,
            nextEpisodeAtUtc = isoUtc,
            nextEpisodeKind = kind,
        )
    }

    private fun AirScheduleEntity.toAniListBgmMapping(
        anilistId: Long,
        nowMillis: Long,
    ): AniListBgmMappingEntity =
        AniListBgmMappingEntity(
            anilistId = anilistId,
            bgmId = bgmId,
            sitesJson = sitesJson,
            title = title,
            titleCn = titleCn,
            beginIso = beginUtc,
            endIso = "",
            monthKey = "",
            updatedAt = nowMillis,
        )

    private fun Subject.toAniListBgmMapping(
        anilistId: Long,
        nowMillis: Long,
    ): AniListBgmMappingEntity =
        AniListBgmMappingEntity(
            anilistId = anilistId,
            bgmId = id,
            sitesJson = "[]",
            title = name,
            titleCn = nameCn.ifBlank { name },
            beginIso = date.ifBlank { airDate },
            endIso = "",
            monthKey = "",
            updatedAt = nowMillis,
        )

    private fun BangumiDataItem.toAniListBgmMapping(
        monthKey: String,
        nowMillis: Long,
    ): AniListBgmMappingEntity? {
        val bgmId = bgmSubjectId ?: return null
        val anilistId =
            sites
                .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                ?.id
                ?.toLongOrNull() ?: return null
        return AniListBgmMappingEntity(
            anilistId = anilistId,
            bgmId = bgmId,
            sitesJson = json.encodeToString(sites.mapNotNull { resolveSiteLink(it) }),
            title = title,
            titleCn = chineseTitle,
            beginIso = begin,
            endIso = end,
            monthKey = monthKey,
            updatedAt = nowMillis,
        )
    }

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

    private fun resolveSiteLink(site: BangumiDataSite): SiteLink? {
        val siteKey = site.site.lowercase()
        val resolver = SITE_RESOLVERS[siteKey] ?: return null
        val url = site.url.ifBlank { resolver.second(site.id) }
        if (url.isBlank()) return null
        return SiteLink(
            siteName = siteKey,
            displayName = resolver.first,
            playUrl = url,
        )
    }

    private fun AirScheduleEntity.toModel(json: Json): AirSchedule {
        val links: List<SiteLink> =
            try {
                json.decodeFromString(sitesJson)
            } catch (_: Exception) {
                emptyList()
            }

        val calculatedEp = nextEpisode.takeIf { it > 0 } ?: 0

        return AirSchedule(
            bgmId = bgmId,
            title = title,
            titleCn = titleCn,
            coverUrl = BgmImageUtils.optimizeBgmImageUrl(coverUrl),
            ratingScore = ratingScore,
            airDate = airDate,
            beginAtUtc = beginAtUtc,
            beginUtc = beginUtc,
            weekday = weekday,
            timeCst = timeCst,
            timeJst = timeJst,
            siteLinks = links,
            nextEpisodeNumber = calculatedEp,
            nextEpisodeAtUtc = nextEpisodeAtUtc,
            nextEpisodeKind = nextEpisodeKind,
        )
    }

    /**
     * 为合并入库或缺少元数据的条目（如 bgm-data 网播番）回补官方高清封面、真实评分与集数。
     * 优先对 coverUrl 为空（避免被缺集数的官方条目饿死）及尚未补齐集数的条目平滑顺序调用官方接口（单批上限 5 条，避免突发流量触发限流）；获取后落库持久化，后续刷新直接复用。
     */
    private suspend fun enrichMissingMetadata(schedules: List<AirScheduleEntity>): List<AirScheduleEntity> {
        val missing =
            schedules
                .filter { it.coverUrl.isBlank() || it.totalEpisodes == 0 }
                .sortedWith(compareBy({ !it.coverUrl.isBlank() }, { it.totalEpisodes != 0 }))
                .take(5)
        if (missing.isEmpty()) return schedules

        val metadataByBgmId =
            missing
                .mapNotNull { entity ->
                    runCatching { apiService.getSubject(entity.bgmId) }.getOrNull()
                }.associateBy { it.id }

        if (metadataByBgmId.isEmpty()) return schedules

        return schedules.map { entity ->
            val subject = metadataByBgmId[entity.bgmId] ?: return@map entity
            val coverUrl = BgmImageUtils.toSecureUrl(subject.images?.bestImage.orEmpty())
            val rating = subject.rating?.score ?: 0.0
            val eps = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes
            val resolvedEpisodes = if (eps > 0) eps else -1
            entity.copy(
                coverUrl = coverUrl.ifBlank { entity.coverUrl },
                ratingScore = if (entity.ratingScore == 0.0) rating else entity.ratingScore,
                totalEpisodes = if (entity.totalEpisodes == 0) resolvedEpisodes else entity.totalEpisodes,
                titleCn = entity.titleCn.ifBlank { subject.nameCn },
            )
        }
    }

    private fun isZombieBgmDataSchedule(
        entity: AirScheduleEntity,
        events: List<AirEventEntity>,
        nowMillis: Long,
    ): Boolean {
        if (entity.source != AirScheduleEntity.SOURCE_BGM_DATA) return false
        val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(nowMillis)
        if (events.isNotEmpty()) {
            val hasActiveOrFutureEvent =
                events.any { event ->
                    val millis = TimeUtils.epochMillisOfIso(event.airAtUtc) ?: 0L
                    millis >= weekStartMillis
                }
            if (hasActiveOrFutureEvent) return false

            // 没有本周或未来事件：仅当确已播完全部集数或总放送周期已结束时才视为僵尸条目
            if (entity.totalEpisodes == 1) return true
            if (entity.totalEpisodes > 1) {
                if (events.size >= entity.totalEpisodes) return true
                val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc)
                if (beginMillis != null && beginMillis + entity.totalEpisodes * WEEK_MILLIS < weekStartMillis) {
                    return true
                }
                return false
            }
            // totalEpisodes <= 0
            val latestEventMillis = events.maxOfOrNull { TimeUtils.epochMillisOfIso(it.airAtUtc) ?: 0L } ?: 0L
            return latestEventMillis < weekStartMillis - 14 * DAY_MILLIS
        }

        // events 为空
        if (entity.totalEpisodes == 1) {
            val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: return true
            return beginMillis < weekStartMillis
        }
        if (entity.totalEpisodes > 1) {
            val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc)
            if (beginMillis != null && beginMillis + entity.totalEpisodes * WEEK_MILLIS < weekStartMillis) {
                return true
            }
            return false
        }
        val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: return false
        return beginMillis < weekStartMillis - 14 * DAY_MILLIS
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
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val WEEK_MILLIS = 7L * DAY_MILLIS
        const val ANILIST_SITE = "anilist"
        const val BILIBILI_SITE = "bilibili"
        const val EVENT_SOURCE_ANILIST = "anilist"
        const val EVENT_SOURCE_BILIBILI = "bilibili"
        const val EVENT_SOURCE_BGM_DATA = "bgm_data"
        const val ROSTER_LOOKBACK_DAYS = 370L

        /** 片名归一化时抹掉的连接符/中点/空白（含全角变体） */
        private val TITLE_NOISE_REGEX = Regex("[・&＆\\-\\s　·]")

        /** 非强制刷新的节流阈值：冷启动/切 Tab 的页面重建不重跑全量管线 */
        const val REFRESH_THROTTLE_MILLIS = 30L * 60L * 1000L

        private fun buildBilibiliUrl(id: String): String =
            when {
                id.startsWith("http") -> id
                id.startsWith("md") -> "https://www.bilibili.com/bangumi/media/$id"
                id.startsWith("ss") -> "https://www.bilibili.com/bangumi/play/$id"
                id.startsWith("ep") -> "https://www.bilibili.com/bangumi/play/$id"
                else -> "https://www.bilibili.com/bangumi/media/md$id"
            }

        private val SITE_RESOLVERS: Map<String, Pair<String, (String) -> String>> =
            mapOf(
                "bilibili" to ("哔哩哔哩" to ::buildBilibiliUrl),
                "gamer" to ("巴哈姆特" to { "https://ani.gamer.com.tw/animeVideo.php?sn=$it" }),
                "gamer_hk" to ("巴哈姆特" to { "https://ani.gamer.com.tw/animeVideo.php?sn=$it" }),
                "iqiyi" to ("爱奇艺" to { "https://www.iqiyi.com/v_$it.html" }),
                "qq" to ("腾讯视频" to { "https://v.qq.com/x/cover/$it.html" }),
                "youku" to ("优酷" to { "https://v.youku.com/v_show/id_$it.html" }),
                "netflix" to ("Netflix" to { "https://www.netflix.com/title/$it" }),
                "danime" to ("d动画" to { "https://animestore.docomo.ne.jp/animestore/ci_pc?workId=$it" }),
                "abema" to ("ABEMA" to { "https://abema.tv/channels/$it" }),
                "unext" to ("U-NEXT" to { "https://video.unext.jp/title/$it" }),
                "prime" to ("Prime Video" to { "https://www.amazon.co.jp/dp/$it" }),
                "disneyplus" to ("Disney+" to { "https://www.disneyplus.com/series/$it" }),
                "crunchyroll" to ("Crunchyroll" to { "https://www.crunchyroll.com/series/$it" }),
                "muse_tw" to ("木棉花" to { "https://www.youtube.com/playlist?list=$it" }),
                "muse_hk" to ("木棉花" to { "https://www.youtube.com/playlist?list=$it" }),
                "ani_one" to ("羚邦" to { "https://www.youtube.com/playlist?list=$it" }),
                "ani_one_asia" to ("羚邦" to { "https://www.youtube.com/playlist?list=$it" }),
                "nicovideo" to ("NicoNico" to { "https://ch.nicovideo.jp/$it" }),
                "mikan" to ("蜜柑计划" to { "https://mikanani.me/Home/Bangumi/$it" }),
            )
    }
}
