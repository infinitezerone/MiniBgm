package com.infinitezerone.minibgm.feature.assistant

/** 围栏代码块（```json … ``` 或裸 ``` … ```），连围栏一起整块匹配 */
private val FENCED_BLOCK = Regex("```[A-Za-z0-9_+-]*\\s*\\n([\\s\\S]*?)```")

/**
 * 提案 JSON 已经在消息下方渲染成操作卡片了，正文里再贴一遍就是重复——把这类围栏块去掉。
 *
 * 只在该消息**确实带了操作卡片**时动手：那种情况下正文里的 JSON 与卡片是同一份，
 * 删掉不丢信息。没有卡片时一律原样返回（模型可能正在贴用户要的规则 JSON，那本身就是答案）。
 *
 * 三种输入都要处理：整段就是 JSON、围栏 JSON + 自然语言、只有围栏 JSON。
 * 前两种与第三种的表现不同（前者剥完为空，后者要留下自然语言），所以返回值交给调用方判空，
 * 由它决定是显示原文还是换成一句提示。
 */
internal fun stripDuplicatedJsonBlock(
    content: String,
    hasActionCards: Boolean,
): String {
    if (!hasActionCards) return content

    val trimmed = content.trim()
    // 整段就是一份 JSON：没有任何可留的自然语言
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return ""

    return FENCED_BLOCK
        .replace(content) { match ->
            val body = match.groupValues[1].trim()
            if (body.startsWith("{") || body.startsWith("[")) "" else match.value
        }.trim()
}

/**
 * 消息内容解析结果：分离思维链思考内容与实际主正文。
 */
internal data class ParsedMessageContent(
    val thinking: String? = null,
    val mainContent: String,
)

private val THINKING_TAG_REGEX = Regex("""(?s)<think>(.*?)(?:</think>|$)""")

/**
 * 从模型回复中提取 `<think>...</think>` 思维链内容。
 * 针对 DeepSeek-R1、Qwen-QwQ 等推理模型，分离思考过程与最终输出。
 */
internal fun parseThinkingProcess(rawContent: String): ParsedMessageContent {
    val match = THINKING_TAG_REGEX.find(rawContent) ?: return ParsedMessageContent(null, rawContent)
    val thinkingText = match.groupValues[1].trim()
    val mainText = rawContent.replace(match.value, "").trim()
    return ParsedMessageContent(
        thinking = thinkingText.takeIf { it.isNotBlank() },
        mainContent = mainText,
    )
}
