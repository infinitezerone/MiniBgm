package com.infinitezerone.minibgm.feature.search

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadTriggersAdvancedSearchSuccessfully() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)

            advanceUntilIdle()

            assertEquals(1, searchRepository.advancedSearchCallCount)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.error)
            assertEquals(1, state.subjects.size)
            assertEquals("葬送的芙莉莲", state.subjects.first().nameCn)
            assertEquals(ExploreMood.MASTERPIECE, state.selectedMood)
            assertEquals(ALL_TIME_SEASON, state.selectedSeason)
            assertEquals(ExploreCategory.ANIME, state.selectedCategory)
            assertEquals(ExploreSort.RANK, state.selectedSort)
            assertTrue(state.selectedTags.isEmpty())
            assertTrue(state.isLoggedIn)
        }

    @Test
    fun onMoodSelectUpdatesSortTagAndSearches() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onMoodSelect(ExploreMood.HEALING)
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(ExploreMood.HEALING, viewModel.uiState.value.selectedMood)
            assertEquals(ExploreSort.RANK, viewModel.uiState.value.selectedSort)
            assertEquals(ALL_TIME_SEASON, viewModel.uiState.value.selectedSeason)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.airDate)
            assertEquals(listOf("治愈", "日常"), searchRepository.lastAdvancedRequest?.filter?.tag)
            assertEquals(listOf(">0"), searchRepository.lastAdvancedRequest?.filter?.rank)
            assertEquals("rank", searchRepository.lastAdvancedRequest?.sort)
        }

    @Test
    fun masterpieceMoodSortsByRankAndFiltersOutUnrankedSubjects() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onMoodSelect(ExploreMood.HOT)
            advanceUntilIdle()
            viewModel.onMoodSelect(ExploreMood.MASTERPIECE)
            advanceUntilIdle()

            assertEquals(ExploreMood.MASTERPIECE, viewModel.uiState.value.selectedMood)
            assertEquals(ExploreSort.RANK, viewModel.uiState.value.selectedSort)
            assertEquals(listOf(">0"), searchRepository.lastAdvancedRequest?.filter?.rank)
            assertEquals("rank", searchRepository.lastAdvancedRequest?.sort)
            assertNull(searchRepository.lastAdvancedRequest?.filter?.airDate)
        }

    @Test
    fun moodPresets_doNotTriggerCustomFilterActive_preventingUnexpectedClearAllRow() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            // 初始状态处于 MASTERPIECE 预设，不是自定义筛选
            assertFalse(viewModel.uiState.value.isCustomFilterActive)

            // 遍历所有预设，均不得被判定为自定义筛选，避免展示奇怪的“清除全部”条
            ExploreMood.entries.forEach { mood ->
                viewModel.onMoodSelect(mood)
                advanceUntilIdle()
                assertEquals(mood, viewModel.uiState.value.selectedMood)
                assertFalse(
                    "预设 ${mood.label} 不应激活自定义筛选栏",
                    viewModel.uiState.value.isCustomFilterActive,
                )
            }
        }

    @Test
    fun manualFiltering_activatesCustomFilterAndClearingPresetsIt() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            // 手动勾选标签，脱离预设并激活自定义筛选
            viewModel.onTagToggle("科幻")
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedMood)
            assertTrue(viewModel.uiState.value.isCustomFilterActive)

            // 重新选择任一预设，恢复预设模式并重置自定义状态
            viewModel.onMoodSelect(ExploreMood.MASTERPIECE)
            advanceUntilIdle()
            assertEquals(ExploreMood.MASTERPIECE, viewModel.uiState.value.selectedMood)
            assertFalse(viewModel.uiState.value.isCustomFilterActive)
        }

    @Test
    fun customFilterSummary_correctlyFormatsActiveFilters() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onTagToggle("科幻")
            viewModel.onTagToggle("冒险")
            advanceUntilIdle()

            assertEquals("#科幻 · #冒险 (2)", viewModel.uiState.value.customFilterSummary)

            viewModel.onSortSelect(ExploreSort.HEAT)
            advanceUntilIdle()
            assertEquals("#科幻 · #冒险 · 热门排行 (3)", viewModel.uiState.value.customFilterSummary)
        }

    @Test
    fun lazyLoading_whenAutoStartIsFalse_doesNotQueryUntilLoadIfNeeded() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel =
                ExploreViewModel(
                    searchRepository = searchRepository,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                    autoStart = false,
                )
            advanceUntilIdle()

            // 尚未切换显示时，不发起任何网络请求
            assertEquals(0, searchRepository.advancedSearchCallCount)

            // 切换展示后，触发首次加载
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            assertEquals(1, searchRepository.advancedSearchCallCount)

            // 再次触发 loadIfNeeded（例如切回 Tab 0 又切回 Tab 1），不重复请求
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            assertEquals(1, searchRepository.advancedSearchCallCount)
        }

    @Test
    fun toggleWishWhenNotLoggedInShowsLoginDialog() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoggedIn)
            assertFalse(viewModel.uiState.value.showLoginPromptDialog)

            viewModel.toggleWish(sampleSubject.id)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showLoginPromptDialog)
            assertEquals(0, collectionRepository.updateCollectionCallCount)

            viewModel.dismissLoginPrompt()
            assertFalse(viewModel.uiState.value.showLoginPromptDialog)
        }

    @Test
    fun toggleWishWhenLoggedInAddsToCollectionAndUpdatesWishedIds() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(sampleSubject.id),
            )

            viewModel.toggleWish(sampleSubject.id)
            advanceUntilIdle()

            assertEquals(1, collectionRepository.updateCollectionCallCount)
            assertTrue(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(sampleSubject.id),
            )
            assertEquals("已加入「想看」列表", viewModel.uiState.value.userMessage)

            // 再次点击提示已在追番中
            viewModel.toggleWish(sampleSubject.id)
            advanceUntilIdle()

            assertEquals(1, collectionRepository.updateCollectionCallCount)
            assertEquals("该番剧已在您的追番列表中", viewModel.uiState.value.userMessage)
        }

    @Test
    fun toggleWishWhenUpdateFailsRollsBackWishedIds() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository =
                FakeCollectionRepository().apply {
                    updateCollectionResult = AppResult.Error(RuntimeException("网络故障"), "网络异常")
                }
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.toggleWish(sampleSubject.id)
            advanceUntilIdle()

            // 验证失败后从 wishedSubjectIds 中回滚
            assertFalse(
                viewModel.uiState.value.wishedSubjectIds
                    .contains(sampleSubject.id),
            )
            assertEquals("网络异常", viewModel.uiState.value.userMessage)
        }

    @Test
    fun onSeasonSelectTriggersNewSearchWithUpdatedAirDate() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            val targetSeason = DEFAULT_SEASONS.first { it != ALL_TIME_SEASON }
            viewModel.onSeasonSelect(targetSeason)
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(targetSeason, viewModel.uiState.value.selectedSeason)
            assertEquals(targetSeason.airDateFilter, searchRepository.lastAdvancedRequest?.filter?.airDate)
            assertNull(viewModel.uiState.value.selectedMood)
        }

    @Test
    fun onCategorySelectUpdatesTypeAndSearches() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onCategorySelect(ExploreCategory.BOOK)
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(ExploreCategory.BOOK, viewModel.uiState.value.selectedCategory)
            assertEquals(listOf(1), searchRepository.lastAdvancedRequest?.filter?.type)
            assertNull(viewModel.uiState.value.selectedMood)
        }

    @Test
    fun onTagToggleTogglesTagAndSearches() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            // 选中标签
            viewModel.onTagToggle("科幻")
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(setOf("科幻"), viewModel.uiState.value.selectedTags)
            assertEquals(listOf("科幻"), searchRepository.lastAdvancedRequest?.filter?.tag)
            assertNull(viewModel.uiState.value.selectedMood)

            // 再次点击取消选中
            viewModel.onTagToggle("科幻")
            advanceUntilIdle()

            assertEquals(3, searchRepository.advancedSearchCallCount)
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertNull(searchRepository.lastAdvancedRequest?.filter?.tag)
        }

    @Test
    fun onSortSelectTriggersNewSearchWithUpdatedSortKey() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onSortSelect(ExploreSort.SCORE)
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(ExploreSort.SCORE, viewModel.uiState.value.selectedSort)
            assertEquals("score", searchRepository.lastAdvancedRequest?.sort)
        }

    @Test
    fun searchFailureSetsErrorAndRetryRecovers() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Error(RuntimeException("网络故障"), "探索加载失败")
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.subjects.isEmpty())
            assertEquals("探索加载失败", state.error)

            // 重试恢复
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            viewModel.retry()
            advanceUntilIdle()

            val updatedState = viewModel.uiState.value
            assertNull(updatedState.error)
            assertEquals(1, updatedState.subjects.size)
        }

    @Test
    fun refreshSetsRefreshingAndUpdatesSubjects() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(1, viewModel.uiState.value.subjects.size)
        }

    @Test
    fun loadMoreAppendsNewUniqueSubjects() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val initialSubjects = (1L..20L).map { sampleSubject.copy(id = it) }
            val nextSubject = sampleSubject.copy(id = 21L)
            searchRepository.advancedSearchResult = AppResult.Success(initialSubjects)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            assertEquals(20, viewModel.uiState.value.subjects.size)
            assertTrue(viewModel.uiState.value.hasMore)

            searchRepository.advancedSearchResult = AppResult.Success(listOf(nextSubject))
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(21, viewModel.uiState.value.subjects.size)
            assertEquals(
                21L,
                viewModel.uiState.value.subjects
                    .last()
                    .id,
            )
            assertFalse(viewModel.uiState.value.isLoadingMore)
            assertFalse(viewModel.uiState.value.hasMore)
        }

    @Test
    fun loadMore_usesServerCursorPageOffset_notListSize() =
        runTest {
            // 回归：去重会丢弃部分返回条目，若翻页 offset 用 subjects.size 而非服务端
            // 游标 pageOffset，两者错位会导致下一次请求跳过或重复返回数据
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val initialSubjects = (1L..20L).map { sampleSubject.copy(id = it) }
            searchRepository.advancedSearchResult = AppResult.Success(initialSubjects)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            // 第二页：返回 20 条，其中 5 条与首页重复被去重（列表只增长 15）
            val secondPage = (16L..35L).map { sampleSubject.copy(id = it) }
            searchRepository.advancedSearchResult = AppResult.Success(secondPage)
            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(35, viewModel.uiState.value.subjects.size)
            assertEquals(40, viewModel.uiState.value.pageOffset)

            // 第三页请求 offset 必须是 40（pageOffset），而非 subjects.size = 35
            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(3, searchRepository.advancedSearchCallCount)
            assertEquals(40, searchRepository.lastAdvancedOffset)
        }

    @Test
    fun onTagToggleAllowsMultiTagCombinationAndIntersection() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            // 1. 添加标签 "科幻"
            viewModel.onTagToggle("科幻")
            advanceUntilIdle()
            assertEquals(setOf("科幻"), viewModel.uiState.value.selectedTags)
            assertEquals(listOf("科幻"), searchRepository.lastAdvancedRequest?.filter?.tag)

            // 2. 组合标签 "悬疑"
            viewModel.onTagToggle("悬疑")
            advanceUntilIdle()
            assertEquals(setOf("科幻", "悬疑"), viewModel.uiState.value.selectedTags)
            assertEquals(listOf("科幻", "悬疑"), searchRepository.lastAdvancedRequest?.filter?.tag)

            // 3. 再次点击 "科幻" 取消勾选
            viewModel.onTagToggle("科幻")
            advanceUntilIdle()
            assertEquals(setOf("悬疑"), viewModel.uiState.value.selectedTags)
            assertEquals(listOf("悬疑"), searchRepository.lastAdvancedRequest?.filter?.tag)

            // 4. 清空全部标签
            viewModel.onClearAllTags()
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .isEmpty(),
            )
            assertNull(searchRepository.lastAdvancedRequest?.filter?.tag)
        }

    @Test
    fun onCustomTagSubmitUpdatesTagAndTriggersSearch() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onCustomTagSubmit("赛博朋克")
            advanceUntilIdle()

            assertEquals(2, searchRepository.advancedSearchCallCount)
            assertEquals(setOf("赛博朋克"), viewModel.uiState.value.selectedTags)
            assertEquals(listOf("赛博朋克"), searchRepository.lastAdvancedRequest?.filter?.tag)
            assertNull(viewModel.uiState.value.selectedMood)
        }

    @Test
    fun beginLogin_dismissesPromptAndReturnsUrl() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = false)
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.toggleWish(sampleSubject.id)
            assertTrue(viewModel.uiState.value.showLoginPromptDialog)

            // 登录由独立路由接管（应用内 WebView + ECH 通道），ViewModel 只负责收起提示
            viewModel.dismissLoginPrompt()

            assertFalse(viewModel.uiState.value.showLoginPromptDialog)
        }

    @Test
    fun initialLoadWithCommunityRepositoryFetchesHotComments() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val communityRepository = FakeCommunityRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val sampleComment =
                SubjectComment(
                    id = 101L,
                    comment = "公路旅行与时间流逝的沉淀，顶级作画和音乐！",
                    rate = 10,
                    user = CommentUser(id = 1L, username = "frieren_fan", nickname = "芙莉莲天下第一"),
                )
            communityRepository.setSubjectComments(
                subjectId = sampleSubject.id,
                page = SubjectCommentPage(total = 1, data = listOf(sampleComment)),
            )

            val viewModel =
                ExploreViewModel(
                    searchRepository = searchRepository,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                    communityRepository = communityRepository,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.hotComments.containsKey(sampleSubject.id))
            val comment = state.hotComments[sampleSubject.id]
            assertEquals("公路旅行与时间流逝的沉淀，顶级作画和音乐！", comment?.comment)
            assertEquals(10, comment?.rate)
            assertEquals("芙莉莲天下第一", comment?.user?.displayName)
        }

    @Test
    fun fetchHotCommentsPrefersSubstantialComments() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository(initialLoggedIn = true)
            val communityRepository = FakeCommunityRepository()
            searchRepository.advancedSearchResult = AppResult.Success(listOf(sampleSubject))

            val blankComment = SubjectComment(id = 1L, comment = "   ", rate = 8)
            val shortComment = SubjectComment(id = 2L, comment = "神", rate = 9)
            val substantialComment =
                SubjectComment(
                    id = 3L,
                    comment = "制作水准极高，对原作氛围的还原无可挑剔。",
                    rate = 10,
                    user = CommentUser(username = "critic"),
                )
            communityRepository.setSubjectComments(
                subjectId = sampleSubject.id,
                page = SubjectCommentPage(total = 3, data = listOf(blankComment, shortComment, substantialComment)),
            )

            val viewModel =
                ExploreViewModel(
                    searchRepository = searchRepository,
                    collectionRepository = collectionRepository,
                    authRepository = authRepository,
                    communityRepository = communityRepository,
                )
            advanceUntilIdle()

            val comment = viewModel.uiState.value.hotComments[sampleSubject.id]
            assertEquals(3L, comment?.id)
            assertEquals("制作水准极高，对原作氛围的还原无可挑剔。", comment?.comment)
        }

    @Test
    fun onSortSelect_persistsSortPreferenceToRepository() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onSortSelect(ExploreSort.HEAT)
            advanceUntilIdle()

            assertEquals("heat", searchRepository.exploreSortPreference)
            assertEquals(1, searchRepository.setExploreSortPreferenceCallCount)
        }

    @Test
    fun initialLoad_restoresPersistedSortPreference() =
        runTest {
            val searchRepository = FakeSearchRepository()
            searchRepository.exploreSortPreference = "score"
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            assertEquals(ExploreSort.SCORE, viewModel.uiState.value.selectedSort)
            assertNull(viewModel.uiState.value.selectedMood)
        }

    @Test
    fun onCustomTagSubmit_persistsTagToCustomFilterTagsAndTogglesSelection() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val collectionRepository = FakeCollectionRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = ExploreViewModel(searchRepository, collectionRepository, authRepository)
            advanceUntilIdle()

            viewModel.onCustomTagSubmit("百合")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.customFilterTags
                    .contains("百合"),
            )
            assertTrue(
                viewModel.uiState.value.selectedTags
                    .contains("百合"),
            )

            viewModel.onRemoveCustomTag("百合")
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.customFilterTags
                    .contains("百合"),
            )
        }
}
