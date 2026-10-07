package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.dao.AniListMappingDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.database.entity.BangumiDataMonthEtagEntity
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.network.AniListAiringEpisode
import com.infinitezerone.minibgm.core.network.AniListMediaSchedule
import com.infinitezerone.minibgm.core.network.AniListWeeklyScheduleItem
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotDto
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotEpisodeDto
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotItemDto
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotResult
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotService
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto
import com.infinitezerone.minibgm.core.network.model.EpisodePageResponse
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.SearchSubjectResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import com.infinitezerone.minibgm.core.testing.datastore.createTestUserPreferencesDataSource
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleRepositoryImplTest {
    private companion object {
        val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    private class FakeAirScheduleDao : AirScheduleDao {
        private val schedulesFlow = MutableStateFlow<List<AirScheduleEntity>>(emptyList())

        override fun getSchedulesByWeekday(weekday: Int): Flow<List<AirScheduleEntity>> =
            schedulesFlow.map { list -> list.filter { it.weekday == weekday } }

        override fun getAllSchedules(): Flow<List<AirScheduleEntity>> = schedulesFlow

        override suspend fun getAllSchedulesList(): List<AirScheduleEntity> = schedulesFlow.value

        override suspend fun getSchedulesByIds(ids: List<Long>): List<AirScheduleEntity> = schedulesFlow.value.filter { it.bgmId in ids }

        override suspend fun insertSchedules(schedules: List<AirScheduleEntity>) {
            val currentMap = schedulesFlow.value.associateBy { it.bgmId }.toMutableMap()
            schedules.forEach { currentMap[it.bgmId] = it }
            schedulesFlow.value = currentMap.values.toList()
        }

        override suspend fun deleteStaleBgmDataSchedules(date: String) {
            schedulesFlow.value =
                schedulesFlow.value.filter {
                    it.source != "bgm_data" || it.airDate >= date
                }
        }

        override suspend fun deleteBgmDataSchedulesByIds(ids: List<Long>) {
            val idSet = ids.toSet()
            schedulesFlow.value =
                schedulesFlow.value.filter {
                    it.source != "bgm_data" || it.bgmId !in idSet
                }
        }

        override suspend fun deleteSchedulesNotIn(keepIds: List<Long>) {
            val keep = keepIds.toSet()
            schedulesFlow.value = schedulesFlow.value.filter { it.bgmId in keep }
        }

        override suspend fun updateCoverUrl(
            bgmId: Long,
            coverUrl: String,
        ) {
            schedulesFlow.value =
                schedulesFlow.value.map {
                    if (it.bgmId == bgmId) it.copy(coverUrl = coverUrl) else it
                }
        }

        override suspend fun clearSchedules() {
            schedulesFlow.value = emptyList()
        }
    }

    private class FakeAirEventDao : AirEventDao {
        val events = MutableStateFlow<List<AirEventEntity>>(emptyList())

        override suspend fun insertAirEvents(events: List<AirEventEntity>) {
            val current =
                this.events.value
                    .associateBy { Triple(it.subjectId, it.episode, it.source) }
                    .toMutableMap()
            events.forEach { current[Triple(it.subjectId, it.episode, it.source)] = it }
            this.events.value = current.values.toList()
        }

        override suspend fun getAllAirEvents(): List<AirEventEntity> = events.value

        override fun getAllAirEventsStream(): Flow<List<AirEventEntity>> = events

        override suspend fun deleteStalePredictedEvents(isoUtc: String) {
            val cutoff = TimeUtils.epochMillisOfIso(isoUtc) ?: return
            events.value =
                events.value.filter { it.kind != AirEventKind.PREDICTED || (TimeUtils.epochMillisOfIso(it.airAtUtc) ?: 0L) >= cutoff }
        }

        override suspend fun deleteAllPredictedEvents() {
            events.value = events.value.filter { it.kind != AirEventKind.PREDICTED }
        }

        override suspend fun deleteEventsNotIn(keepIds: List<Long>) {
            events.value = events.value.filter { it.subjectId in keepIds }
        }

        override suspend fun deleteAnilistEventsFor(subjectId: Long) {
            events.value = events.value.filterNot { it.source == "anilist" && it.subjectId == subjectId }
        }

        override suspend fun getUpcomingEvents(
            subjectIds: List<Long>,
            fromIso: String,
            toIso: String,
        ): List<AirEventEntity> =
            events.value.filter {
                it.subjectId in subjectIds.toSet() && it.airAtUtc >= fromIso && it.airAtUtc <= toIso
            }

        override suspend fun getSubjectIdsWithEventsBetween(
            fromIso: String,
            toIso: String,
        ): List<Long> =
            events.value
                .filter { it.airAtUtc >= fromIso && it.airAtUtc <= toIso }
                .map { it.subjectId }
                .distinct()
    }

    /**
     * 快照服务假体：把旧测试的三种数据形态（周名单 / 逐话真值 / 兼容 schedules）合成为快照。
     * 周名单条目的时间会平移进当前 CST 周（按整周数平移、星期不变），
     * 复刻真实"本周在播"窗口语义，同时保持基于原时间的星期断言不变。
     */
    private class FakeScheduleSnapshotService : ScheduleSnapshotService {
        var schedules: Map<Long, List<AniListAiringEpisode>> = emptyMap()
        var mediaSchedules: Map<Long, AniListMediaSchedule> = emptyMap()
        var weeklySchedules: List<AniListWeeklyScheduleItem> = emptyList()
        var bgmIdByAnilistId: Map<Long, Long> = emptyMap()
        var titleCnByAnilistId: Map<Long, String> = emptyMap()
        var airDateByAnilistId: Map<Long, String> = emptyMap()
        var sitesByAnilistId: Map<Long, List<ScheduleSnapshotSiteDto>> = emptyMap()
        var customSnapshot: ScheduleSnapshotDto? = null
        var snapshot: ScheduleSnapshotDto?
            get() = customSnapshot
            set(value) {
                customSnapshot = value
            }

        override suspend fun getSnapshot(ifNoneMatchEtag: String?): ScheduleSnapshotResult {
            if (customSnapshot != null) return ScheduleSnapshotResult.Modified(customSnapshot!!, etag = "custom-etag")

            data class ItemMeta(
                val weekly: AniListWeeklyScheduleItem?,
                val media: AniListMediaSchedule?,
            )

            val meta = linkedMapOf<Long, ItemMeta>()
            val episodesById = linkedMapOf<Long, MutableList<ScheduleSnapshotEpisodeDto>>()
            for (w in weeklySchedules) {
                meta[w.anilistId] = ItemMeta(w, meta[w.anilistId]?.media)
                episodesById
                    .getOrPut(w.anilistId) { mutableListOf() }
                    .add(ScheduleSnapshotEpisodeDto(n = w.episode, t = w.airAtEpochSeconds))
            }
            for ((id, ms) in mediaSchedules) {
                meta[id] = ItemMeta(meta[id]?.weekly, ms)
                episodesById
                    .getOrPut(id) { mutableListOf() }
                    .addAll(ms.episodes.map { ScheduleSnapshotEpisodeDto(n = it.episode, t = it.airAtEpochSeconds) })
            }
            for ((id, eps) in schedules) {
                episodesById
                    .getOrPut(id) { mutableListOf() }
                    .addAll(eps.map { ScheduleSnapshotEpisodeDto(n = it.episode, t = it.airAtEpochSeconds) })
            }
            val snapshotDto =
                ScheduleSnapshotDto(
                    schema = "minibgm-schedule-snapshot/1",
                    generatedAt = "",
                    items =
                        episodesById.map { (id, eps) ->
                            val m = meta[id]
                            val bgmId = bgmIdByAnilistId[id]
                            val titleCn = titleCnByAnilistId[id]
                            val airDate = airDateByAnilistId[id]
                            val sites = sitesByAnilistId[id] ?: emptyList()
                            ScheduleSnapshotItemDto(
                                anilistId = id,
                                bgmId = bgmId,
                                title = m?.weekly?.titleNative ?: "",
                                titleCn = titleCn,
                                countryOfOrigin = "JP",
                                format = m?.weekly?.format ?: "",
                                status = "RELEASING",
                                coverUrl = m?.weekly?.coverUrl ?: m?.media?.coverUrl,
                                isAdult = m?.weekly?.isAdult ?: false,
                                startYear = m?.weekly?.startYear ?: 0,
                                startMonth = m?.weekly?.startMonth ?: 0,
                                airDate = airDate,
                                sites = sites,
                                episodes = eps.distinctBy { it.n }.sortedBy { it.n },
                            )
                        },
                )
            return ScheduleSnapshotResult.Modified(snapshotDto, etag = "fake-etag")
        }

        override suspend fun getSnapshot(): ScheduleSnapshotDto = (getSnapshot(null) as ScheduleSnapshotResult.Modified).snapshot
    }

    private class FakeBangumiApiService : BangumiApiService {
        var subjects: Map<Long, Subject> = emptyMap()

        override suspend fun getSubject(id: Long): Subject = subjects[id] ?: error("Not implemented for id: $id")

        override suspend fun getSubjectCharacters(id: Long): List<SubjectCharacter> = error("Not implemented")

        override suspend fun getCharacter(id: Long): com.infinitezerone.minibgm.core.model.CharacterDetail = error("Not implemented")

        override suspend fun getCharacterSubjects(id: Long): List<com.infinitezerone.minibgm.core.model.RelatedWork> =
            error("Not implemented")

        override suspend fun getSubjectPersons(id: Long): List<SubjectPerson> = error("Not implemented")

        override suspend fun getPerson(id: Long): com.infinitezerone.minibgm.core.model.PersonDetail = error("Not implemented")

        override suspend fun getPersonSubjects(id: Long): List<com.infinitezerone.minibgm.core.model.RelatedWork> = error("Not implemented")

        override suspend fun getSubjectRelations(id: Long): List<SubjectRelation> = error("Not implemented")

        override suspend fun getEpisodes(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): EpisodePageResponse = error("Not implemented")

        var searchResults: List<Subject> = emptyList()

        override suspend fun searchSubjects(
            keyword: String,
            type: Int,
            limit: Int,
            offset: Int,
        ): SearchSubjectResponse = SearchSubjectResponse(results = searchResults.size, list = searchResults)

        override suspend fun searchSubjectsAdvanced(
            request: SearchSubjectsRequest,
            limit: Int,
            offset: Int,
        ): PageResponse<Subject> = error("Not implemented")

        override suspend fun getUserCollections(
            username: String,
            subjectType: Int,
            type: Int?,
            limit: Int,
            offset: Int,
        ): UserCollectionPageResponse = error("Not implemented")

        override suspend fun getMe(): com.infinitezerone.minibgm.core.model.UserProfile = error("Not implemented")

        override suspend fun getCollection(
            username: String,
            subjectId: Long,
        ): com.infinitezerone.minibgm.core.model.UserCollection? = error("Not implemented")

        override suspend fun updateCollection(
            subjectId: Long,
            type: Int,
            rate: Int?,
            comment: String?,
            private: Boolean,
            epStatus: Int?,
            tags: List<String>?,
        ) = error("Not implemented")

        override suspend fun updateEpisodeStatus(
            subjectId: Long,
            episodeId: Long,
            type: Int,
        ) = error("Not implemented")
    }

    private class FakeAniListMappingDao : AniListMappingDao {
        val mappings = MutableStateFlow<List<AniListBgmMappingEntity>>(emptyList())
        val monthEtags = mutableMapOf<String, BangumiDataMonthEtagEntity>()

        override suspend fun getMappingsByAniListIds(anilistIds: List<Long>): List<AniListBgmMappingEntity> =
            mappings.value.filter { it.anilistId in anilistIds }

        override suspend fun upsertMappings(mappings: List<AniListBgmMappingEntity>) {
            val current =
                this.mappings.value
                    .associateBy { it.anilistId }
                    .toMutableMap()
            mappings.forEach { current[it.anilistId] = it }
            this.mappings.value = current.values.toList()
        }

        override suspend fun getMonthEtag(monthKey: String): BangumiDataMonthEtagEntity? = monthEtags[monthKey]

        override suspend fun upsertMonthEtag(etag: BangumiDataMonthEtagEntity) {
            monthEtags[etag.monthKey] = etag
        }
    }

    private fun createRepository(
        apiService: FakeBangumiApiService = FakeBangumiApiService(),
        scheduleDao: FakeAirScheduleDao = FakeAirScheduleDao(),
        airEventDao: FakeAirEventDao = FakeAirEventDao(),
        anilistMappingDao: FakeAniListMappingDao = FakeAniListMappingDao(),
        snapshotService: FakeScheduleSnapshotService = FakeScheduleSnapshotService(),
        userPreferences: com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource = createTestUserPreferencesDataSource(),
        collectionRepository: CollectionRepository? = null,
    ): ScheduleRepositoryImpl =
        ScheduleRepositoryImpl(
            scheduleDao = scheduleDao,
            airEventDao = airEventDao,
            anilistMappingDao = anilistMappingDao,
            snapshotService = snapshotService,
            userPreferences = userPreferences,
            collectionRepository = collectionRepository,
        )

    @Test
    fun syncBangumiData_updatesRoomOnSuccess() =
        runTest {
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 1001L,
                                title = "无职转生",
                                titleCn = "无职转生",
                                coverUrl = "",
                                ratingScore = 8.5,
                                airDate = "2026-07-05",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val weekStartSec = TimeUtils.cstWeekStartEpochMillis(TimeUtils.nowEpochMillis()) / 1000
            val snapshotService =
                FakeScheduleSnapshotService().apply {
                    snapshot =
                        ScheduleSnapshotDto(
                            schema = "minibgm-schedule-snapshot/1",
                            generatedAt = "",
                            items =
                                listOf(
                                    ScheduleSnapshotItemDto(
                                        anilistId = 2001L,
                                        bgmId = 1001L,
                                        title = "无职转生",
                                        titleCn = "无职转生",
                                        countryOfOrigin = "JP",
                                        format = "TV",
                                        status = "RELEASING",
                                        coverUrl = "https://example.com/cover.jpg",
                                        isAdult = false,
                                        startYear = 2026,
                                        startMonth = 7,
                                        airDate = "2026-07-05",
                                        sites =
                                            listOf(
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bangumi",
                                                    id = "1001",
                                                ),
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bilibili",
                                                    id = "md12345",
                                                ),
                                            ),
                                        episodes =
                                            listOf(
                                                ScheduleSnapshotEpisodeDto(
                                                    n = 1,
                                                    t = weekStartSec + 12 * 3600,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                }

            val repo =
                createRepository(
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = snapshotService,
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            assertTrue(userPrefs.userPreferences.first().bangumiDataLastSyncTimestamp > 0L)

            val updated = dao.getAllSchedulesList().first { it.bgmId == 1001L }
            assertEquals("无职转生", updated.titleCn)
            assertTrue(updated.sitesJson.contains("哔哩哔哩"))
            assertEquals("12:00", updated.timeCst)
        }

    @Test
    fun syncBangumiData_mapsUnmappedWeeklyAnimeViaOnDemandMonthFile() =
        runTest {
            val beginMillis = TimeUtils.nowEpochMillis() - 10 * DAY_MILLIS
            val beginIso = TimeUtils.isoUtcFromEpochMillis(beginMillis)
            val (currentYear, currentMonth) = TimeUtils.currentCstYearMonth()

            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 189046L,
                                episode = 1,
                                airAtEpochSeconds = TimeUtils.cstWeekStartEpochMillis(TimeUtils.nowEpochMillis()) / 1000 + 12 * 3600,
                                titleNative = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                startYear = currentYear,
                                startMonth = currentMonth,
                            ),
                        )
                    bgmIdByAnilistId = mapOf(189046L to 633836L)
                    titleCnByAnilistId = mapOf(189046L to "Re：从零开始的异世界生活 第四季 夺还篇")
                    airDateByAnilistId = mapOf(189046L to beginIso.substringBefore("T"))
                    sitesByAnilistId =
                        mapOf(
                            189046L to
                                listOf(
                                    ScheduleSnapshotSiteDto(site = "bangumi", id = "633836"),
                                    ScheduleSnapshotSiteDto(site = "anilist", id = "189046"),
                                    ScheduleSnapshotSiteDto(site = "gamer", id = "144453"),
                                ),
                        )
                }
            val dao = FakeAirScheduleDao()
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    scheduleDao = dao,
                    snapshotService = anilist,
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            assertEquals(1, stored.size)
            val webOnly = stored.first { it.bgmId == 633836L }
            assertEquals("Re：从零开始的异世界生活 第四季 夺还篇", webOnly.titleCn)
            assertEquals(
                TimeUtils.cstWeekdayOfEpoch(
                    (TimeUtils.cstWeekStartEpochMillis(TimeUtils.nowEpochMillis()) / 1000 + 12 * 3600) * 1000,
                ),
                webOnly.weekday,
            )
            assertEquals(189046L, webOnly.anilistId)
            assertEquals(AirScheduleEntity.SOURCE_BGM_DATA, webOnly.source)
            assertTrue(webOnly.sitesJson.contains("巴哈姆特"))
        }

    @Test
    fun syncBangumiData_mapsLongRunningAnimeBeyondMonthWindowViaStartDate() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val beginMillis = nowMillis - 192 * DAY_MILLIS
            val beginDate = TimeUtils.formatEpochSecondsToDate(beginMillis / 1000)
            val beginYear = beginDate.substring(0, 4).toInt()
            val beginMonth = beginDate.substring(5, 7).toInt()

            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 210482L,
                                episode = 20,
                                airAtEpochSeconds = nowMillis / 1000,
                                titleNative = "スティール・ボール・ラン ジョジョの奇妙な冒険",
                                startYear = beginYear,
                                startMonth = beginMonth,
                            ),
                        )
                    bgmIdByAnilistId = mapOf(210482L to 551918L)
                    titleCnByAnilistId = mapOf(210482L to "飙马野郎 JOJO的奇妙冒险")
                    airDateByAnilistId = mapOf(210482L to beginDate)
                }
            val dao = FakeAirScheduleDao()

            val repo =
                createRepository(
                    scheduleDao = dao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val stored = dao.getAllSchedulesList()
            assertTrue(stored.any { it.bgmId == 551918L }, "开播超 90 天但仍在周更的番应被收录")
            assertTrue(stored.none { it.bgmId == 551919L }, "不在周排期的已完结条目不应被收录")

            // 时刻表流中也应可见
            val visible = repo.getAllSchedulesStream().first()
            assertTrue(visible.any { it.bgmId == 551918L }, "长档番应出现在时刻表流中")
            assertTrue(visible.none { it.bgmId == 551919L })
        }

    @Test
    fun syncBangumiData_persistsSnapshotMetadata_forWebOnlyShow() =
        runTest {
            val beginMillis = TimeUtils.nowEpochMillis() - 5 * DAY_MILLIS
            val beginIso = TimeUtils.isoUtcFromEpochMillis(beginMillis)
            val weekStartSec = TimeUtils.cstWeekStartEpochMillis(TimeUtils.nowEpochMillis()) / 1000

            val snapshotService =
                FakeScheduleSnapshotService().apply {
                    snapshot =
                        ScheduleSnapshotDto(
                            schema = "minibgm-schedule-snapshot/1",
                            generatedAt = "",
                            items =
                                listOf(
                                    ScheduleSnapshotItemDto(
                                        anilistId = 189046L,
                                        bgmId = 633836L,
                                        title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                        titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                        countryOfOrigin = "JP",
                                        format = "TV",
                                        status = "RELEASING",
                                        coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx189046.jpg",
                                        isAdult = false,
                                        startYear = beginIso.substring(0, 4).toInt(),
                                        startMonth = beginIso.substring(5, 7).toInt(),
                                        airDate = beginIso.substringBefore("T"),
                                        sites =
                                            listOf(
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bangumi",
                                                    id = "633836",
                                                ),
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bilibili",
                                                    id = "md12345",
                                                ),
                                            ),
                                        episodes =
                                            listOf(
                                                ScheduleSnapshotEpisodeDto(
                                                    n = 1,
                                                    t = weekStartSec + 12 * 3600,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                }
            val dao = FakeAirScheduleDao()
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = snapshotService,
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            val webOnly = stored.first { it.bgmId == 633836L }
            assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx189046.jpg", webOnly.coverUrl)
            assertEquals("Re：从零开始的异世界生活 第四季 夺还篇", webOnly.titleCn)
            assertTrue(webOnly.sitesJson.contains("哔哩哔哩"))
        }

    @Test
    fun syncAirEvents_derivesAnilistSplitCourOffset_andReconciles() =
        runTest {
            // bgm 拆季条目：begin = 8/12；AniList 是整季 19 话中的 11/12/13 话（8/5、8/12、8/19）
            // 偏移自推导：begin 就近对齐第 12 话 → offset = 11 → bgm 第 1/2 话
            val beginIso = "2026-08-12T14:00:00Z"
            val beginMillis = TimeUtils.epochMillisOfIso(beginIso)!!
            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            189046L to
                                listOf(
                                    AniListAiringEpisode(
                                        episode = 11,
                                        airAtEpochSeconds =
                                            TimeUtils.epochMillisOfIso("2026-08-05T14:00:00Z")!! / 1000,
                                    ),
                                    AniListAiringEpisode(episode = 12, airAtEpochSeconds = beginMillis / 1000),
                                    AniListAiringEpisode(
                                        episode = 13,
                                        airAtEpochSeconds =
                                            TimeUtils.epochMillisOfIso("2026-08-19T14:00:00Z")!! / 1000,
                                    ),
                                ),
                        )
                }
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 633836L,
                                title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = beginIso.substringBefore("T"),
                                beginAtUtc = beginIso,
                                weekday = 3,
                                timeCst = "22:00",
                                timeJst = "23:00",
                                sitesJson = "[]",
                                anilistId = 189046L,
                                broadcastRule = "R/$beginIso/P7D",
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            // 逐话事件已按偏移换算成 bgm 话数（第 1/2 话）
            val storedEvents = airEventDao.getAllAirEvents()
            assertEquals(setOf(1, 2), storedEvents.map { it.episode }.toSet())
            // 仲裁回写：最近事件（第 2 话）驱动 weekday 分桶与话数
            val updated = dao.getAllSchedulesList().single()
            assertEquals(2, updated.nextEpisode)
            assertEquals(
                if (TimeUtils.epochMillisOfIso(updated.nextEpisodeAtUtc)!! <= System.currentTimeMillis()) {
                    AirEventKind.ACTUAL
                } else {
                    AirEventKind.SCHEDULED
                },
                updated.nextEpisodeKind,
            )
            assertEquals(TimeUtils.cstWeekdayOfEpoch(TimeUtils.epochMillisOfIso("2026-08-19T14:00:00Z")!!), updated.weekday)
        }

    @Test
    fun syncAirEvents_derivesAnilistSplitCourOffset_withLateSeasonSnapshotEpisodes_correctsRoundingAndCapsAtTotalEpisodes() =
        runTest {
            // 快照只保留后半季末尾三话（如 AniList 17、18、19 话，对应 9/16、9/23、9/30）
            // 真实开播日为 8/12，条目总集数仅 8 话。
            // 验证：通过四舍五入推导 offset = 11，将 AniList 19 话精准映射到第 8 话（绝不出现虚假的第 9 话）
            val beginIso = "2026-08-12T14:00:00Z"
            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            189046L to
                                listOf(
                                    AniListAiringEpisode(
                                        episode = 17,
                                        airAtEpochSeconds = TimeUtils.epochMillisOfIso("2026-09-16T13:00:00Z")!! / 1000,
                                    ),
                                    AniListAiringEpisode(
                                        episode = 18,
                                        airAtEpochSeconds = TimeUtils.epochMillisOfIso("2026-09-23T13:00:00Z")!! / 1000,
                                    ),
                                    AniListAiringEpisode(
                                        episode = 19,
                                        airAtEpochSeconds = TimeUtils.epochMillisOfIso("2026-09-30T13:00:00Z")!! / 1000,
                                    ),
                                ),
                        )
                }
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 633836L,
                                title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = beginIso.substringBefore("T"),
                                beginAtUtc = beginIso,
                                totalEpisodes = 8,
                                weekday = 3,
                                timeCst = "21:00",
                                timeJst = "22:00",
                                sitesJson = "[]",
                                anilistId = 189046L,
                                broadcastRule = "R/$beginIso/P7D",
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val storedEvents = airEventDao.getAllAirEvents().sortedBy { it.episode }
            assertEquals(listOf(6, 7, 8), storedEvents.map { it.episode })
            val ep8Event = storedEvents.first { it.episode == 8 }
            assertEquals("2026-09-30T13:00:00Z", ep8Event.airAtUtc)
        }

    @Test
    fun syncAirEvents_uncoveredSubjectsWithoutSources_haveNoAirEvents() =
        runTest {
            // 无 anilist 且无 bilibili 站点的条目：不生成任何机械推算或虚假事件
            val ruleStartIso = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis() - 3 * DAY_MILLIS)
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 633836L,
                                title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = ruleStartIso.substringBefore("T"),
                                beginAtUtc = ruleStartIso,
                                weekday = 3,
                                timeCst = "22:00",
                                timeJst = "23:00",
                                sitesJson = "[]",
                                broadcastRule = "R/$ruleStartIso/P7D",
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val storedEvents = airEventDao.getAllAirEvents()
            assertTrue(storedEvents.isEmpty())
            val updated = dao.getAllSchedulesList().single()
            assertEquals("", updated.nextEpisodeKind)
            assertEquals(0, updated.nextEpisode)
        }

    @Test
    fun getAllSchedulesStream_filtersOutFinishedShortAnimeLikeCyborg009() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val twoMonthsAgo = nowMillis - 60L * 24 * 3600 * 1000
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            // 人造人009类：官方来源、7月播完、3集短片、无未来排期
                            AirScheduleEntity(
                                bgmId = 571896L,
                                title = "サイボーグ009 ネメシス",
                                titleCn = "人造人009 涅墨西斯",
                                coverUrl = "",
                                ratingScore = 7.0,
                                airDate = "2026-07-19",
                                totalEpisodes = 3,
                                weekday = 7,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                                nextEpisode = 1,
                                nextEpisodeAtUtc = TimeUtils.isoUtcFromEpochMillis(twoMonthsAgo),
                                nextEpisodeKind = AirEventKind.ACTUAL,
                            ),
                            // 柯南类：长篇官方年番、无未来确切排期、总集数未定 -> 必须安全保留
                            AirScheduleEntity(
                                bgmId = 899L,
                                title = "名探偵コナン",
                                titleCn = "名侦探柯南",
                                coverUrl = "",
                                ratingScore = 8.5,
                                airDate = "1996-01-08",
                                totalEpisodes = 0,
                                weekday = 6,
                                timeCst = "18:00",
                                timeJst = "19:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                                nextEpisode = 0,
                                nextEpisodeAtUtc = "",
                                nextEpisodeKind = "",
                            ),
                            // 当季在播番：本周或未来有排期 -> 正常展示
                            AirScheduleEntity(
                                bgmId = 101L,
                                title = "当季在播番",
                                titleCn = "当季在播番",
                                coverUrl = "",
                                ratingScore = 7.5,
                                airDate = "2026-07-06",
                                totalEpisodes = 12,
                                weekday = 1,
                                timeCst = "23:00",
                                timeJst = "24:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                                nextEpisode = 10,
                                nextEpisodeAtUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis + 24 * 3600 * 1000),
                                nextEpisodeKind = AirEventKind.SCHEDULED,
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    scheduleDao = dao,
                )

            val schedules = repo.getAllSchedulesStream().first()
            assertEquals(2, schedules.size)
            assertTrue(schedules.none { it.bgmId == 571896L }) // 009 被精准剔除
            assertTrue(schedules.any { it.bgmId == 899L }) // 柯南安全保留
            assertTrue(schedules.any { it.bgmId == 101L }) // 在播番正常展示
        }

    @Test
    fun syncAirEvents_whenEpisodeAiredEarlierThisWeek_reconcilesToAiredEpisodeOfThisWeek() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            // 钳制进当前 CST 周：周一凌晨运行时 now-10h 会落回上周，导致"本周已播"前提失效
            val weekStartSec = TimeUtils.cstWeekStartEpochMillis(nowSeconds * 1000) / 1000
            val weekEndSec = TimeUtils.cstWeekEndEpochMillis(nowSeconds * 1000) / 1000
            val airedEarlierThisWeek = maxOf(nowSeconds - 10 * 3600, weekStartSec + 60)
            val nextWeekAiring = airedEarlierThisWeek + 8 * 86400 // 真正落到下周（+7 天仍可能在本周日尾）
            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            207809L to
                                listOf(
                                    AniListAiringEpisode(
                                        episode = 11,
                                        airAtEpochSeconds = airedEarlierThisWeek,
                                    ),
                                    AniListAiringEpisode(
                                        episode = 12,
                                        airAtEpochSeconds = nextWeekAiring,
                                    ),
                                ),
                        )
                }
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 627648L,
                                title = "天は赤い河のほとり",
                                titleCn = "天是红河岸",
                                coverUrl = "",
                                ratingScore = 4.1,
                                airDate = "2026-07-07",
                                beginAtUtc = "2026-07-07T16:35:00Z",
                                weekday = 3,
                                timeCst = "00:35",
                                timeJst = "01:35",
                                anilistId = 207809L,
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val updated = dao.getAllSchedulesList().single()
            // 本周该天已播话数（第 11 话），不能被下周话数（第 12 话）倒挂覆盖
            assertEquals(11, updated.nextEpisode)
            assertEquals(AirEventKind.ACTUAL, updated.nextEpisodeKind)
        }

    @Test
    fun syncAirEvents_reconcilesAnilistWithLargeOffsetWithinTolerance_suchAsMagilumiere() =
        runTest {
            // 魔法光源股份有限公司第二季场景：
            // bangumi-data beginAtUtc 是周三 01:09（BS 卫星台重播时间）
            // airDate 官方首播日期是 2026-10-04（周六）
            // AniList 首播记录为周六 23:55（时差约 73 小时）
            // 在 7 天容差机制下，应成功对齐首话，且不被旧的 3 天容差判定超时抛弃
            val nowMillis = TimeUtils.nowEpochMillis()
            val nowSeconds = nowMillis / 1000
            val satAirEpoch = nowSeconds + 2 * 86400 // 2 天后的周六
            val wedBeginIso = TimeUtils.isoUtcFromEpochMillis((satAirEpoch - 73 * 3600) * 1000)
            val satAirDate = TimeUtils.formatEpochSecondsToDate(satAirEpoch)

            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            176662L to
                                listOf(
                                    AniListAiringEpisode(
                                        episode = 1,
                                        airAtEpochSeconds = satAirEpoch,
                                    ),
                                    AniListAiringEpisode(
                                        episode = 2,
                                        airAtEpochSeconds = satAirEpoch + 7 * 86400,
                                    ),
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 529723L,
                                title = "株式会社マジルミエ 第2期",
                                titleCn = "魔法光源股份有限公司 第二季",
                                coverUrl = "",
                                ratingScore = 7.5,
                                airDate = satAirDate,
                                beginAtUtc = wedBeginIso,
                                weekday = 6,
                                timeCst = "23:55",
                                timeJst = "00:55",
                                sitesJson = "[]",
                                anilistId = 176662L,
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val storedEvents = airEventDao.getAllAirEvents().sortedBy { it.episode }
            assertEquals(listOf(1, 2), storedEvents.map { it.episode })
            val updated = dao.getAllSchedulesList().single()
            assertEquals(1, updated.nextEpisode)
            assertEquals(AirEventKind.SCHEDULED, updated.nextEpisodeKind)
            assertEquals(TimeUtils.isoUtcFromEpochMillis(satAirEpoch * 1000), updated.nextEpisodeAtUtc)
        }

    @Test
    fun syncAirEvents_midSeasonEpisodesWithoutFirstEpisode_stillReconcilesTimeAndEpisode() =
        runTest {
            // 季中在播场景（例如 7 月开播，当前 9 月中旬查询 AniList 仅返回第 10、11 话）：
            // 首播日 60 天前已过，AniList 窗口内不含第 1 话，但绝对开播时间必须直接驱动时刻与星期，绝不抛弃条目
            val nowMillis = TimeUtils.nowEpochMillis()
            val nowSeconds = nowMillis / 1000
            val futureAirEpoch = nowSeconds + 86400 // 明天开播
            val pastAirEpoch = futureAirEpoch - 14 * 86400 // 两周前已开播（确保在跨周/周末运行时亦稳定属于上周之前）
            val ep1AirDate = TimeUtils.formatEpochSecondsToDate(futureAirEpoch - 10 * 7 * 86400)

            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            159309L to
                                listOf(
                                    AniListAiringEpisode(
                                        episode = 10,
                                        airAtEpochSeconds = pastAirEpoch,
                                    ),
                                    AniListAiringEpisode(
                                        episode = 11,
                                        airAtEpochSeconds = futureAirEpoch,
                                    ),
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 412144L,
                                title = "乙女ゲー世界はモブに厳しい世界です2",
                                titleCn = "恋爱游戏世界对路人角色很不友好 第二季",
                                coverUrl = "",
                                ratingScore = 7.2,
                                airDate = ep1AirDate,
                                beginAtUtc = null,
                                weekday = 3,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                                anilistId = 159309L,
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val storedEvents = airEventDao.getAllAirEvents().sortedBy { it.episode }
            assertEquals(listOf(10, 11), storedEvents.map { it.episode })
            val updated = dao.getAllSchedulesList().single()
            // 真实开播时间与星期由第 11 话时间戳驱动，绝非全天待定空字符串
            assertTrue(updated.timeCst.isNotBlank())
            assertTrue(updated.timeJst.isNotBlank())
            assertEquals(11, updated.nextEpisode)
            assertEquals(AirEventKind.SCHEDULED, updated.nextEpisodeKind)
            assertEquals(TimeUtils.isoUtcFromEpochMillis(futureAirEpoch * 1000), updated.nextEpisodeAtUtc)
        }

    @Test
    fun syncBangumiData_longRunningAnimeWithoutRule_doesNotPredictEpisode() =
        runTest {
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 899L,
                                title = "名探偵コナン",
                                titleCn = "名侦探柯南",
                                coverUrl = "https://example.com/conan.jpg",
                                ratingScore = 8.8,
                                airDate = "1996-01-08",
                                weekday = 6,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                                broadcastRule = "",
                                totalEpisodes = 1340,
                                nextEpisode = 11206, // 历史遗留的脏数据
                                nextEpisodeKind = AirEventKind.PREDICTED,
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val updated = dao.getAllSchedulesList().single()
            // 开播超 1 年且无规则的长篇番不胡乱推算，旧脏数据被清理为 0
            assertEquals(0, updated.nextEpisode)
            assertEquals("", updated.nextEpisodeKind)
        }

    @Test
    fun syncBangumiData_completedAnime_clearsNextEpisode() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            // 11 周前开播，总集数 9 话（已于 2 周前播完）
            val elevenWeeksAgo = nowMillis - 11 * 7 * DAY_MILLIS
            val airDate = TimeUtils.formatEpochSecondsToDate(elevenWeeksAgo / 1000)

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 481295L,
                                title = "X-Men '97 Season 2",
                                titleCn = "X战警97 第二季",
                                coverUrl = "https://example.com/xmen.jpg",
                                ratingScore = 8.5,
                                airDate = airDate,
                                beginAtUtc = "${airDate}T00:00:00Z",
                                weekday = 6,
                                timeCst = "00:00",
                                timeJst = "01:00",
                                sitesJson = "[]",
                                broadcastRule = "",
                                totalEpisodes = 9,
                                nextEpisode = 74, // 历史脏数据
                                nextEpisodeKind = AirEventKind.PREDICTED,
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val updated = dao.getAllSchedulesList().single()
            // 已播完的番剧不再预计后续话数，脏数据被清理为 0
            assertEquals(0, updated.nextEpisode)
            assertEquals("", updated.nextEpisodeKind)
        }

    @Test
    fun getUpcomingAiringForSubjects_withEmptySubjectIds_returnsEmptyList() =
        runTest {
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = FakeAirScheduleDao(),
                    airEventDao = FakeAirEventDao(),
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.getUpcomingAiringForSubjects(emptyList())
            assertTrue(result.isEmpty())
        }

    @Test
    fun getUpcomingAiringForSubjects_withStoredAirEvents_deduplicatesAndReturnsUpcomingAiring() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val eventTime1 = TimeUtils.isoUtcFromEpochMillis(nowMillis + 2 * 3600 * 1000L)
            val eventTime2 = TimeUtils.isoUtcFromEpochMillis(nowMillis + 4 * 3600 * 1000L)
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 1001L,
                                title = "Title 1",
                                titleCn = "标题 1",
                                coverUrl = "https://example.com/cover1.jpg",
                                ratingScore = 8.5,
                                airDate = "",
                                weekday = 1,
                                timeCst = "18:00",
                                timeJst = "19:00",
                                sitesJson = "[]",
                            ),
                            AirScheduleEntity(
                                bgmId = 1002L,
                                title = "Title 2",
                                titleCn = "标题 2",
                                coverUrl = "https://example.com/cover2.jpg",
                                ratingScore = 7.5,
                                airDate = "",
                                weekday = 2,
                                timeCst = "20:00",
                                timeJst = "21:00",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val airEventDao =
                FakeAirEventDao().apply {
                    insertAirEvents(
                        listOf(
                            // Predicted event for 1001 ep 3
                            AirEventEntity(
                                subjectId = 1001L,
                                episode = 3,
                                airAtUtc = eventTime1,
                                kind = AirEventKind.PREDICTED,
                                source = "rule",
                            ),
                            // Actual event for 1001 ep 3 (should win deduplication over predicted)
                            AirEventEntity(
                                subjectId = 1001L,
                                episode = 3,
                                airAtUtc = eventTime1,
                                kind = AirEventKind.ACTUAL,
                                source = "anilist",
                            ),
                            // Scheduled event for 1002 ep 5
                            AirEventEntity(
                                subjectId = 1002L,
                                episode = 5,
                                airAtUtc = eventTime2,
                                kind = AirEventKind.SCHEDULED,
                                source = "anilist",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val results = repo.getUpcomingAiringForSubjects(listOf(1001L, 1002L))

            assertEquals(2, results.size)
            assertEquals(1001L, results[0].subjectId)
            assertEquals(3, results[0].episode)
            assertEquals(AirEventKind.ACTUAL, results[0].kind)
            assertEquals("标题 1", results[0].titleCn)

            assertEquals(1002L, results[1].subjectId)
            assertEquals(5, results[1].episode)
            assertEquals(AirEventKind.SCHEDULED, results[1].kind)
        }

    @Test
    fun getUpcomingAiringForSubjects_whenNoAirEvents_fallsBackToAirScheduleEntity() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val upcomingAirUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis + 3 * 3600 * 1000L)
            val pastAirUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis - 48 * 3600 * 1000L)
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 2001L,
                                title = "Fallback Anime",
                                titleCn = "回退动画",
                                coverUrl = "https://example.com/cover.jpg",
                                ratingScore = 8.0,
                                airDate = "",
                                weekday = 3,
                                timeCst = "21:00",
                                timeJst = "22:00",
                                sitesJson = "[]",
                                nextEpisode = 7,
                                nextEpisodeAtUtc = upcomingAirUtc,
                                nextEpisodeKind = AirEventKind.SCHEDULED,
                            ),
                            AirScheduleEntity(
                                bgmId = 2002L,
                                title = "Out of Range Anime",
                                titleCn = "超出范围动画",
                                coverUrl = "https://example.com/cover.jpg",
                                ratingScore = 7.0,
                                airDate = "",
                                weekday = 1,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                nextEpisode = 1,
                                nextEpisodeAtUtc = pastAirUtc,
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val results =
                repo.getUpcomingAiringForSubjects(
                    subjectIds = listOf(2001L, 2002L),
                    hoursAhead = 24L,
                    lookbackHours = 6L,
                )

            assertEquals(1, results.size)
            assertEquals(2001L, results[0].subjectId)
            assertEquals(7, results[0].episode)
            assertEquals("回退动画", results[0].titleCn)
            assertEquals(upcomingAirUtc, results[0].airAtUtc)
        }

    @Test
    fun syncAirEvents_updatesBlankCoverFromAniList() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val futureAirEpoch = (nowMillis + 86400 * 1000) / 1000

            val anilist =
                FakeScheduleSnapshotService().apply {
                    mediaSchedules =
                        mapOf(
                            99999L to
                                AniListMediaSchedule(
                                    episodes = listOf(AniListAiringEpisode(episode = 1, airAtEpochSeconds = futureAirEpoch)),
                                    coverUrl = "https://s4.anilist.co/cover/large/bx99999.jpg",
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 5001L,
                                title = "网播新作",
                                titleCn = "网播新作",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2026-09-01",
                                weekday = 1,
                                timeCst = "12:00",
                                timeJst = "13:00",
                                sitesJson = "[]",
                                anilistId = 99999L,
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                            ),
                        ),
                    )
                }

            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val updated = dao.getAllSchedulesList().first { it.bgmId == 5001L }
            assertEquals("https://s4.anilist.co/cover/large/bx99999.jpg", updated.coverUrl)
        }

    @Test
    fun syncAirEvents_prunesZombieBgmDataSchedules_whenAllEventsEndedInPast() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val futureAirEpoch = (nowMillis + 86400 * 1000) / 1000
            val pastAirEpoch4WeeksAgo = (nowMillis - 28 * DAY_MILLIS) / 1000

            val anilist =
                FakeScheduleSnapshotService().apply {
                    mediaSchedules =
                        mapOf(
                            101L to
                                AniListMediaSchedule(
                                    episodes = listOf(AniListAiringEpisode(episode = 5, airAtEpochSeconds = futureAirEpoch)),
                                ),
                            102L to
                                AniListMediaSchedule(
                                    episodes = listOf(AniListAiringEpisode(episode = 1, airAtEpochSeconds = pastAirEpoch4WeeksAgo)),
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            // 活跃网播条目：明天有最新一集
                            AirScheduleEntity(
                                bgmId = 6001L,
                                title = "活跃网播番",
                                titleCn = "活跃网播番",
                                coverUrl = "https://example.com/c1.jpg",
                                ratingScore = 7.5,
                                airDate = "2026-08-01",
                                weekday = 3,
                                timeCst = "20:00",
                                timeJst = "21:00",
                                sitesJson = "[]",
                                anilistId = 101L,
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                            ),
                            // 僵尸网播条目：四周前已播完单集，未来无任何排期
                            AirScheduleEntity(
                                bgmId = 6002L,
                                title = "泡泡糖忍战 44",
                                titleCn = "泡泡糖忍战 44",
                                coverUrl = "https://example.com/c2.jpg",
                                ratingScore = 6.0,
                                airDate = "2026-08-01",
                                weekday = 4,
                                timeCst = "18:00",
                                timeJst = "19:00",
                                sitesJson = "[]",
                                anilistId = 102L,
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                            ),
                        ),
                    )
                }

            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val schedules = dao.getAllSchedulesList()
            assertTrue(schedules.any { it.bgmId == 6001L }, "活跃网播条目应保留")
            assertTrue(schedules.none { it.bgmId == 6002L }, "已完结的僵尸条目应被剔除")
        }

    @Test
    fun syncBangumiData_populatesSnapshotMetadataDirectlyWithoutApiCall() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val beginIso = TimeUtils.isoUtcFromEpochMillis(nowMillis)

            val dao = FakeAirScheduleDao()
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 600L,
                                episode = 1,
                                airAtEpochSeconds = nowMillis / 1000,
                                titleNative = "快照番",
                                coverUrl = "https://example.com/snapshot_cover.jpg",
                                startYear = beginIso.substring(0, 4).toInt(),
                                startMonth = beginIso.substring(5, 7).toInt(),
                            ),
                        )
                    bgmIdByAnilistId = mapOf(600L to 6L)
                    titleCnByAnilistId = mapOf(600L to "快照番中文名")
                }
            val repo =
                createRepository(
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val entity = dao.getAllSchedulesList().first { it.bgmId == 6L }
            assertEquals("https://example.com/snapshot_cover.jpg", entity.coverUrl)
            assertEquals("快照番中文名", entity.titleCn)
        }

    @Test
    fun getSchedulesByWeekday_filtersInactiveBgmData_andKeepsOfficial() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val futureAirUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis + 86400 * 1000)
            val pastAirUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis - 30 * DAY_MILLIS)

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 8001L,
                                title = "官方番",
                                titleCn = "官方番",
                                coverUrl = "",
                                ratingScore = 8.0,
                                airDate = "2026-07-01",
                                weekday = 1,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                            ),
                            AirScheduleEntity(
                                bgmId = 8002L,
                                title = "活跃网播番",
                                titleCn = "活跃网播番",
                                coverUrl = "",
                                ratingScore = 7.0,
                                airDate = "2026-08-01",
                                weekday = 1,
                                timeCst = "12:00",
                                timeJst = "13:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                                nextEpisodeAtUtc = futureAirUtc,
                            ),
                            AirScheduleEntity(
                                bgmId = 8003L,
                                title = "陈旧网播番",
                                titleCn = "陈旧网播番",
                                coverUrl = "",
                                ratingScore = 6.0,
                                airDate = "2026-06-01",
                                weekday = 1,
                                timeCst = "14:00",
                                timeJst = "15:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                                nextEpisodeAtUtc = pastAirUtc,
                            ),
                            // 连载中网播番：3周前开播，共12集，当前暂无未来话次真值但处于正常连载期
                            AirScheduleEntity(
                                bgmId = 8004L,
                                title = "连载中网播番",
                                titleCn = "连载中网播番",
                                coverUrl = "",
                                ratingScore = 7.5,
                                airDate = TimeUtils.isoUtcFromEpochMillis(nowMillis - 21 * DAY_MILLIS).substringBefore("T"),
                                beginAtUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis - 21 * DAY_MILLIS),
                                totalEpisodes = 12,
                                weekday = 1,
                                timeCst = "16:00",
                                timeJst = "17:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                                nextEpisodeAtUtc = "",
                            ),
                            // 已播完全部12集的网播番：最后一话在过去播出，应判定为已完结不展示
                            AirScheduleEntity(
                                bgmId = 8005L,
                                title = "已完结12集网播番",
                                titleCn = "已完结12集网播番",
                                coverUrl = "",
                                ratingScore = 7.0,
                                airDate = "2026-06-01",
                                totalEpisodes = 12,
                                nextEpisode = 12,
                                weekday = 1,
                                timeCst = "18:00",
                                timeJst = "19:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                                nextEpisodeAtUtc = pastAirUtc,
                            ),
                        ),
                    )
                }

            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val weekdaySchedules = repo.getSchedulesByWeekday(1).first()
            val allSchedules = repo.getAllSchedulesStream().first()

            assertEquals(3, weekdaySchedules.size)
            assertTrue(weekdaySchedules.any { it.bgmId == 8001L }, "官方条目始终展示")
            assertTrue(weekdaySchedules.any { it.bgmId == 8002L }, "活跃网播条目展示")
            assertTrue(weekdaySchedules.none { it.bgmId == 8003L }, "已完结网播条目不展示")
            assertTrue(weekdaySchedules.any { it.bgmId == 8004L }, "连载中且处于季播周期内的网播条目应展示")
            assertTrue(weekdaySchedules.none { it.bgmId == 8005L }, "播完全部12集的网播条目不展示")

            assertEquals(3, allSchedules.size)
        }

    @Test
    fun syncAirEvents_doesNotPurgeOngoingMultiEpisodeAnime_whenFutureEpisodesPendingOnAniList() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val pastAirEpoch1 = (nowMillis - 14 * DAY_MILLIS) / 1000
            val pastAirEpoch2 = (nowMillis - 7 * DAY_MILLIS) / 1000

            val anilist =
                FakeScheduleSnapshotService().apply {
                    mediaSchedules =
                        mapOf(
                            201L to
                                AniListMediaSchedule(
                                    episodes =
                                        listOf(
                                            AniListAiringEpisode(episode = 1, airAtEpochSeconds = pastAirEpoch1),
                                            AniListAiringEpisode(episode = 2, airAtEpochSeconds = pastAirEpoch2),
                                        ),
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            // 连载中12集网播番：虽然 AniList 目前只有前 2 集历史排期（第 3 集可能因停播周未即时定档），但总集数为 12 集，绝不能被误判为僵尸条目剔除
                            AirScheduleEntity(
                                bgmId = 9001L,
                                title = "连载季番",
                                titleCn = "连载季番",
                                coverUrl = "https://example.com/c1.jpg",
                                ratingScore = 8.0,
                                airDate = TimeUtils.isoUtcFromEpochMillis(nowMillis - 14 * DAY_MILLIS).substringBefore("T"),
                                beginAtUtc = TimeUtils.isoUtcFromEpochMillis(nowMillis - 14 * DAY_MILLIS),
                                totalEpisodes = 12,
                                weekday = 1,
                                timeCst = "20:00",
                                timeJst = "21:00",
                                sitesJson = "[]",
                                anilistId = 201L,
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                            ),
                        ),
                    )
                }

            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            val schedules = dao.getAllSchedulesList()
            assertTrue(schedules.any { it.bgmId == 9001L }, "连载中、未播满总集数的网播条目绝不能被剔除")
        }

    @Test
    fun syncAirEvents_includesBgmDataAnimeInTargets_whenUserHasDoingCollections() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val anilist =
                FakeScheduleSnapshotService().apply {
                    mediaSchedules =
                        mapOf(
                            302L to
                                AniListMediaSchedule(
                                    episodes =
                                        listOf(
                                            AniListAiringEpisode(
                                                episode = 1,
                                                airAtEpochSeconds =
                                                    TimeUtils.cstWeekStartEpochMillis(nowMillis) / 1000 + 12 * 3600,
                                            ),
                                        ),
                                ),
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 9101L,
                                title = "正在追的官方番",
                                titleCn = "正在追的官方番",
                                coverUrl = "https://example.com/cover1.jpg",
                                ratingScore = 8.0,
                                airDate = "2026-08-01",
                                weekday = 1,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                                anilistId = 301L,
                            ),
                            AirScheduleEntity(
                                bgmId = 9102L,
                                title = "未在追但已同步过封面的网播番",
                                titleCn = "未在追但已同步过封面的网播番",
                                coverUrl = "https://example.com/cover2.jpg",
                                ratingScore = 7.5,
                                airDate = "2026-08-01",
                                weekday = 1,
                                timeCst = "12:00",
                                timeJst = "13:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                                anilistId = 302L,
                            ),
                        ),
                    )
                }

            val collectionRepo =
                FakeCollectionRepository().apply {
                    sendCollection(
                        UserCollection(
                            subjectId = 9101L,
                            type = CollectionType.DOING.value,
                        ),
                    )
                }

            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                    collectionRepository = collectionRepo,
                )

            val result = repo.syncBangumiData()

            assertIs<AppResult.Success<Unit>>(result)
            // 未在追但属于 SOURCE_BGM_DATA 的条目依然被快照逐话真值覆盖（events 落库），不会因不在追而饿死
            assertTrue(
                airEventDao.getAllAirEvents().any { it.subjectId == 9102L },
                "网播条目必须始终被快照逐话真值覆盖",
            )
        }

    @Test
    fun refreshAllSchedules_holdsIntermediateEmissionsUntilAllSourcesFetched() =
        runTest {
            val weekStartSec = TimeUtils.cstWeekStartEpochMillis(TimeUtils.nowEpochMillis()) / 1000
            val snapshotService =
                FakeScheduleSnapshotService().apply {
                    snapshot =
                        ScheduleSnapshotDto(
                            schema = "minibgm-schedule-snapshot/1",
                            generatedAt = "",
                            items =
                                listOf(
                                    ScheduleSnapshotItemDto(
                                        anilistId = 5001L,
                                        bgmId = 2001L,
                                        title = "官方番A",
                                        titleCn = "官方番A",
                                        countryOfOrigin = "JP",
                                        format = "TV",
                                        status = "RELEASING",
                                        coverUrl = "https://example.com/cover.jpg",
                                        isAdult = false,
                                        startYear = 2026,
                                        startMonth = 9,
                                        airDate = "2026-09-16",
                                        sites =
                                            listOf(
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bangumi",
                                                    id = "2001",
                                                ),
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bilibili",
                                                    id = "md2001",
                                                ),
                                            ),
                                        episodes =
                                            listOf(
                                                ScheduleSnapshotEpisodeDto(
                                                    n = 1,
                                                    t = weekStartSec + 12 * 3600,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                }
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 2001L,
                                title = "官方番A",
                                titleCn = "官方番A",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2026-09-16",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = snapshotService,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val emissions = mutableListOf<List<AirSchedule>>()
            val collector =
                launch {
                    repo.getAllSchedulesStream().collect { emissions.add(it) }
                }
            runCurrent()
            val initialEmissions = emissions.size
            assertTrue(initialEmissions >= 1)

            val result = repo.refreshAllSchedules(force = true)
            assertIs<AppResult.Success<Unit>>(result)
            advanceUntilIdle()
            collector.cancel()

            // 关键断言 1：管线期间中间态从未对外发流——闸门放开后只多发一次
            assertEquals(initialEmissions + 1, emissions.size)
            // 关键断言 2（DAO 层）：快照已为 2001 补全 bilibili 播放源
            val stored = dao.getAllSchedulesList()
            assertEquals(1, stored.size)
            assertTrue(stored.any { it.bgmId == 2001L && it.sitesJson.contains("bilibili") })
            // 关键断言 3：对外流与 DAO 最终态一致（不存在更晚的中间态外发）
            assertEquals(emissions.last(), repo.getAllSchedulesStream().first())
        }

    @Test
    fun getAllSchedulesStream_emitsCachedDataImmediatelyEvenIfRefreshPipelineIsAlreadyRunning() =
        runTest {
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 3001L,
                                title = "已有缓存番",
                                titleCn = "已有缓存番",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = FakeScheduleSnapshotService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            // 模拟冷启动：先发起异步刷新（拉起闸门）
            val refreshJob = launch { repo.refreshAllSchedules() }

            // UI 此时才开始订阅 getAllSchedulesStream
            val emissions = mutableListOf<List<AirSchedule>>()
            val collector =
                launch {
                    repo.getAllSchedulesStream().collect { emissions.add(it) }
                }

            // 即使管线仍在异步运行，首帧本地缓存也必须立即直发，绝不等待网络
            runCurrent()
            assertTrue(emissions.isNotEmpty())
            assertEquals(1, emissions.first().size)
            assertEquals(3001L, emissions.first().first().bgmId)

            advanceUntilIdle()
            refreshJob.join()
            collector.cancel()
        }

    @Test
    fun syncBangumiData_resolvesWeeklyAnimeViaSnapshotBgmId_withoutSearch() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val nowSeconds = nowMillis / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 210482L,
                                episode = 2,
                                airAtEpochSeconds = TimeUtils.cstWeekStartEpochMillis(nowSeconds * 1000) / 1000 + 12 * 3600,
                                titleNative = "ジョジョの奇妙な冒険 スティール・ボール・ラン 2nd・3rd STAGE",
                                format = "ONA",
                            ),
                        )
                    bgmIdByAnilistId = mapOf(210482L to 639938L)
                    titleCnByAnilistId = mapOf(210482L to "飙马野郎")
                }
            val apiService = FakeBangumiApiService()
            val dao = FakeAirScheduleDao()
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData()
            assertIs<AppResult.Success<Unit>>(result)

            val schedules = dao.getAllSchedulesList()
            val sbr = schedules.firstOrNull { it.bgmId == 639938L }
            assertNotNull(sbr)
            assertEquals("飙马野郎", sbr.titleCn)
            assertEquals(210482L, sbr.anilistId)
            assertEquals(2, sbr.nextEpisode)
        }

    @Test
    fun syncBangumiData_mapsWeeklyAnimeViaSnapshot() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val snapshotService =
                FakeScheduleSnapshotService().apply {
                    snapshot =
                        ScheduleSnapshotDto(
                            schema = "minibgm-schedule-snapshot/1",
                            generatedAt = "",
                            items =
                                listOf(
                                    ScheduleSnapshotItemDto(
                                        anilistId = 210482L,
                                        bgmId = 639938L,
                                        title = "スティール・ボール・ラン ジョジョの奇妙な冒険 2nd & 3rd STAGE",
                                        titleCn = "飙马野郎 JOJO的奇妙冒险 第二&第三赛段",
                                        countryOfOrigin = "JP",
                                        format = "TV",
                                        status = "RELEASING",
                                        coverUrl = "https://example.com/sbr.jpg",
                                        isAdult = false,
                                        startYear = 2026,
                                        startMonth = 9,
                                        airDate = "2026-09-25",
                                        sites =
                                            listOf(
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "bangumi",
                                                    id = "639938",
                                                ),
                                                com.infinitezerone.minibgm.core.network.ScheduleSnapshotSiteDto(
                                                    site = "netflix",
                                                    id = "82116553",
                                                ),
                                            ),
                                        episodes =
                                            listOf(
                                                ScheduleSnapshotEpisodeDto(
                                                    n = 1,
                                                    t = TimeUtils.cstWeekStartEpochMillis(nowSeconds * 1000) / 1000 + 12 * 3600,
                                                ),
                                            ),
                                    ),
                                ),
                        )
                }
            val dao = FakeAirScheduleDao()
            val mappingDao = FakeAniListMappingDao()
            val repo =
                createRepository(
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistMappingDao = mappingDao,
                    snapshotService = snapshotService,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val sbr = dao.getAllSchedulesList().firstOrNull { it.bgmId == 639938L }
            assertNotNull(sbr)
            assertEquals(210482L, sbr.anilistId)
            assertEquals("飙马野郎 JOJO的奇妙冒险 第二&第三赛段", sbr.titleCn)
            assertTrue(sbr.sitesJson.contains("netflix"))
            // 快照映射落库供后续复用
            assertNotNull(mappingDao.mappings.value.firstOrNull { it.anilistId == 210482L })
        }

    @Test
    fun syncBangumiData_doesNotBind_whenSearchHasNoUniqueCandidate() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 555L,
                                episode = 1,
                                airAtEpochSeconds = nowSeconds + 3600,
                                titleNative = "全く別のアニメ",
                            ),
                        )
                }
            val apiService =
                FakeBangumiApiService().apply {
                    searchResults =
                        listOf(
                            Subject(id = 1L, name = "無関係な作品 A"),
                            Subject(id = 2L, name = "無関係な作品 B"),
                        )
                }
            val dao = FakeAirScheduleDao()
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())
            val stored = dao.getAllSchedulesList()
            // 不得误绑任何候选；映射不到的条目直接丢弃（不生成占位条目）
            assertTrue(stored.none { it.bgmId == 1L || it.bgmId == 2L }, "无唯一候选时不得绑定任何条目")
            assertTrue(stored.none { it.bgmId == -555L }, "映射不到的条目不得入库")
            val visible = repo.getAllSchedulesStream().first()
            assertTrue(visible.none { it.bgmId <= 0 }, "未映射条目不应出现在时刻表流中")
        }

    @Test
    fun syncBangumiData_writesBackAniListIdToExistingLocalEntity() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 210482L,
                                episode = 1,
                                airAtEpochSeconds = TimeUtils.cstWeekStartEpochMillis(nowSeconds * 1000) / 1000 + 12 * 3600,
                                titleNative = "AniList Only Title",
                            ),
                        )
                }
            val apiService = FakeBangumiApiService()
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 639938L,
                                title = "AniList Only Title",
                                titleCn = "AniList Only Title",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2026-08-01",
                                weekday = 5,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val matched = dao.getAllSchedulesList().filter { it.bgmId == 639938L }
            assertEquals(1, matched.size, "已存在的本地条目应回写 anilistId，而不是重复插入")
            assertEquals(210482L, matched.first().anilistId)
        }

    @Test
    fun syncBangumiData_rejectsCrossSeasonContainsMatch() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 210482L,
                                episode = 1,
                                airAtEpochSeconds = TimeUtils.cstWeekStartEpochMillis(nowSeconds * 1000) / 1000 + 12 * 3600,
                                titleNative = "ジョジョの奇妙な冒険 スティール・ボール・ラン 2nd & 3rd STAGE",
                            ),
                        )
                }
            val apiService = FakeBangumiApiService().apply { searchResults = emptyList() }
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 551918L,
                                title = "ジョジョの奇妙な冒険 スティール・ボール・ラン",
                                titleCn = "飙马野郎 第一赛段",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2026-08-01",
                                weekday = 5,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val stored = dao.getAllSchedulesList()
            println(
                "DEBUG-rejectsCross stored=" + stored.map { "bgm=${it.bgmId},src=${it.source},anilist=${it.anilistId},title=${it.title}" },
            )
            // 551918 已不在本周 AniList 名单会被裁剪清掉；无论如何都不得被误绑到 210482
            assertTrue(
                stored.none { it.bgmId == 551918L && it.anilistId != null },
                "基名不得被包含匹配到另一季的周排期条目上",
            )
            assertTrue(stored.none { it.bgmId == -210482L }, "映射不到的条目不得入库为占位")
            assertTrue(
                stored.single().let { it.bgmId == 551918L && it.anilistId == null },
                "fail-open 语义下旧条目保留且不得误绑 anilistId",
            )
        }

    @Test
    fun syncBangumiData_excludesAdultWeeklyEntries() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 999L,
                                episode = 1,
                                airAtEpochSeconds = nowSeconds + 3600,
                                titleNative = "成人向作品",
                                isAdult = true,
                            ),
                        )
                }
            // 即便搜索能命中候选，也不应入库
            val apiService =
                FakeBangumiApiService().apply {
                    searchResults = listOf(Subject(id = 123L, name = "成人向作品", nameCn = "成人向作品"))
                }
            val dao = FakeAirScheduleDao()
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())
            assertTrue(dao.getAllSchedulesList().isEmpty(), "成人向条目不应进入时刻表")
        }

    @Test
    fun syncBangumiData_mapsSeasonSuffixVariants_viaCanonicalTitleMatch() =
        runTest {
            val nowSeconds = TimeUtils.nowEpochMillis() / 1000
            val anilist =
                FakeScheduleSnapshotService().apply {
                    weeklySchedules =
                        listOf(
                            AniListWeeklyScheduleItem(
                                anilistId = 191832L,
                                episode = 1,
                                airAtEpochSeconds = nowSeconds + 3600,
                                titleNative = "时光代理人 第三季",
                            ),
                        )
                }
            // 本地已有条目 1 是第二季（不得误绑），条目 2 用罗马数字 + Part 后缀（应被归一化命中）
            val apiService = FakeBangumiApiService()
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 341311L,
                                title = "时光代理人 第二季",
                                titleCn = "时光代理人 第二季",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2024-01-01",
                                weekday = 1,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                            AirScheduleEntity(
                                bgmId = 555710L,
                                title = "时光代理人 第3季",
                                titleCn = "时光代理人 第3季",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = "2026-08-01",
                                weekday = 1,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val repo =
                createRepository(
                    apiService = apiService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val stored = dao.getAllSchedulesList()
            assertEquals(1, stored.size)
            assertEquals(555710L, stored.first().bgmId)
        }

    @Test
    fun syncBangumiData_appliesSplitCourOffset_replacingRawWeeklyEpisode() =
        runTest {
            val beginMillis = TimeUtils.nowEpochMillis()
            val beginIso = TimeUtils.isoUtcFromEpochMillis(beginMillis)

            val anilist =
                FakeScheduleSnapshotService().apply {
                    schedules =
                        mapOf(
                            111L to
                                (1..13).map { ep ->
                                    AniListAiringEpisode(
                                        episode = ep,
                                        airAtEpochSeconds = (beginMillis - (13 - ep) * 7 * DAY_MILLIS) / 1000,
                                    )
                                },
                        )
                    bgmIdByAnilistId = mapOf(111L to 222L)
                    airDateByAnilistId = mapOf(111L to beginIso.substringBefore("T"))
                    sitesByAnilistId =
                        mapOf(
                            111L to
                                listOf(
                                    ScheduleSnapshotSiteDto(site = "bangumi", id = "222"),
                                    ScheduleSnapshotSiteDto(site = "anilist", id = "111"),
                                ),
                        )
                }
            val dao = FakeAirScheduleDao()
            val repo =
                createRepository(
                    scheduleDao = dao,
                    snapshotService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            assertIs<AppResult.Success<Unit>>(repo.syncBangumiData())

            val entity = dao.getAllSchedulesList().first { it.bgmId == 222L }
            println("DEBUG-splitCour entity=" + entity)
            assertEquals(1, entity.nextEpisode, "拆季偏移应把 AniList 第 13 话还原为 Bangumi 第 1 话")
        }

    @Test
    fun getSchedulesAiringBetween_keepsOnlyWindowEvents_andOrdersByHeat() =
        runTest {
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            airScheduleEntity(id = 899L, title = "名侦探柯南", ratingScore = 7.9),
                            airScheduleEntity(id = 2L, title = "低热度连载", ratingScore = 5.2),
                            airScheduleEntity(id = 3L, title = "窗口外", ratingScore = 9.9),
                        ),
                    )
                }
            val airEventDao =
                FakeAirEventDao().apply {
                    insertAirEvents(
                        listOf(
                            airEventEntity(subjectId = 899L, airAtUtc = "2026-02-10T12:00:00Z"),
                            airEventEntity(subjectId = 2L, airAtUtc = "2026-03-01T12:00:00Z"),
                            // 落在窗口外：不得进入结果
                            airEventEntity(subjectId = 3L, airAtUtc = "2026-06-01T12:00:00Z"),
                        ),
                    )
                }
            val repo = createRepository(scheduleDao = dao, airEventDao = airEventDao)

            val result =
                repo.getSchedulesAiringBetween(
                    fromUtcIso = "2026-01-01T00:00:00Z",
                    toUtcIso = "2026-03-31T23:59:59Z",
                )

            assertEquals(listOf(899L, 2L), result.map { it.bgmId })
            assertEquals("名侦探柯南", result.first().titleCn)
        }

    private fun airScheduleEntity(
        id: Long,
        title: String,
        ratingScore: Double,
    ) = AirScheduleEntity(
        bgmId = id,
        title = title,
        titleCn = title,
        coverUrl = "",
        ratingScore = ratingScore,
        weekday = 1,
        timeCst = "",
        timeJst = "",
        sitesJson = "[]",
    )

    private fun airEventEntity(
        subjectId: Long,
        airAtUtc: String,
    ) = AirEventEntity(
        subjectId = subjectId,
        episode = 1,
        airAtUtc = airAtUtc,
        kind = AirEventKind.ACTUAL,
        source = "anilist",
    )

    /**
     * `isStalePrunableSchedule` 是无依赖的纯谓词，所以能在这里逐分支钉住。
     *
     * 它分支密度很高（CC=26），而 CRAP 门禁只认覆盖率：留在类里当 private 成员时行覆盖率只有
     * 51.9%、CRAP=101.5，是 DANGER。抽成顶层 internal 后由这条表驱动测试补齐覆盖。
     * 每个用例都对着一个具体的判据分支，新增分支时这里应该同步加一行。
     */
    @Test
    fun isStalePrunableSchedule_coversEveryBranch() {
        val now = assertNotNull(TimeUtils.epochMillisOfIso("2026-09-29T12:00:00Z"))
        val weekStart = TimeUtils.cstWeekStartEpochMillis(now)
        val week = 7 * DAY_MILLIS
        val past = weekStart - 30 * DAY_MILLIS
        val recent = weekStart - 3 * DAY_MILLIS
        val future = weekStart + 2 * DAY_MILLIS
        val stale = weekStart - 20 * DAY_MILLIS

        fun iso(millis: Long) = TimeUtils.isoUtcFromEpochMillis(millis)

        fun entity(
            totalEpisodes: Int = 0,
            beginIso: String = "",
            source: String = AirScheduleEntity.SOURCE_BGM_DATA,
        ) = AirScheduleEntity(
            bgmId = 1L,
            title = "t",
            titleCn = "t",
            coverUrl = "",
            ratingScore = 0.0,
            airDate = beginIso,
            weekday = 1,
            timeCst = "",
            timeJst = "",
            sitesJson = "[]",
            totalEpisodes = totalEpisodes,
            source = source,
        )

        fun event(airAtUtc: String) =
            AirEventEntity(
                subjectId = 1L,
                episode = 1,
                airAtUtc = airAtUtc,
                kind = AirEventKind.ACTUAL,
                source = "anilist",
            )

        data class Case(
            val name: String,
            val expected: Boolean,
            val entity: AirScheduleEntity,
            val events: List<AirEventEntity>,
        )

        val cases =
            listOf(
                // 非 bgm_data 来源一律不判——喂一个"否则会判 true"的输入，证明是 source 短路挡住的
                Case(
                    "official 来源不判",
                    false,
                    entity(1, iso(past), AirScheduleEntity.SOURCE_OFFICIAL),
                    emptyList(),
                ),
                Case("有本周/未来事件 → 不是僵尸", false, entity(1, iso(past)), listOf(event(iso(future)))),
                // 事件时刻解析不出来时按 0 处理，不该被当成"未来事件"
                Case("事件时刻无法解析按 0 处理", true, entity(1, iso(past)), listOf(event("not-a-date"))),
                Case("事件非空 + 单集 → 已播完", true, entity(1, iso(past)), listOf(event(iso(past)))),
                Case(
                    "事件数已达总集数",
                    true,
                    entity(3, iso(past)),
                    listOf(event(iso(past)), event(iso(past)), event(iso(past))),
                ),
                Case("未播完但放送周期已结束", true, entity(12, iso(weekStart - 20 * week)), listOf(event(iso(past)))),
                Case("未播完且放送周期未结束", false, entity(12, iso(weekStart - 2 * week)), listOf(event(iso(past)))),
                Case("未播完且开播时刻未知", false, entity(12, ""), listOf(event(iso(past)))),
                Case("总集数未知 + 最后事件很旧", true, entity(0, iso(past)), listOf(event(iso(stale)))),
                Case("总集数未知 + 最后事件不算旧", false, entity(0, iso(past)), listOf(event(iso(recent)))),
                Case("无事件 + 单集 + 开播未知", true, entity(1, ""), emptyList()),
                Case("无事件 + 单集 + 已开播", true, entity(1, iso(past)), emptyList()),
                Case("无事件 + 单集 + 尚未开播", false, entity(1, iso(future)), emptyList()),
                Case("无事件 + 多集 + 周期已过", true, entity(12, iso(weekStart - 20 * week)), emptyList()),
                Case("无事件 + 多集 + 周期未过", false, entity(12, iso(weekStart - 2 * week)), emptyList()),
                Case("无事件 + 多集 + 开播未知", false, entity(12, ""), emptyList()),
                Case("无事件 + 集数未知 + 开播未知", false, entity(0, ""), emptyList()),
                Case("无事件 + 集数未知 + 开播很久以前", true, entity(0, iso(stale)), emptyList()),
                Case("无事件 + 集数未知 + 开播较近", false, entity(0, iso(recent)), emptyList()),
            )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                isStalePrunableSchedule(case.entity, case.events, now),
                "用例「${case.name}」",
            )
        }
    }

    @Test
    fun syncAirEvents_retainsUpcomingEpisodesInSeasonWindow_andPreservesAdultMetadata() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val futureAirSeconds = (nowMillis + 25 * DAY_MILLIS) / 1000
            val snapshotDto =
                ScheduleSnapshotDto(
                    schema = "minibgm-schedule-snapshot/1",
                    generatedAt = TimeUtils.isoUtcFromEpochMillis(nowMillis),
                    items =
                        listOf(
                            ScheduleSnapshotItemDto(
                                anilistId = 196750L,
                                bgmId = 575204L,
                                title = "シスターブリーダー",
                                titleCn = "姐妹调教饲育者",
                                format = "OVA",
                                isAdult = true,
                                airDate = "2025-09-01",
                                episodes =
                                    listOf(
                                        ScheduleSnapshotEpisodeDto(n = 5, t = futureAirSeconds),
                                    ),
                            ),
                        ),
                )

            val snapshotService =
                FakeScheduleSnapshotService().apply {
                    customSnapshot = snapshotDto
                }

            val airScheduleDao = FakeAirScheduleDao()
            val airEventDao = FakeAirEventDao()
            val anilistMappingDao = FakeAniListMappingDao()

            val repo =
                createRepository(
                    scheduleDao = airScheduleDao,
                    airEventDao = airEventDao,
                    anilistMappingDao = anilistMappingDao,
                    snapshotService = snapshotService,
                )

            val result = repo.syncBangumiData()
            assertIs<AppResult.Success<*>>(result)

            val entities = airScheduleDao.getAllSchedulesList()
            val sisterBreeder = entities.firstOrNull { it.bgmId == 575204L }
            assertNotNull(sisterBreeder, "第 25 天发售的 OVA 应被完整收录于时刻表")
            assertEquals("シスターブリーダー", sisterBreeder.title)
            assertEquals("姐妹调教饲育者", sisterBreeder.titleCn)
            assertEquals(5, sisterBreeder.nextEpisode)
            assertTrue(sisterBreeder.broadcastRule.contains("adult=true"))
            assertTrue(sisterBreeder.broadcastRule.contains("format=OVA"))

            val model = sisterBreeder.toModel(Json { ignoreUnknownKeys = true })
            assertTrue(model.isAdult)
            assertEquals("OVA", model.format)

            val events = airEventDao.getAllAirEvents()
            assertTrue(events.any { it.subjectId == 575204L && it.episode == 5 })
        }
}
