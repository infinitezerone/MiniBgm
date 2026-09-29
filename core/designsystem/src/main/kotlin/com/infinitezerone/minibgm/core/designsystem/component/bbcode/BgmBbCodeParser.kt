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
     * 样式文本：支持粗体、斜体、下划线、删除线、超链接、字号缩放、字体颜色
     */
    data class Styled(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val url: String? = null,
        val sizeScale: Float? = null,
        val colorHex: String? = null,
    ) : BbInlineElement

    /**
     * 黑幕 / 剧透文本：如 [mask]剧透[/mask]，支持点击揭开，支持内部嵌套样式与表情
     */
    data class Mask(
        val id: String,
        val text: String,
        val elements: List<BbInlineElement> = emptyList(),
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
    private val cacheLock = Any()
    private val blocksCache =
        object : LinkedHashMap<String, List<BbCodeBlock>>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<BbCodeBlock>>?): Boolean = size > 256
        }

    private val FULL_IMG_REGEX = Regex("""\[img((?:\s*=[^\]]*|\s+[^\]]*)?)\]([\s\S]*?)\[/img\]""", RegexOption.IGNORE_CASE)
    private val MASKED_IMG_REGEX =
        Regex("""\[__bgm_masked_img__((?:\s*=[^\]]*|\s+[^\]]*)?)\]([\s\S]*?)\[/__bgm_masked_img__\]""", RegexOption.IGNORE_CASE)
    private val QUOTE_AUTHOR_REGEX = Regex("""^(?:\[b\])?(.*?)(?:\[/b\])?\s*(?:说|:)\s*:\s*([\s\S]*)$""", RegexOption.DOT_MATCHES_ALL)
    private val STRIPPED_TAGS_REGEX = Regex("""\[/?(?:photo=\d+|right)\]""", RegexOption.IGNORE_CASE)
    private val INLINE_TOKEN_REGEX =
        Regex(
            """\[(/?)([a-zA-Z]+)(?:=([^\]]+))?\]|\((bgm|musume_?|blake_?)(\d+)\)|https?://[a-zA-Z0-9_\-.~:/?#@!$&*+,;%=]+""",
            RegexOption.IGNORE_CASE,
        )
    private val SUPPORTED_INLINE_TAGS = setOf("b", "i", "u", "s", "mask", "color", "size", "url")

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
     * 将原始评论文本解析为块级语法树列表（内置 LRU 缓存避免多条评论或重复重组时反复正则计算）
     */
    fun parseBlocks(rawText: String): List<BbCodeBlock> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        synchronized(cacheLock) {
            blocksCache[trimmed]?.let { return it }
        }

        val parsed = doParseBlocks(trimmed)

        synchronized(cacheLock) {
            blocksCache[trimmed] = parsed
        }
        return parsed
    }

    private fun doParseBlocks(preprocessedText: String): List<BbCodeBlock> {
        val preprocessed = preprocessMaskedImages(preprocessedText)

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

    private sealed interface Token {
        data class TagOpen(
            val name: String,
            val arg: String?,
            val raw: String,
        ) : Token

        data class TagClose(
            val name: String,
            val raw: String,
        ) : Token

        data class Sticker(
            val code: String,
            val stickerId: String,
            val url: String,
            val isLarge: Boolean,
        ) : Token

        data class RawUrl(
            val url: String,
        ) : Token

        data class Text(
            val content: String,
        ) : Token
    }

    private data class StyleScope(
        val tag: String,
        val arg: String?,
    )

    private class MaskFrame(
        val id: String,
        val elements: MutableList<BbInlineElement> = mutableListOf(),
    )

    private data class InlineStyle(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val url: String? = null,
        val sizeScale: Float? = null,
        val colorHex: String? = null,
    ) {
        val isEmpty: Boolean
            get() = !isBold && !isItalic && !isUnderline && !isStrikethrough && url == null && sizeScale == null && colorHex == null
    }

    private fun computeActiveStyle(styleStack: Collection<StyleScope>): InlineStyle {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isStrikethrough = false
        var url: String? = null
        var sizeScale: Float? = null
        var colorHex: String? = null

        for (scope in styleStack) {
            when (scope.tag) {
                "b" -> isBold = true
                "i" -> isItalic = true
                "u" -> isUnderline = true
                "s" -> isStrikethrough = true
                "url" -> {
                    isUnderline = true
                    if (scope.arg != null) url = scope.arg
                }
                "color" -> if (scope.arg != null) colorHex = scope.arg
                "size" ->
                    if (scope.arg != null) {
                        val pt = scope.arg.toIntOrNull() ?: 14
                        sizeScale = (pt.toFloat() / 14f).coerceIn(0.7f, 1.8f)
                    }
            }
        }
        return InlineStyle(
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            isStrikethrough = isStrikethrough,
            url = url,
            sizeScale = sizeScale,
            colorHex = colorHex,
        )
    }

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var currentIndex = 0
        val matches = INLINE_TOKEN_REGEX.findAll(text)

        for (match in matches) {
            val range = match.range
            if (range.first > currentIndex) {
                val plainBefore = text.substring(currentIndex, range.first)
                if (plainBefore.isNotEmpty()) {
                    tokens.add(Token.Text(plainBefore))
                }
            }

            val raw = match.value
            val isCloseSlash = match.groups[1]?.value?.isNotEmpty() == true
            val tagName = match.groups[2]?.value?.lowercase()

            if (tagName != null) {
                if (tagName in SUPPORTED_INLINE_TAGS) {
                    if (isCloseSlash) {
                        tokens.add(Token.TagClose(name = tagName, raw = raw))
                    } else {
                        val tagArg =
                            match.groups[3]
                                ?.value
                                ?.trim()
                                ?.trim('"', '\'')
                        tokens.add(Token.TagOpen(name = tagName, arg = tagArg, raw = raw))
                    }
                } else {
                    tokens.add(Token.Text(raw))
                }
            } else if (match.groups[4] != null) {
                val prefix =
                    match.groups[4]!!
                        .value
                        .lowercase()
                        .removeSuffix("_")
                val num = match.groups[5]?.value?.toIntOrNull() ?: 0
                val paddedNum = num.toString().padStart(2, '0')
                val (category, url, isLarge) =
                    when (prefix) {
                        "musume" -> Triple("musume", "https://lain.bgm.tv/img/smiles/musume/musume_$paddedNum.gif", true)
                        "blake" -> Triple("blake", "https://lain.bgm.tv/img/smiles/blake/blake_$paddedNum.gif", true)
                        else -> Triple("bgm", "https://lain.bgm.tv/img/smiles/tv/$paddedNum.gif", false)
                    }
                val stickerId = "${category}_$paddedNum"
                tokens.add(Token.Sticker(code = raw, stickerId = stickerId, url = url, isLarge = isLarge))
            } else {
                var rawUrl = raw
                var trailingPunct = ""
                while (rawUrl.isNotEmpty() && rawUrl.last() in TRAILING_PUNCTUATION) {
                    trailingPunct = rawUrl.last() + trailingPunct
                    rawUrl = rawUrl.dropLast(1)
                }
                if (rawUrl.isNotEmpty()) {
                    tokens.add(Token.RawUrl(rawUrl))
                }
                if (trailingPunct.isNotEmpty()) {
                    tokens.add(Token.Text(trailingPunct))
                }
            }

            currentIndex = range.last + 1
        }

        if (currentIndex < text.length) {
            val remaining = text.substring(currentIndex)
            if (remaining.isNotEmpty()) {
                tokens.add(Token.Text(remaining))
            }
        }

        return tokens
    }

    private fun List<BbInlineElement>.extractPlainText(): String =
        joinToString("") { element ->
            when (element) {
                is BbInlineElement.Plain -> element.text
                is BbInlineElement.Styled -> element.text
                is BbInlineElement.Mask -> element.text
                is BbInlineElement.Sticker -> element.code
            }
        }

    private fun MutableList<BbInlineElement>.addOrMerge(element: BbInlineElement) {
        if (element is BbInlineElement.Plain && element.text.isEmpty()) return
        if (element is BbInlineElement.Styled && element.text.isEmpty()) return

        if (isEmpty()) {
            add(element)
            return
        }
        val last = last()
        if (last is BbInlineElement.Plain && element is BbInlineElement.Plain) {
            set(lastIndex, BbInlineElement.Plain(last.text + element.text))
        } else if (last is BbInlineElement.Styled &&
            element is BbInlineElement.Styled &&
            last.isBold == element.isBold &&
            last.isItalic == element.isItalic &&
            last.isUnderline == element.isUnderline &&
            last.isStrikethrough == element.isStrikethrough &&
            last.url == element.url &&
            last.sizeScale == element.sizeScale &&
            last.colorHex == element.colorHex
        ) {
            set(lastIndex, last.copy(text = last.text + element.text))
        } else {
            add(element)
        }
    }

    /**
     * 将一段非块级文本解析为包含丰富行内样式的 Paragraph。
     * 采用词法 Token 流 + 作用域下推栈（Pushdown Stack Automaton）算法，
     * 彻底解决同名嵌套、交叉嵌套、未闭合容错与黑幕内部富文本支持。
     */
    fun parseParagraph(rawParagraph: String): BbCodeBlock.Paragraph {
        val cleanParagraph = rawParagraph.replace(STRIPPED_TAGS_REGEX, "")
        if (cleanParagraph.isEmpty()) {
            return BbCodeBlock.Paragraph(rawText = "", elements = emptyList())
        }

        val tokens = tokenize(cleanParagraph)
        val styleStack = mutableListOf<StyleScope>()
        val maskStack = ArrayDeque<MaskFrame>()
        val rootElements = mutableListOf<BbInlineElement>()
        var maskCounter = 0

        fun addElement(element: BbInlineElement) {
            val targetList = if (maskStack.isNotEmpty()) maskStack.last().elements else rootElements
            targetList.addOrMerge(element)
        }

        for (token in tokens) {
            when (token) {
                is Token.TagOpen -> {
                    if (token.name == "mask") {
                        val maskId = "mask_${++maskCounter}_${token.raw.hashCode()}"
                        maskStack.addLast(MaskFrame(id = maskId))
                    } else {
                        styleStack.add(StyleScope(tag = token.name, arg = token.arg))
                    }
                }
                is Token.TagClose -> {
                    if (token.name == "mask") {
                        if (maskStack.isNotEmpty()) {
                            val frame = maskStack.removeLast()
                            val plainText = frame.elements.extractPlainText()
                            val maskElement =
                                BbInlineElement.Mask(
                                    id = frame.id,
                                    text = plainText,
                                    elements = frame.elements.toList(),
                                )
                            addElement(maskElement)
                        } else {
                            addElement(BbInlineElement.Plain(token.raw))
                        }
                    } else {
                        val lastIdx = styleStack.indexOfLast { it.tag == token.name }
                        if (lastIdx >= 0) {
                            styleStack.removeAt(lastIdx)
                        } else {
                            addElement(BbInlineElement.Plain(token.raw))
                        }
                    }
                }

                is Token.Text -> {
                    val activeUrl = styleStack.lastOrNull { it.tag == "url" }
                    if (activeUrl != null && activeUrl.arg == null) {
                        val trimmedUrl = token.content.trim()
                        val link = BgmUrlParser.parse(trimmedUrl)
                        val displayText =
                            if (link !is BgmLink.External) {
                                BgmUrlParser.formatDisplayLabel(link)
                            } else {
                                token.content
                            }
                        val style = computeActiveStyle(styleStack)
                        addElement(
                            BbInlineElement.Styled(
                                text = displayText,
                                url = trimmedUrl,
                                isUnderline = true,
                                isBold = style.isBold,
                                isItalic = style.isItalic,
                                isStrikethrough = style.isStrikethrough,
                                sizeScale = style.sizeScale,
                                colorHex = style.colorHex,
                            ),
                        )
                    } else {
                        val style = computeActiveStyle(styleStack)
                        if (style.isEmpty) {
                            addElement(BbInlineElement.Plain(token.content))
                        } else {
                            addElement(
                                BbInlineElement.Styled(
                                    text = token.content,
                                    isBold = style.isBold,
                                    isItalic = style.isItalic,
                                    isUnderline = style.isUnderline,
                                    isStrikethrough = style.isStrikethrough,
                                    url = style.url,
                                    sizeScale = style.sizeScale,
                                    colorHex = style.colorHex,
                                ),
                            )
                        }
                    }
                }
                is Token.RawUrl -> {
                    val activeUrl = styleStack.lastOrNull { it.tag == "url" }
                    if (activeUrl != null && activeUrl.arg != null) {
                        val style = computeActiveStyle(styleStack)
                        addElement(
                            BbInlineElement.Styled(
                                text = token.url,
                                isBold = style.isBold,
                                isItalic = style.isItalic,
                                isUnderline = style.isUnderline,
                                isStrikethrough = style.isStrikethrough,
                                url = style.url,
                                sizeScale = style.sizeScale,
                                colorHex = style.colorHex,
                            ),
                        )
                    } else {
                        val link = BgmUrlParser.parse(token.url)
                        val displayText =
                            if (link !is BgmLink.External) {
                                BgmUrlParser.formatDisplayLabel(link)
                            } else {
                                token.url
                            }
                        val style = computeActiveStyle(styleStack)
                        addElement(
                            BbInlineElement.Styled(
                                text = displayText,
                                url = token.url,
                                isUnderline = true,
                                isBold = style.isBold,
                                isItalic = style.isItalic,
                                isStrikethrough = style.isStrikethrough,
                                sizeScale = style.sizeScale,
                                colorHex = style.colorHex,
                            ),
                        )
                    }
                }
                is Token.Sticker -> {
                    addElement(
                        BbInlineElement.Sticker(
                            code = token.code,
                            stickerId = token.stickerId,
                            url = token.url,
                            isLarge = token.isLarge,
                        ),
                    )
                }
            }
        }

        while (maskStack.isNotEmpty()) {
            val frame = maskStack.removeLast()
            val plainText = frame.elements.extractPlainText()
            val maskElement =
                BbInlineElement.Mask(
                    id = frame.id,
                    text = plainText,
                    elements = frame.elements.toList(),
                )
            addElement(maskElement)
        }

        return BbCodeBlock.Paragraph(rawText = cleanParagraph, elements = rootElements)
    }
}
