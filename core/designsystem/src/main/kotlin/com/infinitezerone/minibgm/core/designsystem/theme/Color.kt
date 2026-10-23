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

// Layer 2: 浅色模式表面与容器 (Streamer Light Neutral Surfaces)
val BackgroundLight = Color(0xFFF6F6F7)
val OnBackgroundLight = Color(0xFF18181B)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF18181B)
val SurfaceVariantLight = Color(0xFFEBEBEF)
val OnSurfaceVariantLight = Color(0xFF524346)

val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFF4F4F6)
val SurfaceContainerHighLight = Color(0xFFECECEF)
val SurfaceContainerHighestLight = Color(0xFFE2E2E6)

val SurfaceDimLight = Color(0xFFD6D6DC)
val SurfaceBrightLight = Color(0xFFFFFFFF)

val OutlineLight = Color(0xFF8E8E93)
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
val BackgroundDark = Color(0xFF101012) // 沉浸暗夜纯净黑曜底
val OnBackgroundDark = Color(0xFFF1F3F5) // High-contrast soft white
val SurfaceDark = Color(0xFF101012)
val OnSurfaceDark = Color(0xFFF1F3F5)
val SurfaceVariantDark = Color(0xFF38383F)
val OnSurfaceVariantDark = Color(0xFF9E9EA8) // High-contrast soft neutral grey

val SurfaceContainerLowestDark = Color(0xFF0A0A0C)
val SurfaceContainerLowDark = Color(0xFF26262B) // 现代纯正中性质感浅灰卡片容器 (iOS/Apple/ChatGPT 风格)
val SurfaceContainerDark = Color(0xFF2E2E33)
val SurfaceContainerHighDark = Color(0xFF38383F)
val SurfaceContainerHighestDark = Color(0xFF44444C)

val SurfaceDimDark = Color(0xFF101012)
val SurfaceBrightDark = Color(0xFF44444C)

val OutlineDark = Color(0xFF6E6E78)
val OutlineVariantDark = Color(0x14FFFFFF) // Color.White.copy(alpha = 0.08f)

val InverseSurfaceDark = Color(0xFFF1F3F5)
val InverseOnSurfaceDark = Color(0xFF12141B)
val ScrimDark = Color(0xFF000000)

// ==========================================
// Layer 3: ChatGPT 风格高反差实体操作胶囊角色 (Solid High-Contrast Pill Roles)
// ==========================================
val SolidPillContainerDark = Color(0xFFFFFFFF)
val OnSolidPillContainerDark = Color(0xFF111111)
val SolidPillContainerLight = Color(0xFF111111)
val OnSolidPillContainerLight = Color(0xFFFFFFFF)
