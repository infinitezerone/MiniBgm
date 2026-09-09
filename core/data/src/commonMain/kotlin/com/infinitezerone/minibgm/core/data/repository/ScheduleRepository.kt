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
import com.infinitezerone.minibgm.core.model.BangumiDataSite
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.UpcomingAiring
import com.infinitezerone.minibgm.core.network.AniListService
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiDataResult
import com.infinitezerone.minibgm.core.network.BangumiDataService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val titles = scheduleDao.getAllSchedulesList().associateBy { it.bgmId }
        return airEventDao
            .getUpcomingEvents(subjectIds, fromIso, toIso)
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
    }

    override suspend fun refreshSchedules(): AppResult<Unit> =
        try {
            // 1. 获取官方每日放送日历数据（包含高清海报图片、官方评分与排行、星期分类）
            val calendarDays =
                runCatching { apiService.getCalendar() }.getOrNull() ?: emptyList()

            if (calendarDays.isEmpty()) {
                return AppResult.Error(IllegalStateException("Failed to load official schedule calendar"))
            }

            // 2. 读取本地已有的缓存实体，复用已同步好的播放源与时刻（0 次 CDN 请求）
            val existingEntities = scheduleDao.getAllSchedulesList().associateBy { it.bgmId }
            val entities = mutableListOf<AirScheduleEntity>()

            for (day in calendarDays) {
                val officialWeekday = day.weekday.id
                for (subject in day.items) {
                    val bgmId = subject.id
                    val existing = existingEntities[bgmId]

                    val coverUrl =
                        (subject.images?.bestImage ?: "").replace("http://", "https://")
                    val titleCn = subject.nameCn.ifBlank { existing?.titleCn ?: "" }
                    val beginUtc =
                        existing?.beginUtc.takeIf { !it.isNullOrBlank() }
                            ?: subject.airDate

                    entities.add(
                        AirScheduleEntity(
                            bgmId = bgmId,
                            title = subject.name,
                            titleCn = titleCn,
                            coverUrl = coverUrl,
                            ratingScore = subject.rating?.score ?: 0.0,
                            beginUtc = beginUtc,
                            weekday = officialWeekday,
                            timeCst = existing?.timeCst ?: "",
                            timeJst = existing?.timeJst ?: "",
                            sitesJson = existing?.sitesJson ?: "[]",
                            anilistId = existing?.anilistId,
                            broadcastRule = existing?.broadcastRule ?: "",
                            totalEpisodes =
                                subject.eps.takeIf { it > 0 }
                                    ?: subject.totalEpisodes.takeIf { it > 0 }
                                    ?: existing?.totalEpisodes
                                    ?: 0,
                            source = AirScheduleEntity.SOURCE_OFFICIAL,
                            nextEpisode = existing?.nextEpisode ?: 0,
                            nextEpisodeAtUtc = existing?.nextEpisodeAtUtc ?: "",
                            nextEpisodeKind = existing?.nextEpisodeKind ?: "",
                            updatedAt = existing?.updatedAt ?: 0L,
                        ),
                    )
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
            AppResult.Error(e)
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

            // 若本地所有条目的播放源均为空（例如此前未同步成功或被 304 错过），则强制拉取（忽略 ETag），避免 304 死锁
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
                runCatching { dataService.getBangumiData(currentEtag) }.getOrElse { throwable ->
                    return AppResult.Error(throwable)
                }

            if (bangumiDataResult is BangumiDataResult.Success) {
                val now = TimeUtils.nowEpochMillis()
                bangumiDataResult.etag.takeIf { !it.isNullOrBlank() }?.let {
                    userPreferences.setBangumiDataEtag(it)
                }
                userPreferences.setBangumiDataLastSyncTimestamp(now)

                val itemsWithId = bangumiDataResult.items.filter { it.bgmSubjectId != null }
                val bgmMap = itemsWithId.associateBy { it.bgmSubjectId!! }

                // ① 增强既有条目：话源、中文名、时刻，并回填 AniList ID 与周播规则
                val enriched =
                    existingEntities.map { entity ->
                        val dataItem = bgmMap[entity.bgmId] ?: return@map entity
                        val siteLinks = dataItem.sites.mapNotNull { s -> resolveSiteLink(s) }
                        entity.copy(
                            titleCn = entity.titleCn.ifBlank { dataItem.chineseTitle },
                            beginUtc = dataItem.begin,
                            timeCst = TimeUtils.formatToCstTime(dataItem.begin),
                            timeJst = TimeUtils.formatToJstTime(dataItem.begin),
                            sitesJson = json.encodeToString(siteLinks),
                            anilistId =
                                entity.anilistId
                                    ?: dataItem.sites
                                        .firstOrNull { it.site.equals(ANILIST_SITE, ignoreCase = true) }
                                        ?.id
                                        ?.toLongOrNull(),
                            broadcastRule = dataItem.broadcast.ifBlank { entity.broadcastRule },
                        )
                    }

                // ② 新增合并：官方日历不收录的网络独播番（如 Re:Zero 夺还篇），
                //    begin 在窗口内且带 bgm 条目 ID 的直接入库，消除 roster 缺口
                val existingIds = existingEntities.map { it.bgmId }.toSet()
                val windowStart = now - ROSTER_LOOKBACK_DAYS * DAY_MILLIS
                val windowEnd = now + ROSTER_AHEAD_DAYS * DAY_MILLIS
                val inserted =
                    itemsWithId.mapNotNull { item ->
                        val bgmId = item.bgmSubjectId!!
                        if (existingIds.contains(bgmId)) return@mapNotNull null
                        val beginMillis = TimeUtils.epochMillisOfIso(item.begin) ?: return@mapNotNull null
                        if (beginMillis < windowStart || beginMillis > windowEnd) return@mapNotNull null
                        val ruleStart = TimeUtils.parseBroadcastRule(item.broadcast)?.first ?: beginMillis
                        AirScheduleEntity(
                            bgmId = bgmId,
                            title = item.title,
                            titleCn = item.chineseTitle,
                            coverUrl = "",
                            ratingScore = 0.0,
                            beginUtc = item.begin,
                            weekday = TimeUtils.jstWeekdayOfEpoch(ruleStart),
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
                        )
                    }

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
            AppResult.Error(e)
        }

    /**
     * 逐话事件同步与仲裁：
     * 1) AniList airingSchedule → actual（已播）/ scheduled（已排期）事件，
     *    通过 beginUtc 就近对齐自推导拆季偏移；
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
        val existingEvents = airEventDao.getAllAirEvents().groupBy { it.subjectId }

        // 1. AniList 逐话真值
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
            // 偏移自推导：bgm 条目 begin 就近对齐 AniList 话次（±3 天），消除拆季粒度错位
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
                val kind =
                    if (airAtMillis <= nowMillis) {
                        AirEventKind.ACTUAL
                    } else {
                        AirEventKind.SCHEDULED
                    }
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
        if (anilistEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(anilistEvents)
        }

        // 2. 规则推算（predicted）：只服务 AniList 未覆盖的条目
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
        if (predictedEvents.isNotEmpty()) {
            airEventDao.insertAirEvents(predictedEvents)
        }

        // 3. 仲裁回写：weekday 分桶与 next* 字段取"距当前最近"的事件，
        //    按可信度 actual > scheduled > predicted 打破同刻平局
        val allEvents = airEventDao.getAllAirEvents().groupBy { it.subjectId }
        val reconciled =
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
        scheduleDao.insertSchedules(reconciled)
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
     * 仅对 coverUrl 为空的条目并发调用官方接口；获取后落库持久化，后续刷新直接复用。
     */
    private suspend fun enrichMissingMetadata(schedules: List<AirScheduleEntity>): List<AirScheduleEntity> {
        val missing = schedules.filter { it.coverUrl.isBlank() }
        if (missing.isEmpty()) return schedules

        val metadataByBgmId =
            coroutineScope {
                missing
                    .map { entity ->
                        async {
                            runCatching { apiService.getSubject(entity.bgmId) }.getOrNull()
                        }
                    }.awaitAll()
            }.filterNotNull()
                .associateBy { it.id }

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
