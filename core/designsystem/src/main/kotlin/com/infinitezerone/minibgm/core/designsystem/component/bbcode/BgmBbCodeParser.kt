package com.infinitezerone.minibgm.core.designsystem.component.bbcode

import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser

/**
 * Bangumi 块级语法树节点
 */
sealed interface BbCodeBlock {
    /**
     * 引用块：如 [quote][b]用户名[/b] 说: 引用的文本[/quote] 或 [quote]引用的文本[/quote]
     */
    data class Quote(
        val author: String?,
        val content: String,
    ) : BbCodeBlock

    /**
     * 独立图片块：如 [img]https://...[/img] 或 [img=224,126]https://...[/img]，
     * 支持被 [mask] 标签包裹时的黑幕剧透属性 [isMasked]。
     */
    data class Image(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
        val isMasked: Boolean = false,
    ) : BbCodeBlock {
        val aspectRatio: Float?
            get() =
                if (width != null && height != null && width > 0 && height > 0) {
                    width.toFloat() / height.toFloat()
                } else {
                    null
                }
    }

    /**
     * 富文本段落块
     */
    data class Paragraph(
        val rawText: String,
        val elements: List<BbInlineElement>,
    ) : BbCodeBlock
}

/**
 * Bangumi 行内语法树节点
 */
sealed interface BbInlineElement {
    /** 普通纯文本 */
    data class Plain(
        val text: String,
    ) : BbInlineElement

    /**
     * 样式文本：支持粗体、斜体、下划线、删除线、超链接、字号缩放
     */
    data class Styled(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val url: String? = null,
        val sizeScale: Float? = null,
    ) : BbInlineElement

    /**
     * 黑幕 / 剧透文本：如 [mask]剧透[/mask]，支持点击揭开
     */
    data class Mask(
        val id: String,
        val text: String,
    ) : BbInlineElement

    /**
     * Bangumi 经典娘表情贴图：如 (bgm38), (musume_14), (blake_01)
     */
    data class Sticker(
        val code: String,
        val stickerId: String,
        val url: String,
        val isLarge: Boolean = false,
    ) : BbInlineElement {
        val id: Int
            get() = stickerId.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
}

/**
 * Bangumi BBCode 解析器
 *
 * 专门解析 Bangumi 社区吐槽、短评与讨论帖中的 BBCode 及表情语法。
 * 具备极高容错性，针对未闭合标签或畸形输入提供平滑降级，确保永不崩溃。
 */
object BgmBbCodeParser {
    private val FULL_IMG_REGEX = Regex("""\[img((?:\s*=[^\]]*|\s+[^\]]*)?)\]([\s\S]*?)\[/img\]""", RegexOption.IGNORE_CASE)
    private val MASKED_IMG_REGEX =
        Regex("""\[__bgm_masked_img__((?:\s*=[^\]]*|\s+[^\]]*)?)\]([\s\S]*?)\[/__bgm_masked_img__\]""", RegexOption.IGNORE_CASE)
    private val QUOTE_AUTHOR_REGEX = Regex("""^(?:\[b\])?(.*?)(?:\[/b\])?\s*(?:说|:)\s*:\s*([\s\S]*)$""", RegexOption.DOT_MATCHES_ALL)
    private val STICKER_REGEX = Regex("""\((bgm|musume_?|blake_?)(\d+)\)""", RegexOption.IGNORE_CASE)
    private val MASK_REGEX = Regex("""\[mask\]([\s\S]*?)\[/mask\]""", RegexOption.IGNORE_CASE)
    private val UNWANTED_TAGS_REGEX = Regex("""\[/?(?:photo=\d+|right|size=\d+|color=[^\]]+)\]""", RegexOption.IGNORE_CASE)

    /**
     * 将包含 [img] 的 [mask] 标签解构转换：使其中的图片标记为 [__bgm_masked_img__]，
     * 并确保 mask 内部包裹的其他伴随文本仍保留为 [mask] 标签以便在段落中作为黑幕剧透渲染。
     */
    private fun preprocessMaskedImages(text: String): String {
        if (!text.contains("[mask", ignoreCase = true) || !text.contains("[img", ignoreCase = true)) {
            return text
        }

        val maskWithImgRegex = Regex("""\[mask\]([\s\S]*?)(?:\[/mask\]|$)""", RegexOption.IGNORE_CASE)
        return maskWithImgRegex.replace(text) { matchResult ->
            val inner = matchResult.groupValues[1]
            if (!inner.contains("[img", ignoreCase = true)) {
                matchResult.value
            } else {
                val sb = StringBuilder()
                var lastIdx = 0
                for (imgMatch in FULL_IMG_REGEX.findAll(inner)) {
                    val textBefore = inner.substring(lastIdx, imgMatch.range.first).trim()
                    if (textBefore.isNotEmpty()) {
                        sb.append("[mask]").append(textBefore).append("[/mask]\n")
                    }
                    val tagArgs = imgMatch.groupValues[1]
                    val imgUrl = imgMatch.groupValues[2].trim()
                    sb
                        .append("[__bgm_masked_img__")
                        .append(tagArgs)
                        .append("]")
                        .append(imgUrl)
                        .append("[/__bgm_masked_img__]\n")
                    lastIdx = imgMatch.range.last + 1
                }
                if (lastIdx < inner.length) {
                    val textAfter = inner.substring(lastIdx).trim()
                    if (textAfter.isNotEmpty()) {
                        sb.append("[mask]").append(textAfter).append("[/mask]\n")
                    }
                }
                sb.toString()
            }
        }
    }

    /**
     * 从 [from] 起查找平衡的 [quote]...[/quote]（支持嵌套），返回 整块区间 to 内容区间。
     * 未闭合时内容取到文末（平滑降级）；找不到返回 null。
     */
    private fun findBalancedQuote(
        text: String,
        from: Int,
    ): Pair<IntRange, IntRange>? {
        val open = text.indexOf("[quote]", from, ignoreCase = true)
        if (open < 0) return null
        val contentStart = open + "[quote]".length
        var depth = 1
        var idx = contentStart
        while (idx <= text.length) {
            val nextOpen = text.indexOf("[quote]", idx, ignoreCase = true)
            val nextClose = text.indexOf("[/quote]", idx, ignoreCase = true)
            when {
                // 未闭合：内容取到文末
                nextClose < 0 -> return (open..text.lastIndex) to (contentStart..text.lastIndex)
                // 嵌套的开标签出现在下一个闭标签之前
                nextOpen in 0 until nextClose -> {
                    depth++
                    idx = nextOpen + "[quote]".length
                }
                else -> {
                    depth--
                    if (depth == 0) {
                        return (open..(nextClose + "[/quote]".length - 1)) to (contentStart until nextClose)
                    }
                    idx = nextClose + "[/quote]".length
                }
            }
        }
        return (open..text.lastIndex) to (contentStart..text.lastIndex)
    }

    /**
     * 将原始评论文本解析为块级语法树列表
     */
    fun parseBlocks(rawText: String): List<BbCodeBlock> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val preprocessed = preprocessMaskedImages(trimmed)

        val blocks = mutableListOf<BbCodeBlock>()
        var currentIndex = 0

        // 非引用块级元素（屏蔽图 / 普通图片）
        val imgBlockRegex =
            Regex(
                """(\[__bgm_masked_img__(?:\s*=[^\]]*|\s+[^\]]*)?\][\s\S]*?\[/__bgm_masked_img__\]|\[img(?:\s*=[^\]]*|\s+[^\]]*)?\][\s\S]*?\[/img\])""",
                RegexOption.IGNORE_CASE,
            )

        while (currentIndex <= preprocessed.lastIndex) {
            val quote = findBalancedQuote(preprocessed, currentIndex)
            val imgMatch = imgBlockRegex.find(preprocessed, currentIndex)
            val nextQuoteStart = quote?.first?.first ?: Int.MAX_VALUE
            val nextImgStart = imgMatch?.range?.first ?: Int.MAX_VALUE

            if (nextQuoteStart == Int.MAX_VALUE && nextImgStart == Int.MAX_VALUE) {
                // 剩余纯文本
                val remaining = preprocessed.substring(currentIndex).trim()
                if (remaining.isNotEmpty()) {
                    blocks.add(parseParagraph(remaining))
                }
                break
            }

            if (nextQuoteStart <= nextImgStart) {
                val (blockRange, contentRange) = quote!!
                if (blockRange.first > currentIndex) {
                    val textSegment = preprocessed.substring(currentIndex, blockRange.first).trim()
                    if (textSegment.isNotEmpty()) {
                        blocks.add(parseParagraph(textSegment))
                    }
                }
                val quoteInner = preprocessed.substring(contentRange).trim()
                val authorMatch = QUOTE_AUTHOR_REGEX.find(quoteInner)
                if (authorMatch != null) {
                    val author = authorMatch.groupValues[1].trim()
                    val content = authorMatch.groupValues[2].trim()
                    blocks.add(BbCodeBlock.Quote(author = author.ifBlank { null }, content = content))
                } else {
                    blocks.add(BbCodeBlock.Quote(author = null, content = quoteInner))
                }
                currentIndex = blockRange.last + 1
            } else {
                val match = imgMatch!!
                if (match.range.first > currentIndex) {
                    val textSegment = preprocessed.substring(currentIndex, match.range.first).trim()
                    if (textSegment.isNotEmpty()) {
                        blocks.add(parseParagraph(textSegment))
                    }
                }
                val matchedStr = match.value
                val isMasked = matchedStr.startsWith("[__bgm_masked_img__", ignoreCase = true)
                val matchResult =
                    if (isMasked) {
                        MASKED_IMG_REGEX.find(matchedStr)
                    } else {
                        FULL_IMG_REGEX.find(matchedStr)
                    }
                if (matchResult != null) {
                    val tagArgs = matchResult.groupValues[1]
                    val imgUrl = matchResult.groupValues[2].trim()
                    val (w, h) = parseImageDimensions(tagArgs)
                    if (imgUrl.isNotBlank()) {
                        blocks.add(BbCodeBlock.Image(url = imgUrl, width = w, height = h, isMasked = isMasked))
                    }
                }
                currentIndex = match.range.last + 1
            }
        }

        return blocks
    }

    /**
     * 解析 [img] 标签中的尺寸属性（如 [img=224,126], [img=224x126], [img width=224 height=126]）
     */
    private fun parseImageDimensions(rawArgs: String): Pair<Int?, Int?> {
        val clean = rawArgs.trim()
        if (clean.isEmpty()) return null to null

        // 1. [img=224,126] 或 [img=224x126] 或 [img=224]
        if (clean.startsWith("=")) {
            val value = clean.removePrefix("=").trim().trim('"', '\'')
            if (value.contains(",")) {
                val parts = value.split(",")
                val w = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val h = parts.getOrNull(1)?.trim()?.toIntOrNull()
                return w to h
            }
            if (value.contains("x", ignoreCase = true)) {
                val parts = value.split(Regex("[xX]"))
                val w = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val h = parts.getOrNull(1)?.trim()?.toIntOrNull()
                return w to h
            }
            val w = value.toIntOrNull()
            return w to null
        }

        // 2. [img width=224 height=126] 或 [img w=224 h=126]
        val widthRegex = Regex("""\b(?:width|w)\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
        val heightRegex = Regex("""\b(?:height|h)\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
        val w =
            widthRegex
                .find(clean)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        val h =
            heightRegex
                .find(clean)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        return w to h
    }

    /**
     * 将一段非块级文本解析为包含丰富行内样式的 Paragraph
     */
    fun parseParagraph(rawParagraph: String): BbCodeBlock.Paragraph {
        // 先清理不影响排版的未知废弃标签（如 [right] 等）
        val cleanParagraph = rawParagraph.replace(UNWANTED_TAGS_REGEX, "")

        val elements = mutableListOf<BbInlineElement>()
        var maskCounter = 0

        // 正则识别 [mask]...[/mask] 与 (bgmXX) / (musume_XX) / (blake_XX) 贴图
        val inlineTokenRegex =
            Regex(
                """(\[mask\][\s\S]*?\[/mask\]|\((?:bgm|musume_?|blake_?)\d+\))""",
                RegexOption.IGNORE_CASE,
            )
        var currentIndex = 0

        val matches = inlineTokenRegex.findAll(cleanParagraph)
        for (match in matches) {
            val range = match.range
            if (range.first > currentIndex) {
                val textChunk = cleanParagraph.substring(currentIndex, range.first)
                if (textChunk.isNotEmpty()) {
                    elements.addAll(parseFormattedText(textChunk))
                }
            }

            val token = match.value
            if (token.startsWith("[mask", ignoreCase = true)) {
                val inner =
                    MASK_REGEX
                        .find(token)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                val maskId = "mask_${++maskCounter}_${inner.hashCode()}"
                elements.add(BbInlineElement.Mask(id = maskId, text = inner))
            } else if (token.startsWith("(", ignoreCase = true)) {
                val stickerMatch = STICKER_REGEX.find(token)
                if (stickerMatch != null) {
                    val prefix = stickerMatch.groupValues[1].lowercase().removeSuffix("_")
                    val num = stickerMatch.groupValues[2].toIntOrNull() ?: 0
                    val paddedNum = num.toString().padStart(2, '0')
                    val (category, url, isLarge) =
                        when (prefix) {
                            "musume" -> Triple("musume", "https://lain.bgm.tv/img/smiles/musume/musume_$paddedNum.gif", true)
                            "blake" -> Triple("blake", "https://lain.bgm.tv/img/smiles/blake/blake_$paddedNum.gif", true)
                            else -> Triple("bgm", "https://lain.bgm.tv/img/smiles/tv/$paddedNum.gif", false)
                        }
                    val stickerId = "${category}_$paddedNum"
                    elements.add(
                        BbInlineElement.Sticker(
                            code = token,
                            stickerId = stickerId,
                            url = url,
                            isLarge = isLarge,
                        ),
                    )
                }
            }

            currentIndex = range.last + 1
        }

        if (currentIndex < cleanParagraph.length) {
            val remaining = cleanParagraph.substring(currentIndex)
            if (remaining.isNotEmpty()) {
                elements.addAll(parseFormattedText(remaining))
            }
        }

        return BbCodeBlock.Paragraph(rawText = cleanParagraph, elements = elements)
    }

    private val RAW_URL_REGEX = Regex("""https?://[a-zA-Z0-9_\-.~:/?#@!$&*+,;%=]+""")
    private val TRAILING_PUNCTUATION =
        charArrayOf(
            '.',
            ',',
            '!',
            '?',
            ';',
            ':',
            ')',
            ']',
            '}',
            '。',
            '，',
            '！',
            '？',
            '；',
            '：',
            '）',
            '」',
            '』',
            '”',
            '’',
        )

    /** 行内样式的可继承表示：嵌套标签时子元素在父样式上叠加 */
    private data class InlineStyle(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val url: String? = null,
    ) {
        val isEmpty: Boolean
            get() = !isBold && !isItalic && !isUnderline && !isStrikethrough && url == null
    }

    private fun InlineStyle.merge(other: InlineStyle): InlineStyle =
        InlineStyle(
            isBold = isBold || other.isBold,
            isItalic = isItalic || other.isItalic,
            isUnderline = isUnderline || other.isUnderline,
            isStrikethrough = isStrikethrough || other.isStrikethrough,
            url = other.url ?: url,
        )

    private fun applyStyle(
        elements: List<BbInlineElement>,
        style: InlineStyle,
    ): List<BbInlineElement> =
        elements.map { element ->
            when (element) {
                is BbInlineElement.Plain ->
                    BbInlineElement.Styled(
                        text = element.text,
                        isBold = style.isBold,
                        isItalic = style.isItalic,
                        isUnderline = style.isUnderline,
                        isStrikethrough = style.isStrikethrough,
                        url = style.url,
                    )
                is BbInlineElement.Styled ->
                    element.copy(
                        isBold = element.isBold || style.isBold,
                        isItalic = element.isItalic || style.isItalic,
                        isUnderline = element.isUnderline || style.isUnderline,
                        isStrikethrough = element.isStrikethrough || style.isStrikethrough,
                        url = element.url ?: style.url,
                    )
                else -> element // Mask / Sticker 保持原样
            }
        }

    /**
     * 解析基础格式化标签：[b], [i], [s], [u], [url] 以及裸 URL 链接识别。
     * 支持嵌套（如 [b]粗体[i]粗斜体[/i][/b]）：子标签在父样式上递归叠加，
     * 未被任何标签识别的内容原样保留，不会把原始标签文本渲染给用户。
     */
    private fun parseFormattedText(
        text: String,
        style: InlineStyle = InlineStyle(),
    ): List<BbInlineElement> {
        if (!text.contains('[') || !text.contains(']')) {
            return parsePlainAndRawUrls(text, style)
        }

        val results = mutableListOf<BbInlineElement>()
        val tagRegex = Regex("""\[(b|i|s|u|url)(?:=([^\]]+))?\]([\s\S]*?)\[/\1\]""", RegexOption.IGNORE_CASE)
        var currentIndex = 0

        val matches = tagRegex.findAll(text)
        for (match in matches) {
            val range = match.range
            if (range.first > currentIndex) {
                val plainPart = text.substring(currentIndex, range.first)
                if (plainPart.isNotEmpty()) {
                    results.addAll(parsePlainAndRawUrls(plainPart, style))
                }
            }

            val tagName =
                match.groups[1]
                    ?.value
                    .orEmpty()
                    .lowercase()
            val tagArg = match.groups[2]?.value?.trim('"', '\'')
            val innerContent = match.groups[3]?.value.orEmpty()

            when (tagName) {
                "b" -> results.addAll(applyStyle(parseFormattedText(innerContent, style), style.copy(isBold = true)))
                "i" -> results.addAll(applyStyle(parseFormattedText(innerContent, style), style.copy(isItalic = true)))
                "s" ->
                    results.addAll(
                        applyStyle(parseFormattedText(innerContent, style), style.copy(isStrikethrough = true)),
                    )
                "u" ->
                    results.addAll(
                        applyStyle(parseFormattedText(innerContent, style), style.copy(isUnderline = true)),
                    )
                "url" -> {
                    val url = tagArg?.ifBlank { null } ?: innerContent.trim()
                    // 子内容递归解析：内容即 URL 时裸链接自动美化 Bangumi 文案，
                    // 自定义文案则作为 Plain 被叠加 url + 下划线样式
                    results.addAll(
                        applyStyle(
                            parseFormattedText(innerContent, style),
                            style.merge(InlineStyle(isUnderline = true, url = url)),
                        ),
                    )
                }
                else -> {
                    results.addAll(parsePlainAndRawUrls(innerContent, style))
                }
            }

            currentIndex = range.last + 1
        }

        if (currentIndex < text.length) {
            val remaining = text.substring(currentIndex)
            if (remaining.isNotEmpty()) {
                results.addAll(parsePlainAndRawUrls(remaining, style))
            }
        }

        return if (results.isEmpty()) {
            applyStyle(listOf(BbInlineElement.Plain(text)), style)
        } else {
            results
        }
    }

    /**
     * 解析普通文本段落中的裸 URL（Autolink），并自动美化 Bangumi 内部链接文案
     */
    private fun parsePlainAndRawUrls(
        text: String,
        style: InlineStyle = InlineStyle(),
    ): List<BbInlineElement> {
        if (!text.contains("http://", ignoreCase = true) && !text.contains("https://", ignoreCase = true)) {
            return when {
                text.isEmpty() -> emptyList()
                style.isEmpty -> listOf(BbInlineElement.Plain(text))
                else -> applyStyle(listOf(BbInlineElement.Plain(text)), style)
            }
        }

        val results = mutableListOf<BbInlineElement>()
        var currentIndex = 0
        val matches = RAW_URL_REGEX.findAll(text)

        for (match in matches) {
            val range = match.range
            if (range.first > currentIndex) {
                val plainBefore = text.substring(currentIndex, range.first)
                if (plainBefore.isNotEmpty()) {
                    results.add(
                        if (style.isEmpty) {
                            BbInlineElement.Plain(plainBefore)
                        } else {
                            applyStyle(listOf(BbInlineElement.Plain(plainBefore)), style).first()
                        },
                    )
                }
            }

            var rawUrl = match.value
            var trailingPunct = ""
            while (rawUrl.isNotEmpty() && rawUrl.last() in TRAILING_PUNCTUATION) {
                trailingPunct = rawUrl.last() + trailingPunct
                rawUrl = rawUrl.dropLast(1)
            }

            if (rawUrl.isNotEmpty()) {
                val link = BgmUrlParser.parse(rawUrl)
                val displayText =
                    if (link !is BgmLink.External) {
                        BgmUrlParser.formatDisplayLabel(link)
                    } else {
                        rawUrl
                    }
                results.add(
                    BbInlineElement.Styled(
                        text = displayText,
                        url = rawUrl,
                        isUnderline = true,
                        isBold = style.isBold,
                        isItalic = style.isItalic,
                        isStrikethrough = style.isStrikethrough,
                    ),
                )
            }
            if (trailingPunct.isNotEmpty()) {
                results.add(
                    if (style.isEmpty) {
                        BbInlineElement.Plain(trailingPunct)
                    } else {
                        applyStyle(listOf(BbInlineElement.Plain(trailingPunct)), style).first()
                    },
                )
            }

            currentIndex = range.last + 1
        }

        if (currentIndex < text.length) {
            val remaining = text.substring(currentIndex)
            if (remaining.isNotEmpty()) {
                results.add(
                    if (style.isEmpty) {
                        BbInlineElement.Plain(remaining)
                    } else {
                        applyStyle(listOf(BbInlineElement.Plain(remaining)), style).first()
                    },
                )
            }
        }

        return if (results.isEmpty()) {
            applyStyle(listOf(BbInlineElement.Plain(text)), style)
        } else {
            results
        }
    }
}
