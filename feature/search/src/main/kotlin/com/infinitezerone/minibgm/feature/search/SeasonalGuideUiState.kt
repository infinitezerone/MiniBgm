package com.infinitezerone.minibgm.feature.search

import androidx.compose.runtime.Immutable
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
    val label: String,
    val displayLabel: String,
) {
    WINTER(1, "冬", "1月冬"),
    SPRING(4, "春", "4月春"),
    SUMMER(7, "夏", "7月夏"),
    AUTUMN(10, "秋", "10月秋"),
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
    val airDateLabel: String
        get() =
            when (this) {
                WINTER -> "首播 12月下旬 ~ 3月中旬"
                SPRING -> "首播 3月下旬 ~ 6月中旬"
                SUMMER -> "首播 6月下旬 ~ 9月中旬"
                AUTUMN -> "首播 9月下旬 ~ 12月中旬"
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
 * 100% 服务端下推（`filter.meta_tags`），零客户端过滤，分页游标与 total 严格对齐。
 */
enum class SeasonOriginFilter(
    val label: String,
    val metaTag: String?,
) {
    ALL("全部", null),
    JAPAN("日本", "日本"),
    CHINA("国产", "中国"),
}

/**
 * 二级筛选：放送形式。
 *
 * 仅保留全集与官方 API 唯一下推精确支持的「剧场版」（`filter.meta_tags: ["剧场版"]`）。
 * 100% 服务端下推，无需任何客户端补筛。
 */
enum class SeasonFormFilter(
    val label: String,
    val metaTag: String?,
) {
    ALL("全部", null),
    MOVIE("剧场版", "剧场版"),
    ;

    companion object {
        val DEFAULT = ALL
    }
}

/**
 * 排序方式。只有服务端真实支持的取值才进得来：
 * - `heat` 混合热度（默认）；
 * - `score` 评分降序——注意服务端会让只有个位数打分的 10 分小样本排在最前，这是 API 固有行为；
 * - `rank` **不可用**：实测服务端把无排名（rank=0）的条目排在最前，对季度筛选场景是坏的；
 * - `air_date` 服务端 400 拒绝，客户端排序又会破坏无限翻页的完整性，故不提供。
 */
enum class SeasonSortOption(
    val label: String,
    val apiValue: String,
) {
    HEAT("热度", "heat"),
    SCORE("评分", "score"),
    ;

    companion object {
        val DEFAULT = HEAT
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
) {
    /** 筛选完全下推服务端，可见条目即服务端返回的原始条目 */
    val filteredSubjects: List<Subject>
        get() = subjects

    /**
     * 筛选栏收起后，那一行摘要里显示的当前筛选，如「日本 · 剧场版」「剧场版」「全部」。
     */
    val filterSummary: String
        get() {
            val originLabel = selectedOrigin.label.takeIf { selectedOrigin != SeasonOriginFilter.ALL }
            val formLabel = selectedForm.label.takeIf { selectedForm != SeasonFormFilter.ALL }
            return listOfNotNull(originLabel, formLabel)
                .joinToString(" · ")
                .ifEmpty { SeasonOriginFilter.ALL.label }
        }
}
