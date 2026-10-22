package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens

/**
 * ChatGPT 风格全幅平铺列表行 (Flat Edge-to-Edge Row)
 *
 * 丢弃外层厚重卡片，直接在页面底板上平铺延伸：
 * - 适合高密度内容（排期列表、分集列表、历史记录等）；
 * - 紧凑规整、滑动平滑；
 * - 极弱发丝分隔线（带左侧缩进对齐文本）；
 * - 纯净交互（零强制震动）。
 */
@Composable
fun BgmFlatRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(BgmContentTokens.FlatRow.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BgmContentTokens.FlatRow.itemGap),
        ) {
            // 1. 左侧缩略图/图标槽位
            if (leading != null) {
                leading()
            }

            // 2. 中间文本信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BgmContentTokens.FlatRow.spaceBetweenLines),
            ) {
                Text(
                    text = title,
                    style = BgmContentTokens.Typography.focalTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = BgmContentTokens.Typography.subtleSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 3. 右侧状态/小动作槽位
            if (trailing != null) {
                trailing()
            }
        }

        // 4. 极弱发丝分隔线（从文本起始位置缩进）
        if (showDivider) {
            HorizontalDivider(
                modifier =
                    Modifier.padding(
                        start =
                            if (leading != null) {
                                BgmContentTokens.FlatRow.horizontalPadding +
                                    BgmContentTokens.FlatRow.thumbWidth +
                                    BgmContentTokens.FlatRow.itemGap
                            } else {
                                BgmContentTokens.FlatRow.horizontalPadding
                            },
                        end = BgmContentTokens.FlatRow.horizontalPadding,
                    ),
                thickness = BgmContentTokens.FlatRow.dividerHeight,
                color = BgmContentTokens.FlatRow.dividerColor(),
            )
        }
    }
}
