package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.feature.subject.components.calculateSubjectTabTailSpacerPx
import org.junit.Test
import kotlin.test.assertEquals

class SubjectTabTailSpacerTest {
    @Test
    fun shortContent_addsExactlyEnoughSpaceToReachPinnedTabs() {
        val spacer =
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = 2000f,
                bottomPaddingPx = 40f,
                tabBarTopPx = 500f,
                contentEndTopPx = 1100f,
            )

        assertEquals(1360f, spacer)
    }

    @Test
    fun contentAlreadyFillsViewport_addsNoSpace() {
        val spacer =
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = 2000f,
                bottomPaddingPx = 40f,
                tabBarTopPx = 500f,
                contentEndTopPx = 3000f,
            )

        assertEquals(0f, spacer)
    }

    @Test
    fun missingMeasurements_addNoSpace() {
        assertEquals(
            0f,
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = 0f,
                bottomPaddingPx = 40f,
                tabBarTopPx = 500f,
                contentEndTopPx = 1100f,
            ),
        )
        assertEquals(
            0f,
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = 2000f,
                bottomPaddingPx = 40f,
                tabBarTopPx = Float.NaN,
                contentEndTopPx = 1100f,
            ),
        )
        assertEquals(
            0f,
            calculateSubjectTabTailSpacerPx(
                viewportHeightPx = 2000f,
                bottomPaddingPx = 40f,
                tabBarTopPx = 500f,
                contentEndTopPx = Float.NaN,
            ),
        )
    }
}
