package com.infinitezerone.minibgm.feature.subject.player

import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerController] 纯 JVM 单测：引擎用 [FakePlayerEngine]，不触碰任何 Android/ExoPlayer 实例。
 *
 * 覆盖旧实现踩过的坑：换源不应重载播放器（只更新请求头）、状态收敛、错误/结束态、轮询。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    private class FakePlayerEngine : PlayerEngine {
        private var listener: PlayerEngineListener? = null

        var loadCalls = 0
        var loadedUrl: String? = null
        var loadedHeaders: Map<String, String> = emptyMap()
        var retryCalls = 0
        var clearCalls = 0
        var releaseCalls = 0
        var playing = false
        var position = 0L
        var duration = 0L
        var speed = 1f
        val seeks = mutableListOf<Long>()

        override val player: Player
            get() = error("FakePlayerEngine 不提供真实 Player（UI 层才需要）")

        override val currentPositionMs: Long get() = position

        override val durationMs: Long get() = duration

        override val isPlaying: Boolean get() = playing

        override fun setListener(listener: PlayerEngineListener?) {
            this.listener = listener
        }

        override fun setRequestHeaders(headers: Map<String, String>) {
            loadedHeaders = headers
        }

        override fun load(
            url: String,
            headers: Map<String, String>,
        ) {
            loadCalls++
            loadedUrl = url
            loadedHeaders = headers
        }

        override fun retry() {
            retryCalls++
        }

        override fun clear() {
            clearCalls++
            loadedUrl = null
        }

        override fun play() {
            playing = true
            listener?.onIsPlayingChanged(true)
        }

        override fun pause() {
            playing = false
            listener?.onIsPlayingChanged(false)
        }

        override fun seekTo(positionMs: Long) {
            position = positionMs
            seeks += positionMs
        }

        override fun setPlaybackSpeed(speed: Float) {
            this.speed = speed
        }

        override fun release() {
            releaseCalls++
        }

        fun emitPlaybackState(state: EnginePlaybackState) = listener?.onPlaybackStateChanged(state)

        fun emitError(message: String) = listener?.onError(message)
    }

    @Test
    fun setMedia_firstUrl_loadsWithHeaders() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)

            controller.setMedia("https://cdn/a.m3u8", mapOf("Referer" to "https://site"))

            assertEquals(1, engine.loadCalls)
            assertEquals("https://cdn/a.m3u8", engine.loadedUrl)
            assertEquals(mapOf("Referer" to "https://site"), engine.loadedHeaders)
            assertEquals("https://cdn/a.m3u8", controller.state.value.mediaUrl)
            assertTrue(controller.state.value.isBuffering)
        }

    @Test
    fun setMedia_sameUrl_onlyRefreshesHeaders_doesNotReload() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            controller.setMedia("https://cdn/a.m3u8", mapOf("Referer" to "https://other"))

            assertEquals("URL 不变时不应重新加载播放器", 1, engine.loadCalls)
            assertEquals(mapOf("Referer" to "https://other"), engine.loadedHeaders)
        }

    @Test
    fun setMedia_newUrl_reloads() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            controller.setMedia("https://cdn/b.m3u8", emptyMap())

            assertEquals(2, engine.loadCalls)
            assertEquals("https://cdn/b.m3u8", engine.loadedUrl)
        }

    @Test
    fun setMedia_blankUrl_clearsEngineAndState() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            controller.setMedia("", emptyMap())

            assertEquals(1, engine.clearCalls)
            assertEquals("", controller.state.value.mediaUrl)
            assertFalse(controller.state.value.isBuffering)
        }

    @Test
    fun retry_repreparesAndClearsError() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())
            engine.emitError("网络连接超时，请检查网络")

            controller.retry()

            assertEquals(1, engine.retryCalls)
            assertNull(controller.state.value.error)
            assertTrue(controller.state.value.isBuffering)
        }

    @Test
    fun engineError_isSurfacedAndStopsBuffering() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitError("播放地址已失效或返回错误")

            assertEquals("播放地址已失效或返回错误", controller.state.value.error)
            assertFalse(controller.state.value.isBuffering)
        }

    @Test
    fun engineEnded_marksEndedState() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitPlaybackState(EnginePlaybackState.ENDED)

            assertTrue(controller.state.value.isEnded)
            assertFalse(controller.state.value.isBuffering)
        }

    @Test
    fun engineReady_clearsBuffering() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitPlaybackState(EnginePlaybackState.READY)

            assertFalse(controller.state.value.isBuffering)
            assertNull(controller.state.value.error)
        }

    @Test
    fun togglePlayPause_afterEnded_seeksToStartAndPlays() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())
            engine.emitPlaybackState(EnginePlaybackState.ENDED)

            controller.togglePlayPause()

            assertTrue(engine.seeks.contains(0L))
            assertTrue(engine.playing)
            assertFalse(controller.state.value.isEnded)
        }

    @Test
    fun togglePlayPause_pausesWhenEnginePlaying() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())
            engine.play()

            controller.togglePlayPause()

            assertFalse(engine.playing)
        }

    @Test
    fun positionPolling_reportsEnginePosition() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())
            engine.position = 1234L
            engine.duration = 10_000L

            engine.play()
            advanceTimeBy(600L)

            assertEquals(1234L, controller.state.value.positionMs)
            assertEquals(10_000L, controller.state.value.durationMs)
            assertTrue(controller.state.value.isPlaying)

            engine.pause()
            assertEquals(1234L, controller.state.value.positionMs)
        }

    @Test
    fun release_stopsPollingAndReleasesEngine() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            controller.setMedia("https://cdn/a.m3u8", emptyMap())
            engine.play()

            controller.release()

            assertEquals(1, engine.releaseCalls)
        }

    @Test
    fun readyEvent_isEmittedWhenEngineBecomesReady() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            val events = mutableListOf<PlayerEngineEvent>()
            backgroundScope.launch { controller.events.collect { events += it } }
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitPlaybackState(EnginePlaybackState.READY)
            runCurrent()

            assertTrue(events.contains(PlayerEngineEvent.Ready))
        }

    @Test
    fun endedEvent_isEmittedWhenEngineEnds() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            val events = mutableListOf<PlayerEngineEvent>()
            backgroundScope.launch { controller.events.collect { events += it } }
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitPlaybackState(EnginePlaybackState.ENDED)
            runCurrent()

            assertTrue(events.contains(PlayerEngineEvent.Ended))
        }

    @Test
    fun errorEvent_carriesMessage() =
        runTest {
            val engine = FakePlayerEngine()
            val controller = PlayerController(engine, backgroundScope)
            val events = mutableListOf<PlayerEngineEvent>()
            backgroundScope.launch { controller.events.collect { events += it } }
            controller.setMedia("https://cdn/a.m3u8", emptyMap())

            engine.emitError("视频流格式无法解析")
            runCurrent()

            assertEquals(
                listOf<PlayerEngineEvent>(PlayerEngineEvent.Error("视频流格式无法解析")),
                events.filterIsInstance<PlayerEngineEvent.Error>(),
            )
        }
}
