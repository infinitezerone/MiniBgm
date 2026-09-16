package com.infinitezerone.minibgm.core.ai.tools

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubjectToolsTest {
    private val fakeSearchRepository = FakeSearchRepository()
    private val fakeSubjectRepository = FakeSubjectRepository()
    private val subjectTools = SubjectTools(fakeSearchRepository, fakeSubjectRepository)

    @Test
    fun searchAnime_returns_matching_results() =
        runTest {
            val testSubject =
                Subject(
                    id = 12345L,
                    name = "Sousou no Frieren",
                    nameCn = "葬送的芙莉莲",
                    summary = "After the party of heroes defeated the Demon King...",
                    rating = Rating(score = 9.1),
                    airDate = "2023-09-29",
                    eps = 28,
                )
            fakeSearchRepository.searchResult = AppResult.Success(SearchResult(total = 1, list = listOf(testSubject)))

            val result = subjectTools.searchAnime("Frieren")
            assertTrue(result.contains("12345"))
            assertTrue(result.contains("Sousou no Frieren"))
            assertTrue(result.contains("葬送的芙莉莲"))
            assertTrue(result.contains("9.1"))
        }

    @Test
    fun searchAnime_handles_blank_and_empty_results() =
        runTest {
            val blankResult = subjectTools.searchAnime("")
            assertEquals("Search query must not be empty.", blankResult)

            fakeSearchRepository.searchResult = AppResult.Success(SearchResult(total = 0, list = emptyList()))
            val emptyResult = subjectTools.searchAnime("UnknownAnimeNotExist")
            assertTrue(emptyResult.contains("No anime found matching query"))
        }

    @Test
    fun getSubjectDetail_returns_details_on_success() =
        runTest {
            val subject =
                Subject(
                    id = 12345L,
                    name = "Sousou no Frieren",
                    nameCn = "葬送的芙莉莲",
                    summary = "Fantasy journey after hero death",
                    rating = Rating(score = 9.1, rank = 1),
                    totalEpisodes = 28,
                    airDate = "2023-09-29",
                )
            fakeSubjectRepository.sendSubject(subject)

            val result = subjectTools.getSubjectDetail(12345L)
            assertTrue(result.contains("12345"))
            assertTrue(result.contains("葬送的芙莉莲"))
            assertTrue(result.contains("9.1"))
            assertTrue(result.contains("28"))
        }

    @Test
    fun getSubjectDetail_handles_error() =
        runTest {
            fakeSubjectRepository.fetchSubjectDetailResult = { AppResult.Error(IllegalStateException("Network timeout")) }
            val result = subjectTools.getSubjectDetail(12345L)
            assertTrue(result.contains("Failed to get subject details"))
            assertTrue(result.contains("Network timeout"))
        }

    @Test
    fun getSubjectEpisodes_returns_episode_list() =
        runTest {
            val episodes =
                listOf(
                    Episode(
                        id = 9001L,
                        ep = 1f,
                        sort = 1f,
                        name = "The End of the Journey",
                        nameCn = "冒险的结束",
                        airdate = "2023-09-29",
                    ),
                    Episode(
                        id = 9002L,
                        ep = 2f,
                        sort = 2f,
                        name = "It Didn't Have to Be Magic...",
                        nameCn = "不一定要是魔法...",
                        airdate = "2023-09-29",
                    ),
                )
            fakeSubjectRepository.sendEpisodes(12345L, episodes)

            val result = subjectTools.getSubjectEpisodes(12345L)
            assertTrue(result.contains("9001"))
            assertTrue(result.contains("冒险的结束"))
            assertTrue(result.contains("9002"))
        }

    @Test
    fun getSubjectEpisodes_handles_empty_and_error() =
        runTest {
            fakeSubjectRepository.sendEpisodes(12345L, emptyList())
            val emptyResult = subjectTools.getSubjectEpisodes(12345L)
            assertTrue(emptyResult.contains("No episodes found"))

            fakeSubjectRepository.fetchEpisodesResult = { AppResult.Error(IllegalStateException("API Error")) }
            val errorResult = subjectTools.getSubjectEpisodes(12345L)
            assertTrue(errorResult.contains("Failed to get episodes"))
        }

    @Test
    fun getSubjectDetail_and_Episodes_validate_subjectId() =
        runTest {
            val detailRes = subjectTools.getSubjectDetail(0L)
            assertTrue(detailRes.contains("Invalid subject ID"))

            val epRes = subjectTools.getSubjectEpisodes(-10L)
            assertTrue(epRes.contains("Invalid subject ID"))
        }

    @Test
    fun searchAnime_coerces_non_positive_limit() =
        runTest {
            fakeSearchRepository.searchResult = AppResult.Success(SearchResult(total = 0, list = emptyList()))
            val result = subjectTools.searchAnime(query = "Frieren", limit = -1)
            assertTrue(result.contains("No anime found"))
        }
}
