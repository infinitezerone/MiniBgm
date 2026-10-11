package com.infinitezerone.minibgm.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryPlaybackTest {
    @Test
    fun `续播位置表超上限时淘汰最旧地址`() {
        val current = (1..SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS).associate { "url-$it" to it.toLong() * 1000 }

        val updated = current.withUpdatedPosition("url-new", 123L)

        assertEquals(SettingsRepositoryImpl.MAX_PLAYBACK_POSITIONS, updated.size)
        assertEquals(123L, updated["url-new"])
        assertTrue("url-1" !in updated)
        assertEquals(2000L, updated["url-2"])
    }

    @Test
    fun `重写已有地址视为最近使用`() {
        val updated = mapOf("a" to 1L, "b" to 2L, "c" to 3L).withUpdatedPosition("a", 9L)

        assertEquals(9L, updated["a"])
        assertEquals("a", updated.keys.last())
    }
}
