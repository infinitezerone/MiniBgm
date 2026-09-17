package com.infinitezerone.minibgm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import com.infinitezerone.minibgm.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UserPreferencesDataSource 的账号池读写行为（内存存储，不落盘） */
class UserPreferencesDataSourceTest {
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
        initialData: UserPreferences,
    ) : Storage<UserPreferences> {
        @Volatile
        private var data: UserPreferences = initialData

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
    }

    private val existingAccount =
        UserProfile(
            id = 42L,
            username = "infinitezerone",
            nickname = "零一",
        )

    private fun createDataSource(
        initial: UserPreferences =
            UserPreferences(savedProfiles = mapOf(42L to existingAccount)),
    ): Pair<UserPreferencesDataSource, DataStore<UserPreferences>> {
        val dataStore = DataStoreFactory.create(storage = InMemoryStorage(initial))
        return UserPreferencesDataSource(dataStore) to dataStore
    }

    @Test
    fun `saveUserProfile upserts profile into pool without touching session`() =
        runTest {
            val (dataSource, dataStore) = createDataSource()

            dataSource.saveUserProfile(UserProfile(id = 999L, username = "alt", nickname = "马甲"))

            val stored = dataStore.data.first()
            assertEquals(2, stored.savedProfiles.size)
            assertEquals("infinitezerone", stored.savedProfiles[42L]?.username)
            assertEquals("马甲", stored.savedProfiles[999L]?.nickname)
        }

    @Test
    fun `removeAccount removes profile from pool`() =
        runTest {
            val (dataSource, dataStore) = createDataSource()

            dataSource.removeAccount(42L)

            val stored = dataStore.data.first()
            assertEquals(0, stored.savedProfiles.size)
        }

    @Test
    fun `removeAccount keeps other profiles in pool`() =
        runTest {
            val (dataSource, dataStore) = createDataSource()
            dataSource.saveUserProfile(UserProfile(id = 999L, username = "alt", nickname = "马甲"))

            dataSource.removeAccount(42L)

            val stored = dataStore.data.first()
            assertEquals(1, stored.savedProfiles.size)
            assertEquals("马甲", stored.savedProfiles[999L]?.nickname)
        }

    @Test
    fun `clearAllUserData clears pool and verifier but preserves device preferences`() =
        runTest {
            val (dataSource, dataStore) = createDataSource()
            dataSource.setDarkMode(true)
            dataSource.setNotifyBeforeAirMinutes(30)
            dataSource.setPendingOAuthVerifier("verifier-1")

            dataSource.clearAllUserData()

            val stored = dataStore.data.first()
            assertTrue(stored.savedProfiles.isEmpty())
            assertEquals("", stored.pendingOAuthVerifier)
            assertTrue(stored.isDarkMode)
            assertEquals(30, stored.notifyBeforeAirMinutes)
        }
}
