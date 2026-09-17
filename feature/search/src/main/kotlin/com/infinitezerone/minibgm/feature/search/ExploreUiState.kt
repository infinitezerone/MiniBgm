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
    val category: TimeCategory = TimeCategory.YEAR,
)

enum class TimeCategory(
    val label: String,
) {
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
    MASTERPIECE("💎 封神必看", emptyList(), ExploreSort.RANK),
    HOT("🔥 热门流行", emptyList(), ExploreSort.HEAT),
    HEALING("🌿 深夜解压", listOf("治愈", "日常"), ExploreSort.RANK),
    SHONEN("⚔️ 热血高燃", listOf("热血", "战斗"), ExploreSort.HEAT),
    SUSPENSE("🧠 烧脑悬疑", listOf("悬疑", "推理"), ExploreSort.RANK),
    TEARS("💧 催泪后劲", listOf("催泪", "感动"), ExploreSort.RANK),
    ROMANCE("🌸 纯爱心动", listOf("恋爱", "纯爱"), ExploreSort.RANK),
    FANTASY("🔮 异界奇幻", listOf("奇幻", "冒险"), ExploreSort.HEAT),
    BLIND_BOX("🎲 随心盲盒", emptyList(), ExploreSort.RANK),
}

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

/** 生成完整的年代与年份列表 */
fun generateFullTimeOptions(
    nowYear: Int,
    nowMonth: Int = 1,
): List<SeasonOption> {
    val options = mutableListOf<SeasonOption>()

    // 1. 全部时间 (默认)
    options.add(SeasonOption(id = "all", label = "全部时间", airDateFilter = null, category = TimeCategory.ALL))

    // 2. 年份维度（近 6 年单年）
    for (yearOffset in 0..5) {
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
        ?: SeasonOption(id = "all", label = "全部时间", airDateFilter = null, category = TimeCategory.ALL)
val CURRENT_SEASON = ALL_TIME_SEASON

/** 探索发现界面的单一不可变 UI 状态 */
@Immutable
data class ExploreUiState(
    val selectedSeason: SeasonOption = ALL_TIME_SEASON,
    val selectedCategory: ExploreCategory = ExploreCategory.ANIME,
    val selectedTags: Set<String> = emptySet(),
    val customTagInput: String = "",
    val selectedSort: ExploreSort = ExploreSort.RANK,
    val selectedMood: ExploreMood? = ExploreMood.MASTERPIECE,
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
