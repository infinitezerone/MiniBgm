package com.infinitezerone.minibgm.core.datastore

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
import kotlin.test.assertTrue

/** AgentConfigDataSource 的加密存储读写测试（内存存储，不落物理磁盘） */
class AgentConfigDataSourceTest {
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

    private fun createDataSource(): AgentConfigDataSource =
        AgentConfigDataSource(
            dataStore = DataStoreFactory.create(storage = InMemoryStorage()),
            crypto = FakeCryptoManager(),
        )

    @Test
    fun save_then_read_roundTripsConfig() =
        runTest {
            val dataSource = createDataSource()

            assertNull(dataSource.current())
            dataSource.save(AgentModelConfig("https://api.example.com/v1", "sk-test", "test-model"))

            val config = dataSource.current()
            assertEquals("https://api.example.com/v1", config?.baseUrl)
            assertEquals("sk-test", config?.apiKey)
            assertEquals("test-model", config?.model)
            assertTrue(config!!.isConfigured)
        }

    @Test
    fun save_overwritesPreviousConfig() =
        runTest {
            val dataSource = createDataSource()

            dataSource.save(AgentModelConfig("https://old/v1", "old", "old-model"))
            dataSource.save(AgentModelConfig("https://new/v1", "new", "new-model"))

            assertEquals("new-model", dataSource.current()?.model)
        }

    @Test
    fun clear_emptiesConfig() =
        runTest {
            val dataSource = createDataSource()

            dataSource.save(AgentModelConfig("https://api.example.com/v1", "sk-test", "m"))
            dataSource.clear()

            assertNull(dataSource.current())
            assertNull(dataSource.config.first())
        }

    @Test
    fun corruptedBlob_decodesToNullInsteadOfCrashing() =
        runTest {
            // 模拟备份恢复后 Keystore 密钥不可用：密文无法解密
            val dataSource =
                AgentConfigDataSource(
                    dataStore = DataStoreFactory.create(storage = InMemoryStorage("not-a-valid-blob")),
                    crypto = FakeCryptoManager(),
                )

            assertNull(dataSource.current())
            assertNull(dataSource.config.first())
        }
}
