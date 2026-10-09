package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import com.infinitezerone.minibgm.feature.subject.R

/** 条目头部卡片：立体圆角海报、完整译名与原名、年份季度徽章、评分与全站 Rank、主要制作团队、热门标签、轻量收藏条、可展开简介 */
@Composable
fun SubjectHeaderCard(
    subject: Subject,
    subjectType: SubjectType,
    modifier: Modifier = Modifier,
    sharedElementSource: String = "",
    collection: UserCollection? = null,
    totalEpisodes: Int = 0,
    onOpenCollectionSheet: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
) {
    var isSummaryExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 立体圆角海报：无嵌套容器，与列表源端严格保持相同的宽高比与共享元素规格
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = subject.displayName,
                    cornerRadius = 8.dp,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier =
                        Modifier
                            .width(108.dp)
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, sharedElementSource),
                                clipInOverlayDuringTransition = RoundedCornerShape(8.dp),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 类型徽章、放送/发行日期与集数标签（采用 FlowRow 避免小屏/大字号下胶囊文字被挤压换行）
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = subjectType.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
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
                                    maxLines = 1,
                                    softWrap = false,
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
                                    text =
                                        stringResource(
                                            R.string.feature_subject_total_episodes_format,
                                            episodeCount,
                                            subjectType.unitName,
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
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
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = RatingGold.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Star,
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
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            if (rating.total > 0) {
                                Text(
                                    text = stringResource(R.string.feature_subject_rating_votes_count, rating.total),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    // 核心主创与制作团队（监督/原作/动画制作公司等）
                    val staffHighlights = subject.keyStaff.take(2)
                    if (staffHighlights.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            staffHighlights.forEach { (role, name) ->
                                Text(
                                    text = "$role: $name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 核心分类标签（按热度排名前4）
                    val topTags = subject.tags.filter { it.name.isNotBlank() }.take(4)
                    if (topTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            topTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable { onTagClick?.invoke(tag.name) },
                                ) {
                                    Text(
                                        text = "#${tag.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val trimmedSummary = remember(subject.summary) { subject.summary.trim() }
            if (trimmedSummary.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // 智能段落折叠：Bangumi 条目常以空行分隔首段中文简介与后续日文原文/设定
                val summaryBlocks =
                    remember(trimmedSummary) {
                        trimmedSummary.split(Regex("(?:\r?\n){2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                    }
                val hasMultiParagraphs = summaryBlocks.size > 1
                val firstParagraph = summaryBlocks.firstOrNull().orEmpty()
                val isSingleLongBlock =
                    !hasMultiParagraphs &&
                        (trimmedSummary.length > 120 || trimmedSummary.count { it == '\n' } >= 3)
                val canExpand = hasMultiParagraphs || isSingleLongBlock

                val displayText =
                    when {
                        !canExpand -> trimmedSummary
                        isSummaryExpanded -> trimmedSummary
                        hasMultiParagraphs -> firstParagraph
                        else -> trimmedSummary
                    }

                val maxLines =
                    if (isSummaryExpanded || !canExpand || hasMultiParagraphs) {
                        Int.MAX_VALUE
                    } else {
                        4
                    }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(),
                )

                if (canExpand) {
                    val expandLabel =
                        when {
                            isSummaryExpanded -> stringResource(R.string.feature_subject_collapse_summary)
                            hasMultiParagraphs -> stringResource(R.string.feature_subject_expand_summary_with_original)
                            else -> stringResource(R.string.feature_subject_expand_summary)
                        }
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
                            text = expandLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = if (isSummaryExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
