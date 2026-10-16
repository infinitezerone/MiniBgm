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
import com.infinitezerone.minibgm.core.testing.repository.FakeSecureSecretStore
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

/** SettingsRepository 的长期偏好（弹幕显示 / 自动连播 / 两页共用的吐槽排序）读写行为测试 */
class SettingsRepositoryPlayViewPrefsTest {
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
        val repository = SettingsRepositoryImpl(dataSource, FakeSecureSecretStore())
    }

    @Test
    fun `默认设置中弹幕显示与自动连播开启`() =
        runTest {
            val settings = Harness().repository.settings.first()

            assertTrue(settings.danmakuEnabled)
            assertTrue(settings.autoNextEnabled)
        }

    @Test
    fun `setDanmakuEnabled 写入设置流并持久化`() =
        runTest {
            val h = Harness()

            h.repository.setDanmakuEnabled(false)

            assertFalse(
                h.repository.settings
                    .first()
                    .danmakuEnabled,
            )
            assertFalse(h.storage.snapshot().danmakuEnabled, "flag must persist to preferences")

            h.repository.setDanmakuEnabled(true)

            assertTrue(
                h.repository.settings
                    .first()
                    .danmakuEnabled,
            )
            assertTrue(h.storage.snapshot().danmakuEnabled)
        }

    @Test
    fun `setAutoNextEnabled 写入设置流并持久化`() =
        runTest {
            val h = Harness()

            h.repository.setAutoNextEnabled(false)

            assertFalse(
                h.repository.settings
                    .first()
                    .autoNextEnabled,
            )
            assertFalse(h.storage.snapshot().autoNextEnabled, "flag must persist to preferences")

            h.repository.setAutoNextEnabled(true)

            assertTrue(
                h.repository.settings
                    .first()
                    .autoNextEnabled,
            )
            assertTrue(h.storage.snapshot().autoNextEnabled)
        }

    @Test
    fun `setCommentSortOrder 写入设置流并持久化`() =
        runTest {
            val h = Harness()

            // 空串 = 尚未设置（按热门），两页共用这一条
            assertEquals(
                "",
                h.repository.settings
                    .first()
                    .commentSortOrder,
            )

            h.repository.setCommentSortOrder("desc")

            assertEquals(
                "desc",
                h.repository.settings
                    .first()
                    .commentSortOrder,
            )
            assertEquals("desc", h.storage.snapshot().commentSortOrder, "sort key must persist to preferences")

            h.repository.setCommentSortOrder("asc")
            assertEquals("asc", h.storage.snapshot().commentSortOrder)
        }

    @Test
    fun `吐槽排序与播放页开关互不串写`() =
        runTest {
            val h = Harness()

            h.repository.setCommentSortOrder("asc")

            val settings = h.repository.settings.first()
            assertEquals("asc", settings.commentSortOrder)
            assertTrue(settings.danmakuEnabled, "改排序不得顺手关掉弹幕")
            assertTrue(settings.autoNextEnabled, "改排序不得顺手关掉自动连播")

            h.repository.setDanmakuEnabled(false)
            assertEquals(
                "asc",
                h.repository.settings
                    .first()
                    .commentSortOrder,
                "拨播放页开关不得重置排序偏好",
            )
        }

    @Test
    fun `两个播放页偏好互不串写`() =
        runTest {
            val h = Harness()

            h.repository.setDanmakuEnabled(false)

            val afterDanmaku = h.repository.settings.first()
            assertFalse(afterDanmaku.danmakuEnabled)
            assertTrue(afterDanmaku.autoNextEnabled, "关闭弹幕不得顺手关掉自动连播")

            h.repository.setAutoNextEnabled(false)

            val afterAutoNext = h.repository.settings.first()
            assertFalse(afterAutoNext.danmakuEnabled)
            assertFalse(afterAutoNext.autoNextEnabled)
            assertTrue(afterAutoNext.pipEnabled, "播放页偏好不得波及画中画开关")
        }

    @Test
    fun `写入播放页偏好不影响其他设置`() =
        runTest {
            val h = Harness()
            h.repository.setSyncInterval(SyncInterval.DAILY)

            h.repository.setDanmakuEnabled(false)
            h.repository.setAutoNextEnabled(false)

            val settings = h.repository.settings.first()
            assertFalse(settings.danmakuEnabled)
            assertFalse(settings.autoNextEnabled)
            assertEquals(SyncInterval.DAILY, settings.syncInterval)
            assertEquals(8, settings.airingReminderHour)
        }
}
