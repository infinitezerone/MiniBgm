package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BounceOnClickTest {
    @Test
    fun bounceDefaults_useMediumBouncySpringWithLowStiffness() {
        assertEquals(0.92f, BounceDefaults.PRESSED_SCALE)
        assertEquals(80, BounceDefaults.PRESS_DURATION_MILLIS)
        assertEquals(Spring.DampingRatioMediumBouncy, BounceDefaults.DAMPING_RATIO)
        assertEquals(Spring.StiffnessMediumLow, BounceDefaults.STIFFNESS)
    }

    @Test
    fun bounceDefaults_pressSpecIsShortTweenAndReleaseSpecIsBouncySpring() {
        assertEquals(BounceDefaults.PRESS_DURATION_MILLIS, BounceDefaults.pressSpec.durationMillis)
        assertEquals(BounceDefaults.DAMPING_RATIO, BounceDefaults.releaseSpec.dampingRatio)
        assertEquals(BounceDefaults.STIFFNESS, BounceDefaults.releaseSpec.stiffness)
    }

    @Test
    fun bounceOnClickState_startsAtFullScaleWithConfiguredPressedScale() {
        val state = BounceOnClickState(0.9f, CoroutineScope(Dispatchers.Unconfined))

        assertEquals(0.9f, state.pressedScale)
        assertEquals(1f, state.currentScale)
    }

    @Test
    fun bounce_scalesDownThenSpringsBackToFullScale() =
        runBlocking {
            val clock = ManualFrameClock()
            val state =
                BounceOnClickState(
                    BounceDefaults.PRESSED_SCALE,
                    CoroutineScope(Dispatchers.Unconfined + clock),
                )

            state.bounce()

            // 按下段：随帧推进从 1f 向 pressedScale 缩小
            var frameTime = 0L
            repeat(4) {
                clock.sendFrame(frameTime)
                frameTime += 16_000_000L
            }
            assertTrue(state.scale.isRunning)
            assertTrue(state.currentScale < 1f)
            assertTrue(state.currentScale > BounceDefaults.PRESSED_SCALE)

            // 回弹段：持续推进直到 medium-bouncy 弹簧稳定回 1f
            var frames = 0
            while (state.scale.isRunning && frames < 600) {
                clock.sendFrame(frameTime)
                frameTime += 16_000_000L
                frames++
            }
            assertFalse(state.scale.isRunning)
            assertEquals(1f, state.currentScale, absoluteTolerance = 0.001f)
        }

    @Test
    fun bounce_triggeredWhileRunning_restartsAndStillSettlesAtFullScale() =
        runBlocking {
            val clock = ManualFrameClock()
            val state =
                BounceOnClickState(
                    BounceDefaults.PRESSED_SCALE,
                    CoroutineScope(Dispatchers.Unconfined + clock),
                )

            var frameTime = 0L
            state.bounce()
            repeat(4) {
                clock.sendFrame(frameTime)
                frameTime += 16_000_000L
            }
            // 动画进行中再次点击：取消上一次并以当前缩放为起点重新执行
            val midScale = state.currentScale
            state.bounce()

            var frames = 0
            while (state.scale.isRunning && frames < 600) {
                clock.sendFrame(frameTime)
                frameTime += 16_000_000L
                frames++
            }
            assertFalse(state.scale.isRunning)
            assertTrue(midScale <= 1f)
            assertEquals(1f, state.currentScale, absoluteTolerance = 0.001f)
        }
}

/** 手动驱动的帧时钟：逐帧喂时间，用于在纯 JVM 单测中驱动 Compose 动画。 */
private class ManualFrameClock : MonotonicFrameClock {
    private val frames = Channel<Long>(Channel.UNLIMITED)

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())

    suspend fun sendFrame(frameTimeNanos: Long) {
        frames.send(frameTimeNanos)
    }
}
