package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.infinitezerone.minibgm.core.designsystem.theme.BgmMotion

/**
 * ColorOS 水生设计标准按压弹性微动效修饰符 (Aquamorphic Press Motion)。
 *
 * 遵循 TOKEN_PROVENANCE.md 规范，唯一真源对齐 [BgmMotion.spatialSpring] (Scheme.Aquamorphic, Speed.Fast)：
 * - 采用水生流体临界阻尼弹簧物理（Damping: 0.78f, Stiffness: 850f）；
 * - 拒绝 M3 美式卡通晃荡过冲，呈现如水流下陷、微弹即止的沉浸温润手感；
 * - 彻底杜绝每个组件重复手写动画与 Interaction 状态收集；
 * - 任何具备 [InteractionSource] 的点击组件挂载此修饰符后，自动获得柔和微下陷与水生释放回弹。
 *
 * @param interactionSource 控件的交互源（与 clickable / Box 共用）
 * @param pressedScale 按下时的物理缩放比（小操作键 0.92f，中药丸 0.95f，大焦点卡 0.985f）
 */
fun Modifier.aquamorphicPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
): Modifier =
    if (!enabled) {
        this
    } else {
        this.composed {
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) pressedScale else 1f,
                animationSpec =
                    BgmMotion.spatialSpring(
                        scheme = BgmMotion.Scheme.Aquamorphic,
                        speed = BgmMotion.Speed.Fast,
                    ),
                label = "aquamorphic_press_scale",
            )
            this.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        }
    }

/**
 * [aquamorphicPress] 的兼容别名。
 */
fun Modifier.expressivePress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true,
): Modifier = aquamorphicPress(interactionSource, pressedScale, enabled)
