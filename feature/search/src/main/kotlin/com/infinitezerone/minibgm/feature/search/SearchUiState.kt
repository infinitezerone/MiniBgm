package com.infinitezerone.minibgm.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject

/** 搜索分类定义：全部 (0)、动画 (2)、书籍 (1)、游戏 (4)、音乐 (3) */
enum class SearchCategory(
    val type: Int,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    ALL(0, R.string.feature_search_category_all, BgmIcons.Assistant),
    ANIME(2, R.string.feature_search_category_anime, BgmIcons.Tv),
    BOOK(1, R.string.feature_search_category_book, BgmIcons.Book),
    GAME(4, R.string.feature_search_category_game, BgmIcons.Game),
    MUSIC(3, R.string.feature_search_category_music, BgmIcons.Music),
    ;

    companion object {
        fun fromType(type: Int): SearchCategory = entries.firstOrNull { it.type == type } ?: ALL
    }
}

/** 搜索结果排序维度（直接对接 Bangumi 官方 v0 全量服务端排序规则） */
enum class SearchSort(
    @StringRes val labelRes: Int,
    val serverSort: String,
) {
    MATCH(R.string.feature_search_search_sort_match, "match"),
    HEAT(R.string.feature_search_search_sort_heat, "heat"),
    SCORE(R.string.feature_search_search_sort_score, "score"),
    RANK(R.string.feature_search_search_sort_rank, "rank"),
}

/** 搜索视图模式 */
enum class SearchViewMode {
    LIST, // 详细大卡列表
    GRID, // 3列高密度海报网格
}

/**
 * 搜索界面的单一不可变 UI 状态。
 *
 * 一次性提示（打卡结果 / 失败原因）不在状态里：走 [SearchViewModel.userMessage] 的
 * `Channel(BUFFERED) + receiveAsFlow()`，与状态流隔离、消费即消失。
 */
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
    /** 本地别名索引命中（06-A）：网络失败时为离线降级结果，成功时为接口未覆盖的别名兜底 */
    val localMatches: List<com.infinitezerone.minibgm.core.model.LocalSubjectMatch> = emptyList(),
    /** 弱网降级软提示（07-A）：非 null 时展示为可重试的提示条而非全屏错误 */
    val offlineNotice: String? = null,
    val userCollections: Map<Long, CollectionType> = emptyMap(),
    val showLoginPromptDialog: Boolean = false,
    val error: String? = null,
    val searchHistory: List<String> = emptyList(),
)
