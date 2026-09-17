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
)
