package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// ACG 收藏与状态语义色 (Radix / Perception-calibrated)
// ==========================================

/** 想看 (Wish) - 柔和鸢尾紫 (Soft Indigo-400) */
val StatusWish = Color(0xFF818CF8)

/** 在看 (Doing) - 薄荷生机翠绿 (Mint Glow Emerald-400) */
val StatusDoing = Color(0xFF34D399)

/** 看过 (Collect) - 经典樱花粉桃 (Sakura Rose-400) */
val StatusCollect = Color(0xFFF472B6)

/** 搁置 (On Hold) - 温暖琥珀黄 (Warm Amber-400) */
val StatusOnHold = Color(0xFFFBBF24)

/** 抛弃 (Dropped) - 低敏灰蓝板岩 (Muted Slate-400) */
val StatusDropped = Color(0xFF94A3B8)

// ==========================================
// 放送状态与徽章
// ==========================================

/** 放送中（正在播出） - 活力珊瑚橙 */
val StatusAiring = Color(0xFFFF5722)

/** 经典口碑徽章（非近期热播、以评分人数取胜） */
val BadgeClassic = Color(0xFF3F51B5)

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
