package com.infinitezerone.minibgm.feature.subject.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmPillVariant
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.component.formatScore
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.ratingTextColor
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/** 关联条目分类枚举（对齐 Bangumi 官网分类规则） */
enum class RelationCategory(
    @StringRes val labelRes: Int,
) {
    MAIN_STORY(R.string.feature_subject_relation_cat_main_story),
    ORIGINAL(R.string.feature_subject_relation_cat_original),
    MUSIC(R.string.feature_subject_relation_cat_music),
    SPINOFF(R.string.feature_subject_relation_cat_spinoff),
    GAME(R.string.feature_subject_relation_cat_game),
    OTHER(R.string.feature_subject_relation_cat_other),
    ;

    companion object {
        fun classify(relation: SubjectRelation): RelationCategory {
            val rel = relation.relation.trim()
            val type = relation.type

            // 1. 原声音乐：类型为 3 或含音乐相关关键词
            if (type == 3 ||
                rel in MUSIC_KEYWORDS ||
                rel.contains("曲") ||
                rel.contains("歌") ||
                rel.contains("原声") ||
                rel.contains("广播") ||
                rel.contains("OST", ignoreCase = true)
            ) {
                return MUSIC
            }
            // 2. 改编原著：关键词或书籍类型
            if (rel in ORIGINAL_KEYWORDS || (type == 1 && (rel in BOOK_KEYWORDS || rel.contains("漫画") || rel.contains("小说")))) {
                return ORIGINAL
            }
            // 3. 游戏：类型为 4 或关键词
            if (type == 4 || rel.contains("游戏")) {
                return GAME
            }
            // 4. 正传/续作：前传、续集、主线、相同世界观等
            if (rel in MAIN_STORY_KEYWORDS) {
                return MAIN_STORY
            }
            // 5. 衍生/特典：番外、特典、OVA、剧场版、短片等
            if (rel in SPINOFF_KEYWORDS) {
                return SPINOFF
            }
            return OTHER
        }

        private val MUSIC_KEYWORDS = setOf("片头曲", "片尾曲", "原声集", "角色歌", "插入歌", "印象曲", "广播剧", "主题歌", "OST")
        private val ORIGINAL_KEYWORDS = setOf("原著", "前传原著", "续集原著", "原作")
        private val BOOK_KEYWORDS = setOf("漫画", "小说", "书籍", "画集")
        private val MAIN_STORY_KEYWORDS = setOf("前传", "续集", "全集", "主线故事", "相同世界观", "不同世界观", "总集篇", "正篇")
        private val SPINOFF_KEYWORDS = setOf("番外篇", "侧线故事", "短片", "衍生", "特典", "OVA", "剧场版", "其他外传")
    }
}

/** 主线系列时间轴条目模型 */
data class TimelineStoryNode(
    val id: Long,
    val name: String,
    val relationLabel: String,
    val coverUrl: String?,
    val score: Double,
    val isCurrent: Boolean,
)

/** 关联作品区域：支持按正传/续作、原声音乐、改编原著等细粒度分类过滤，支持横滑与海报墙视图切换 */
@Composable
fun RelationsSection(
    relations: List<SubjectRelation>,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    currentSubjectId: Long? = null,
    currentSubjectName: String = "",
    currentSubjectCover: String? = null,
    currentSubjectScore: Double = 0.0,
) {
    if (relations.isEmpty()) return

    val uniqueRelations = remember(relations) { relations.distinctBy { "${it.id}_${it.relation}" } }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val thisWorkLabel = stringResource(R.string.feature_subject_this_work)

    val mainStoryTimelineNodes =
        remember(uniqueRelations, currentSubjectId, currentSubjectName, currentSubjectCover, currentSubjectScore, thisWorkLabel) {
            if (currentSubjectId == null) return@remember emptyList()
            val prequels =
                uniqueRelations
                    .filter { it.relation in setOf("前传", "前传原著") || it.relation.startsWith("前传") }
                    .sortedBy { it.id }
            val sequels =
                uniqueRelations
                    .filter { it.relation in setOf("续集", "续作") || it.relation.startsWith("续集") || it.relation.startsWith("续作") }
                    .sortedBy { it.id }

            if (prequels.isEmpty() && sequels.isEmpty()) return@remember emptyList()

            val list = mutableListOf<TimelineStoryNode>()
            prequels.forEach {
                list.add(
                    TimelineStoryNode(
                        id = it.id,
                        name = it.displayName,
                        relationLabel = it.relation,
                        coverUrl = it.images?.common ?: it.images?.large ?: it.images?.medium,
                        score = it.score,
                        isCurrent = false,
                    ),
                )
            }
            list.add(
                TimelineStoryNode(
                    id = currentSubjectId,
                    name = currentSubjectName,
                    relationLabel = thisWorkLabel,
                    coverUrl = currentSubjectCover,
                    score = currentSubjectScore,
                    isCurrent = true,
                ),
            )
            sequels.forEach {
                list.add(
                    TimelineStoryNode(
                        id = it.id,
                        name = it.displayName,
                        relationLabel = it.relation,
                        coverUrl = it.images?.common ?: it.images?.large ?: it.images?.medium,
                        score = it.score,
                        isCurrent = false,
                    ),
                )
            }
            list
        }

    // 统计各分类实际包含的作品数量
    val categoryCounts =
        remember(uniqueRelations) {
            uniqueRelations.groupingBy { RelationCategory.classify(it) }.eachCount()
        }
    // 只展示当前条目实际存在的分类（按预设顺序）
    val availableCategories =
        remember(categoryCounts) {
            RelationCategory.entries.filter { (categoryCounts[it] ?: 0) > 0 }
        }

    val filteredRelations =
        remember(uniqueRelations, selectedCategory) {
            if (selectedCategory == null) {
                uniqueRelations
            } else {
                uniqueRelations.filter { RelationCategory.classify(it).name == selectedCategory }
            }
        }
    val canExpand =
        uniqueRelations.size > COLLAPSED_RELATION_COUNT || mainStoryTimelineNodes.size >= 2

    // 收起态优先给主线承接（前传/续集），避免画集、原声集等边角类别挤掉用户最关心的关系
    val prioritizedRelations =
        remember(uniqueRelations) {
            uniqueRelations.filter { isMainStoryRelation(it.relation) } +
                uniqueRelations.filterNot { isMainStoryRelation(it.relation) }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. 标题与视图切换按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val titleText =
                if (selectedCategory != null) {
                    val catLabel = stringResource(RelationCategory.valueOf(selectedCategory!!).labelRes)
                    stringResource(R.string.feature_subject_relations_title_with_category, catLabel, filteredRelations.size)
                } else {
                    stringResource(R.string.feature_subject_relations_title, uniqueRelations.size)
                }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            if (isExpanded && uniqueRelations.size > COLLAPSED_RELATION_COUNT) {
                BgmGhostIconButton(
                    onClick = { isGridView = !isGridView },
                    icon = if (isGridView) BgmIcons.ViewCarousel else BgmIcons.GridView,
                    contentDescription =
                        if (isGridView) {
                            stringResource(R.string.feature_subject_view_mode_scroll_mode)
                        } else {
                            stringResource(R.string.feature_subject_view_mode_grid, uniqueRelations.size)
                        },
                    iconSize = 19.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (canExpand) {
                SectionExpandAction(
                    isExpanded = isExpanded,
                    collapsedText = stringResource(R.string.feature_subject_expand_relations, uniqueRelations.size),
                    expandedText = stringResource(R.string.feature_subject_collapse_relations),
                    onToggle = { isExpanded = !isExpanded },
                )
            }
        }

        if (!isExpanded) {
            RelationSummaryList(
                relations = prioritizedRelations.take(COLLAPSED_RELATION_COUNT),
                onSubjectClick = onSubjectClick,
            )
        } else {
            // 展开后才展示系列时间轴、分类筛选与完整海报墙，避免默认首屏重复堆叠关系数据。
            if ((selectedCategory == null || selectedCategory == RelationCategory.MAIN_STORY.name) &&
                mainStoryTimelineNodes.isNotEmpty()
            ) {
                MainStoryTimeline(
                    nodes = mainStoryTimelineNodes,
                    onSubjectClick = onSubjectClick,
                )
            }

            if (availableCategories.size >= 2 || uniqueRelations.size > 3) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item(key = "rel_cat_all") {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_subject_relations_all, uniqueRelations.size),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            border = null,
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                    items(items = availableCategories, key = { it.name }) { category ->
                        val count = categoryCounts[category] ?: 0
                        val isSelected = selectedCategory == category.name
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = if (isSelected) null else category.name
                            },
                            label = {
                                Text(
                                    text = "${stringResource(category.labelRes)} ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            border = null,
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }
            }

            if (!isGridView) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = filteredRelations, key = { "${it.id}_${it.relation}" }) { relation ->
                        RelationCard(
                            relation = relation,
                            onClick = { onSubjectClick(relation.id) },
                            modifier = Modifier.width(115.dp),
                        )
                    }
                }
            } else {
                val chunked = remember(filteredRelations) { filteredRelations.chunked(3) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    chunked.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    RelationCard(
                                        relation = item,
                                        onClick = { onSubjectClick(item.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val COLLAPSED_RELATION_COUNT = 4

/** 主线承接关系（前传/续集家族）：收起态摘要排序与主线时间轴共用同一判定。 */
private fun isMainStoryRelation(relation: String): Boolean =
    relation in setOf("前传", "前传原著", "续集", "续作") ||
        relation.startsWith("前传") ||
        relation.startsWith("续集") ||
        relation.startsWith("续作")

/** 关联作品默认摘要：优先呈现关系、标题与评分，展开后再进入时间轴和完整海报墙。 */
@Composable
private fun RelationSummaryList(
    relations: List<SubjectRelation>,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 自然流行列表：与整页节奏一致，不再套灰卡；分隔线承担分组
    Column(modifier = modifier.fillMaxWidth()) {
        relations.forEachIndexed { index, relation ->
            RelationSummaryRow(
                relation = relation,
                onClick = { onSubjectClick(relation.id) },
            )
            if (index < relations.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 46.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun RelationSummaryRow(
    relation: SubjectRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = remember(relation) { RelationCategory.classify(relation) }
    val (badgeContainerColor, badgeContentColor) = relationBadgeColors(category)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverImage(
            url = relation.images?.bestImage.orEmpty(),
            contentDescription = relation.displayName,
            modifier = Modifier.size(width = 36.dp, height = 48.dp),
            shape = RoundedCornerShape(8.dp),
            aspectRatio = BGM_POSTER_ASPECT_RATIO,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = relation.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (relation.relation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeContainerColor.copy(alpha = 0.9f),
                    ) {
                        Text(
                            text = relation.relation,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeContentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                if (relation.score > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = relation.score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ratingTextColor(),
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = BgmIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SectionExpandAction(
    isExpanded: Boolean,
    collapsedText: String,
    expandedText: String,
    onToggle: () -> Unit,
) {
    TextButton(
        onClick = onToggle,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            text = if (isExpanded) expandedText else collapsedText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 主线系列观影顺序时间轴：前传 -> 本作(当前) -> 续作 连线时间轨 */
@Composable
fun MainStoryTimeline(
    nodes: List<TimelineStoryNode>,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.size < 2) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = BgmIcons.ViewCarousel,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.feature_subject_main_series_order, nodes.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.feature_subject_order_summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items = nodes, key = { index, node -> "${node.id}_${node.relationLabel}_$index" }) { index, node ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimelineNodeCard(
                        node = node,
                        index = index + 1,
                        onClick = {
                            if (!node.isCurrent) {
                                onSubjectClick(node.id)
                            }
                        },
                    )

                    if (index < nodes.lastIndex) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = BgmIcons.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNodeCard(
    node: TimelineStoryNode,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBorder =
        if (node.isCurrent) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

    val cardColor =
        if (node.isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder,
        modifier = modifier.width(130.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                CoverImage(
                    url = node.coverUrl.orEmpty(),
                    contentDescription = node.name,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Surface(
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                    color =
                        if (node.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                        },
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text =
                            if (node.isCurrent) {
                                stringResource(
                                    R.string.feature_subject_current_work,
                                )
                            } else {
                                "$index. ${node.relationLabel}"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.82f,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (node.isCurrent) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (node.isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color =
                        if (node.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(34.dp),
                )

                if (node.score > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = node.score.formatScore(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

/** 关联作品卡片：语义化徽章颜色分类（主线蓝/原著金/音乐紫）与金星评分 */
@Composable
private fun RelationCard(
    relation: SubjectRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = remember(relation) { RelationCategory.classify(relation) }
    val (badgeContainerColor, badgeContentColor) = relationBadgeColors(category)

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    url = relation.images?.bestImage.orEmpty(),
                    contentDescription = relation.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                )
                if (relation.relation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                        color = badgeContainerColor.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = relation.relation,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeContentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = relation.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(34.dp),
                )

                if (relation.score > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Star,
                            contentDescription = null,
                            tint = RatingGold,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = relation.score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ratingTextColor(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun relationBadgeColors(category: RelationCategory): Pair<Color, Color> =
    when (category) {
        RelationCategory.MAIN_STORY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        RelationCategory.ORIGINAL -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        RelationCategory.MUSIC -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        RelationCategory.GAME -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        RelationCategory.SPINOFF -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }

/** 登场角色与声优区域 */
@Composable
fun CharactersSection(
    characters: List<SubjectCharacter>,
    onCharacterClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val uniqueCharacters = remember(characters) { characters.distinctBy { it.id } }
    val canExpand = uniqueCharacters.size > COLLAPSED_CHARACTER_COUNT
    val visibleCharacters =
        if (isExpanded || !canExpand) {
            uniqueCharacters
        } else {
            uniqueCharacters.take(COLLAPSED_CHARACTER_COUNT)
        }

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
                text = stringResource(R.string.feature_subject_characters_and_actors),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (canExpand) {
                SectionExpandAction(
                    isExpanded = isExpanded,
                    collapsedText = stringResource(R.string.feature_subject_expand_characters, uniqueCharacters.size),
                    expandedText = stringResource(R.string.feature_subject_collapse_characters),
                    onToggle = { isExpanded = !isExpanded },
                )
            } else {
                Text(
                    text = stringResource(R.string.feature_subject_characters_count, uniqueCharacters.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 收起态一律纵向摘要行（与角色数量无关），完整立绘卡只在展开后出现
        if (isExpanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = visibleCharacters, key = { it.id }) { character ->
                    CharacterCard(
                        character = character,
                        onCharacterClick = onCharacterClick,
                        onActorClick = onActorClick,
                        onPreviewCharacter = onPreviewCharacter,
                    )
                }
            }
        } else {
            CharacterSummaryList(
                characters = visibleCharacters,
                onCharacterClick = onCharacterClick,
            )
        }
    }
}

private const val COLLAPSED_CHARACTER_COUNT = 4

/** 默认态只给角色、定位与声优摘要行；完整立绘卡在展开后呈现。 */
@Composable
private fun CharacterSummaryList(
    characters: List<SubjectCharacter>,
    onCharacterClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 自然流行列表：与关联摘要同构，不再套灰卡
    Column(modifier = modifier.fillMaxWidth()) {
        characters.forEachIndexed { index, character ->
            CharacterSummaryRow(
                character = character,
                onClick = { onCharacterClick(character.id) },
            )
            if (index < characters.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun CharacterSummaryRow(
    character: SubjectCharacter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverImage(
            url = character.images?.bestImage.orEmpty(),
            contentDescription = character.name,
            modifier = Modifier.size(width = 38.dp, height = 50.dp),
            shape = RoundedCornerShape(8.dp),
            aspectRatio = 0.76f,
            alignment = Alignment.TopCenter,
            placeholder = CoverPlaceholder.Person,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = character.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val actorName =
                character.actors
                    .firstOrNull()
                    ?.name
                    .orEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (character.roleName.isNotBlank()) {
                    Text(
                        text = character.roleName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (actorName.isNotBlank()) {
                    Text(
                        text = "CV: $actorName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 角色卡片：头部正容立绘、主角/配角定位、放大立绘按钮、声优信息与点击跳转 */
@Composable
private fun CharacterCard(
    character: SubjectCharacter,
    onCharacterClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onCharacterClick(character.id) },
        modifier = modifier.width(136.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    url = character.images?.bestImage.orEmpty(),
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    aspectRatio = 0.85f,
                    alignment = Alignment.TopCenter,
                    placeholder = CoverPlaceholder.Person,
                )

                if (character.roleName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = character.roleName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Surface(
                    onClick = { onPreviewCharacter(character) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BgmIcons.ZoomIn,
                            contentDescription = stringResource(R.string.feature_subject_view_full_character_image),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                val actor = character.actors.firstOrNull()
                if (actor != null && actor.name.isNotBlank()) {
                    Surface(
                        onClick = { onActorClick(actor.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            val actorImage = actor.images?.bestImage.orEmpty()
                            CoverImage(
                                url = actorImage,
                                contentDescription = actor.name,
                                modifier = Modifier.size(18.dp),
                                shape = CircleShape,
                                aspectRatio = 1f,
                                alignment = Alignment.TopCenter,
                                placeholder = CoverPlaceholder.Person,
                            )
                            Text(
                                text = "CV: ${actor.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 制作团队区域：支持点击职员跳转其个人主页 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffSection(
    persons: List<SubjectPerson>,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val entries =
        remember(persons) {
            persons
                .groupBy { it.relation }
                .entries
                .mapIndexed { index, entry -> Triple(keyStaffPriority(entry.key), index, entry) }
                .sortedWith(compareBy<Triple<Int, Int, Map.Entry<String, List<SubjectPerson>>>> { it.first }.thenBy { it.second })
                .map { it.third }
        }
    val displayEntries =
        if (isExpanded || entries.size <= DEFAULT_STAFF_ENTRY_COUNT) {
            entries
        } else {
            entries.take(DEFAULT_STAFF_ENTRY_COUNT)
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_subject_production_staff),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        // 职位与人员是纯文本参考信息，与简介同构：自然流直坐在背景上，不套灰卡
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            displayEntries.forEach { (relation, staffMembers) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = relation,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(86.dp).padding(vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        staffMembers.forEachIndexed { index, person ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onPersonClick(person.id) }
                                            .padding(horizontal = 3.dp),
                                )
                                if (index < staffMembers.lastIndex) {
                                    Text(
                                        text = "、",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (entries.size > DEFAULT_STAFF_ENTRY_COUNT) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (isExpanded) {
                                stringResource(R.string.feature_subject_collapse_staff)
                            } else {
                                stringResource(R.string.feature_subject_expand_staff, entries.size)
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private const val DEFAULT_STAFF_ENTRY_COUNT = 4

private val KeyStaffRelationPriority =
    listOf(
        "总导演",
        "导演",
        "监督",
        "系列构成",
        "脚本",
        "编剧",
        "原作",
        "音乐",
        "动画制作",
    )

/** 将真正影响作品判断的关键主创放在 API 返回顺序之前。 */
private fun keyStaffPriority(relation: String): Int {
    val normalized = relation.trim()
    val priority = KeyStaffRelationPriority.indexOfFirst { key -> normalized == key || normalized.startsWith(key) }
    return if (priority >= 0) priority else Int.MAX_VALUE
}

/** 角色全身立绘与原图大图预览弹窗 */
@Composable
fun CharacterImagePreviewDialog(
    character: SubjectCharacter,
    onDismiss: () -> Unit,
    onViewDetail: (Long) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.92f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BgmGhostIconButton(
                    onClick = onDismiss,
                    icon = BgmIcons.Close,
                    contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_close),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    tint = Color.White,
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val imageUrl = character.images?.bestImage.orEmpty()
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = character.name,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (character.roleName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = character.roleName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    val actor = character.actors.firstOrNull()
                    if (actor != null && actor.name.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.feature_subject_actor_format, actor.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    BgmPillButton(onClick = {
                        onDismiss()
                        onViewDetail(character.id)
                    }, text = stringResource(R.string.feature_subject_view_character_detail), variant = BgmPillVariant.Tonal)
                }
            }
        }
    }
}
