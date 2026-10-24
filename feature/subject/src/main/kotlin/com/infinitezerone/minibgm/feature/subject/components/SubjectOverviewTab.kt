package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState

/** 概览页：评分分布与标签、角色与声优、Staff、关联作品。 */
@Composable
internal fun OverviewTabPage(
    subject: Subject,
    uiState: SubjectDetailUiState,
    displaySubject: Subject,
    onTagClick: (String) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onActorClick: (Long) -> Unit,
    onPreviewCharacter: (SubjectCharacter) -> Unit,
    onPersonClick: (Long) -> Unit,
    onSubjectClick: (Long) -> Unit,
    bottomPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "rating_distribution") {
            RatingDistributionCard(
                rating = subject.rating,
                collection = subject.collection,
                tags = subject.tags,
                onTagClick = onTagClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

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

        if (uiState.persons.isNotEmpty()) {
            item(key = "staff_section") {
                StaffSection(
                    persons = uiState.persons,
                    onPersonClick = onPersonClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

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

        if (uiState.isDetailsLoading &&
            uiState.relations.isEmpty() &&
            uiState.characters.isEmpty() &&
            uiState.persons.isEmpty()
        ) {
            item(key = "details_loading_indicator") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
