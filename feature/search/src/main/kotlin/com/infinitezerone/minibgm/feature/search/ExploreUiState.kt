package com.infinitezerone.minibgm.feature.search

import androidx.compose.runtime.Immutable
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectComment
import java.time.LocalDate

/** 季度/年份/年代时间筛选选项 */
@Immutable
data class SeasonOption(
    val id: String,
    val label: String,
    val airDateFilter: List<String>? = null,
    val category: TimeCategory = TimeCategory.QUARTER,
)

enum class TimeCategory(
    val label: String,
) {
    QUARTER("按季度"),
    YEAR("按年份/年代"),
    ALL("全部时间"),
}

/** 探索分类定义：动画 (2)、书籍 (1)、游戏 (4)、音乐 (3)、全部 (null) */
enum class ExploreCategory(
    val type: Int?,
    val label: String,
) {
    ANIME(2, "动画"),
    BOOK(1, "书籍"),
    GAME(4, "游戏"),
    MUSIC(3, "音乐"),
    ALL(null, "全部"),
}

/** 排序方式定义 */
enum class ExploreSort(
    val sortKey: String,
    val label: String,
) {
    HEAT("heat", "热门排行"),
    SCORE("score", "评分最高"),
    RANK("rank", "排名优先"),
}

/** 心境/场景预设筛选（小红书/盲盒安利流） */
enum class ExploreMood(
    val label: String,
    val tags: List<String> = emptyList(),
    val sort: ExploreSort,
) {
    TRENDING("🔥 当季爆款", emptyList(), ExploreSort.HEAT),
    MASTERPIECE("💎 封神必看", emptyList(), ExploreSort.RANK),
    HEALING("🌿 深夜解压", listOf("治愈", "日常"), ExploreSort.RANK),
    SHONEN("⚔️ 热血高燃", listOf("热血", "战斗"), ExploreSort.HEAT),
    SUSPENSE("🧠 烧脑悬疑", listOf("悬疑", "推理"), ExploreSort.RANK),
    TEARS("💧 催泪后劲", listOf("催泪", "感动"), ExploreSort.RANK),
    ROMANCE("🌸 纯爱心动", listOf("恋爱", "纯爱"), ExploreSort.RANK),
    FANTASY("🔮 异界奇幻", listOf("奇幻", "冒险"), ExploreSort.HEAT),
    BLIND_BOX("🎲 随心盲盒", emptyList(), ExploreSort.RANK),
}

private data class SeasonMeta(
    val name: String,
    val startMonthDay: String,
    val endYearOffset: Int,
    val endMonthDay: String,
)

/** 标签维度分组 */
@Immutable
data class TagGroup(
    val name: String,
    val tags: List<String>,
)

val TAG_GROUPS =
    listOf(
        TagGroup(
            name = "🌈 题材风格",
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
            name = "🎬 制作厂牌/监督",
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
            name = "📺 形式与受众",
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

/** 生成完整的季度与年代列表 */
fun generateFullTimeOptions(
    nowYear: Int,
    nowMonth: Int,
): List<SeasonOption> {
    val options = mutableListOf<SeasonOption>()
    val currentQuarter = (nowMonth - 1) / 3 + 1 // 1..4

    // 1. 季度维度：下季(+1)、当季(0)、过去8个季度
    for (offset in 1 downTo -7) {
        var q = currentQuarter + offset
        var y = nowYear
        while (q < 1) {
            q += 4
            y -= 1
        }
        while (q > 4) {
            q -= 4
            y += 1
        }

        val meta =
            when (q) {
                1 -> SeasonMeta("冬季 (1月)", "01-01", 0, "04-01")
                2 -> SeasonMeta("春季 (4月)", "04-01", 0, "07-01")
                3 -> SeasonMeta("夏季 (7月)", "07-01", 0, "10-01")
                else -> SeasonMeta("秋季 (10月)", "10-01", 1, "01-01")
            }

        val isCurrent = (y == nowYear && q == currentQuarter)
        val isNext = (offset == 1)
        val prefix =
            when {
                isCurrent -> "🔥 本季 "
                isNext -> "👀 下季 "
                else -> ""
            }
        val label = "$prefix$y ${meta.name}"
        val id = "$y-q$q"
        val endYear = y + meta.endYearOffset
        val airDates = listOf(">=$y-${meta.startMonthDay}", "<$endYear-${meta.endMonthDay}")
        options.add(SeasonOption(id = id, label = label, airDateFilter = airDates, category = TimeCategory.QUARTER))
    }

    // 2. 年份维度（近 6 年单年）
    for (yearOffset in 0..6) {
        val y = nowYear - yearOffset
        options.add(
            SeasonOption(
                id = "$y-full",
                label = "${y}年",
                airDateFilter = listOf(">=$y-01-01", "<${y + 1}-01-01"),
                category = TimeCategory.YEAR,
            ),
        )
    }

    // 3. 经典年代维度
    options.add(
        SeasonOption(
            id = "2010s",
            label = "2010 年代 (2010-2019)",
            airDateFilter = listOf(">=2010-01-01", "<2020-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "2000s",
            label = "2000 年代 (2000-2009)",
            airDateFilter = listOf(">=2000-01-01", "<2010-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "1990s",
            label = "90 年代 (1990-1999)",
            airDateFilter = listOf(">=1990-01-01", "<2000-01-01"),
            category = TimeCategory.YEAR,
        ),
    )
    options.add(
        SeasonOption(
            id = "1980s-before",
            label = "80 年代及更早 (<1990)",
            airDateFilter = listOf("<1990-01-01"),
            category = TimeCategory.YEAR,
        ),
    )

    // 4. 全部时间
    options.add(SeasonOption(id = "all", label = "全部时间", airDateFilter = null, category = TimeCategory.ALL))

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
val CURRENT_SEASON = DEFAULT_SEASONS.firstOrNull { it.label.contains("本季") } ?: DEFAULT_SEASONS.first()
val ALL_TIME_SEASON =
    DEFAULT_SEASONS.firstOrNull { it.category == TimeCategory.ALL }
        ?: SeasonOption(id = "all", label = "全部时间", airDateFilter = null, category = TimeCategory.ALL)

/** 探索发现界面的单一不可变 UI 状态 */
@Immutable
data class ExploreUiState(
    val selectedSeason: SeasonOption = CURRENT_SEASON,
    val selectedCategory: ExploreCategory = ExploreCategory.ANIME,
    val selectedTags: Set<String> = emptySet(),
    val customTagInput: String = "",
    val selectedSort: ExploreSort = ExploreSort.HEAT,
    val selectedMood: ExploreMood? = ExploreMood.TRENDING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val pageOffset: Int = 0,
    val isLoggedIn: Boolean = false,
    val showLoginPromptDialog: Boolean = false,
    val subjects: List<Subject> = emptyList(),
    val wishedSubjectIds: Set<Long> = emptySet(),
    val hotComments: Map<Long, SubjectComment> = emptyMap(),
    val error: String? = null,
    val userMessage: String? = null,
)
