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

object AquamorphicMotionDefaults {
    const val PHYSICAL_VISIBILITY_THRESHOLD = 0.0001f

    val PressDownSpring =
        spring<Float>(
            dampingRatio = 1.0f,
            stiffness = 1000f,
            visibilityThreshold = PHYSICAL_VISIBILITY_THRESHOLD,
        )

    val ReleaseReboundSpring =
        spring<Float>(
            dampingRatio = 0.38f,
            stiffness = 800f,
            visibilityThreshold = PHYSICAL_VISIBILITY_THRESHOLD,
        )
}

/**
 * 水生按压微弹性修饰符。
 *
 * @param interactionSource 控件的交互源（可配合 [CombinedInteractionSource] 供整栏统一响应）
 * @param pressedScale 按下时的物理缩放比
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
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
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
                        animJob?.cancel()
                        animJob = launch {
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
