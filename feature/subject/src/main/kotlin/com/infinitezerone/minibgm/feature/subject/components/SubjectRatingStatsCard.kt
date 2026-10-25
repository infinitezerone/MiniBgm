package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.ratingTextColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDroppedContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusOnHoldContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusWishContainerColor
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 评分分布、全站收藏分布与热门标签：决策上下文以自然流坐在页面背景上，
 * 用分组标题与留白组织，不套整块灰卡——卡片只留给可独立点击的对象。
 */
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // a. 评分分布直方图：分数本身就是这一段的视觉锚点
        if (rating != null && (rating.total > 0 || rating.count.isNotEmpty())) {
            RatingDistributionSection(rating = rating)
        }

        // b. 全站收藏状态分布：小节标题到内容统一 8dp，与热门标签小节同构
        if (collection != null &&
            (collection.wish > 0 || collection.collect > 0 || collection.doing > 0 || collection.onHold > 0 || collection.dropped > 0)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.feature_subject_all_collections_status),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CollectionStatsSection(collection = collection)
            }
        }

        // c. 热门标签
        if (hasTags) {
            TagsSection(tags = tags, onTagClick = onTagClick)
        }
    }
}

/**
 * 评分分布：紧凑锚点（星标分数 + 人数 + 好评率）与比例直方图。
 * 头部已展示分数徽章，这里不再放全页最大的数字；众数由直方图的金色分段直接可视化。
 */
@Composable
private fun RatingDistributionSection(
    rating: Rating,
    modifier: Modifier = Modifier,
) {
    val counts =
        (1..10).map { score ->
            score to (rating.count[score.toString()] ?: 0)
        }
    val totalCount = if (rating.total > 0) rating.total else counts.sumOf { it.second }
    val maxCount = counts.maxOfOrNull { it.second } ?: 0
    val modeScore = if (maxCount > 0) counts.maxByOrNull { it.second }?.first else null

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    tint = RatingGold,
                    modifier = Modifier.size(16.dp),
                )
                if (rating.score > 0.0) {
                    Text(
                        text = rating.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ratingTextColor(),
                    )
                }
                if (totalCount > 0) {
                    Text(
                        text = stringResource(R.string.feature_subject_rating_votes_parenthesis, totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (totalCount > 0) {
                val highScores = counts.filter { it.first >= 8 }.sumOf { it.second }
                val highScorePercent = ((highScores.toFloat() / totalCount.toFloat()) * 100f).toInt()
                Text(
                    text = stringResource(R.string.feature_subject_rating_positive_ratio_format, highScorePercent),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ratingTextColor(),
                )
            }
        }

        // 比例分段条 (8dp 高度，4dp 圆角，众数分段用纯金高亮)
        if (totalCount > 0) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                counts.forEach { (score, count) ->
                    if (count > 0) {
                        val weight = count.toFloat() / totalCount.toFloat()
                        val isMode = score == modeScore
                        Box(
                            modifier =
                                Modifier
                                    .weight(weight.coerceAtLeast(0.01f))
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            isMode -> RatingGold
                                            score >= 8 -> RatingGold.copy(alpha = 0.65f)
                                            score >= 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/** 收藏分布切片：比例条与图例共用同一份数据 */
private data class CollectionSlice(
    val label: String,
    val count: Int,
    val color: Color,
)

/** 全站收藏状态分布：一条五段比例条加五列图例，替代易换行的徽章阵列 */
@Composable
private fun CollectionStatsSection(
    collection: CollectionCount,
    modifier: Modifier = Modifier,
) {
    val slices =
        listOf(
            CollectionSlice(stringResource(R.string.feature_subject_status_wish), collection.wish, statusWishContainerColor()),
            CollectionSlice(stringResource(R.string.feature_subject_status_doing), collection.doing, statusDoingContainerColor()),
            CollectionSlice(stringResource(R.string.feature_subject_status_collect), collection.collect, statusCollectContainerColor()),
            CollectionSlice(stringResource(R.string.feature_subject_status_on_hold), collection.onHold, statusOnHoldContainerColor()),
            CollectionSlice(stringResource(R.string.feature_subject_status_dropped), collection.dropped, statusDroppedContainerColor()),
        )
    val total = slices.sumOf { it.count }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (total > 0) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                slices
                    .filter { it.count > 0 }
                    .forEach { slice ->
                        Box(
                            modifier =
                                Modifier
                                    .weight(slice.count.toFloat())
                                    .fillMaxHeight()
                                    .background(slice.color),
                        )
                    }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slices.forEach { slice ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(slice.color),
                        )
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = slice.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private const val COLLAPSED_TAGS_LIMIT = 10

/** 概览页热门标签卡片 */
@Composable
fun OverviewTagsCard(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        TagsSection(
            tags = tags,
            onTagClick = onTagClick,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 热门标签芯片流：支持过多时默认折叠（前 10 个），支持平滑展开与收起 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsSection(
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

        // 标签是紧凑元数据芯片，禁止用 Surface(onClick) 承载：它会无条件应用 48dp 最小
        // 交互尺寸，21dp 高的芯片布局被撑到 48dp，FlowRow 按布局高度排行会多出约 27dp
        // 的隐形行距。用 clip+background+clickable 的普通 Box，行距完全交给 FlowRow。
        FlowRow(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            displayedTags.forEach { tag ->
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onTagClick(tag.name) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(
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
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .clickable { isExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
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
