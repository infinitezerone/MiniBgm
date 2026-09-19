package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.BgmImageUtils
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.onError
import com.infinitezerone.minibgm.core.common.runCatchingCancellable
import com.infinitezerone.minibgm.core.data.search.SearchAliasIndex
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
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
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiDataResult
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.BilibiliService
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
     * 前台极速刷新官方日历（50KB）：
     * 100% 不碰 CDN，直接结合本地已有的播放源毫秒级入库，0 额外开销。
     * 仅清理官方名单内的行，bgm-data 合并插入的网播番不受影响。
     */
    suspend fun refreshSchedules(): AppResult<Unit>

    /**
     * 全量刷新管线（UX_REMEDIATION 诉求：所有数据源获取完再更新 UI 列表）：
     * 依次拉齐官方日历（含逐话事件）与 bangumi-data 播放源/网播番/事件仲裁，
     * 期间对外流被闸门扣住，全部完成后才以最终状态对外发一次。
     * 各数据源失败不互相中断，错误信息聚合返回。
     */
    suspend fun refreshAllSchedules(): AppResult<Unit>

    /**
     * 后台 / 手动同步：
     * 1) CDN 播放源静态数据（ETag 304 探测）；
     * 2) 新增合并官方日历遗漏的网播番（begin 在窗口内的 bgm-data 条目直接入库）；
     * 3) 逐话播出事件同步（AniList 真值 + broadcast 规则推算）并仲裁回写。
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
    private val anilistService: AniListService,
    private val bilibiliService: BilibiliService,
    private val userPreferences: UserPreferencesDataSource,
    private val collectionRepository: CollectionRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ScheduleRepository {
    override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> =
        scheduleDao.getSchedulesByWeekday(weekday).map { entities ->
            val nowMillis = TimeUtils.nowEpochMillis()
            entities
                .filter { it.isActiveForSchedule(nowMillis) }
                .map { it.toModel(json) }
        }

    /**
     * 同步闸门：全量刷新管线进行中为 true，对外流扣住不发；
     * 闸门放开时经 flatMapLatest 重新订阅 DAO 流，确保以管线完成后的最新数据发一次。
     */
    private val scheduleHoldGate = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllSchedulesStream(): Flow<List<AirSchedule>> =
        scheduleHoldGate
            .flatMapLatest { holding ->
                if (holding) {
                    flowOf(null)
                } else {
                    scheduleDao.getAllSchedules().map { entities ->
                        val nowMillis = TimeUtils.nowEpochMillis()
                        entities
                            .filter { it.isActiveForSchedule(nowMillis) }
                            .map { it.toModel(json) }
                    }
                }
            }.filterNotNull()

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

    override suspend fun refreshSchedules(): AppResult<Unit> =
        try {
            // 1. 获取官方每日放送日历数据（包含高清海报图片、官方评分与排行、星期分类）
            val calendarDays =
                runCatching { apiService.getCalendar() }.getOrNull() ?: emptyList()

            if (calendarDays.isEmpty()) {
                return AppResult.Error(IllegalStateException("获取官方放送日历失败，请稍后重试"))
            }

            // 2. 读取本地已有的缓存实体，复用已同步好的播放源与时刻（0 次 CDN 请求）
            val existingEntities = scheduleDao.getAllSchedulesList().associateBy { it.bgmId }
            val entities =
                calendarDays.flatMap { day ->
                    val officialWeekday = day.weekday.id
                    day.items.map { subject ->
                        mapCalendarSubjectToEntity(subject, officialWeekday, existingEntities[subject.id])
                    }
                }

            if (entities.isNotEmpty()) {
                // 官方日历只是名单的一部分：只清理官方名单内的行，
                // bgm-data 合并插入的网络独播番（不在官方日历中）必须保留
                scheduleDao.deleteOfficialSchedulesNotIn(entities.map { it.bgmId })
                scheduleDao.insertSchedules(entities)
                runCatchingCancellable { syncAirEvents() }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("同步官方放送日历"))
        }

    override suspend fun refreshAllSchedules(): AppResult<Unit> {
        scheduleHoldGate.value = true
        try {
            val failures = mutableListOf<String>()
            refreshSchedules().onError { _, message -> failures += message }
            syncBangumiData().onError { _, message -> failures += message }
            return if (failures.isEmpty()) {
                AppResult.Success(Unit)
            } else {
                AppResult.Error(IllegalStateException(failures.joinToString("；")), failures.joinToString("；"))
            }
        } finally {
            scheduleHoldGate.value = false
        }
    }

    override suspend fun syncBangumiData(force: Boolean): AppResult<Unit> =
        try {
            var existingEntities = scheduleDao.getAllSchedulesList()
            if (existingEntities.isEmpty()) {
                // 首次同步或本地时刻表为空时，先拉取官方日历构建基础实体，以确保 bgm-data 能够正确填充播放源与时刻
                val refreshResult = refreshSchedules()
                if (refreshResult is AppResult.Error) {
                    return refreshResult
                }
                existingEntities = scheduleDao.getAllSchedulesList()
            }

            val now = TimeUtils.nowEpochMillis()
            val (currentYear, currentMonth) = TimeUtils.currentCstYearMonth()
            val shouldForce = force || existingEntities.all { it.sitesJson == "[]" || it.sitesJson.isBlank() }

            val currentEtag =
                if (shouldForce) {
                    ""
                } else {
                    userPreferences.userPreferences
                        .firstOrNull()
                        ?.bangumiDataEtag
                        .orEmpty()
                }

            val bangumiDataResult =
                runCatching {
                    dataService.getRecentBangumiData(
                        year = currentYear,
                        month = currentMonth,
                        lookbackMonths = 4,
                        aheadMonths = 1,
                        etag = currentEtag,
                    )
                }.getOrElse { throwable ->
                    return AppResult.Error(throwable, throwable.toUserFriendlyMessage("同步番组数据"))
                }

            if (bangumiDataResult is BangumiDataResult.Success) {
                val itemsWithId = bangumiDataResult.items.filter { it.bgmSubjectId != null }
                bangumiDataResult.etag?.takeIf { it.isNotBlank() }?.let {
                    userPreferences.setBangumiDataEtag(it)
                }
                userPreferences.setBangumiDataLastSyncTimestamp(now)

                val bgmMap = itemsWithId.associateBy { it.bgmSubjectId!! }

                val enriched = enrichExistingEntities(existingEntities, bgmMap, now)
                val existingIds = existingEntities.map { it.bgmId }.toSet()
                val inserted = createNewEntitiesFromBangumiData(itemsWithId, existingIds, now)

                val allSchedules = enriched + inserted
                val finalized = enrichMissingMetadata(allSchedules)
                scheduleDao.insertSchedules(finalized)
            }

            // ③ 逐话事件同步与仲裁：失败只降级为"预计"数据，不阻塞名单同步
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

    private fun enrichExistingEntities(
        existingEntities: List<AirScheduleEntity>,
        bgmMap: Map<Long, BangumiDataItem>,
        now: Long,
    ): List<AirScheduleEntity> =
        existingEntities.map { entity ->
            val dataItem = bgmMap[entity.bgmId] ?: return@map entity
            val siteLinks = dataItem.sites.mapNotNull { s -> resolveSiteLink(s) }
            val anilistId =
                entity.anilistId
                    ?: dataItem.sites
                        .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                        ?.id
                        ?.toLongOrNull()
            entity.copy(
                titleCn = entity.titleCn.ifBlank { dataItem.chineseTitle },
                sitesJson = json.encodeToString(siteLinks),
                anilistId = anilistId,
            )
        }

    private fun createNewEntitiesFromBangumiData(
        itemsWithId: List<BangumiDataItem>,
        existingIds: Set<Long>,
        now: Long,
    ): List<AirScheduleEntity> {
        val windowStart = now - ROSTER_LOOKBACK_DAYS * DAY_MILLIS
        val windowEnd = now + ROSTER_AHEAD_DAYS * DAY_MILLIS
        val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(now)
        return itemsWithId.mapNotNull { item ->
            val bgmId = item.bgmSubjectId!!
            if (existingIds.contains(bgmId)) return@mapNotNull null
            val beginMillis = TimeUtils.epochMillisOfIso(item.begin) ?: return@mapNotNull null
            if (beginMillis < windowStart || beginMillis > windowEnd) return@mapNotNull null
            val endMillis = TimeUtils.epochMillisOfIso(item.end)
            if (endMillis != null && endMillis < weekStartMillis) return@mapNotNull null
            val normalizedBegin = TimeUtils.normalizeIsoUtc(item.begin)
            val timeCst = TimeUtils.formatToCstTime(item.begin)
            AirScheduleEntity(
                bgmId = bgmId,
                title = item.title,
                titleCn = item.chineseTitle,
                coverUrl = "",
                ratingScore = 0.0,
                airDate = item.begin.substringBefore("T"),
                beginAtUtc = normalizedBegin.ifBlank { null },
                sortMinutes = TimeUtils.parseTimeToMinutes(timeCst),
                weekday = TimeUtils.cstWeekdayOfEpoch(beginMillis),
                timeCst = timeCst,
                timeJst = TimeUtils.formatToJstTime(item.begin),
                sitesJson = json.encodeToString(item.sites.mapNotNull { s -> resolveSiteLink(s) }),
                anilistId =
                    item.sites
                        .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                        ?.id
                        ?.toLongOrNull(),
                broadcastRule = "",
                source = AirScheduleEntity.SOURCE_BGM_DATA,
                nextEpisode = 0,
                nextEpisodeAtUtc = "",
                nextEpisodeKind = "",
            )
        }
    }

    /**
     * 同步全量条目的播出事件并仲裁时刻表：
     * 1) 从 AniList 获取逐话真值与高清封面（日番主流，精确到秒级）；
     * 2) AniList 未覆盖但存在 B站源的条目由 BilibiliService 获取逐话真值（国创/B站独播）；
     * 3) 仲裁回写条目的 next* 字段与 weekday，自动回补 AniList 高清封面，并剔除已完结僵尸条目。
     */
    private suspend fun syncAirEvents() {
        val entities = scheduleDao.getAllSchedulesList()
        if (entities.isEmpty()) return
        val nowMillis = TimeUtils.nowEpochMillis()

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

        // 2. Bilibili 逐话真值（针对 AniList 未覆盖但有 B 站源的国创/独播条目）
        val uncoveredTargets = targets.filter { it.bgmId !in coveredSubjects }
        val bilibiliEvents = fetchBilibiliAirEvents(uncoveredTargets, nowMillis)
        if (bilibiliEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(bilibiliEvents)
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
        scheduleDao.insertSchedules(reconciled)
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

    private suspend fun fetchBilibiliAirEvents(
        entities: List<AirScheduleEntity>,
        nowMillis: Long,
    ): List<AirEventEntity> {
        val bilibiliEvents = mutableListOf<AirEventEntity>()
        for (entity in entities) {
            val siteId = extractBilibiliSiteId(entity) ?: continue
            val episodes = runCatching { bilibiliService.getAiringEpisodes(siteId) }.getOrElse { emptyList() }
            if (episodes.isEmpty()) continue

            for (episode in episodes) {
                if (episode.episode < 1) continue
                val airAtMillis = episode.airAtEpochSeconds * 1000
                val kind = if (airAtMillis <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED
                bilibiliEvents +=
                    AirEventEntity(
                        subjectId = entity.bgmId,
                        episode = episode.episode,
                        airAtUtc = TimeUtils.isoUtcFromEpochMillis(airAtMillis),
                        kind = kind,
                        source = EVENT_SOURCE_BILIBILI,
                    )
            }
        }
        return bilibiliEvents
    }

    private fun extractBilibiliSiteId(entity: AirScheduleEntity): String? {
        val links: List<SiteLink> =
            try {
                json.decodeFromString(entity.sitesJson)
            } catch (_: Exception) {
                emptyList()
            }
        val playUrl = links.firstOrNull { it.siteName.equals(BILIBILI_SITE, ignoreCase = true) }?.playUrl
        if (playUrl != null) {
            return when {
                playUrl.contains("/media/") -> playUrl.substringAfter("/media/").substringBefore("/").substringBefore("?")
                playUrl.contains("/play/") -> playUrl.substringAfter("/play/").substringBefore("/").substringBefore("?")
                else -> null
            }
        }
        val rawSites: List<BangumiDataSite> =
            try {
                json.decodeFromString(entity.sitesJson)
            } catch (_: Exception) {
                emptyList()
            }
        val rawSite = rawSites.firstOrNull { it.site.equals(BILIBILI_SITE, ignoreCase = true) } ?: return null
        return rawSite.id.ifBlank {
            when {
                rawSite.url.contains("/media/") ->
                    rawSite.url
                        .substringAfter("/media/")
                        .substringBefore("/")
                        .substringBefore("?")
                rawSite.url.contains("/play/") ->
                    rawSite.url
                        .substringAfter("/play/")
                        .substringBefore("/")
                        .substringBefore("?")
                else -> null
            }
        }
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

    private fun mapCalendarSubjectToEntity(
        subject: Subject,
        officialWeekday: Int,
        existing: AirScheduleEntity?,
    ): AirScheduleEntity =
        if (existing == null) {
            createInitialScheduleEntity(subject, officialWeekday)
        } else {
            mergeScheduleEntity(subject, officialWeekday, existing)
        }

    private fun createInitialScheduleEntity(
        subject: Subject,
        officialWeekday: Int,
    ): AirScheduleEntity =
        AirScheduleEntity(
            bgmId = subject.id,
            title = subject.name,
            titleCn = subject.nameCn,
            coverUrl = BgmImageUtils.toSecureUrl(subject.images?.bestImage.orEmpty()),
            ratingScore = subject.rating?.score ?: 0.0,
            airDate = subject.airDate,
            beginAtUtc = null,
            sortMinutes = AirScheduleEntity.UNKNOWN_SORT_MINUTES,
            weekday = officialWeekday,
            timeCst = "",
            timeJst = "",
            sitesJson = "[]",
            anilistId = null,
            broadcastRule = "",
            totalEpisodes = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes.takeIf { it > 0 } ?: 0,
            source = AirScheduleEntity.SOURCE_OFFICIAL,
            nextEpisode = 0,
            nextEpisodeAtUtc = "",
            nextEpisodeKind = "",
        )

    private fun mergeScheduleEntity(
        subject: Subject,
        officialWeekday: Int,
        existing: AirScheduleEntity,
    ): AirScheduleEntity {
        val coverUrl =
            BgmImageUtils
                .toSecureUrl(subject.images?.bestImage.orEmpty())
                .ifBlank { existing.coverUrl }
        val titleCn = subject.nameCn.ifBlank { existing.titleCn }
        val airDate = existing.airDate.ifBlank { subject.airDate }
        val totalEpisodes =
            subject.eps.takeIf { it > 0 }
                ?: subject.totalEpisodes.takeIf { it > 0 }
                ?: existing.totalEpisodes

        return existing.copy(
            title = subject.name,
            titleCn = titleCn,
            coverUrl = coverUrl,
            ratingScore = subject.rating?.score ?: 0.0,
            airDate = airDate,
            weekday = officialWeekday,
            totalEpisodes = totalEpisodes,
            source = AirScheduleEntity.SOURCE_OFFICIAL,
        )
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
        if (source != AirScheduleEntity.SOURCE_BGM_DATA) return true
        val weekStartMillis = TimeUtils.cstWeekStartEpochMillis(nowMillis)
        val nextMillis = TimeUtils.epochMillisOfIso(nextEpisodeAtUtc)
        if (nextMillis != null && nextMillis >= weekStartMillis) {
            return true
        }

        val beginMillis = TimeUtils.epochMillisOfIso(beginUtc)
        if (totalEpisodes == 1) {
            return beginMillis != null && beginMillis >= weekStartMillis
        }
        if (totalEpisodes > 1) {
            if (nextEpisode >= totalEpisodes && nextMillis != null && nextMillis < weekStartMillis) {
                return false
            }
            return beginMillis != null && beginMillis + totalEpisodes * WEEK_MILLIS >= weekStartMillis
        }
        // totalEpisodes <= 0
        return beginMillis != null && beginMillis >= weekStartMillis - 14 * DAY_MILLIS
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
        const val ROSTER_LOOKBACK_DAYS = 90L
        const val ROSTER_AHEAD_DAYS = 7L

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
