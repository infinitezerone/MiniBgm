package com.infinitezerone.minibgm.core.designsystem.component

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.lang.reflect.Method

/**
 * 默认 BottomSheet 位移与速度阈值配置（业界实践黄金平衡点）：
 * - [DefaultBottomSheetPositionalThreshold]: 96.dp。约为两个标准触控单位（~2.7cm），拇指自然下划一段距离即判定有意关闭，拉动不足时平滑回弹。
 * - [DefaultBottomSheetVelocityThreshold]: 500.dp。基于 Android 交互手势动力学（Fling Dismiss），随手轻快一甩即关闭，同时彻底过滤手指微动与慢划误触发。
 */
val DefaultBottomSheetPositionalThreshold: Dp = 96.dp
val DefaultBottomSheetVelocityThreshold: Dp = 500.dp

/**
 * Material 3 规范中的 Emphasized Accelerate 动画曲线 (md.sys.motion.easing.emphasized-accelerate)。
 * 适用于元素永久离开屏幕（Dismiss / Exit），起步平稳柔和，逐渐变快，并在最高速度时滑出屏幕视野。
 * cubic-bezier(0.3, 0.0, 0.8, 0.15)
 */
val BottomSheetEmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Material 3 规范中的 BottomSheet 收起/关闭动画：
 * 时长 250ms (md.sys.motion.duration.medium1)，配合 Emphasized Accelerate 加速度曲线。
 */
val DefaultBottomSheetHideAnimationSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = 250,
        easing = BottomSheetEmphasizedAccelerateEasing,
    )

@OptIn(ExperimentalMaterial3Api::class)
private val setHideMotionSpecMethod: Method? by lazy {
    try {
        SheetState::class.java.declaredMethods
            .firstOrNull {
                it.name.startsWith("setHideMotionSpec") && it.parameterTypes.size == 1
            }?.apply { isAccessible = true }
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val getHideMotionSpecMethod: Method? by lazy {
    try {
        SheetState::class.java.declaredMethods
            .firstOrNull {
                it.name.startsWith("getHideMotionSpec") && it.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
    } catch (_: Throwable) {
        null
    }
}

/**
 * 将 Material 3 规范中的加速退出动效（Emphasized Accelerate）应用到 [SheetState]。
 */
@OptIn(ExperimentalMaterial3Api::class)
fun SheetState.applyMaterial3MotionSpecs(hideSpec: FiniteAnimationSpec<Float> = DefaultBottomSheetHideAnimationSpec) {
    try {
        setHideMotionSpecMethod?.invoke(this, hideSpec)
    } catch (_: Throwable) {
        // 反射不可用时自动回退为系统默认行为，保持最高鲁棒性
    }
}

@VisibleForTesting
@OptIn(ExperimentalMaterial3Api::class)
internal fun SheetState.getHideMotionSpecOrNull(): FiniteAnimationSpec<Float>? =
    try {
        @Suppress("UNCHECKED_CAST")
        getHideMotionSpecMethod?.invoke(this) as? FiniteAnimationSpec<Float>
    } catch (_: Throwable) {
        null
    }

/**
 * 自定义 BottomSheet 状态，调优手势阻尼和防误触阈值，并注入 M3 Emphasized Accelerate 收起动效。
 *
 * Material 3 默认的 [positionalThreshold] 为 56.dp，[velocityThreshold] 为 125.dp。
 * 125 dp/s 的速度极低，任何轻微下划在手指离开屏幕瞬间都会达到，导致 BottomSheet 轻轻一碰就误关。
 *
 * @param skipPartiallyExpanded 是否跳过半展开直接全屏展开，默认 true
 * @param confirmValueChange 状态变更确认回调
 * @param initialValue 初始状态
 * @param skipHiddenState 是否跳过隐藏状态
 * @param positionalThreshold 需下拉的距离阈值，默认 96.dp
 * @param velocityThreshold 需达到的甩动速度阈值，默认 500.dp
 */
@Composable
@ExperimentalMaterial3Api
fun rememberBgmBottomSheetState(
    skipPartiallyExpanded: Boolean = true,
    confirmValueChange: (SheetValue) -> Boolean = { true },
    initialValue: SheetValue = SheetValue.Hidden,
    skipHiddenState: Boolean = false,
    positionalThreshold: Dp = DefaultBottomSheetPositionalThreshold,
    velocityThreshold: Dp = DefaultBottomSheetVelocityThreshold,
): SheetState {
    val density = LocalDensity.current
    val positionalThresholdToPx = { with(density) { positionalThreshold.toPx() } }
    val velocityThresholdToPx = { with(density) { velocityThreshold.toPx() } }
    val sheetState =
        rememberSaveable(
            skipPartiallyExpanded,
            confirmValueChange,
            skipHiddenState,
            saver =
                SheetState.Saver(
                    skipPartiallyExpanded = skipPartiallyExpanded,
                    positionalThreshold = positionalThresholdToPx,
                    velocityThreshold = velocityThresholdToPx,
                    confirmValueChange = confirmValueChange,
                    skipHiddenState = skipHiddenState,
                ),
        ) {
            SheetState(
                skipPartiallyExpanded = skipPartiallyExpanded,
                positionalThreshold = positionalThresholdToPx,
                velocityThreshold = velocityThresholdToPx,
                initialValue = initialValue,
                confirmValueChange = confirmValueChange,
                skipHiddenState = skipHiddenState,
            ).also { it.applyMaterial3MotionSpecs() }
        }
    SideEffect {
        sheetState.applyMaterial3MotionSpecs()
    }
    return sheetState
}

/**
 * MiniBgm 统一封装的 [ModalBottomSheet]，预置了更适宜的手势阻尼阈值与 Material 3 Emphasized Accelerate 收起动效，
 * 支持平板/大屏最大宽度 600dp。
 */
@Composable
@ExperimentalMaterial3Api
fun BgmModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBgmBottomSheetState(),
    sheetMaxWidth: Dp = 600.dp,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = {
            SideEffect {
                sheetState.applyMaterial3MotionSpecs()
            }
            content()
        },
    )
}
