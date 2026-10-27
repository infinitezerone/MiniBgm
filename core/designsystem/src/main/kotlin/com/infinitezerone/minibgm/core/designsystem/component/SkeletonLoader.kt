package com.infinitezerone.minibgm.core.designsystem.component

import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Matrix
import android.graphics.Shader as AndroidShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.BgmMotion
import com.infinitezerone.minibgm.core.designsystem.theme.BgmOpacity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 骨架屏配置状态，用于在多个骨架子节点间同步扫光动画进度。
 */
@Stable
class SkeletonState(
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    durationMillis: Int = BgmMotion.skeletonMillis,
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
 * 局部骨架屏状态 CompositionLocal，供顶层骨架容器跨组件树隐式透传共享动画时钟，
 * 避免深层子组件未显式传参时退化为独立无协调的局部动画循环。
 */
val LocalSkeletonState = compositionLocalOf<SkeletonState?> { null }

/**
 * 记住并提供统一的 [SkeletonState]，自动驱动流光扫光循环。
 * 默认自适应 Material 3 动态色彩与暗色模式。
 */
@Composable
fun rememberSkeletonState(
    enabled: Boolean = true,
    shimmerEnabled: Boolean = true,
    angleDegrees: Float = 20f,
    durationMillis: Int = BgmMotion.skeletonMillis,
    shimmerWidth: Float = 800f,
    shimmerRatio: Float? = null,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    highlightColor: Color = Color.Unspecified,
): SkeletonState {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dynamicHighlight =
        if (highlightColor != Color.Unspecified) {
            highlightColor
        } else if (isDarkSurface) {
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = BgmOpacity.skeleton)
                .compositeOver(MaterialTheme.colorScheme.surfaceBright)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }

    val transition = rememberInfiniteTransition(label = "skeleton_state")
    val progress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = BgmMotion.standardTween(durationMillis),
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
                highlightColor = dynamicHighlight,
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
        state.highlightColor = dynamicHighlight
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fallbackBase = MaterialTheme.colorScheme.surfaceContainerHighest
    val fallbackHighlight =
        if (isDarkSurface) {
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = BgmOpacity.skeleton)
                .compositeOver(MaterialTheme.colorScheme.surfaceBright)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }
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
    highlightColor: Color = Color.Unspecified,
    durationMillis: Int = BgmMotion.skeletonMillis,
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val resolvedHighlight =
        if (highlightColor != Color.Unspecified) {
            highlightColor
        } else if (isDarkSurface) {
            MaterialTheme.colorScheme.onSurface
                .copy(alpha = BgmOpacity.skeleton)
                .compositeOver(MaterialTheme.colorScheme.surfaceBright)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }
    return this
        .then(minSizeModifier)
        .then(
            SkeletonElement(
                shimmerEnabled = shimmerEnabled,
                angleDegrees = angleDegrees,
                shape = shape,
                baseColor = baseColor,
                highlightColor = resolvedHighlight,
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
    durationMillis: Int = BgmMotion.skeletonMillis,
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
): Modifier {
    val effectiveState = state ?: LocalSkeletonState.current
    return if (effectiveState != null) {
        skeletonNode(
            state = effectiveState,
            shape = shape,
            useRootCoordinates = useRootCoordinates,
        )
    } else {
        skeletonNode(
            shape = shape,
            useRootCoordinates = useRootCoordinates,
        )
    }
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
    val effectiveState = state ?: LocalSkeletonState.current
    Box(
        modifier =
            modifier.bgmSkeleton(
                shape = shape,
                state = effectiveState,
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

    // 缓存根坐标与尺寸，避免每帧高频遍历布局树（findRootCoordinates / positionInRoot）
    private var cachedOriginInRoot: Offset? = null
    private var cachedRootSize: IntSize? = null

    // 缓存 Shape 几何轮廓，避免每帧重复分配 Outline.Rounded 产生 GC 抖动
    private var cachedOutline: Outline? = null
    private var cachedOutlineSize: Size = Size.Unspecified
    private var cachedOutlineLayoutDirection: LayoutDirection? = null
    private var cachedOutlineShape: Shape? = null

    // 预计算扫光角度三角函数，避免每帧重复调用 cos / sin
    private var dx: Float = 1f
    private var dy: Float = 0f

    // 缓存底层着色器与变换矩阵，通过 setLocalMatrix 平移扫光，彻底消除 120Hz 每帧创建销毁 SkLinearGradientShader 原生对象的巨大开销
    private val localMatrix = Matrix()
    private var cachedLinearGradient: AndroidLinearGradient? = null
    private var cachedShaderBrush: ShaderBrush? = null
    private var cachedShaderWidth: Float = -1f
    private var cachedShaderBaseColor: Color = Color.Unspecified
    private var cachedShaderHighlightColor: Color = Color.Unspecified

    private fun obtainShaderBrush(
        width: Float,
        base: Color,
        highlight: Color,
    ): ShaderBrush {
        val existingBrush = cachedShaderBrush
        val existingShader = cachedLinearGradient
        if (existingBrush != null &&
            existingShader != null &&
            cachedShaderWidth == width &&
            cachedShaderBaseColor == base &&
            cachedShaderHighlightColor == highlight
        ) {
            return existingBrush
        }

        val baseArgb = base.toArgb()
        val highlightArgb = highlight.toArgb()
        val colors = intArrayOf(baseArgb, baseArgb, highlightArgb, baseArgb, baseArgb)
        val positions = floatArrayOf(0.00f, 0.25f, 0.50f, 0.75f, 1.00f)

        val shader =
            AndroidLinearGradient(
                0f,
                0f,
                width * dx,
                width * dy,
                colors,
                positions,
                AndroidShader.TileMode.CLAMP,
            )
        val brush = ShaderBrush(shader)

        cachedLinearGradient = shader
        cachedShaderBrush = brush
        cachedShaderWidth = width
        cachedShaderBaseColor = base
        cachedShaderHighlightColor = highlight

        return brush
    }

    override fun onAttach() {
        onParamsChanged()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
        coordinates = null
        cachedOriginInRoot = null
        cachedRootSize = null
        cachedOutline = null
        cachedOutlineShape = null
        cachedLinearGradient = null
        cachedShaderBrush = null
        cachedShaderWidth = -1f
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        updateCoordinatesCache(coordinates)
    }

    private fun updateCoordinatesCache(coords: LayoutCoordinates) {
        if (useRootCoordinates && coords.isAttached) {
            val root = coords.findRootCoordinates()
            if (root.isAttached) {
                cachedRootSize = root.size
                cachedOriginInRoot = coords.positionInRoot()
            } else {
                cachedRootSize = null
                cachedOriginInRoot = null
            }
        } else {
            cachedRootSize = null
            cachedOriginInRoot = null
        }
    }

    fun onParamsChanged() {
        val currentRad = angleDegrees * (PI.toFloat() / 180f)
        dx = cos(currentRad)
        dy = sin(currentRad)
        if (cachedShaderBaseColor != baseColor || cachedShaderHighlightColor != highlightColor) {
            cachedLinearGradient = null
            cachedShaderBrush = null
            cachedShaderWidth = -1f
        }

        if (cachedOutlineShape != shape) {
            cachedOutline = null
            cachedOutlineShape = null
        }
        coordinates?.let { updateCoordinatesCache(it) }

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
                            animationSpec = BgmMotion.standardTween(durationMillis),
                        )
                    }
                } catch (_: CancellationException) {
                }
            }
    }

    private fun ContentDrawScope.obtainOutline(): Outline {
        val currentSize = size
        val currentDirection = layoutDirection
        val currentShape = shape
        var outline = cachedOutline
        if (outline == null ||
            cachedOutlineSize != currentSize ||
            cachedOutlineLayoutDirection != currentDirection ||
            cachedOutlineShape != currentShape
        ) {
            outline = currentShape.createOutline(currentSize, currentDirection, this)
            cachedOutline = outline
            cachedOutlineSize = currentSize
            cachedOutlineLayoutDirection = currentDirection
            cachedOutlineShape = currentShape
        }
        return outline
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        val outline = obtainOutline()

        if (!shimmerEnabled) {
            drawOutline(outline = outline, color = baseColor)
            return
        }

        val progressValue = progressState?.value ?: progress.value
        val coords = coordinates
        val isCoordsAttached = coords != null && coords.isAttached

        // 优先使用 onPlaced 阶段计算的根坐标缓存；若缺失且 attached 则单次懒加载
        val originInRoot =
            cachedOriginInRoot ?: if (useRootCoordinates && isCoordsAttached) {
                val root = coords.findRootCoordinates()
                if (root.isAttached) {
                    cachedRootSize = root.size
                    coords.positionInRoot().also { cachedOriginInRoot = it }
                } else {
                    null
                }
            } else {
                null
            }
        val rootSize = cachedRootSize

        val refWidth = rootSize?.width?.toFloat() ?: size.width
        val refHeight = rootSize?.height?.toFloat() ?: size.height

        // 光束正向投影的最大物理长度：W * cosθ + H * sinθ
        val maxProj = refWidth * dx + refHeight * dy

        val originX = if (originInRoot != null && rootSize != null) originInRoot.x else 0f
        val originY = if (originInRoot != null && rootSize != null) originInRoot.y else 0f

        val ratio = shimmerRatio
        val actualShimmerWidth =
            when {
                ratio != null -> maxProj * ratio
                !useRootCoordinates -> (size.width * dx + size.height * dy) * 0.8f
                else -> shimmerWidth
            }

        // 精确收敛扫描行程：从光束刚接触视野边缘到刚好完全离开，消除屏幕外大半虚空空跑
        val start = -actualShimmerWidth
        val end = maxProj
        val t = start + (end - start) * progressValue

        val startX = t * dx - originX
        val startY = t * dy - originY

        val brush = obtainShaderBrush(actualShimmerWidth, baseColor, highlightColor)
        val shader = cachedLinearGradient
        if (shader != null) {
            localMatrix.reset()
            localMatrix.setTranslate(startX, startY)
            shader.setLocalMatrix(localMatrix)
        }

        drawOutline(outline = outline, brush = brush)
        // 注意：无需显式调用 invalidateDraw()，progressState/progress.value 作为 Snapshot State
        // 在 Draw 阶段被读取时，Compose 会自动挂载观察者，在下一帧动画值更新时自动精准触发局部重绘。
    }
}
