package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MiniBgm 内容 UI 令牌系统 (Content UI Design Tokens)
 *
 * 融合 ChatGPT 极简瑞士排版与 ColorOS 15/17 水生连续张力美学：
 * 1. 焦点卡 (Focus Card)：贴顶全幅大图（Full-bleed Header），淘汰 1px 亮灰外圈硬边，连续平滑大圆角。
 * 2. 平铺行 (Flat Row)：无框平铺透气到底，微弱缩进发丝线，杜绝卡片千层饼。
 * 3. 实体药丸键 (Solid Pill Button)：40dp 黄金饱满胶囊，深色纯白高反差，触觉分量极强。
 * 4. 紧凑字阶 (Dense Typography)：16sp 主标题、13.5sp 副标、等宽防抖时间戳。
 */
object BgmContentTokens {
    /** 核心焦点卡片（接着看 Next Up 等大焦点区块） */
    object FocusCard {
        val shape: Shape = RoundedCornerShape(18.dp)

        /** 贴顶全幅海报的圆角：仅上两角跟随外卡连续圆角，左右与顶边贴合全幅 */
        val mediaShape: Shape =
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )

        val mediaAspectRatio: Float = 16f / 9f

        /** 下方文字与操作区内边距（海报本身全幅无外框围困） */
        val contentPadding: PaddingValues =
            PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)

        val progressHeight: Dp = 3.dp
        val spaceBetweenTitleAndSubtitle: Dp = 4.dp
        val spaceBetweenInfoAndActions: Dp = 14.dp

        /** 容器色：采用 SurfaceContainerLow 实色明度差自然浮起，零描边 */
        @Composable
        @ReadOnlyComposable
        fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow
    }

    /** 全幅平铺列表行（今日更新排期、分集列表、历史记录等） */
    object FlatRow {
        val horizontalPadding: Dp = 16.dp
        val verticalPadding: Dp = 12.dp
        val contentPadding: PaddingValues =
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)

        val minHeight: Dp = 72.dp
        val thumbCornerRadius: Dp = 8.dp
        val thumbShape: Shape = RoundedCornerShape(8.dp)
        val thumbWidth: Dp = 48.dp
        val thumbAspectRatio: Float = 0.7f // 约 5:7 黄金立绘比例
        val itemGap: Dp = 14.dp
        val spaceBetweenLines: Dp = 3.dp
        val dividerHeight: Dp = 0.5.dp

        /** 分隔线颜色：极低透明度，确保平滑透气不切割视觉 */
        @Composable
        @ReadOnlyComposable
        fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    }

    /** 实体药丸操作键令牌 (ChatGPT / ColorOS 饱满大胶囊) */
    object PillButton {
        /** 40dp 黄金饱满高度，完美符合人体工学与视觉分量 */
        val height: Dp = 40.dp
        val compactHeight: Dp = 30.dp
        val largeHeight: Dp = 46.dp
        val shape: Shape = CircleShape

        /** 宽裕内边距，确保胶囊舒展大气 */
        val horizontalPadding: Dp = 18.dp
        val compactHorizontalPadding: Dp = 12.dp
        val largeHorizontalPadding: Dp = 22.dp

        val iconSize: Dp = 16.dp
        val compactIconSize: Dp = 13.dp
        val iconSpacing: Dp = 6.dp

        @Composable
        @ReadOnlyComposable
        fun primaryContainerColor(): Color =
            if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                SolidPillContainerDark
            } else {
                SolidPillContainerLight
            }

        @Composable
        @ReadOnlyComposable
        fun onPrimaryContainerColor(): Color =
            if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                OnSolidPillContainerDark
            } else {
                OnSolidPillContainerLight
            }

        @Composable
        @ReadOnlyComposable
        fun secondaryContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

        @Composable
        @ReadOnlyComposable
        fun onSecondaryContainerColor(): Color = MaterialTheme.colorScheme.onSurface
    }

    /** 紧凑微排版与等宽字阶 (Dense & Tabular Typography) */
    object Typography {
        /** 焦点标题 (16sp, SemiBold, 紧凑字距) */
        val focalTitle: TextStyle =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp,
            )

        /** 舒展副标题 (13.5sp, Medium) */
        val subtleSubtitle: TextStyle =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )

        /** 等宽紧凑元数据 (12sp, Regular, 等宽)：用于倒计时、时间戳、进度 */
        val tabularCaption: TextStyle =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.15.sp,
                fontFeatureSettings = "tnum",
            )

        /** 微型角标 (10sp, Bold, 等宽)：用于海报评分徽章、集数微角标 */
        val microLabel: TextStyle =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum",
            )
    }

    /** 状态小圆点与微型徽章 */
    object StatusDot {
        val size: Dp = 6.dp
        val ringSize: Dp = 10.dp
    }
}
