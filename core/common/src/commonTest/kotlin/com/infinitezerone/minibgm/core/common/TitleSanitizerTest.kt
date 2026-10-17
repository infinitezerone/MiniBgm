package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleSanitizerTest {
    @Test
    fun `纯净标题不产生多余衍生词`() {
        val derivatives = TitleSanitizer.extractDerivatives("葬送的芙莉莲")
        assertTrue(derivatives.isEmpty())
    }

    @Test
    fun `复合标题与赛段限定词成功提取核心子短语与去限定词全名`() {
        val derivatives = TitleSanitizer.extractDerivatives("飙马野郎 JOJO的奇妙冒险 第二&第三赛段")
        assertEquals(
            listOf("飙马野郎", "JOJO的奇妙冒险", "飙马野郎 JOJO的奇妙冒险"),
            derivatives,
        )
    }

    @Test
    fun `中文季数限定词成功剥离`() {
        val derivatives = TitleSanitizer.extractDerivatives("间谍过家家 第二季")
        assertEquals(listOf("间谍过家家"), derivatives)
    }

    @Test
    fun `英文与中文复合多重限定词成功剥离`() {
        val derivatives = TitleSanitizer.extractDerivatives("进击的巨人 The Final Season 完结篇 后篇")
        assertEquals(listOf("进击的巨人"), derivatives)
    }

    @Test
    fun `篇章修饰词成功剥离`() {
        val derivatives = TitleSanitizer.extractDerivatives("鬼灭之刃 柱训练篇")
        assertEquals(listOf("鬼灭之刃"), derivatives)
    }

    @Test
    fun `数字与英文单词不作为独立子短语拆分`() {
        val derivatives = TitleSanitizer.extractDerivatives("86 -不存在的战区-")
        assertEquals(listOf("不存在的战区"), derivatives)
    }

    @Test
    fun `searchTitles 将提炼出的核心词排在首发位并进入候选`() {
        val candidates =
            ChineseConverter.searchTitles(
                primary = "飙马野郎 JOJO的奇妙冒险 第二&第三赛段",
                aliases = listOf("STEEL BALL RUN JoJo's Bizarre Adventure 2nd & 3rd STAGE"),
            )

        // 确保前两位为高置信度核心短词，能直接在 Fast Track 双路竞速中秒中采集站
        assertTrue(candidates.size >= 4)
        assertEquals("飙马野郎", candidates[0])
        assertEquals(ChineseConverter.toTraditional("飙马野郎"), candidates[1])
        assertEquals("JOJO的奇妙冒险", candidates[2])
        assertTrue(candidates.contains("飙马野郎 JOJO的奇妙冒险"))
    }
}
