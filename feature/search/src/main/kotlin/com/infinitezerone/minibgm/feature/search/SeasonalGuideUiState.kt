package com.infinitezerone.minibgm.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.infinitezerone.minibgm.core.model.Subject
import java.time.LocalDate
import java.util.Locale

/**
 * 季节季度划分（业界クール口径）。
 *
 * 业界按「クール」分季：以 1/4/7/10 月开播为界的三个月档期。关键在于一部番归属哪一季看它的
 * **放送档期**，而不是首播日的日历月——实际首播日普遍落在档期开始前后约 10 天的模糊带里：
 * 秋番可以从 9 月下旬开播（葬送的芙莉莲 2023-09-29 是公认的 2023 秋番），夏番可以从 6 月下旬开播。
 *
 * 因此季界取**每月 21 日**，而不是日历月末：用日历月末会把 9 月下旬开播的秋番判成夏番，
 * 把 6 月下旬开播的夏番判成春番。季名仍沿用业界叫法，按档期首月命名（冬 = 1月クール … 秋 = 10月クール）。
 */
enum class SeasonQuarter(
    val month: Int,
    @StringRes val labelRes: Int,
    @StringRes val displayLabelRes: Int,
) {
    WINTER(
        1,
        R.string.feature_search_quarter_winter,
        R.string.feature_search_quarter_winter_display,
    ),
    SPRING(
        4,
        R.string.feature_search_quarter_spring,
        R.string.feature_search_quarter_spring_display,
    ),
    SUMMER(
        7,
        R.string.feature_search_quarter_summer,
        R.string.feature_search_quarter_summer_display,
    ),
    AUTUMN(
        10,
        R.string.feature_search_quarter_autumn,
        R.string.feature_search_quarter_autumn_display,
    ),
    ;

    /**
     * 本季的首播窗口 `[起, 止]`（含两端）。
     *
     * 取「档期首月的前一月 21 日」到「档期末月 20 日」，因此**冬季窗口跨年**：
     * 冬 2026 = 2025-12-21 ~ 2026-03-20。四档恰好无缝覆盖全年，不重不漏。
     */
    fun getAirDateRange(year: Int): Pair<String, String> {
        val startMonth = if (month == 1) 12 else month - 1
        val startYear = if (month == 1) year - 1 else year
        val start = String.format(Locale.US, "%04d-%02d-21", startYear, startMonth)
        val end = String.format(Locale.US, "%04d-%02d-20", year, month + 2)
        return start to end
    }

    /** 首播窗口的中文简写。档期卡片拿它当副标——「10月秋」的列表里出现 9 月的日期时不至于让人困惑 */
    @get:StringRes
    val airDateLabelRes: Int
        get() =
            when (this) {
                WINTER -> R.string.feature_search_quarter_winter_air
                SPRING -> R.string.feature_search_quarter_spring_air
                SUMMER -> R.string.feature_search_quarter_summer_air
                AUTUMN -> R.string.feature_search_quarter_autumn_air
            }

    companion object {
        /** 按「档期首月」归季。用于只知道月份的场景（如外部传入的初始月份） */
        fun fromMonth(month: Int): SeasonQuarter =
            when (month) {
                in 1..3 -> WINTER
                in 4..6 -> SPRING
                in 7..9 -> SUMMER
                else -> AUTUMN
            }

        /**
         * 按首播日归季。季界在每月 21 日：21 日及以后算**下一季**——这正是「秋番可从 9 月下旬开播」
         * 在代码里的落地。12 月下旬因此归入**次年**冬季，配套年份要用 [seasonYearOf] 取。
         */
        fun fromDate(date: LocalDate): SeasonQuarter {
            val quarterMonth = if (date.dayOfMonth >= 21) date.monthValue + 1 else date.monthValue
            return if (quarterMonth > 12) WINTER else fromMonth(quarterMonth)
        }

        /**
         * 首播日所属季的**年份**。
         *
         * 冬季窗口跨年，12 月下旬属于次年的冬季（它是「次年 12-21 ~ 次年 3-20」那段窗口的起点）。
         */
        fun seasonYearOf(date: LocalDate): Int = if (date.monthValue == 12 && date.dayOfMonth >= 21) date.year + 1 else date.year
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
 * 一级筛选：作品产地。
 *
 * 支持按标准 ISO 产地代码（`countryOfOrigin`）精确匹配，同时携带服务端下推的 `metaTag` 备选。
 */
enum class SeasonOriginFilter(
    @StringRes val labelRes: Int,
    val code: String?,
    val metaTag: String?,
) {
    ALL(R.string.feature_search_origin_all, null, null),
    JAPAN(R.string.feature_search_origin_japan, "JP", "日本"),
    CHINA(R.string.feature_search_origin_china, "CN", "中国"),
    ;

    fun matches(subject: Subject): Boolean {
        if (code == null) return true
        if (subject.countryOfOrigin.isNotBlank()) {
            return subject.countryOfOrigin.equals(code, ignoreCase = true)
        }
        return metaTag != null && subject.metaTags.contains(metaTag)
    }
}

/**
 * 二级筛选：放送形式。
 *
 * 覆盖所有核心动画形式（TV / 剧场版 / 网络动画 / OVA），支持多代码集合自适应匹配。
 */
enum class SeasonFormFilter(
    @StringRes val labelRes: Int,
    val formats: Set<String>,
    val metaTag: String?,
) {
    ALL(R.string.feature_search_form_all, emptySet(), null),
    TV(R.string.feature_search_form_tv, setOf("TV", "TV_SHORT"), "TV"),
    MOVIE(R.string.feature_search_form_movie, setOf("MOVIE", "剧场版"), "剧场版"),
    WEB(R.string.feature_search_form_web, setOf("WEB", "ONA"), "WEB"),
    OVA(R.string.feature_search_form_ova, setOf("OVA", "OAD"), "OVA"),
    ;

    fun matches(subject: Subject): Boolean {
        if (formats.isEmpty()) return true
        val p = subject.platform.uppercase()
        if (p in formats) return true
        return subject.metaTags.any { it.uppercase() in formats }
    }

    companion object {
        val DEFAULT = ALL
    }
}

/**
 * 排序方式。
 * - `HEAT` 综合热度（默认）；
 * - `SCORE` 评分最高；
 * - `AIR_DATE_DESC` 开播时间（新到旧）；
 * - `AIR_DATE_ASC` 开播时间（早到晚）；
 * - `TITLE` 标题拼音/英文升序（A-Z）。
 */
enum class SeasonSortOption(
    @StringRes val labelRes: Int,
    val apiValue: String,
) {
    HEAT(R.string.feature_search_season_sort_heat, "heat"),
    SCORE(R.string.feature_search_season_sort_score, "score"),
    AIR_DATE_DESC(R.string.feature_search_season_sort_air_desc, "air_date_desc"),
    AIR_DATE_ASC(R.string.feature_search_season_sort_air_asc, "air_date_asc"),
    TITLE(R.string.feature_search_season_sort_title, "title"),
    ;

    companion object {
        val DEFAULT = HEAT
    }
}

/**
 * 当季作品放送范围筛选：
 * - [ALL] 全部在播（本季首播新作 + 跨季连载中续作，统一大盘混合横向对比）；
 * - [NEW_ONLY] 仅首播新番（仅查看本季度第一话开播的作品）；
 * - [CONTINUING_ONLY] 仅跨季续播（仅查看半年番后半、年番及接档连载作品）。
 */
enum class SeasonAiringScope(
    @StringRes val labelRes: Int,
) {
    ALL(R.string.feature_search_airing_scope_all),
    NEW_ONLY(R.string.feature_search_airing_scope_new),
    CONTINUING_ONLY(R.string.feature_search_airing_scope_continuing),
    ;

    companion object {
        val DEFAULT = ALL
    }
}

/**
 * 季度片单 UI 状态。
 *
 * 这是**投影**而非容器（方案 B·响应式派生流）：ViewModel 把筛选输入、分页结果与收藏仓的流
 * `combine` 成这一份只读快照——状态是"底层事件流的数学映射"，没有谁去"改"它，上游变了它自然变。
 * 因此这里不该出现 setter，也不该有人对它做 `copy` 后回写。
 */
@Immutable
data class SeasonalGuideUiState(
    val selectedYear: Int = 2026,
    val selectedQuarter: SeasonQuarter = SeasonQuarter.WINTER,
    val currentYear: Int = 2026,
    val currentQuarter: SeasonQuarter = SeasonQuarter.WINTER,
    val selectedOrigin: SeasonOriginFilter = SeasonOriginFilter.ALL,
    val selectedForm: SeasonFormFilter = SeasonFormFilter.DEFAULT,
    /** 排序方式；服务端排序，切换即一次新查询 */
    val selectedSort: SeasonSortOption = SeasonSortOption.DEFAULT,
    /** 视图形态；纯展示偏好，切换不需要重新取数 */
    val viewMode: SeasonalViewMode = SeasonalViewMode.LIST,
    val availableYears: List<Int> = emptyList(),
    /** 本季首播：Bangumi `air_date` 区间过滤的结果 */
    val subjects: List<Subject> = emptyList(),
    val wishedSubjectIds: Set<Long> = emptySet(),
    val doingSubjectIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** 服务端游标：下一页的 offset。不能用 subjects.size 代替——去重会丢弃重复条目导致错位 */
    val pageOffset: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    /** 持久化的自定义常用筛选标签 */
    val customFilterTags: List<String> = emptyList(),
    /** 当前已激活包含的标签筛选集合（多选） */
    val selectedTags: Set<String> = emptySet(),
    /** 当前已激活排除的标签筛选集合（避雷） */
    val excludedTags: Set<String> = emptySet(),
    /** 从当前季度已拉取番剧中动态聚合的原生 AniList 核心题材分类统计（大类） */
    val seasonalGenres: List<Pair<String, Int>> = emptyList(),
    /** 从当前季度已拉取番剧中动态聚合的原生 AniList 特色微观标签统计（小类） */
    val seasonalHotTags: List<Pair<String, Int>> = emptyList(),
    /** 是否开启内容净化（将短片、MV、泡面番、动态漫折叠为胶囊单元） */
    val purifyContent: Boolean = true,
    /** 当前已被用户就地展开的折叠胶囊组键集合 */
    val expandedGroupKeys: Set<String> = emptySet(),
    /** 当季放送范围筛选（全部在播 / 仅首播新番 / 仅跨季续播） */
    val selectedAiringScope: SeasonAiringScope = SeasonAiringScope.DEFAULT,
    /** 跨季在播番的当前播出的下一话集数映射 (bgmId -> nextEpisodeNumber) */
    val continuingNextEpisodes: Map<Long, Int> = emptyMap(),
) {
    /** 当前所选年份和季度是否为真实当前季度（跨季续播仅在当季生效） */
    val isCurrentSeason: Boolean
        get() = selectedYear == currentYear && selectedQuarter == currentQuarter

    /** 筛选完全下推服务端，可见条目即服务端返回的原始条目 */
    val filteredSubjects: List<Subject>
        get() = subjects

    /** 依净化规则与展开状态计算出的界面展示单元列表 */
    val displayItems: List<SeasonalDisplayItem>
        get() = buildSeasonalDisplayItems(subjects, purifyContent, expandedGroupKeys)
}

/**
 * 筛选栏收起后，那一行摘要里显示的当前筛选，如「全部在播 · 日本 · 剧场版 · #百合」「全部」。
 */
@Composable
fun SeasonalGuideUiState.filterSummary(): String {
    val scopeLabel =
        if (isCurrentSeason && selectedAiringScope != SeasonAiringScope.ALL) {
            stringResource(selectedAiringScope.labelRes)
        } else {
            null
        }
    val originLabel =
        if (selectedOrigin != SeasonOriginFilter.ALL) stringResource(selectedOrigin.labelRes) else null
    val formLabel = if (selectedForm != SeasonFormFilter.ALL) stringResource(selectedForm.labelRes) else null
    val excludedTagLabels = excludedTags.map { stringResource(R.string.feature_search_filter_exclude_tag, it) }
    val purifyLabel = if (!purifyContent) stringResource(R.string.feature_search_filter_all_flat) else null
    val fallbackLabel = stringResource(SeasonOriginFilter.ALL.labelRes)
    return filterSummary(scopeLabel, originLabel, formLabel, excludedTagLabels, purifyLabel, fallbackLabel)
}

/**
 * 纯函数版本：各类标签文案由调用方解析后传入，便于在 Composable 之外复用与单元测试。
 */
internal fun SeasonalGuideUiState.filterSummary(
    scopeLabel: String?,
    originLabel: String?,
    formLabel: String?,
    excludedTagLabels: List<String>,
    purifyLabel: String?,
    fallbackLabel: String,
): String {
    val tagsLabel = if (selectedTags.isNotEmpty()) selectedTags.joinToString(" · ") { "#$it" } else null
    val excludedTagsLabel = excludedTagLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    return listOfNotNull(scopeLabel, originLabel, formLabel, tagsLabel, excludedTagsLabel, purifyLabel)
        .joinToString(" · ")
        .ifEmpty { fallbackLabel }
}

/**
 * 季度片单界面展示单元（常规条目 或 折叠胶囊）。
 */
sealed interface SeasonalDisplayItem {
    val key: String

    /** 常规条目展示 */
    data class Anime(
        val subject: Subject,
        val isFromFoldedGroup: Boolean = false,
    ) : SeasonalDisplayItem {
        override val key: String get() = "anime_${subject.id}"
    }

    /** 连续非主流条目聚合成的折叠胶囊 */
    data class FoldedGroup(
        val groupKey: String,
        val subjects: List<Subject>,
        val isExpanded: Boolean,
    ) : SeasonalDisplayItem {
        override val key: String get() = "folded_$groupKey"
    }
}

/**
 * 将季度片单原始作品流按净化规则聚合为展示单元列表：
 * 若开启净化 [purifyContent]，则将连续命中的 [Subject.isPurifiedNoise] 条目聚合为 [SeasonalDisplayItem.FoldedGroup]；
 * 用户若在界面上就地展开了某组（[expandedGroupKeys] 包含其 groupKey），则将该组内的条目作为带折叠标记的普通条目紧随其后呈现；
 * 若未开启净化，则全部平铺为 [SeasonalDisplayItem.Anime]。
 */
internal fun buildSeasonalDisplayItems(
    subjects: List<Subject>,
    purifyContent: Boolean,
    expandedGroupKeys: Set<String>,
): List<SeasonalDisplayItem> {
    if (!purifyContent) {
        return subjects.map { SeasonalDisplayItem.Anime(it) }
    }

    val result = mutableListOf<SeasonalDisplayItem>()
    val currentNoiseBuffer = mutableListOf<Subject>()

    fun flushNoiseBuffer() {
        if (currentNoiseBuffer.isEmpty()) return
        val groupKey = currentNoiseBuffer.first().id.toString()
        val isExpanded = groupKey in expandedGroupKeys
        val groupSubjects = currentNoiseBuffer.toList()
        currentNoiseBuffer.clear()

        result.add(SeasonalDisplayItem.FoldedGroup(groupKey, groupSubjects, isExpanded))
        if (isExpanded) {
            groupSubjects.forEach { sub ->
                result.add(SeasonalDisplayItem.Anime(sub, isFromFoldedGroup = true))
            }
        }
    }

    for (subject in subjects) {
        if (subject.isPurifiedNoise) {
            currentNoiseBuffer.add(subject)
        } else {
            flushNoiseBuffer()
            result.add(SeasonalDisplayItem.Anime(subject))
        }
    }
    flushNoiseBuffer()

    return result
}
