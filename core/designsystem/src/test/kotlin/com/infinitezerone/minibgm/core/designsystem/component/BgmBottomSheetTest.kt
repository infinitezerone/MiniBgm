package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class BgmBottomSheetTest {
    @Test
    fun defaultThresholds_areHigherThanMaterialDefaults() {
        // M3 原生默认值为 56.dp 和 125.dp，在日常划动列表时极其容易误关
        // 业界实践黄金平衡点：96.dp 位移阈值与 500.dp/s 速度阈值
        assertTrue(DefaultBottomSheetPositionalThreshold > 56.dp)
        assertTrue(DefaultBottomSheetVelocityThreshold > 125.dp)
        assertEquals(96.dp, DefaultBottomSheetPositionalThreshold)
        assertEquals(500.dp, DefaultBottomSheetVelocityThreshold)
    }

    @Test
    fun sheetState_creationWithCustomThresholds_initializesCorrectly() {
        val state =
            SheetState(
                skipPartiallyExpanded = true,
                positionalThreshold = { 96f },
                velocityThreshold = { 500f },
                initialValue = SheetValue.Hidden,
            )
        assertEquals(SheetValue.Hidden, state.currentValue)
        assertFalse(state.isVisible)
    }

    @Test
    fun emphasizedAccelerateEasing_matchesMaterial3Specification() {
        // M3 规范：md.sys.motion.easing.emphasized-accelerate = cubic-bezier(0.3, 0.0, 0.8, 0.15)
        val easing = BottomSheetEmphasizedAccelerateEasing
        val progressAtZero = easing.transform(0.0f)
        val progressAtQuarter = easing.transform(0.25f)
        val progressAtHalf = easing.transform(0.5f)
        val progressAtThreeQuarters = easing.transform(0.75f)
        val progressAtEnd = easing.transform(1.0f)

        assertEquals(0.0f, progressAtZero, 0.001f)
        assertEquals(1.0f, progressAtEnd, 0.001f)

        // 加速曲线特征：前半段位移极慢，后半段急剧加速
        // t 在 0~0.5 之间的位移增量显著小于 0.5~1.0 之间的增量
        val firstHalfDelta = progressAtHalf - progressAtZero
        val secondHalfDelta = progressAtEnd - progressAtHalf
        assertTrue(secondHalfDelta > firstHalfDelta * 2f, "Second half delta should be much larger than first half in accelerate curve")
        assertTrue(progressAtQuarter < 0.10f, "Quarter progress should be gentle and low (< 0.10)")
        assertTrue(progressAtHalf < 0.25f, "Half progress should be low (< 0.25)")
        assertTrue(progressAtThreeQuarters < 0.55f, "Three quarters progress should be < 0.55")
    }

    @Test
    fun applyMaterial3MotionSpecs_injectsHideMotionSpecSuccessfully() {
        val state =
            SheetState(
                skipPartiallyExpanded = true,
                positionalThreshold = { 160f },
                velocityThreshold = { 1400f },
                initialValue = SheetValue.Hidden,
            )

        // 应用 M3 Emphasized Accelerate 退出动效
        state.applyMaterial3MotionSpecs()

        val hideSpec = state.getHideMotionSpecOrNull()
        assertEquals(DefaultBottomSheetHideAnimationSpec, hideSpec)
    }
}
