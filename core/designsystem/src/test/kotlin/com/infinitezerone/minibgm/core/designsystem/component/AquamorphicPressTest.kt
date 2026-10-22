package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AquamorphicPressTest {

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
