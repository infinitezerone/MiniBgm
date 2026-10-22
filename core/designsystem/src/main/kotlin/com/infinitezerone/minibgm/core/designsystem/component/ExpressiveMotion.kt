package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 复合按压交互源：将多个子项的 [InteractionSource] 数据流汇聚为单一交互流。
 * 供容器（如悬浮底栏胶囊）整体监听并响应任意子项的点击与回弹。
 */
class CombinedInteractionSource(
    private val sources: List<InteractionSource>,
) : InteractionSource {
    constructor(vararg sources: InteractionSource) : this(sources.toList())

    override val interactions: Flow<Interaction> =
        merge(*sources.map { it.interactions }.toTypedArray())
}

/**
 * ColorOS 15/17 水生动效物理参数默认值。
 */
object AquamorphicMotionDefaults {
    /** 最小下沉蓄力冲量时长（毫秒），确保轻点时不被瞬时中断 */
    const val MIN_IMPULSE_MS = 90L

    /** 物理亚像素采样阈值：杜绝 Compose 默认 0.01f (1%) 阈值抹杀水滴二次微弹 */
    const val PHYSICAL_VISIBILITY_THRESHOLD = 0.0001f

    /** 按下态弹簧：坚实果断触底，Damping 1.0f 临界阻尼，绝不过冲晃荡 */
    val PressDownSpring =
        spring<Float>(
            dampingRatio = 1.0f,
            stiffness = 700f,
            visibilityThreshold = PHYSICAL_VISIBILITY_THRESHOLD,
        )

    /**
     * 释放回弹弹簧：ColorOS 15/17 水生流体二次微弹（Damping 0.35f，Stiffness 450f，Threshold 0.0001f）。
     *
     * 物理振荡特性（以 0.95f 缩放回弹至 1.0f 为例）：
     * 1. 主回弹过冲 (Primary Overshoot)：在 ~150ms 处膨胀至 ~1.016x (+1.6% 弹性拉伸)；
     * 2. 二次微弹 (Secondary Micro-bounce)：在 ~310ms 处微凹至 ~0.995x (-0.5% 水滴表面张力回缩)；
     * 3. 平滑收敛：在 ~420ms 处自然静止于 1.000x。
     */
    val ReleaseReboundSpring =
        spring<Float>(
            dampingRatio = 0.35f,
            stiffness = 450f,
            visibilityThreshold = PHYSICAL_VISIBILITY_THRESHOLD,
        )
}

/**
 * ColorOS 15/17 水生设计标准按压微弹性修饰符 (基于现代 Compose Modifier.Node 架构)。
 *
 * 核心手感与物理特性：
 * 1. **零 Recomposition 性能**：完全基于 [Modifier.Node] 与 [LayoutModifierNode]，
 *    直接驱动 RenderNode GraphicsLayer 缩放，零 Composable 树重组；
 * 2. **90ms 物理下沉冲量保护 (Impulse Hold)**：解决移动端快速轻触 (<50ms) 导致动效被截断的通病，
 *    无论手指抬起多快，动效引擎必然保证走完一次完整的「下沉触底 -> 蓄力 -> 弹起」周期；
 * 3. **ColorOS 非对称水生弹簧物理与二次微弹 (Asymmetric Spring & Secondary Micro-bounce)**：
 *    - 按下触底：坚实沉稳（Stiffness 700f, Damping 1.0f 临界阻尼，绝不过冲）；
 *    - 释放回弹：水滴弹性势能释放（Stiffness 450f, Damping 0.35f 微过冲，浮现 ~1.016x 过冲与 ~0.995x 二次微弹）。
 *
 * @param interactionSource 控件的交互源（可配合 [CombinedInteractionSource] 供整栏统一响应）
 * @param pressedScale 按下时的物理缩放比（小操作键 0.92f，中药丸/底栏 0.95f，大焦点卡 0.985f）
 * @param enabled 是否启用此物理微动效
 */
fun Modifier.aquamorphicPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
): Modifier =
    if (!enabled) {
        this
    } else {
        this then AquamorphicPressNodeElement(
            interactionSource = interactionSource,
            pressedScale = pressedScale,
        )
    }

/**
 * [aquamorphicPress] 的兼容别名。
 */
fun Modifier.expressivePress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
): Modifier = aquamorphicPress(interactionSource, pressedScale, enabled)

internal data class AquamorphicPressNodeElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<AquamorphicPressNode>() {
    override fun create(): AquamorphicPressNode =
        AquamorphicPressNode(
            interactionSource = interactionSource,
            pressedScale = pressedScale,
        )

    override fun update(node: AquamorphicPressNode) {
        node.update(interactionSource, pressedScale)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "aquamorphicPress"
        properties["interactionSource"] = interactionSource
        properties["pressedScale"] = pressedScale
    }
}

internal class AquamorphicPressNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(),
    LayoutModifierNode {

    val scaleAnimatable =
        Animatable(
            initialValue = 1f,
            visibilityThreshold = AquamorphicMotionDefaults.PHYSICAL_VISIBILITY_THRESHOLD,
        )
    private var collectJob: Job? = null
    private var animJob: Job? = null

    override fun onAttach() {
        startObserving()
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
        animJob?.cancel()
        animJob = null
    }

    fun update(
        interactionSource: InteractionSource,
        pressedScale: Float,
    ) {
        val interactionChanged = this.interactionSource != interactionSource
        this.interactionSource = interactionSource
        this.pressedScale = pressedScale
        if (interactionChanged && isAttached) {
            startObserving()
        }
    }

    private fun startObserving() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            var pressStartTime = 0L

            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressStartTime = System.currentTimeMillis()
                        animJob?.cancel()
                        animJob = launch {
                            scaleAnimatable.animateTo(
                                targetValue = pressedScale,
                                animationSpec = AquamorphicMotionDefaults.PressDownSpring,
                            ) {
                                invalidatePlacement()
                            }
                        }
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val elapsed = System.currentTimeMillis() - pressStartTime
                        val remainingImpulse = (AquamorphicMotionDefaults.MIN_IMPULSE_MS - elapsed).coerceAtLeast(0L)
                        animJob?.cancel()
                        animJob = launch {
                            if (remainingImpulse > 0L) {
                                scaleAnimatable.animateTo(
                                    targetValue = pressedScale,
                                    animationSpec = AquamorphicMotionDefaults.PressDownSpring,
                                ) {
                                    invalidatePlacement()
                                }
                                val postElapsed = System.currentTimeMillis() - pressStartTime
                                val remainingPost = (AquamorphicMotionDefaults.MIN_IMPULSE_MS - postElapsed).coerceAtLeast(0L)
                                if (remainingPost > 0L) {
                                    delay(remainingPost)
                                }
                            }
                            scaleAnimatable.animateTo(
                                targetValue = 1f,
                                animationSpec = AquamorphicMotionDefaults.ReleaseReboundSpring,
                            ) {
                                invalidatePlacement()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scaleAnimatable.value
                scaleY = scaleAnimatable.value
            }
        }
    }
}
