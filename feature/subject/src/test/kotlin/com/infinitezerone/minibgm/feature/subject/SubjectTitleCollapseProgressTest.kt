package com.infinitezerone.minibgm.feature.subject

import androidx.compose.ui.geometry.Rect
import org.junit.Test
import kotlin.test.assertEquals

class SubjectTitleCollapseProgressTest {
    @Test
    fun titleBelowTopBar_isNotCollapsed() {
        val progress =
            calculateSubjectTitleCollapseProgress(
                titleBounds = Rect(left = 0f, top = 240f, right = 300f, bottom = 300f),
                topBarBottomPx = 200f,
            )

        assertEquals(0f, progress)
    }

    @Test
    fun titleHalfWayThroughTopBar_isHalfCollapsed() {
        val progress =
            calculateSubjectTitleCollapseProgress(
                titleBounds = Rect(left = 0f, top = 170f, right = 300f, bottom = 230f),
                topBarBottomPx = 200f,
            )

        assertEquals(0.5f, progress)
    }

    @Test
    fun titleFullyBehindTopBar_isFullyCollapsed() {
        val progress =
            calculateSubjectTitleCollapseProgress(
                titleBounds = Rect(left = 0f, top = 120f, right = 300f, bottom = 180f),
                topBarBottomPx = 200f,
            )

        assertEquals(1f, progress)
    }

    @Test
    fun missingOrEmptyTitleBounds_fallsBackToExpanded() {
        assertEquals(0f, calculateSubjectTitleCollapseProgress(titleBounds = null, topBarBottomPx = 200f))
        assertEquals(
            0f,
            calculateSubjectTitleCollapseProgress(
                titleBounds = Rect(left = 0f, top = 200f, right = 300f, bottom = 200f),
                topBarBottomPx = 200f,
            ),
        )
    }

    @Test
    fun headerScrolledPast_staysCollapsedWhenBoundsBecomeEmpty() {
        assertEquals(
            1f,
            calculateSubjectTitleCollapseProgress(
                titleBounds = Rect.Zero,
                topBarBottomPx = 200f,
                headerScrolledPast = true,
            ),
        )
        assertEquals(
            1f,
            calculateSubjectTitleCollapseProgress(
                titleBounds = null,
                topBarBottomPx = 200f,
                headerScrolledPast = true,
            ),
        )
    }
}
