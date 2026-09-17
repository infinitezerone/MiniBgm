package com.infinitezerone.minibgm.feature.user

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.data.sampleUserProfile
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserCollectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        authRepo: FakeAuthRepository =
            FakeAuthRepository(
                initialLoggedIn = true,
                initialProfile = sampleUserProfile,
            ),
        collectionRepo: FakeCollectionRepository = FakeCollectionRepository(),
    ): Pair<UserCollectionsViewModel, FakeCollectionRepository> {
        val viewModel =
            UserCollectionsViewModel(
                collectionRepository = collectionRepo,
                authRepository = authRepo,
            )
        return viewModel to collectionRepo
    }

    @Test
    fun initialState_notLoggedIn_setsLoginErrorMessage() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false)
            val (viewModel, _) = createViewModel(authRepo = authRepo)

            viewModel.setInitialType(CollectionType.DOING)

            val state = viewModel.uiState.first { it.error != null }
            assertFalse(state.isLoggedIn)
            assertEquals("请先登录 Bangumi 账号", state.error)
            assertTrue(state.collections.isEmpty())
        }

    @Test
    fun setInitialType_loadsCollectionsForActiveUser() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)

            val state = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertTrue(state.isLoggedIn)
            assertEquals(CollectionType.DOING, state.selectedType)
            assertEquals(1, state.collections.size)
            assertEquals(sampleSubject.id, state.collections.first().subjectId)
            assertEquals(12, state.collections.first().epStatus)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun selectType_updatesStateAndFetchesCollections() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val wishCollection = sampleUserCollection.copy(subjectId = 2002L, type = CollectionType.WISH.value)
            collectionRepo.sendCollection(sampleUserCollection)
            collectionRepo.sendCollection(wishCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val doingState = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(1, doingState.collections.size)
            assertEquals(sampleUserCollection.subjectId, doingState.collections.first().subjectId)

            viewModel.selectType(CollectionType.WISH)
            val wishState =
                viewModel.uiState.first {
                    it.selectedType == CollectionType.WISH &&
                        it.collections.any { c -> c.subjectId == 2002L }
                }
            assertEquals(CollectionType.WISH, wishState.selectedType)
            assertEquals(1, wishState.collections.size)
            assertEquals(2002L, wishState.collections.first().subjectId)
            assertEquals(2, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun selectSubjectFilter_updatesFilterAndFetches() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val bookCollection = sampleUserCollection.copy(subjectId = 3003L, subjectType = 1)
            collectionRepo.sendCollection(sampleUserCollection) // subjectType = 2 (anime)
            collectionRepo.sendCollection(bookCollection) // subjectType = 1 (book)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            viewModel.selectSubjectFilter(CollectionSubjectFilter.BOOK)

            val state =
                viewModel.uiState.first {
                    it.selectedSubjectFilter == CollectionSubjectFilter.BOOK &&
                        it.collections.any { c -> c.subjectId == 3003L }
                }
            assertEquals(CollectionSubjectFilter.BOOK, state.selectedSubjectFilter)
            assertEquals(1, state.collections.size)
            assertEquals(3003L, state.collections.first().subjectId)
        }

    @Test
    fun refresh_reloadsCollections() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)

            viewModel.refresh()
            viewModel.uiState.first { !it.isRefreshing }
            assertEquals(2, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun fetchError_updatesErrorMessage() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.fetchUserCollectionsResult = AppResult.Error(Exception("网络超时"), "获取用户收藏失败：网络超时")
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)

            val state = viewModel.uiState.first { it.error != null }
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("网络超时"))
        }

    @Test
    fun incrementEpisodeProgress_optimisticallyUpdatesAndCallsRepository() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val initial = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(12, initial.collections.first().epStatus)

            viewModel.incrementEpisodeProgress(sampleUserCollection)

            val updated = viewModel.uiState.first { it.collections.firstOrNull()?.epStatus == 13 }
            assertEquals(13, updated.collections.first().epStatus)
            assertEquals(1, collectionRepo.updateEpisodeCallCount)
        }

    @Test
    fun selectType_isolatesTabsAndDoesNotLeakPreviousTabContent() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val wishCollection = sampleUserCollection.copy(subjectId = 2002L, type = CollectionType.WISH.value)
            collectionRepo.sendCollection(sampleUserCollection)
            collectionRepo.sendCollection(wishCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val doingState = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(1, doingState.collections.size)
            assertEquals(sampleUserCollection.subjectId, doingState.collections.first().subjectId)

            // 切换到尚未加载过的 WISH
            viewModel.selectType(CollectionType.WISH)

            // 验证 WISH 的数据隔离，并成功加载对应数据
            val wishState =
                viewModel.uiState.first {
                    it.selectedType == CollectionType.WISH &&
                        it.collections.any { c -> c.subjectId == 2002L }
                }
            assertEquals(1, wishState.collections.size)
            assertEquals(2002L, wishState.collections.first().subjectId)

            // 切回已缓存的 DOING，应立即命中本地缓存，无需重新拉取网络
            viewModel.selectType(CollectionType.DOING)
            val cachedDoingState = viewModel.uiState.value
            assertEquals(CollectionType.DOING, cachedDoingState.selectedType)
            assertEquals(1, cachedDoingState.collections.size)
            assertEquals(sampleUserCollection.subjectId, cachedDoingState.collections.first().subjectId)
            assertEquals(2, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun setInitialType_whenCalledAgainOnRotation_doesNotResetSelectedType() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(CollectionType.DOING, viewModel.uiState.value.selectedType)

            // 用户手动切换到 WISH
            viewModel.selectType(CollectionType.WISH)
            assertEquals(CollectionType.WISH, viewModel.uiState.value.selectedType)

            // 模拟屏幕旋转，界面重新传入 initialType
            viewModel.setInitialType(CollectionType.DOING)
            // 选中的 Tab 必须保持在 WISH，不被踢回 DOING
            assertEquals(CollectionType.WISH, viewModel.uiState.value.selectedType)
        }

    @Test
    fun loadMore_appendsNewCollectionsAndUpdatesPaginationState() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val collections =
                (1L..60L).map { id ->
                    sampleUserCollection.copy(subjectId = id, type = CollectionType.DOING.value)
                }
            collections.forEach { collectionRepo.sendCollection(it) }

            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)

            val initial = viewModel.uiState.first { it.collections.size == 50 }
            assertEquals(50, initial.collections.size)
            assertTrue(initial.currentTabHasMore)
            assertFalse(initial.isCurrentTabLoadingMore)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)

            viewModel.loadMore(CollectionType.DOING)

            val loaded = viewModel.uiState.first { it.collections.size == 60 }
            assertEquals(60, loaded.collections.size)
            assertFalse(loaded.currentTabHasMore)
            assertFalse(loaded.isCurrentTabLoadingMore)
            assertEquals(2, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun loadMore_whenHasMoreIsFalse_doesNotTriggerFetch() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val initial = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(1, initial.collections.size)
            assertFalse(initial.currentTabHasMore)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)

            viewModel.loadMore(CollectionType.DOING)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun loginAfterUnauthenticated_triggersReloadAndClearsError() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = false, initialProfile = null)
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val unauthState = viewModel.uiState.first { it.error != null }
            assertFalse(unauthState.isLoggedIn)
            assertEquals("请先登录 Bangumi 账号", unauthState.error)
            assertTrue(unauthState.collections.isEmpty())

            // 用户登录成功
            authRepo.setLoggedIn(true)
            authRepo.setActiveProfile(sampleUserProfile)

            // 验证自动触发加载并清空错误态
            val loggedInState = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertTrue(loggedInState.isLoggedIn)
            assertNull(loggedInState.error)
            assertEquals(1, loggedInState.collections.size)
            assertEquals(sampleSubject.id, loggedInState.collections.first().subjectId)
            assertEquals(1, collectionRepo.fetchUserCollectionsCallCount)
        }

    @Test
    fun logout_clearsCollectionsAndSetsUnauthenticatedError() =
        runTest {
            val authRepo = FakeAuthRepository(initialLoggedIn = true, initialProfile = sampleUserProfile)
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.sendCollection(sampleUserCollection)
            val (viewModel, _) = createViewModel(authRepo = authRepo, collectionRepo = collectionRepo)

            viewModel.setInitialType(CollectionType.DOING)
            val loggedInState = viewModel.uiState.first { it.collections.isNotEmpty() }
            assertEquals(1, loggedInState.collections.size)

            // 账号登出
            authRepo.setLoggedIn(false)
            authRepo.setActiveProfile(null)

            // 验证已缓存的收藏数据被清空，并显示未登录提示
            val loggedOutState = viewModel.uiState.first { !it.isLoggedIn && it.error != null }
            assertEquals("请先登录 Bangumi 账号", loggedOutState.error)
            assertTrue(loggedOutState.collections.isEmpty())
        }
}
