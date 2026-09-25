package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 公网搜索结果条目。
 *
 * @property title 网页标题
 * @property url 网页直达地址
 * @property snippet 网页内容摘要
 */
@Serializable
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)
