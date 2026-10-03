package com.infinitezerone.minibgm.feature.subject.components

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.component.formatScore
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_PORTRAIT_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.BGM_POSTER_ASPECT_RATIO
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation

/** 关联条目分类枚举（对齐 Bangumi 官网分类规则） */
enum class RelationCategory(
    val label: String,
) {
    MAIN_STORY("正传/续作"),
    ORIGINAL("改编原著"),
    MUSIC("原声音乐"),
    SPINOFF("衍生/特典"),
    GAME("游戏"),
    OTHER("其他"),
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

    val mainStoryTimelineNodes =
        remember(uniqueRelations, currentSubjectId, currentSubjectName, currentSubjectCover, currentSubjectScore) {
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
                    relationLabel = "本作",
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
                    val catLabel = RelationCategory.valueOf(selectedCategory!!).label
                    "关联作品 · $catLabel (${filteredRelations.size})"
                } else {
                    "关联作品 (${uniqueRelations.size})"
                }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (uniqueRelations.size > 4) {
                Surface(
                    onClick = { isGridView = !isGridView },
                    shape = RoundedCornerShape(16.dp),
                    color =
                        if (isGridView) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isGridView) BgmIcons.ViewCarousel else BgmIcons.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint =
                                if (isGridView) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Text(
                            text = if (isGridView) "横滑模式" else "海报墙 (${uniqueRelations.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (isGridView) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }

        // 2. 主线系列观影顺序时间轴（仅在有前传/续集且处于全部分类或正传分类时高亮展示）
        if ((selectedCategory == null || selectedCategory == RelationCategory.MAIN_STORY.name) && mainStoryTimelineNodes.isNotEmpty()) {
            MainStoryTimeline(
                nodes = mainStoryTimelineNodes,
                onSubjectClick = onSubjectClick,
            )
        }

        // 3. 细粒度分类过滤 Chips（仅在分类数 >= 2 或条目总数 > 3 时展示）
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
                                text = "全部 (${uniqueRelations.size})",
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
                                text = "${category.label} ($count)",
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

        // 3. 内容区：横滑模式 vs 3 列海报墙
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
                text = "主线系列观影顺序 (${nodes.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "前传 · 本作 · 续作",
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
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(
                    url = node.coverUrl.orEmpty(),
                    contentDescription = node.name,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                    modifier = Modifier.fillMaxSize(),
                )

                Surface(
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                    color =
                        if (node.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                        },
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = if (node.isCurrent) "当前 · 本作" else "$index. ${node.relationLabel}",
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

/** 关联作品卡片：语义化徽章颜色分类（主线蓝/原著金/音乐紫）与金星评分 */
@Composable
private fun RelationCard(
    relation: SubjectRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = remember(relation) { RelationCategory.classify(relation) }
    val (badgeContainerColor, badgeContentColor) =
        when (category) {
            RelationCategory.MAIN_STORY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            RelationCategory.ORIGINAL -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
            RelationCategory.MUSIC -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            RelationCategory.GAME -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            RelationCategory.SPINOFF -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(7.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    url = relation.images?.bestImage.orEmpty(),
                    contentDescription = relation.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp,
                    aspectRatio = BGM_POSTER_ASPECT_RATIO,
                )
                if (relation.relation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
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

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = relation.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (relation.score > 0.0) {
                Spacer(modifier = Modifier.height(2.dp))
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
                        color = RatingGold,
                    )
                }
            }
        }
    }
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
                text = "登场角色与声优",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${characters.size} 位角色",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val uniqueCharacters = remember(characters) { characters.distinctBy { it.id } }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = uniqueCharacters, key = { it.id }) { character ->
                CharacterCard(
                    character = character,
                    onCharacterClick = onCharacterClick,
                    onActorClick = onActorClick,
                    onPreviewCharacter = onPreviewCharacter,
                )
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
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    url = character.images?.bestImage.orEmpty(),
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp,
                    aspectRatio = BGM_PORTRAIT_ASPECT_RATIO,
                    alignment = Alignment.TopCenter,
                    placeholder = CoverPlaceholder.Person,
                )

                if (character.roleName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
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
                            contentDescription = "查看全身立绘",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                Spacer(modifier = Modifier.height(6.dp))
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
                            cornerRadius = 9.dp,
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

/** 制作团队区域：支持点击职员跳转其个人主页 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffSection(
    persons: List<SubjectPerson>,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val groupedStaff = persons.groupBy { it.relation }
    val entries = groupedStaff.entries.toList()
    val displayEntries = if (isExpanded || entries.size <= 6) entries else entries.take(6)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "制作团队",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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

                if (entries.size > 6) {
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
                            text = if (isExpanded) "收起制作团队" else "查看完整制作团队 (${entries.size})",
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
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                    )
                }

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
                            text = "声优：${actor.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onViewDetail(character.id)
                        },
                    ) {
                        Text("查看角色详情")
                    }
                }
            }
        }
    }
}
