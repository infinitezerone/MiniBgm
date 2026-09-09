package com.infinitezerone.minibgm.feature.subject

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.components.CharacterDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.CharacterImagePreviewDialog
import com.infinitezerone.minibgm.feature.subject.components.CharactersSection
import com.infinitezerone.minibgm.feature.subject.components.CollectionStatusBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.EpisodeDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGrid
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroupFilterChips
import com.infinitezerone.minibgm.feature.subject.components.EpisodeListItem
import com.infinitezerone.minibgm.feature.subject.components.EpisodesSectionHeader
import com.infinitezerone.minibgm.feature.subject.components.PersonDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.RatingDistributionCard
import com.infinitezerone.minibgm.feature.subject.components.RelationsSection
import com.infinitezerone.minibgm.feature.subject.components.StaffSection
import com.infinitezerone.minibgm.feature.subject.components.SubjectCommunitySection
import com.infinitezerone.minibgm.feature.subject.components.SubjectHeaderCard
import com.infinitezerone.minibgm.feature.subject.components.SubjectPersonalProgressCard
import com.infinitezerone.minibgm.feature.subject.components.isEpisodeWatched
import com.infinitezerone.minibgm.feature.subject.components.launchCustomTab
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 条目详情页二级分栏枚举 */
enum class SubjectDetailTab(
    val label: String,
) {
    EPISODES("📺 章节打卡"),
    DETAILS("📖 资料与演职员"),
    COMMUNITY("💬 社区吐槽"),
}

private fun getTabLabel(
    tab: SubjectDetailTab,
    subjectType: SubjectType,
): String =
    when (tab) {
        SubjectDetailTab.EPISODES ->
            when (subjectType) {
                SubjectType.BOOK -> "📚 卷册与章节"
                SubjectType.MUSIC -> "🎵 曲目列表"
                SubjectType.GAME -> "🎮 关卡与章节"
                SubjectType.ANIME, SubjectType.REAL -> "📺 章节打卡"
            }
        SubjectDetailTab.DETAILS ->
            when (subjectType) {
                SubjectType.BOOK -> "📖 原作与出版信息"
                SubjectType.MUSIC -> "💿 专辑制作与人员"
                SubjectType.GAME -> "🎮 游戏资料与主创"
                SubjectType.ANIME, SubjectType.REAL -> "📖 资料与演职员"
            }
        SubjectDetailTab.COMMUNITY -> "💬 社区吐槽"
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubjectDetailScreen(
    subjectId: Long,
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onCharacterClick: ((Long) -> Unit)? = null,
    onPersonClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SubjectDetailViewModel = koinViewModel(parameters = { parametersOf(subjectId) }),
) {
    val context = LocalContext.current
    var activeCharacter by remember { mutableStateOf<SubjectCharacter?>(null) }
    var activePerson by remember { mutableStateOf<SubjectPerson?>(null) }

    val handleCharacterClick: (Long) -> Unit = { characterId ->
        if (onCharacterClick != null) {
            onCharacterClick(characterId)
        } else {
            activePerson = null
            activeCharacter =
                viewModel.uiState.value.characters
                    .firstOrNull { it.id == characterId }
                    ?: SubjectCharacter(id = characterId, name = "")
            viewModel.loadCharacterDetail(characterId)
        }
    }
    val handlePersonClick: (Long) -> Unit = { personId ->
        if (onPersonClick != null) {
            onPersonClick(personId)
        } else {
            activeCharacter = null
            activePerson =
                viewModel.uiState.value.persons
                    .firstOrNull { it.id == personId }
                    ?: SubjectPerson(id = personId, name = "")
            viewModel.loadPersonDetail(personId)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subjectType = uiState.subject?.type?.let { SubjectType.fromValue(it) } ?: SubjectType.ANIME
    var selectedTab by rememberSaveable { mutableStateOf(SubjectDetailTab.EPISODES) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            SubjectDetailTab.EPISODES -> Unit
            SubjectDetailTab.DETAILS -> viewModel.loadDetailsTabIfNeeded()
            SubjectDetailTab.COMMUNITY -> viewModel.loadCommunityTabIfNeeded()
        }
    }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodeForDetail by remember { mutableStateOf<Episode?>(null) }
    var previewCharacter by remember { mutableStateOf<SubjectCharacter?>(null) }

    val handleLinkClick: (String) -> Unit = { url ->
        when (val link = BgmUrlParser.parse(url)) {
            is BgmLink.Subject -> {
                selectedEpisodeForDetail = null
                onSubjectClick(link.subjectId)
            }
            is BgmLink.Character -> {
                selectedEpisodeForDetail = null
                handleCharacterClick(link.characterId)
            }
            is BgmLink.Person -> {
                selectedEpisodeForDetail = null
                handlePersonClick(link.personId)
            }
            is BgmLink.Episode -> {
                val ep = uiState.episodes.firstOrNull { it.id == link.episodeId }
                if (ep != null) {
                    selectedEpisodeForDetail = ep
                } else {
                    launchCustomTab(context, url)
                }
            }
            is BgmLink.Topic -> launchCustomTab(context, url)
            is BgmLink.User -> launchCustomTab(context, url)
            is BgmLink.External -> launchCustomTab(context, url)
        }
    }

    LaunchedEffect(subjectType, uiState.episodes) {
        if (subjectType == SubjectType.GAME && uiState.episodes.isEmpty()) {
            selectedTab = SubjectDetailTab.DETAILS
        }
    }

    val groupedEpisodes =
        remember(uiState.episodes) {
            uiState.episodes.groupBy { EpisodeGroup.fromType(it.type) }
        }
    val availableGroups =
        remember(groupedEpisodes) {
            EpisodeGroup.entries.filter { groupedEpisodes.containsKey(it) }
        }
    var selectedGroup by rememberSaveable {
        mutableStateOf(EpisodeGroup.MAIN)
    }
    val activeGroup = if (selectedGroup in availableGroups) selectedGroup else availableGroups.firstOrNull() ?: EpisodeGroup.MAIN
    val currentEpisodes = groupedEpisodes[activeGroup] ?: emptyList()

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = uiState.subject?.displayName ?: "条目详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.subject != null,
            onRefresh = {
                viewModel.refresh()
                when (selectedTab) {
                    SubjectDetailTab.EPISODES -> Unit
                    SubjectDetailTab.DETAILS -> viewModel.loadDetailsTabIfNeeded(force = true)
                    SubjectDetailTab.COMMUNITY -> viewModel.loadCommunityTabIfNeeded(force = true)
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isLoading && uiState.subject == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    uiState.subject == null && uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "正在加载条目详情...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    uiState.subject == null && uiState.error != null -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Card(
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = "条目加载失败",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = uiState.error.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Button(
                                        onClick = viewModel::refresh,
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        Text(text = "重新加载")
                                    }
                                }
                            }
                        }
                    }

                    uiState.subject != null -> {
                        val subject = uiState.subject!!
                        val totalEpisodes = if (subject.eps > 0) subject.eps else subject.totalEpisodes
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (uiState.error != null) {
                                item(key = "inline_error") {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "同步提示：${uiState.error}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }

                            // 1. 条目头部 Hero 卡片
                            item(key = "header") {
                                SubjectHeaderCard(
                                    subject = subject,
                                    subjectType = subjectType,
                                )
                            }

                            // 2. 我的追番/阅读/收听/游玩与进度条面板
                            item(key = "collection_progress_bar") {
                                SubjectPersonalProgressCard(
                                    collection = uiState.collection,
                                    totalEpisodes = totalEpisodes,
                                    subjectType = subjectType,
                                    onOpenSheet = { showCollectionSheet = true },
                                    onToggleWatching = viewModel::toggleWatching,
                                )
                            }

                            // 3. 粘性二级分栏 Tab 栏
                            stickyHeader(key = "subject_tabs_bar") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                ) {
                                    PrimaryTabRow(
                                        selectedTabIndex = selectedTab.ordinal,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        SubjectDetailTab.entries.forEach { tab ->
                                            val tabLabel = getTabLabel(tab, subjectType)
                                            Tab(
                                                selected = selectedTab == tab,
                                                onClick = { selectedTab = tab },
                                                text = {
                                                    Text(
                                                        text =
                                                            when (tab) {
                                                                SubjectDetailTab.EPISODES ->
                                                                    if (currentEpisodes.isNotEmpty()) {
                                                                        "$tabLabel (${currentEpisodes.size})"
                                                                    } else {
                                                                        tabLabel
                                                                    }
                                                                SubjectDetailTab.COMMUNITY ->
                                                                    if (uiState.subjectCommentTotal > 0) {
                                                                        "$tabLabel (${uiState.subjectCommentTotal})"
                                                                    } else {
                                                                        tabLabel
                                                                    }
                                                                else -> tabLabel
                                                            },
                                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Tab 切换内容
                            when (selectedTab) {
                                SubjectDetailTab.EPISODES -> {
                                    val watchedInGroup = currentEpisodes.count { isEpisodeWatched(it, uiState.collection?.epStatus ?: 0) }

                                    item(key = "episodes_header") {
                                        EpisodesSectionHeader(
                                            totalEpisodes = currentEpisodes.size,
                                            watchedEpisodes = watchedInGroup,
                                            subjectType = subjectType,
                                            isGridView = isGridView,
                                            onToggleView = { isGridView = !isGridView },
                                        )
                                    }

                                    if (availableGroups.size > 1) {
                                        item(key = "episode_group_chips") {
                                            EpisodeGroupFilterChips(
                                                availableGroups = availableGroups,
                                                groupedEpisodes = groupedEpisodes,
                                                selectedGroup = activeGroup,
                                                onGroupSelected = { selectedGroup = it },
                                            )
                                        }
                                    }

                                    if (currentEpisodes.isEmpty()) {
                                        item(key = "episodes_empty") {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = if (uiState.isLoading) "正在加载章节列表..." else "暂无分集信息",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    } else if (isGridView) {
                                        item(key = "episodes_grid") {
                                            EpisodeGrid(
                                                episodes = currentEpisodes,
                                                watchedCount = uiState.collection?.epStatus ?: 0,
                                                onToggleWatched = { episode, isWatched ->
                                                    val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
                                                    viewModel.toggleEpisodeWatched(episode.id, isWatched, epNumber)
                                                },
                                                onEpisodeLongClick = { episode ->
                                                    selectedEpisodeForDetail = episode
                                                },
                                            )
                                        }
                                    } else {
                                        items(items = currentEpisodes, key = { it.id }) { episode ->
                                            val isWatched = isEpisodeWatched(episode, uiState.collection?.epStatus ?: 0)
                                            val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
                                            EpisodeListItem(
                                                episode = episode,
                                                isWatched = isWatched,
                                                onClick = {
                                                    selectedEpisodeForDetail = episode
                                                },
                                                onToggleWatched = {
                                                    viewModel.toggleEpisodeWatched(episode.id, !isWatched, epNumber)
                                                },
                                            )
                                        }
                                    }
                                }

                                SubjectDetailTab.DETAILS -> {
                                    item(key = "rating_distribution") {
                                        RatingDistributionCard(
                                            rating = subject.rating,
                                            collection = subject.collection,
                                            tags = subject.tags,
                                            onTagClick = onTagClick,
                                        )
                                    }

                                    if (uiState.isDetailsLoading &&
                                        uiState.relations.isEmpty() &&
                                        uiState.characters.isEmpty() &&
                                        uiState.persons.isEmpty()
                                    ) {
                                        item(key = "details_loading_indicator") {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            }
                                        }
                                    }

                                    if (uiState.relations.isNotEmpty()) {
                                        item(key = "relations_section") {
                                            RelationsSection(
                                                relations = uiState.relations,
                                                onSubjectClick = onSubjectClick,
                                            )
                                        }
                                    }

                                    if (uiState.characters.isNotEmpty()) {
                                        item(key = "characters_section") {
                                            CharactersSection(
                                                characters = uiState.characters,
                                                onCharacterClick = handleCharacterClick,
                                                onActorClick = handlePersonClick,
                                                onPreviewCharacter = { previewCharacter = it },
                                            )
                                        }
                                    }

                                    if (uiState.persons.isNotEmpty()) {
                                        item(key = "staff_section") {
                                            StaffSection(
                                                persons = uiState.persons,
                                                onPersonClick = handlePersonClick,
                                            )
                                        }
                                    }
                                }

                                SubjectDetailTab.COMMUNITY -> {
                                    item(key = "community_tab_section") {
                                        SubjectCommunitySection(
                                            comments = uiState.subjectComments,
                                            commentTotal = uiState.subjectCommentTotal,
                                            isLoadingMoreComments = uiState.isLoadingMoreComments,
                                            hasMoreComments = uiState.hasMoreComments,
                                            onLoadMoreComments = { viewModel.loadMoreSubjectComments() },
                                            topics = uiState.subjectTopics,
                                            onUrlClick = handleLinkClick,
                                            isLoading = uiState.isCommunityLoading,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCollectionSheet) {
        CollectionStatusBottomSheet(
            currentCollection = uiState.collection,
            subjectType = subjectType,
            onDismiss = { showCollectionSheet = false },
            onSave = { type, rate, comment, private ->
                viewModel.updateCollectionStatus(
                    type = type,
                    rate = rate,
                    comment = comment,
                    private = private,
                )
            },
        )
    }

    selectedEpisodeForDetail?.let { ep ->
        val isWatched = isEpisodeWatched(ep, uiState.collection?.epStatus ?: 0)
        EpisodeDetailBottomSheet(
            episode = ep,
            isWatched = isWatched,
            comments = uiState.episodeComments[ep.id].orEmpty(),
            isLoadingComments = uiState.isEpisodeCommentsLoading,
            onLoadComments = { viewModel.loadEpisodeComments(ep.id) },
            onDismiss = { selectedEpisodeForDetail = null },
            onToggleWatched = { episode, watched ->
                val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
                viewModel.toggleEpisodeWatched(episode.id, watched, epNumber)
            },
            onUrlClick = handleLinkClick,
        )
    }

    previewCharacter?.let { character ->
        CharacterImagePreviewDialog(
            character = character,
            onDismiss = { previewCharacter = null },
            onViewDetail = { characterId ->
                handleCharacterClick(characterId)
            },
        )
    }

    activeCharacter?.let { character ->
        CharacterDetailBottomSheet(
            character = character,
            detail = uiState.selectedCharacterDetail,
            relatedWorks = uiState.selectedCharacterWorks,
            isLoading = uiState.isLoadingEntityDetail,
            onDismiss = {
                activeCharacter = null
                viewModel.clearEntityDetail()
            },
            onSubjectClick = { relSubjectId ->
                activeCharacter = null
                viewModel.clearEntityDetail()
                onSubjectClick(relSubjectId)
            },
            onActorClick = { actorId ->
                activeCharacter = null
                activePerson =
                    viewModel.uiState.value.persons
                        .firstOrNull { it.id == actorId }
                        ?: SubjectPerson(id = actorId, name = "")
                viewModel.loadPersonDetail(actorId)
            },
        )
    }

    activePerson?.let { person ->
        PersonDetailBottomSheet(
            person = person,
            detail = uiState.selectedPersonDetail,
            relatedWorks = uiState.selectedPersonWorks,
            isLoading = uiState.isLoadingEntityDetail,
            onDismiss = {
                activePerson = null
                viewModel.clearEntityDetail()
            },
            onSubjectClick = { relSubjectId ->
                activePerson = null
                viewModel.clearEntityDetail()
                onSubjectClick(relSubjectId)
            },
        )
    }
}
