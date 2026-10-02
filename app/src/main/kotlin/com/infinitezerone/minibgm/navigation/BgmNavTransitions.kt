package com.infinitezerone.minibgm.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
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
 * 顶层 Tab 转场元数据（Material「Fade Through」模式）：
 * 顶层目的地之间没有方向语义（不存在"返回"关系），因此不用 Shared Axis 的横向位移——
 * 旧页快速淡出并轻微收缩，新页从 92% 缩放淡入，读作"内容切换"而非"页面移动"。
 * 经 Navigation 3 官方元数据机制由各顶层 Entry 独立声明。
 */
val bgmTopLevelTransitionMetadata: Map<String, Any> =
    NavDisplay.transitionSpec {
        (
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spatialFloatSpec,
            ) + fadeIn(animationSpec = effectsSpec)
        ).togetherWith(
            scaleOut(
                targetScale = 0.96f,
                animationSpec = spatialFloatSpec,
            ) + fadeOut(animationSpec = effectsSpec),
        )
    } +
        NavDisplay.popTransitionSpec {
            (
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spatialFloatSpec,
                ) + fadeIn(animationSpec = effectsSpec)
            ).togetherWith(
                scaleOut(
                    targetScale = 0.96f,
                    animationSpec = spatialFloatSpec,
                ) + fadeOut(animationSpec = effectsSpec),
            )
        } +
        NavDisplay.predictivePopTransitionSpec {
            (
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spatialFloatSpec,
                ) + fadeIn(animationSpec = effectsSpec)
            ).togetherWith(
                scaleOut(
                    targetScale = 0.96f,
                    animationSpec = spatialFloatSpec,
                ) + fadeOut(animationSpec = effectsSpec),
            )
        }

/**
 * 推进二级/三级页面转场（Material「Shared Axis X」前进方向）：
 * 新页面从右侧 1/3 处淡入滑入，旧页面向左视差退避 1/4 并淡出至 50% 透明度——
 * 不做完全淡出，保证过渡全程旧页面内容可见，根除滑动尾期露出黑屏背景的闪烁假死。
 * 相比此前"全宽滑入"，1/3 位移更接近 Shared Axis 的标准幅度，不会产生满屏横扫的重体感。
 */
val bgmNavTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 3 },
            animationSpec = spatialSpec,
        ) +
            fadeIn(animationSpec = effectsSpec) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = spatialSpec,
            ) +
            fadeOut(
                targetAlpha = 0.5f,
                animationSpec = effectsSpec,
            )
    }

/**
 * 返回上一级页面转场（Shared Axis X 后退方向，与前进严格镜像）：
 * 上级页面从左侧 1/4 视差位淡入滑回，当前页面滑出右侧 1/3 处并淡出至 50%，
 * 全程不露出转场底色。
 */
val bgmNavPopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 4 },
            animationSpec = spatialSpec,
        ) +
            fadeIn(animationSpec = effectsSpec) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 3 },
                animationSpec = spatialSpec,
            ) +
            fadeOut(
                targetAlpha = 0.5f,
                animationSpec = effectsSpec,
            )
    }

/**
 * 预测性返回手势转场（Material Predictive Back 规格）：
 * 退出页面在手势拖拽方向上滑出的同时收缩至 94% 并淡出（官方"页面缩小退场"范式），
 * 下层页面按手势边缘从对应侧的 1/4 视差位淡入滑回。
 */
val bgmNavPredictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { swipeEdge ->
        val isFromRight = swipeEdge == NavigationEvent.EDGE_RIGHT
        slideInHorizontally(
            initialOffsetX = { fullWidth -> if (isFromRight) fullWidth / 4 else -fullWidth / 4 },
            animationSpec = spatialSpec,
        ) +
            fadeIn(animationSpec = effectsSpec) togetherWith
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> if (isFromRight) -fullWidth / 3 else fullWidth / 3 },
                animationSpec = spatialSpec,
            ) +
            scaleOut(
                targetScale = 0.94f,
                animationSpec = spatialFloatSpec,
            ) +
            fadeOut(
                targetAlpha = 0.5f,
                animationSpec = effectsSpec,
            )
    }

/**
 * 判断指定对象（路由或 Key）是否为顶层 Tab 路由（[TopLevelRoute]）。
 */
internal fun isTopLevelRoute(route: Any?): Boolean = route is TopLevelRoute
