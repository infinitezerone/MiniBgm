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
 * 二级筛选：放送形式（**多选**）。
 *
 * 早先按 `platform` 字段的取值拆成 TV / 网络 / 剧场版 / 短片 四档单选，是照着数据字段的形状设计界面，
 * 结果既反直觉又冗余。实测当季 240 条：
 * - TV 87 / WEB 75，两者**严格互斥**（没有任何条目同时带这两个 meta_tag），等于把占 67.5% 的
 *   同一类东西（正常时长的连载番）硬劈两半、逼用户二选一；
 * - TV/WEB 与产地几乎完全重合（日本条目 82% 是 TV、国产条目 98% 是 WEB），这一层基本是上一级的
 *   重复编码，还会组合出近乎空集——选「国产 + TV」只剩 1 部，界面看起来像坏了。
 *
 * 改按**内容形态**分成三档并放开为多选：用户真正会同时关心的是「正片 + 剧场版」，
 * 「在哪个平台播」没人关心。三档互斥且恰好覆盖 100%（正片 67.1%、剧场版 12.9%、短片 20.0%）。
 *
 * **全不选 = 不筛形式**（显示全部），与多选筛选的通用语义一致。
 */
enum class SeasonFormFilter(
    val label: String,
) {
    /**
     * 正常时长的连载番：TV + 网络 + OVA。
     * **只能客户端筛**——服务端 `meta_tags` 多值是 AND 语义，表达不了「TV 或 WEB」。
     */
    MAIN("正片"),

    /** 剧场版。恰好只选中这一档时可下推服务端，此时分页总数是准的（见 [serverMetaTagOf]） */
    MOVIE("剧场版"),

    /** 片段型：MV / PV / CM / 短片。同样是「或」关系（MV 或 PV 或 …），服务端表达不了 */
    SHORT("短片 / MV"),
    ;

    companion object {
        /** 默认只开「正片」——导视的首要诉求是「这季有哪些连载番在播」 */
        val DEFAULT: Set<SeasonFormFilter> = setOf(MAIN)

        /**
         * 能整体下推服务端的组合，返回对应的 `meta_tags` 值；表达不了则为 null。
         *
         * 只有「恰好只选剧场版」这一种情况服务端能精确表达（`meta_tags: ["剧场版"]`）。
         * 正片与短片都是「或」关系，而服务端多值 meta_tags 是 AND、又没有排除语法。
         */
        fun serverMetaTagOf(forms: Set<SeasonFormFilter>): String? = if (forms == setOf(MOVIE)) META_TAG_MOVIE else null
    }
}

private const val META_TAG_JAPAN = "日本"
private const val META_TAG_CHINA = "中国"
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
 * 条目属于哪一档形式。三档互斥，恰好覆盖全部条目（实测当季 240 条：161 / 31 / 48）。
 *
 * 先判片段型：[Subject.isShortForm] 已经同时看 `platform` 与片段类元标签——
 * 真实数据里有 `platform` 标成 WEB 却带「短片」标签的条目，只看 `platform` 会漏。
 */
fun formBucketOf(subject: Subject): SeasonFormFilter =
    when {
        subject.isShortForm -> SeasonFormFilter.SHORT
        subject.platform == META_TAG_MOVIE || META_TAG_MOVIE in subject.metaTags -> SeasonFormFilter.MOVIE
        else -> SeasonFormFilter.MAIN
    }

/**
 * 条目是否符合形式筛选。
 *
 * 空集合表示「不筛形式」；否则按 [formBucketOf] 归类后看该档是否被选中。
 *
 * 服务端只在「恰好只选剧场版」时筛过一遍，且其判据与 [formBucketOf] 同源，重复筛不会误杀；
 * 其余组合服务端都表达不了，只能靠这里补——「本季连载中」分组不走搜索接口，更是只能靠这里。
 */
fun matchesForm(
    subject: Subject,
    forms: Set<SeasonFormFilter>,
): Boolean = forms.isEmpty() || formBucketOf(subject) in forms

/**
 * 季度新番导视 UI 状态。
 *
 * 这是**投影**而非容器（方案 B·响应式派生流）：ViewModel 把筛选输入、分页结果、排期仓与收藏仓的流
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
    /** 二级筛选可多选；**空集合 = 不筛形式**（显示全部） */
    val selectedForms: Set<SeasonFormFilter> = SeasonFormFilter.DEFAULT,
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
     * 产地仍是「日本／国产下推服务端，欧美客户端兜」——服务端筛过的档位判据与
     * [matchesOrigin] 同源，不重复筛也不会误杀。形式则一律在这里补：服务端只在
     * 「恰好只选剧场版」时筛过一次（见 [SeasonFormFilter.serverMetaTagOf]），其余组合它表达不了。
     *
     * 过滤不改变翻页游标——游标始终按服务端返回的原始条数前进，所以一路加载到底得到的就是完整结果。
     */
    val filteredSubjects: List<Subject>
        get() =
            subjects.filter { subject ->
                val originMatches = !selectedOrigin.needsClientFilter || matchesOrigin(subject, selectedOrigin)
                originMatches && matchesForm(subject, selectedForms)
            }

    /** 「本季连载中」来自本地排期仓，不经搜索接口，故两层筛选都要在客户端补上 */
    val filteredOngoingSubjects: List<Subject>
        get() = ongoingSubjects.filter { matchesOrigin(it, selectedOrigin) && matchesForm(it, selectedForms) }

    /**
     * 筛选栏收起后，那一行摘要里显示的当前筛选，如「日本 · 正片」「正片+剧场版」「全部」。
     *
     * 产地为「全部」时不显示它，避免出现「全部 · 全部」这种同义重复；两者都无约束时回落为「全部」。
     */
    val filterSummary: String
        get() {
            val originLabel = selectedOrigin.label.takeIf { selectedOrigin != SeasonOriginFilter.ALL }
            val formsLabel = selectedForms.sorted().joinToString("+") { it.label }.ifEmpty { null }
            return listOfNotNull(originLabel, formsLabel)
                .joinToString(" · ")
                .ifEmpty { SeasonOriginFilter.ALL.label }
        }
}
