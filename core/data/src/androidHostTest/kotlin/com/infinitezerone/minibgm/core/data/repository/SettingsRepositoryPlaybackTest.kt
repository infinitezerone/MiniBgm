package com.infinitezerone.minibgm.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackPlaylistSchema
import com.infinitezerone.minibgm.core.model.PlaylistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsRepositoryPlaybackTest {
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

    private fun entryJson(
        label: String,
        url: String,
    ) = """{"label":"$label","url":"$url"}"""

    private fun docJson(
        id: String,
        name: String,
        vararg entries: String,
        subjectId: Long = 42L,
    ) = """{"schemaVersion":1,"playlists":[{"id":"$id","name":"$name","bgmSubjectId":$subjectId,"entries":[${entries.joinToString(
        ",",
    )}]}]}"""

    @Test
    fun `导入合法文档后播放列表流可见并落盘为信封格式`() =
        runTest {
            val h = Harness()
            val result =
                h.repository.importPlaylistsFromJson(
                    docJson("p1", "片单一", entryJson("01", "https://x.example.com/1.m3u8")),
                )
            assertIs<AppResult.Success<*>>(result)
            val playlists = h.repository.playlists.first()
            assertEquals(1, playlists.size)
            assertEquals("p1", playlists.single().id)
            assertEquals(
                "01",
                playlists
                    .single()
                    .entries
                    .single()
                    .label,
            )
            val raw = h.storage.snapshot().playlistsJson
            assertTrue(raw.contains("\"schemaVersion\""), "envelope must persist schema version: $raw")
        }

    @Test
    fun `同 id 覆盖旧列表 新 id 追加`() =
        runTest {
            val h = Harness()
            h.repository.importPlaylistsFromJson(docJson("p1", "旧名", entryJson("01", "https://x/1")))
            h.repository.importPlaylistsFromJson(docJson("p1", "新名", entryJson("02", "https://x/2")))
            h.repository.importPlaylistsFromJson(docJson("p2", "第二份", entryJson("01", "https://x/3")))

            val playlists = h.repository.playlists.first()
            assertEquals(2, playlists.size)
            val replaced = playlists.first { it.id == "p1" }
            assertEquals("新名", replaced.name)
            assertEquals(listOf("02"), replaced.entries.map { it.label })
        }

    @Test
    fun `部分非法条目只丢弃该条并报告原因`() =
        runTest {
            val h = Harness()
            val result =
                h.repository.importPlaylistsFromJson(
                    docJson(
                        "p1",
                        "片单",
                        entryJson("01", "https://x/1"),
                        entryJson("02", "javascript:alert(1)"),
                    ),
                )
            assertIs<AppResult.Success<*>>(result)
            val summary = (result as AppResult.Success).data
            assertEquals(1, summary.addedCount)
            assertTrue(summary.issues.any { it.contains("http/https") })
            assertEquals(
                listOf("01"),
                h.repository.playlists
                    .first()
                    .single()
                    .entries
                    .map { it.label },
            )
        }

    @Test
    fun `全部条目非法时报错且不改动现有数据`() =
        runTest {
            val h = Harness()
            h.repository.importPlaylistsFromJson(docJson("p1", "已有", entryJson("01", "https://x/1")))
            val result =
                h.repository.importPlaylistsFromJson(
                    docJson("p2", "坏单", entryJson("", "ftp://x/1")),
                )
            assertIs<AppResult.Error>(result)
            assertEquals(
                listOf("p1"),
                h.repository.playlists
                    .first()
                    .map { it.id },
            )
        }

    @Test
    fun `损坏的存量数据拒绝导入但可重置恢复`() =
        runTest {
            val h = Harness()
            h.dataSource.setPlaylistsJson("{ broken json")
            val blocked = h.repository.importPlaylistsFromJson(docJson("p1", "片单", entryJson("01", "https://x/1")))
            assertIs<AppResult.Error>(blocked)
            assertEquals(emptyList(), h.repository.playlists.first())

            h.repository.clearPlaylists()
            val ok = h.repository.importPlaylistsFromJson(docJson("p1", "片单", entryJson("01", "https://x/1")))
            assertIs<AppResult.Success<*>>(ok)
            assertEquals(
                1,
                h.repository.playlists
                    .first()
                    .size,
            )
        }

    @Test
    fun `删除指定列表后其余保留`() =
        runTest {
            val h = Harness()
            h.repository.importPlaylistsFromJson(docJson("p1", "一", entryJson("01", "https://x/1")))
            h.repository.importPlaylistsFromJson(docJson("p2", "二", entryJson("01", "https://x/2")))
            h.repository.deletePlaylist("p1")
            val playlists: List<PlaybackPlaylist> = h.repository.playlists.first()
            assertEquals(listOf("p2"), playlists.map { it.id })
            val entry: PlaylistEntry = playlists.single().entries.single()
            assertEquals("https://x/2", entry.url)
        }

    @Test
    fun `模板示例经真实仓库导入成功`() =
        runTest {
            val h = Harness()
            val result =
                h.repository.importPlaylistsFromJson(PlaybackPlaylistSchema.TEMPLATE_EXAMPLE_JSON)
            assertIs<AppResult.Success<*>>(result)
            assertEquals(
                1,
                h.repository.playlists
                    .first()
                    .size,
            )
        }

    @Test
    fun `续播位置表超上限时淘汰最旧地址`() {
        val current = (1..SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS).associate { "url-$it" to it.toLong() * 1000 }

        val updated = current.withUpdatedPosition("url-new", 123L)

        assertEquals(SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS, updated.size)
        assertEquals(123L, updated["url-new"])
        assertTrue("url-1" !in updated)
        assertEquals(2000L, updated["url-2"])
    }

    @Test
    fun `重写已有地址视为最近使用`() {
        val updated = mapOf("a" to 1L, "b" to 2L, "c" to 3L).withUpdatedPosition("a", 9L)

        assertEquals(9L, updated["a"])
        assertEquals("a", updated.keys.last())
    }
}
