package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BgmTypographyTest {
    private val scale =
        listOf(
            BgmTypography.displayLarge,
            BgmTypography.displayMedium,
            BgmTypography.displaySmall,
            BgmTypography.headlineLarge,
            BgmTypography.headlineMedium,
            BgmTypography.headlineSmall,
            BgmTypography.titleLarge,
            BgmTypography.titleMedium,
            BgmTypography.titleSmall,
            BgmTypography.bodyLarge,
            BgmTypography.bodyMedium,
            BgmTypography.bodySmall,
            BgmTypography.labelLarge,
            BgmTypography.labelMedium,
            BgmTypography.labelSmall,
        )

    @Test
    fun typeScale_keepsTheMaterialThreeRoleSizes() {
        assertEquals(57f, BgmTypography.displayLarge.fontSize.value)
        assertEquals(45f, BgmTypography.displayMedium.fontSize.value)
        assertEquals(36f, BgmTypography.displaySmall.fontSize.value)
        assertEquals(32f, BgmTypography.headlineLarge.fontSize.value)
        assertEquals(28f, BgmTypography.headlineMedium.fontSize.value)
        assertEquals(24f, BgmTypography.headlineSmall.fontSize.value)
        assertEquals(20f, BgmTypography.titleLarge.fontSize.value)
        assertEquals(16f, BgmTypography.titleMedium.fontSize.value)
        assertEquals(14f, BgmTypography.titleSmall.fontSize.value)
        assertEquals(16f, BgmTypography.bodyLarge.fontSize.value)
        assertEquals(14f, BgmTypography.bodyMedium.fontSize.value)
        assertEquals(12f, BgmTypography.bodySmall.fontSize.value)
        assertEquals(14f, BgmTypography.labelLarge.fontSize.value)
        assertEquals(12f, BgmTypography.labelMedium.fontSize.value)
        assertEquals(11f, BgmTypography.labelSmall.fontSize.value)
    }

    @Test
    fun everyRole_usesTheSystemFontFamily() {
        scale.forEach { style ->
            assertEquals(FontFamily.Default, style.fontFamily)
        }
    }

    @Test
    fun bodyAndLabelCopy_keepCjkLineHeightAtLeastOnePointTwoFive() {
        listOf(
            BgmTypography.bodyLarge,
            BgmTypography.bodyMedium,
            BgmTypography.bodySmall,
            BgmTypography.labelLarge,
            BgmTypography.labelMedium,
            BgmTypography.labelSmall,
        ).forEach { style ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue(ratio >= 1.25f, "line-height ratio $ratio is too tight for CJK copy")
        }
    }

    @Test
    fun titles_staySemiboldOrBolder() {
        listOf(
            BgmTypography.headlineLarge,
            BgmTypography.headlineMedium,
            BgmTypography.headlineSmall,
            BgmTypography.titleLarge,
            BgmTypography.titleMedium,
            BgmTypography.titleSmall,
        ).forEach { style: TextStyle ->
            val weight = assertNotNull(style.fontWeight).weight
            assertTrue(weight >= FontWeight.SemiBold.weight, "title weight $weight is too light")
        }
    }
}
