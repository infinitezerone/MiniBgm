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

        override suspend fun getUserCollections(
            username: String,
            subjectType: Int,
            type: Int?,
            limit: Int,
            offset: Int,
        ): UserCollectionPageResponse {
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

        override suspend fun getCollection(
            username: String,
            subjectId: Long,
        ): UserCollection? = collectionToReturn

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

        override suspend fun updateEpisodeStatus(
            subjectId: Long,
            episodeId: Long,
            type: Int,
        ) {
            onBeforeUpdateEpisode?.invoke()
            if (shouldThrowOnUpdateEpisode) {
                throw BgmNetworkException.ServerError(500, "Mock server error")
            }
            updateEpisodeCalls.add(UpdateEpisodeCall(subjectId, episodeId, type))
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
}
