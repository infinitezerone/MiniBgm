package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.infinitezerone.minibgm.core.navigation.TopLevelRoute

/**
 * Material 3 Expressive 官方物理弹性动效规格：
 * - 空间位移（页面滑动、缩放）：defaultSpatialSpec 弹簧规范（dampingRatio = 0.8f, stiffness = 380f）；
 * - 视效透明度：defaultEffectsSpec 规范（dampingRatio = 1.0f, stiffness = 1600f）。
 */
private val expressiveSpatialSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 380f)
private val expressiveEffectsSpec = spring<Float>(dampingRatio = 1.0f, stiffness = 1600f)

/**
 * 顶层 Tab 专用转场元数据：
 * 显式声明顶层 Tab 进出使用瞬时转场（无位移动效），
 * 通过 Navigation 3 官方元数据机制由各顶层 Entry 独立声明，
 * 不再在全局 transitionSpec 中判断 key 或解析字符串。
 */
val bgmTopLevelTransitionMetadata: Map<String, Any> =
    NavDisplay.transitionSpec {
        EnterTransition.None togetherWith ExitTransition.None
    } +
        NavDisplay.popTransitionSpec {
            EnterTransition.None togetherWith ExitTransition.None
        } +
        NavDisplay.predictivePopTransitionSpec {
            EnterTransition.None togetherWith ExitTransition.None
        }

/**
 * 推进二级/三级页面转场规范（NavDisplay 默认 forward transition）：
 * 新页面由右侧滑入并淡入，底层旧页面轻微向左视差退避（-1/6 宽度），
 * 绝不全透明淡出，彻底根除滑动尾期露出黑屏背景的闪烁假死缺陷。
 */
val bgmNavTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = expressiveSpatialSpec,
        ) +
            fadeIn(
                animationSpec = expressiveEffectsSpec,
            ) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 6 },
                animationSpec = expressiveSpatialSpec,
            )
    }

/**
 * 返回上一级页面转场规范（NavDisplay 默认 pop transition）：
 * 上级页面从左侧视差位平滑滑回，当前页面完整滑出右侧屏幕边缘，
 * 不提前全淡出，彻底解决滑到 2/3 处突兀凭空消失的穿帮掉帧感。
 */
val bgmNavPopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 6 },
            animationSpec = expressiveSpatialSpec,
        ) +
            fadeIn(
                animationSpec = expressiveEffectsSpec,
            ) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = expressiveSpatialSpec,
            )
    }

/**
 * 预测性返回手势转场规范（NavDisplay 默认 predictive pop transition）：
 * 自适应手势边缘（左滑/右滑），采用官方 Expressive 弹性物理规格。
 */
val bgmNavPredictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { swipeEdge ->
        val isFromRight = swipeEdge == NavigationEvent.EDGE_RIGHT
        slideInHorizontally(
            initialOffsetX = { fullWidth -> if (isFromRight) fullWidth / 6 else -fullWidth / 6 },
            animationSpec = expressiveSpatialSpec,
        ) +
            fadeIn(
                animationSpec = expressiveEffectsSpec,
            ) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (isFromRight) -fullWidth else fullWidth },
                animationSpec = expressiveSpatialSpec,
            )
    }

/**
 * 判断指定对象（路由或 Key）是否为顶层 Tab 路由（[TopLevelRoute]）。
 */
internal fun isTopLevelRoute(route: Any?): Boolean = route is TopLevelRoute
