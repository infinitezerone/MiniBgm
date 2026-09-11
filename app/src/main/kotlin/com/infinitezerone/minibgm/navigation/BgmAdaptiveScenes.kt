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
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.ui.component.BgmDetailPlaceholder

/**
 * 封装 Material 3 Adaptive Navigation 3 的自适应分栏策略及 Pane 角色元数据构建辅助函数。
 */

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberBgmPaneDirective(): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    return remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
            horizontalPartitionSpacerSize = 0.dp,
        )
    }
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

/** 详情 Pane 元数据 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun bgmDetailPane(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

/** 额外/三级扩展 Pane 元数据（如分集全屏讨论、关联作品） */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun bgmExtraPane(): Map<String, Any> = ListDetailSceneStrategy.extraPane()
