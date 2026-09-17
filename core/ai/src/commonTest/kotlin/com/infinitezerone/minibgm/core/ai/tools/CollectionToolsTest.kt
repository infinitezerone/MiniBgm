package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.ai.DefaultPendingActionExecutor
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.PendingAction
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollectionToolsTest {
    private val fakeCollectionRepository = FakeCollectionRepository()
    private val collectionTools = CollectionTools(fakeCollectionRepository)
    private val executor = DefaultPendingActionExecutor(fakeCollectionRepository)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun getCollection_read_operation_executes_automatically() =
        runTest {
            fakeCollectionRepository.sendCollection(
                UserCollection(
                    userId = 1L,
                    subjectId = 12345L,
                    type = CollectionType.DOING.value,
                    epStatus = 12,
                    rate = 9,
                    comment = "Masterpiece",
                    updatedAt = "2024-03-01T10:00:00Z",
                ),
            )

            val result = collectionTools.getCollection(12345L)
            assertTrue(result.contains("12345"))
            assertTrue(result.contains("DOING"))
            assertTrue(result.contains("12"))
            assertTrue(result.contains("Masterpiece"))
        }

    @Test
    fun getCollection_returns_not_found_message_when_not_in_collection() =
        runTest {
            val result = collectionTools.getCollection(99999L)
            assertTrue(result.contains("is not currently in user's collection"))
        }

    @Test
    fun getWatchingList_read_operation_executes_automatically() =
        runTest {
            fakeCollectionRepository.sendCollection(
                UserCollection(
                    userId = 1L,
                    subjectId = 1001L,
                    type = CollectionType.DOING.value,
                    epStatus = 5,
                ),
            )
            fakeCollectionRepository.sendCollection(
                UserCollection(
                    userId = 1L,
                    subjectId = 1002L,
                    type = CollectionType.DOING.value,
                    epStatus = 20,
                ),
            )

            val result = collectionTools.getWatchingList()
            assertTrue(result.contains("1001"))
            assertTrue(result.contains("1002"))
            assertTrue(result.contains("DOING"))
        }

    @Test
    fun proposeUpdateCollection_does_NOT_mutate_repository_and_returns_proposal() =
        runTest {
            val initialStatusCalls = fakeCollectionRepository.updateCollectionCallCount

            val responseJson =
                collectionTools.proposeUpdateCollection(
                    subjectId = 12345L,
                    subjectTitle = "葬送的芙莉莲",
                    collectionType = "COLLECT",
                    rating = 10,
                    comment = "神作！",
                )

            // HITL Safety Assertion: Repository must NOT be mutated during tool execution
            assertEquals(
                initialStatusCalls,
                fakeCollectionRepository.updateCollectionCallCount,
                "HITL Violation: proposeUpdateCollection silently mutated repository!",
            )

            val proposal = json.decodeFromString<ActionProposal>(responseJson)
            assertEquals("PENDING_CONFIRMATION", proposal.status)
            assertIs<PendingAction.UpdateCollection>(proposal.action)
            val action = proposal.action as PendingAction.UpdateCollection
            assertEquals(12345L, action.subjectId)
            assertEquals("葬送的芙莉莲", action.subjectTitle)
            assertEquals(CollectionType.COLLECT, action.collectionType)
            assertEquals(10, action.rating)
            assertEquals("神作！", action.comment)
        }

    @Test
    fun proposeUpdateEpisodeProgress_does_NOT_mutate_repository_and_returns_proposal() =
        runTest {
            val initialEpisodeCalls = fakeCollectionRepository.updateEpisodeCallCount

            val responseJson =
                collectionTools.proposeUpdateEpisodeProgress(
                    subjectId = 12345L,
                    subjectTitle = "葬送的芙莉莲",
                    episodeNumber = 15,
                    isWatched = true,
                )

            // HITL Safety Assertion: Repository must NOT be mutated during tool execution
            assertEquals(
                initialEpisodeCalls,
                fakeCollectionRepository.updateEpisodeCallCount,
                "HITL Violation: proposeUpdateEpisodeProgress silently mutated repository!",
            )

            val proposal = json.decodeFromString<ActionProposal>(responseJson)
            assertEquals("PENDING_CONFIRMATION", proposal.status)
            assertIs<PendingAction.UpdateEpisode>(proposal.action)
            val action = proposal.action as PendingAction.UpdateEpisode
            assertEquals(12345L, action.subjectId)
            assertEquals("葬送的芙莉莲", action.subjectTitle)
            assertEquals(15, action.episodeNumber)
            assertEquals(true, action.isWatched)
        }

    @Test
    fun pendingActionExecutor_executes_confirmed_proposals_against_repository() =
        runTest {
            val updateCollectionAction =
                PendingAction.UpdateCollection(
                    actionId = "test_act_1",
                    subjectId = 12345L,
                    subjectTitle = "葬送的芙莉莲",
                    collectionType = CollectionType.COLLECT,
                    rating = 10,
                    comment = "神作！",
                    description = "Update to COLLECT",
                )

            val result1 = executor.execute(updateCollectionAction)
            assertIs<AppResult.Success<Unit>>(result1)
            assertEquals(1, fakeCollectionRepository.updateCollectionCallCount)

            val updateEpisodeAction =
                PendingAction.UpdateEpisode(
                    actionId = "test_act_2",
                    subjectId = 12345L,
                    subjectTitle = "葬送的芙莉莲",
                    episodeNumber = 16,
                    isWatched = true,
                    description = "Mark ep 16 watched",
                )

            val result2 = executor.execute(updateEpisodeAction)
            assertIs<AppResult.Success<Unit>>(result2)
            assertEquals(1, fakeCollectionRepository.updateEpisodeCallCount)
        }

    @Test
    fun proposeUpdateCollection_validates_input_bounds_and_types() =
        runTest {
            // Invalid subject ID
            val resInvalidSubject =
                collectionTools.proposeUpdateCollection(
                    subjectId = -1L,
                    collectionType = "DOING",
                )
            assertTrue(resInvalidSubject.contains("Invalid subject ID"))

            // Invalid collection type
            val resInvalidType =
                collectionTools.proposeUpdateCollection(
                    subjectId = 123L,
                    collectionType = "NOT_A_VALID_TYPE",
                )
            assertTrue(resInvalidType.contains("Invalid collection type"))

            // Invalid rating
            val resInvalidRating =
                collectionTools.proposeUpdateCollection(
                    subjectId = 123L,
                    collectionType = "DOING",
                    rating = 15,
                )
            assertTrue(resInvalidRating.contains("Invalid rating"))
        }

    @Test
    fun proposeUpdateCollection_supports_synonyms() =
        runTest {
            val resWatching =
                collectionTools.proposeUpdateCollection(
                    subjectId = 123L,
                    collectionType = "WATCHING",
                )
            val pWatching = json.decodeFromString<ActionProposal>(resWatching)
            assertEquals(CollectionType.DOING, (pWatching.action as PendingAction.UpdateCollection).collectionType)

            val resWish =
                collectionTools.proposeUpdateCollection(
                    subjectId = 123L,
                    collectionType = "想看",
                )
            val pWish = json.decodeFromString<ActionProposal>(resWish)
            assertEquals(CollectionType.WISH, (pWish.action as PendingAction.UpdateCollection).collectionType)
        }

    @Test
    fun proposeUpdateEpisodeProgress_validates_bounds() =
        runTest {
            val resInvalidSubject =
                collectionTools.proposeUpdateEpisodeProgress(
                    subjectId = 0L,
                    episodeNumber = 1,
                )
            assertTrue(resInvalidSubject.contains("Invalid subject ID"))

            val resInvalidEp =
                collectionTools.proposeUpdateEpisodeProgress(
                    subjectId = 123L,
                    episodeNumber = 0,
                )
            assertTrue(resInvalidEp.contains("Invalid episode number"))
        }

    @Test
    fun getCollection_validates_subjectId() =
        runTest {
            val res = collectionTools.getCollection(-5L)
            assertTrue(res.contains("Invalid subject ID"))
        }

    @Test
    fun pendingActionExecutor_handles_repository_errors() =
        runTest {
            fakeCollectionRepository.updateCollectionResult = AppResult.Error(IllegalStateException("Network offline"))
            val action =
                PendingAction.UpdateCollection(
                    actionId = "test_err",
                    subjectId = 12345L,
                    collectionType = CollectionType.DOING,
                    description = "Update to DOING",
                )
            val result = executor.execute(action)
            assertIs<AppResult.Error>(result)
            assertEquals("Network offline", result.throwable.message)

            // Invalid ID
            val invalidAction =
                PendingAction.UpdateCollection(
                    actionId = "test_invalid_id",
                    subjectId = -1L,
                    collectionType = CollectionType.DOING,
                    description = "Invalid ID",
                )
            val invalidResult = executor.execute(invalidAction)
            assertIs<AppResult.Error>(invalidResult)
        }
}
