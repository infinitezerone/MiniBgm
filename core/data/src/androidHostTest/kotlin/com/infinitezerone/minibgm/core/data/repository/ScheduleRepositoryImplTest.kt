package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.database.dao.AirEventDao
import com.infinitezerone.minibgm.core.database.dao.AirScheduleDao
import com.infinitezerone.minibgm.core.database.entity.AirEventEntity
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.BangumiDataItem
import com.infinitezerone.minibgm.core.model.BangumiDataSite
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.network.AniListAiringEpisode
import com.infinitezerone.minibgm.core.network.AniListService
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BangumiDataResult
import com.infinitezerone.minibgm.core.network.BangumiDataService
import com.infinitezerone.minibgm.core.network.model.CalendarDayResponse
import com.infinitezerone.minibgm.core.network.model.CalendarWeekday
import com.infinitezerone.minibgm.core.network.model.EpisodePageResponse
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.SearchSubjectResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import com.infinitezerone.minibgm.core.testing.datastore.createTestUserPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

        override suspend fun deleteStaleBgmDataSchedules(isoUtc: String) {
            val cutoff = TimeUtils.epochMillisOfIso(isoUtc) ?: return
            schedulesFlow.value =
                schedulesFlow.value.filter {
                    it.source != "bgm_data" || (TimeUtils.epochMillisOfIso(it.beginUtc) ?: 0L) >= cutoff
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
                    .associateBy { Triple(it.subjectId, it.episode, it.kind + "@" + it.source) }
                    .toMutableMap()
            events.forEach { current[Triple(it.subjectId, it.episode, it.kind + "@" + it.source)] = it }
            this.events.value = current.values.toList()
        }

        override suspend fun getAllAirEvents(): List<AirEventEntity> = events.value

        override suspend fun deleteStalePredictedEvents(isoUtc: String) {
            val cutoff = TimeUtils.epochMillisOfIso(isoUtc) ?: return
            events.value =
                events.value.filter { it.kind != AirEventKind.PREDICTED || (TimeUtils.epochMillisOfIso(it.airAtUtc) ?: 0L) >= cutoff }
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
        var requestedIds: List<Long> = emptyList()

        override suspend fun getAiringSchedules(anilistIds: List<Long>): Map<Long, List<AniListAiringEpisode>> {
            requestedIds = anilistIds
            return schedules.filterKeys { it in anilistIds.toSet() }
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
        apiService: FakeBangumiApiService,
        dataService: FakeBangumiDataService,
        scheduleDao: FakeAirScheduleDao,
        airEventDao: FakeAirEventDao,
        anilistService: FakeAniListService,
        userPreferences: com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource,
    ) = ScheduleRepositoryImpl(
        apiService = apiService,
        dataService = dataService,
        scheduleDao = scheduleDao,
        airEventDao = airEventDao,
        anilistService = anilistService,
        userPreferences = userPreferences,
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
                                beginUtc = "",
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
                                beginUtc = TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis() - 10 * DAY_MILLIS),
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
                                beginUtc = "",
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
            assertTrue(updated.sitesJson.contains("哔哩哔哩"))
            assertEquals("23:00", updated.timeCst)
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
            assertEquals("23:00", item.timeCst)
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
                                beginUtc = "",
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
            // 周几由 broadcast 规则起点（日本时区）推导
            assertEquals(TimeUtils.jstWeekdayOfEpoch(beginMillis), webOnly.weekday)
            assertEquals(189046L, webOnly.anilistId)
            assertTrue(webOnly.broadcastRule.contains("P7D"))
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
            assertEquals("https://lain.bgm.tv/pic/cover/c/sample_rezero.jpg", webOnly.coverUrl)
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
                                beginUtc = beginIso,
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
    fun syncAirEvents_marksUncoveredSubjectsAsPredicted() =
        runTest {
            // 无 anilist 映射的条目：由 broadcast 规则生成 predicted 事件
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
                                beginUtc = ruleStartIso,
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
            assertTrue(storedEvents.isNotEmpty())
            assertTrue(storedEvents.all { it.kind == AirEventKind.PREDICTED })
            val updated = dao.getAllSchedulesList().single()
            assertEquals(AirEventKind.PREDICTED, updated.nextEpisodeKind)
            assertTrue(updated.nextEpisode > 0)
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
                                beginUtc = "",
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
                                beginUtc = "",
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
                                beginUtc = "",
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
                                beginUtc = "",
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
}
