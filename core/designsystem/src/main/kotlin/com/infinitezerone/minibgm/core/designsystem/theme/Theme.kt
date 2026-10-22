package com.infinitezerone.minibgm.core.designsystem.theme

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node =
        object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        inversePrimary = InversePrimaryDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = TertiaryDark,
        onTertiary = OnTertiaryDark,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceTint = PrimaryDark,
        inverseSurface = InverseSurfaceDark,
        inverseOnSurface = InverseOnSurfaceDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        scrim = ScrimDark,
        surfaceBright = SurfaceBrightDark,
        surfaceDim = SurfaceDimDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        inversePrimary = InversePrimaryLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = TertiaryLight,
        onTertiary = OnTertiaryLight,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceTint = PrimaryLight,
        inverseSurface = InverseSurfaceLight,
        inverseOnSurface = InverseOnSurfaceLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        scrim = ScrimLight,
        surfaceBright = SurfaceBrightLight,
        surfaceDim = SurfaceDimLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
    )

/**
 * AMOLED 纯黑档：品牌/文本角色与深灰档完全一致，仅表面与容器阶梯沉到纯黑或近纯黑
 * （见 Color.kt Layer 3）。仅应在深色模式下启用。
 */
private val AmoledDarkColorScheme =
    DarkColorScheme.copy(
        background = BackgroundAmoledDark,
        surface = SurfaceAmoledDark,
        surfaceVariant = SurfaceVariantAmoledDark,
        surfaceDim = SurfaceDimAmoledDark,
        surfaceBright = SurfaceBrightAmoledDark,
        surfaceContainerLowest = SurfaceContainerLowestAmoledDark,
        surfaceContainerLow = SurfaceContainerLowAmoledDark,
        surfaceContainer = SurfaceContainerAmoledDark,
        surfaceContainerHigh = SurfaceContainerHighAmoledDark,
        surfaceContainerHighest = SurfaceContainerHighestAmoledDark,
        inverseOnSurface = InverseOnSurfaceAmoledDark,
    )

/** 供 Glance 小组件等无 Material You 动态取色能力的宿主桥接品牌色 */
val MiniBgmLightColors: ColorScheme = LightColorScheme
val MiniBgmDarkColors: ColorScheme = DarkColorScheme
val MiniBgmAmoledDarkColors: ColorScheme = AmoledDarkColorScheme

/**
 * 低端机与省电模式下降级为实体材质。
 *
 * 2026 年的 Apple / Microsoft / 小米 / OPPO 都在做同一件事：玻璃是装饰层，设备扛不住时
 * 必须无感退回实体面板。这里监听省电模式广播，低内存设备则在首次组合时直接判定。
 */
@Composable
private fun rememberReducedTransparency(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val activityManager = remember(context) { context.getSystemService(ActivityManager::class.java) }
    val reduced =
        remember {
            mutableStateOf(
                powerManager?.isPowerSaveMode == true ||
                    activityManager?.isLowRamDevice == true,
            )
        }

    DisposableEffect(context, powerManager, activityManager) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    reduced.value =
                        powerManager?.isPowerSaveMode == true ||
                        activityManager?.isLowRamDevice == true
                }
            }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return reduced.value
}

@Composable
fun MiniBgmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledDark: Boolean = false,
    dynamicColor: Boolean = false,
    uiStyle: AppUiStyle = AppUiStyle.Brand,
    content: @Composable () -> Unit,
) {
    // minSdk 31 起 Material You 动态取色恒可用，无需再判 SDK_INT
    val colorScheme =
        when {
            // AMOLED 纯黑档是用户显式选择，优先级高于动态取色的深色方案（动态取色本身无 AMOLED 概念）
            darkTheme && amoledDark -> AmoledDarkColorScheme
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val reducedTransparency = rememberReducedTransparency()

    CompositionLocalProvider(
        LocalAppUiStyle provides uiStyle,
        LocalLuminousTokens provides
            buildLuminousTokens(
                darkTheme = darkTheme,
                amoledDark = amoledDark,
                colorScheme = colorScheme,
                reducedTransparency = reducedTransparency,
            ),
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            typography = BgmTypography,
            shapes = BgmShapes,
        ) {
            CompositionLocalProvider(
                LocalIndication provides NoIndication,
                content = content,
            )
        }
    }
}
