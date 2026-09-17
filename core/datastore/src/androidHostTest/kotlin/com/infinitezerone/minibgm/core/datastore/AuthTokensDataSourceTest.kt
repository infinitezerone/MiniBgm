package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AuthTokensDataSource 的加解密存储与账号会话流转测试（内存存储，不落物理磁盘） */
class AuthTokensDataSourceTest {
    private class LocalCoordinator : InterProcessCoordinator {
        private val mutex = Mutex()
        private var version = 0

        override val updateNotifications: Flow<Unit> = emptyFlow()

        override suspend fun <T> lock(block: suspend () -> T): T = mutex.withLock { block() }

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

    private class InMemoryStorage(
        initialData: String = "",
    ) : Storage<String> {
        @Volatile
        private var data: String = initialData

        private val coordinator = LocalCoordinator()

        override fun createConnection(): StorageConnection<String> =
            object : StorageConnection<String> {
                override val coordinator: InterProcessCoordinator = this@InMemoryStorage.coordinator

                override suspend fun <R> readScope(block: suspend (ReadScope<String>, Boolean) -> R): R =
                    block(
                        object : ReadScope<String> {
                            override suspend fun readData(): String = data

                            override fun close() {}
                        },
                        true,
                    )

                override suspend fun writeScope(block: suspend (WriteScope<String>) -> Unit) {
                    block(
                        object : WriteScope<String> {
                            override suspend fun readData(): String = data

                            override suspend fun writeData(value: String) {
                                data = value
                            }

                            override fun close() {}
                        },
                    )
                }

                override fun close() {}
            }
    }

    private class FakeCryptoManager : CryptoManager {
        override fun encrypt(plain: ByteArray): ByteArray = plain

        override fun decrypt(blob: ByteArray): ByteArray = blob
    }

    private fun createDataSource(initial: String = ""): Pair<AuthTokensDataSource, DataStore<String>> {
        val dataStore = DataStoreFactory.create(storage = InMemoryStorage(initial))
        return AuthTokensDataSource(dataStore = dataStore, crypto = FakeCryptoManager()) to dataStore
    }

    @Test
    fun saveTokens_withUserId_setsActiveUserAndStoresTokens() =
        runTest {
            val (dataSource, _) = createDataSource()

            dataSource.saveTokens(42L, "access_1", "refresh_1")

            assertEquals(42L, dataSource.activeUserId.first())
            assertEquals("access_1" to "refresh_1", dataSource.tokens.first())
            assertEquals("access_1", dataSource.getAccessToken())
            assertEquals("refresh_1", dataSource.getRefreshToken())
        }

    @Test
    fun saveTokens_withoutUserId_preservesActiveUserIdAndUpdatesTokens() =
        runTest {
            val (dataSource, _) = createDataSource()

            // 1. 用户已登录
            dataSource.saveTokens(42L, "old_access", "old_refresh")
            assertEquals(42L, dataSource.activeUserId.first())

            // 2. Ktor 401 自动触发刷新（无 userId 调用）
            dataSource.saveTokens("new_access", "new_refresh")

            // 3. 验证：活跃用户 ID 未被冲刷成 0L/null，登录态不中断
            assertEquals(42L, dataSource.activeUserId.first())
            assertEquals("new_access" to "new_refresh", dataSource.tokens.first())
            assertEquals("new_access", dataSource.getAccessToken())
            assertEquals("new_refresh", dataSource.getRefreshToken())
        }

    @Test
    fun multiAccount_switchAndRemove_worksCorrectly() =
        runTest {
            val (dataSource, _) = createDataSource()

            dataSource.saveTokens(1L, "a1", "r1")
            dataSource.saveTokens(2L, "a2", "r2")
            assertEquals(2L, dataSource.activeUserId.first())
            assertEquals("a2", dataSource.getAccessToken())

            // 切换账号到 1L
            dataSource.setActiveUser(1L)
            assertEquals(1L, dataSource.activeUserId.first())
            assertEquals("a1", dataSource.getAccessToken())

            // 移除当前账号 1L，应自动迁移活跃账号到 2L
            dataSource.removeTokens(1L)
            assertEquals(2L, dataSource.activeUserId.first())
            assertEquals("a2", dataSource.getAccessToken())

            // 移除最后一个账号，会话被清理
            dataSource.removeTokens(2L)
            assertNull(dataSource.activeUserId.first())
            assertNull(dataSource.tokens.first())
        }

    @Test
    fun corruptBlob_handledGracefullyAsNull() =
        runTest {
            val (dataSource, dataStore) = createDataSource()
            dataStore.updateData { "not-a-valid-encrypted-json" }

            assertNull(dataSource.activeUserId.first())
            assertNull(dataSource.tokens.first())
            assertNull(dataSource.getAccessToken())
        }

    // ---- 边界语义钉子 ----

    @Test
    fun setActiveUser_forMissingAccount_isNoOp() =
        runTest {
            val (dataSource, _) = createDataSource()
            dataSource.saveTokens(42L, "access_1", "refresh_1")

            dataSource.setActiveUser(999L)

            assertEquals(42L, dataSource.activeUserId.first())
            assertEquals("access_1", dataSource.getAccessToken())
        }

    @Test
    fun removeTokens_lastAccount_clearsSessionCompletely() =
        runTest {
            val (dataSource, _) = createDataSource()
            dataSource.saveTokens(42L, "access_1", "refresh_1")

            dataSource.removeTokens(42L)

            assertNull(dataSource.activeUserId.first())
            assertNull(dataSource.tokens.first())
            assertNull(dataSource.getAccessToken())
        }

    @Test
    fun removeTokens_otherAccount_keepsActiveUserIntact() =
        runTest {
            val (dataSource, _) = createDataSource()
            dataSource.saveTokens(42L, "access_1", "refresh_1")
            dataSource.saveTokens(7L, "access_2", "refresh_2")

            dataSource.removeTokens(7L)

            assertEquals(42L, dataSource.activeUserId.first())
            assertEquals("access_1", dataSource.getAccessToken())
        }

    @Test
    fun legacyBlob_withZeroActiveUserId_tokensResolveButSessionStaysLoggedOut() =
        runTest {
            // 文档化现行为：legacy 顶层 accessToken 迁移到 accounts[activeUserId]，
            // 当 activeUserId == 0（哨兵"无用户"）时 tokens 流可解析（客户端仍可带凭据请求），
            // 但 activeUserId 流过滤 0 → 登录态为 null。若未来调整该语义，此钉子会提示同步
            // 检查 AuthRepository.isLoggedIn 与 BgmSyncWorker 的登录判据
            val legacyJson =
                """{"activeUserId":0,"accounts":{},"accessToken":"legacy-access","refreshToken":"legacy-refresh"}"""
            // 真实存储的 blob 是 Base64(JSON)（见 AuthBlobSerializer / encodeState）
            val legacyBlob =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(legacyJson.encodeToByteArray())
            val (dataSource, _) = createDataSource(initial = legacyBlob)

            assertEquals("legacy-access" to "legacy-refresh", dataSource.tokens.first())
            assertNull(dataSource.activeUserId.first())
        }
}
