package com.infinitezerone.minibgm.core.data.download

import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentStreamEngineTest {
    @Test
    fun uninstalled_startStream_triggersPluginRequired() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = false)

            assertFalse(engine.isPluginInstalled())
            val result = engine.startStream("Title", "magnet:?xt=urn:btih:abc")

            assertTrue(result.isFailure)
            assertTrue(engine.streamState.value is TorrentStreamState.PluginRequired)
        }

    @Test
    fun installPlugin_succeedsAndAllowsStream() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = false)

            val installResult = engine.installPlugin()
            assertTrue(installResult.isSuccess)
            assertTrue(engine.isPluginInstalled())

            val streamResult = engine.startStream("Title", "magnet:?xt=urn:btih:abc")
            assertTrue(streamResult.isSuccess)
            assertEquals("http://127.0.0.1:8899/stream?hash=fake", streamResult.getOrNull())
            assertTrue(engine.streamState.value is TorrentStreamState.Ready)
        }

    @Test
    fun stopStream_resetsToIdle() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = true)

            engine.startStream("Title", "magnet:?xt=urn:btih:abc")
            assertTrue(engine.streamState.value is TorrentStreamState.Ready)

            engine.stopStream()
            assertTrue(engine.streamState.value is TorrentStreamState.Idle)
        }

    @Test
    fun startStream_withTorrentUrl_succeeds() =
        runTest {
            val engine = FakeTorrentStreamEngine(initialInstalled = true)

            val streamResult = engine.startStream("Title", "magnet:?xt=urn:btih:abc", "https://example.com/test.torrent")
            assertTrue(streamResult.isSuccess)
            assertEquals("http://127.0.0.1:8899/stream?hash=fake", streamResult.getOrNull())
        }
}
