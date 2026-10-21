package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens

/**
 * Timeless Obsidian / Apple TV+ 式全景沉浸焦点卡 (Hero Focus Card)
 *
 * 遵循 screen1_home_timeless.png 终稿标准：
 * - 整张卡片就是完整的 16:9 电影级海报，无任何死板外框或拦腰截断的黑块；
 * - 底部自适应微暗渐隐过渡（Atmospheric Gradient Scrim），文字与控制件直接浮在海报上；
 * - 左侧常驻纯白实体药丸操作键 [▶ 继续观看]，右侧显示等宽时间戳；
 * - 纯净交互（零强制震动）。
 */
@Composable
fun BgmFocusCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
    statusLabel: String? = null,
    progress: Float? = null,
    progressLabel: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = BgmContentTokens.FocusCard.shape,
        color = BgmContentTokens.FocusCard.containerColor(),
        modifier =
            modifier
                .expressivePress(
                    interactionSource = interactionSource,
                    pressedScale = 0.985f,
                ).fillMaxWidth()
                .clip(BgmContentTokens.FocusCard.shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f),
        ) {
            // 1. 全幅海报底图（占满整卡）
            Box(modifier = Modifier.fillMaxSize()) {
                cover()
            }

            // 2. 底部电影级暗光氛围渐隐层（保证白色文字与操作键在任何海报下清晰可读）
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0.0f to Color.Transparent,
                                        0.35f to Color.Transparent,
                                        0.65f to Color(0x66000000),
                                        0.85f to Color(0xB3000000),
                                        1.0f to Color(0xE6000000),
                                    ),
                            ),
                        ),
            )

            // 3. 左上角更新状态微胶囊
            if (statusLabel != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                ) {
                    Text(
                        text = statusLabel,
                        style = BgmContentTokens.Typography.microLabel,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // 4. 下部浮动内容区（直接悬浮于海报之上）
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // 标题（纯白，强反差）
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            letterSpacing = (-0.2).sp,
                        ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 副标题（冷银灰）
                Text(
                    text = subtitle,
                    style =
                        BgmContentTokens.Typography.subtleSubtitle.copy(
                            color = Color.White.copy(alpha = 0.75f),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 进度条
                if (progress != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 底部动作行：左侧纯白药丸键，右侧等宽播放时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (action != null) {
                        action()
                    } else {
                        BgmPillButton(
                            text = "继续观看",
                            icon = BgmIcons.Play,
                            onClick = onClick,
                        )
                    }

                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style =
                                BgmContentTokens.Typography.tabularCaption.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.5.sp,
                                ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
