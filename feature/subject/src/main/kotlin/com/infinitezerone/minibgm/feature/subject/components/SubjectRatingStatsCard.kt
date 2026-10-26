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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusCollect
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDoing
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDropped
import com.infinitezerone.minibgm.core.designsystem.theme.StatusOnHold
import com.infinitezerone.minibgm.core.designsystem.theme.StatusWish
import com.infinitezerone.minibgm.core.designsystem.theme.ratingTextColor
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
 * 评分分布：影评级口碑总览卡，左侧为大号评分与口碑指标，右侧为 10 档经典垂直波峰柱状图。
 * 清晰可视化众数与受众口碑走势，0 票客观归零（仅底线无柱体），支持点击柱体即时下钻票数。
 */
@Composable
internal fun RatingDistributionSection(
    rating: Rating,
    modifier: Modifier = Modifier,
) {
    val counts =
        (1..10).map { score ->
            score to (rating.count[score.toString()] ?: 0)
        }
    val totalCount = if (rating.total > 0) rating.total else counts.sumOf { it.second }
    val maxCount = (counts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val modeScore = if (maxCount > 1) counts.maxByOrNull { it.second }?.first else null

    var selectedScore by rememberSaveable { mutableStateOf<Int?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧：大号评分与口碑指标（点击柱子可下钻具体票数）
        Column(
            modifier = Modifier.widthIn(min = 110.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = BgmIcons.Star,
                    contentDescription = null,
                    tint = RatingGold,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = if (rating.score > 0.0) rating.score.toString() else "-",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ratingTextColor(),
                )
            }

            if (totalCount > 0) {
                val currentSelected = selectedScore
                if (currentSelected != null) {
                    val selCount = counts.firstOrNull { it.first == currentSelected }?.second ?: 0
                    val selPercent = if (totalCount > 0) (selCount.toFloat() / totalCount.toFloat()) * 100f else 0f
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.feature_subject_rating_score_detail,
                                    currentSelected,
                                    selCount,
                                    selPercent,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    val highScores = counts.filter { it.first >= 8 }.sumOf { it.second }
                    val highScorePercent = ((highScores.toFloat() / totalCount.toFloat()) * 100f).toInt()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_rating_votes_parenthesis, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = stringResource(R.string.feature_subject_rating_positive_ratio_format, highScorePercent),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        // 右侧：10 档经典垂直波峰柱状图（自适应等宽分布，0 票真实归零，支持点击下钻）
        if (totalCount > 0) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                counts.forEach { (score, count) ->
                    val isMode = score == modeScore
                    val isSelected = score == selectedScore
                    val isZero = count == 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedScore = if (selectedScore == score) null else score
                                },
                    ) {
                        // 柱体可视区：固定 36dp 高度，底部对齐
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // 0 票刻度基线：客观标示刻度底线，高度 1.5dp
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(1.5.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(0.dp),
                                        ),
                            )

                            // 真实柱体：仅当 count > 0 时才绘制有效高度，绝不伪造 0 票柱体
                            if (!isZero) {
                                val heightFraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                                val barHeight = (36.dp * heightFraction).coerceIn(4.dp, 36.dp)
                                val barColor =
                                    when {
                                        isSelected -> RatingGold
                                        selectedScore != null -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                        isMode -> RatingGold
                                        score >= 8 -> RatingGold.copy(alpha = 0.7f)
                                        score >= 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(barHeight)
                                            .background(
                                                color = barColor,
                                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                            ),
                                )
                            }
                        }

                        // 标尺文字 (1..10)
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color =
                                when {
                                    isSelected -> RatingGold
                                    selectedScore == null && isMode -> RatingGold
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                },
                            fontWeight =
                                if (isSelected || (selectedScore == null && isMode)) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            modifier = Modifier.padding(top = 2.dp),
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
            CollectionSlice(stringResource(R.string.feature_subject_status_wish), collection.wish, StatusWish),
            CollectionSlice(stringResource(R.string.feature_subject_status_doing), collection.doing, StatusDoing),
            CollectionSlice(stringResource(R.string.feature_subject_status_collect), collection.collect, StatusCollect),
            CollectionSlice(stringResource(R.string.feature_subject_status_on_hold), collection.onHold, StatusOnHold),
            CollectionSlice(stringResource(R.string.feature_subject_status_dropped), collection.dropped, StatusDropped),
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
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
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

/** 热门标签芯片流：标题栏右侧统领展开收起，流内统一等高规整排版（8dp呼吸间距） */
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_popular_tags, tags.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (canCollapse) {
                SubjectSectionToggleAction(
                    isExpanded = isExpanded,
                    onToggle = { isExpanded = !isExpanded },
                    totalCount = tags.size,
                )
            }
        }

        // 标签是紧凑元数据芯片，使用 8dp 规整间距与 10dp/4dp 饱满内边距，统一高度与基线
        FlowRow(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            displayedTags.forEach { tag ->
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onTagClick(tag.name) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
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
