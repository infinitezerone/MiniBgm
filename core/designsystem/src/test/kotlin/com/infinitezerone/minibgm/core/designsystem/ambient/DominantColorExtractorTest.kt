package com.infinitezerone.minibgm.core.designsystem.ambient

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DominantColorExtractorTest {
    @Test
    fun empty_pixels_return_null() {
        assertNull(extractDominantColor(IntArray(0)))
    }

    @Test
    fun fully_transparent_pixels_return_null() {
        assertNull(extractDominantColor(IntArray(256) { 0x00000000 }))
    }

    @Test
    fun uniform_saturated_cover_returns_exact_color() {
        // 饱和朱红，亮度 0.21 落在可读区间内，应原样返回
        val pixels = IntArray(64 * 64) { 0xFFFF3B30.toInt() }
        assertEquals(0xFFFF3B30.toInt(), extractDominantColor(pixels))
    }

    @Test
    fun bright_background_with_small_accent_prefers_accent() {
        // 90% 近白（过亮被排除）+ 10% 高饱和蓝，应选中蓝色点缀
        val pixels = IntArray(1000) { if (it < 900) 0xFFF5F5F5.toInt() else 0xFF2E5BE6.toInt() }
        val result = assertNotNull(extractDominantColor(pixels), "期望提取到主色")

        assertEquals(0x2E, (result shr 16) and 0xFF)
        assertEquals(0x5B, (result shr 8) and 0xFF)
        assertEquals(0xE6, result and 0xFF)
        assertEquals(0xFF, (result ushr 24) and 0xFF)
    }

    @Test
    fun dominant_bucket_wins_over_vivid_minority() {
        // 70% 品红 + 30% 高饱和青：像素数优势应压过饱和度权重
        val pixels = IntArray(1000) { if (it < 700) 0xFFE91E63.toInt() else 0xFF00BFA5.toInt() }
        val result = assertNotNull(extractDominantColor(pixels), "期望提取到主色")

        assertEquals(0xE9, (result shr 16) and 0xFF)
        assertEquals(0x1E, (result shr 8) and 0xFF)
        assertEquals(0x63, result and 0xFF)
    }

    @Test
    fun too_dark_cover_is_lifted_into_readable_range() {
        val pixels = IntArray(1024) { 0xFF0A0A0A.toInt() }
        val result = assertNotNull(extractDominantColor(pixels), "期望提取到主色")

        assertTrue(argbLuminance(result) >= AMBIENT_MIN_LUMINANCE, "暗色封面应被抬升至可读亮度")
        assertTrue(argbLuminance(result) <= AMBIENT_MAX_LUMINANCE, "抬升不应越过上限")
        assertEquals(0xFF, (result ushr 24) and 0xFF)
    }

    @Test
    fun too_bright_cover_is_clamped_into_readable_range() {
        val pixels = IntArray(256) { 0xFFFFFFFF.toInt() }
        val result = assertNotNull(extractDominantColor(pixels), "期望提取到主色")

        assertTrue(argbLuminance(result) <= AMBIENT_MAX_LUMINANCE + 1e-4f, "亮色封面应被压暗")
        assertTrue(argbLuminance(result) >= AMBIENT_MIN_LUMINANCE)
    }

    @Test
    fun semi_transparent_pixels_are_ignored() {
        // 一半全透明黑 + 一半不透明橙，橙色是唯一有效候选
        val pixels = IntArray(100) { if (it < 50) 0x00000000 else 0xFFF97316.toInt() }
        assertEquals(0xFFF97316.toInt(), extractDominantColor(pixels))
    }

    @Test
    fun normalize_luminance_keeps_in_range_color_untouched() {
        assertEquals(0xFF2E5BE6.toInt(), normalizeArgbLuminance(0xFF2E5BE6.toInt()))
    }

    @Test
    fun normalize_luminance_is_symmetric_on_white_and_black() {
        val lifted = normalizeArgbLuminance(0xFF000000.toInt())
        val clamped = normalizeArgbLuminance(0xFFFFFFFF.toInt())
        assertTrue(argbLuminance(lifted) >= AMBIENT_MIN_LUMINANCE)
        assertTrue(argbLuminance(clamped) <= AMBIENT_MAX_LUMINANCE + 1e-4f)
    }
}
