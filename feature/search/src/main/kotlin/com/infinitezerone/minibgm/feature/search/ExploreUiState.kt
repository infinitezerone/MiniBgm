package com.infinitezerone.minibgm.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.infinitezerone.minibgm.core.model.Subject
import java.time.LocalDate

/** 季度/年份/年代时间筛选选项 */
@Immutable
data class SeasonOption(
    val id: String,
    @StringRes val labelRes: Int,
    val labelArgs: List<Any> = emptyList(),
    val airDateFilter: List<String>? = null,
    val category: TimeCategory = TimeCategory.YEAR,
)

enum class TimeCategory(
    @StringRes val labelRes: Int,
) {
    YEAR(R.string.feature_search_time_category_year),
    ALL(R.string.feature_search_time_category_all),
}

/** 探索分类定义：动画 (2)、书籍 (1)、游戏 (4)、音乐 (3)、全部 (null) */
enum class ExploreCategory(
    val type: Int?,
    @StringRes val labelRes: Int,
) {
    ANIME(2, R.string.feature_search_category_anime),
    BOOK(1, R.string.feature_search_category_book),
    GAME(4, R.string.feature_search_category_game),
    MUSIC(3, R.string.feature_search_category_music),
    ALL(null, R.string.feature_search_category_all),
}

/** 排序方式定义 */
enum class ExploreSort(
    val sortKey: String,
    @StringRes val labelRes: Int,
) {
    HEAT("heat", R.string.feature_search_explore_sort_heat),
    SCORE("score", R.string.feature_search_explore_sort_score),
    RANK("rank", R.string.feature_search_explore_sort_rank),
}

/** 心境/场景预设筛选（小红书/盲盒安利流） */
enum class ExploreMood(
    @StringRes val labelRes: Int,
    val tags: List<String> = emptyList(),
    val sort: ExploreSort,
) {
    MASTERPIECE(R.string.feature_search_mood_masterpiece, emptyList(), ExploreSort.RANK),
    HOT(R.string.feature_search_mood_hot, emptyList(), ExploreSort.HEAT),
    HEALING(R.string.feature_search_mood_healing, listOf("治愈", "日常"), ExploreSort.RANK),
    SHONEN(R.string.feature_search_mood_shonen, listOf("热血", "战斗"), ExploreSort.HEAT),
    SUSPENSE(R.string.feature_search_mood_suspense, listOf("悬疑", "推理"), ExploreSort.RANK),
    TEARS(R.string.feature_search_mood_tears, listOf("催泪", "感动"), ExploreSort.RANK),
    ROMANCE(R.string.feature_search_mood_romance, listOf("恋爱", "纯爱"), ExploreSort.RANK),
    FANTASY(R.string.feature_search_mood_fantasy, listOf("奇幻", "冒险"), ExploreSort.HEAT),
    BLIND_BOX(R.string.feature_search_mood_blind_box, emptyList(), ExploreSort.RANK),
}

/** 标签维度分组 */
@Immutable
data class TagGroup(
    @StringRes val nameRes: Int,
    val tags: List<String>,
)

val TAG_GROUPS =
    listOf(
        TagGroup(
            nameRes = R.string.feature_search_tag_group_genre,
            tags =
                listOf(
                    "奇幻",
                    "战斗",
                    "热血",
                    "恋爱",
                    "日常",
                    "科幻",
                    "悬疑",
                    "治愈",
                    "搞笑",
                    "校园",
                    "异世界",
                    "冒险",
                    "百合",
                    "运动",
                    "机战",
                    "魔法",
                    "美食",
                    "职场",
                    "历史",
                    "穿越",
                    "超能力",
                    "偶像",
                    "音乐",
                    "竞技",
                    "黑暗",
                    "推理",
                    "后宫",
                    "纯爱",
                ),
        ),
        TagGroup(
            nameRes = R.string.feature_search_tag_group_studio,
            tags =
                listOf(
                    "京阿尼",
                    "骨头社",
                    "MAPPA",
                    "飞碟社",
                    "霸权社",
                    "A-1 Pictures",
                    "CloverWorks",
                    "SHAFT",
                    "疯房子",
                    "Trigger",
                    "P.A.WORKS",
                    "Sunrise",
                    "Production I.G",
                    "动画工房",
                    "吉卜力",
                    "新海诚",
                    "宫崎骏",
                    "庵野秀明",
                    "汤浅政明",
                    "今石洋之",
                    "新房昭之",
                ),
        ),
        TagGroup(
            nameRes = R.string.feature_search_tag_group_form,
            tags =
                listOf(
                    "TV",
                    "剧场版",
                    "OVA",
                    "Web",
                    "原创",
                    "漫画改",
                    "轻小说改",
                    "游戏改",
                    "女性向",
                    "少年向",
                    "青年向",
                ),
        ),
    )

/** 生成完整的年代与年份列表 */
fun generateFullTimeOptions(
    nowYear: Int,
    nowMonth: Int = 1,
): List<SeasonOption> {
    val options = mutableListOf<SeasonOption>()

    // 1. 全部时间 (默认)
    options.add(
        SeasonOption(
            id = "all",
            labelRes = R.string.feature_search_time_category_all,
            airDateFilter = null,
            category = TimeCategory.ALL,
        ),
    )

    // 2. 年份维度（近 6 年单年）
    for (yearOffset in 0..5) {
        val y = nowYear - yearOffset
        options.add(
            SeasonOption(
                id = "$y-full",
                labelRes = R.string.feature_search_season_year_format,
                labelArgs = listOf(y),
                airDateFilter = listOf(">=$y-01-01", "<${y + 1}-01-01"),
                category = TimeCategory.YEAR,
            ),
        )
    }

    // 3. 经典年代维度
    options.add(
        SeasonOption(
            id = "2010s",
            labelRes = R.string.feature_search_season_2010s,
            airDateFilter = listOf(">=2010-01-01", "<2020-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "2000s",
            labelRes = R.string.feature_search_season_2000s,
            airDateFilter = listOf(">=2000-01-01", "<2010-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "1990s",
            labelRes = R.string.feature_search_season_1990s,
            airDateFilter = listOf(">=1990-01-01", "<2000-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "1980s-before",
            labelRes = R.string.feature_search_season_before_1990,
            airDateFilter = listOf("<1990-01-01"),
            category = TimeCategory.YEAR,
        ),
    )

    return options
}

fun getCurrentSeasonList(): List<SeasonOption> {
    val now =
        try {
            LocalDate.now()
        } catch (_: Exception) {
            LocalDate.of(2026, 9, 1)
        }
    return generateFullTimeOptions(now.year, now.monthValue)
}

val DEFAULT_SEASONS = getCurrentSeasonList()
val ALL_TIME_SEASON =
    DEFAULT_SEASONS.firstOrNull { it.category == TimeCategory.ALL }
        ?: SeasonOption(
            id = "all",
            labelRes = R.string.feature_search_time_category_all,
            airDateFilter = null,
            category = TimeCategory.ALL,
        )
val CURRENT_SEASON = ALL_TIME_SEASON

/** 探索首页的横滑行定义（B站/AniList 式客观维度榜单：时间 × 热度，题材留给筛选器） */
enum class ExploreRow(
    @StringRes val labelRes: Int,
) {
    HOT(R.string.feature_search_row_hot),
    MASTERPIECE(R.string.feature_search_row_masterpiece),
    UPCOMING(R.string.feature_search_row_upcoming),
}

/** 单个横滑行的数据状态；失败即空列表（行级 fail-open，不阻塞其他区块） */
@Immutable
data class ExploreRowState(
    val subjects: List<Subject> = emptyList(),
    val isLoading: Boolean = false,
)

/** 探索页浏览模式：ROWS = 榜单行区块（默认）；FULL_LIST = 心境胶囊 + 全量瀑布流 */
enum class ExploreBrowseMode {
    ROWS,
    FULL_LIST,
}

/** 探索发现界面的单一不可变 UI 状态 */
@Immutable
data class ExploreUiState(
    val selectedSeason: SeasonOption = ALL_TIME_SEASON,
    val selectedCategory: ExploreCategory = ExploreCategory.ANIME,
    val selectedTags: Set<String> = emptySet(),
    val customTagInput: String = "",
    val selectedSort: ExploreSort = ExploreSort.RANK,
    val selectedMood: ExploreMood? = ExploreMood.MASTERPIECE,
    val browseMode: ExploreBrowseMode = ExploreBrowseMode.ROWS,
    val rowStates: Map<ExploreRow, ExploreRowState> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val pageOffset: Int = 0,
    val isLoggedIn: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val subjects: List<Subject> = emptyList(),
    val wishedSubjectIds: Set<Long> = emptySet(),
    val error: String? = null,
    val userMessage: String? = null,
    val customFilterTags: List<String> = emptyList(),
)

/**
 * 是否存在生效中的用户自定义筛选条件。
 *
 * 判据：只有当用户脱离了顶部的预设频道（selectedMood == null），且设置了非默认的
 * 时间、标签、分类或排序时，才视为自定义筛选生效。
 *
 * 当用户处于预设频道（selectedMood != null）时，所有条件均由预设统领，
 * 不属于用户自选，不应出现 ActiveFilterPillRow 或在高级筛选图标上亮起 Badge，
 * 避免在切换预设时突然弹出“清除全部”等令人困惑的 UI。
 */
val ExploreUiState.isCustomFilterActive: Boolean
    get() =
        selectedMood == null &&
            (
                selectedSeason != ALL_TIME_SEASON ||
                    selectedTags.isNotEmpty() ||
                    selectedCategory != ExploreCategory.ANIME ||
                    selectedSort != ExploreSort.RANK
            )

/** 生成已选自定义筛选条件的紧凑单行摘要文本 */
@Composable
fun ExploreUiState.customFilterSummary(): String =
    customFilterSummary(
        seasonLabel =
            if (selectedSeason != ALL_TIME_SEASON) {
                stringResource(selectedSeason.labelRes, *selectedSeason.labelArgs.toTypedArray())
            } else {
                null
            },
        categoryLabel =
            if (selectedCategory != ExploreCategory.ANIME) {
                stringResource(selectedCategory.labelRes)
            } else {
                null
            },
        sortLabel = if (selectedSort != ExploreSort.RANK) stringResource(selectedSort.labelRes) else null,
        emptyLabel = stringResource(R.string.feature_search_custom_filter_summary_none),
    )

/**
 * 纯函数版本：各类标签文案由调用方解析后传入，便于在 Composable 之外复用与单元测试。
 */
internal fun ExploreUiState.customFilterSummary(
    seasonLabel: String?,
    categoryLabel: String?,
    sortLabel: String?,
    emptyLabel: String,
): String {
    val parts = mutableListOf<String>()
    seasonLabel?.let(parts::add)
    categoryLabel?.let(parts::add)
    selectedTags.forEach { tag ->
        parts.add("#$tag")
    }
    sortLabel?.let(parts::add)
    val count = parts.size
    return if (parts.isEmpty()) {
        emptyLabel
    } else {
        "${parts.joinToString(" · ")} ($count)"
    }
}
