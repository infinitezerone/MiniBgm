package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.database.dao.EpisodeDao
import com.infinitezerone.minibgm.core.database.dao.SubjectDao
import com.infinitezerone.minibgm.core.database.entity.EpisodeEntity
import com.infinitezerone.minibgm.core.database.entity.SubjectEntity
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.model.CalendarDayResponse
import com.infinitezerone.minibgm.core.network.model.EpisodePageResponse
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.SearchSubjectResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubjectRepositoryImplTest {
    private class FakeSubjectDao : SubjectDao {
        val subjectsFlow = MutableStateFlow<Map<Long, SubjectEntity>>(emptyMap())

        override fun getSubjectById(id: Long): Flow<SubjectEntity?> = subjectsFlow.map { it[id] }

        override suspend fun insertSubjects(subjects: List<SubjectEntity>) {
            val map = subjectsFlow.value.toMutableMap()
            subjects.forEach { map[it.id] = it }
            subjectsFlow.value = map
        }

        override suspend fun insertSubject(subject: SubjectEntity) {
            val map = subjectsFlow.value.toMutableMap()
            map[subject.id] = subject
            subjectsFlow.value = map
        }
    }

    private class FakeEpisodeDao : EpisodeDao {
        private val episodesFlow = MutableStateFlow<Map<Long, List<EpisodeEntity>>>(emptyMap())

        override fun getEpisodesBySubjectId(subjectId: Long): Flow<List<EpisodeEntity>> = episodesFlow.map { it[subjectId].orEmpty() }

        override suspend fun insertEpisodes(episodes: List<EpisodeEntity>) {
            val map = episodesFlow.value.toMutableMap()
            val subjectId = episodes.firstOrNull()?.subjectId ?: return
            map[subjectId] = episodes
            episodesFlow.value = map
        }

        fun getStoredEpisodes(subjectId: Long): List<EpisodeEntity> = episodesFlow.value[subjectId].orEmpty()
    }

    private class FakeBangumiApiService : BangumiApiService {
        var episodesResponse: EpisodePageResponse = EpisodePageResponse(total = 0, data = emptyList())
        var subjectResponse: Subject? = null
        var shouldThrow: Boolean = false
        var cancellationToThrow: Boolean = false

        override suspend fun getCalendar(): List<CalendarDayResponse> = error("Not needed")

        override suspend fun getSubject(id: Long): Subject =
            if (cancellationToThrow) {
                throw kotlinx.coroutines.CancellationException("Job cancelled")
            } else if (shouldThrow) {
                error("Network error")
            } else {
                subjectResponse ?: Subject(
                    id = id,
                    name = "葬送のフリーレン",
                    nameCn = "葬送的芙莉莲",
                )
            }

        override suspend fun getSubjectCharacters(id: Long): List<SubjectCharacter> = emptyList()

        override suspend fun getCharacter(id: Long): com.infinitezerone.minibgm.core.model.CharacterDetail =
            if (shouldThrow) {
                error("Network error")
            } else {
                com.infinitezerone.minibgm.core.model
                    .CharacterDetail(id = id, name = "フリーレン")
            }

        override suspend fun getCharacterSubjects(id: Long): List<com.infinitezerone.minibgm.core.model.RelatedWork> =
            if (shouldThrow) {
                error(
                    "Network error",
                )
            } else {
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .RelatedWork(id = 100L, name = "葬送のフリーレン"),
                )
            }

        override suspend fun getSubjectPersons(id: Long): List<SubjectPerson> = emptyList()

        override suspend fun getPerson(id: Long): com.infinitezerone.minibgm.core.model.PersonDetail =
            if (shouldThrow) {
                error("Network error")
            } else {
                com.infinitezerone.minibgm.core.model
                    .PersonDetail(id = id, name = "種﨑敦美")
            }

        override suspend fun getPersonSubjects(id: Long): List<com.infinitezerone.minibgm.core.model.RelatedWork> =
            if (shouldThrow) {
                error(
                    "Network error",
                )
            } else {
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .RelatedWork(id = 100L, name = "葬送のフリーレン"),
                )
            }

        override suspend fun getSubjectRelations(id: Long): List<SubjectRelation> = emptyList()

        override suspend fun getEpisodes(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): EpisodePageResponse =
            if (shouldThrow) {
                throw IllegalStateException("API error")
            } else {
                episodesResponse
            }

        override suspend fun searchSubjects(
            keyword: String,
            type: Int,
            limit: Int,
            offset: Int,
        ): SearchSubjectResponse = error("Not needed")

        override suspend fun searchSubjectsAdvanced(
            request: SearchSubjectsRequest,
            limit: Int,
            offset: Int,
        ): PageResponse<Subject> = error("Not needed")

        override suspend fun getUserCollections(
            username: String,
            subjectType: Int,
            type: Int?,
            limit: Int,
            offset: Int,
        ): UserCollectionPageResponse = error("Not needed")

        override suspend fun getMe(): com.infinitezerone.minibgm.core.model.UserProfile = error("Not needed")

        override suspend fun getCollection(
            username: String,
            subjectId: Long,
        ): com.infinitezerone.minibgm.core.model.UserCollection? = null

        override suspend fun updateCollection(
            subjectId: Long,
            type: Int,
            rate: Int?,
            comment: String?,
            private: Boolean,
            epStatus: Int?,
        ) = Unit

        override suspend fun updateEpisodeStatus(
            subjectId: Long,
            episodeId: Long,
            type: Int,
        ) = Unit
    }

    @Test
    fun getEpisodesStream_mapsAllFieldsCorrectly() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val apiService = FakeBangumiApiService()
            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)

            val testEntities =
                listOf(
                    EpisodeEntity(
                        id = 101L,
                        subjectId = 1L,
                        sort = 1f,
                        ep = 1f,
                        name = "Ep 1",
                        nameCn = "第1集",
                        duration = "24:00",
                        airdate = "2023-10-01",
                        type = 0,
                        desc = "本篇第1集简介",
                        comment = 120,
                    ),
                    EpisodeEntity(
                        id = 102L,
                        subjectId = 1L,
                        sort = 1f,
                        ep = 1f,
                        name = "SP 1",
                        nameCn = "特别篇1",
                        duration = "10:00",
                        airdate = "2023-10-15",
                        type = 1,
                        desc = "特别篇简介",
                        comment = 50,
                    ),
                    EpisodeEntity(
                        id = 103L,
                        subjectId = 1L,
                        sort = 1f,
                        ep = 1f,
                        name = "OP 1 勇者",
                        nameCn = "片头曲1",
                        duration = "01:30",
                        airdate = "2023-10-01",
                        type = 2,
                        desc = "YOASOBI 演唱 OP",
                        comment = 88,
                    ),
                )

            episodeDao.insertEpisodes(testEntities)

            val episodes = repo.getEpisodesStream(1L).first()
            assertEquals(3, episodes.size)

            val ep0 = episodes[0]
            assertEquals(101L, ep0.id)
            assertEquals(0, ep0.type)
            assertEquals("本篇第1集简介", ep0.desc)
            assertEquals(120, ep0.comment)

            val ep1 = episodes[1]
            assertEquals(102L, ep1.id)
            assertEquals(1, ep1.type)
            assertEquals("特别篇简介", ep1.desc)
            assertEquals(50, ep1.comment)

            val ep2 = episodes[2]
            assertEquals(103L, ep2.id)
            assertEquals(2, ep2.type)
            assertEquals("YOASOBI 演唱 OP", ep2.desc)
            assertEquals(88, ep2.comment)
        }

    @Test
    fun fetchEpisodes_persistsTypeDescCommentToDatabase() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val apiService =
                FakeBangumiApiService().apply {
                    episodesResponse =
                        EpisodePageResponse(
                            total = 2,
                            data =
                                listOf(
                                    Episode(
                                        id = 201L,
                                        sort = 1f,
                                        ep = 1f,
                                        name = "Main Episode",
                                        nameCn = "正片第1集",
                                        duration = "24:30",
                                        airdate = "2023-10-01",
                                        type = 0,
                                        desc = "剧情介绍",
                                        comment = 999,
                                    ),
                                    Episode(
                                        id = 202L,
                                        sort = 1f,
                                        ep = 1f,
                                        name = "Creditless ED",
                                        nameCn = "无字ED",
                                        duration = "01:30",
                                        airdate = "2023-10-01",
                                        type = 3,
                                        desc = "ED 动画",
                                        comment = 33,
                                    ),
                                ),
                        )
                }

            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)
            val result = repo.fetchEpisodes(1001L)

            assertIs<AppResult.Success<List<Episode>>>(result)
            assertEquals(2, result.data.size)

            val stored = episodeDao.getStoredEpisodes(1001L)
            assertEquals(2, stored.size)
            assertEquals(0, stored[0].type)
            assertEquals("剧情介绍", stored[0].desc)
            assertEquals(999, stored[0].comment)

            assertEquals(3, stored[1].type)
            assertEquals("ED 动画", stored[1].desc)
            assertEquals(33, stored[1].comment)
        }

    @Test
    fun fetchEpisodes_onApiError_returnsAppResultError() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val apiService =
                FakeBangumiApiService().apply {
                    shouldThrow = true
                }

            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)
            val result = repo.fetchEpisodes(1001L)

            assertIs<AppResult.Error>(result)
            assertTrue(result.throwable is IllegalStateException)
        }

    @Test
    fun fetchSubjectDetail_persistsToDatabaseAndReturnsSuccess() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val testSubject =
                Subject(
                    id = 528828L,
                    name = "骸骨騎士様",
                    nameCn = "骸骨骑士大人",
                    rating =
                        com.infinitezerone.minibgm.core.model.Rating(
                            score = 7.5,
                            rank = 1200,
                            total = 450,
                            count = mapOf("8" to 200, "9" to 150),
                        ),
                    collection =
                        com.infinitezerone.minibgm.core.model.CollectionCount(
                            wish = 10,
                            collect = 300,
                            doing = 50,
                            onHold = 5,
                            dropped = 2,
                        ),
                    tags =
                        listOf(
                            com.infinitezerone.minibgm.core.model
                                .Tag("异世界", 120),
                        ),
                )
            val apiService =
                FakeBangumiApiService().apply {
                    subjectResponse = testSubject
                }
            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)

            val result = repo.fetchSubjectDetail(528828L)
            assertIs<AppResult.Success<Subject>>(result)
            assertEquals("骸骨騎士様", result.data.name)

            val streamSubject = repo.getSubjectStream(528828L).first()
            kotlin.test.assertNotNull(streamSubject)
            assertEquals(7.5, streamSubject.rating?.score)
            assertEquals(450, streamSubject.rating?.total)
            assertEquals(200, streamSubject.rating?.count?.get("8"))
            assertEquals(50, streamSubject.collection?.doing)
            assertEquals(1, streamSubject.tags.size)
            assertEquals("异世界", streamSubject.tags[0].name)

            val storedEntity = subjectDao.subjectsFlow.value[528828L]
            kotlin.test.assertNotNull(storedEntity)
            assertTrue(storedEntity.updatedAt > 0L)
        }

    @Test
    fun fetchSubjectDetail_onError_returnsAppResultError() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val apiService =
                FakeBangumiApiService().apply {
                    shouldThrow = true
                }
            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)

            val result = repo.fetchSubjectDetail(528828L)
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.startsWith("获取条目详情失败"))
        }

    @Test
    fun fetchSubjectDetail_onCancellation_rethrows() =
        runTest {
            val subjectDao = FakeSubjectDao()
            val episodeDao = FakeEpisodeDao()
            val apiService =
                FakeBangumiApiService().apply {
                    cancellationToThrow = true
                }
            val repo = SubjectRepositoryImpl(apiService, subjectDao, episodeDao)

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                repo.fetchSubjectDetail(528828L)
            }
        }
}
