package com.infinitezerone.minibgm.core.data.search

import com.infinitezerone.minibgm.core.model.LocalSubjectMatch

/** 归一化查询结果的命中等级：精确 > 前缀 > 包含 */
private const val RANK_EXACT = 0
private const val RANK_PREFIX = 1
private const val RANK_CONTAINS = 2

/**
 * 本地别名词典索引：对 Room 缓存的排期条目（bangumi-data 已同步窗口内）做容错搜索。
 *
 * 归一化规则：小写化、全角转半角、去空白与常见间隔符（·・_- 等），
 * 使「葬送的芙莉莲」与「葬送的 芙莉莲」等变体可互相命中。
 * 纯 Kotlin，可单测。
 */
class SearchAliasIndex(
    entries: List<Entry>,
) {
    data class Entry(
        val subjectId: Long,
        val title: String,
        val titleCn: String,
    )

    private val entries: List<Pair<Entry, List<String>>>

    init {
        this.entries =
            entries
                .filter { it.subjectId > 0 }
                .map { entry ->
                    entry to
                        buildList {
                            entry.title
                                .normalize()
                                .takeIf { it.isNotEmpty() }
                                ?.let { add(it) }
                            entry.titleCn
                                .normalize()
                                .takeIf { it.isNotEmpty() && !contains(it) }
                                ?.let { add(it) }
                        }
                }.filter { it.second.isNotEmpty() }
    }

    /** 按命中等级与原始顺序返回（最多 [limit] 条）；无命中返回空列表 */
    fun search(
        query: String,
        limit: Int = 8,
    ): List<LocalSubjectMatch> {
        val normalizedQuery = query.normalize()
        if (normalizedQuery.isEmpty()) return emptyList()
        return entries
            .mapIndexedNotNull { index, (entry, aliases) ->
                val rank = aliases.minOf { alias -> matchRank(alias, normalizedQuery) }
                when (rank) {
                    RANK_EXACT -> index to RANK_EXACT
                    RANK_PREFIX -> index to RANK_PREFIX
                    RANK_CONTAINS -> index to RANK_CONTAINS
                    else -> null
                }?.let { (i, r) -> Triple(i, r, entry) }
            }.sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .map { (_, _, entry) ->
                LocalSubjectMatch(
                    bgmId = entry.subjectId,
                    title = entry.title,
                    titleCn = entry.titleCn,
                )
            }
    }

    /** [alias] 对归一化查询的命中等级；未命中返回 3 */
    private fun matchRank(
        alias: String,
        normalizedQuery: String,
    ): Int =
        when {
            alias == normalizedQuery -> RANK_EXACT
            alias.startsWith(normalizedQuery) -> RANK_PREFIX
            alias.contains(normalizedQuery) -> RANK_CONTAINS
            else -> 3
        }
}

/** 小写化 + 全角转半角 + 去空白与常见间隔符 */
internal fun String.normalize(): String =
    buildString {
        for (raw in this@normalize) {
            val c =
                if (raw.code in 0xFF01..0xFF5E) {
                    (raw.code - 0xFEE0).toChar()
                } else {
                    raw
                }
            when {
                c.isWhitespace() -> Unit
                c == '·' || c == '・' || c == '-' || c == '－' || c == '_' || c == '～' || c == '~' -> Unit
                else -> append(c.lowercaseChar())
            }
        }
    }
