package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效令牌：统一时长、缓动曲线与弹簧参数。
 *
 * 分三层：
 * - [Durations] / [Easings] / [Springs]：Material 3 原始令牌，供新代码直接引用；
 * - [spatialSpring] / [effectsSpring]：M3 Expressive 的空间位移与视效弹簧；
 * - [standardTween] / [standardSpring] / [snappySpring]：项目兼容层，保留既有观感。
 *
 * 使用边界：
 * - 位置、尺寸、旋转等空间变化用 [spatialSpring]；
 * - 透明度、颜色等视效变化用 [effectsSpring]；
 * - 进度、弹幕、骨架、滚动、手势跟随等功能性动效用 [functionalTween] 或线性时间轴；
 * - 不要把所有动画都弹簧化。
 */
object BgmMotion {
    /** Material 3 duration tokens，单位为毫秒。 */
    object Durations {
        val short1: Int = 50
        val short2: Int = 100
        val short3: Int = 150
        val short4: Int = 200
        val medium1: Int = 250
        val medium2: Int = 300
        val medium3: Int = 350
        val medium4: Int = 400
        val long1: Int = 450
        val long2: Int = 500
        val long3: Int = 550
        val long4: Int = 600
        val extraLong1: Int = 700
        val extraLong2: Int = 800
        val extraLong3: Int = 900
        val extraLong4: Int = 1000
    }

    /** Material 3 easing tokens。 */
    object Easings {
        val linear: Easing = LinearEasing
        val standard: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val standardAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
        val standardDecelerate: CubicBezierEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
        val emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val emphasizedAccelerate: CubicBezierEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
        val emphasizedDecelerate: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

        /** M2 兼容曲线，仅用于保持既有动效观感。 */
        val legacyFastOutSlowIn: Easing = FastOutSlowInEasing
        val legacyFastOutLinearIn: Easing = FastOutLinearInEasing
        val legacyLinearOutSlowIn: Easing = LinearOutSlowInEasing
    }

    /** Material 3 spring physics，按 standard / expressive 与 fast / default / slow 分组。 */
    object Springs {
        object Standard {
            val defaultSpatialDampingRatio: Float = 0.9f
            val defaultSpatialStiffness: Float = 700f
            val fastSpatialDampingRatio: Float = 0.9f
            val fastSpatialStiffness: Float = 1400f
            val slowSpatialDampingRatio: Float = 0.9f
            val slowSpatialStiffness: Float = 300f

            val defaultEffectsDampingRatio: Float = 1.0f
            val defaultEffectsStiffness: Float = 1600f
            val fastEffectsDampingRatio: Float = 1.0f
            val fastEffectsStiffness: Float = 3800f
            val slowEffectsDampingRatio: Float = 1.0f
            val slowEffectsStiffness: Float = 800f
        }

        object Expressive {
            val defaultSpatialDampingRatio: Float = 0.8f
            val defaultSpatialStiffness: Float = 380f
            val fastSpatialDampingRatio: Float = 0.6f
            val fastSpatialStiffness: Float = 800f
            val slowSpatialDampingRatio: Float = 0.8f
            val slowSpatialStiffness: Float = 200f

            val defaultEffectsDampingRatio: Float = 1.0f
            val defaultEffectsStiffness: Float = 1600f
            val fastEffectsDampingRatio: Float = 1.0f
            val fastEffectsStiffness: Float = 3800f
            val slowEffectsDampingRatio: Float = 1.0f
            val slowEffectsStiffness: Float = 800f
        }

        /** ColorOS 15 水生流体动效物理参数（Aquamorphic Spring Physics）。 */
        object Aquamorphic {
            /** 微交互快速按压回弹：趋近临界阻尼，微弹即止，杜绝多余来回晃荡 */
            val fastSpatialDampingRatio: Float = 0.78f
            val fastSpatialStiffness: Float = 850f

            /** 容器/浮层展开：温润水生顺滑过渡 */
            val defaultSpatialDampingRatio: Float = 0.85f
            val defaultSpatialStiffness: Float = 420f

            val slowSpatialDampingRatio: Float = 0.88f
            val slowSpatialStiffness: Float = 220f

            val defaultEffectsDampingRatio: Float = 1.0f
            val defaultEffectsStiffness: Float = 1600f
            val fastEffectsDampingRatio: Float = 1.0f
            val fastEffectsStiffness: Float = 3800f
            val slowEffectsDampingRatio: Float = 1.0f
            val slowEffectsStiffness: Float = 800f
        }

        /** 兼容别名：Expressive default spatial。 */
        val ExpressiveSpatialDampingRatio: Float = Expressive.defaultSpatialDampingRatio
        val ExpressiveSpatialStiffness: Float = Expressive.defaultSpatialStiffness

        /** 兼容别名：Expressive default effects。 */
        val ExpressiveEffectsDampingRatio: Float = Expressive.defaultEffectsDampingRatio
        val ExpressiveEffectsStiffness: Float = Expressive.defaultEffectsStiffness
    }

    enum class Scheme { Standard, Expressive, Aquamorphic }

    enum class Speed { Fast, Default, Slow }

    val fastMillis: Int = Durations.short3
    val standardMillis: Int = Durations.medium1
    val ambientMillis: Int = Durations.long1
    val skeletonMillis: Int = 1500

    /** M2 兼容曲线；新代码请使用 [Easings.standard]。 */
    val standardEasing: Easing = Easings.legacyFastOutSlowIn

    val emphasizedAccelerateEasing: CubicBezierEasing = Easings.emphasizedAccelerate

    /** M3 标准进入/状态切换曲线。 */
    fun <T> m3StandardTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.standard,
        )

    fun <T> m3StandardAccelerateTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.standardAccelerate,
        )

    fun <T> m3StandardDecelerateTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.standardDecelerate,
        )

    fun <T> m3EmphasizedTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.emphasized,
        )

    fun <T> m3EmphasizedAccelerateTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.emphasizedAccelerate,
        )

    fun <T> m3EmphasizedDecelerateTween(durationMillis: Int = Durations.medium1): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.emphasizedDecelerate,
        )

    /** 功能性动效：速度必须可预测，不使用弹簧。 */
    fun <T> functionalTween(durationMillis: Int = Durations.short3): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = Easings.linear,
        )

    /** M3 空间弹簧：位置、尺寸、旋转。默认 Expressive / Default。 */
    fun <T> spatialSpring(
        scheme: Scheme = Scheme.Expressive,
        speed: Speed = Speed.Default,
    ): SpringSpec<T> {
        val (dampingRatio, stiffness) = spatialSpringToken(scheme, speed)
        return spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    /** M3 视效弹簧：透明度、颜色，不允许过冲。默认 Expressive / Default。 */
    fun <T> effectsSpring(
        scheme: Scheme = Scheme.Expressive,
        speed: Speed = Speed.Default,
    ): SpringSpec<T> {
        val (dampingRatio, stiffness) = effectsSpringToken(scheme, speed)
        return spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    private fun spatialSpringToken(
        scheme: Scheme,
        speed: Speed,
    ): Pair<Float, Float> =
        when (scheme) {
            Scheme.Standard ->
                when (speed) {
                    Speed.Fast -> Springs.Standard.fastSpatialDampingRatio to Springs.Standard.fastSpatialStiffness
                    Speed.Default -> Springs.Standard.defaultSpatialDampingRatio to Springs.Standard.defaultSpatialStiffness
                    Speed.Slow -> Springs.Standard.slowSpatialDampingRatio to Springs.Standard.slowSpatialStiffness
                }

            Scheme.Expressive ->
                when (speed) {
                    Speed.Fast -> Springs.Expressive.fastSpatialDampingRatio to Springs.Expressive.fastSpatialStiffness
                    Speed.Default -> Springs.Expressive.defaultSpatialDampingRatio to Springs.Expressive.defaultSpatialStiffness
                    Speed.Slow -> Springs.Expressive.slowSpatialDampingRatio to Springs.Expressive.slowSpatialStiffness
                }

            Scheme.Aquamorphic ->
                when (speed) {
                    Speed.Fast -> Springs.Aquamorphic.fastSpatialDampingRatio to Springs.Aquamorphic.fastSpatialStiffness
                    Speed.Default -> Springs.Aquamorphic.defaultSpatialDampingRatio to Springs.Aquamorphic.defaultSpatialStiffness
                    Speed.Slow -> Springs.Aquamorphic.slowSpatialDampingRatio to Springs.Aquamorphic.slowSpatialStiffness
                }
        }

    private fun effectsSpringToken(
        scheme: Scheme,
        speed: Speed,
    ): Pair<Float, Float> =
        when (scheme) {
            Scheme.Standard ->
                when (speed) {
                    Speed.Fast -> Springs.Standard.fastEffectsDampingRatio to Springs.Standard.fastEffectsStiffness
                    Speed.Default -> Springs.Standard.defaultEffectsDampingRatio to Springs.Standard.defaultEffectsStiffness
                    Speed.Slow -> Springs.Standard.slowEffectsDampingRatio to Springs.Standard.slowEffectsStiffness
                }

            Scheme.Expressive ->
                when (speed) {
                    Speed.Fast -> Springs.Expressive.fastEffectsDampingRatio to Springs.Expressive.fastEffectsStiffness
                    Speed.Default -> Springs.Expressive.defaultEffectsDampingRatio to Springs.Expressive.defaultEffectsStiffness
                    Speed.Slow -> Springs.Expressive.slowEffectsDampingRatio to Springs.Expressive.slowEffectsStiffness
                }

            Scheme.Aquamorphic ->
                when (speed) {
                    Speed.Fast -> Springs.Aquamorphic.fastEffectsDampingRatio to Springs.Aquamorphic.fastEffectsStiffness
                    Speed.Default -> Springs.Aquamorphic.defaultEffectsDampingRatio to Springs.Aquamorphic.defaultEffectsStiffness
                    Speed.Slow -> Springs.Aquamorphic.slowEffectsDampingRatio to Springs.Aquamorphic.slowEffectsStiffness
                }
        }

    /** 兼容旧调用：保留 M2 FastOutSlowIn 观感。 */
    fun <T> standardTween(durationMillis: Int = standardMillis): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = standardEasing,
        )

    /** 兼容旧调用：保留 M3 emphasized accelerate 观感。 */
    fun <T> emphasizedAccelerate(durationMillis: Int = standardMillis): TweenSpec<T> =
        tween(
            durationMillis = durationMillis,
            easing = emphasizedAccelerateEasing,
        )

    /** 兼容旧调用：项目调参弹簧，新代码优先使用 [spatialSpring]。 */
    fun <T> standardSpring(
        dampingRatio: Float = 0.82f,
        stiffness: Float = Spring.StiffnessMediumLow,
    ): SpringSpec<T> =
        spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
        )

    /** 兼容旧调用：项目调参弹簧，新代码优先使用 [effectsSpring]。 */
    fun <T> snappySpring(
        dampingRatio: Float = 0.80f,
        stiffness: Float = Spring.StiffnessMedium,
    ): SpringSpec<T> =
        spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
        )
}
