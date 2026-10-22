package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AquamorphicPressTest {

    private class ManualFrameClock(private val stepNanos: Long = 16_000_000L) : MonotonicFrameClock {
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += stepNanos
            return onFrame(time)
        }
    }

    @Test
    fun aquamorphicPressNodeElement_implementsValueEquality() {
        val source1 = MutableInteractionSource()
        val source2 = MutableInteractionSource()

        val elem1 = AquamorphicPressNodeElement(source1, 0.95f)
        val elem2 = AquamorphicPressNodeElement(source1, 0.95f)
        val elemDifferentScale = AquamorphicPressNodeElement(source1, 0.92f)
        val elemDifferentSource = AquamorphicPressNodeElement(source2, 0.95f)

        assertEquals(elem1, elem2)
        assertEquals(elem1.hashCode(), elem2.hashCode())
        assertNotEquals(elem1, elemDifferentScale)
        assertNotEquals(elem1, elemDifferentSource)
    }

    @Test
    fun aquamorphicPress_releaseSpring_producesSecondaryMicroBounce() = runTest {
        withContext(ManualFrameClock()) {
            val animatable =
                Animatable(
                    initialValue = 0.95f,
                    visibilityThreshold = AquamorphicMotionDefaults.PHYSICAL_VISIBILITY_THRESHOLD,
                )
            val trajectory = mutableListOf<Float>()

            animatable.animateTo(
                targetValue = 1f,
                animationSpec = AquamorphicMotionDefaults.ReleaseReboundSpring,
            ) {
                trajectory.add(value)
            }

            // 1. 验证主过冲
            val maxOvershoot = trajectory.maxOrNull() ?: 1f
            assertTrue(
                "释放回弹需产生主过冲 (>= 1.01f), 实际: $maxOvershoot",
                maxOvershoot >= 1.01f,
            )

            // 2. 验证二次微弹
            val maxIndex = trajectory.indexOf(maxOvershoot)
            val subsequentValues = trajectory.subList(maxIndex, trajectory.size)
            val minSecondary = subsequentValues.minOrNull() ?: 1f
            assertTrue(
                "释放回弹需产生二次微弹 (< 0.998f), 实际: $minSecondary",
                minSecondary < 0.998f,
            )

            // 3. 验证终态稳定在 1.0f
            assertEquals(1f, animatable.value, 0.001f)
        }
    }

    @Test
    fun aquamorphicPress_pressDownSpring_isCriticallyDampedWithoutOvershoot() = runTest {
        withContext(ManualFrameClock()) {
            val animatable =
                Animatable(
                    initialValue = 1f,
                    visibilityThreshold = AquamorphicMotionDefaults.PHYSICAL_VISIBILITY_THRESHOLD,
                )
            val trajectory = mutableListOf<Float>()

            animatable.animateTo(
                targetValue = 0.95f,
                animationSpec = AquamorphicMotionDefaults.PressDownSpring,
            ) {
                trajectory.add(value)
            }

            // 按下阶段无过冲
            val minScale = trajectory.minOrNull() ?: 1f
            assertTrue(
                "按下阶段无过冲 (>= 0.9499f), 实际: $minScale",
                minScale >= 0.9499f,
            )
            assertEquals(0.95f, animatable.value, 0.001f)
        }
    }

    @Test
    fun combinedInteractionSource_mergesEventsFromMultipleSources() = runTest {
        val source1 = MutableInteractionSource()
        val source2 = MutableInteractionSource()
        val combined = CombinedInteractionSource(source1, source2)

        val press = PressInteraction.Press(Offset(10f, 10f))
        var received: Interaction? = null

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            received = combined.interactions.first()
        }

        source2.emit(press)
        assertEquals(press, received)
        job.cancel()
    }

    @Test
    fun combinedInteractionSource_handlesMultipleSourcesConcurrently() = runTest {
        val source1 = MutableInteractionSource()
        val source2 = MutableInteractionSource()
        val source3 = MutableInteractionSource()
        val combined = CombinedInteractionSource(source1, source2, source3)

        val press1 = PressInteraction.Press(Offset(1f, 1f))
        val press3 = PressInteraction.Press(Offset(3f, 3f))

        val events = mutableListOf<Interaction>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            combined.interactions.collect {
                events.add(it)
            }
        }

        source1.emit(press1)
        source3.emit(press3)

        assertEquals(2, events.size)
        assertEquals(press1, events[0])
        assertEquals(press3, events[1])
        job.cancel()
    }
}
