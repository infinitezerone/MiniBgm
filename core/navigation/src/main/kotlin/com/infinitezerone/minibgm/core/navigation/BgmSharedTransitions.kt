package com.infinitezerone.minibgm.core.navigation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 全局共享元素过渡作用域（由外层导航容器通过 CompositionLocal 注入）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/**
 * 与全局页面横向滑动推进时长（300ms, FastOutSlowInEasing）严格同步的共享元素位移动画规格。
 * 避免默认 Spring 曲线（~500ms）在页面滑动已停止后仍在滞后漂移造成的拖拽感与卡顿感。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val BgmSharedBoundsTransform: BoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 300, easing = FastOutSlowInEasing)
    }

/**
 * 共享元素统一标识 Key
 */
object BgmSharedElementKeys {
    fun subjectCover(
        subjectId: Long,
        source: String = "",
    ): String? = if (source.isNotBlank()) "subject_cover_${source}_$subjectId" else null
}

/**
 * 为条目封面等共享元素提供统一且安全的 Modifier 扩展。
 *
 * 当且仅当外层导航提供了 [LocalSharedTransitionScope] 时激活共享过渡动画；
 * 在 Compose Preview 或单测环境下（未注入作用域），安全回退为空 Modifier，绝不产生副作用或异常。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bgmSharedElement(
    key: Any?,
    clipInOverlayDuringTransition: Shape? = null,
    boundsTransform: BoundsTransform = BgmSharedBoundsTransform,
): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current

    return with(sharedScope) {
        val state = rememberSharedContentState(key = key)
        if (clipInOverlayDuringTransition != null) {
            this@bgmSharedElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = animatedScope,
                boundsTransform = boundsTransform,
                clipInOverlayDuringTransition = OverlayClip(clipInOverlayDuringTransition),
            )
        } else {
            this@bgmSharedElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = animatedScope,
                boundsTransform = boundsTransform,
            )
        }
    }
}

/**
 * 检查当前 Nav3 导航进场动画是否仍在运行中。
 * 在动画进行中（例如 300ms 推进飞渡期间），可据此暂缓复杂子组件（如几十个分集网格、长文本展开等）的重型排版，
 * 避免在飞渡刚起飞的第 1 帧发生主线程大面积重组掉帧。
 *
 * 当不在导航过渡上下文（如单测或 Preview）中时，安全回退为 false。
 */
@Composable
fun isNavEntering(): Boolean {
    if (LocalSharedTransitionScope.current == null) return false
    val animatedScope = LocalNavAnimatedContentScope.current
    val transition = animatedScope.transition
    return transition.targetState == androidx.compose.animation.EnterExitState.Visible &&
        (transition.isRunning || transition.currentState != androidx.compose.animation.EnterExitState.Visible)
}
