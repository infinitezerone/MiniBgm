package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDroppedContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusOnHoldContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusWishContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDroppedContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusOnHoldContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusWishContainerColor
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.feature.subject.R

/** 评分分布柱状图、全站收藏分布与热门标签卡片 */
@Composable
fun RatingDistributionCard(
    rating: Rating?,
    collection: CollectionCount?,
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasRatingData = rating != null && (rating.total > 0 || rating.count.isNotEmpty())
    val hasCollectionData =
        collection != null &&
            (collection.wish > 0 || collection.collect > 0 || collection.doing > 0 || collection.onHold > 0 || collection.dropped > 0)
    val hasTags = tags.isNotEmpty()

    if (!hasRatingData && !hasCollectionData && !hasTags) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // a. 评分分布柱状图
            if (rating != null && (rating.total > 0 || rating.count.isNotEmpty())) {
                RatingDistributionSection(rating = rating)
            }

            // 分割线
            if (hasRatingData && (hasCollectionData || hasTags)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // b. 全站收藏状态分布
            if (collection != null &&
                (collection.wish > 0 || collection.collect > 0 || collection.doing > 0 || collection.onHold > 0 || collection.dropped > 0)
            ) {
                CollectionStatsSection(collection = collection)
            }

            // 分割线
            if (hasCollectionData && hasTags) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // c. 热门标签
            if (hasTags) {
                TagsSection(tags = tags, onTagClick = onTagClick)
            }
        }
    }
}

/** 评分分布柱状图：10 根垂直柱状图、分数标签、mode 主色高亮 */
@Composable
private fun RatingDistributionSection(
    rating: Rating,
    modifier: Modifier = Modifier,
) {
    val counts =
        (1..10).map { score ->
            score to (rating.count[score.toString()] ?: 0)
        }
    val maxCount = counts.maxOfOrNull { it.second } ?: 0
    val modeScore = if (maxCount > 0) counts.maxByOrNull { it.second }?.first else null

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.feature_subject_rating_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (rating.score > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = rating.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.feature_subject_score_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (rating.total > 0) {
                        Text(
                            text = stringResource(R.string.feature_subject_rating_votes_parenthesis, rating.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 10 根垂直柱状图 (1..10 分)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { (score, count) ->
                val isMode = score == modeScore && count > 0
                val ratio = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (count > 0) formatCompactNumber(count) else "",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                            ),
                        fontWeight = if (isMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .width(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )

                        if (count > 0) {
                            val barHeightFraction = (ratio * 0.92f + 0.08f).coerceIn(0.08f, 1f)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxHeight(barHeightFraction)
                                        .width(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isMode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            },
                                        ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isMode) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (isMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

/** 全站收藏状态分布：想看、在看、看过、搁置、抛弃 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionStatsSection(
    collection: CollectionCount,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_subject_all_collections_status),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollectionStatusBadge(
                label = stringResource(R.string.feature_subject_status_wish),
                count = collection.wish,
                containerColor = statusWishContainerColor(),
                contentColor = onStatusWishContainerColor(),
            )
            CollectionStatusBadge(
                label = stringResource(R.string.feature_subject_status_doing),
                count = collection.doing,
                containerColor = statusDoingContainerColor(),
                contentColor = onStatusDoingContainerColor(),
            )
            CollectionStatusBadge(
                label = stringResource(R.string.feature_subject_status_collect),
                count = collection.collect,
                containerColor = statusCollectContainerColor(),
                contentColor = onStatusCollectContainerColor(),
            )
            CollectionStatusBadge(
                label = stringResource(R.string.feature_subject_status_on_hold),
                count = collection.onHold,
                containerColor = statusOnHoldContainerColor(),
                contentColor = onStatusOnHoldContainerColor(),
            )
            CollectionStatusBadge(
                label = stringResource(R.string.feature_subject_status_dropped),
                count = collection.dropped,
                containerColor = statusDroppedContainerColor(),
                contentColor = onStatusDroppedContainerColor(),
            )
        }
    }
}

/** 收藏状态小徽章 */
@Composable
private fun CollectionStatusBadge(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

private const val COLLAPSED_TAGS_LIMIT = 10

/** 热门标签芯片流：支持过多时默认折叠（前 10 个），支持平滑展开与收起 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val canCollapse = tags.size > COLLAPSED_TAGS_LIMIT
    val displayedTags = if (isExpanded || !canCollapse) tags else tags.take(COLLAPSED_TAGS_LIMIT)
    val hiddenCount = tags.size - COLLAPSED_TAGS_LIMIT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_popular_tags, tags.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (canCollapse) {
                Surface(
                    onClick = { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text =
                                if (isExpanded) {
                                    stringResource(
                                        R.string.feature_subject_collapse,
                                    )
                                } else {
                                    stringResource(R.string.feature_subject_expand_all)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            displayedTags.forEach { tag ->
                Surface(
                    onClick = { onTagClick(tag.name) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (tag.count > 0) {
                            Text(
                                text = tag.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (!isExpanded && canCollapse) {
                Surface(
                    onClick = { isExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_more_tags_count, hiddenCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** 紧凑数字格式化（如 1.8k, 15k） */
fun formatCompactNumber(number: Int): String =
    when {
        number >= 10_000 -> "${number / 1000}k"
        number >= 1_000 -> "${number / 1000}.${(number % 1000) / 100}k"
        number > 0 -> number.toString()
        else -> "0"
    }
