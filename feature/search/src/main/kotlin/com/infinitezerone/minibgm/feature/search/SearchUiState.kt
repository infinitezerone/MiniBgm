package com.infinitezerone.minibgm.feature.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject

/** 搜索分类定义：全部 (0)、动画 (2)、书籍 (1)、游戏 (4)、音乐 (3) */
enum class SearchCategory(
    val type: Int,
    val label: String,
    val icon: ImageVector,
) {
    ALL(0, "全部", Icons.Filled.AutoAwesome),
    ANIME(2, "动画", Icons.Filled.Tv),
    BOOK(1, "书籍", Icons.Filled.Book),
    GAME(4, "游戏", Icons.Filled.SportsEsports),
    MUSIC(3, "音乐", Icons.Filled.MusicNote),
    ;

    companion object {
        fun fromType(type: Int): SearchCategory = entries.firstOrNull { it.type == type } ?: ALL
    }
}

/** 搜索结果排序维度（直接对接 Bangumi 官方 v0 全量服务端排序规则） */
enum class SearchSort(
    val label: String,
    val serverSort: String,
) {
    MATCH("综合匹配", "match"),
    HEAT("热门收藏 🔥", "heat"),
    SCORE("高分优先 ⭐", "score"),
    RANK("排名靠前 🏆", "rank"),
}

/** 搜索视图模式 */
enum class SearchViewMode {
    LIST, // 详细大卡列表
    GRID, // 3列高密度海报网格
}

/** 搜索界面的单一不可变 UI 状态 */
@Immutable
data class SearchUiState(
    val query: String = "",
    val hasSearched: Boolean = false,
    val selectedType: Int = 0,
    val selectedSort: SearchSort = SearchSort.MATCH,
    val viewMode: SearchViewMode = SearchViewMode.LIST,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
    val results: List<Subject> = emptyList(),
    val userCollections: Map<Long, CollectionType> = emptyMap(),
    val showLoginPromptDialog: Boolean = false,
    val userMessage: String? = null,
    val error: String? = null,
    val searchHistory: List<String> = emptyList(),
)
