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

    @Test
    fun `searchTitles 主标题优先别名按中文字形排序原名垫底`() {
        val titles =
            ChineseConverter.searchTitles(
                primary = "葬送的芙莉莲",
                aliases = listOf("Frieren: Beyond Journey's End", "Sousou no Frieren", "葬送的芙莉蓮"),
                origin = "葬送のフリーレン",
            )

        // 英文名没有简繁差异，去重后只出现一次；繁体别名与主标题的繁体形态合并
        assertEquals(
            listOf(
                "葬送的芙莉莲",
                "葬送的芙莉蓮",
                "Frieren: Beyond Journey's End",
                "Sousou no Frieren",
                "葬送のフリーレン",
            ),
            titles,
        )
    }

    @Test
    fun `searchTitles 没有别名时保持既有的主标题加繁体两态`() {
        assertEquals(listOf("葬送的芙莉莲", "葬送的芙莉蓮"), ChineseConverter.searchTitles("葬送的芙莉莲"))
    }

    @Test
    fun `searchTitles 按 max 截断且主标题不被挤掉`() {
        val aliases = (1..10).map { "别名$it" }

        val titles = ChineseConverter.searchTitles("主标题", aliases, max = 5)

        assertEquals(5, titles.size)
        assertEquals("主标题", titles.first())
    }

    @Test
    fun `searchTitles 忽略空白候选并合并别名重复形态`() {
        assertEquals(
            listOf("主标题", "主標題"),
            ChineseConverter.searchTitles("主标题", aliases = listOf("  ", "主標題")),
        )
    }
}
