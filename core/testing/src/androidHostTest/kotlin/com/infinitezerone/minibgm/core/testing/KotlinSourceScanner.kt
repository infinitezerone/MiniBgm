package com.infinitezerone.minibgm.core.testing

import java.io.File

/**
 * 结构化 Kotlin 源码扫描与脱敏工具。
 *
 * 彻底消除基于纯文本行匹配的架构测试固有缺陷：
 * 1. 自动过滤单行注释、多行块注释（支持嵌套）与 KDoc，100% 保留换行与原行号；
 * 2. 自动屏蔽普通字符串（"..."）、多行原生字符串（"""..."""）与字符常量（'...'），防止日志与文案误伤；
 * 3. 严格保持脱敏后字符串长度与字符索引不变，精确定位违规代码所在的行号与内容；
 * 4. 结构化提取 import 语句、排除私有声明的可变状态暴露等。
 */
class KotlinSourceScanner private constructor(
    val file: File,
    val rawContent: String,
    val sanitizedContent: String,
) {
    val rawLines: List<String> by lazy { rawContent.lines() }
    val sanitizedLines: List<String> by lazy { sanitizedContent.lines() }

    /** 解析所有有效 import 语句及其在原文件的行号（1-indexed，已过滤注释与字符串） */
    val imports: List<ImportStatement> by lazy {
        val list = mutableListOf<ImportStatement>()
        val importRegex = Regex("""\bimport\s+([a-zA-Z0-9_.*]+)""")
        for (match in importRegex.findAll(sanitizedContent)) {
            val lineNumber = getLineNumber(match.range.first)
            list.add(ImportStatement(match.groupValues[1], lineNumber, match.range.first))
        }
        list
    }

    /** 计算字符索引对应的原文件行号（1-indexed） */
    fun getLineNumber(charIndex: Int): Int {
        var line = 1
        val limit = minOf(charIndex, rawContent.length)
        for (i in 0 until limit) {
            if (rawContent[i] == '\n') line++
        }
        return line
    }

    /** 获取指定行号对应的原始文本行（1-indexed） */
    fun getRawLine(lineNumber: Int): String = if (lineNumber in 1..rawLines.size) rawLines[lineNumber - 1].trim() else ""

    /** 查找代码中的模式匹配（已排除注释与字符串），返回违规列表（含行号与原始行内容） */
    fun findMatches(pattern: Regex): List<SourceMatch> =
        pattern
            .findAll(sanitizedContent)
            .map { match ->
                val line = getLineNumber(match.range.first)
                SourceMatch(match.value, line, getRawLine(line))
            }.toList()

    /** 检查是否包含指定代码标记（已排除注释与字符串） */
    fun containsToken(token: String): List<SourceMatch> {
        val matches = mutableListOf<SourceMatch>()
        var index = sanitizedContent.indexOf(token)
        while (index >= 0) {
            val line = getLineNumber(index)
            matches.add(SourceMatch(token, line, getRawLine(line)))
            index = sanitizedContent.indexOf(token, index + token.length)
        }
        return matches
    }

    /**
     * 检查 ViewModel 中是否对外暴露了 MutableStateFlow（非 private 属性）。
     * 会自动向后向前解析修饰符，跨行声明与复杂泛型也能准确识别，同时彻底免疫注释与字符串中的干扰。
     */
    fun findExposedMutableStateFlows(): List<SourceMatch> {
        val violations = mutableListOf<SourceMatch>()
        val propRegex = Regex("""(?:^|[\n;{}])\s*([^\n;{}]*?)\b(val|var)\s+(\w+)""")
        val matches = propRegex.findAll(sanitizedContent).toList()

        for (i in matches.indices) {
            val match = matches[i]
            val prefix = match.groupValues[1].split(Regex("""\s+"""))
            val isPrivate = prefix.contains("private")
            val propName = match.groupValues[3]

            val startPos = match.range.first
            val endPos = if (i + 1 < matches.size) matches[i + 1].range.first else sanitizedContent.length
            val propBody = sanitizedContent.substring(startPos, endPos)

            if (!isPrivate && propBody.contains("MutableStateFlow")) {
                val nameOffset = match.value.indexOf(propName).coerceAtLeast(0)
                val line = getLineNumber(match.range.first + nameOffset)
                violations.add(SourceMatch(propName, line, getRawLine(line)))
            }
        }

        return violations
    }

    companion object {
        fun fromFile(file: File): KotlinSourceScanner {
            val raw = file.readText()
            val sanitized = sanitize(raw)
            return KotlinSourceScanner(file, raw, sanitized)
        }

        fun fromContent(
            content: String,
            dummyFile: File = File("Dummy.kt"),
        ): KotlinSourceScanner {
            val sanitized = sanitize(content)
            return KotlinSourceScanner(dummyFile, content, sanitized)
        }

        /**
         * 状态机：将注释与字符串字符替换为空格，严格保留换行符。
         */
        internal fun sanitize(code: String): String {
            val sb = StringBuilder(code.length)
            var i = 0
            val len = code.length

            while (i < len) {
                // 1. 检查多行原始字符串 """
                if (i + 2 < len && code[i] == '"' && code[i + 1] == '"' && code[i + 2] == '"') {
                    sb.append("   ")
                    i += 3
                    while (i < len) {
                        if (i + 2 < len && code[i] == '"' && code[i + 1] == '"' && code[i + 2] == '"') {
                            sb.append("   ")
                            i += 3
                            break
                        }
                        if (code[i] == '\n') sb.append('\n') else sb.append(' ')
                        i++
                    }
                    continue
                }

                // 2. 检查普通单行字符串 "..."
                if (code[i] == '"') {
                    sb.append(' ')
                    i++
                    while (i < len && code[i] != '"' && code[i] != '\n') {
                        if (code[i] == '\\' && i + 1 < len) {
                            sb.append("  ")
                            i += 2
                        } else {
                            sb.append(' ')
                            i++
                        }
                    }
                    if (i < len && code[i] == '"') {
                        sb.append(' ')
                        i++
                    }
                    continue
                }

                // 3. 检查字符常量 '...'
                if (code[i] == '\'') {
                    sb.append(' ')
                    i++
                    while (i < len && code[i] != '\'' && code[i] != '\n') {
                        if (code[i] == '\\' && i + 1 < len) {
                            sb.append("  ")
                            i += 2
                        } else {
                            sb.append(' ')
                            i++
                        }
                    }
                    if (i < len && code[i] == '\'') {
                        sb.append(' ')
                        i++
                    }
                    continue
                }

                // 4. 检查单行注释 //
                if (i + 1 < len && code[i] == '/' && code[i + 1] == '/') {
                    while (i < len && code[i] != '\n') {
                        sb.append(' ')
                        i++
                    }
                    continue
                }

                // 5. 检查块注释 /* ... */（支持嵌套注释）
                if (i + 1 < len && code[i] == '/' && code[i + 1] == '*') {
                    var depth = 1
                    sb.append("  ")
                    i += 2
                    while (i < len && depth > 0) {
                        if (i + 1 < len && code[i] == '/' && code[i + 1] == '*') {
                            depth++
                            sb.append("  ")
                            i += 2
                        } else if (i + 1 < len && code[i] == '*' && code[i + 1] == '/') {
                            depth--
                            sb.append("  ")
                            i += 2
                        } else {
                            if (code[i] == '\n') sb.append('\n') else sb.append(' ')
                            i++
                        }
                    }
                    continue
                }

                // 正常代码字符
                sb.append(code[i])
                i++
            }

            return sb.toString()
        }
    }
}

data class ImportStatement(
    val path: String,
    val lineNumber: Int,
    val charIndex: Int,
)

data class SourceMatch(
    val token: String,
    val lineNumber: Int,
    val lineContent: String,
)
