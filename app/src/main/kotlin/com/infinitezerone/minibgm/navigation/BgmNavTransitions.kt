package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
 * Material Motion 转场规格（对齐 M3 Expressive 官方物理参数）：
 * - 空间位移（页面滑动、缩放）：spatial spring（dampingRatio = 0.8f, stiffness = 380f）；
 * - 视效透明度（淡入淡出）：effects spring（dampingRatio = 1.0f, stiffness = 1600f），
 *   两层分离是 Expressive 的核心纪律——位置有弹性，透明度始终干脆。
 */
private val spatialSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 380f)
private val spatialFloatSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
private val effectsSpec = spring<Float>(dampingRatio = 1.0f, stiffness = 1600f)

/**
 * Material 3 Fade Through 顶层 Tab 时间轴规格：
 * 退出页 90ms 快速淡出，进入页 90ms 延迟后 210ms 平滑微放大淡入。
 * 确定性的时间轴杜绝物理弹簧尾部亚像素振荡，在数据异步加载时保障 120Hz 绝对平滑。
 */
private val topLevelFadeOutSpec = tween<Float>(durationMillis = 90, easing = FastOutLinearInEasing)
private val topLevelFadeInSpec = tween<Float>(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing)
private val topLevelScaleInSpec = tween<Float>(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing)

/**
 * 顶层 Tab 转场元数据（Material「Fade Through」模式）：
 * 顶层目的地之间没有方向语义，采用官方标准的轻量快速淡入淡出，
 * 避免全屏大比例变形与数据加载重组互相阻塞，经 Navigation 3 官方元数据机制由各顶层 Entry 独立声明。
 */
val bgmTopLevelTransitionMetadata: Map<String, Any> =
    NavDisplay.transitionSpec {
        (
            scaleIn(
                initialScale = 0.98f,
                animationSpec = topLevelScaleInSpec,
            ) + fadeIn(animationSpec = topLevelFadeInSpec)
        ).togetherWith(
            fadeOut(animationSpec = topLevelFadeOutSpec),
        )
    } +
        NavDisplay.popTransitionSpec {
            (
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = topLevelScaleInSpec,
                ) + fadeIn(animationSpec = topLevelFadeInSpec)
            ).togetherWith(
                fadeOut(animationSpec = topLevelFadeOutSpec),
            )
        } +
        NavDisplay.predictivePopTransitionSpec {
            (
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = topLevelScaleInSpec,
                ) + fadeIn(animationSpec = topLevelFadeInSpec)
            ).togetherWith(
                fadeOut(animationSpec = topLevelFadeOutSpec),
            )
        }

/**
 * 推进二级/三级页面转场（Material 3「Forward and Backward」前进方向）：
 * 新页面由屏幕右侧边缘（fullWidth）完整实体推入，
 * 旧页面向左视差退避 1/4 同时快速完全淡出到 0f，彻底消灭新页面到位后底层残留 60% 半透明悬停的怪异现象。
 */
val bgmNavTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spatialSpec,
        ) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = spatialSpec,
            ) +
            fadeOut(
                targetAlpha = 0.0f,
                animationSpec = effectsSpec,
            )
    }

/**
 * 返回上一级页面转场（Material 3「Forward and Backward」后退方向，与前进严格对称）：
 * 上级页面从左侧 1/4 视差位滑回，伴随从 0f 平滑淡入复原；
 * 当前页面完整平滑滑出屏幕右侧边缘（fullWidth），下层页面自然显露，进出动作严格对称且无残影。
 */
val bgmNavPopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 4 },
            animationSpec = spatialSpec,
        ) +
            fadeIn(
                initialAlpha = 0.0f,
                animationSpec = effectsSpec,
            ) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = spatialSpec,
            )
    }

/**
 * 预测性返回手势转场（Material 3 Expressive Predictive Back 规范）：
 * 退出页面随手势在对应方向完整滑出屏幕边缘，同时平滑微缩放至 90% 并完全淡出；
 * 下层页面根据手势边缘从 1/4 视差位滑回，伴随微缩放复原与平滑淡入。
 */
val bgmNavPredictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { swipeEdge ->
        val isFromRight = swipeEdge == NavigationEvent.EDGE_RIGHT
        slideInHorizontally(
            initialOffsetX = { fullWidth -> if (isFromRight) fullWidth / 4 else -fullWidth / 4 },
            animationSpec = spatialSpec,
        ) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = spatialFloatSpec,
            ) +
            fadeIn(animationSpec = effectsSpec) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (isFromRight) -fullWidth else fullWidth },
                animationSpec = spatialSpec,
            ) +
            scaleOut(
                targetScale = 0.90f,
                animationSpec = spatialFloatSpec,
            ) +
            fadeOut(
                animationSpec = effectsSpec,
            )
    }

/**
 * 判断指定对象（路由或 Key）是否为顶层 Tab 路由（[TopLevelRoute]）。
 */
internal fun isTopLevelRoute(route: Any?): Boolean = route is TopLevelRoute
