package com.infinitezerone.minibgm.core.navigation

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * 创建可在配置变更与进程死亡后恢复的导航状态（对齐 NiA 的 core:navigation 模式）：
 * [BgmNavState.topLevelStack] 记录顶层 Tab 的切换历史（返回键沿历史回退），
 * 每个 Tab 另有独立子返回栈；用户始终经由起始 Tab 退出应用（exit through home）。
 */
@Composable
fun rememberBgmNavState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
): BgmNavState {
    val topLevelStack = rememberNavBackStack(startRoute)
    val subStacks: Map<NavKey, NavBackStack<NavKey>> =
        topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        BgmNavState(
            startRoute = startRoute,
            topLevelStack = topLevelStack,
            subStacks = subStacks,
        )
    }
}

/**
 * 导航状态持有者；仅通过 [navigateTo]/[goBack] 修改自身状态
 */
class BgmNavState(
    val startRoute: NavKey,
    val topLevelStack: NavBackStack<NavKey>,
    private val subStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    /** 当前选中的顶层 Tab */
    val currentTopLevelKey: NavKey by derivedStateOf { topLevelStack.last() }

    val topLevelKeys: Set<NavKey>
        get() = subStacks.keys

    /** 当前 Tab 的子返回栈 */
    @get:VisibleForTesting
    val currentSubStack: NavBackStack<NavKey>
        get() =
            subStacks[currentTopLevelKey]
                ?: error("Sub stack for $currentTopLevelKey does not exist")

    /** 各顶层 Tab 的子栈深度（供测试断言返回链上界） */
    @get:VisibleForTesting
    val subStackSizes: Map<NavKey, Int>
        get() = subStacks.mapValues { (_, stack) -> stack.size }

    /** 当前 Tab 栈顶 key，即屏幕上可见的目的地 */
    val currentKey: NavKey by derivedStateOf { currentSubStack.last() }

    private val _tabReselectionEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)

    /** 当在顶层 Tab 根页面再次点击当前 Tab 时分发的重选事件流（用于列表平滑回顶等手势） */
    val tabReselectionEvents: SharedFlow<NavKey> = _tabReselectionEvents.asSharedFlow()

    /** 获取指定顶层 Tab 重选事件流并转换为触发回顶的 Flow<Unit> */
    fun scrollToTopFor(route: NavKey): Flow<Unit> = tabReselectionEvents.filter { it == route }.map { }

    /**
     * 重复点击当前 Tab → 若在子栈则重置到根部，若已在根部则派发重选回顶事件；
     * 点击其他 Tab → 记入顶层历史并切换；
     * 其余 key → 以 single-top 方式压入当前 Tab 子栈
     */
    fun navigateTo(key: NavKey) {
        when (key) {
            currentTopLevelKey -> {
                if (currentSubStack.size > 1) {
                    clearSubStack()
                } else {
                    _tabReselectionEvents.tryEmit(key)
                }
            }
            in topLevelKeys -> goToTopLevel(key)
            else -> goToKey(key)
        }
    }

    fun goBack() {
        when (currentKey) {
            // 起始 Tab 根部是应用出口，NavDisplay 在无可弹出条目时不会回调 onBack
            startRoute -> error("You cannot go back from the start route")
            // 已在当前 Tab 根部：沿顶层历史回退到上一个 Tab
            currentTopLevelKey -> topLevelStack.removeLastOrNull()
            else -> currentSubStack.removeLastOrNull()
        }
    }

    private fun goToKey(key: NavKey) {
        // 所有路由必须实现 BgmRoute（sealed 层级），入栈语义 when 因此为编译期穷尽匹配：
        // 新增路由类型必须在此显式声明层级语义，否则编译不过（杜绝静默落入 push 兜底分支）
        val route =
            checkNotNull(key as? BgmRoute) {
                "Navigation keys must implement BgmRoute (see BgmRoutes.kt): $key"
            }
        currentSubStack.apply {
            when (route) {
                is SubjectDetailRoute -> {
                    // 当从列表选择条目详情时（尤其是分栏模式下左右双栏同屏展示），
                    // 替换掉当前栈中已有的条目详情或详情子层级（条目详情、关联条目、分集讨论、标签专题），
                    // 避免用户在列表连续点击多个条目时在栈内无限堆叠，
                    // 保证返回时直接回到当前列表/占位页，而非倒退返回上一个条目。
                    removeAll {
                        it is SubjectDetailRoute ||
                            it is LinkedSubjectRoute ||
                            it is EpisodeDetailRoute ||
                            it is TagSubjectsRoute
                    }
                }
                is SearchRoute, is UserCollectionsRoute, is AgentChatRoute -> {
                    // 进入新的列表二级页面时，清理先前残留的详情层级；
                    // 同类二级页（不同 query/type 的搜索、收藏）按层级语义替换而非堆叠，
                    // 避免返回时倒退经过过期的旧搜索结果
                    removeAll {
                        it is SubjectDetailRoute ||
                            it is LinkedSubjectRoute ||
                            it is EpisodeDetailRoute ||
                            it is TagSubjectsRoute
                    }
                    removeAll { it::class == key::class }
                }
                // 钻取链层级：关联条目、分集讨论、标签专题允许逐层压栈（single-top 去重相同 key）
                is LinkedSubjectRoute, is EpisodeDetailRoute, is TagSubjectsRoute -> remove(key)
                // 顶层 Tab 根永远是子栈首元素，不允许作为子页入栈（走 navigateTo 的顶层分支）
                is ScheduleRoute, is ExploreRoute, is UserRoute ->
                    error("Top-level route cannot be pushed onto a sub stack: $key")
            }
            add(key)
        }
    }

    private fun goToTopLevel(key: NavKey) {
        topLevelStack.apply {
            if (key == startRoute) {
                // 回到起始 Tab 时清空顶层历史，使其重新成为唯一出口
                clear()
            } else {
                remove(key)
            }
            add(key)
        }
    }

    private fun clearSubStack() {
        currentSubStack.apply {
            if (size > 1) subList(1, size).clear()
        }
    }

    /**
     * 将导航状态转换为带装饰器的条目列表供 [androidx.navigation3.ui.NavDisplay] 渲染：
     * SaveableStateHolder 保存各条目的界面状态，ViewModelStore 让每个条目拥有独立的
     * ViewModel 作用域（feature 的 ViewModel 应经 entry 内的 viewModel() 获取而非 Activity 级）；
     * 起始 Tab 的条目始终在列（exit through home），其余 Tab 的栈状态仍被保留，只是不参与渲染。
     */
    @Composable
    fun toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val decorators =
            remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
                listOf(saveableStateHolderDecorator, viewModelStoreDecorator)
            }

        val decoratedEntries =
            subStacks.mapValues { (_, stack) ->
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = decorators,
                    entryProvider = entryProvider,
                )
            }

        return remember(topLevelStack.toList(), decoratedEntries) {
            topLevelStack
                .flatMap { decoratedEntries[it] ?: emptyList() }
        }
    }
}
