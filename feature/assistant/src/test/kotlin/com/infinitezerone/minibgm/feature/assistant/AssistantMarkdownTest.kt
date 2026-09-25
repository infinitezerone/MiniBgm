package com.infinitezerone.minibgm.feature.assistant

import androidx.compose.ui.graphics.Color
import com.infinitezerone.minibgm.feature.assistant.components.MarkdownBlock
import com.infinitezerone.minibgm.feature.assistant.components.buildMarkdownAnnotatedString
import com.infinitezerone.minibgm.feature.assistant.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownTest {
    @Test
    fun parseMarkdownBlocks_emptyText_returnsEmptyList() {
        assertTrue(parseMarkdownBlocks("").isEmpty())
        assertTrue(parseMarkdownBlocks("   \n\n  ").isEmpty())
    }

    @Test
    fun parseMarkdownBlocks_headingsAndParagraphs() {
        val markdown =
            """
            # 一级标题
            这是段落内容。

            ## 二级标题
            这是第二段。
            """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading && (blocks[0] as MarkdownBlock.Heading).level == 1)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph && (blocks[1] as MarkdownBlock.Paragraph).text == "这是段落内容。")
        assertTrue(blocks[2] is MarkdownBlock.Heading && (blocks[2] as MarkdownBlock.Heading).level == 2)
        assertTrue(blocks[3] is MarkdownBlock.Paragraph && (blocks[3] as MarkdownBlock.Paragraph).text == "这是第二段。")
    }

    @Test
    fun parseMarkdownBlocks_fencedCodeBlock() {
        val markdown =
            """
            下面是代码：
            ```kotlin
            fun main() {
                println("Hello")
            }
            ```
            执行完毕。
            """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        val codeBlock = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("println(\"Hello\")"))
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    @Test
    fun parseMarkdownBlocks_listsAndQuotes() {
        val markdown =
            """
            > 这是引用文字

            - 第一项
            - 第二项

            1. 有序项一
            2. 有序项二

            ---
            """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(6, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Blockquote)
        assertTrue(blocks[1] is MarkdownBlock.ListItem && (blocks[1] as MarkdownBlock.ListItem).prefix == "• ")
        assertTrue(blocks[2] is MarkdownBlock.ListItem && (blocks[2] as MarkdownBlock.ListItem).prefix == "• ")
        assertTrue(blocks[3] is MarkdownBlock.ListItem && (blocks[3] as MarkdownBlock.ListItem).prefix == "1. ")
        assertTrue(blocks[4] is MarkdownBlock.ListItem && (blocks[4] as MarkdownBlock.ListItem).prefix == "2. ")
        assertTrue(blocks[5] is MarkdownBlock.Divider)
    }

    @Test
    fun buildMarkdownAnnotatedString_parsesInlineStylesAndLinks() {
        val raw = "这是一段 **加粗文字** 和 *斜体*，还有 `val x = 1` 代码，以及 [Bangumi](https://bgm.tv) 链接与裸链接 https://github.com"
        val annotated =
            buildMarkdownAnnotatedString(
                content = raw,
                linkColor = Color.Blue,
                codeBgColor = Color.LightGray,
            )

        val text = annotated.text
        assertTrue(text.contains("加粗文字"))
        assertTrue(text.contains("斜体"))
        assertTrue(text.contains("val x = 1"))
        assertTrue(text.contains("Bangumi"))
        assertTrue(text.contains("https://github.com"))
        // 确保原生的 ** 或 [ 等语法标记被替换/剥离
        assertTrue(!text.contains("**加粗文字**"))
        assertTrue(!text.contains("[Bangumi](https://bgm.tv)"))
    }

    @Test
    fun parseMarkdownBlocks_handlesMultipleDividersAndParagraphSeparation() {
        val markdown =
            """
            段落一
            ***
            段落二
            ___
            段落三
            """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Divider)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
        assertTrue(blocks[3] is MarkdownBlock.Divider)
        assertTrue(blocks[4] is MarkdownBlock.Paragraph)
    }
}
