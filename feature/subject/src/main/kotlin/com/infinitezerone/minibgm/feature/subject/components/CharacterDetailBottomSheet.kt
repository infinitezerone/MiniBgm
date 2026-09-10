package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SubjectCharacter

/**
 * 原生角色详情底栏：支持立绘、声优联动、属性生平与直接在端内跳转出演作品
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailBottomSheet(
    character: SubjectCharacter?,
    detail: CharacterDetail?,
    relatedWorks: List<RelatedWork>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scrollState = rememberScrollState()
    var isSummaryExpanded by remember { mutableStateOf(false) }

    val characterName = character?.name ?: detail?.name.orEmpty()
    val characterImage =
        character?.images?.bestImage?.ifBlank { detail?.images?.bestImage.orEmpty() }
            ?: detail?.images?.bestImage.orEmpty()
    val summary = detail?.summary.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 顶部标题栏（角色档案徽章与关闭）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = if (!character?.roleName.isNullOrBlank()) "角色档案 · ${character.roleName}" else "角色档案",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 2. 主体立绘与现代胶囊元数据（Hero Section）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverImage(
                    url = characterImage,
                    contentDescription = characterName,
                    modifier = Modifier.width(108.dp),
                    cornerRadius = 12.dp,
                    aspectRatio = 0.72f,
                    alignment = Alignment.TopCenter,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!character?.roleName.isNullOrBlank()) {
                            EntityInfoPill(
                                text = character.roleName,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        detail?.genderText?.let { gender ->
                            EntityInfoPill(
                                text = gender,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        detail?.birthdayText?.let { birthday ->
                            EntityInfoPill(
                                text = birthday,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val collects = detail?.stat?.collects ?: 0
                        if (collects > 0) {
                            EntityInfoPill(
                                text = "$collects 收藏",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    if (isLoading && detail == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 3. 关联声优专属导流卡片（点击无缝直达声优详情）
            val actor = character?.actors?.firstOrNull()
            if (actor != null && actor.name.isNotBlank()) {
                Surface(
                    onClick = { onActorClick(actor.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val actorImg = actor.images?.bestImage.orEmpty()
                        if (actorImg.isNotBlank()) {
                            CoverImage(
                                url = actorImg,
                                contentDescription = actor.name,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                cornerRadius = 16.dp,
                                aspectRatio = 1f,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CV 声优",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = actor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "查看声优详情",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // 4. 角色背景生平简介
            if (summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "角色简介",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = summary.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                            maxLines = if (isSummaryExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (summary.length > 90) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { isSummaryExpanded = !isSummaryExpanded }
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isSummaryExpanded) "收起完整简介" else "展开完整简介",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector =
                                        if (isSummaryExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.KeyboardArrowDown
                                        },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 5. 出演作品展示区（精选横滑 + 3列海报网格墙双模式）
            RelatedWorksSection(
                title = "出演作品",
                works = relatedWorks,
                onSubjectClick = onSubjectClick,
                onDismiss = onDismiss,
                badgeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
