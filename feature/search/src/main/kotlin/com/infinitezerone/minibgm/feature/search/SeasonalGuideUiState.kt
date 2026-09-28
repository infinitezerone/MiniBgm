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
 * 一级筛选：作品产地。
 *
 * 取值来自 Bangumi 官方元标签 `meta_tags`，与高级搜索的 `filter.meta_tags` 同源。
 * 日本／国产都能用单个标签精确下推；[WESTERN] 不行，原因见该档注释。
 */
enum class SeasonOriginFilter(
    val label: String,
    /** 下推服务端用的 `meta_tags` 值；null 表示服务端不追加该条件（改由客户端筛，或本就无需筛） */
    val metaTag: String?,
) {
    ALL("全部", null),
    JAPAN("日本", META_TAG_JAPAN),
    CHINA("国产", META_TAG_CHINA),

    /**
     * 欧美。
     *
     * 服务端的 `meta_tags` 是**精确单标签匹配、且多值为 AND**，没有"或"。实测当季 20 条欧美条目里，
     * 只有 7 条带「欧美」标签、15 条带「美国」，两者重叠仅 5 条——用任何一个单标签下推都会漏掉一半以上。
     * 因此这一档服务端不过滤，改在客户端按 [META_TAGS_WESTERN] 整个集合筛。
     */
    WESTERN("欧美", null),
    ;

    /**
     * 首播列表是否还需要客户端补过滤。
     *
     * 服务端筛过的档位不能再客户端筛一遍：两端口径不完全一致时会误杀。
     */
    val needsClientFilter: Boolean
        get() = this == WESTERN
}

/**
 * 二级筛选：放送形式。
 *
 * `meta_tags` 多值是 **AND** 语义、且没有排除语法，这决定了三个档位的取舍：
 * [TV] / [WEB] / [MOVIE] 都能用单个标签精确下推；[SHORT]（片段型）是"以上皆非"，
 * 服务端表达不了，只能留在客户端筛。
 */
enum class SeasonFormFilter(
    val label: String,
    val metaTag: String?,
) {
    ALL("全部", null),
    TV("TV", META_TAG_TV),
    WEB("网络", META_TAG_WEB),
    MOVIE("剧场版", META_TAG_MOVIE),

    /** 片段型条目（MV / PV / CM / 短片），默认被 [ALL] 折叠掉 */
    SHORT("短片 / MV", null),
    ;

    /**
     * 首播列表是否还需要客户端补过滤。
     *
     * [TV] / [WEB] / [MOVIE] 已由服务端按 `meta_tags` 过滤，客户端再筛一遍反而可能
     * 因判据不完全一致而误杀；[ALL]（排除片段型）与 [SHORT] 服务端无法表达，只能客户端兜。
     */
    val needsClientFilter: Boolean
        get() = this == ALL || this == SHORT
}

private const val META_TAG_JAPAN = "日本"
private const val META_TAG_CHINA = "中国"
private const val META_TAG_TV = "TV"
private const val META_TAG_WEB = "WEB"
private const val META_TAG_MOVIE = "剧场版"

/**
 * 判定产地为欧美用的元标签集合。
 *
 * 「欧美」这个聚合标签只覆盖了一部分条目——真实数据里大量条目**只标了具体国家**没标「欧美」
 * （当季 20 条欧美条目中只有 7 条带「欧美」），所以判定要取整个集合。
 */
private val META_TAGS_WESTERN = setOf("欧美", "美国", "英国", "法国", "加拿大", "德国")

/** 条目是否符合产地筛选 */
fun matchesOrigin(
    subject: Subject,
    origin: SeasonOriginFilter,
): Boolean =
    when (origin) {
        SeasonOriginFilter.ALL -> true
        SeasonOriginFilter.JAPAN -> META_TAG_JAPAN in subject.metaTags
        SeasonOriginFilter.CHINA -> META_TAG_CHINA in subject.metaTags
        SeasonOriginFilter.WESTERN -> subject.metaTags.any { it in META_TAGS_WESTERN }
    }

/**
 * 条目是否符合形式筛选。
 *
 * 对首播列表，[SeasonFormFilter.needsClientFilter] 为 false 时服务端已经筛过；
 * 真正的用武之地是「本季连载中」分组——它来自本地排期仓，不走搜索接口。
 */
fun matchesForm(
    subject: Subject,
    form: SeasonFormFilter,
): Boolean =
    when (form) {
        SeasonFormFilter.ALL -> !subject.isShortForm
        SeasonFormFilter.SHORT -> subject.isShortForm
        SeasonFormFilter.TV -> META_TAG_TV in subject.metaTags || subject.platform == META_TAG_TV
        SeasonFormFilter.WEB -> META_TAG_WEB in subject.metaTags || subject.platform == META_TAG_WEB
        SeasonFormFilter.MOVIE -> META_TAG_MOVIE in subject.metaTags || subject.platform == META_TAG_MOVIE
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
    val selectedOrigin: SeasonOriginFilter = SeasonOriginFilter.ALL,
    val selectedForm: SeasonFormFilter = SeasonFormFilter.ALL,
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
    /**
     * 首播列表的可见条目。
     *
     * 日本／国产两档产地与 TV／网络／剧场版三档形式都已由服务端筛过，这里只兜服务端表达不了的两类：
     * 产地 [SeasonOriginFilter.WESTERN]（"或"关系）与形式 [SeasonFormFilter.ALL]（排除片段型）／
     * [SeasonFormFilter.SHORT]（只看片段型）（排除语法）。
     *
     * 过滤不改变翻页游标——游标始终按服务端返回的原始条数前进，所以一路加载到底得到的就是完整结果。
     */
    val filteredSubjects: List<Subject>
        get() =
            subjects.filter { subject ->
                val originMatches = !selectedOrigin.needsClientFilter || matchesOrigin(subject, selectedOrigin)
                val formMatches = !selectedForm.needsClientFilter || matchesForm(subject, selectedForm)
                originMatches && formMatches
            }

    /** 「本季连载中」来自本地排期仓，不经搜索接口，故两层筛选都要在客户端补上 */
    val filteredOngoingSubjects: List<Subject>
        get() = ongoingSubjects.filter { matchesOrigin(it, selectedOrigin) && matchesForm(it, selectedForm) }
}
