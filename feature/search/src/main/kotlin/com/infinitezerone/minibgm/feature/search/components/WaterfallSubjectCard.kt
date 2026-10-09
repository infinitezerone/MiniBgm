package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.ScoreBadge
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_PORTRAIT_ASPECT_RATIO
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement
import com.infinitezerone.minibgm.feature.search.R

/**
 * 双列瀑布流卡片（海报主导型）：
 * 封面承载评分角标与悬浮「想看」按钮，卡面只保留标题与一行元数据（年份 · 热度/口碑），
 * 标签筛选与社区热评等深层信息收敛到详情页，保证瀑布流的对齐度与扫读效率。
 */
@Composable
fun WaterfallSubjectCard(
    subject: Subject,
    isWished: Boolean,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onToggleWish: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTitle = subject.displayName
    val rating = subject.rating
    val score = rating?.score ?: 0.0
    val rank = rating?.rank ?: 0
    val doingCount = subject.collection?.doing ?: 0

    Card(
        onClick = {
            onSubjectClick(
                SubjectDetailRoute(
                    subjectId = subject.id,
                    initialName = primaryTitle,
                    initialCoverUrl = subject.images?.bestImage.orEmpty(),
                    initialScore = score,
                    source = "explore",
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 封面区域（左上评分角标 + 右下悬浮想看按钮）
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    cornerRadius = 12.dp,
                    aspectRatio = BGM_PORTRAIT_ASPECT_RATIO,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bgmSharedElement(
                                key = BgmSharedElementKeys.subjectCover(subject.id, "explore"),
                                clipInOverlayDuringTransition = RoundedCornerShape(12.dp),
                            ),
                )

                ScoreBadge(
                    score = score,
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .align(Alignment.TopStart),
                )

                // 榜单排名徽标（豆瓣榜单 / AniList Trending 的权威感锚点）：全站排名前 100 才展示
                if (rank in 1..100) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                WishFAB(
                    isWished = isWished,
                    onClick = { onToggleWish(subject.id) },
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd),
                )
            }

            // 2. 信息区：标题 + 一行元数据
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = buildCardMetaLine(subject, doingCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 元数据行：播出年月 · 在追热度（近作）/ 评分人数（长青作） */
@Composable
private fun buildCardMetaLine(
    subject: Subject,
    doingCount: Int,
): String {
    val dateText = subject.date.ifBlank { subject.airDate }.take(7)
    val isRecent = isRecentAiring(subject.date.ifBlank { subject.airDate })
    val parts =
        mutableListOf<String>().apply {
            if (dateText.isNotBlank()) add(dateText)
            if (isRecent && doingCount > 50) {
                add(stringResource(R.string.feature_search_meta_doing, formatCount(doingCount)))
            } else if ((subject.rating?.total ?: 0) > 0) {
                add(stringResource(R.string.feature_search_meta_rating, formatCount(subject.rating!!.total)))
            }
        }
    return parts.joinToString(" · ")
}

/** 封面右下角的悬浮「想看」按钮：视觉 30dp，触控判定区 44dp（满足最小触控目标） */
@Composable
private fun WishFAB(
    isWished: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue =
            if (isWished) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Black.copy(alpha = 0.45f)
            },
        label = "WishFABColor",
    )

    Box(
        modifier =
            modifier
                .size(44.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isWished) BgmIcons.Bookmark else BgmIcons.BookmarkBorder,
                    contentDescription =
                        stringResource(
                            if (isWished) {
                                R.string.feature_search_collection_wished
                            } else {
                                R.string.feature_search_collection_wish
                            },
                        ),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

internal fun formatCount(count: Int): String =
    when {
        count >= 10000 -> {
            val wan = count / 10000.0
            String.format(java.util.Locale.getDefault(), "%.1fw", wan)
        }
        count >= 1000 -> {
            val qian = count / 1000.0
            String.format(java.util.Locale.getDefault(), "%.1fk", qian)
        }
        else -> count.toString()
    }

internal fun isRecentAiring(dateStr: String): Boolean {
    if (dateStr.isBlank()) return false
    val year = dateStr.take(4).toIntOrNull() ?: return false
    val currentYear =
        java.time.LocalDate
            .now()
            .year
    return year >= currentYear
}
