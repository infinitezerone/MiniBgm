package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import com.infinitezerone.minibgm.core.navigation.ExploreRoute
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.core.navigation.UserRoute

/**
 * 推进页面转场规范：
 * - 顶层 Tab 间平滑淡入淡出（200ms/150ms），避免横向滑动导致底栏与内容视觉干扰；
 * - 推进二级/三级页面：新页面从右侧滑入伴随轻微淡入，旧页面轻微向左视差退避并淡出。
 */
val bgmNavTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        val fromKey = initialState.key
        val toKey = targetState.key
        if (isTopLevelRoute(fromKey) && isTopLevelRoute(toKey)) {
            fadeIn(
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            ) togetherWith
                fadeOut(
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                )
        } else {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) +
                fadeIn(
                    animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                ) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                ) +
                fadeOut(
                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                )
        }
    }

/**
 * 返回上一级页面转场规范：
 * - 顶层 Tab 间回退平滑淡入淡出；
 * - 普通页面返回：上级页面从左侧视差位滑回并淡入，当前页面向右滑出并淡出。
 */
val bgmNavPopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        val fromKey = initialState.key
        val toKey = targetState.key
        if (isTopLevelRoute(fromKey) && isTopLevelRoute(toKey)) {
            fadeIn(
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            ) togetherWith
                fadeOut(
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                )
        } else {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) +
                fadeIn(
                    animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                ) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                ) +
                fadeOut(
                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                )
        }
    }

/**
 * 预测性返回手势转场规范：
 * - 顶层 Tab 间返回淡入淡出；
 * - 二级/三级页面手势返回：自适应手势边缘（左滑/右滑），采用 Material 3 标准物理弹簧参数。
 */
val bgmNavPredictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { swipeEdge ->
        val fromKey = initialState.key
        val toKey = targetState.key
        if (isTopLevelRoute(fromKey) && isTopLevelRoute(toKey)) {
            fadeIn(
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            ) togetherWith
                fadeOut(
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
                )
        } else {
            val isFromRight = swipeEdge == NavigationEvent.EDGE_RIGHT
            slideInHorizontally(
                initialOffsetX = { fullWidth -> if (isFromRight) fullWidth / 4 else -fullWidth / 4 },
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600.0f),
            ) +
                fadeIn(
                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600.0f),
                ) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (isFromRight) -fullWidth else fullWidth },
                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600.0f),
                ) +
                fadeOut(
                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600.0f),
                )
        }
    }

/**
 * 判断指定 contentKey（可能经过 NavEntryDecorator 包装为 Pair）是否为顶层 Tab 路由。
 */
internal fun isTopLevelRoute(contentKey: Any?): Boolean {
    var route = contentKey
    while (route is Pair<*, *>) {
        route = route.second
    }
    return route is ScheduleRoute || route is ExploreRoute || route is UserRoute
}
