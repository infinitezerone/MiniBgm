package com.infinitezerone.minibgm.core.network.model

import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.UserCollection
import kotlinx.serialization.Serializable

@Serializable
data class CalendarDayResponse(
    val weekday: CalendarWeekday,
    val items: List<Subject>,
)

@Serializable
data class CalendarWeekday(
    val en: String,
    val cn: String,
    val ja: String,
    val id: Int,
)

@Serializable
data class PageResponse<T>(
    val total: Int = 0,
    val limit: Int = 30,
    val offset: Int = 0,
    val data: List<T> = emptyList(),
)

typealias EpisodePageResponse = PageResponse<Episode>
typealias UserCollectionPageResponse = PageResponse<UserCollection>
typealias SubjectPageResponse = PageResponse<Subject>

@Serializable
data class SearchSubjectResponse(
    val results: Int = 0,
    val list: List<Subject> = emptyList(),
)
