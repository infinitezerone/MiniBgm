package com.infinitezerone.minibgm.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.SyncInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SettingsRepository 的 AMOLED 纯黑模式读写行为（内存存储，不落盘；同 SettingsRepositoryPlaybackTest） */
class SettingsRepositoryAmoledDarkModeTest {
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

    /** 内存版 Storage，绕开 DataStore 在 Windows 上的文件句柄问题（同 AuthRepositoryImplTest） */
    private class InMemoryStorage : Storage<UserPreferences> {
        @Volatile
        private var data: UserPreferences = UserPreferences()

        private val coordinator = LocalCoordinator()

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

        suspend fun snapshot(): UserPreferences = data
    }

    private class Harness {
        val storage = InMemoryStorage()
        val dataStore: DataStore<UserPreferences> =
            DataStoreFactory.create(storage = storage)
        val dataSource = UserPreferencesDataSource(dataStore)
        val repository = SettingsRepositoryImpl(dataSource)
    }

    @Test
    fun `默认设置中 AMOLED 纯黑模式关闭`() =
        runTest {
            val h = Harness()

            val settings = h.repository.settings.first()

            assertFalse(settings.amoledDarkMode)
        }

    @Test
    fun `setAmoledDarkMode 写入设置流并落盘`() =
        runTest {
            val h = Harness()

            h.repository.setAmoledDarkMode(true)

            assertTrue(
                h.repository.settings
                    .first()
                    .amoledDarkMode,
            )
            assertTrue(h.storage.snapshot().amoledDarkMode, "flag must persist to the preferences file")

            h.repository.setAmoledDarkMode(false)

            assertFalse(
                h.repository.settings
                    .first()
                    .amoledDarkMode,
            )
            assertFalse(h.storage.snapshot().amoledDarkMode)
        }

    @Test
    fun `写入 AMOLED 开关不影响其他设置`() =
        runTest {
            val h = Harness()
            h.repository.setSyncInterval(SyncInterval.DAILY)

            h.repository.setAmoledDarkMode(true)

            val settings = h.repository.settings.first()
            assertTrue(settings.amoledDarkMode)
            assertEquals(SyncInterval.DAILY, settings.syncInterval)
            assertEquals(8, settings.airingReminderHour)
        }
}
