package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.PendingAction

/**
 * 从智能体推理回复或工具调用输出中解析 [PendingAction] 提案的通用解析器。
 * 支持完整 [ActionProposal] JSON、多态 [PendingAction] JSON 以及 Markdown 内嵌 JSON 结构。
 */
object PendingActionParser {
    private val json = aiJson

    fun extractPendingActions(text: String): List<PendingAction> {
        if (text.isBlank()) return emptyList()
        val trimmed = text.trim()

        tryParseAction(trimmed)?.let { return listOf(it) }

        val fromCodeBlocks = extractFromCodeBlocks(text)
        if (fromCodeBlocks.isNotEmpty()) {
            return fromCodeBlocks.distinctBy { it.actionId }
        }

        val candidates = scanJsonBlocks(text)
        val actions = mutableListOf<PendingAction>()
        for (candidate in candidates) {
            val parsed = tryParseAction(candidate)
            if (parsed != null) {
                actions.add(parsed)
            } else {
                val nextBrace = candidate.indexOf('{', 1)
                if (nextBrace != -1) {
                    actions.addAll(extractPendingActions(candidate.substring(nextBrace)))
                }
            }
        }
        return actions.distinctBy { it.actionId }
    }

    private fun tryParseAction(candidate: String): PendingAction? {
        try {
            return json.decodeFromString<ActionProposal>(candidate).action
        } catch (_: Exception) {
        }
        try {
            return json.decodeFromString<PendingAction>(candidate)
        } catch (_: Exception) {
        }
        return null
    }

    private fun extractFromCodeBlocks(text: String): List<PendingAction> {
        val codeBlockRegex = Regex("""```(?:json)?\s*([\s\S]*?)```""")
        val results = mutableListOf<PendingAction>()
        for (match in codeBlockRegex.findAll(text)) {
            val content = match.groupValues[1].trim()
            results.addAll(extractPendingActions(content))
        }
        return results
    }

    private fun scanJsonBlocks(text: String): List<String> {
        val candidates = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var isEscaped = false

        for (i in text.indices) {
            val c = text[i]
            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                } else if (c == '\\') {
                    isEscaped = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) startIndex = i
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && startIndex != -1) {
                            candidates.add(text.substring(startIndex, i + 1))
                            startIndex = -1
                        }
                    }
                }
            }
        }
        return candidates
    }
}
