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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
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
        PrimaryTabRow(
            selectedTabIndex = COLLECTION_TYPES.indexOf(selectedType).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        ) {
            COLLECTION_TYPES.forEach { type ->
                val isSelected = selectedType == type
                Tab(
                    selected = isSelected,
                    onClick = { onSelectType(type) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                            )
                            // 计数降一档字号 + 浅色：读作「分类角标」而非指标，
                            // 不与上方通栏数字带的数字争夺视觉重量
                            Spacer(modifier = Modifier.width(3.dp))
                            // 计数未就绪时也占住位置，否则计数到达会让 5 个标签同时左右位移
                            Box(modifier = Modifier.widthIn(min = 10.dp)) {
                                counts[type]?.let { count ->
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            },
                                        maxLines = 1,
                                        softWrap = false,
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

/**
 * 收藏档案馆矩阵（Collection Archives Hub）。
 *
 * 替代死板重复的吸顶单排 Tab，以 2x2 磁贴卡片矩阵组织四大收藏空间：
 * - 🎬 已看完毕 (COLLECT)
 * - ⚡ 正在追更 (DOING)
 * - ⏳ 补番心愿 (WISH)
 * - 📦 封存归档 (ON_HOLD / DROPPED)
 *
 * 点击任一磁贴即可激活并展开下方对应的明细列表与类型筛选。
 */
@Composable
internal fun CollectionArchivesGrid(
    selectedType: CollectionType,
    counts: Map<CollectionType, Int>,
    onSelectType: (CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectCount = counts[CollectionType.COLLECT]?.toString() ?: "—"
    val doingCount = counts[CollectionType.DOING]?.toString() ?: "—"
    val wishCount = counts[CollectionType.WISH]?.toString() ?: "—"
    val onHoldCount = counts[CollectionType.ON_HOLD] ?: 0
    val droppedCount = counts[CollectionType.DROPPED] ?: 0
    val archiveCount =
        if (counts.containsKey(CollectionType.ON_HOLD) || counts.containsKey(CollectionType.DROPPED)) {
            (onHoldCount + droppedCount).toString()
        } else {
            "—"
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "收藏档案馆",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "点击切换展区",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArchiveTile(
                title = "看过",
                subtitle = "已完成的收藏",
                count = collectCount,
                isSelected = selectedType == CollectionType.COLLECT,
                onClick = { onSelectType(CollectionType.COLLECT) },
                modifier = Modifier.weight(1f),
            )
            ArchiveTile(
                title = "在看",
                subtitle = "正在追番 / 观看",
                count = doingCount,
                isSelected = selectedType == CollectionType.DOING,
                onClick = { onSelectType(CollectionType.DOING) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArchiveTile(
                title = "想看",
                subtitle = "追番预定 / 心愿单",
                count = wishCount,
                isSelected = selectedType == CollectionType.WISH,
                onClick = { onSelectType(CollectionType.WISH) },
                modifier = Modifier.weight(1f),
            )
            ArchiveTile(
                title = "搁置 / 抛弃",
                subtitle = "暂停与弃坑",
                count = archiveCount,
                isSelected = selectedType == CollectionType.ON_HOLD || selectedType == CollectionType.DROPPED,
                onClick = { onSelectType(CollectionType.ON_HOLD) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArchiveTile(
    title: String,
    subtitle: String,
    count: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounceState = rememberBounceOnClick(pressedScale = 0.97f)
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier =
            modifier
                .bounceClickable(state = bounceState, onClickLabel = "切换到$title") { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

/** 条目类型筛选行：横向胶囊，不做吸顶，随内容滚出即可 */
@Composable
internal fun SubjectFilterRow(
    selectedFilter: CollectionSubjectFilter,
    onSelectFilter: (CollectionSubjectFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CollectionSubjectFilter.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text(filter.label) },
                border = null,
                // 选中态刻意不用 primaryContainer 实心块：吸顶 Tab 的选中态已经是实心高亮，
                // 两排同构会让人以为「状态」和「条目类型」是同一组筛选器。这里降为淡色底 + 主色字。
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

@Composable
internal fun UserCollectionCard(
    collection: UserCollection,
    isUpdating: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onIncrementProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = collection.subject
    val title = subject?.displayName ?: "条目 #${collection.subjectId}"
    val coverUrl = subject?.images?.bestImage.orEmpty()
    val eps = subject?.eps ?: 0
    val totalEps = subject?.totalEpisodes?.takeIf { it > 0 } ?: eps
    val epStatus = collection.epStatus
    val canIncrement = totalEps == 0 || epStatus < totalEps

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
