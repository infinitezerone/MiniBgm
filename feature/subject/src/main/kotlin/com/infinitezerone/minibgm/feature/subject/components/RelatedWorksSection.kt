package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.aggregateBySubject

private const val MAX_FEATURED_ITEMS = 10
private const val POSTER_GRID_COLUMNS = 3
private const val MIN_ITEMS_FOR_VIEW_TOGGLE = 6
private const val MIN_ROLES_FOR_FILTER = 2
private const val MIN_ITEMS_FOR_ROLE_FILTER = 4
private const val INITIAL_GRID_DISPLAY_LIMIT = 18

/**
 * 关联作品/出演作品展示区：
 * 支持「精选横滑」与「3列海报墙」双模式切换，多职位快捷过滤，以及点击直达条目详情。
 */
@Composable
fun RelatedWorksSection(
    title: String,
    works: List<RelatedWork>,
    onSubjectClick: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    badgeContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
    badgeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    if (works.isEmpty()) return

    val aggregatedWorks = remember(works) { works.aggregateBySubject() }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var selectedRoleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var gridDisplayLimit by rememberSaveable { mutableIntStateOf(INITIAL_GRID_DISPLAY_LIMIT) }

    // 提取出现频率最高的多职位分类（用于创作者/角色身兼多职时精准过滤）
    val availableRoles =
        remember(aggregatedWorks) {
            aggregatedWorks
                .flatMap { it.staff.split(" / ") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
        }

    val filteredWorks =
        remember(aggregatedWorks, selectedRoleFilter) {
            if (selectedRoleFilter == null) {
                aggregatedWorks
            } else {
                aggregatedWorks.filter {
                    it.staff
                        .split(" / ")
                        .map(String::trim)
                        .contains(selectedRoleFilter)
                }
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 1. 标题栏与视图切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sectionTitle =
                if (selectedRoleFilter != null) {
                    "$title · $selectedRoleFilter (${filteredWorks.size})"
                } else {
                    "$title (${aggregatedWorks.size})"
                }

            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (aggregatedWorks.size > MIN_ITEMS_FOR_VIEW_TOGGLE) {
                val toggleContainerColor =
                    if (isGridView) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                val toggleContentColor =
                    if (isGridView) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Surface(
                    onClick = { isGridView = !isGridView },
                    shape = RoundedCornerShape(16.dp),
                    color = toggleContainerColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Filled.ViewCarousel else Icons.Filled.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = toggleContentColor,
                        )
                        Text(
                            text = if (isGridView) "横滑精选" else "海报墙 (${aggregatedWorks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = toggleContentColor,
                        )
                    }
                }
            }
        }

        // 2. 多职位分类筛选标签 (仅当存在 2 个及以上不同职位，且作品总数 > 4 时展示)
        if (availableRoles.size >= MIN_ROLES_FOR_FILTER && aggregatedWorks.size > MIN_ITEMS_FOR_ROLE_FILTER) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "role_all") {
                    FilterChip(
                        selected = selectedRoleFilter == null,
                        onClick = { selectedRoleFilter = null },
                        label = { Text("全部 (${aggregatedWorks.size})", style = MaterialTheme.typography.labelSmall) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
                items(items = availableRoles, key = { it.first }) { (role, count) ->
                    FilterChip(
                        selected = selectedRoleFilter == role,
                        onClick = {
                            selectedRoleFilter = if (selectedRoleFilter == role) null else role
                            if (!isGridView) isGridView = true
                        },
                        label = { Text("$role ($count)", style = MaterialTheme.typography.labelSmall) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }
        }

        // 3. 作品列表渲染
        if (!isGridView) {
            // 精选横滑模式（最多展示 MAX_FEATURED_ITEMS 部，第 11 项为“查看全部”卡片）
            val previewWorks = remember(filteredWorks) { filteredWorks.take(MAX_FEATURED_ITEMS) }
            val hasMore = filteredWorks.size > MAX_FEATURED_ITEMS

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = previewWorks, key = { it.id }) { work ->
                    RelatedWorkCard(
                        work = work,
                        badgeContainerColor = badgeContainerColor,
                        badgeContentColor = badgeContentColor,
                        modifier = Modifier.width(100.dp),
                        onClick = {
                            onDismiss()
                            onSubjectClick(work.id)
                        },
                    )
                }
                if (hasMore) {
                    item(key = "view_all_card") {
                        Card(
                            onClick = { isGridView = true },
                            modifier =
                                Modifier
                                    .width(100.dp)
                                    .height(178.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.GridView,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "查看全部",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "共 ${filteredWorks.size} 部",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 海报墙网格模式（POSTER_GRID_COLUMNS 列纵向排列，带上限保护与展开更多）
            val displayedWorks =
                remember(filteredWorks, gridDisplayLimit) {
                    filteredWorks.take(gridDisplayLimit)
                }
            val hasMoreGridWorks = filteredWorks.size > gridDisplayLimit
            val chunkedWorks = remember(displayedWorks) { displayedWorks.chunked(POSTER_GRID_COLUMNS) }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                chunkedWorks.forEach { rowWorks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowWorks.forEach { work ->
                            Box(modifier = Modifier.weight(1f)) {
                                RelatedWorkCard(
                                    work = work,
                                    badgeContainerColor = badgeContainerColor,
                                    badgeContentColor = badgeContentColor,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onDismiss()
                                        onSubjectClick(work.id)
                                    },
                                )
                            }
                        }
                        repeat(POSTER_GRID_COLUMNS - rowWorks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (hasMoreGridWorks) {
                    val remainingCount = filteredWorks.size - gridDisplayLimit
                    OutlinedButton(
                        onClick = { gridDisplayLimit += 24 },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text("展开更多作品 (剩余 $remainingCount 部)")
                    }
                }
            }
        }
    }
}

/**
 * 单部作品海报展示卡片
 */
@Composable
private fun RelatedWorkCard(
    work: RelatedWork,
    modifier: Modifier = Modifier,
    badgeContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
    badgeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box {
                CoverImage(
                    url = work.coverImage,
                    contentDescription = work.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 6.dp,
                    aspectRatio = 0.72f,
                )
                if (work.staff.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = badgeContainerColor,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = work.staff,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = work.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 现代胶囊信息标签（用于职业、性别、生日、收藏等元数据）
 */
@Composable
fun EntityInfoPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
