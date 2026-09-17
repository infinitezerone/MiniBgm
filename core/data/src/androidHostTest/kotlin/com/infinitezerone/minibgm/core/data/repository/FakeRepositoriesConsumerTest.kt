package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.testing.data.sampleAirScheduleList
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfile
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.infinitezerone.minibgm.core.testing.util.testBgmDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证从其他模块（如 :core:data 或后续 :feature:*）引入并消费 :core:testing
 * 基础设施时的可用性与行为正确性。
 */
class FakeRepositoriesConsumerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun verifyTestDispatchersIntegration() {
        val dispatchers = testBgmDispatchers(mainDispatcherRule.testDispatcher)
        assertNotNull(dispatchers.default)
        assertNotNull(dispatchers.io)
        assertNotNull(dispatchers.main)
    }

    @Test
    fun verifyFakeAuthRepositoryInConsumer() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = true)
            assertTrue(authRepo.isLoggedIn.first())

            authRepo.logout()
            assertFalse(authRepo.isLoggedIn.first())
            assertEquals(1, authRepo.logoutCallCount)

            authRepo.setLoggedIn(true)
            authRepo.logoutAll()
            assertFalse(authRepo.isLoggedIn.first())
            assertEquals(1, authRepo.logoutAllCallCount)
        }

    @Test
    fun verifyFakeScheduleRepositoryInConsumer() =
        runTest {
            val scheduleRepo = FakeScheduleRepository()
            scheduleRepo.sendSchedules(weekday = 5, schedules = sampleAirScheduleList)

            val schedules = scheduleRepo.getSchedulesByWeekday(5).first()
            assertEquals(1, schedules.size)
            assertEquals("葬送のフリーレン", schedules.first().title)
        }

    @Test
    fun verifyFakeSubjectRepositoryInConsumer() =
        runTest {
            val subjectRepo = FakeSubjectRepository()
            subjectRepo.sendSubject(sampleSubject)
            subjectRepo.sendEpisodes(sampleSubject.id, sampleEpisodeList)

            val subject = subjectRepo.getSubjectStream(sampleSubject.id).first()
            assertNotNull(subject)
            assertEquals(sampleSubject.id, subject.id)

            val episodes = subjectRepo.getEpisodesStream(sampleSubject.id).first()
            assertEquals(2, episodes.size)
            assertEquals("冒険の終わり", episodes.first().name)
        }

    @Test
    fun verifyFakeCollectionRepositoryInConsumer() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)

            val collection = collectionRepo.getCollectionStream(sampleUserCollection.subjectId).first()
            assertNotNull(collection)
            assertEquals(3, collection.type)
            assertEquals(9, collection.rate)

            collectionRepo.updateCollectionStatus(sampleUserCollection.subjectId, CollectionType.COLLECT, rate = 10)
            val updated = collectionRepo.getCollectionStream(sampleUserCollection.subjectId).first()
            assertNotNull(updated)
            assertEquals(2, updated.type)
            assertEquals(10, updated.rate)
            assertEquals(1, collectionRepo.updateCollectionCallCount)

            val fetchResult = collectionRepo.fetchUserCollections("test_user", subjectType = 2, type = CollectionType.COLLECT)
            assertIs<AppResult.Success<List<com.infinitezerone.minibgm.core.model.UserCollection>>>(fetchResult)
            assertEquals(1, fetchResult.data.size)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun verifyFakeSearchRepositoryInConsumer() =
        runTest {
            val searchRepo = FakeSearchRepository()
            searchRepo.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(sampleSubject)))

            val result = searchRepo.searchSubjects("芙莉莲")
            assertIs<AppResult.Success<SearchResult>>(result)
            assertEquals(1, result.data.list.size)
            assertEquals(
                "葬送のフリーレン",
                result.data.list
                    .first()
                    .name,
            )
        }

    @Test
    fun verifySampleDataIntegrity() {
        assertEquals(1001L, sampleSubject.id)
        assertEquals(2, sampleEpisodeList.size)
        assertEquals(1, sampleAirScheduleList.size)
        assertEquals(42L, sampleUserProfile.id)
    }
}
