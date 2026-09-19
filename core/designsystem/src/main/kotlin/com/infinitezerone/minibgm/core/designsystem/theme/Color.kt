package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// 经典 Bangumi 品牌色基准 (Brand Anchors)
// ==========================================

/** 经典番组樱花粉主色 (Seed) */
val BgmPink = Color(0xFFF09199)
val BgmPinkLight = Color(0xFFFEDFE1)
val BgmPinkDark = Color(0xFFD05A3F)

// ==========================================
// Layer 1: Google Material 3 HCT 浅色角色 (Light Roles)
// ==========================================

val PrimaryLight = Color(0xFFD03567)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFECF0)
val OnPrimaryContainerLight = Color(0xFF5E0026)
val InversePrimaryLight = Color(0xFFFFB1C8)

val SecondaryLight = Color(0xFF75565F)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFDAE2)
val OnSecondaryContainerLight = Color(0xFF2B151C)

val TertiaryLight = Color(0xFF81553F)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDBCF)
val OnTertiaryContainerLight = Color(0xFF321204)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Layer 2: 浅色模式表面与容器 (Streamer Light Surfaces)
val BackgroundLight = Color(0xFFF1F4F8)
val OnBackgroundLight = Color(0xFF191C1E)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF191C1E)
val SurfaceVariantLight = Color(0xFFE8EDF2)
val OnSurfaceVariantLight = Color(0xFF524346)

val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFF6F8FA)
val SurfaceContainerHighLight = Color(0xFFEEF2F6)
val SurfaceContainerHighestLight = Color(0xFFE3E8EF)

val SurfaceDimLight = Color(0xFFD8DEE6)
val SurfaceBrightLight = Color(0xFFFFFFFF)

val OutlineLight = Color(0xFF8A92A0)
val OutlineVariantLight = Color(0x14000000)

val InverseSurfaceLight = Color(0xFF12141B)
val InverseOnSurfaceLight = Color(0xFFF1F3F5)
val ScrimLight = Color(0xFF000000)

// ==========================================
// Layer 1 & 2: Google M3 HCT + ACG 暗夜沉浸角色 (Dark Immersion Roles)
// ==========================================

val PrimaryDark = Color(0xFFFFB0C8)
val OnPrimaryDark = Color(0xFF5E1133)
val PrimaryContainerDark = Color(0xFF7B2949)
val OnPrimaryContainerDark = Color(0xFFFFD9E2)
val InversePrimaryDark = Color(0xFF984061)

val SecondaryDark = Color(0xFFE5BDC6)
val OnSecondaryDark = Color(0xFF432931)
val SecondaryContainerDark = Color(0xFF5B3F47)
val OnSecondaryContainerDark = Color(0xFFFFD9E2)

val TertiaryDark = Color(0xFFF5B79F)
val OnTertiaryDark = Color(0xFF4C2615)
val TertiaryContainerDark = Color(0xFF663D2A)
val OnTertiaryContainerDark = Color(0xFFFFDBCF)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Layer 2: 媒体沉浸暗夜表面 (Content-First & Frame Retreat)
val BackgroundDark = Color(0xFF0E1015) // Deep Charcoal
val OnBackgroundDark = Color(0xFFF1F3F5) // High-contrast soft white
val SurfaceDark = Color(0xFF12141B)
val OnSurfaceDark = Color(0xFFF1F3F5)
val SurfaceVariantDark = Color(0xFF232732)
val OnSurfaceVariantDark = Color(0xFF94A3B8) // High-contrast soft grey

val SurfaceContainerLowestDark = Color(0xFF0E1015)
val SurfaceContainerLowDark = Color(0xFF15181E) // Standard anime card container
val SurfaceContainerDark = Color(0xFF1C2028)
val SurfaceContainerHighDark = Color(0xFF232732)
val SurfaceContainerHighestDark = Color(0xFF2A2F3C)

val SurfaceDimDark = Color(0xFF0E1015)
val SurfaceBrightDark = Color(0xFF2A2F3C)

val OutlineDark = Color(0xFF64748B)
val OutlineVariantDark = Color(0x14FFFFFF) // Color.White.copy(alpha = 0.08f)

val InverseSurfaceDark = Color(0xFFF1F3F5)
val InverseOnSurfaceDark = Color(0xFF12141B)
val ScrimDark = Color(0xFF000000)

// ==========================================
// Layer 3: AMOLED 纯黑双档角色 (AMOLED Pure-Black Roles)
// ==========================================
// 仅替换表面/容器阶梯：品牌色、文本色与深灰档一致（OnSurface F1F3F5 / OnSurfaceVariant
// 94A3B8 在纯黑上对比度更高），真黑省电且与系统导航栏无边界融合。

val BackgroundAmoledDark = Color(0xFF000000)
val SurfaceAmoledDark = Color(0xFF000000)
val SurfaceVariantAmoledDark = Color(0xFF1A1C22)

val SurfaceContainerLowestAmoledDark = Color(0xFF000000)

// 近纯黑阶梯：容器间保留可辨的层级梯度，避免卡片与画布完全融成一团
val SurfaceContainerLowAmoledDark = Color(0xFF0A0A0C)
val SurfaceContainerAmoledDark = Color(0xFF101114)
val SurfaceContainerHighAmoledDark = Color(0xFF16181C)
val SurfaceContainerHighestAmoledDark = Color(0xFF1D1F24)

val SurfaceDimAmoledDark = Color(0xFF000000)
val SurfaceBrightAmoledDark = Color(0xFF24262B)

val InverseOnSurfaceAmoledDark = Color(0xFF000000)
