package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ==========================================
// ACG 收藏与状态语义色 (Tailwind 色板 + 自定义暗色容器)
// ==========================================

/** 想看 (Wish) - 柔和鸢尾紫 (Soft Indigo-400) */
val StatusWish = Color(0xFF818CF8)
val StatusWishContainerLight = Color(0xFFEEF2FF)
val OnStatusWishContainerLight = Color(0xFF3730A3)
val StatusWishContainerDark = Color(0xFF262A56)
val OnStatusWishContainerDark = Color(0xFFC7D2FE)
val StatusWishContainer = StatusWishContainerLight
val OnStatusWishContainer = OnStatusWishContainerLight

/** 在看 (Doing) - 薄荷生机翠绿 (Mint Glow Emerald-400) */
val StatusDoing = Color(0xFF34D399)
val StatusDoingContainerLight = Color(0xFFECFDF5)
val OnStatusDoingContainerLight = Color(0xFF065F46)
val StatusDoingContainerDark = Color(0xFF0F392B)
val OnStatusDoingContainerDark = Color(0xFFA7F3D0)
val StatusDoingContainer = StatusDoingContainerLight
val OnStatusDoingContainer = OnStatusDoingContainerLight

/** 看过 (Collect) - 经典樱花粉桃 (Sakura Rose-400) */
val StatusCollect = Color(0xFFF472B6)
val StatusCollectContainerLight = Color(0xFFFDF2F8)
val OnStatusCollectContainerLight = Color(0xFF9D174D)
val StatusCollectContainerDark = Color(0xFF4C1D36)
val OnStatusCollectContainerDark = Color(0xFFFBCFE8)
val StatusCollectContainer = StatusCollectContainerLight
val OnStatusCollectContainer = OnStatusCollectContainerLight

/** 搁置 (On Hold) - 温暖琥珀黄 (Warm Amber-400) */
val StatusOnHold = Color(0xFFFBBF24)
val StatusOnHoldContainerLight = Color(0xFFFFFBEB)
val OnStatusOnHoldContainerLight = Color(0xFF92400E)
val StatusOnHoldContainerDark = Color(0xFF422006)
val OnStatusOnHoldContainerDark = Color(0xFFFDE68A)
val StatusOnHoldContainer = StatusOnHoldContainerLight
val OnStatusOnHoldContainer = OnStatusOnHoldContainerLight

/** 抛弃 (Dropped) - 低敏灰蓝板岩 (Muted Slate-400) */
val StatusDropped = Color(0xFF94A3B8)
val StatusDroppedContainerLight = Color(0xFFF1F5F9)
val OnStatusDroppedContainerLight = Color(0xFF334155)
val StatusDroppedContainerDark = Color(0xFF1E293B)
val OnStatusDroppedContainerDark = Color(0xFFCBD5E1)
val StatusDroppedContainer = StatusDroppedContainerLight
val OnStatusDroppedContainer = OnStatusDroppedContainerLight

// ==========================================
// 放送状态与徽章
// ==========================================

/** 放送中（正在播出） - 成熟橘（Tailwind Orange-600，实色徽章上白字可读） */
val StatusAiring = Color(0xFFEA580C)
val StatusAiringContainerLight = Color(0xFFFFF7ED)
val OnStatusAiringContainerLight = Color(0xFFC2410C)
val StatusAiringContainerDark = Color(0xFF451A03)
val OnStatusAiringContainerDark = Color(0xFFFDBA74)
val StatusAiringContainer = StatusAiringContainerLight
val OnStatusAiringContainer = OnStatusAiringContainerLight

/** 经典口碑徽章（非近期热播、以评分人数取胜）- 尾随靛蓝（Tailwind Indigo-600） */
val BadgeClassic = Color(0xFF4F46E5)

// ==========================================
// 语义容器色自适应辅助函数 (Theme-adaptive Composable Helpers)
// ==========================================

@Composable
@ReadOnlyComposable
fun statusWishContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusWishContainerDark else StatusWishContainerLight

@Composable
@ReadOnlyComposable
fun onStatusWishContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusWishContainerDark else OnStatusWishContainerLight

@Composable
@ReadOnlyComposable
fun statusDoingContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusDoingContainerDark else StatusDoingContainerLight

@Composable
@ReadOnlyComposable
fun onStatusDoingContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusDoingContainerDark else OnStatusDoingContainerLight

@Composable
@ReadOnlyComposable
fun statusCollectContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusCollectContainerDark else StatusCollectContainerLight

@Composable
@ReadOnlyComposable
fun onStatusCollectContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusCollectContainerDark else OnStatusCollectContainerLight

@Composable
@ReadOnlyComposable
fun statusOnHoldContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusOnHoldContainerDark else StatusOnHoldContainerLight

@Composable
@ReadOnlyComposable
fun onStatusOnHoldContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusOnHoldContainerDark else OnStatusOnHoldContainerLight

@Composable
@ReadOnlyComposable
fun statusDroppedContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusDroppedContainerDark else StatusDroppedContainerLight

@Composable
@ReadOnlyComposable
fun onStatusDroppedContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusDroppedContainerDark else OnStatusDroppedContainerLight

@Composable
@ReadOnlyComposable
fun statusAiringContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) StatusAiringContainerDark else StatusAiringContainerLight

@Composable
@ReadOnlyComposable
fun onStatusAiringContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnStatusAiringContainerDark else OnStatusAiringContainerLight

// ==========================================
// 正文文本层次（标题/强调用 onSurface；长读正文降一档柔化暗色观感）
// ==========================================

/**
 * 长读正文色（成段评论、简介、主题帖、AI 对话等）。
 * 亮色与 onSurface 同档；暗色降档对齐 ChatGPT「标题亮、正文柔和」的做法（ECECEC 一档），
 * 夜间长阅读不刺眼，对比度仍 >10:1。BBCode 渲染层的默认色即本档。
 */
val OnSurfaceBodyLight = Color(0xFF18181B)
val OnSurfaceBodyDark = Color(0xFFD4D4D8)

@Composable
@ReadOnlyComposable
fun onSurfaceBodyColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnSurfaceBodyDark else OnSurfaceBodyLight

// ==========================================
// 评分与收藏
// ==========================================

/** 权威评分星标 / 金色徽章 (Amber Gold) */
val RatingGold = Color(0xFFF59E0B)

/** 深色遮罩上的高亮评分文字 / 选中态圆点 (Amber-300) */
val RatingGoldBright = Color(0xFFFCD34D)

/** 金色底上的高对比前景文字（Rank 徽章等） */
val OnRatingGold = Color(0xFF3E1F00)

/** 温暖琥珀（向后兼容别名，已校准为柔和琥珀 Warm Amber-400） */
val WishOrange = StatusOnHold

// ==========================================
// 快捷打卡动作色（向后兼容别名）
// ==========================================

val ActionWish = StatusWish
val ActionDoing = StatusDoing
val ActionCollect = StatusCollect
val ActionOnHold = StatusOnHold
val ActionDropped = StatusDropped

// 搜索关键词高亮（亮/暗双档：暗色档用暖褐深底 + 亮琥珀前景，避免浅黄底在纯黑上发糊）

val HighlightAmber = Color(0xFFD97706)
val HighlightContainerLight = Color(0xFFFFF3D0)
val OnHighlightContainerLight = Color(0xFFB25E00)
val HighlightContainerDark = Color(0xFF43330C)
val OnHighlightContainerDark = Color(0xFFFCD34D)
val HighlightContainer = HighlightContainerLight
val OnHighlightContainer = OnHighlightContainerLight

// 条目类型色板（与状态色同族：Tailwind 50/700 亮色阶梯 + 项目暗色深容器 / 300 前景；亮/暗双档）

val TypeBookContainer = Color(0xFFFFF7ED)
val OnTypeBook = Color(0xFFC2410C)
val TypeBookContainerDark = Color(0xFF4A2210)
val OnTypeBookDark = Color(0xFFFDBA74)
val TypeAnimeContainer = Color(0xFFEFF6FF)
val OnTypeAnime = Color(0xFF1D4ED8)
val TypeAnimeContainerDark = Color(0xFF17335C)
val OnTypeAnimeDark = Color(0xFF93C5FD)
val TypeMusicContainer = Color(0xFFFAF5FF)
val OnTypeMusic = Color(0xFF7E22CE)
val TypeMusicContainerDark = Color(0xFF3B1A5E)
val OnTypeMusicDark = Color(0xFFD8B4FE)
val TypeGameContainer = Color(0xFFF0FDF4)
val OnTypeGame = Color(0xFF15803D)
val TypeGameContainerDark = Color(0xFF123B24)
val OnTypeGameDark = Color(0xFF86EFAC)
val TypeRealContainer = Color(0xFFFDF2F8)
val OnTypeReal = Color(0xFFBE185D)
val TypeRealContainerDark = Color(0xFF4C1D3C)
val OnTypeRealDark = Color(0xFFF9A8D4)

// ==========
// 搜索关键词高亮自适应助手
// ==========

@Composable
@ReadOnlyComposable
fun highlightContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) HighlightContainerDark else HighlightContainerLight

@Composable
@ReadOnlyComposable
fun onHighlightContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnHighlightContainerDark else OnHighlightContainerLight

// ==========
// 条目类型色板自适应助手
// ==========

@Composable
@ReadOnlyComposable
fun typeBookContainerColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeBookContainerDark else TypeBookContainer

@Composable
@ReadOnlyComposable
fun onTypeBookColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeBookDark else OnTypeBook

@Composable
@ReadOnlyComposable
fun typeAnimeContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeAnimeContainerDark else TypeAnimeContainer

@Composable
@ReadOnlyComposable
fun onTypeAnimeColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeAnimeDark else OnTypeAnime

@Composable
@ReadOnlyComposable
fun typeMusicContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeMusicContainerDark else TypeMusicContainer

@Composable
@ReadOnlyComposable
fun onTypeMusicColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeMusicDark else OnTypeMusic

@Composable
@ReadOnlyComposable
fun typeGameContainerColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeGameContainerDark else TypeGameContainer

@Composable
@ReadOnlyComposable
fun onTypeGameColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeGameDark else OnTypeGame

@Composable
@ReadOnlyComposable
fun typeRealContainerColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeRealContainerDark else TypeRealContainer

@Composable
@ReadOnlyComposable
fun onTypeRealColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeRealDark else OnTypeReal

// ==========================================
// 播放与资源渠道平台色
// ==========================================

/** 哔哩哔哩品牌粉色 */
val BrandBilibili = Color(0xFFFB7299)
val OnBrandBilibili = Color(0xFFE0567E)

/** 蜜柑计划品牌青绿色 */
val BrandMikan = Color(0xFF009688)
val OnBrandMikan = Color(0xFF00796B)

// ==========================================
// AI 独立入口彩色光晕色板 (Prismatic AI Glow Palette)
// ==========================================
val AiGlowBlue = Color(0xFF388BFD)
val AiGlowPurple = Color(0xFF8A2BE2)
val AiGlowPink = Color(0xFFFF3366)
val AiGlowAmber = Color(0xFFFF9900)
val AiGlowCyan = Color(0xFF00E5FF)

val AiIconGradientColors: List<Color> =
    listOf(
        AiGlowCyan,
        AiGlowBlue,
        AiGlowPurple,
        AiGlowPink,
        AiGlowAmber,
    )
