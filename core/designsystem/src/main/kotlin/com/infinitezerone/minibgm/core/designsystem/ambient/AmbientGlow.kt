package com.infinitezerone.minibgm.core.designsystem.ambient

import android.graphics.Bitmap
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.Image
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 光晕渐变形态 */
enum class AmbientGlowStyle {
    /** 纵向：光晕从顶部起势，向下平滑收敛为透明（适合卡片/页头背景） */
    VerticalFade,

    /** 径向：光晕以中心为焦点，向四周收敛为透明（适合海报叠层） */
    RadialFocus,

    /** 漫射：以左上方封面为发光原点，向右下方扇形自然弥散为透明（专为条目详情等海报卡片设计） */
    HeroDiffuse,
}

/** 封面主色光晕的默认参数 */
private object AmbientGlowDefaults {
    /** 深色表面上的光晕峰值透明度（轻盈透气，避免暗沉脏色） */
    const val MAX_ALPHA_ON_DARK_SURFACE = 0.22f

    /** 浅色表面上的光晕峰值透明度（柔和通透，保留前景色阶对比） */
    const val MAX_ALPHA_ON_LIGHT_SURFACE = 0.16f

    /** 主色切换时的渐变时长 */
    const val COLOR_ANIMATION_MILLIS = 450

    /** 主色降采样时短边像素上限 */
    const val SAMPLE_SHORTER_SIDE_CAP = 24
}

/**
 * 封面主色状态：由 Coil 加载回调或 [com.infinitezerone.minibgm.core.designsystem.component.CoverImage]
 * 的主色回调写入，[AmbientGlow] 读取绘制。
 */
@Stable
class AmbientDominantColorState {
    var dominantColor: Color? by mutableStateOf(null)
        private set

    /** 直接写入主色（null 表示清除光晕，例如封面加载失败时） */
    fun updateDominantColor(color: Color?) {
        dominantColor = color
    }

    /** 供裸 [coil3.compose.AsyncImage] 的 onSuccess 回调接入 */
    fun onImageSuccess(success: AsyncImagePainter.State.Success) {
        updateDominantColor(success.result.image.toDominantColor())
    }

    /** 供裸 [coil3.compose.AsyncImage] 的 onError 回调接入：清除旧色，避免换封面后残留 */
    fun onImageError() {
        updateDominantColor(null)
    }
}

@Composable
fun rememberAmbientDominantColorState(): AmbientDominantColorState = remember { AmbientDominantColorState() }

/**
 * 背景光晕（Ambient Glow）：用封面主色铺一层柔和的氛围光，透明度向对侧收敛。
 *
 * - 深色表面下允许更强的光晕，浅色表面下压低峰值，两种模式都不牺牲前景可读性；
 * - 主色本身在提取阶段已被夹进可读亮度区间，这里再按表面明暗做透明度收敛；
 * - 主色为 null（未加载/加载失败）时完全透明，不占额外布局。
 */
@Composable
fun AmbientGlow(
    dominantColor: Color?,
    modifier: Modifier = Modifier,
    style: AmbientGlowStyle = AmbientGlowStyle.VerticalFade,
    intensity: Float = 1f,
) {
    Box(modifier = modifier.background(brush = rememberAmbientGlowBrush(dominantColor, style, intensity)))
}

/**
 * 页面级全景封面高斯模糊氛围层（Ambient Blur Backdrop）。
 *
 * 将海报封面进行大半径硬件高斯模糊，在页面顶部全景漫射，
 * 底部通过纵向渐隐遮罩与页面背景色 [MaterialTheme.colorScheme.surface] 平滑融合。
 *
 * - 全景透光：自然保留封面本身的丰富色相（天空、夕阳、发色、服饰），彻底告别单色渐变画笔的局限；
 * - 性能轻盈：复用 Coil 内存缓存，0 额外网络开销；Android 12+ 走 GPU RenderEffect 硬件加速；
 * - 容错机制：URL 为空或加载异常时不阻碍页面，装饰性功能 fail-open 绝不崩溃。
 */
@Composable
fun AmbientBlurBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    intensity: Float = 1f,
) {
    if (imageUrl.isNullOrBlank()) return

    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseAlpha = if (isDarkSurface) 0.36f else 0.22f
    val resolvedAlpha = (baseAlpha * intensity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier =
            modifier
                .clipToBounds()
                .background(surfaceColor),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = resolvedAlpha
                    }.blur(radius = blurRadius),
        )

        // 垂直平滑遮罩：顶部保持通透微光，中部渐次柔和过渡，底部完全融合入页面背景色
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to surfaceColor.copy(alpha = 0.25f),
                                0.70f to surfaceColor.copy(alpha = 0.80f),
                                1.0f to surfaceColor,
                            ),
                    ),
        )
    }
}

/**
 * [AmbientGlow] 的 Modifier 形态：适合直接垫在已有容器的 padding 之前，
 * 让光晕铺满容器而不改变任何布局几何（共享元素转场不受影响）。
 */
@Composable
fun Modifier.ambientGlow(
    dominantColor: Color?,
    style: AmbientGlowStyle = AmbientGlowStyle.VerticalFade,
    intensity: Float = 1f,
): Modifier = background(brush = rememberAmbientGlowBrush(dominantColor, style, intensity))

@Composable
private fun rememberAmbientGlowBrush(
    dominantColor: Color?,
    style: AmbientGlowStyle,
    intensity: Float,
): Brush {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val animatedColor =
        remember {
            Animatable(
                initialValue = Color.Transparent,
                typeConverter = Color.VectorConverter(ColorSpaces.Srgb),
            )
        }
    LaunchedEffect(dominantColor) {
        animatedColor.animateTo(
            targetValue = dominantColor ?: Color.Transparent,
            animationSpec = tween(durationMillis = AmbientGlowDefaults.COLOR_ANIMATION_MILLIS),
        )
    }

    val maxAlpha =
        if (isDarkSurface) {
            AmbientGlowDefaults.MAX_ALPHA_ON_DARK_SURFACE
        } else {
            AmbientGlowDefaults.MAX_ALPHA_ON_LIGHT_SURFACE
        }
    val alpha = maxAlpha * intensity.coerceIn(0f, 1f)
    val glow = animatedColor.value

    return when (style) {
        AmbientGlowStyle.VerticalFade ->
            Brush.verticalGradient(
                0.0f to glow.copy(alpha = alpha),
                0.20f to glow.copy(alpha = alpha * 0.75f),
                0.45f to glow.copy(alpha = alpha * 0.30f),
                0.70f to Color.Transparent,
                1.0f to Color.Transparent,
            )
        AmbientGlowStyle.RadialFocus ->
            Brush.radialGradient(
                colors = listOf(glow.copy(alpha = alpha), Color.Transparent),
            )
        AmbientGlowStyle.HeroDiffuse ->
            Brush.radialGradient(
                0.0f to glow.copy(alpha = alpha),
                0.28f to glow.copy(alpha = alpha * 0.65f),
                0.55f to glow.copy(alpha = alpha * 0.22f),
                0.80f to glow.copy(alpha = alpha * 0.05f),
                1.0f to Color.Transparent,
                center = Offset(x = 160f, y = 180f),
                radius = 1100f,
            )
    }
}

/** 从 Coil 解码结果中提取主色；非位图解码（如 GIF 帧/自定义 Image）返回 null，光晕自然缺省。
 *  任何位图读取异常一律吞掉降级为 null——光晕是纯装饰，绝不能让页面崩溃。
 */
internal fun Image.toDominantColor(): Color? =
    runCatching {
        val source = (this as? BitmapImage)?.bitmap ?: return@runCatching null
        val pixels = source.toDownsampledPixels() ?: return@runCatching null
        extractDominantColor(pixels)?.let(::Color)
    }.getOrNull()

private fun Bitmap.toDownsampledPixels(): IntArray? {
    if (width <= 0 || height <= 0) return null
    // Coil 在真机上默认解码为 Config#HARDWARE（仅存 GPU，getPixels 不支持），
    // 需先 copy 成软件位图才能读像素；copy 对硬件位图是合法操作
    val readable =
        if (config == Bitmap.Config.HARDWARE) {
            runCatching { copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
        } else {
            this
        }
    val shorterSide = min(readable.width, readable.height)
    val scale =
        if (shorterSide > AmbientGlowDefaults.SAMPLE_SHORTER_SIDE_CAP) {
            AmbientGlowDefaults.SAMPLE_SHORTER_SIDE_CAP.toFloat() / shorterSide
        } else {
            1f
        }
    val targetWidth = max(1, (readable.width * scale).roundToInt())
    val targetHeight = max(1, (readable.height * scale).roundToInt())
    val sampled =
        if (targetWidth == readable.width && targetHeight == readable.height) {
            readable
        } else {
            Bitmap.createScaledBitmap(readable, targetWidth, targetHeight, true)
        }
    val pixels = IntArray(targetWidth * targetHeight)
    sampled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    // 只回收我们自己创建的缩放/拷贝产物，原始位图归 Coil 所有
    if (sampled !== readable) sampled.recycle()
    if (readable !== this) readable.recycle()
    return pixels
}
