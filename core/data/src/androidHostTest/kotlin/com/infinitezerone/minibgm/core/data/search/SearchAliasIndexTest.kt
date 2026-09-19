package com.infinitezerone.minibgm.core.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAliasIndexTest {
    private val index =
        SearchAliasIndex(
            listOf(
                SearchAliasIndex.Entry(1L, "Frieren", "葬送的芙莉莲"),
                SearchAliasIndex.Entry(2L, "葬送のデルリン", "葬送的德爾琳"),
                SearchAliasIndex.Entry(3L, "Ｓｐｙ×Family", "间谍过家家"),
                SearchAliasIndex.Entry(4L, "Chainsaw Man", "电锯人"),
            ),
        )

    @Test
    fun `全角英数与空白差异被归一化命中`() {
        val results = index.search("Ｆａｍｉｌｙ", limit = 8)

        assertEquals(listOf(3L), results.map { it.bgmId })
    }

    @Test
    fun `间隔符与大小写不影响精确命中`() {
        assertEquals(listOf(4L), index.search("chainsaw-man").map { it.bgmId })
        assertEquals(listOf(1L), index.search("葬送的 芙莉莲").map { it.bgmId })
    }

    @Test
    fun `标题与中文名双向匹配`() {
        assertEquals(listOf(1L), index.search("Frieren").map { it.bgmId })
        assertEquals(listOf(1L), index.search("葬送的芙莉莲").map { it.bgmId })
    }

    @Test
    fun `前缀命中优先于包含`() {
        // 「葬送的」同时是 1（前缀）与 2（前缀）的别名前缀，按原始顺序稳定输出
        val results = index.search("葬送的")
        assertEquals(listOf(1L, 2L), results.map { it.bgmId })
    }

    @Test
    fun `无命中返回空列表且空查询不搜索`() {
        assertTrue(index.search("不存在的作品名").isEmpty())
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun `limit 截断结果`() {
        val results = index.search("葬送的", limit = 1)
        assertEquals(1, results.size)
    }
}
