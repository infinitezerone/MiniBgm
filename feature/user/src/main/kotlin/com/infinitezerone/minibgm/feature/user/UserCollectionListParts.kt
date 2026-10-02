package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/**
 * 收藏五状态的展示顺序：主动在追的排前面，归档态（搁置 / 抛弃）收尾。
 * 与个人页吸顶 Tab、列表分页加载共用同一份顺序，避免两处口径漂移。
 */
internal val COLLECTION_TYPES =
    listOf(
        CollectionType.DOING,
        CollectionType.WISH,
        CollectionType.COLLECT,
        CollectionType.ON_HOLD,
        CollectionType.DROPPED,
    )

/**
 * 个人页吸顶分区 Tab：状态名 + 收藏计数。
 *
 * 计数来自 [UserCollectionsUiState] 之外的 `collectionCounts`（v0 legacy 单请求 + TTL 缓存），
 * 未加载或未登录时整段后缀不渲染——宁可只有状态名，也不显示 "0" 这种会被误读为「一条都没有」的数字。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionTypeTabs(
    selectedType: CollectionType,
    counts: Map<CollectionType, Int>,
    onSelectType: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = COLLECTION_TYPES.indexOf(selectedType).coerceAtLeast(0),
            edgePadding = 12.dp,
            divider = {},
            modifier = Modifier.fillMaxWidth(),
        ) {
            COLLECTION_TYPES.forEach { type ->
                val isSelected = selectedType == type
                val count = counts[type]
                Tab(
                    selected = isSelected,
                    onClick = { onSelectType(type) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (count != null) {
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 条目类型筛选行：横向精致胶囊，不做吸顶，随内容滚出即可 */
@Composable
internal fun SubjectFilterRow(
    selectedFilter: CollectionSubjectFilter,
    onSelectFilter: (CollectionSubjectFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(CollectionSubjectFilter.entries) { filter ->
            val isSelected = selectedFilter == filter
            Surface(
                onClick = { onSelectFilter(filter) },
                shape = CircleShape,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                border =
                    if (isSelected) {
                        null
                    } else {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    },
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** 连载/完结/囤番多维状态筛选行 */
@Composable
internal fun AirFilterRow(
    selectedFilter: CollectionAirFilter,
    onSelectFilter: (CollectionAirFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(CollectionAirFilter.entries) { filter ->
            val isSelected = selectedFilter == filter
            Surface(
                onClick = { onSelectFilter(filter) },
                shape = CircleShape,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                border =
                    if (isSelected) {
                        null
                    } else {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    },
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp),
                )
            }
        }
    }
}

@Composable
internal fun UserCollectionCard(
    collection: UserCollection,
    isUpdating: Boolean,
    isBinge: Boolean = false,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onIncrementProgress: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    onToggleBinge: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val subject = collection.subject
    val title = subject?.displayName ?: "条目 #${collection.subjectId}"
    val coverUrl = subject?.images?.bestImage.orEmpty()
    val eps = subject?.eps ?: 0
    val totalEps = subject?.totalEpisodes?.takeIf { it > 0 } ?: eps
    val epStatus = collection.epStatus
    val isFinished = collection.isFinished()
    val canIncrement = !isFinished && (totalEps == 0 || epStatus < totalEps)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable {
                    onSubjectClick(
                        SubjectDetailRoute(
                            subjectId = collection.subjectId,
                            initialName = title,
                            initialCoverUrl = coverUrl,
                            initialScore = subject?.rating?.score ?: 0.0,
                            source = "user",
                        ),
                    )
                },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CoverImage(
                url = coverUrl,
                contentDescription = title,
                modifier =
                    Modifier
                        .width(76.dp)
                        .bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(collection.subjectId, "user"),
                            clipInOverlayDuringTransition = RoundedCornerShape(10.dp),
                        ),
                cornerRadius = 10.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val typeName = getSubjectTypeName(subject?.type ?: collection.subjectType)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    val isEffectiveBinge = isBinge && !isFinished
                    val airBadge = collection.airStatusBadge(isEffectiveBinge)
                    val badgeColors =
                        when {
                            isFinished -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                            isEffectiveBinge -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColors.first,
                    ) {
                        Text(
                            text = airBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColors.second,
                            fontWeight = if (isEffectiveBinge || isFinished) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    if (collection.rate > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${collection.rate} 分",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 进度与打卡区（增加精致圆角微进度条）
                if (totalEps > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progressRatio = (epStatus.toFloat() / totalEps).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progressText =
                        if (totalEps > 0) {
                            "$epStatus / $totalEps 话"
                        } else if (epStatus > 0) {
                            "已看 $epStatus 话"
                        } else {
                            "尚未开始"
                        }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val isAnimeOrReal = collection.subjectType == 2 || collection.subjectType == 6
                        if (!isFinished && isAnimeOrReal && onToggleBinge != null) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleBinge()
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = if (isBinge) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                                    contentDescription = if (isBinge) "取消囤番" else "加入囤番",
                                    tint =
                                        if (isBinge) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (onPlayClick != null && isAnimeOrReal) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPlayClick()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tv,
                                        contentDescription = "播放源",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "播放源",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        if (canIncrement || isUpdating) {
                            FilledTonalIconButton(
                                onClick = onIncrementProgress,
                                enabled = !isUpdating && canIncrement,
                                modifier = Modifier.size(30.dp),
                            ) {
                                if (isUpdating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.PlusOne,
                                        contentDescription = "+1 话",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (collection.comment.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = collection.comment,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分页加载骨架：三个卡片高度的占位。
 * 内联在个人页列表里，因此不做 fillMaxSize —— 它只占据「当前分区的列表位」，不是整屏。
 */
@Composable
internal fun CollectionLoadingView(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            CollectionSkeletonCard(skeletonState = skeletonState)
        }
    }
}

@Composable
private fun CollectionSkeletonCard(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(
                modifier = Modifier.size(width = 64.dp, height = 88.dp),
                shape = RoundedCornerShape(8.dp),
                state = skeletonState,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    state = skeletonState,
                )
            }
        }
    }
}

/** 空态：内联列表区块，不占满全屏 */
@Composable
internal fun EmptyCollectionsView(
    message: String = "暂无该分类收藏",
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Text("刷新")
        }
    }
}

/** 错误态：内联列表区块，不占满全屏 */
@Composable
internal fun ErrorCollectionsView(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载收藏失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
internal fun CollectionListFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadedCount: Int,
    modifier: Modifier = Modifier,
) {
    when {
        isLoadingMore ->
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }

        !hasMore && loadedCount >= 50 ->
            Text(
                text = "— 已加载全部收藏 —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
            )
    }
}

internal fun getSubjectTypeName(type: Int): String =
    when (type) {
        1 -> "书籍"
        2 -> "动画"
        3 -> "音乐"
        4 -> "游戏"
        6 -> "三次元"
        else -> "条目"
    }
