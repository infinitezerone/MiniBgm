package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 播放源统一骨架加载卡片。
 * 在首次网络检索或源嗅探时呈现平滑的微光骨架轮廓，杜绝粗暴的全屏空白与文字转圈。
 */
@Composable
fun SourceLoadingSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
    showHeader: Boolean = true,
    headerTitle: String? = null,
    headerSubtitle: String? = null,
) {
    val skeletonState = rememberSkeletonState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeader) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = headerTitle ?: stringResource(R.string.feature_subject_source_loading_resources),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = headerSubtitle ?: stringResource(R.string.feature_subject_source_loading_resources_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        repeat(count) {
            Surface(
                shape = BgmShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 顶栏：分集标识 + 字幕组 + 体积占位（与真实卡片 1:1 对齐）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 44.dp, height = 18.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 96.dp, height = 18.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 46.dp, height = 14.dp),
                                shape = RoundedCornerShape(4.dp),
                                state = skeletonState,
                            )
                        }
                    }

                    // 标题文本占位（两行层次）
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.92f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.58f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }

                    // 规格标签占位
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(width = 44.dp, height = 16.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(width = 40.dp, height = 16.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }

                    // 底部操作按钮行占位：[ ▶ 边播 ] (weight 1) + [ 下载 ] + [ ⋮ ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = BgmShapes.small,
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.width(84.dp).height(34.dp),
                            shape = BgmShapes.small,
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(34.dp),
                            shape = BgmShapes.small,
                            state = skeletonState,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 播放源统一空状态引导卡片。
 * 用于未检索到资源、未配置规则或分集无可用线路时的统一空态呈现，杜绝散落的裸 Text。
 */
@Composable
fun SourceEmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconVector: ImageVector = BgmIcons.Search,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (!actionText.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onActionClick,
                    shape = BgmShapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * 播放源统一错误状态卡片。
 * 提供诚实的错误详情反馈与原位重试按钮。
 */
@Composable
fun SourceErrorStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = BgmIcons.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
            }

            if (onRetry != null) {
                FilledTonalButton(
                    onClick = onRetry,
                    shape = BgmShapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
