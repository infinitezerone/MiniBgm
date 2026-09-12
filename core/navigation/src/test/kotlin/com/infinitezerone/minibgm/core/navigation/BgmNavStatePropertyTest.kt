package com.infinitezerone.minibgm.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val TOP_LEVEL_ROUTES = setOf(ScheduleRoute, ExploreRoute, UserRoute)
private val RANDOM_TAGS = listOf("搞笑", "科幻", "日常", "恋爱")
private val RANDOM_QUERIES = listOf("", "fate", "白圣女")

/** 循环取元素，允许负数索引（随机种子可能为负） */
private fun <T> List<T>.cyclic(index: Int): T = get(((index % size) + size) % size)

/**
 * 导航不变式的属性化测试：以固定种子生成大量随机导航操作序列，逐步断言 [BgmNavState]
 * 必须始终满足的结构不变式（而非逐个手写回归用例）。新增路由或修改 [BgmNavState]
 * 的入栈/替换语义时，这里的任何失败都意味着导航契约被破坏，而非某个孤立场景的回归。
 *
 * 不变式清单（与 AGENTS.md 的导航红线对应）：
 * 1. 每个顶层 Tab 的子栈首元素恒为该 Tab 根路由（根部不可被弹出或覆盖）；
 * 2. 顶层历史首元素恒为 [ScheduleRoute]（exit through home）；
 * 3. 子栈中 [SubjectDetailRoute] 至多一个（连续点选条目是 replace 不是 push）；
 * 4. 任意时刻入栈 [SubjectDetailRoute] 后，子栈恰为 `[Tab根, 条目详情]`（返回一次必回到列表）；
 * 5. 入栈 [SearchRoute]/[UserCollectionsRoute] 后，子栈恰为 `[Tab根, 二级页]`（清理残留详情层级）；
 * 6. 从任意状态连续 [BgmNavState.goBack] 有界可达起始 Tab 根，且此后再退即抛出（应用出口唯一）。
 */
class BgmNavStatePropertyTest {
    private val topLevelRoutes = setOf(ScheduleRoute, ExploreRoute, UserRoute)

    /** 构造一步随机导航动作 */
    private sealed interface Action {
        fun applyTo(state: BgmNavState): Boolean
    }

    private data class PushDetail(
        val seed: Long,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(SubjectDetailRoute(subjectId = seed % 50 + 1))
            return true
        }
    }

    private data class PushLinked(
        val seed: Long,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(LinkedSubjectRoute(subjectId = seed % 50 + 100))
            return true
        }
    }

    private data class PushEpisode(
        val seed: Long,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(
                EpisodeDetailRoute(episodeId = seed % 500 + 1, subjectId = seed % 50 + 1),
            )
            return true
        }
    }

    private data class PushTag(
        val index: Int,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(TagSubjectsRoute(RANDOM_TAGS.cyclic(index)))
            return true
        }
    }

    private data class PushSearch(
        val index: Int,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(SearchRoute(initialQuery = RANDOM_QUERIES.cyclic(index)))
            return true
        }
    }

    private data class PushCollections(
        val seed: Long,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(UserCollectionsRoute(initialType = (seed % 3).toInt() + 1))
            return true
        }
    }

    private data class SwitchTab(
        val index: Int,
    ) : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            state.navigateTo(TOP_LEVEL_ROUTES.toList().cyclic(index))
            return true
        }
    }

    private data object GoBack : Action {
        override fun applyTo(state: BgmNavState): Boolean {
            if (state.currentKey == state.startRoute) return false
            state.goBack()
            return true
        }
    }

    private fun randomActions(rng: Random): List<Action> =
        List(rng.nextInt(10, 40)) {
            when (rng.nextInt(8)) {
                0 -> PushDetail(rng.nextLong())
                1 -> PushLinked(rng.nextLong())
                2 -> PushEpisode(rng.nextLong())
                3 -> PushTag(rng.nextInt())
                4 -> PushSearch(rng.nextInt())
                5 -> PushCollections(rng.nextLong())
                6 -> SwitchTab(rng.nextInt())
                else -> GoBack
            }
        }

    private fun newState(): BgmNavState =
        BgmNavState(
            startRoute = ScheduleRoute,
            topLevelStack = NavBackStack<NavKey>(ScheduleRoute),
            subStacks = topLevelRoutes.associateWith { key -> NavBackStack<NavKey>(key) },
        )

    // ---- 不变式断言 ----

    private fun assertInvariants(
        state: BgmNavState,
        context: String,
    ) {
        // 1. 顶层历史首元素恒为 startRoute，且非空
        assertTrue(
            "$context: topLevelStack 必须以 startRoute 开头，实际 ${state.topLevelStack.toList()}",
            state.topLevelStack.first() == state.startRoute,
        )
        // 2. 当前子栈非空且首元素为当前 Tab 根路由
        val stack = state.currentSubStack.toList()
        assertTrue("$context: 子栈不能为空", stack.isNotEmpty())
        assertEquals(
            "$context: 子栈首元素必须是当前 Tab 根",
            state.currentTopLevelKey,
            stack.first(),
        )
        // 3. 子栈中 SubjectDetailRoute 至多一个；同类二级页（搜索/收藏）也至多一个
        val detailCount = stack.count { it is SubjectDetailRoute }
        assertTrue(
            "$context: 子栈中 SubjectDetailRoute 出现 $detailCount 次（>1 意味着条目详情被 push 而非 replace）：$stack",
            detailCount <= 1,
        )
        val searchCount = stack.count { it is SearchRoute }
        assertTrue(
            "$context: 子栈中 SearchRoute 出现 $searchCount 次（同类二级页应替换而非堆叠）：$stack",
            searchCount <= 1,
        )
        val collectionsCount = stack.count { it is UserCollectionsRoute }
        assertTrue(
            "$context: 子栈中 UserCollectionsRoute 出现 $collectionsCount 次（同类二级页应替换而非堆叠）：$stack",
            collectionsCount <= 1,
        )
    }

    /**
     * 从任意状态连续 goBack 应有界可达 startRoute。上界 = 顶层历史中各 Tab 子栈深度之和 - 1
     * （每个 Tab 需要退回自身根）+ 顶层历史跳转次数（topLevelStack.size - 1）：
     * 返回键在当前 Tab 根会沿顶层历史回退到上一个 Tab，而上一个 Tab 的子栈也可能很深。
     */
    private fun assertBackReachable(
        state: BgmNavState,
        context: String,
    ) {
        val maxSteps =
            state.topLevelStack.sumOf { state.subStackSizes.getValue(it) - 1 } +
                (state.topLevelStack.size - 1)
        var steps = 0
        while (state.currentKey != state.startRoute) {
            state.goBack()
            steps++
            assertTrue(
                "$context: 连续 goBack 超过 $maxSteps 次仍未回到 startRoute（返回链断裂或成环）",
                steps <= maxSteps,
            )
        }
        assertEquals(
            "$context: 回到 startRoute 后顶层历史应只剩起始 Tab",
            listOf(state.startRoute),
            state.topLevelStack.toList(),
        )
    }

    @Test
    fun randomSequences_preserveNavigationInvariants() {
        // 多个固定种子保证失败可复现：任何种子失败即可用该种子单独重放调试
        val seeds = listOf(1L, 7L, 42L, 2026L, 0x5EEDL)
        for (seed in seeds) {
            val rng = Random(seed)
            val state = newState()
            val actions = randomActions(rng)
            actions.forEachIndexed { index, action ->
                val applied = action.applyTo(state)
                if (!applied) return@forEachIndexed
                assertInvariants(state, "seed=$seed step=$index action=${action::class.simpleName}")
            }
        }
    }

    @Test
    fun randomSequences_backReturnsToListAfterDetailSelection() {
        // 分栏模式核心契约：在"Tab 根 ↔ 详情钻取链"路径上（不含搜索等二级页），
        // 点选条目详情后子栈恰为 [Tab根, 详情]，返回一次必回到 Tab 根而非倒退到上一个条目。
        // 注：搜索/收藏页上点详情的合法路径为 [根, 二级页, 详情]，返回回到二级页，另行覆盖。
        val seeds = listOf(1L, 7L, 42L, 2026L, 0x5EEDL)
        for (seed in seeds) {
            val rng = Random(seed)
            val state = newState()
            repeat(30) {
                val action =
                    listOf(
                        PushDetail(rng.nextLong()),
                        PushLinked(rng.nextLong()),
                        PushEpisode(rng.nextLong()),
                        PushTag(rng.nextInt()),
                        SwitchTab(rng.nextInt()),
                        GoBack,
                    )[rng.nextInt(6)]
                action.applyTo(state)
            }
            // 任意顶层 Tab 下：点选详情 ⇒ 子栈恰为 [Tab根, 详情] ⇒ 返回一次回到根
            state.navigateTo(SubjectDetailRoute(subjectId = 99L))
            assertEquals(
                "seed=$seed: 点选条目详情后子栈深度应为 2，实际 ${state.currentSubStack.toList()}",
                2,
                state.currentSubStack.size,
            )
            state.goBack()
            assertEquals(
                "seed=$seed: 返回一次应回到 Tab 根",
                state.currentTopLevelKey,
                state.currentKey,
            )
        }
    }

    @Test
    fun randomSequences_secondLevelScreens_clearResidualDetailHierarchy() {
        val seeds = listOf(1L, 7L, 42L, 2026L, 0x5EEDL)
        for (seed in seeds) {
            val rng = Random(seed)
            val state = newState()
            repeat(20) {
                listOf(
                    PushDetail(rng.nextLong()),
                    PushLinked(rng.nextLong()),
                    PushEpisode(rng.nextLong()),
                    PushTag(rng.nextInt()),
                    GoBack,
                )[rng.nextInt(5)].applyTo(state)
            }
            // 声明的契约：进入二级列表页清理全部详情层级残留，二级页之下只保留 Tab 根；
            // 二级页之间可以共存（如 [根, Search, Collections]），但详情链不得残留在其上方
            state.navigateTo(SearchRoute())
            assertTrue(
                "seed=$seed: 进入搜索后详情层级应被清理，实际 ${state.currentSubStack.toList()}",
                state.currentSubStack.none { it is SubjectDetailRoute },
            )
            assertTrue(
                "seed=$seed: 进入搜索后子栈应以 Tab 根开始，实际 ${state.currentSubStack.toList()}",
                state.currentSubStack.first() == state.currentTopLevelKey,
            )
            state.navigateTo(UserCollectionsRoute())
            assertTrue(
                "seed=$seed: 进入收藏列表后详情层级应被清理，实际 ${state.currentSubStack.toList()}",
                state.currentSubStack.none { it is SubjectDetailRoute },
            )
            assertTrue(
                "seed=$seed: 进入收藏列表后子栈应以 Tab 根开始，实际 ${state.currentSubStack.toList()}",
                state.currentSubStack.first() == state.currentTopLevelKey,
            )
        }
    }

    @Test
    fun randomSequences_detailOverSecondLevel_backReturnsToSecondLevel() {
        // 搜索/收藏页点选条目是合法的 [根, 二级页, 详情] 路径：返回一次回到二级页（结果列表），
        // 再次点选新详情时替换旧详情而非堆叠
        val seeds = listOf(1L, 7L, 42L, 2026L, 0x5EEDL)
        for (seed in seeds) {
            val state = newState()
            state.navigateTo(ExploreRoute)
            state.navigateTo(SearchRoute())
            state.navigateTo(SubjectDetailRoute(subjectId = 1L))
            assertEquals(3, state.currentSubStack.size)
            state.goBack()
            assertEquals(
                "seed=$seed: 从详情返回应回到搜索结果页",
                SearchRoute(),
                state.currentKey,
            )
            state.navigateTo(SubjectDetailRoute(subjectId = 2L))
            assertEquals(
                "seed=$seed: 搜索结果页连续点选不同条目应替换而非堆叠",
                listOf<NavKey>(ExploreRoute, SearchRoute(), SubjectDetailRoute(2L)),
                state.currentSubStack.toList(),
            )
        }
    }

    @Test
    fun randomSequences_backIsBoundedAndTerminatesAtStartRoute() {
        val seeds = listOf(1L, 7L, 42L, 2026L, 0x5EEDL)
        for (seed in seeds) {
            val rng = Random(seed)
            val state = newState()
            randomActions(rng).forEach { action -> action.applyTo(state) }
            assertBackReachable(state, "seed=$seed")
            // 到达起始 Tab 根后，goBack 即应用出口（抛出而非静默无操作）
            org.junit.Assert.assertThrows(IllegalStateException::class.java) { state.goBack() }
        }
    }
}
