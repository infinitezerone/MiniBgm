package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 搜索结果模型
 * @param total 搜索匹配到的全量条目总数
 * @param list 当前分页加载的条目列表
 */
@Serializable
data class SearchResult(
    val total: Int = 0,
    val list: List<Subject> = emptyList(),
)
