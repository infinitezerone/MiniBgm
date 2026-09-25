package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TRAILING_PUNCTUATION = charArrayOf('，', '。', '、', '）', ')', '】', '」', '’', '"', '.', ',')

private val INLINE_TOKEN_REGEX =
    Regex(
        """(\[([^\]]+)\]\((https?://[^\s)]+)\))|""" +
            """(`([^`]+)`)|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\*([^*]+)\*|_([^_]+)_|(https?://[^\s<>"'{}|\\^`]+)""",
    )

private val FENCED_CODE_BLOCK_REGEX = Regex("""```([A-Za-z0-9_+-]*)\s*\n([\s\S]*?)```""")

internal sealed interface MarkdownBlock {
    data class Paragraph(
        val text: String,
    ) : MarkdownBlock

    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class CodeBlock(
        val language: String,
        val code: String,
    ) : MarkdownBlock

    data class ListItem(
        val prefix: String,
        val text: String,
    ) : MarkdownBlock

    data class Blockquote(
        val text: String,
    ) : MarkdownBlock

    data object Divider : MarkdownBlock
}

/**
 * 将 Markdown 格式的原始消息文本解析为块级元素序列
 */
internal fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    if (raw.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    var cursor = 0

    FENCED_CODE_BLOCK_REGEX.findAll(raw).forEach { match ->
        val textBefore = raw.substring(cursor, match.range.first)
        if (textBefore.isNotBlank()) {
            parseTextLines(textBefore, blocks)
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(language = language, code = code))
        cursor = match.range.last + 1
    }

    if (cursor < raw.length) {
        val remaining = raw.substring(cursor)
        if (remaining.isNotBlank()) {
            parseTextLines(remaining, blocks)
        }
    }

    return blocks
}

private fun isSpecialMarkdownLine(trimmed: String): Boolean =
    trimmed.startsWith("#") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.startsWith("> ") ||
        trimmed.matches(Regex("""^\d+\.\s+.*""")) ||
        trimmed == "---" ||
        trimmed == "***" ||
        trimmed == "___"

private fun parseTextLines(
    text: String,
    out: MutableList<MarkdownBlock>,
) {
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> {
                i++
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                out.add(MarkdownBlock.Divider)
                i++
            }
            trimmed.startsWith("### ") -> {
                out.add(MarkdownBlock.Heading(level = 3, text = trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                out.add(MarkdownBlock.Heading(level = 2, text = trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("# ") -> {
                out.add(MarkdownBlock.Heading(level = 1, text = trimmed.removePrefix("# ").trim()))
                i++
            }
            trimmed.startsWith("> ") -> {
                out.add(MarkdownBlock.Blockquote(text = trimmed.removePrefix("> ").trim()))
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val prefix = "• "
                val itemText = trimmed.substring(2).trim()
                out.add(MarkdownBlock.ListItem(prefix = prefix, text = itemText))
                i++
            }
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val num = trimmed.substringBefore('.')
                val itemText = trimmed.substringAfter('.').trim()
                out.add(MarkdownBlock.ListItem(prefix = "$num. ", text = itemText))
                i++
            }
            else -> {
                // 普通段落，连续行合并
                val paragraphLines = mutableListOf(line)
                while (i + 1 < lines.size &&
                    lines[i + 1].isNotBlank() &&
                    !isSpecialMarkdownLine(lines[i + 1].trim())
                ) {
                    i++
                    paragraphLines.add(lines[i].trimEnd())
                }
                out.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
                i++
            }
        }
    }
}

/**
 * 将行内 Markdown（粗体、斜体、删除线、行内代码、链接）解析为 Compose AnnotatedString
 */
internal fun buildMarkdownAnnotatedString(
    content: String,
    linkColor: Color,
    codeBgColor: Color,
    onOpenUrl: ((String) -> Unit)? = null,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0

        INLINE_TOKEN_REGEX.findAll(content).forEach { match ->
            if (match.range.first > cursor) {
                append(content.substring(cursor, match.range.first))
            }

            when {
                // Markdown 链接 [title](url)
                match.groups[1] != null -> {
                    val title = match.groups[2]!!.value
                    val url = match.groups[3]!!.value
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles =
                                TextLinkStyles(
                                    style =
                                        SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                ),
                            linkInteractionListener = { onOpenUrl?.invoke(url) },
                        ),
                    ) {
                        append(title)
                    }
                }
                // 行内代码 `code`
                match.groups[4] != null -> {
                    val code = match.groups[5]!!.value
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            fontSize = 13.sp,
                        ),
                    ) {
                        append(" $code ")
                    }
                }
                // 粗体 **bold** 或 __bold__
                match.groups[6] != null || match.groups[7] != null -> {
                    val boldText = match.groups[6]?.value ?: match.groups[7]!!.value
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }
                // 删除线 ~~strike~~
                match.groups[8] != null -> {
                    val strikeText = match.groups[8]!!.value
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(strikeText)
                    }
                }
                // 斜体 *italic* 或 _italic_
                match.groups[9] != null || match.groups[10] != null -> {
                    val italicText = match.groups[9]?.value ?: match.groups[10]!!.value
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italicText)
                    }
                }
                // 裸 URL
                match.groups[11] != null -> {
                    val rawMatch = match.groups[11]!!.value
                    val url = rawMatch.trimEnd { it in TRAILING_PUNCTUATION }
                    val trailing = rawMatch.substring(url.length)
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles =
                                TextLinkStyles(
                                    style =
                                        SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                ),
                            linkInteractionListener = { onOpenUrl?.invoke(url) },
                        ),
                    ) {
                        append(url)
                    }
                    if (trailing.isNotEmpty()) {
                        append(trailing)
                    }
                }
            }

            cursor = match.range.last + 1
        }

        if (cursor < content.length) {
            append(content.substring(cursor))
        }
    }

/**
 * 原生 Compose Markdown 富文本渲染组件
 */
@Composable
fun AssistantMarkdownText(
    content: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val blocks = parseMarkdownBlocks(content)

    if (blocks.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    val annotated =
                        buildMarkdownAnnotatedString(
                            content = block.text,
                            linkColor = linkColor,
                            codeBgColor = codeBgColor,
                            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                        )
                    Text(text = annotated, style = style)
                }
                is MarkdownBlock.Heading -> {
                    val headingStyle =
                        when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            2 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            else -> style.copy(fontWeight = FontWeight.Bold)
                        }
                    val annotated =
                        buildMarkdownAnnotatedString(
                            content = block.text,
                            linkColor = linkColor,
                            codeBgColor = codeBgColor,
                            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                        )
                    Text(text = annotated, style = headingStyle)
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .padding(10.dp)
                                    .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.prefix,
                            style = style.copy(fontWeight = FontWeight.Bold),
                        )
                        val annotated =
                            buildMarkdownAnnotatedString(
                                content = block.text,
                                linkColor = linkColor,
                                codeBgColor = codeBgColor,
                                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                            )
                        Text(
                            text = annotated,
                            style = style,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                        val annotated =
                            buildMarkdownAnnotatedString(
                                content = block.text,
                                linkColor = linkColor,
                                codeBgColor = codeBgColor,
                                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                            )
                        Text(
                            text = annotated,
                            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
