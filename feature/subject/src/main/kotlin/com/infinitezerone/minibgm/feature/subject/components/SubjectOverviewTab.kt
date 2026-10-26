package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.onSurfaceBodyColor
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

internal const val SUBJECT_OVERVIEW_LIST_TEST_TAG = "subject_overview_list"

/**
 * 详情页「概览」按用户决策顺序组织：
 * 1. 评分、收藏分布与题材标签：先回答「值不值得看、属于什么类型」；
 * 2. 剧情简介：再回答「讲什么」；
 * 3. 关联作品：回答「接下来还能看什么」；
 * 4. 登场角色与演职团队：最后补充「谁参与创作」。
 */
@Composable
internal fun OverviewTabPage(
    subject: Subject,
    displaySubject: Subject,
    uiState: SubjectDetailUiState,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onPersonClick: (Long) -> Unit,
    onSubjectClick: (Long) -> Unit,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(SUBJECT_OVERVIEW_LIST_TEST_TAG),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 决策上下文：评分口碑、收藏分布与题材标签合并为一个判断面板
        item(key = "overview_decision_context") {
            RatingDistributionCard(
                rating = subject.rating,
                collection = subject.collection,
                tags = subject.tags,
                onTagClick = onTagClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // 2. 剧情简介
        val trimmedSummary = subject.summary.trim()
        if (trimmedSummary.isNotBlank()) {
            item(key = "subject_summary_section") {
                SubjectSummarySection(
                    summary = trimmedSummary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // 3. 登场角色与声优
        if (uiState.characters.isNotEmpty()) {
            item(key = "characters_section") {
                CharactersSection(
                    characters = uiState.characters,
                    onCharacterClick = onCharacterClick,
                    onActorClick = onActorClick,
                    onPreviewCharacter = onPreviewCharacter,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // 4. 演职制作团队 Staff
        if (uiState.persons.isNotEmpty() || displaySubject.keyStaff.isNotEmpty()) {
            item(key = "staff_section") {
                StaffSection(
                    persons = uiState.persons,
                    onPersonClick = onPersonClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // 5. 关联作品与系列番剧
        if (uiState.relations.isNotEmpty()) {
            item(key = "relations_section") {
                RelationsSection(
                    relations = uiState.relations,
                    onSubjectClick = onSubjectClick,
                    currentSubjectId = displaySubject.id,
                    currentSubjectName = displaySubject.nameCn.ifBlank { displaySubject.name },
                    currentSubjectCover = displaySubject.images?.bestImage ?: displaySubject.images?.large,
                    currentSubjectScore = displaySubject.rating?.score ?: 0.0,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** 智能段落折叠简介：纯净自然流，无多余灰框包裹。 */
@Composable
private fun SubjectSummarySection(
    summary: String,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val summaryBlocks =
        remember(summary) {
            summary.split(Regex("(?:\r?\n){2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        }

    val collapsedPreview =
        remember(summaryBlocks, summary) {
            if (summaryBlocks.size > 1) {
                val firstBlock = summaryBlocks[0]
                if (firstBlock.length < 45 && summaryBlocks.size >= 2) {
                    "$firstBlock\n\n${summaryBlocks[1]}"
                } else {
                    firstBlock
                }
            } else {
                summary
            }
        }

    val canExpand =
        remember(summary, collapsedPreview, summaryBlocks) {
            summaryBlocks.size > 1 || summary.length > 120 || summary.count { it == '\n' } >= 3
        }

    val displayText = if (isExpanded || !canExpand) summary else collapsedPreview

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_subject_summary_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceBodyColor(),
            maxLines = if (isExpanded || !canExpand) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        )

        if (canExpand) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text =
                        if (isExpanded) {
                            stringResource(R.string.feature_subject_collapse_summary)
                        } else {
                            stringResource(R.string.feature_subject_expand_summary)
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
