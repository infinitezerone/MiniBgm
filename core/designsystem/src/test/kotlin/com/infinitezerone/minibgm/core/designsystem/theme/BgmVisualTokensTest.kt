package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BgmVisualTokensTest {
    @Test
    fun motionTokens_keepExistingDurationsAndCurveOrder() {
        assertEquals(150, BgmMotion.fastMillis)
        assertEquals(250, BgmMotion.standardMillis)
        assertEquals(450, BgmMotion.ambientMillis)
        assertEquals(1500, BgmMotion.skeletonMillis)
        assertTrue(BgmMotion.fastMillis < BgmMotion.standardMillis)
        assertTrue(BgmMotion.standardMillis < BgmMotion.ambientMillis)
    }

    @Test
    fun m3DurationTokens_matchOfficialScale() {
        assertEquals(50, BgmMotion.Durations.short1)
        assertEquals(100, BgmMotion.Durations.short2)
        assertEquals(150, BgmMotion.Durations.short3)
        assertEquals(200, BgmMotion.Durations.short4)
        assertEquals(250, BgmMotion.Durations.medium1)
        assertEquals(300, BgmMotion.Durations.medium2)
        assertEquals(350, BgmMotion.Durations.medium3)
        assertEquals(400, BgmMotion.Durations.medium4)
        assertEquals(450, BgmMotion.Durations.long1)
        assertEquals(500, BgmMotion.Durations.long2)
        assertEquals(550, BgmMotion.Durations.long3)
        assertEquals(600, BgmMotion.Durations.long4)
        assertEquals(700, BgmMotion.Durations.extraLong1)
        assertEquals(800, BgmMotion.Durations.extraLong2)
        assertEquals(900, BgmMotion.Durations.extraLong3)
        assertEquals(1000, BgmMotion.Durations.extraLong4)
    }

    @Test
    fun m3EasingTokens_keepM3CurvesAndLegacySeparate() {
        assertNotSame(BgmMotion.Easings.standard, BgmMotion.Easings.legacyFastOutSlowIn)
        assertSame(BgmMotion.Easings.legacyFastOutSlowIn, BgmMotion.standardEasing)
        assertSame(BgmMotion.Easings.emphasizedAccelerate, BgmMotion.emphasizedAccelerateEasing)
    }

    @Test
    fun expressiveSpringTokens_matchOfficialPhysics() {
        assertEquals(0.8f, BgmMotion.Springs.Expressive.defaultSpatialDampingRatio)
        assertEquals(380f, BgmMotion.Springs.Expressive.defaultSpatialStiffness)
        assertEquals(0.6f, BgmMotion.Springs.Expressive.fastSpatialDampingRatio)
        assertEquals(800f, BgmMotion.Springs.Expressive.fastSpatialStiffness)
        assertEquals(0.8f, BgmMotion.Springs.Expressive.slowSpatialDampingRatio)
        assertEquals(200f, BgmMotion.Springs.Expressive.slowSpatialStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Expressive.defaultEffectsDampingRatio)
        assertEquals(1600f, BgmMotion.Springs.Expressive.defaultEffectsStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Expressive.fastEffectsDampingRatio)
        assertEquals(3800f, BgmMotion.Springs.Expressive.fastEffectsStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Expressive.slowEffectsDampingRatio)
        assertEquals(800f, BgmMotion.Springs.Expressive.slowEffectsStiffness)
    }

    @Test
    fun standardSpringTokens_matchOfficialPhysics() {
        assertEquals(0.9f, BgmMotion.Springs.Standard.defaultSpatialDampingRatio)
        assertEquals(700f, BgmMotion.Springs.Standard.defaultSpatialStiffness)
        assertEquals(0.9f, BgmMotion.Springs.Standard.fastSpatialDampingRatio)
        assertEquals(1400f, BgmMotion.Springs.Standard.fastSpatialStiffness)
        assertEquals(0.9f, BgmMotion.Springs.Standard.slowSpatialDampingRatio)
        assertEquals(300f, BgmMotion.Springs.Standard.slowSpatialStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Standard.defaultEffectsDampingRatio)
        assertEquals(1600f, BgmMotion.Springs.Standard.defaultEffectsStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Standard.fastEffectsDampingRatio)
        assertEquals(3800f, BgmMotion.Springs.Standard.fastEffectsStiffness)
        assertEquals(1.0f, BgmMotion.Springs.Standard.slowEffectsDampingRatio)
        assertEquals(800f, BgmMotion.Springs.Standard.slowEffectsStiffness)
    }

    @Test
    fun springFactories_selectSchemeAndSpeed() {
        val expressiveFastSpatial =
            BgmMotion.spatialSpring<Float>(
                scheme = BgmMotion.Scheme.Expressive,
                speed = BgmMotion.Speed.Fast,
            )
        assertEquals(0.6f, expressiveFastSpatial.dampingRatio)
        assertEquals(800f, expressiveFastSpatial.stiffness)

        val standardSlowEffects =
            BgmMotion.effectsSpring<Float>(
                scheme = BgmMotion.Scheme.Standard,
                speed = BgmMotion.Speed.Slow,
            )
        assertEquals(1.0f, standardSlowEffects.dampingRatio)
        assertEquals(800f, standardSlowEffects.stiffness)

        val expressiveDefaultSpatial = BgmMotion.spatialSpring<Float>()
        assertEquals(0.8f, expressiveDefaultSpatial.dampingRatio)
        assertEquals(380f, expressiveDefaultSpatial.stiffness)
    }

    @Test
    fun motionFactories_wireDurationAndEasing() {
        val standard = BgmMotion.m3StandardTween<Float>(BgmMotion.Durations.medium2)
        assertEquals(300, standard.durationMillis)
        assertSame(BgmMotion.Easings.standard, standard.easing)

        val functional = BgmMotion.functionalTween<Float>(BgmMotion.Durations.short4)
        assertEquals(200, functional.durationMillis)
        assertSame(BgmMotion.Easings.linear, functional.easing)

        val legacy = BgmMotion.standardTween<Float>()
        assertEquals(250, legacy.durationMillis)
        assertSame(BgmMotion.Easings.legacyFastOutSlowIn, legacy.easing)
    }

    @Test
    fun elevationTokens_keepContentAndChromeTiersSeparate() {
        assertEquals(0.dp, BgmElevation.none)
        assertEquals(2.dp, BgmElevation.content)
        assertEquals(10.dp, BgmElevation.glassChrome)
        assertEquals(12.dp, BgmElevation.floatingChrome)
        assertTrue(BgmElevation.content < BgmElevation.glassChrome)
        assertTrue(BgmElevation.glassChrome < BgmElevation.floatingChrome)
    }

    @Test
    fun opacityTokens_stayWithinReadableRange() {
        listOf(
            BgmOpacity.skeleton,
            BgmOpacity.tintWash,
            BgmOpacity.divider,
            BgmOpacity.placeholder,
            BgmOpacity.border,
            BgmOpacity.muted,
            BgmOpacity.scrim,
            BgmOpacity.overlay,
            BgmOpacity.emphasis,
        ).forEach { alpha -> assertTrue(alpha in 0f..1f) }

        assertTrue(BgmOpacity.skeleton < BgmOpacity.divider)
        assertTrue(BgmOpacity.divider < BgmOpacity.border)
        assertTrue(BgmOpacity.border < BgmOpacity.muted)
        assertTrue(BgmOpacity.muted < BgmOpacity.emphasis)
    }
}
