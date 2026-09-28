package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bangumi API v0 高级搜索请求体 (POST /v0/search/subjects)
 */
@Serializable
data class SearchSubjectsRequest(
    val keyword: String? = null,
    val sort: String? = null,
    val filter: SearchFilter? = null,
)

/**
 * Bangumi API v0 高级搜索多维过滤条件
 */
@Serializable
data class SearchFilter(
    val type: List<Int>? = null,
    val tag: List<String>? = null,
    @SerialName("air_date") val airDate: List<String>? = null,
    val rating: List<String>? = null,
    val rank: List<String>? = null,
    val nsfw: Boolean? = null,
    /**
     * 官方元标签过滤，多值为 **AND** 语义。
     *
     * 产地（日本 / 中国 / 欧美）与放送形式（TV / WEB / 剧场版）都在这个取值域里，
     * 导视页的两级筛选因此可以整体下推服务端——只有服务端过滤过，总数与分页才是准的。
     * 注意它没有排除语法，「剧场版或 OVA」这类"或"关系表达不了。
     */
    @SerialName("meta_tags") val metaTags: List<String>? = null,
)
