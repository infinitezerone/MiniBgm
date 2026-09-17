package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class BgmLoggerTest {
    @Test
    fun bgmLogger_createsLoggerWithGivenTag() {
        val logger = bgmLogger("Bgm/Test")
        assertEquals("Bgm/Test", logger.tag)
    }
}
