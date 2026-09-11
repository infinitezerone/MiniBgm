package com.infinitezerone.minibgm.feature.search

import androidx.compose.runtime.Immutable
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject

/**
 * 标签专题条目界面的单一不可变 UI 状态
 */
@Immutable
data class TagSubjectsUiState(
    val tag: String = "",
    val selectedType: Int = 0, // 0: 全部, 2: 动画, 1: 书籍, 4: 游戏, 3: 音乐
    val selectedSort: SearchSort = SearchSort.RANK,
    val viewMode: SearchViewMode = SearchViewMode.LIST,
    val subjects: List<Subject> = emptyList(),
    val userCollections: Map<Long, CollectionType> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val userMessage: String? = null,
)
