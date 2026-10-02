package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.navigation.BgmSharedElementKeys
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.core.navigation.bgmSharedElement

/** 封面海报宽度：84dp 配合 0.7f 比例（约 120dp 高），呈现典雅海报质感的同时提供充裕的信息排版空间 */
private val ROW_COVER_WIDTH = 84.dp

/** 行内最多展示的题材标签数 */
private const val MAX_GENRE_TAGS = 3

/** 题材标签长度上限 */
private const val MAX_GENRE_TAG_LENGTH = 8

/** 过滤非题材属性的泛化标签 */
private val NON_GENRE_TAG_REGEX = Regex("""^(\d{4}年\d{1,2}月|TV|WEB|OVA|OAD|剧场版|电影)$""")

/**
 * 季度导视「列表模式」精致卡片：
 * 1. 84dp 比例封面海报，左上角标注文创形式（TV/剧场版等），左下角高亮 Bangumi 评分；
 * 2. 标题区双层展示（中文译名 + 日文原名），兼顾大众与资深漫迷识别习惯；
 * 3. 右侧快捷追番药丸（想看 / 已想看 / 在看），状态清晰、触控友好；
 * 4. 首播时间、集数、电视台、Rank 排名与在看热度多维元数据；
 * 5. 题材风格胶囊标签（奇幻 / 冒险 / 治愈）；
 * 6. 2 行剧情简介梗概，让用户无须频繁点击进入详情即可精准扫读新番剧情。
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
    val originalTitle = subject.name.trim()
    val hasOriginalTitle = originalTitle.isNotBlank() && originalTitle != primaryTitle
    val score = subject.rating?.score ?: 0.0
    val rank = subject.rating?.rank ?: 0
    val doingCount = subject.collection?.doing ?: 0
    val airDate = subject.date.ifBlank { subject.airDate }.take(10)
    val platformTag =
        subject.platform.takeIf { it.isNotBlank() && it != "其他" }
            ?: subject.metaTags.firstOrNull { it in setOf("TV", "WEB", "剧场版", "OVA", "OAD") }

    val metaItems =
        remember(subject) {
            listOfNotNull(
                airDate.takeIf { it.isNotBlank() }?.let { if (it.length >= 5) it.substring(5) + " 首播" else it },
                "${subject.eps}话".takeIf { subject.eps > 0 },
                subject.broadcastStation.takeIf { it.isNotBlank() },
                if (rank > 0) "#$rank" else null,
                if (doingCount > 0) "${formatCount(doingCount)}追" else null,
            )
        }

    val genres =
        remember(subject.id, subject.tags) {
            subject.tags
                .map { it.name.trim() }
                .filter {
                    it.isNotBlank() && it.length <= MAX_GENRE_TAG_LENGTH && !NON_GENRE_TAG_REGEX.matches(it)
                }.distinct()
                .take(MAX_GENRE_TAGS)
        }

    val cleanSummary =
        remember(subject.summary) {
            subject.summary
                .replace("\r\n", " ")
                .replace("\n", " ")
                .trim()
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
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 1. 封面海报（叠加形式徽章与评分徽章）
            Box(
                modifier =
                    Modifier
                        .width(ROW_COVER_WIDTH)
                        .bgmSharedElement(
                            key = BgmSharedElementKeys.subjectCover(subject.id, "seasonal_guide"),
                            clipInOverlayDuringTransition = RoundedCornerShape(10.dp),
                        ),
            ) {
                CoverImage(
                    url = subject.images?.bestImage.orEmpty(),
                    contentDescription = primaryTitle,
                    cornerRadius = 10.dp,
                    aspectRatio = 0.7f,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 形式徽章（TV / WEB / 剧场版 等）
                if (!platformTag.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp, topStart = 10.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = platformTag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                // Bangumi 评分徽章
                if (score > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 10.dp),
                        color = Color.Black.copy(alpha = 0.76f),
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = score.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 右侧主体信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // 标题与一键追番胶囊按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Text(
                            text = primaryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (hasOriginalTitle) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (hasOriginalTitle) {
                            Text(
                                text = originalTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 快捷追番药丸（支持触感震动与清晰状态切换）
                    QuickCollectionPill(
                        isWished = isWished,
                        isDoing = isDoing,
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleCollection(
                                subject.id,
                                if (isWished) CollectionType.DOING else CollectionType.WISH,
                            )
                        },
                    )
                }

                // 关键元信息行（首播 · 集数 · 电视台 · Rank · 热度）
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 题材标签小药丸
                if (genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        genres.forEach { genre ->
                            GenreTag(label = genre)
                        }
                    }
                }

                // 剧情梗概简介（扫读找番的核心利器，限 2 行）
                if (cleanSummary.isNotBlank()) {
                    Text(
                        text = cleanSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

private data class CollectionPillStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val label: String,
)

/** 尾部快捷追番胶囊：清晰展示「想看」「已想看」「在看」并提供触感反馈 */
@Composable
private fun QuickCollectionPill(
    isWished: Boolean,
    isDoing: Boolean,
    onToggle: () -> Unit,
) {
    val style =
        when {
            isDoing ->
                CollectionPillStyle(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Filled.Check,
                    label = "在看",
                )
            isWished ->
                CollectionPillStyle(
                    containerColor = RatingGold.copy(alpha = 0.16f),
                    contentColor = RatingGold,
                    icon = Icons.Filled.Bookmark,
                    label = "已想看",
                )
            else ->
                CollectionPillStyle(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = Icons.Filled.BookmarkBorder,
                    label = "想看",
                )
        }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = style.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = style.label,
                tint = style.contentColor,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = style.contentColor,
            )
        }
    }
}

/** 题材标签小药丸；宽度由短词上限保证，不挤占其他信息 */
@Composable
private fun GenreTag(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
