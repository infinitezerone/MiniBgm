package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 打卡弹性按压动效的规格参数。 */
object BounceDefaults {
    /** 点击瞬间的最小缩放值（1f 为原始尺寸）。 */
    const val PRESSED_SCALE = 0.92f

    /** 按下缩小段的时长（毫秒），足够短以保证动效只发生在点击瞬间。 */
    const val PRESS_DURATION_MILLIS = 80

    /** 回弹段的弹簧阻尼比：中等弹性，克制不夸张。 */
    const val DAMPING_RATIO = Spring.DampingRatioMediumBouncy

    /** 回弹段的弹簧刚度：较低刚度，回弹柔和。 */
    const val STIFFNESS = Spring.StiffnessMediumLow

    /** 按下缩小段：短促匀速缩小。 */
    internal val pressSpec: TweenSpec<Float> =
        tween(durationMillis = PRESS_DURATION_MILLIS, easing = LinearEasing)

    /** 回弹段：medium-bouncy 低刚度弹簧回弹到原始尺寸。 */
    internal val releaseSpec: SpringSpec<Float> =
        spring(dampingRatio = DAMPING_RATIO, stiffness = STIFFNESS)
}

/**
 * 驱动 [Modifier.bounceOnClick] 弹性缩放动画的状态。
 *
 * 通过 [rememberBounceOnClick] 在组合中创建；[bounce] 在点击回调里触发即可。
 */
@Stable
class BounceOnClickState internal constructor(
    val pressedScale: Float,
    private val scope: CoroutineScope,
) {
    internal val scale = Animatable(1f)

    /** 当前缩放值（1f 为原始尺寸）。 */
    val currentScale: Float get() = scale.value

    /**
     * 触发一次点击弹性动画：先短促缩小到 [pressedScale]，再以 medium-bouncy 弹簧回弹到原始尺寸。
     * 动画进行中重复调用会取消上一次动画并以当前缩放为起点重新执行，不会跳变。
     */
    fun bounce() {
        scope.launch {
            scale.animateTo(pressedScale, BounceDefaults.pressSpec)
            scale.animateTo(1f, BounceDefaults.releaseSpec)
        }
    }
}

/**
 * 创建并记住一个 [BounceOnClickState]。
 *
 * @param pressedScale 点击瞬间的最小缩放值，默认 [BounceDefaults.PRESSED_SCALE]。
 */
@Composable
fun rememberBounceOnClick(pressedScale: Float = BounceDefaults.PRESSED_SCALE): BounceOnClickState {
    val scope = rememberCoroutineScope()
    return remember(pressedScale, scope) { BounceOnClickState(pressedScale, scope) }
}

/**
 * 对内容施加由 [state] 驱动的点击弹性缩放（scale 弹性形变）。
 *
 * 纯视觉修饰：基于 graphicsLayer 的绘制层缩放，不参与测量与布局，也不增删任何语义，
 * 因此不影响布局与无障碍。点击回调由调用方负责（按钮的 onClick 中调用 [BounceOnClickState.bounce]，
 * 或直接使用自包含的 [bounceClickable]）。
 */
fun Modifier.bounceOnClick(state: BounceOnClickState): Modifier =
    graphicsLayer {
        val scale = state.scale.value
        scaleX = scale
        scaleY = scale
    }

/**
 * 自包含的弹性点击：点击瞬间触发弹性缩放并组合 [clickable]。
 *
 * 语义（role / onClickLabel）原样透传，不影响无障碍。
 */
fun Modifier.bounceClickable(
    state: BounceOnClickState,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier =
    this
        .bounceOnClick(state)
        .clickable(enabled = enabled, onClickLabel = onClickLabel, role = role) {
            state.bounce()
            onClick()
        }
