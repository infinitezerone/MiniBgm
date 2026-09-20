package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.BangumiDataItem
import com.infinitezerone.minibgm.core.model.BangumiDataSite
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.network.AniListAiringEpisode
import com.infinitezerone.minibgm.core.network.AniListMediaSchedule
import com.infinitezerone.minibgm.core.network.AniListService
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiDataResult
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.BilibiliAiringEpisode
import com.infinitezerone.minibgm.core.network.BilibiliService
import com.infinitezerone.minibgm.core.network.model.CalendarDayResponse
import com.infinitezerone.minibgm.core.network.model.CalendarWeekday
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

        override suspend fun deleteOfficialSchedulesNotIn(keepIds: List<Long>) {
            schedulesFlow.value =
                schedulesFlow.value.filter { it.bgmId in keepIds || it.source != "official" }
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

        override suspend fun getUpcomingEvents(
            subjectIds: List<Long>,
            fromIso: String,
            toIso: String,
        ): List<AirEventEntity> =
            events.value.filter {
                it.subjectId in subjectIds.toSet() && it.airAtUtc >= fromIso && it.airAtUtc <= toIso
            }
    }

    private class FakeAniListService : AniListService {
        var schedules: Map<Long, List<AniListAiringEpisode>> = emptyMap()
        var mediaSchedules: Map<Long, AniListMediaSchedule> = emptyMap()
        var requestedIds: List<Long> = emptyList()

        override suspend fun getMediaSchedules(anilistIds: List<Long>): Map<Long, AniListMediaSchedule> {
            requestedIds = anilistIds
            val fromMedia = mediaSchedules.filterKeys { it in anilistIds.toSet() }
            val fromLegacy =
                schedules.filterKeys { it in anilistIds.toSet() }.mapValues {
                    AniListMediaSchedule(episodes = it.value)
                }
            return fromLegacy + fromMedia
        }

        override suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>> {
            requestedIds = anilistIds
            return getMediaSchedules(anilistIds).mapValues { it.value.episodes }
        }
    }

    private class FakeBilibiliService : BilibiliService {
        var episodesBySiteId: Map<String, List<BilibiliAiringEpisode>> = emptyMap()
        val requestedSiteIds: MutableList<String> = mutableListOf()

        override suspend fun getAiringEpisodes(bilibiliSiteId: String): List<BilibiliAiringEpisode> {
            requestedSiteIds += bilibiliSiteId
            return episodesBySiteId[bilibiliSiteId].orEmpty()
        }
    }

    private class FakeBangumiApiService : BangumiApiService {
        var calendarDays: List<CalendarDayResponse> = emptyList()
        var subjects: Map<Long, Subject> = emptyMap()

        override suspend fun getCalendar(): List<CalendarDayResponse> = calendarDays

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

        override suspend fun searchSubjects(
            keyword: String,
            type: Int,
            limit: Int,
            offset: Int,
        ): SearchSubjectResponse = error("Not implemented")

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
        ) = error("Not implemented")

        override suspend fun updateEpisodeStatus(
            subjectId: Long,
            episodeId: Long,
            type: Int,
        ) = error("Not implemented")
    }

    private class FakeBangumiDataService : BangumiDataService {
        var dataResult: BangumiDataResult = BangumiDataResult.NotModified
        var calledEtag: String? = null
        var callCount: Int = 0

        override suspend fun getBangumiData(etag: String?): BangumiDataResult {
            callCount++
            calledEtag = etag
            return dataResult
        }
    }

    private fun createRepository(
        apiService: FakeBangumiApiService = FakeBangumiApiService(),
        dataService: FakeBangumiDataService = FakeBangumiDataService(),
        scheduleDao: FakeAirScheduleDao = FakeAirScheduleDao(),
        airEventDao: FakeAirEventDao = FakeAirEventDao(),
        anilistService: FakeAniListService = FakeAniListService(),
        bilibiliService: FakeBilibiliService = FakeBilibiliService(),
        userPreferences: com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource = createTestUserPreferencesDataSource(),
        collectionRepository: CollectionRepository? = null,
    ) = ScheduleRepositoryImpl(
        apiService = apiService,
        dataService = dataService,
        scheduleDao = scheduleDao,
        airEventDao = airEventDao,
        anilistService = anilistService,
        bilibiliService = bilibiliService,
        userPreferences = userPreferences,
        collectionRepository = collectionRepository,
    )

    @Test
    fun refreshSchedules_fetchesCalendarWithoutTouchingCDN() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items =
                                    listOf(
                                        Subject(
                                            id = 1001L,
                                            name = "无职转生",
                                            nameCn = "无职转生 第三季",
                                        ),
                                    ),
                            ),
                        )
                }
            val dataService = FakeBangumiDataService()
            val dao = FakeAirScheduleDao()
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.refreshSchedules()

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(0, dataService.callCount) // 0 次 CDN 请求
            val stored = dao.getAllSchedulesList()
            assertEquals(1, stored.size)
            assertEquals(1001L, stored[0].bgmId)
            assertEquals(7, stored[0].weekday)
            assertEquals("无职转生 第三季", stored[0].titleCn)
        }

    @Test
    fun refreshSchedules_preservesWebOnlyRows_notInOfficialCalendar() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items =
                                    listOf(
                                        Subject(id = 1001L, name = "无职转生", nameCn = "无职转生 第三季"),
                                    ),
                            ),
                        )
                }
            val dataService = FakeBangumiDataService()
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 1001L,
                                title = "无职转生",
                                titleCn = "无职转生 第三季",
                                coverUrl = "",
                                ratingScore = 8.5,
                                airDate = "",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                            // 官方日历不收录的网络独播番（bgm-data 合并插入的行）
                            AirScheduleEntity(
                                bgmId = 633836L,
                                title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                titleCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                coverUrl = "",
                                ratingScore = 0.0,
                                airDate = TimeUtils.formatEpochSecondsToDate((TimeUtils.nowEpochMillis() - 10 * DAY_MILLIS) / 1000),
                                beginAtUtc = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis() - 10 * DAY_MILLIS),
                                weekday = 3,
                                timeCst = "22:00",
                                timeJst = "23:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_BGM_DATA,
                            ),
                        ),
                    )
                }
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.refreshSchedules()

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            assertEquals(2, stored.size) // 网播番行必须保留
            assertTrue(stored.any { it.bgmId == 633836L })
        }

    @Test
    fun syncBangumiData_updatesRoomOnSuccess() =
        runTest {
            val apiService = FakeBangumiApiService()
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "无职转生",
                                        titleTranslate = mapOf("zh-Hans" to listOf("无职转生 第三季")),
                                        begin = "2026-07-05T15:00:00.000Z",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "1001"),
                                                BangumiDataSite(site = "bilibili", id = "md12345"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"etag-999\"",
                        )
                }
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
                                airDate = "",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]",
                            ),
                        ),
                    )
                }
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData(force = false)

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, dataService.callCount)
            assertEquals("W/\"etag-999\"", userPrefs.userPreferences.first().bangumiDataEtag)
            assertTrue(userPrefs.userPreferences.first().bangumiDataLastSyncTimestamp > 0L)

            val updated = dao.getAllSchedulesList().first { it.bgmId == 1001L }
            assertEquals("无职转生", updated.titleCn)
            assertTrue(updated.sitesJson.contains("哔哩哔哩"))
            assertEquals("", updated.timeCst)
        }

    @Test
    fun syncBangumiData_populatesCalendarFirst_whenDaoIsEmpty() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items =
                                    listOf(
                                        Subject(
                                            id = 1001L,
                                            name = "无职转生",
                                            nameCn = "无职转生",
                                        ),
                                    ),
                            ),
                        )
                }
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "无职转生",
                                        titleTranslate = mapOf("zh-Hans" to listOf("无职转生 第三季")),
                                        begin = "2026-07-05T15:00:00.000Z",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "1001"),
                                                BangumiDataSite(site = "bilibili", id = "md12345"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"etag-coldstart\"",
                        )
                }
            val dao = FakeAirScheduleDao() // 完全为空
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData(force = false)

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            assertEquals(1, stored.size)
            val item = stored.first()
            assertEquals(1001L, item.bgmId)
            assertTrue(item.sitesJson.contains("哔哩哔哩"))
            assertEquals("", item.timeCst)
            assertEquals("W/\"etag-coldstart\"", userPrefs.userPreferences.first().bangumiDataEtag)
        }

    @Test
    fun syncBangumiData_forcesFetch_whenExistingEntitiesAllHaveEmptySites() =
        runTest {
            val apiService = FakeBangumiApiService()
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "无职转生",
                                        titleTranslate = mapOf("zh-Hans" to listOf("无职转生 第三季")),
                                        begin = "2026-07-05T15:00:00.000Z",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "1001"),
                                                BangumiDataSite(site = "bilibili", id = "md12345"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"etag-forced\"",
                        )
                }
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
                                airDate = "",
                                weekday = 7,
                                timeCst = "",
                                timeJst = "",
                                sitesJson = "[]", // 播放源全部为空
                            ),
                        ),
                    )
                }
            val userPrefs = createTestUserPreferencesDataSource()
            userPrefs.setBangumiDataEtag("W/\"old-stale-etag\"")

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            // force = false，但由于本地条目 sitesJson 均为空，应自动强制使用空 ETag 拉取避免 304 死锁
            val result = repo.syncBangumiData(force = false)

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals("", dataService.calledEtag) // 强制使用空 ETag
            val updated = dao.getAllSchedulesList().first { it.bgmId == 1001L }
            assertTrue(updated.sitesJson.contains("哔哩哔哩"))
            assertEquals("W/\"etag-forced\"", userPrefs.userPreferences.first().bangumiDataEtag)
        }

    @Test
    fun syncBangumiData_insertsWebOnlyShow_missingFromOfficialCalendar() =
        runTest {
            // Re:Zero 夺还篇场景：官方日历只收录 1001；
            // bgm-data 里存在官方没有的网播番 633836（10 天前开播，带 anilist 映射与周播规则）
            val beginMillis = TimeUtils.nowEpochMillis() - 10 * DAY_MILLIS
            val beginIso = TimeUtils.isoUtcFromEpochMillis(beginMillis)

            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items = listOf(Subject(id = 1001L, name = "无职转生", nameCn = "无职转生 第三季")),
                            ),
                        )
                }
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "無職転生 III",
                                        titleTranslate = mapOf("zh-Hans" to listOf("无职转生 第三季")),
                                        begin = beginIso,
                                        sites = listOf(BangumiDataSite(site = "bangumi", id = "1001")),
                                    ),
                                    BangumiDataItem(
                                        title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                        titleTranslate = mapOf("zh-Hans" to listOf("Re：从零开始的异世界生活 第四季 夺还篇")),
                                        begin = beginIso,
                                        broadcast = "R/$beginIso/P7D",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "633836"),
                                                BangumiDataSite(site = "anilist", id = "189046"),
                                                BangumiDataSite(site = "gamer", id = "144453"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"etag-webonly\"",
                        )
                }
            val dao = FakeAirScheduleDao()
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            assertEquals(2, stored.size)
            val webOnly = stored.first { it.bgmId == 633836L }
            assertEquals("Re：从零开始的异世界生活 第四季 夺还篇", webOnly.titleCn)
            // 周几由 broadcast 规则起点（与应用时间表一致采用 CST 放送时区）推导
            assertEquals(TimeUtils.cstWeekdayOfEpoch(beginMillis), webOnly.weekday)
            assertEquals(189046L, webOnly.anilistId)
            assertEquals("", webOnly.broadcastRule)
            assertEquals(AirScheduleEntity.SOURCE_BGM_DATA, webOnly.source)
            assertTrue(webOnly.sitesJson.contains("巴哈姆特"))
        }

    @Test
    fun syncBangumiData_enrichesMissingMetadata_forWebOnlyShow() =
        runTest {
            val beginMillis = TimeUtils.nowEpochMillis() - 5 * DAY_MILLIS
            val beginIso = TimeUtils.isoUtcFromEpochMillis(beginMillis)

            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items = listOf(Subject(id = 1001L, name = "常规番", nameCn = "常规番")),
                            ),
                        )
                    subjects =
                        mapOf(
                            633836L to
                                Subject(
                                    id = 633836L,
                                    name = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                    nameCn = "Re：从零开始的异世界生活 第四季 夺还篇",
                                    images =
                                        SubjectImages(
                                            common = "https://lain.bgm.tv/pic/cover/c/sample_rezero.jpg",
                                        ),
                                    rating = Rating(score = 8.6),
                                    eps = 16,
                                ),
                        )
                }
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "Re:ゼロから始める異世界生活 4th season 奪還編",
                                        titleTranslate = mapOf("zh-Hans" to listOf("Re：从零开始的异世界生活 第四季 夺还篇")),
                                        begin = beginIso,
                                        broadcast = "R/$beginIso/P7D",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "633836"),
                                                BangumiDataSite(site = "anilist", id = "189046"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"etag-enrich\"",
                        )
                }
            val dao = FakeAirScheduleDao()
            val userPrefs = createTestUserPreferencesDataSource()

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = userPrefs,
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList()
            val webOnly = stored.first { it.bgmId == 633836L }
            assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample_rezero.jpg", webOnly.coverUrl)
            assertEquals(8.6, webOnly.ratingScore)
            assertEquals(16, webOnly.totalEpisodes)
            assertEquals("Re：从零开始的异世界生活 第四季 夺还篇", webOnly.titleCn)
        }

    @Test
    fun syncAirEvents_derivesAnilistSplitCourOffset_andReconciles() =
        runTest {
            // bgm 拆季条目：begin = 8/12；AniList 是整季 19 话中的 11/12/13 话（8/5、8/12、8/19）
            // 偏移自推导：begin 就近对齐第 12 话 → offset = 11 → bgm 第 1/2 话
            val beginIso = "2026-08-12T14:00:00Z"
            val beginMillis = TimeUtils.epochMillisOfIso(beginIso)!!
            val anilist =
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(listOf(189046L), anilist.requestedIds)
            // 逐话事件已按偏移换算成 bgm 话数（第 1/2 话）
            val storedEvents = airEventDao.getAllAirEvents()
            assertEquals(setOf(1, 2), storedEvents.map { it.episode }.toSet())
            // 仲裁回写：最近事件（第 2 话）驱动 weekday 分桶与话数
            val updated = dao.getAllSchedulesList().single()
            assertEquals(2, updated.nextEpisode)
            assertEquals(AirEventKind.ACTUAL, updated.nextEpisodeKind)
            assertEquals(TimeUtils.cstWeekdayOfEpoch(TimeUtils.epochMillisOfIso("2026-08-19T14:00:00Z")!!), updated.weekday)
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val storedEvents = airEventDao.getAllAirEvents()
            assertTrue(storedEvents.isEmpty())
            val updated = dao.getAllSchedulesList().single()
            assertEquals("", updated.nextEpisodeKind)
            assertEquals(0, updated.nextEpisode)
        }

    @Test
    fun syncAirEvents_bypassesBilibiliAndReliesOnAniList() =
        runTest {
            val bilibiliService = FakeBilibiliService()
            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        listOf(
                            AirScheduleEntity(
                                bgmId = 456789L,
                                title = "凡人修仙传",
                                titleCn = "凡人修仙传",
                                coverUrl = "",
                                ratingScore = 8.0,
                                airDate = "2020-07-25",
                                beginAtUtc = "2020-07-25T03:00:00Z",
                                weekday = 6,
                                timeCst = "11:00",
                                timeJst = "12:00",
                                sitesJson = """[{"site":"bilibili","id":"4315482"}]""",
                            ),
                        ),
                    )
                }
            val airEventDao = FakeAirEventDao()
            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    bilibiliService = bilibiliService,
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            // Bilibili API is no longer invoked, preventing slow serial network loops
            assertTrue(bilibiliService.requestedSiteIds.isEmpty())
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
            val airedEarlierThisWeek = nowSeconds - 10 * 3600 // 10 小时前（本周内已播）
            val nextWeekAiring = nowSeconds + 6 * 86400 + 14 * 3600 // 下周待播
            val anilist =
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = FakeAirScheduleDao(),
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = airEventDao,
                    anilistService = FakeAniListService(),
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
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
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

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
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val schedules = dao.getAllSchedulesList()
            assertTrue(schedules.any { it.bgmId == 6001L }, "活跃网播条目应保留")
            assertTrue(schedules.none { it.bgmId == 6002L }, "已完结的僵尸条目应被剔除")
        }

    @Test
    fun refreshSchedules_mergeScheduleEntity_preservesExistingCover_whenCalendarHasBlankCover() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items =
                                    listOf(
                                        Subject(
                                            id = 7001L,
                                            name = "官方番",
                                            nameCn = "官方番",
                                            images = null, // 官方刷新返回空图片
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
                                bgmId = 7001L,
                                title = "官方番",
                                titleCn = "官方番",
                                coverUrl = "https://example.com/existing_cover.jpg",
                                ratingScore = 8.0,
                                airDate = "2026-07-01",
                                weekday = 7,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                            ),
                        ),
                    )
                }

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.refreshSchedules()

            assertIs<AppResult.Success<Unit>>(result)
            val stored = dao.getAllSchedulesList().single { it.bgmId == 7001L }
            assertEquals("https://example.com/existing_cover.jpg", stored.coverUrl)
        }

    @Test
    fun syncBangumiData_enrichMissingMetadata_prioritizesBlankCoversAheadOfMissingEpisodes() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val beginIso = TimeUtils.isoUtcFromEpochMillis(nowMillis)

            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items = (1L..5L).map { id -> Subject(id = id, name = "官方番$id", nameCn = "官方番$id") },
                            ),
                        )
                    // API 只配置了缺失封面的第 6 个条目的元数据
                    subjects =
                        mapOf(
                            6L to
                                Subject(
                                    id = 6L,
                                    name = "无封面网播番",
                                    nameCn = "无封面网播番",
                                    images = SubjectImages(common = "https://lain.bgm.tv/pic/cover/c/sample_6.jpg"),
                                    eps = 12,
                                ),
                        )
                }

            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "无封面网播番",
                                        titleTranslate = mapOf("zh-Hans" to listOf("无封面网播番")),
                                        begin = beginIso,
                                        sites = listOf(BangumiDataSite(site = "bangumi", id = "6")),
                                    ),
                                ),
                            etag = "W/\"etag-test\"",
                        )
                }

            val dao =
                FakeAirScheduleDao().apply {
                    insertSchedules(
                        // 插入 5 部已有封面但 totalEpisodes == 0 的官方条目
                        (1L..5L).map { id ->
                            AirScheduleEntity(
                                bgmId = id,
                                title = "官方番$id",
                                titleCn = "官方番$id",
                                coverUrl = "https://example.com/has_cover_$id.jpg",
                                ratingScore = 8.0,
                                airDate = "2026-07-01",
                                weekday = 7,
                                timeCst = "10:00",
                                timeJst = "11:00",
                                sitesJson = "[]",
                                totalEpisodes = 0,
                                source = AirScheduleEntity.SOURCE_OFFICIAL,
                            )
                        },
                    )
                }

            val repo =
                createRepository(
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val enriched = dao.getAllSchedulesList().first { it.bgmId == 6L }
            // 确认条目 6 的封面成功被回补，未被前 5 个 totalEpisodes == 0 的官方条目饿死
            assertEquals("https://lain.bgm.tv/r/400/pic/cover/l/sample_6.jpg", enriched.coverUrl)
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
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
                FakeAniListService().apply {
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            val schedules = dao.getAllSchedulesList()
            assertTrue(schedules.any { it.bgmId == 9001L }, "连载中、未播满总集数的网播条目绝不能被剔除")
        }

    @Test
    fun syncAirEvents_includesBgmDataAnimeInTargets_whenUserHasDoingCollections() =
        runTest {
            val nowMillis = TimeUtils.nowEpochMillis()
            val anilist = FakeAniListService()

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

            val repo =
                createRepository(
                    apiService = FakeBangumiApiService(),
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = anilist,
                    userPreferences = createTestUserPreferencesDataSource(),
                    collectionRepository = collectionRepo,
                )

            val result = repo.syncBangumiData(force = true)

            assertIs<AppResult.Success<Unit>>(result)
            // 验证未在追但属于 SOURCE_BGM_DATA 的条目依然包含在 targets 中请求 AniList，不会因已有封面且不在追而发生排期饿死与事件不同步
            assertTrue(anilist.requestedIds.contains(302L), "网播条目必须始终被纳入 AniList 逐话排期校验 targets")
        }

    @Test
    fun refreshAllSchedules_holdsIntermediateEmissionsUntilAllSourcesFetched() =
        runTest {
            // 数据源 1（官方日历）：网播番 B（id=2002）
            val apiService =
                FakeBangumiApiService().apply {
                    calendarDays =
                        listOf(
                            CalendarDayResponse(
                                weekday = CalendarWeekday(en = "Sun", cn = "星期日", ja = "日", id = 7),
                                items =
                                    listOf(
                                        Subject(
                                            id = 2002L,
                                            name = "网播番B",
                                            nameCn = "网播番B",
                                        ),
                                    ),
                            ),
                        )
                }
            // 数据源 2（bangumi-data）：为官方番 A（id=2001）补全播放源
            val dataService =
                FakeBangumiDataService().apply {
                    dataResult =
                        BangumiDataResult.Success(
                            items =
                                listOf(
                                    BangumiDataItem(
                                        title = "官方番A",
                                        titleTranslate = mapOf("zh-Hans" to listOf("官方番A")),
                                        begin = "2026-09-16T15:00:00.000Z",
                                        sites =
                                            listOf(
                                                BangumiDataSite(site = "bangumi", id = "2001"),
                                                BangumiDataSite(site = "bilibili", id = "md2001"),
                                            ),
                                    ),
                                ),
                            etag = "W/\"gate-1\"",
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
                    apiService = apiService,
                    dataService = dataService,
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
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

            val result = repo.refreshAllSchedules()
            assertIs<AppResult.Success<Unit>>(result)
            advanceUntilIdle()
            collector.cancel()

            // 关键断言 1：管线期间中间态从未对外发流——闸门放开后只多发一次
            assertEquals(initialEmissions + 1, emissions.size)
            // 关键断言 2（DAO 层）：两个数据源都已合并——官方行 2002 已插入，
            // bangumi-data 已为 2001 补全 bilibili 播放源
            val stored = dao.getAllSchedulesList()
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.bgmId == 2001L && it.sitesJson.contains("bilibili") })
            assertTrue(stored.any { it.bgmId == 2002L })
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
                    dataService = FakeBangumiDataService(),
                    scheduleDao = dao,
                    airEventDao = FakeAirEventDao(),
                    anilistService = FakeAniListService(),
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
}
