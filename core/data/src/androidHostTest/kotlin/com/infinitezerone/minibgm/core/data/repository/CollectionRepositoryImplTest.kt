package com.infinitezerone.minibgm.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.TokenProvider
import com.infinitezerone.minibgm.core.database.dao.UserCollectionDao
import com.infinitezerone.minibgm.core.database.entity.UserCollectionEntity
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmAuthConfig
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.BgmTokenService
import com.infinitezerone.minibgm.core.network.model.PageResponse
import com.infinitezerone.minibgm.core.network.model.UserCollectionPageResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CollectionRepositoryImpl 的会话派生与云端收藏批量同步行为 */
class CollectionRepositoryImplTest {
    private class FakeTokenProvider : TokenProvider {
        private val activeUserIdState = MutableStateFlow<Long?>(null)
        override val activeUserId: Flow<Long?> = activeUserIdState
        override val hasTokens: Flow<Boolean> = activeUserIdState.map { it != null }

        override suspend fun getAccessToken(): String? = if (activeUserIdState.value != null) "access" else null

        override suspend fun getRefreshToken(): String? = if (activeUserIdState.value != null) "refresh" else null

        override suspend fun saveTokens(
            userId: Long,
            accessToken: String,
            refreshToken: String,
        ) {
            activeUserIdState.value = userId.takeIf { it != 0L }
        }

        override suspend fun saveTokens(
            accessToken: String,
            refreshToken: String,
        ) = Unit

        override suspend fun setActiveUser(userId: Long) {
            activeUserIdState.value = userId.takeIf { it != 0L }
        }

        override suspend fun removeTokens(userId: Long) {
            if (activeUserIdState.value == userId) activeUserIdState.value = null
        }

        override suspend fun clearTokens() {
            activeUserIdState.value = null
        }
    }

    private class FakeUserCollectionDao : UserCollectionDao {
        val stored = MutableStateFlow<List<UserCollectionEntity>>(emptyList())

        override fun getCollectionsByType(
            userId: Long,
            type: Int,
        ): Flow<List<UserCollectionEntity>> = stored.map { list -> list.filter { it.userId == userId && it.type == type } }

        override fun getCollectionBySubjectId(
            userId: Long,
            subjectId: Long,
        ): Flow<UserCollectionEntity?> = stored.map { list -> list.firstOrNull { it.userId == userId && it.subjectId == subjectId } }

        override suspend fun insertCollection(collection: UserCollectionEntity) {
            stored.value = stored.value.filterNot { it.userId == collection.userId && it.subjectId == collection.subjectId } + collection
        }

        override suspend fun insertCollections(collections: List<UserCollectionEntity>) {
            stored.value =
                stored.value
                    .filterNot { existing -> collections.any { it.userId == existing.userId && it.subjectId == existing.subjectId } } +
                collections
        }

        override suspend fun deleteBySubjectId(
            userId: Long,
            subjectId: Long,
        ) {
            stored.value = stored.value.filterNot { it.userId == userId && it.subjectId == subjectId }
        }

        override suspend fun deleteByType(
            userId: Long,
            type: Int,
        ) {
            stored.value = stored.value.filterNot { it.userId == userId && it.type == type }
        }

        override suspend fun replaceCollectionsByType(
            userId: Long,
            type: Int,
            collections: List<UserCollectionEntity>,
        ) {
            stored.value = stored.value.filterNot { it.userId == userId && it.type == type } + collections
        }

        override suspend fun clearByUserId(userId: Long) {
            stored.value = stored.value.filterNot { it.userId == userId }
        }

        override suspend fun clearAll() {
            stored.value = emptyList()
        }
    }

    private class FakeBangumiApiService : BangumiApiService {
        /** offset -> 分页响应；null 表示该页抛异常（模拟网络错误） */
        val collectionPages = MutableStateFlow<Map<Int, List<UserCollection>>>(emptyMap())
        var collectionsTotal = 0
        val requests = mutableListOf<Pair<Int, Int>>() // limit to offset

        var cancellationToThrow: Boolean = false

        override suspend fun getUserCollections(
            username: String,
            subjectType: Int,
            type: Int?,
            limit: Int,
            offset: Int,
        ): UserCollectionPageResponse {
            if (cancellationToThrow) throw kotlinx.coroutines.CancellationException("Job cancelled")
            requests += limit to offset
            val page = collectionPages.value[offset] ?: error("Network failure")
            return PageResponse(total = collectionsTotal, data = page)
        }

        override suspend fun getCalendar() = error("Not needed")

        override suspend fun getSubject(id: Long) = error("Not needed")

        override suspend fun getSubjectCharacters(id: Long) = error("Not needed")

        override suspend fun getCharacter(id: Long) = error("Not needed")

        override suspend fun getCharacterSubjects(id: Long) = error("Not needed")

        override suspend fun getSubjectPersons(id: Long) = error("Not needed")

        override suspend fun getPerson(id: Long) = error("Not needed")

        override suspend fun getPersonSubjects(id: Long) = error("Not needed")

        override suspend fun getSubjectRelations(id: Long) = error("Not needed")

        data class UpdateCollectionCall(
            val subjectId: Long,
            val type: Int,
            val rate: Int?,
            val comment: String?,
            val private: Boolean,
            val epStatus: Int?,
        )

        data class UpdateEpisodeCall(
            val subjectId: Long,
            val episodeId: Long,
            val type: Int,
        )

        var collectionToReturn: UserCollection? = null
        val updateCollectionCalls = mutableListOf<UpdateCollectionCall>()
        val updateEpisodeCalls = mutableListOf<UpdateEpisodeCall>()
        var episodesToReturn: List<com.infinitezerone.minibgm.core.model.Episode> = emptyList()
        var shouldThrowOnUpdateCollection = false
        var shouldThrowOnUpdateEpisode = false
        var onBeforeUpdateCollection: (suspend () -> Unit)? = null
        var onBeforeUpdateEpisode: (suspend () -> Unit)? = null

        override suspend fun getEpisodes(
            subjectId: Long,
            limit: Int,
            offset: Int,
        ): PageResponse<com.infinitezerone.minibgm.core.model.Episode> =
            PageResponse(total = episodesToReturn.size, limit = limit, offset = offset, data = episodesToReturn)

        override suspend fun searchSubjects(
            keyword: String,
            type: Int,
            limit: Int,
            offset: Int,
        ) = error("Not needed")

        override suspend fun searchSubjectsAdvanced(
            request: com.infinitezerone.minibgm.core.model.SearchSubjectsRequest,
            limit: Int,
            offset: Int,
        ) = error("Not needed")

        override suspend fun getMe(): UserProfile = error("Not needed")

        var collectionStatsToReturn: List<com.infinitezerone.minibgm.core.network.UserCollectionStatusGroup> = emptyList()
        var getUserCollectionStatsCallCount = 0

        override suspend fun getUserCollectionStats(
            username: String,
        ): List<com.infinitezerone.minibgm.core.network.UserCollectionStatusGroup> {
            getUserCollectionStatsCallCount++
            return collectionStatsToReturn
        }

        var getCollectionCallCount = 0

        override suspend fun getCollection(
            username: String,
            subjectId: Long,
        ): UserCollection? {
            getCollectionCallCount++
            return collectionToReturn
        }

        override suspend fun updateCollection(
            subjectId: Long,
            type: Int,
            rate: Int?,
            comment: String?,
            private: Boolean,
            epStatus: Int?,
        ) {
            onBeforeUpdateCollection?.invoke()
            if (shouldThrowOnUpdateCollection) {
                throw BgmNetworkException.ServerError(500, "Mock server error")
            }
            updateCollectionCalls.add(UpdateCollectionCall(subjectId, type, rate, comment, private, epStatus))
        }

        var throwNotFoundOnFirstUpdateEpisode = false
        var updateEpisodeNotFoundCount = 0

        override suspend fun updateEpisodeStatus(
            subjectId: Long,
            episodeId: Long,
            type: Int,
        ) {
            onBeforeUpdateEpisode?.invoke()
            if (throwNotFoundOnFirstUpdateEpisode && updateEpisodeNotFoundCount == 0) {
                updateEpisodeNotFoundCount++
                throw BgmNetworkException.NotFound("Mock 404 Not Found")
            }
            if (shouldThrowOnUpdateEpisode) {
                throw BgmNetworkException.ServerError(500, "Mock server error")
            }
            updateEpisodeCalls.add(UpdateEpisodeCall(subjectId, episodeId, type))
        }

        data class UpdateEpisodesCall(
            val subjectId: Long,
            val episodeIds: List<Long>,
            val type: Int,
        )

        val updateEpisodesCalls = mutableListOf<UpdateEpisodesCall>()
        var throwNotFoundOnFirstUpdateEpisodes = false
        var updateEpisodesNotFoundCount = 0
        var shouldThrowOnUpdateEpisodes = false

        override suspend fun updateEpisodesStatus(
            subjectId: Long,
            episodeIds: List<Long>,
            type: Int,
        ) {
            if (throwNotFoundOnFirstUpdateEpisodes && updateEpisodesNotFoundCount == 0) {
                updateEpisodesNotFoundCount++
                throw BgmNetworkException.NotFound("Mock 404 Not Found")
            }
            if (shouldThrowOnUpdateEpisodes) {
                throw BgmNetworkException.ServerError(500, "Mock server error")
            }
            updateEpisodesCalls.add(UpdateEpisodesCall(subjectId, episodeIds, type))
        }
    }

    /** 内存版 Storage，绕开 DataStore 文件系统依赖（见 AuthRepositoryImplTest 同名注释） */
    private class InMemoryStorage : Storage<UserPreferences> {
        @Volatile
        private var data: UserPreferences = UserPreferences()
        private val coordinator =
            object : InterProcessCoordinator {
                private val mutex = Mutex()
                private var version = 0
                override val updateNotifications: Flow<Unit> = emptyFlow()

                override suspend fun <T> lock(block: suspend () -> T) = mutex.withLock { block() }

                override suspend fun <T> tryLock(block: suspend (Boolean) -> T): T {
                    if (!mutex.tryLock()) return block(false)
                    return try {
                        block(true)
                    } finally {
                        mutex.unlock()
                    }
                }

                override suspend fun getVersion(): Int = version

                override suspend fun incrementAndGetVersion(): Int = ++version
            }

        override fun createConnection(): StorageConnection<UserPreferences> =
            object : StorageConnection<UserPreferences> {
                override val coordinator: InterProcessCoordinator = this@InMemoryStorage.coordinator

                override suspend fun <R> readScope(block: suspend (ReadScope<UserPreferences>, Boolean) -> R): R =
                    block(
                        object : ReadScope<UserPreferences> {
                            override suspend fun readData(): UserPreferences = data

                            override fun close() {}
                        },
                        true,
                    )

                override suspend fun writeScope(block: suspend (WriteScope<UserPreferences>) -> Unit) {
                    block(
                        object : WriteScope<UserPreferences> {
                            override suspend fun readData(): UserPreferences = data

                            override suspend fun writeData(value: UserPreferences) {
                                data = value
                            }

                            override fun close() {}
                        },
                    )
                }

                override fun close() {}
            }
    }

    private class Harness {
        val tokenProvider = FakeTokenProvider()
        val dao = FakeUserCollectionDao()
        val api = FakeBangumiApiService()
        private val dataStore: DataStore<UserPreferences> = DataStoreFactory.create(storage = InMemoryStorage())

        val repository =
            CollectionRepositoryImpl(
                apiService = api,
                userCollectionDao = dao,
                tokenProvider = tokenProvider,
            )
    }

    private fun collection(
        subjectId: Long,
        type: Int = 3, // DOING
    ): UserCollection =
        UserCollection(
            userId = 42L,
            subjectId = subjectId,
            subjectType = 2,
            rate = 0,
            type = type,
            comment = "",
            epStatus = 1,
            volStatus = 0,
            updatedAt = "2026-09-08T00:00:00Z",
        )

    @Test
    fun syncWatchingCollections_paginatesAndUpsertsIntoLocalDb() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            // 两页数据：50 + 20，total=70 驱动翻页
            harness.api.collectionsTotal = 70
            harness.api.collectionPages.value =
                mapOf(
                    0 to (1L..50L).map { collection(it) },
                    50 to (51L..70L).map { collection(it) },
                )

            val result = harness.repository.syncWatchingCollections()

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(2, harness.api.requests.size)
            assertEquals(50 to 0, harness.api.requests[0])
            assertEquals(50 to 50, harness.api.requests[1])
            assertEquals(70, harness.dao.stored.value.size)
        }

    @Test
    fun syncWatchingCollections_withoutSession_returnsError() =
        runTest {
            val harness = Harness()

            val result = harness.repository.syncWatchingCollections()

            assertIs<AppResult.Error>(result)
            assertTrue(harness.api.requests.isEmpty())
        }

    @Test
    fun syncWatchingCollections_remoteFailure_returnsErrorWithoutPartialCommit() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            // 第一页正常，第二页网络错误
            harness.api.collectionsTotal = 200
            harness.api.collectionPages.value = mapOf(0 to (1L..50L).map { collection(it) })

            val result = harness.repository.syncWatchingCollections()

            assertIs<AppResult.Error>(result)
            assertTrue(
                harness.dao.stored.value
                    .isEmpty(),
            )
        }

    @Test
    fun syncWatchingCollections_replacesStaleCollections() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            // 本地原本有 999L (DOING)
            harness.dao.insertCollection(collection(999L).asEntity(42L))
            assertEquals(1, harness.dao.stored.value.size)

            // 远端只有 1L
            harness.api.collectionsTotal = 1
            harness.api.collectionPages.value = mapOf(0 to listOf(collection(1L)))

            val result = harness.repository.syncWatchingCollections()
            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.dao.stored.value.size)
            assertEquals(
                1L,
                harness.dao.stored.value
                    .first()
                    .subjectId,
            )
        }

    @Test
    fun getCollectionsByTypeStream_followsSessionLifecycle() =
        runTest {
            val harness = Harness()
            val typeFlow = harness.repository.getCollectionsByTypeStream(com.infinitezerone.minibgm.core.model.CollectionType.DOING)

            // 未登录：凭据库无活跃用户，即使本地库有数据也不得泄漏
            harness.dao.insertCollections(listOf(collection(1L).asEntity(42L)))
            assertTrue(typeFlow.first().isEmpty())

            // 登录后：凭据库活跃用户到位，数据可见
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            assertEquals(1, typeFlow.first().size)
        }

    @Test
    fun activeProfile_derivedFromCredentialStoreAndPool() =
        runTest {
            val dataStore: DataStore<UserPreferences> = DataStoreFactory.create(storage = InMemoryStorage())
            val prefs = UserPreferencesDataSource(dataStore)
            val tokenProvider = FakeTokenProvider()
            val repository =
                AuthRepositoryImpl(
                    tokenService = BgmTokenService(HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }), BgmAuthConfig()),
                    tokenProvider = tokenProvider,
                    userPreferences = prefs,
                    authConfig = BgmAuthConfig(),
                    apiService = FakeBangumiApiService(),
                    userDataCleaner =
                        com.infinitezerone.minibgm.core.data.util
                            .UserDataCleaner(emptyList()),
                )

            // 资料池有数据但无凭据：不得视为已登录
            prefs.saveUserProfile(UserProfile(id = 42L, username = "u"))
            assertTrue(!repository.isLoggedIn.first())
            assertNull(repository.activeProfile.first())

            // 同账号凭据到位：登录态与资料立即成立
            tokenProvider.saveTokens(42L, "at", "rt")
            assertTrue(repository.isLoggedIn.first())
            assertEquals(42L, repository.activeProfile.first()?.id)
        }

    @Test
    fun updateCollectionStatus_withoutSession_returnsError() =
        runTest {
            val harness = Harness()
            val result =
                harness.repository.updateCollectionStatus(
                    subjectId = 100L,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING,
                )
            assertIs<AppResult.Error>(result)
            assertTrue(harness.api.updateCollectionCalls.isEmpty())
        }

    @Test
    fun updateCollectionStatus_success_updatesRemoteAndLocalDatabase() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            val result =
                harness.repository.updateCollectionStatus(
                    subjectId = 100L,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING,
                    rate = 9,
                    comment = "神作",
                    epStatus = 5,
                    subjectType = 2,
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.api.updateCollectionCalls.size)
            val call = harness.api.updateCollectionCalls.first()
            assertEquals(100L, call.subjectId)
            assertEquals(com.infinitezerone.minibgm.core.model.CollectionType.DOING.value, call.type)
            assertEquals(9, call.rate)
            assertEquals("神作", call.comment)
            assertEquals(5, call.epStatus)

            // 验证写入 Room 的本地实体字段（仅追番核心字段）
            val stored =
                harness.dao.stored.value
                    .firstOrNull { it.subjectId == 100L }
            assertTrue(stored != null)
            assertEquals(42L, stored.userId)
            assertEquals(100L, stored.subjectId)
            assertEquals(2, stored.subjectType)
            assertEquals(com.infinitezerone.minibgm.core.model.CollectionType.DOING.value, stored.type)
            assertEquals(5, stored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_withoutSession_returnsError() =
        runTest {
            val harness = Harness()
            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1001L,
                    isWatched = true,
                    epNumber = 1,
                )
            assertIs<AppResult.Error>(result)
            assertTrue(harness.api.updateEpisodeCalls.isEmpty())
        }

    @Test
    fun updateEpisodeStatus_wishAutoTransitionsToDoing_updatesRemoteAndLocal() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.collectionToReturn =
                UserCollection(
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.WISH.value,
                    epStatus = 0,
                )

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1001L,
                    isWatched = true,
                    epNumber = 1,
                )

            assertIs<AppResult.Success<Unit>>(result)
            // 自动提升条目状态为 DOING
            assertEquals(1, harness.api.updateCollectionCalls.size)
            assertEquals(
                com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                harness.api.updateCollectionCalls
                    .first()
                    .type,
            )
            // 调用单集打卡接口
            assertEquals(1, harness.api.updateEpisodeCalls.size)
            assertEquals(
                1001L,
                harness.api.updateEpisodeCalls
                    .first()
                    .episodeId,
            )
            assertEquals(
                2,
                harness.api.updateEpisodeCalls
                    .first()
                    .type,
            ) // 2 = watched

            // 本地 Room 记录也是 DOING，epStatus = 1
            val stored =
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
            assertEquals(com.infinitezerone.minibgm.core.model.CollectionType.DOING.value, stored.type)
            assertEquals(1, stored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_uncheckHistoricalEpisode_doesNotRegressHighestProgress() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.collectionToReturn =
                UserCollection(
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 10,
                )

            // 取消标记第 3 集，当前总进度已达 10
            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1003L,
                    isWatched = false,
                    epNumber = 3,
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.api.updateEpisodeCalls.size)
            assertEquals(
                0,
                harness.api.updateEpisodeCalls
                    .first()
                    .type,
            ) // 0 = uncheck

            // 本地进度仍维持在 10，不倒退
            val stored =
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
            assertEquals(10, stored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_resolvesEpisodeIdWhenNotProvided() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.collectionToReturn =
                UserCollection(
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 0,
                )
            harness.api.episodesToReturn =
                listOf(
                    com.infinitezerone.minibgm.core.model.Episode(
                        id = 555L,
                        name = "第1话",
                        nameCn = "第1话",
                        ep = 1.0f,
                        sort = 1.0f,
                    ),
                )

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = null, // 无单集 ID
                    isWatched = true,
                    epNumber = 1,
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.api.updateEpisodeCalls.size)
            assertEquals(
                555L,
                harness.api.updateEpisodeCalls
                    .first()
                    .episodeId,
            )
        }

    @Test
    fun updateEpisodeStatus_uncheckHighestEpisode_regressesToPreviousEpisode() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.collectionToReturn =
                UserCollection(
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 5,
                )

            // 取消标记正是当前最大进度的第 5 集，应回退到第 4 集
            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1005L,
                    isWatched = false,
                    epNumber = 5,
                )

            assertIs<AppResult.Success<Unit>>(result)
            val stored =
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
            assertEquals(4, stored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_watchEarlierEpisode_preservesHighestProgress() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.collectionToReturn =
                UserCollection(
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 8,
                )

            // 补打卡第 3 集，当前总进度已达 8，总进度应维持 8
            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1003L,
                    isWatched = true,
                    epNumber = 3,
                )

            assertIs<AppResult.Success<Unit>>(result)
            val stored =
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
            assertEquals(8, stored.epStatus)
        }

    @Test
    fun syncWatchingCollections_emptyRemote_clearsAllLocalWatching() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            // 本地原本有追番数据
            harness.dao.insertCollection(collection(999L).asEntity(42L))
            assertEquals(1, harness.dao.stored.value.size)

            // 远端返回 total = 0
            harness.api.collectionsTotal = 0
            harness.api.collectionPages.value = mapOf(0 to emptyList())

            val result = harness.repository.syncWatchingCollections()
            assertIs<AppResult.Success<Unit>>(result)
            // 本地在看数据应被原子清空
            assertTrue(
                harness.dao.stored.value
                    .isEmpty(),
            )
        }

    @Test
    fun updateCollectionStatus_remoteFailure_rollsBackOptimisticWrite() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.shouldThrowOnUpdateCollection = true

            // 情况 1：原本本地无记录，远端失败后应完全删除本地乐观插入的记录
            val result1 =
                harness.repository.updateCollectionStatus(
                    subjectId = 100L,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING,
                )
            assertIs<AppResult.Error>(result1)
            assertNull(
                harness.dao.stored.value
                    .firstOrNull { it.subjectId == 100L },
            )

            // 情况 2：原本本地已有记录（例如想看 WISH，epStatus = 0），远端失败后应回滚到原快照
            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.WISH.value,
                    epStatus = 0,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            val result2 =
                harness.repository.updateCollectionStatus(
                    subjectId = 100L,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING,
                    epStatus = 5,
                )
            assertIs<AppResult.Error>(result2)
            val restored =
                harness.dao.stored.value
                    .firstOrNull { it.subjectId == 100L }
            assertNotNull(restored)
            assertEquals(com.infinitezerone.minibgm.core.model.CollectionType.WISH.value, restored.type)
            assertEquals(0, restored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_remoteFailure_rollsBackOptimisticWrite() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.shouldThrowOnUpdateEpisode = true

            // 情况 1：原本本地无记录，打卡失败回滚后删除乐观数据
            val result1 =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1001L,
                    isWatched = true,
                    epNumber = 1,
                )
            assertIs<AppResult.Error>(result1)
            assertNull(
                harness.dao.stored.value
                    .firstOrNull { it.subjectId == 100L },
            )

            // 情况 2：原本已有进度 epStatus = 2，打卡第 3 集失败回滚至原快照
            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 2,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            val result2 =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1003L,
                    isWatched = true,
                    epNumber = 3,
                )
            assertIs<AppResult.Error>(result2)
            val restored =
                harness.dao.stored.value
                    .firstOrNull { it.subjectId == 100L }
            assertNotNull(restored)
            assertEquals(2, restored.epStatus)
        }

    @Test
    fun updateEpisodeStatus_optimisticWrite_immediatelyUpdatesLocalBeforeRemote() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = com.infinitezerone.minibgm.core.model.CollectionType.DOING.value,
                    epStatus = 2,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            var observedOptimisticStatusDuringRemote: Int? = null
            harness.api.onBeforeUpdateEpisode = {
                // 在远端请求执行期间检查 Room 数据库，验证 Stage 1 乐观写入已生效
                observedOptimisticStatusDuringRemote =
                    harness.dao.stored.value
                        .firstOrNull { it.subjectId == 100L }
                        ?.epStatus
            }

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1003L,
                    isWatched = true,
                    epNumber = 3,
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(3, observedOptimisticStatusDuringRemote)
            assertEquals(
                3,
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
                    .epStatus,
            )
        }

    @Test
    fun updateEpisodeStatus_accountSwitchDuringInFlightRequest_bindsToOriginalActiveUid() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            // 请求发出前是用户 42
            harness.api.onBeforeUpdateEpisode = {
                // 模拟在打卡网络请求途中切换账号到 999
                harness.tokenProvider.saveTokens(999L, "at2", "rt2")
            }

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1001L,
                    isWatched = true,
                    epNumber = 1,
                )

            assertIs<AppResult.Success<Unit>>(result)
            // 写入的实体必须归属于发起操作时的活跃账号 42，严禁污染新账号 999
            val stored42 =
                harness.dao.stored.value
                    .firstOrNull { it.userId == 42L && it.subjectId == 100L }
            assertNotNull(stored42)
            assertEquals(1, stored42.epStatus)
            assertNull(
                harness.dao.stored.value
                    .firstOrNull { it.userId == 999L },
            )
        }

    @Test
    fun cancellationException_rethrowsFromCollectionRepositoryMethods() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "access", "refresh")
            harness.api.cancellationToThrow = true

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                harness.repository.fetchUserCollections("testuser")
            }

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                harness.repository.fetchCollectionCount("testuser", com.infinitezerone.minibgm.core.model.CollectionType.DOING)
            }

            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                harness.repository.syncWatchingCollections()
            }
        }

    @Test
    fun fetchCollectionCounts_aggregatesAllCategoriesAndCachesResult() =
        runTest {
            val harness = Harness()
            val sampleStats =
                listOf(
                    com.infinitezerone.minibgm.core.network.UserCollectionStatusGroup(
                        type = 2,
                        name = "anime",
                        collects =
                            listOf(
                                com.infinitezerone.minibgm.core.network.UserCollectionStatusEntry(
                                    status =
                                        com.infinitezerone.minibgm.core.network.UserCollectionStatusDetail(
                                            id = 3,
                                            type = "do",
                                            name = "在看",
                                        ),
                                    count = 12,
                                ),
                                com.infinitezerone.minibgm.core.network.UserCollectionStatusEntry(
                                    status =
                                        com.infinitezerone.minibgm.core.network.UserCollectionStatusDetail(
                                            id = 1,
                                            type = "wish",
                                            name = "想看",
                                        ),
                                    count = 5,
                                ),
                            ),
                    ),
                    com.infinitezerone.minibgm.core.network.UserCollectionStatusGroup(
                        type = 1,
                        name = "book",
                        collects =
                            listOf(
                                com.infinitezerone.minibgm.core.network.UserCollectionStatusEntry(
                                    status =
                                        com.infinitezerone.minibgm.core.network.UserCollectionStatusDetail(
                                            id = 3,
                                            type = "do",
                                            name = "在读",
                                        ),
                                    count = 3,
                                ),
                            ),
                    ),
                )
            harness.api.collectionStatsToReturn = sampleStats

            // 1. 首次加载：请求网络并聚合各分类
            val firstResult = harness.repository.fetchCollectionCounts("testuser", force = false)
            assertTrue(firstResult is AppResult.Success)
            val counts = firstResult.data
            assertEquals(15, counts[CollectionType.DOING]) // 12 + 3
            assertEquals(5, counts[CollectionType.WISH])
            assertEquals(0, counts[CollectionType.COLLECT])
            assertEquals(1, harness.api.getUserCollectionStatsCallCount)

            // 2. 二次调用（非强制）：命中内存缓存，不发网络请求
            val cachedResult = harness.repository.fetchCollectionCounts("testuser", force = false)
            assertTrue(cachedResult is AppResult.Success)
            assertEquals(1, harness.api.getUserCollectionStatsCallCount)

            // 3. 强制刷新：绕过缓存重新发请求
            val forceResult = harness.repository.fetchCollectionCounts("testuser", force = true)
            assertTrue(forceResult is AppResult.Success)
            assertEquals(2, harness.api.getUserCollectionStatsCallCount)
        }

    @Test
    fun updateEpisodeStatus_whenAlreadyInCollection_skipsGetCollectionAndDirectlyChecksIn() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = CollectionType.DOING.value,
                    epStatus = 2,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1003L,
                    isWatched = true,
                    epNumber = 3,
                )

            assertIs<AppResult.Success<Unit>>(result)
            // 验证未调用冗余的 getCollection 与 updateCollection，仅发 1 次打卡请求
            assertEquals(0, harness.api.getCollectionCallCount)
            assertEquals(0, harness.api.updateCollectionCalls.size)
            assertEquals(1, harness.api.updateEpisodeCalls.size)
            assertEquals(
                1003L,
                harness.api.updateEpisodeCalls
                    .first()
                    .episodeId,
            )
            assertEquals(
                2,
                harness.api.updateEpisodeCalls
                    .first()
                    .type,
            )
        }

    @Test
    fun updateEpisodeStatus_whenServerReturnsNotFound_selfHealsAndRetries() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = CollectionType.DOING.value,
                    epStatus = 1,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            // 首次打卡报 404
            harness.api.throwNotFoundOnFirstUpdateEpisode = true

            val result =
                harness.repository.updateEpisodeStatus(
                    subjectId = 100L,
                    episodeId = 1002L,
                    isWatched = true,
                    epNumber = 2,
                )

            assertIs<AppResult.Success<Unit>>(result)
            // 验证自愈触发：补调 updateCollection 加入在看，然后再次尝试 updateEpisodeStatus 成功
            assertEquals(1, harness.api.updateEpisodeNotFoundCount)
            assertEquals(1, harness.api.updateCollectionCalls.size)
            assertEquals(
                CollectionType.DOING.value,
                harness.api.updateCollectionCalls
                    .first()
                    .type,
            )
            assertEquals(1, harness.api.updateEpisodeCalls.size)
            assertEquals(
                2,
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
                    .epStatus,
            )
        }

    @Test
    fun markEpisodesWatchedUpTo_withProvidedEpisodeIds_updatesCollectionAndCallsApi() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")

            val originalEntity =
                UserCollectionEntity(
                    userId = 42L,
                    subjectId = 100L,
                    subjectType = 2,
                    type = CollectionType.DOING.value,
                    epStatus = 1,
                    updatedAt = "2026-09-08T00:00:00Z",
                )
            harness.dao.insertCollection(originalEntity)

            val result =
                harness.repository.markEpisodesWatchedUpTo(
                    subjectId = 100L,
                    epNumber = 3,
                    episodeIds = listOf(1001L, 1002L, 1003L),
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.api.updateEpisodesCalls.size)
            val call = harness.api.updateEpisodesCalls.first()
            assertEquals(100L, call.subjectId)
            assertEquals(listOf(1001L, 1002L, 1003L), call.episodeIds)
            assertEquals(2, call.type)

            val stored =
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
            assertEquals(3, stored.epStatus)
            assertEquals(CollectionType.DOING.value, stored.type)
        }

    @Test
    fun markEpisodesWatchedUpTo_withoutEpisodeIds_resolvesFromApi() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.episodesToReturn =
                listOf(
                    com.infinitezerone.minibgm.core.model
                        .Episode(id = 101L, sort = 1f, ep = 1f, name = "Ep 1"),
                    com.infinitezerone.minibgm.core.model
                        .Episode(id = 102L, sort = 2f, ep = 2f, name = "Ep 2"),
                    com.infinitezerone.minibgm.core.model
                        .Episode(id = 103L, sort = 3f, ep = 3f, name = "Ep 3"),
                    com.infinitezerone.minibgm.core.model
                        .Episode(id = 104L, sort = 4f, ep = 4f, name = "Ep 4"),
                )

            val result =
                harness.repository.markEpisodesWatchedUpTo(
                    subjectId = 100L,
                    epNumber = 2,
                )

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, harness.api.updateEpisodesCalls.size)
            assertEquals(
                listOf(101L, 102L),
                harness.api.updateEpisodesCalls
                    .first()
                    .episodeIds,
            )
            assertEquals(
                2,
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
                    .epStatus,
            )
        }

    @Test
    fun markEpisodesWatchedUpTo_notInCollection_createsCollectionAndSelfHeals() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.throwNotFoundOnFirstUpdateEpisodes = true

            val result =
                harness.repository.markEpisodesWatchedUpTo(
                    subjectId = 100L,
                    epNumber = 1,
                    episodeIds = listOf(101L),
                )

            assertIs<AppResult.Success<Unit>>(result)
            // 先尝试创建在看，打卡报 404 后自愈重试，最终成功
            assertEquals(1, harness.api.updateEpisodesNotFoundCount)
            assertEquals(1, harness.api.updateEpisodesCalls.size)
            assertEquals(
                1,
                harness.dao.stored.value
                    .first { it.subjectId == 100L }
                    .epStatus,
            )
        }

    @Test
    fun markEpisodesWatchedUpTo_whenNotLoggedIn_returnsError() =
        runTest {
            val harness = Harness()
            val result = harness.repository.markEpisodesWatchedUpTo(100L, epNumber = 1)
            assertIs<AppResult.Error>(result)
        }

    @Test
    fun markEpisodesWatchedUpTo_whenNetworkFails_rollsBackRoom() =
        runTest {
            val harness = Harness()
            harness.tokenProvider.saveTokens(42L, "at", "rt")
            harness.api.shouldThrowOnUpdateEpisodes = true

            val result =
                harness.repository.markEpisodesWatchedUpTo(
                    subjectId = 100L,
                    epNumber = 2,
                    episodeIds = listOf(101L, 102L),
                )

            assertIs<AppResult.Error>(result)
            // 失败后回滚，Room 中不应保留未确认的状态
            assertEquals(
                emptyList(),
                harness.dao.stored.value
                    .filter { it.subjectId == 100L },
            )
        }
}
