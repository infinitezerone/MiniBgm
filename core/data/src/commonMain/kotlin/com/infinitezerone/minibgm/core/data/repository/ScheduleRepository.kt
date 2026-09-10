package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.runCatchingCancellable
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
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

interface ScheduleRepository {
    fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>>

    /** 观察全量放送排播流（单流监听，避免按天拆流导致的重复数据库查询与高频重组） */
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
     * 后台 / 手动同步：
     * 1) CDN 播放源静态数据（ETag 304 探测）；
     * 2) 新增合并官方日历遗漏的网播番（begin 在窗口内的 bgm-data 条目直接入库）；
     * 3) 逐话播出事件同步（AniList 真值 + broadcast 规则推算）并仲裁回写。
     */
    suspend fun syncBangumiData(force: Boolean = false): AppResult<Unit>

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
    private val userPreferences: UserPreferencesDataSource,
    private val collectionRepository: CollectionRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ScheduleRepository {
    override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirSchedule>> =
        scheduleDao.getSchedulesByWeekday(weekday).map { entities ->
            entities.map { it.toModel(json) }
        }

    override fun getAllSchedulesStream(): Flow<List<AirSchedule>> =
        scheduleDao.getAllSchedules().map { entities ->
            entities.map { it.toModel(json) }
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
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("同步官方放送日历"))
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

    private fun enrichExistingEntities(
        existingEntities: List<AirScheduleEntity>,
        bgmMap: Map<Long, BangumiDataItem>,
        now: Long,
    ): List<AirScheduleEntity> =
        existingEntities.map { entity ->
            val dataItem = bgmMap[entity.bgmId] ?: return@map entity
            val siteLinks = dataItem.sites.mapNotNull { s -> resolveSiteLink(s) }
            val rule = dataItem.broadcast.ifBlank { entity.broadcastRule }
            val begin = dataItem.begin.ifBlank { entity.beginUtc }
            val (calcEp, calcAirUtc) = calculateNextEpisode(begin, rule, entity.totalEpisodes, now)
            entity.copy(
                titleCn = entity.titleCn.ifBlank { dataItem.chineseTitle },
                beginUtc = begin,
                timeCst = TimeUtils.formatToCstTime(begin).ifBlank { entity.timeCst },
                timeJst = TimeUtils.formatToJstTime(begin).ifBlank { entity.timeJst },
                sitesJson = json.encodeToString(siteLinks),
                anilistId =
                    entity.anilistId
                        ?: dataItem.sites
                            .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                            ?.id
                            ?.toLongOrNull(),
                broadcastRule = rule,
                nextEpisode = if (calcEp > 0) calcEp else entity.nextEpisode,
                nextEpisodeAtUtc = calcAirUtc.ifBlank { entity.nextEpisodeAtUtc },
                nextEpisodeKind = if (calcAirUtc.isNotBlank()) AirEventKind.SCHEDULED else entity.nextEpisodeKind,
            )
        }

    private fun createNewEntitiesFromBangumiData(
        itemsWithId: List<BangumiDataItem>,
        existingIds: Set<Long>,
        now: Long,
    ): List<AirScheduleEntity> {
        val windowStart = now - ROSTER_LOOKBACK_DAYS * DAY_MILLIS
        val windowEnd = now + ROSTER_AHEAD_DAYS * DAY_MILLIS
        return itemsWithId.mapNotNull { item ->
            val bgmId = item.bgmSubjectId!!
            if (existingIds.contains(bgmId)) return@mapNotNull null
            val beginMillis = TimeUtils.epochMillisOfIso(item.begin) ?: return@mapNotNull null
            if (beginMillis < windowStart || beginMillis > windowEnd) return@mapNotNull null
            val ruleStart = TimeUtils.parseBroadcastRule(item.broadcast)?.first ?: beginMillis
            val (nextEp, nextEpUtc) = calculateNextEpisode(item.begin, item.broadcast, 0, now)
            AirScheduleEntity(
                bgmId = bgmId,
                title = item.title,
                titleCn = item.chineseTitle,
                coverUrl = "",
                ratingScore = 0.0,
                beginUtc = item.begin,
                weekday = TimeUtils.cstWeekdayOfEpoch(ruleStart),
                timeCst = TimeUtils.formatToCstTime(item.begin),
                timeJst = TimeUtils.formatToJstTime(item.begin),
                sitesJson = json.encodeToString(item.sites.mapNotNull { s -> resolveSiteLink(s) }),
                anilistId =
                    item.sites
                        .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                        ?.id
                        ?.toLongOrNull(),
                broadcastRule = item.broadcast,
                source = AirScheduleEntity.SOURCE_BGM_DATA,
                nextEpisode = nextEp,
                nextEpisodeAtUtc = nextEpUtc,
                nextEpisodeKind = if (nextEpUtc.isNotBlank()) AirEventKind.SCHEDULED else "",
            )
        }
    }

    /**
     * 同步全量条目的播出事件并仲裁时刻表：
     * 1) 从 AniList 获取逐话真值（实际播出/官方预定，仅在看条目精准校准）；
     * 2) AniList 未覆盖的条目由 broadcast 规则生成 predicted 事件（未来 30 天窗口）；
     * 3) 按可信度仲裁回写条目的 weekday 分桶与 next* 字段。
     */
    private suspend fun syncAirEvents() {
        val entities = scheduleDao.getAllSchedulesList()
        if (entities.isEmpty()) return
        val nowMillis = TimeUtils.nowEpochMillis()
        val nowIso = TimeUtils.isoUtcFromEpochMillis(nowMillis)

        // 事件与名单裁剪：过期预计事件、已消失条目的事件、超出名单窗口的 bgm_data 合并行
        val keepIds = entities.map { it.bgmId }.toSet()
        airEventDao.deleteEventsNotIn(keepIds.toList())
        airEventDao.deleteStalePredictedEvents(nowIso)
        scheduleDao.deleteStaleBgmDataSchedules(TimeUtils.isoUtcFromEpochMillis(nowMillis - ROSTER_LOOKBACK_DAYS * DAY_MILLIS))

        // 用户在看收藏过滤：有在看数据时仅对在看条目发起 AniList 精准排期校验（削减 95% 请求量）
        val trackingSubjectIds =
            collectionRepository
                ?.getCollectionsByTypeStream(CollectionType.DOING)
                ?.firstOrNull()
                ?.map { it.subjectId }
                ?.toSet()

        val targetsForAniList =
            if (!trackingSubjectIds.isNullOrEmpty()) {
                entities.filter { it.bgmId in trackingSubjectIds }
            } else {
                entities
            }

        // 1. AniList 逐话真值（在追条目精准校准）
        val (anilistEvents, coveredSubjects) = fetchAnilistAirEvents(targetsForAniList, nowMillis)
        if (anilistEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(anilistEvents)
        }

        // 2. 规则推算（predicted）：只服务 AniList 未覆盖的条目
        val predictedEvents = generatePredictedAirEvents(entities, coveredSubjects, nowMillis)
        if (predictedEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(predictedEvents)
        }

        // 3. 仲裁回写：weekday 分桶与 next* 字段取"距当前最近"的事件，
        //    按可信度 actual > scheduled > predicted 打破同刻平局
        val allEvents = airEventDao.getAllAirEvents().groupBy { it.subjectId }
        val reconciled = reconcileScheduleEntities(entities, allEvents, nowMillis)
        scheduleDao.insertSchedules(reconciled)
    }

    private suspend fun fetchAnilistAirEvents(
        entities: List<AirScheduleEntity>,
        nowMillis: Long,
    ): Pair<List<AirEventEntity>, Set<Long>> {
        val withAnilistId = entities.filter { it.anilistId != null }
        val schedulesByAnilistId =
            runCatching { anilistService.getAiringSchedules(withAnilistId.mapNotNull { it.anilistId }) }
                .getOrElse { emptyMap() }
        val anilistEvents = mutableListOf<AirEventEntity>()
        val coveredSubjects = mutableSetOf<Long>()

        for (entity in withAnilistId) {
            val anilistId = entity.anilistId ?: continue
            val episodes = schedulesByAnilistId[anilistId].orEmpty()
            if (episodes.isEmpty()) continue
            val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: continue

            val offset =
                episodes
                    .minByOrNull { abs(it.airAtEpochSeconds * 1000 - beginMillis) }
                    ?.takeIf { abs(it.airAtEpochSeconds * 1000 - beginMillis) <= OFFSET_TOLERANCE_MILLIS }
                    ?.episode
                    ?.minus(1)
                    ?: continue

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
                coveredSubjects += entity.bgmId
            }
        }
        return anilistEvents to coveredSubjects
    }

    private fun generatePredictedAirEvents(
        entities: List<AirScheduleEntity>,
        coveredSubjects: Set<Long>,
        nowMillis: Long,
    ): List<AirEventEntity> {
        val predictedEvents = mutableListOf<AirEventEntity>()
        for (entity in entities) {
            if (entity.bgmId in coveredSubjects) continue
            val rule =
                TimeUtils.parseBroadcastRule(entity.broadcastRule)
                    ?: run {
                        val beginMillis = TimeUtils.epochMillisOfIso(entity.beginUtc) ?: return@run null
                        beginMillis to DAY_MILLIS
                    }
                    ?: continue
            val (startMillis, periodMillis) = rule
            var episode = ((nowMillis - startMillis) / periodMillis).toInt() + 1
            if (episode < 1) episode = 1
            if (entity.totalEpisodes in 1..(episode - 1)) continue // 官方话数已播完，不再预计
            var airAt = startMillis + (episode - 1L) * periodMillis
            while (airAt < nowMillis) {
                airAt += periodMillis
                episode += 1
            }
            var generated = 0
            while (airAt <= nowMillis + PREDICTED_HORIZON_DAYS * DAY_MILLIS && generated < MAX_PREDICTED_EVENTS) {
                if (entity.totalEpisodes <= 0 || episode <= entity.totalEpisodes) {
                    predictedEvents +=
                        AirEventEntity(
                            subjectId = entity.bgmId,
                            episode = episode,
                            airAtUtc = TimeUtils.isoUtcFromEpochMillis(airAt),
                            kind = AirEventKind.PREDICTED,
                            source = EVENT_SOURCE_BGM_DATA,
                        )
                    generated += 1
                }
                airAt += periodMillis
                episode += 1
            }
        }
        return predictedEvents
    }

    private fun reconcileScheduleEntities(
        entities: List<AirScheduleEntity>,
        allEvents: Map<Long, List<AirEventEntity>>,
        nowMillis: Long,
    ): List<AirScheduleEntity> =
        entities.map { entity ->
            val events =
                allEvents[entity.bgmId]
                    .orEmpty()
                    .mapNotNull { event ->
                        val millis = TimeUtils.epochMillisOfIso(event.airAtUtc) ?: return@mapNotNull null
                        event to millis
                    }
            if (events.isEmpty()) {
                return@map entity
            }
            val closest =
                events.minWithOrNull(
                    compareBy({ abs(it.second - nowMillis) }, { AirEventKind.rank(it.first.kind) }),
                ) ?: return@map entity
            val (closestEvent, closestMillis) = closest
            val next = events.filter { it.second > nowMillis }.minByOrNull { it.second }
            entity.copy(
                weekday = TimeUtils.cstWeekdayOfEpoch(closestMillis),
                nextEpisode = closestEvent.episode,
                nextEpisodeAtUtc = next?.first?.airAtUtc ?: "",
                nextEpisodeKind = next?.first?.kind ?: closestEvent.kind,
            )
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
            coverUrl = (subject.images?.bestImage.orEmpty()).replace("http://", "https://"),
            ratingScore = subject.rating?.score ?: 0.0,
            beginUtc = subject.airDate,
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
            updatedAt = 0L,
        )

    private fun mergeScheduleEntity(
        subject: Subject,
        officialWeekday: Int,
        existing: AirScheduleEntity,
    ): AirScheduleEntity {
        val coverUrl = (subject.images?.bestImage.orEmpty()).replace("http://", "https://")
        val titleCn = subject.nameCn.ifBlank { existing.titleCn }
        val beginUtc = existing.beginUtc.takeIf { it.isNotBlank() } ?: subject.airDate
        val totalEpisodes =
            subject.eps.takeIf { it > 0 }
                ?: subject.totalEpisodes.takeIf { it > 0 }
                ?: existing.totalEpisodes

        return existing.copy(
            title = subject.name,
            titleCn = titleCn,
            coverUrl = coverUrl,
            ratingScore = subject.rating?.score ?: 0.0,
            beginUtc = beginUtc,
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

        val calculatedEp =
            nextEpisode.takeIf { it > 0 }
                ?: TimeUtils.calculateCurrentEpisode(beginUtc)

        return AirSchedule(
            bgmId = bgmId,
            title = title,
            titleCn = titleCn,
            coverUrl = coverUrl,
            ratingScore = ratingScore,
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
     * 仅对 coverUrl 为空的条目平滑顺序调用官方接口（单批上限 5 条，避免突发流量触发限流）；获取后落库持久化，后续刷新直接复用。
     */
    private suspend fun enrichMissingMetadata(schedules: List<AirScheduleEntity>): List<AirScheduleEntity> {
        val missing = schedules.filter { it.coverUrl.isBlank() }.take(5)
        if (missing.isEmpty()) return schedules

        val metadataByBgmId =
            missing
                .mapNotNull { entity ->
                    runCatching { apiService.getSubject(entity.bgmId) }.getOrNull()
                }.associateBy { it.id }

        if (metadataByBgmId.isEmpty()) return schedules

        return schedules.map { entity ->
            val subject = metadataByBgmId[entity.bgmId] ?: return@map entity
            val coverUrl = (subject.images?.bestImage ?: "").replace("http://", "https://")
            val rating = subject.rating?.score ?: 0.0
            val eps = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes
            entity.copy(
                coverUrl = coverUrl.ifBlank { entity.coverUrl },
                ratingScore = if (entity.ratingScore == 0.0) rating else entity.ratingScore,
                totalEpisodes = if (entity.totalEpisodes == 0) eps else entity.totalEpisodes,
                titleCn = entity.titleCn.ifBlank { subject.nameCn },
            )
        }
    }

    private fun calculateNextEpisode(
        beginUtc: String,
        broadcastRule: String,
        totalEpisodes: Int,
        nowMillis: Long,
    ): Pair<Int, String> {
        val rule =
            TimeUtils.parseBroadcastRule(broadcastRule)
                ?: run {
                    val beginMillis = TimeUtils.epochMillisOfIso(beginUtc) ?: return 0 to ""
                    beginMillis to (7L * 24 * 60 * 60 * 1000)
                }
        val (startMillis, periodMillis) = rule
        if (periodMillis <= 0L) return 0 to ""

        if (startMillis > nowMillis) {
            return 1 to TimeUtils.isoUtcFromEpochMillis(startMillis)
        }

        val elapsed = nowMillis - startMillis
        val airedCount = (elapsed / periodMillis).toInt() + 1
        if (totalEpisodes in 1..airedCount) {
            return airedCount to ""
        }
        val nextEpisode = airedCount + 1
        val nextAirMillis = startMillis + (nextEpisode - 1L) * periodMillis
        return airedCount to TimeUtils.isoUtcFromEpochMillis(nextAirMillis)
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val WEEK_MILLIS = 7L * DAY_MILLIS
        const val ANILIST_SITE = "anilist"
        const val EVENT_SOURCE_ANILIST = "anilist"
        const val EVENT_SOURCE_BGM_DATA = "bgm_data"
        const val ROSTER_LOOKBACK_DAYS = 90L
        const val ROSTER_AHEAD_DAYS = 7L
        const val PREDICTED_HORIZON_DAYS = 30L
        const val MAX_PREDICTED_EVENTS = 6
        const val OFFSET_TOLERANCE_MILLIS = 3L * DAY_MILLIS

        private fun buildBilibiliUrl(id: String): String =
            when {
                id.startsWith("http") -> id
                id.startsWith("md") -> "https://www.bilibili.com/bangumi/media/$id"
                id.startsWith("ss") -> "https://www.bilibili.com/bangumi/play/$id"
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
