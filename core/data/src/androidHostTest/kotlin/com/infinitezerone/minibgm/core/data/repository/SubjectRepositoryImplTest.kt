package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubjectRepositoryImplTest {
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
    fun getEpisodesStream_initiallyEmpty_emitsAfterFetch() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    episodesResponse =
                        EpisodePageResponse(
                            total = 3,
                            data =
                                listOf(
                                    Episode(
                                        id = 101L,
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
                                    Episode(
                                        id = 102L,
                                        sort = 2f,
                                        ep = 2f,
                                        name = "SP 1",
                                        nameCn = "特别篇1",
                                        duration = "10:00",
                                        airdate = "2023-10-15",
                                        type = 1,
                                        desc = "特别篇简介",
                                        comment = 50,
                                    ),
                                    Episode(
                                        id = 103L,
                                        sort = 3f,
                                        ep = 3f,
                                        name = "OP 1 勇者",
                                        nameCn = "片头曲1",
                                        duration = "01:30",
                                        airdate = "2023-10-01",
                                        type = 2,
                                        desc = "YOASOBI 演唱 OP",
                                        comment = 88,
                                    ),
                                ),
                        )
                }
            val repo = SubjectRepositoryImpl(apiService)

            // 初始为空
            val initial = repo.getEpisodesStream(1L).first()
            assertEquals(0, initial.size)

            // 拉取后立即注入内存缓存并下发流
            val fetchResult = repo.fetchEpisodes(1L)
            assertIs<AppResult.Success<List<Episode>>>(fetchResult)
            assertEquals(3, fetchResult.data.size)

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
        }

    @Test
    fun fetchEpisodes_onApiError_returnsAppResultError() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    shouldThrow = true
                }

            val repo = SubjectRepositoryImpl(apiService)
            val result = repo.fetchEpisodes(1001L)

            assertIs<AppResult.Error>(result)
            assertTrue(result.throwable is IllegalStateException)
        }

    @Test
    fun fetchSubjectDetail_persistsToMemoryCacheAndEmitsToStream() =
        runTest {
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
            val repo = SubjectRepositoryImpl(apiService)

            // 拉取前流为 null
            assertNull(repo.getSubjectStream(528828L).first())

            val result = repo.fetchSubjectDetail(528828L)
            assertIs<AppResult.Success<Subject>>(result)
            assertEquals("骸骨騎士様", result.data.name)

            val streamSubject = repo.getSubjectStream(528828L).first()
            assertNotNull(streamSubject)
            assertEquals(7.5, streamSubject.rating?.score)
            assertEquals(450, streamSubject.rating?.total)
            assertEquals(200, streamSubject.rating?.count?.get("8"))
            assertEquals(50, streamSubject.collection?.doing)
            assertEquals(1, streamSubject.tags.size)
            assertEquals("异世界", streamSubject.tags[0].name)
        }

    @Test
    fun fetchSubjectDetail_lruEviction_evictsOldestWhenExceedingCapacity() =
        runTest {
            val apiService = FakeBangumiApiService()
            val repo = SubjectRepositoryImpl(apiService, maxMemoryEntries = 2)

            repo.fetchSubjectDetail(1L)
            repo.fetchSubjectDetail(2L)
            assertNotNull(repo.getSubjectStream(1L).first())
            assertNotNull(repo.getSubjectStream(2L).first())

            // 写入第 3 个，应淘汰最早的 1L
            repo.fetchSubjectDetail(3L)
            assertNull(repo.getSubjectStream(1L).first())
            assertNotNull(repo.getSubjectStream(2L).first())
            assertNotNull(repo.getSubjectStream(3L).first())
        }

    @Test
    fun fetchSubjectDetail_onError_returnsAppResultError() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    shouldThrow = true
                }
            val repo = SubjectRepositoryImpl(apiService)

            val result = repo.fetchSubjectDetail(528828L)
            assertIs<AppResult.Error>(result)
            assertTrue(result.message.startsWith("获取条目详情失败"))
        }

    @Test
    fun fetchSubjectDetail_onCancellation_rethrows() =
        runTest {
            val apiService =
                FakeBangumiApiService().apply {
                    cancellationToThrow = true
                }
            val repo = SubjectRepositoryImpl(apiService)

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                repo.fetchSubjectDetail(528828L)
            }
        }
}
