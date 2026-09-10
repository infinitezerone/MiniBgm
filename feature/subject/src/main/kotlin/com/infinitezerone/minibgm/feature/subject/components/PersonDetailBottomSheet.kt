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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.infinitezerone.minibgm.core.designsystem.component.CoverPlaceholder
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SubjectPerson

/**
 * 原生人物/制作团队/声优详情底栏：支持头像、职业标签、生平维基与直接在端内跳转代表作作品
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailBottomSheet(
    person: SubjectPerson?,
    detail: PersonDetail?,
    relatedWorks: List<RelatedWork>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    var isSummaryExpanded by remember { mutableStateOf(false) }

    val personName = person?.name ?: detail?.name.orEmpty()
    val personAvatar =
        detail?.bestAvatar?.ifBlank { person?.images?.bestImage.orEmpty() }
            ?: person?.images?.bestImage.orEmpty()
    val summary = detail?.summary.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 600.dp,
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
            // 1. 顶部标题栏（档案标签与关闭）
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
                        text = if (!person?.relation.isNullOrBlank()) "人物档案 · ${person.relation}" else "人物档案",
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

            // 2. 主体形象与现代胶囊元数据（Hero Section）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverImage(
                    url = personAvatar,
                    contentDescription = personName,
                    modifier = Modifier.width(108.dp),
                    cornerRadius = 12.dp,
                    aspectRatio = 0.8f,
                    alignment = Alignment.TopCenter,
                    placeholder = CoverPlaceholder.Person,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = personName,
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
                        if (!detail?.careerText.isNullOrBlank()) {
                            detail.career.forEach { rawCareer ->
                                val careerLabel =
                                    when (rawCareer.lowercase()) {
                                        "seiyu" -> "声优"
                                        "artist" -> "歌手"
                                        "writer" -> "作家"
                                        "illustrator" -> "插画师"
                                        "actor" -> "演员"
                                        else -> rawCareer
                                    }
                                EntityInfoPill(
                                    text = careerLabel,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        detail?.genderText?.let { gender ->
                            EntityInfoPill(
                                text = gender,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                text = "$collects 关注",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    if (isLoading && detail == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 46.dp, height = 20.dp),
                                shape = RoundedCornerShape(6.dp),
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 54.dp, height = 20.dp),
                                shape = RoundedCornerShape(6.dp),
                            )
                        }
                    }
                }
            }

            // 3. 人物履历生平简介
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
                            text = "个人简介",
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
            } else if (isLoading && detail == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.3f).height(16.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.92f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.85f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                        SkeletonBox(
                            modifier = Modifier.fillMaxWidth(0.55f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }

            // 4. 代表作/参与作品展示区（精选横滑 + 3列海报网格墙双模式）
            RelatedWorksSection(
                title = "参与作品",
                works = relatedWorks,
                onSubjectClick = onSubjectClick,
                onDismiss = onDismiss,
                badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
