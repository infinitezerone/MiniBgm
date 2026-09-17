package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.model.CalendarDayResponse
import com.infinitezerone.minibgm.core.network.model.EpisodePageResponse
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.SearchSubjectResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import com.infinitezerone.minibgm.core.testing.datastore.createTestUserPreferencesDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchRepositoryImplTest {
    private class FakeApiService : BangumiApiService {
        var advancedResponse: PageResponse<Subject> = PageResponse(total = 0, limit = 20, offset = 0, data = emptyList())
        var legacyResponse: SearchSubjectResponse = SearchSubjectResponse(results = 0, list = emptyList())
        var shouldThrowAdvanced: Boolean = false
        var shouldThrowLegacy: Boolean = false
        var cancellationToThrow: Boolean = false

        override suspend fun searchSubjectsAdvanced(
            request: SearchSubjectsRequest,
            limit: Int,
            offset: Int,
        ): PageResponse<Subject> {
            if (cancellationToThrow) throw CancellationException("Cancelled")
            if (shouldThrowAdvanced) throw BgmNetworkException.ServerError(500, "Server Error")
            return advancedResponse
        }

        override suspend fun searchSubjects(
            keyword: String,
            type: Int,
            limit: Int,
            offset: Int,
        ): SearchSubjectResponse {
            if (cancellationToThrow) throw CancellationException("Cancelled")
            if (shouldThrowLegacy) throw BgmNetworkException.ServerError(500, "Legacy Fail")
            return legacyResponse
        }

        override suspend fun getCalendar(): List<CalendarDayResponse> = error("Not needed")

        override suspend fun getSubject(id: Long): Subject = error("Not needed")

        override suspend fun getSubjectCharacters(id: Long): List<SubjectCharacter> = emptyList()

        override suspend fun getCharacter(id: Long): CharacterDetail = error("Not needed")

        override suspend fun getCharacterSubjects(id: Long): List<RelatedWork> = emptyList()

        override suspend fun getSubjectPersons(id: Long): List<SubjectPerson> = emptyList()

        override suspend fun getPerson(id: Long): PersonDetail = error("Not needed")

        override suspend fun getPersonSubjects(id: Long): List<RelatedWork> = emptyList()

        override suspend fun getSubjectRelations(id: Long): List<SubjectRelation> = emptyList()

        override suspend fun getEpisodes(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): EpisodePageResponse = error("Not needed")

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
    fun searchSubjects_advancedSuccess_returnsData() =
        runTest {
            val fakeApi =
                FakeApiService().apply {
                    advancedResponse =
                        PageResponse(
                            total = 1,
                            limit = 20,
                            offset = 0,
                            data = listOf(Subject(id = 1L, name = "Frieren", nameCn = "芙莉莲")),
                        )
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            val result = repo.searchSubjects("Frieren")
            assertIs<AppResult.Success<*>>(result)
            val data = (result as AppResult.Success).data
            assertEquals(1, data.total)
            assertEquals(1, data.list.size)
            assertEquals("Frieren", data.list[0].name)
        }

    @Test
    fun searchSubjects_advancedFails_fallsBackToLegacy() =
        runTest {
            val fakeApi =
                FakeApiService().apply {
                    shouldThrowAdvanced = true
                    legacyResponse =
                        SearchSubjectResponse(
                            results = 1,
                            list = listOf(Subject(id = 2L, name = "Dungeon Meshi", nameCn = "迷宫饭")),
                        )
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            val result = repo.searchSubjects("Dungeon")
            assertIs<AppResult.Success<*>>(result)
            val data = (result as AppResult.Success).data
            assertEquals(1, data.total)
            assertEquals(1, data.list.size)
            assertEquals("Dungeon Meshi", data.list[0].name)
        }

    @Test
    fun searchSubjects_bothFail_returnsAppResultError() =
        runTest {
            val fakeApi =
                FakeApiService().apply {
                    shouldThrowAdvanced = true
                    shouldThrowLegacy = true
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            val result = repo.searchSubjects("Fail")
            assertIs<AppResult.Error>(result)
        }

    @Test
    fun searchSubjects_onCancellation_rethrows() =
        runTest {
            val fakeApi =
                FakeApiService().apply {
                    cancellationToThrow = true
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            assertFailsWith<CancellationException> {
                repo.searchSubjects("Cancel")
            }
        }

    @Test
    fun searchSubjectsAdvanced_successAndCancellation() =
        runTest {
            val fakeApi =
                FakeApiService().apply {
                    advancedResponse =
                        PageResponse(
                            total = 1,
                            limit = 20,
                            offset = 0,
                            data = listOf(Subject(id = 3L, name = "Steins;Gate")),
                        )
                }
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            val result = repo.searchSubjectsAdvanced(SearchSubjectsRequest(keyword = "Gate"))
            assertIs<AppResult.Success<List<Subject>>>(result)
            assertEquals(1, result.data.size)

            fakeApi.cancellationToThrow = true
            assertFailsWith<CancellationException> {
                repo.searchSubjectsAdvanced(SearchSubjectsRequest(keyword = "Gate"))
            }
        }

    @Test
    fun searchHistory_addRemoveClear() =
        runTest {
            val fakeApi = FakeApiService()
            val userPrefs = createTestUserPreferencesDataSource()
            val repo = SearchRepositoryImpl(fakeApi, userPrefs)

            repo.addSearchHistory("Eva")
            repo.addSearchHistory("Cowboy")

            val history = repo.getSearchHistory().first()
            assertTrue(history.contains("Eva"))
            assertTrue(history.contains("Cowboy"))

            repo.removeSearchHistory("Eva")
            val updated = repo.getSearchHistory().first()
            assertTrue(!updated.contains("Eva"))

            repo.clearSearchHistory()
            val empty = repo.getSearchHistory().first()
            assertTrue(empty.isEmpty())
        }
}
