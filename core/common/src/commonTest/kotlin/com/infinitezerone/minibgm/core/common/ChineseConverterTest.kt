package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ChineseConverterTest {
    @Test
    fun testToTraditional() {
        assertEquals("葬送的芙莉蓮", ChineseConverter.toTraditional("葬送的芙莉莲"))
        assertEquals("二十世紀電氣目錄", ChineseConverter.toTraditional("二十世纪电气目录"))
        assertEquals("進擊的巨人", ChineseConverter.toTraditional("进击的巨人"))
        assertEquals("無職轉生", ChineseConverter.toTraditional("无职转生"))
        assertEquals("孤獨搖滾", ChineseConverter.toTraditional("孤独摇滚"))
        assertEquals("鬼滅之刃", ChineseConverter.toTraditional("鬼灭之刃"))
    }
}
