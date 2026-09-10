package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkeletonLoaderTest {
    @Test
    fun skeletonState_defaultValues_areConfiguredCorrectly() {
        val state = SkeletonState()
        assertTrue(state.enabled)
        assertTrue(state.shimmerEnabled)
        assertEquals(20f, state.angleDegrees)
        assertEquals(1500, state.durationMillis)
        assertEquals(800f, state.shimmerWidth)
        assertEquals(Color.Unspecified, state.baseColor)
        assertEquals(Color.Unspecified, state.highlightColor)
    }

    @Test
    fun skeletonState_mutation_updatesProperties() {
        val state = SkeletonState()
        state.enabled = false
        state.shimmerEnabled = false
        state.angleDegrees = 45f
        state.durationMillis = 2000
        state.shimmerWidth = 1000f
        state.baseColor = Color.Red
        state.highlightColor = Color.White

        assertFalse(state.enabled)
        assertFalse(state.shimmerEnabled)
        assertEquals(45f, state.angleDegrees)
        assertEquals(2000, state.durationMillis)
        assertEquals(1000f, state.shimmerWidth)
        assertEquals(Color.Red, state.baseColor)
        assertEquals(Color.White, state.highlightColor)
    }
}
