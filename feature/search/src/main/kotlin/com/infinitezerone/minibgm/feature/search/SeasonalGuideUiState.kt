package com.infinitezerone.minibgm.feature.search

import androidx.compose.runtime.Immutable
import com.infinitezerone.minibgm.core.model.Subject
import java.util.Locale

/**
 * 季节季度划分：1月冬、4月春、7月夏、10月秋
 */
enum class SeasonQuarter(
    val month: Int,
    val label: String,
    val displayLabel: String,
) {
    WINTER(1, "冬", "1月冬"),
    SPRING(4, "春", "4月春"),
    SUMMER(7, "夏", "7月夏"),
    AUTUMN(10, "秋", "10月秋"),
    ;

    fun getAirDateRange(year: Int): Pair<String, String> {
        val start = String.format(Locale.US, "%04d-%02d-01", year, month)
        val end =
            when (this) {
                WINTER -> String.format(Locale.US, "%04d-03-31", year)
                SPRING -> String.format(Locale.US, "%04d-06-30", year)
                SUMMER -> String.format(Locale.US, "%04d-09-30", year)
                AUTUMN -> String.format(Locale.US, "%04d-12-31", year)
            }
        return start to end
    }

    companion object {
        fun fromMonth(month: Int): SeasonQuarter =
            when (month) {
                in 1..3 -> WINTER
                in 4..6 -> SPRING
                in 7..9 -> SUMMER
                else -> AUTUMN
            }
    }
}

/**
 * 导视页视图形态。
 *
 * 与 `SearchViewMode` 同构（两态、操作条上一个图标按钮切换），差异只在默认值：
 * 导视的首要诉求是"一季有哪些番、几点在哪台播"，行式列表一屏约 6-7 条且带集数／电视台／题材，
 * 海报网格一屏只容 4 部、除封面外几乎没有信息，因此默认取 [LIST]。
 */
enum class SeasonalViewMode {
    /** 紧凑行式列表：封面 + 标题 + 集数 · 电视台 + 题材标签 */
    LIST,

    /** 2:3 海报展板网格 */
    POSTER,
}

/**
 * 导视条目播出形式分类筛选
 */
enum class SeasonCategoryFilter(
    val label: String,
) {
    ALL("全部"),
    TV("TV 动画"),
    WEB("网络独播"),
    MOVIE_OVA("剧场版 / OVA"),
}

private val WEB_WORD_REGEX = Regex("""\bWEB\b""")

/**
 * 判断条目是否符合给定的放送类型筛选条件
 */
fun matchesCategory(
    subject: Subject,
    category: SeasonCategoryFilter,
): Boolean {
    if (category == SeasonCategoryFilter.ALL) return true
    val tagNames = subject.tags.map { it.name.trim().uppercase() }
    val title = (subject.name + " " + subject.nameCn).uppercase()

    val isMovieOva =
        tagNames.any { it in listOf("剧场版", "OVA", "OAD", "电影", "MOVIE") } ||
            title.contains("剧场版") ||
            title.contains("OVA") ||
            title.contains("OAD")

    val isWeb =
        tagNames.any { it in listOf("WEB", "网络动画", "WEB动画", "网络独播") } ||
            title.contains("WEB动画") ||
            title.contains("网络动画") ||
            title.contains("网络独播") ||
            WEB_WORD_REGEX.containsMatchIn(title)

    val isTv = tagNames.any { it in listOf("TV", "TV动画", "电视动画") } || (!isWeb && !isMovieOva)

    return when (category) {
        SeasonCategoryFilter.ALL -> true
        SeasonCategoryFilter.TV -> isTv && !isWeb && !isMovieOva
        SeasonCategoryFilter.WEB -> isWeb && !isMovieOva
        SeasonCategoryFilter.MOVIE_OVA -> isMovieOva
    }
}

/**
 * 季度新番导视 UI 状态模型
 */
@Immutable
data class SeasonalGuideUiState(
    val selectedYear: Int = 2026,
    val selectedQuarter: SeasonQuarter = SeasonQuarter.WINTER,
    val currentYear: Int = 2026,
    val currentQuarter: SeasonQuarter = SeasonQuarter.WINTER,
    val selectedCategory: SeasonCategoryFilter = SeasonCategoryFilter.ALL,
    /** 视图形态；纯展示偏好，切换不需要重新取数 */
    val viewMode: SeasonalViewMode = SeasonalViewMode.LIST,
    val availableYears: List<Int> = emptyList(),
    /** 本季首播：Bangumi `air_date` 区间过滤的结果 */
    val subjects: List<Subject> = emptyList(),
    /**
     * 本季连载中：首播日不在本季、但本季确有播出事件的长期连载番。
     * 仅当季/未来季有数据（播出事件来自滚动快照），历史季恒为空。
     */
    val ongoingSubjects: List<Subject> = emptyList(),
    val wishedSubjectIds: Set<Long> = emptySet(),
    val doingSubjectIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingOngoing: Boolean = false,
    /** 服务端游标：下一页的 offset。不能用 subjects.size 代替——去重会丢弃重复条目导致错位 */
    val pageOffset: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    val userMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
) {
    val filteredSubjects: List<Subject>
        get() =
            if (selectedCategory == SeasonCategoryFilter.ALL) {
                subjects
            } else {
                subjects.filter { matchesCategory(it, selectedCategory) }
            }

    val filteredOngoingSubjects: List<Subject>
        get() =
            if (selectedCategory == SeasonCategoryFilter.ALL) {
                ongoingSubjects
            } else {
                ongoingSubjects.filter { matchesCategory(it, selectedCategory) }
            }
}
