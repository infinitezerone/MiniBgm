package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 骨架屏配置状态，用于在多个骨架子节点间同步扫光动画进度。
 */
@Stable
class SkeletonState(
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    durationMillis: Int = 1500,
    shimmerWidth: Float = 800f,
    val shimmerRatio: Float? = null,
    baseColor: Color = Color.Unspecified,
    highlightColor: Color = Color.Unspecified,
    val progressState: State<Float>? = null,
) {
    var enabled by mutableStateOf(enabled)
    var shimmerEnabled by mutableStateOf(shimmerEnabled)
    var angleDegrees by mutableFloatStateOf(angleDegrees)
    var durationMillis by mutableStateOf(durationMillis)
    var shimmerWidth by mutableStateOf(shimmerWidth)
    var baseColor by mutableStateOf(baseColor)
    var highlightColor by mutableStateOf(highlightColor)
}

/**
 * 记住并提供统一的 [SkeletonState]，自动驱动流光扫光循环。
 * 默认自适应 Material 3 动态色彩与暗色模式。
 */
@Composable
fun rememberSkeletonState(
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    durationMillis: Int = 1500,
    shimmerWidth: Float = 800f,
    shimmerRatio: Float? = null,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceBright,
): SkeletonState {
    val transition = rememberInfiniteTransition(label = "skeleton_state")
    val progress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "skeleton_progress",
        )
    val state =
        remember {
            SkeletonState(
                enabled = enabled,
                shimmerEnabled = shimmerEnabled,
                angleDegrees = angleDegrees,
                durationMillis = durationMillis,
                shimmerWidth = shimmerWidth,
                shimmerRatio = shimmerRatio,
                baseColor = baseColor,
                highlightColor = highlightColor,
                progressState = progress,
            )
        }
    SideEffect {
        state.enabled = enabled
        state.shimmerEnabled = shimmerEnabled
        state.angleDegrees = angleDegrees
        state.durationMillis = durationMillis
        state.shimmerWidth = shimmerWidth
        state.baseColor = baseColor
        state.highlightColor = highlightColor
    }
    return state
}

/**
 * 基于 Compose [Modifier.Node] 的骨架流光修饰符（绑定共享 [SkeletonState]）。
 * 纯 Draw 阶段渲染，0 重组开销，支持全屏统一扫光。
 */
@Composable
fun Modifier.skeletonNode(
    state: SkeletonState,
    shape: Shape = RoundedCornerShape(4.dp),
    shimmerWidth: Float? = null,
    shimmerRatio: Float? = null,
    useRootCoordinates: Boolean = true,
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
): Modifier {
    if (!state.enabled) return this
    val minSizeModifier =
        if (minWidth != Dp.Unspecified || minHeight != Dp.Unspecified) {
            Modifier.defaultMinSize(minWidth = minWidth, minHeight = minHeight)
        } else {
            Modifier
        }
    val fallbackBase = MaterialTheme.colorScheme.surfaceContainerHighest
    val fallbackHighlight = MaterialTheme.colorScheme.surfaceBright
    val resolvedBase = if (state.baseColor != Color.Unspecified) state.baseColor else fallbackBase
    val resolvedHighlight =
        if (state.highlightColor != Color.Unspecified) state.highlightColor else fallbackHighlight

    return this
        .then(minSizeModifier)
        .then(
            SkeletonElement(
                shimmerEnabled = state.shimmerEnabled,
                angleDegrees = state.angleDegrees,
                shape = shape,
                baseColor = resolvedBase,
                highlightColor = resolvedHighlight,
                durationMillis = state.durationMillis,
                shimmerWidth = shimmerWidth ?: state.shimmerWidth,
                shimmerRatio = shimmerRatio ?: state.shimmerRatio,
                progressState = state.progressState,
                useRootCoordinates = useRootCoordinates,
            ),
        )
}

/**
 * 基于 Compose [Modifier.Node] 的独立骨架流光修饰符（自动适配主题色）。
 */
@Composable
fun Modifier.skeletonNode(
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    shape: Shape = RoundedCornerShape(4.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceBright,
    durationMillis: Int = 1500,
    shimmerWidth: Float = 800f,
    shimmerRatio: Float? = null,
    useRootCoordinates: Boolean = true,
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
): Modifier {
    if (!enabled) return this
    val minSizeModifier =
        if (minWidth != Dp.Unspecified || minHeight != Dp.Unspecified) {
            Modifier.defaultMinSize(minWidth = minWidth, minHeight = minHeight)
        } else {
            Modifier
        }
    return this
        .then(minSizeModifier)
        .then(
            SkeletonElement(
                shimmerEnabled = shimmerEnabled,
                angleDegrees = angleDegrees,
                shape = shape,
                baseColor = baseColor,
                highlightColor = highlightColor,
                durationMillis = durationMillis,
                shimmerWidth = shimmerWidth,
                shimmerRatio = shimmerRatio,
                useRootCoordinates = useRootCoordinates,
            ),
        )
}

/**
 * 非 Composable 上下文使用的骨架流光修饰符（需显式传入颜色）。
 */
fun Modifier.skeletonNode(
    baseColor: Color,
    highlightColor: Color,
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    shape: Shape = RoundedCornerShape(4.dp),
    durationMillis: Int = 1500,
    shimmerWidth: Float = 800f,
    shimmerRatio: Float? = null,
    useRootCoordinates: Boolean = true,
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
): Modifier {
    if (!enabled) return this
    val minSizeModifier =
        if (minWidth != Dp.Unspecified || minHeight != Dp.Unspecified) {
            Modifier.defaultMinSize(minWidth = minWidth, minHeight = minHeight)
        } else {
            Modifier
        }
    return this
        .then(minSizeModifier)
        .then(
            SkeletonElement(
                shimmerEnabled = shimmerEnabled,
                angleDegrees = angleDegrees,
                shape = shape,
                baseColor = baseColor,
                highlightColor = highlightColor,
                durationMillis = durationMillis,
                shimmerWidth = shimmerWidth,
                shimmerRatio = shimmerRatio,
                useRootCoordinates = useRootCoordinates,
            ),
        )
}

/**
 * 极简别名修饰符：[skeletonNode] 的便捷封装。
 */
@Composable
fun Modifier.bgmSkeleton(
    shape: Shape = RoundedCornerShape(4.dp),
    state: SkeletonState? = null,
    useRootCoordinates: Boolean = true,
): Modifier =
    if (state != null) {
        skeletonNode(
            state = state,
            shape = shape,
            useRootCoordinates = useRootCoordinates,
        )
    } else {
        skeletonNode(
            shape = shape,
            useRootCoordinates = useRootCoordinates,
        )
    }

/**
 * 独立的骨架占位方块组件。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    state: SkeletonState? = null,
    useRootCoordinates: Boolean = true,
) {
    Box(
        modifier =
            modifier.bgmSkeleton(
                shape = shape,
                state = state,
                useRootCoordinates = useRootCoordinates,
            ),
    )
}

private data class SkeletonElement(
    val shimmerEnabled: Boolean,
    val angleDegrees: Float,
    val shape: Shape,
    val baseColor: Color,
    val highlightColor: Color,
    val durationMillis: Int,
    val shimmerWidth: Float,
    val shimmerRatio: Float? = null,
    val progressState: State<Float>? = null,
    val useRootCoordinates: Boolean = false,
) : ModifierNodeElement<SkeletonNode>() {
    override fun create(): SkeletonNode =
        SkeletonNode(
            shimmerEnabled,
            angleDegrees,
            shape,
            baseColor,
            highlightColor,
            durationMillis,
            shimmerWidth,
            shimmerRatio,
            progressState,
            useRootCoordinates,
        )

    override fun update(node: SkeletonNode) {
        node.shimmerEnabled = shimmerEnabled
        node.angleDegrees = angleDegrees
        node.shape = shape
        node.baseColor = baseColor
        node.highlightColor = highlightColor
        node.durationMillis = durationMillis
        node.shimmerWidth = shimmerWidth
        node.shimmerRatio = shimmerRatio
        node.progressState = progressState
        node.useRootCoordinates = useRootCoordinates
        node.onParamsChanged()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "skeletonNode"
    }
}

private class SkeletonNode(
    var shimmerEnabled: Boolean,
    var angleDegrees: Float,
    var shape: Shape,
    var baseColor: Color,
    var highlightColor: Color,
    var durationMillis: Int,
    var shimmerWidth: Float,
    var shimmerRatio: Float?,
    var progressState: State<Float>?,
    var useRootCoordinates: Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode {
    private val progress = Animatable(0f)
    private var job: Job? = null
    private var coordinates: LayoutCoordinates? = null

    override fun onAttach() {
        onParamsChanged()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun onParamsChanged() {
        if (progressState != null) {
            job?.cancel()
            job = null
            invalidateDraw()
            return
        }
        if (!shimmerEnabled) {
            job?.cancel()
            job = null
            invalidateDraw()
            return
        }
        restartAnimation()
    }

    private fun restartAnimation() {
        job?.cancel()
        job =
            coroutineScope.launch {
                try {
                    while (isActive) {
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis, easing = LinearEasing),
                        )
                    }
                } catch (_: CancellationException) {
                }
            }
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!shimmerEnabled) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline = outline, color = baseColor)
            return
        }

        val rad = angleDegrees * (PI.toFloat() / 180f)
        val dx = cos(rad)
        val dy = sin(rad)

        val progressValue = progressState?.value ?: progress.value
        val coords = coordinates
        val isCoordsAttached = coords != null && coords.isAttached
        val rootCoordinates = if (useRootCoordinates && isCoordsAttached) coords.findRootCoordinates() else null
        val rootSize = rootCoordinates?.takeIf { it.isAttached }?.size
        val originInRoot = if (isCoordsAttached) coords.positionInRoot() else null
        val maxDim =
            if (rootSize != null) {
                sqrt(rootSize.width.toFloat() * rootSize.width + rootSize.height.toFloat() * rootSize.height)
            } else {
                sqrt(size.width * size.width + size.height * size.height)
            }
        val start = -maxDim
        val end = maxDim
        val t = start + (end - start) * progressValue

        val originOffset =
            if (originInRoot != null && rootSize != null) {
                Offset(originInRoot.x, originInRoot.y)
            } else {
                Offset.Zero
            }

        val ratio = shimmerRatio
        val actualShimmerWidth =
            when {
                ratio != null -> maxDim * ratio
                !useRootCoordinates -> maxDim * 0.5f
                else -> shimmerWidth
            }

        val startPoint = Offset(t * dx, t * dy) - originOffset
        val endPoint =
            Offset(
                startPoint.x + actualShimmerWidth * dx,
                startPoint.y + actualShimmerWidth * dy,
            )

        val brush =
            Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = startPoint,
                end = endPoint,
            )
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline = outline, brush = brush)
        invalidateDraw()
    }
}
