package com.infinitezerone.minibgm.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.ui.component.BgmDetailPlaceholder

/**
 * 封装 Material 3 Adaptive Navigation 3 的自适应分栏策略及 Pane 角色元数据构建辅助函数。
 */

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberBgmPaneDirective(): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    return remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).let { directive ->
            directive.copy(
                maxHorizontalPartitions =
                    if (shouldUseSplitPane(windowAdaptiveInfo.windowSizeClass.minWidthDp)) {
                        directive.maxHorizontalPartitions
                    } else {
                        1
                    },
                horizontalPartitionSpacerSize = 0.dp,
                defaultPanePreferredWidth =
                    preferredPaneWidthFor(
                        minWidthDp = windowAdaptiveInfo.windowSizeClass.minWidthDp,
                        fallback = directive.defaultPanePreferredWidth,
                    ),
            )
        }
    }
}

/**
 * 分栏必须给列表和详情都留下可用宽度。80dp 导航轨道之后，840dp 窗口只剩 760dp，
 * 再拆成左右两栏只会得到两个窄条；1200dp 起才是这张应用里真正成立的分栏下限。
 */
internal fun shouldUseSplitPane(minWidthDp: Int): Boolean = minWidthDp >= 1200

internal fun preferredPaneWidthFor(
    minWidthDp: Int,
    fallback: Dp,
): Dp =
    when {
        minWidthDp >= WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND -> 420.dp
        minWidthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND -> 480.dp
        else -> fallback
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val PaneScaffoldDirective.isSplitLayout: Boolean
    get() = maxHorizontalPartitions > 1

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberBgmListDetailStrategy(directive: PaneScaffoldDirective = rememberBgmPaneDirective()): ListDetailSceneStrategy<NavKey> =
    rememberListDetailSceneStrategy(
        directive = directive,
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
    )

/** 列表 Pane 元数据；右侧未选择条目时默认展示作品占位页 [BgmDetailPlaceholder] */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun bgmListPane(detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = { BgmDetailPlaceholder() }): Map<String, Any> =
    ListDetailSceneStrategy.listPane(detailPlaceholder = detailPlaceholder)

/**
 * 只有放送时间表是天然的时间线 + 详情结构；探索、搜索、我的都是单栏信息流。
 * 硬套 list-detail 会在右侧留下一个没有内容职责的空面板。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun bgmTopLevelPaneMetadata(
    route: NavKey,
    detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit,
): Map<String, Any> =
    if (route is ScheduleRoute) {
        bgmListPane(detailPlaceholder)
    } else {
        emptyMap()
    }

/** 详情 Pane 元数据 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun bgmDetailPane(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

/** 额外/三级扩展 Pane 元数据（如分集全屏讨论、关联作品） */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun bgmExtraPane(): Map<String, Any> = ListDetailSceneStrategy.extraPane()
