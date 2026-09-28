package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/** 封面缩略图宽度：行高由它决定（2:3 竖图约 83dp），也是"一屏可见 6-7 条"的密度来源 */
private val ROW_COVER_WIDTH = 58.dp

/** 行内最多展示的题材标签数：再多就会挤掉标题的横向空间 */
private const val MAX_GENRE_TAGS = 3

/** 题材标签的朴素判据——短词。长词几乎都是制作公司／原著名（StudioBind、BiburyAnimationStudios），行内也放不下 */
private const val MAX_GENRE_TAG_LENGTH = 8

/** 档期与放送形式已由顶部"档期胶囊 + 播出形式筛选条"表达，行内不再重复这类标签 */
private val NON_GENRE_TAG_REGEX = Regex("""^(\d{4}年\d{1,2}月|TV|WEB|OVA|OAD|剧场版|电影)$""")

/**
 * 季度导视「紧凑行式」条目。
 *
 * 与 [SeasonalAnimeCard] 的取舍：放弃 2:3 大图展板，换成一屏约 6-7 条的可扫读密度——
 * 标题独占剩余宽度（长标题两行），第二行"集数 · 放送电视台"，第三行题材标签。
 * 评分角标压在封面上（不额外占位），追番收成尾部图标按钮（行内塞整宽按钮会把行高撑回卡片级）。
 *
 * 追番语义与 [SeasonalAnimeCard] 封面上的书签完全一致（想看 → 在看），避免同一图标在两种视图里
 * 行为分叉；本页没有"取消收藏"动作，重复点击由 ViewModel 以提示语拦下。
 */
@Composable
fun SeasonalAnimeRow(
    subject: Subject,
    isWished: Boolean,
    isDoing: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleCollection: (Long, CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val primaryTitle = subject.displayName
    val score = subject.rating?.score ?: 0.0
    val meta =
        listOfNotNull(
            "${subject.eps}话".takeIf { subject.eps > 0 },
            subject.broadcastStation.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    val genres =
        remember(subject.id, subject.tags) {
            subject.tags
                .map { it.name.trim() }
                .filter {
                    it.isNotBlank() && it.length <= MAX_GENRE_TAG_LENGTH && !NON_GENRE_TAG_REGEX.matches(it)
                }.distinct()
                .take(MAX_GENRE_TAGS)
        }

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = score,
                    source = "seasonal_guide",
                ),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. 封面缩略图 + 评分角标（与海报卡片同一共享元素键，两种视图都能做转场）
            Box {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    cornerRadius = 8.dp,
                    aspectRatio = 0.7f,
                    modifier =
                        Modifier
                            .width(ROW_COVER_WIDTH)
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, "seasonal_guide"),
                                clipInOverlayDuringTransition = RoundedCornerShape(8.dp),
                            ),
                )

                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp, topStart = 8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(9.dp),
                            )
                            Text(
                                text = score.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // 2. 标题 / 集数·电视台 / 题材标签
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (genres.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        genres.forEach { genre -> GenreTag(label = genre) }
                    }
                }
            }

            // 3. 尾部一键追番（与海报卡片封面书签同语义：想看 → 在看）
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleCollection(
                        subject.id,
                        if (isWished) CollectionType.DOING else CollectionType.WISH,
                    )
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector =
                        when {
                            isDoing -> Icons.Filled.Check
                            isWished -> Icons.Filled.Bookmark
                            else -> Icons.Filled.BookmarkBorder
                        },
                    contentDescription =
                        when {
                            isDoing -> "在看"
                            isWished -> "想看"
                            else -> "标记想看"
                        },
                    tint =
                        when {
                            isDoing -> MaterialTheme.colorScheme.primary
                            isWished -> RatingGold
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 题材标签小药丸；宽度由短词上限保证，不会挤掉标题 */
@Composable
private fun GenreTag(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
