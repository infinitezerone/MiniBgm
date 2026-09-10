package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import kotlin.math.roundToInt

/** 条目头部卡片：立体圆角海报、完整译名与原名、年份季度徽章、评分与全站 Rank、可展开简介 */
@Composable
fun SubjectHeaderCard(
    subject: Subject,
    subjectType: SubjectType,
    modifier: Modifier = Modifier,
    sharedElementSource: String = "",
) {
    var isSummaryExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 立体圆角海报：无嵌套容器，与列表源端严格保持相同的宽高比与共享元素规格
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = subject.displayName,
                    cornerRadius = 10.dp,
                    aspectRatio = 0.7f,
                    modifier =
                        Modifier
                            .width(108.dp)
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, sharedElementSource),
                                clipInOverlayDuringTransition = RoundedCornerShape(10.dp),
                            ),
                )

                // 右侧信息区
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = subject.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    if (subject.name.isNotBlank() && subject.name != subject.displayName) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 类型徽章、放送/发行日期与集数标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "${subjectType.iconEmoji} ${subjectType.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                        }

                        val dateText = subject.date.ifBlank { subject.airDate }
                        if (dateText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                )
                            }
                        }

                        val episodeCount = if (subject.eps > 0) subject.eps else subject.totalEpisodes
                        if (episodeCount > 0 && subjectType != SubjectType.GAME) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "全 $episodeCount ${subjectType.unitName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                )
                            }
                        }
                    }

                    // 评分与 Rank 黄金徽章
                    val rating = subject.rating
                    if (rating != null && rating.score > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RatingGold.copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, RatingGold.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = RatingGold,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rating.score.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RatingGold,
                                    )
                                }
                            }

                            if (rating.rank > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "Rank #${rating.rank}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            if (rating.total > 0) {
                                Text(
                                    text = "${rating.total}人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            if (subject.summary.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = subject.summary.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isSummaryExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isSummaryExpanded = !isSummaryExpanded }
                            .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSummaryExpanded) "收起简介" else "展开完整简介",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isSummaryExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

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
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "评分分布",
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
                        text = "分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (rating.total > 0) {
                        Text(
                            text = "(${rating.total}人评分)",
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
            text = "全站收藏状态",
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
                label = "想看",
                count = collection.wish,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            CollectionStatusBadge(
                label = "在看",
                count = collection.doing,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            CollectionStatusBadge(
                label = "看过",
                count = collection.collect,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            CollectionStatusBadge(
                label = "搁置",
                count = collection.onHold,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CollectionStatusBadge(
                label = "抛弃",
                count = collection.dropped,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.outline,
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

/** 热门标签芯片流 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "热门标签",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
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

/** Bangumi 评分说明文案 */
fun getScoreLabel(score: Int): String =
    when (score) {
        1 -> "不忍直视"
        2 -> "很差"
        3 -> "差"
        4 -> "较差"
        5 -> "不过不失"
        6 -> "还行"
        7 -> "推荐"
        8 -> "力荐"
        9 -> "神作"
        10 -> "极品"
        else -> "未评分"
    }

/** 个人追番/阅读/收听/游玩状态与进度卡片 */
@Composable
fun SubjectPersonalProgressCard(
    collection: UserCollection?,
    totalEpisodes: Int,
    subjectType: SubjectType,
    onOpenSheet: () -> Unit,
    onToggleWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEp = collection?.epStatus ?: 0
    val progress = if (totalEpisodes > 0) (currentEp.toFloat() / totalEpisodes).coerceIn(0f, 1f) else 0f

    val cardTitle =
        when (subjectType) {
            SubjectType.BOOK -> "我的阅读与进度"
            SubjectType.MUSIC -> "我的收听与进度"
            SubjectType.GAME -> "我的游玩与评测"
            SubjectType.ANIME, SubjectType.REAL -> "我的追番与进度"
        }

    val actionButtonText =
        if (collection != null) {
            "修改"
        } else {
            when (subjectType) {
                SubjectType.BOOK -> "追读"
                SubjectType.MUSIC -> "收听"
                SubjectType.GAME -> "在玩"
                SubjectType.ANIME, SubjectType.REAL -> "追番"
            }
        }

    Card(
        onClick = onOpenSheet,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (collection != null) {
                        val verb = CollectionType.fromValue(collection.type).getVerb(subjectType)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = verb,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = if (collection != null) onOpenSheet else onToggleWatching,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (collection != null) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionButtonText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (collection != null) {
                if (subjectType == SubjectType.GAME) {
                    val statusVerb = CollectionType.fromValue(collection.type).getVerb(subjectType)
                    Text(
                        text = "游玩状态：$statusVerb",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else if (totalEpisodes > 0 || (subjectType == SubjectType.BOOK && collection.volStatus > 0)) {
                    val progressLabel =
                        when (subjectType) {
                            SubjectType.BOOK -> {
                                val vol = collection.volStatus
                                val ep = collection.epStatus
                                if (vol > 0) "已读 $vol 卷 · $ep 话" else "已读 $ep / 全 $totalEpisodes 话"
                            }
                            SubjectType.MUSIC -> "已听 $currentEp / 全 $totalEpisodes 首"
                            else -> "已看 $currentEp / 全 $totalEpisodes 话"
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (totalEpisodes > 0) {
                            Text(
                                text = "${(progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (totalEpisodes > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (collection.rate > 0) {
                        Text(
                            text = "★ ${collection.rate}分 · ${getScoreLabel(collection.rate)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RatingGold,
                        )
                    }
                    if (collection.comment.isNotBlank()) {
                        Text(
                            text = "「${collection.comment}」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                val idlePrompt =
                    when (subjectType) {
                        SubjectType.BOOK -> "点击记录阅读状态、已读卷数与个人短评"
                        SubjectType.MUSIC -> "点击记录收听状态、已听曲目与个人短评"
                        SubjectType.GAME -> "点击记录游玩状态、通关评价与心得打分"
                        SubjectType.ANIME, SubjectType.REAL -> "点击记录追番状态、更新观看进度与个人打分"
                    }
                Text(
                    text = idlePrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
