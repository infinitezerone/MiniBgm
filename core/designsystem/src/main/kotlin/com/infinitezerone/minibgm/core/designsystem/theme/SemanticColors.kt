package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ==========================================
// ACG 收藏与状态语义色 (Radix / Perception-calibrated)
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

/** 放送中（正在播出） - 活力珊瑚橙 */
val StatusAiring = Color(0xFFFF5722)
val StatusAiringContainerLight = Color(0xFFFFEBE5)
val OnStatusAiringContainerLight = Color(0xFFC42B00)
val StatusAiringContainerDark = Color(0xFF4D1405)
val OnStatusAiringContainerDark = Color(0xFFFFCCBC)
val StatusAiringContainer = StatusAiringContainerLight
val OnStatusAiringContainer = OnStatusAiringContainerLight

/** 经典口碑徽章（非近期热播、以评分人数取胜） */
val BadgeClassic = Color(0xFF3F51B5)

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

// 搜索关键词高亮

val HighlightAmber = Color(0xFFD97706)
val HighlightContainer = Color(0xFFFFF3D0)
val OnHighlightContainer = Color(0xFFB25E00)

// 条目类型色板（container 为浅底，On 为其上的内容色）

val TypeBookContainer = Color(0xFFFFF3E0)
val OnTypeBook = Color(0xFFE65100)
val TypeAnimeContainer = Color(0xFFE3F2FD)
val OnTypeAnime = Color(0xFF1976D2)
val TypeMusicContainer = Color(0xFFF3E5F5)
val OnTypeMusic = Color(0xFF7B1FA2)
val TypeGameContainer = Color(0xFFE8F5E9)
val OnTypeGame = Color(0xFF2E7D32)
val TypeRealContainer = Color(0xFFFCE4EC)
val OnTypeReal = Color(0xFFC2185B)
