package com.infinitezerone.minibgm.core.designsystem.component.bbcode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser

/**
 * Bangumi 专用 BBCode 富文本渲染组件
 *
 * 原生支持：
 * - [quote] 引用块（带强调色左竖线、浅色卡片底色及作者注明）
 * - [mask] 黑幕刮刮乐（默认遮罩，点击原地刮开揭晓/再次点击遮挡）
 * - [b] 粗体、[s] 删除线、[i] 斜体、[u] 下划线、[url] 超链接
 * - (bgmXX) 行内娘表情贴图（无缝排版与官方 GIF 加载，防遮挡与垂直居中）
 * - (musume_XX) / (blake_XX) 专属大表情贴图（自动成组网格展示，防文字挤压遮挡）
 * - [img] 独立安全限高与宽高比自适应图片渲染
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BgmBbCodeContent(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onUrlClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(content) { BgmBbCodeParser.parseBlocks(content) }

    if (blocks.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is BbCodeBlock.Quote -> {
                    BgmBbCodeQuote(
                        quote = block,
                        style = style,
                        color = color,
                        onUrlClick = onUrlClick,
                    )
                }
                is BbCodeBlock.Image -> {
                    BgmBbCodeImage(
                        image = block,
                        onUrlClick = onUrlClick,
                    )
                }
                is BbCodeBlock.Paragraph -> {
                    BgmBbCodeParagraph(
                        paragraph = block,
                        style = style,
                        color = color,
                        onUrlClick = onUrlClick,
                    )
                }
            }
        }
    }
}

/**
 * 引用卡片渲染
 */
@Composable
private fun BgmBbCodeQuote(
    quote: BbCodeBlock.Quote,
    style: TextStyle,
    color: Color,
    onUrlClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val barWidth = 3.dp.toPx()
                        drawRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, size.height),
                        )
                    }.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (quote.author != null) {
                Text(
                    text = "引用 @${quote.author} 说：",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BgmBbCodeParagraph(
                paragraph = remember(quote.content) { BgmBbCodeParser.parseParagraph(quote.content) },
                style = style.copy(fontSize = (style.fontSize.value * 0.9f).sp),
                color = color.copy(alpha = 0.85f),
                onUrlClick = onUrlClick,
            )
        }
    }
}

/**
 * 图片卡片渲染（支持 X/Twitter 风格的黑幕/隐藏图片遮罩与显示切换）
 */
@Composable
private fun BgmBbCodeImage(
    image: BbCodeBlock.Image,
    onUrlClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var isRevealed by rememberSaveable(image.url) { mutableStateOf(!image.isMasked) }
    val aspectRatio = image.aspectRatio
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .then(
                if (aspectRatio != null && aspectRatio > 0f) {
                    Modifier.aspectRatio(aspectRatio.coerceIn(0.25f, 4f))
                } else {
                    Modifier.heightIn(min = 140.dp, max = 260.dp)
                },
            ).then(
                if (!isRevealed) {
                    Modifier.heightIn(min = 150.dp)
                } else {
                    Modifier
                },
            ).clip(RoundedCornerShape(8.dp))
            .then(
                if (!isRevealed) {
                    Modifier.blur(radius = 32.dp)
                } else {
                    Modifier
                },
            )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        onClick = {
            if (isRevealed) {
                onUrlClick?.invoke(image.url)
            }
        },
        enabled = isRevealed,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = if (image.isMasked) "隐藏图片" else "评论图片",
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
            )

            // X 风格遮罩：未显示时覆盖深色磨砂遮罩与居中警告提示
            if (!isRevealed) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "隐藏内容",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "发布者已将此图片隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { isRevealed = true },
                            shape = CircleShape,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "显示",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else if (image.isMasked) {
                // 已展开状态：右上角显示精简的半透明重新遮挡/隐藏胶囊
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    onClick = { isRevealed = false },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "重新隐藏",
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "隐藏",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 富文本段落（含表情行内排版、黑幕揭晓交互及样式文本）
 */
@Composable
private fun BgmBbCodeParagraph(
    paragraph: BbCodeBlock.Paragraph,
    style: TextStyle,
    color: Color,
    onUrlClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val revealedMasks = remember { mutableStateMapOf<String, Boolean>() }

    val inlineContent =
        remember(paragraph.elements) {
            val map = mutableMapOf<String, InlineTextContent>()
            paragraph.elements.filterIsInstance<BbInlineElement.Sticker>().forEach { sticker ->
                val key = "sticker_${sticker.stickerId}_${sticker.hashCode()}"
                val size = if (sticker.isLarge) 34.sp else 18.sp
                val align =
                    if (sticker.isLarge) {
                        PlaceholderVerticalAlign.TextBottom
                    } else {
                        PlaceholderVerticalAlign.TextCenter
                    }
                map[key] =
                    InlineTextContent(
                        Placeholder(
                            width = size,
                            height = size,
                            placeholderVerticalAlign = align,
                        ),
                    ) {
                        AsyncImage(
                            model = sticker.url,
                            contentDescription = sticker.code,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 1.5.dp),
                        )
                    }
            }
            map
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface

    val annotatedString =
        remember(paragraph, revealedMasks.toMap(), primaryColor, onSurface) {
            buildAnnotatedString {
                paragraph.elements.forEach { element ->
                    when (element) {
                        is BbInlineElement.Plain -> {
                            append(element.text)
                        }
                        is BbInlineElement.Styled -> {
                            val textDecorations =
                                when {
                                    element.isUnderline && element.isStrikethrough ->
                                        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                                    element.isUnderline -> TextDecoration.Underline
                                    element.isStrikethrough -> TextDecoration.LineThrough
                                    else -> null
                                }
                            val isBgmLink = element.url != null && BgmUrlParser.parse(element.url) !is BgmLink.External
                            withStyle(
                                SpanStyle(
                                    fontWeight =
                                        when {
                                            element.isBold -> FontWeight.Bold
                                            isBgmLink -> FontWeight.SemiBold
                                            else -> null
                                        },
                                    fontStyle = if (element.isItalic) FontStyle.Italic else null,
                                    textDecoration = textDecorations,
                                    color = if (element.url != null) primaryColor else Color.Unspecified,
                                ),
                            ) {
                                if (element.url != null) {
                                    pushLink(
                                        LinkAnnotation.Url(
                                            url = element.url,
                                            linkInteractionListener =
                                                onUrlClick?.let { onClick ->
                                                    { onClick(element.url) }
                                                },
                                        ),
                                    )
                                    append(element.text)
                                    pop()
                                } else {
                                    append(element.text)
                                }
                            }
                        }
                        is BbInlineElement.Mask -> {
                            val isRevealed = revealedMasks[element.id] == true
                            val link =
                                LinkAnnotation.Clickable(
                                    tag = element.id,
                                    linkInteractionListener = {
                                        revealedMasks[element.id] = !isRevealed
                                    },
                                )
                            pushLink(link)
                            withStyle(
                                SpanStyle(
                                    background =
                                        if (isRevealed) {
                                            primaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            onSurface.copy(alpha = 0.85f)
                                        },
                                    color = if (isRevealed) onPrimaryContainer else Color.Transparent,
                                ),
                            ) {
                                append(element.text.ifEmpty { " " })
                            }
                            pop()
                        }
                        is BbInlineElement.Sticker -> {
                            val key = "sticker_${element.stickerId}_${element.hashCode()}"
                            appendInlineContent(id = key, alternateText = element.code)
                        }
                    }
                }
            }
        }

    val hasLargeStickers =
        remember(paragraph.elements) {
            paragraph.elements.any { it is BbInlineElement.Sticker && it.isLarge }
        }

    val textStyle =
        if (hasLargeStickers) {
            style.copy(
                lineHeight = TextUnit.Unspecified,
                lineHeightStyle = null,
            )
        } else {
            val fontSize = if (style.fontSize.isSpecified && style.fontSize.value > 0f) style.fontSize else 13.sp
            val calculatedLineHeight =
                if (style.lineHeight.isSpecified && style.lineHeight.value > 0f) {
                    maxOf(style.lineHeight.value, fontSize.value * 1.55f).sp
                } else {
                    (fontSize.value * 1.55f).sp
                }
            style.copy(
                lineHeight = calculatedLineHeight,
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
            )
        }

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        style = textStyle,
        color = color,
        modifier = modifier,
    )
}
