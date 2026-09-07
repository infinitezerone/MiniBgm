package com.infinitezerone.minibgm.feature.subject

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.bbcode.BgmBbCodeContent
import com.infinitezerone.minibgm.core.designsystem.theme.RatingGold
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.feature.subject.components.CharacterDetailBottomSheet
import com.infinitezerone.minibgm.feature.subject.components.PersonDetailBottomSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

private const val BGM_BASE_URL = "https://bgm.tv"

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
    val subjectType =
        remember(uiState.subject?.type) {
            uiState.subject?.type?.let { SubjectType.fromValue(it) } ?: SubjectType.ANIME
        }
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var selectedEpisodeForDetail by remember { mutableStateOf<Episode?>(null) }
    var previewCharacter by remember { mutableStateOf<SubjectCharacter?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(SubjectDetailTab.EPISODES) }

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
            onRefresh = viewModel::refresh,
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

/** 关联作品区域 */
@Composable
private fun RelationsSection(
    relations: List<SubjectRelation>,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "关联作品",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = relations, key = { "${it.id}_${it.relation}" }) { relation ->
                RelationCard(
                    relation = relation,
                    onClick = { onSubjectClick(relation.id) },
                )
            }
        }
    }
}

/** 关联作品卡片 */
@Composable
private fun RelationCard(
    relation: SubjectRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(120.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                CoverImage(
                    url = relation.images?.bestImage.orEmpty(),
                    contentDescription = relation.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp,
                    aspectRatio = 0.7f,
                )
                if (relation.relation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = relation.relation,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = relation.score.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 登场角色与声优区域 */
@Composable
private fun CharactersSection(
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
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = characters, key = { it.id }) { character ->
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

/** 角色卡片：头部正容立绘（顶部对齐防裁切）、主角/配角定位、放大立绘按钮、声优信息与点击跳转 */
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
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 顶部对齐（TopCenter）裁切：确保长条立绘优先完整显示头部、面部与眼神，消除“只有身子”的问题
                CoverImage(
                    url = character.images?.bestImage.orEmpty(),
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp,
                    aspectRatio = 0.72f,
                    alignment = Alignment.TopCenter,
                )

                // 主角 / 配角定位标签
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

                // 放大预览按钮：点击查看全身完整立绘
                Surface(
                    onClick = { onPreviewCharacter(character) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ZoomIn,
                            contentDescription = "查看全身立绘",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 角色名字
            Text(
                text = character.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // 声优信息：独立胶囊，点击查看声优详情
            val actor = character.actors.firstOrNull()
            if (actor != null && actor.name.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = { onActorClick(actor.id) },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        val actorImage = actor.images?.bestImage.orEmpty()
                        if (actorImage.isNotBlank()) {
                            CoverImage(
                                url = actorImage,
                                contentDescription = actor.name,
                                modifier = Modifier.size(18.dp),
                                cornerRadius = 9.dp,
                                aspectRatio = 1f,
                                alignment = Alignment.TopCenter,
                            )
                        }
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
private fun StaffSection(
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
            shape = RoundedCornerShape(14.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
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
private fun CharacterImagePreviewDialog(
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
                        imageVector = Icons.Filled.Close,
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

/** 条目头部卡片：立体圆角海报、完整译名与原名、年份季度徽章、评分与全站 Rank、可展开简介 */
@Composable
private fun SubjectHeaderCard(
    subject: Subject,
    subjectType: SubjectType,
    modifier: Modifier = Modifier,
) {
    var isSummaryExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 立体圆角海报
                Box(
                    modifier =
                        Modifier
                            .width(108.dp)
                            .height(152.dp)
                            .clip(RoundedCornerShape(10.dp)),
                ) {
                    CoverImage(
                        url = subject.images?.bestImage.orEmpty(),
                        contentDescription = subject.displayName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 右侧信息区
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = subject.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    if (subject.name.isNotBlank() && subject.name != subject.displayName) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.9f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 类型徽章、放送/发行日期与集数标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "${subjectType.iconEmoji} ${subjectType.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                        }

                        val dateText = subject.date.ifBlank { subject.airDate }
                        if (dateText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                )
                            }
                        }

                        val episodeCount = if (subject.eps > 0) subject.eps else subject.totalEpisodes
                        if (episodeCount > 0 && subjectType != SubjectType.GAME) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "全 $episodeCount ${subjectType.unitName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                )
                            }
                        }
                    }

                    // 评分与 Rank 黄金徽章
                    val rating = subject.rating
                    if (rating != null && rating.score > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RatingGold.copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, RatingGold.copy(alpha = 0.5f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = RatingGold,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rating.score.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RatingGold,
                                    )
                                }
                            }

                            if (rating.rank > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "Rank #${rating.rank}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            if (rating.total > 0) {
                                Text(
                                    text = "${rating.total}人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            if (subject.summary.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = subject.summary.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isSummaryExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isSummaryExpanded = !isSummaryExpanded }
                            .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSummaryExpanded) "收起简介" else "展开完整简介",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isSummaryExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 评分分布柱状图、全站收藏分布与热门标签卡片 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingDistributionCard(
    rating: Rating?,
    collection: CollectionCount?,
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasRatingData = rating != null && (rating.total > 0 || rating.count.isNotEmpty())
    val hasCollectionData =
        collection != null &&
            (collection.wish > 0 || collection.collect > 0 || collection.doing > 0 || collection.onHold > 0 || collection.dropped > 0)
    val hasTags = tags.isNotEmpty()

    if (!hasRatingData && !hasCollectionData && !hasTags) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // a. 评分分布柱状图
            if (rating != null && (rating.total > 0 || rating.count.isNotEmpty())) {
                RatingDistributionSection(rating = rating)
            }

            // 分割线
            if (hasRatingData && (hasCollectionData || hasTags)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // b. 全站收藏状态分布
            if (collection != null &&
                (collection.wish > 0 || collection.collect > 0 || collection.doing > 0 || collection.onHold > 0 || collection.dropped > 0)
            ) {
                CollectionStatsSection(collection = collection)
            }

            // 分割线
            if (hasCollectionData && hasTags) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // c. 热门标签
            if (hasTags) {
                TagsSection(tags = tags, onTagClick = onTagClick)
            }
        }
    }
}

/** 评分分布柱状图：10 根垂直柱状图、分数标签、mode 主色高亮 */
@Composable
private fun RatingDistributionSection(
    rating: Rating,
    modifier: Modifier = Modifier,
) {
    val counts =
        (1..10).map { score ->
            score to (rating.count[score.toString()] ?: 0)
        }
    val maxCount = counts.maxOfOrNull { it.second } ?: 0
    val modeScore = if (maxCount > 0) counts.maxByOrNull { it.second }?.first else null

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "评分分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (rating.score > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = rating.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (rating.total > 0) {
                        Text(
                            text = "(${rating.total}人评分)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 10 根垂直柱状图 (1..10 分)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { (score, count) ->
                val isMode = score == modeScore && count > 0
                val ratio = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (count > 0) formatCompactNumber(count) else "",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                            ),
                        fontWeight = if (isMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // 背景底槽
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .width(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )

                        // 填充柱体
                        if (count > 0) {
                            val barHeightFraction = (ratio * 0.92f + 0.08f).coerceIn(0.08f, 1f)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxHeight(barHeightFraction)
                                        .width(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isMode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            },
                                        ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isMode) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (isMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

/** 全站收藏状态分布：想看、在看、看过、搁置、抛弃 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionStatsSection(
    collection: CollectionCount,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "全站收藏状态",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollectionStatusBadge(
                label = "想看",
                count = collection.wish,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            CollectionStatusBadge(
                label = "在看",
                count = collection.doing,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            CollectionStatusBadge(
                label = "看过",
                count = collection.collect,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            CollectionStatusBadge(
                label = "搁置",
                count = collection.onHold,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CollectionStatusBadge(
                label = "抛弃",
                count = collection.dropped,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 收藏状态小徽章 */
@Composable
private fun CollectionStatusBadge(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

/** 热门标签芯片流 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "热门标签",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                Surface(
                    onClick = { onTagClick(tag.name) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (tag.count > 0) {
                            Text(
                                text = tag.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 紧凑数字格式化（如 1.8k, 15k） */
private fun formatCompactNumber(number: Int): String =
    when {
        number >= 10_000 -> "${number / 1000}k"
        number >= 1_000 -> "${number / 1000}.${(number % 1000) / 100}k"
        number > 0 -> number.toString()
        else -> "0"
    }

/** 个人追番/阅读/收听/游玩状态与进度卡片 */
@Composable
private fun SubjectPersonalProgressCard(
    collection: UserCollection?,
    totalEpisodes: Int,
    subjectType: SubjectType,
    onOpenSheet: () -> Unit,
    onToggleWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEp = collection?.epStatus ?: 0
    val progress = if (totalEpisodes > 0) (currentEp.toFloat() / totalEpisodes).coerceIn(0f, 1f) else 0f

    val cardTitle =
        when (subjectType) {
            SubjectType.BOOK -> "我的阅读与进度"
            SubjectType.MUSIC -> "我的收听与进度"
            SubjectType.GAME -> "我的游玩与评测"
            SubjectType.ANIME, SubjectType.REAL -> "我的追番与进度"
        }

    val actionButtonText =
        if (collection != null) {
            "修改"
        } else {
            when (subjectType) {
                SubjectType.BOOK -> "追读"
                SubjectType.MUSIC -> "收听"
                SubjectType.GAME -> "在玩"
                SubjectType.ANIME, SubjectType.REAL -> "追番"
            }
        }

    Card(
        onClick = onOpenSheet,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (collection != null) {
                        val verb = CollectionType.fromValue(collection.type).getVerb(subjectType)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = verb,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = if (collection != null) onOpenSheet else onToggleWatching,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (collection != null) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionButtonText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (collection != null) {
                if (subjectType == SubjectType.GAME) {
                    val statusVerb = CollectionType.fromValue(collection.type).getVerb(subjectType)
                    Text(
                        text = "游玩状态：$statusVerb",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else if (totalEpisodes > 0 || (subjectType == SubjectType.BOOK && collection.volStatus > 0)) {
                    val progressLabel =
                        when (subjectType) {
                            SubjectType.BOOK -> {
                                val vol = collection.volStatus
                                val ep = collection.epStatus
                                if (vol > 0) "已读 $vol 卷 · $ep 话" else "已读 $ep / 全 $totalEpisodes 话"
                            }
                            SubjectType.MUSIC -> "已听 $currentEp / 全 $totalEpisodes 首"
                            else -> "已看 $currentEp / 全 $totalEpisodes 话"
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (totalEpisodes > 0) {
                            Text(
                                text = "${(progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (totalEpisodes > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (collection.rate > 0) {
                        Text(
                            text = "★ ${collection.rate}分 · ${getScoreLabel(collection.rate)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RatingGold,
                        )
                    }
                    if (collection.comment.isNotBlank()) {
                        Text(
                            text = "「${collection.comment}」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                val idlePrompt =
                    when (subjectType) {
                        SubjectType.BOOK -> "点击记录阅读状态、已读卷数与个人短评"
                        SubjectType.MUSIC -> "点击记录收听状态、已听曲目与个人短评"
                        SubjectType.GAME -> "点击记录游玩状态、通关评价与心得打分"
                        SubjectType.ANIME, SubjectType.REAL -> "点击记录追番状态、更新观看进度与个人打分"
                    }
                Text(
                    text = idlePrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 收藏状态 BottomSheet：单选状态、1~10 评分器、私密开关、短评输入 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CollectionStatusBottomSheet(
    currentCollection: UserCollection?,
    subjectType: SubjectType,
    onDismiss: () -> Unit,
    onSave: (type: CollectionType, rate: Int?, comment: String?, private: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by rememberSaveable {
        mutableStateOf(
            currentCollection?.type?.let { CollectionType.fromValue(it) } ?: CollectionType.DOING,
        )
    }
    var rating by rememberSaveable { mutableIntStateOf(currentCollection?.rate ?: 0) }
    var comment by rememberSaveable { mutableStateOf(currentCollection?.comment.orEmpty()) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "标记条目状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // 1. 收藏状态单选
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "收藏类型",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CollectionType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(text = type.getVerb(subjectType)) },
                            leadingIcon =
                                if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            // 2. 评分打分器 (1~10 分)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "我的评分",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (rating == 0) "不评分" else "$rating 分 · ${getScoreLabel(rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rating > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 快捷 1~10 星打分器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (star in 1..10) {
                        IconButton(
                            onClick = { rating = if (rating == star) 0 else star },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star 分",
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 3. 私密收藏开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "仅自己可见 (私密收藏)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it },
                )
            }

            // 4. 短评输入框
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("简评 / 吐槽") },
                placeholder = { Text("写下你的追番感想或评价...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            // 5. 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "取消")
                }
                Button(
                    onClick = {
                        onSave(
                            selectedType,
                            if (rating > 0) rating else null,
                            comment.ifBlank { null },
                            isPrivate,
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "保存")
                }
            }
        }
    }
}

/** 分集/曲目/章节列表头部栏：总数/打卡进度与列表/网格切换 */
@Composable
private fun EpisodesSectionHeader(
    totalEpisodes: Int,
    watchedEpisodes: Int,
    subjectType: SubjectType,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerTitle =
        when (subjectType) {
            SubjectType.MUSIC -> "曲目列表"
            SubjectType.BOOK -> "章节与卷册"
            SubjectType.GAME -> "关卡与章节"
            SubjectType.ANIME, SubjectType.REAL -> "分集列表"
        }

    val progressLabel =
        when (subjectType) {
            SubjectType.MUSIC -> "已听 $watchedEpisodes / 全 $totalEpisodes 首"
            SubjectType.BOOK -> "已读 $watchedEpisodes / 全 $totalEpisodes 话"
            SubjectType.GAME -> "已过 $watchedEpisodes / 全 $totalEpisodes 关"
            SubjectType.ANIME, SubjectType.REAL -> "已看 $watchedEpisodes / 全 $totalEpisodes 话"
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (totalEpisodes > 0) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.Filled.FormatListNumbered else Icons.Filled.GridView,
                contentDescription = if (isGridView) "切换为列表视图" else "切换为网格视图",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 分集类型分组枚举 */
enum class EpisodeGroup(
    val label: String,
) {
    MAIN("本篇"),
    SP("特别篇"),
    OP_ED("OP/ED"),
    OTHER("其他"),
    ;

    companion object {
        fun fromType(type: Int): EpisodeGroup =
            when (type) {
                0 -> MAIN
                1 -> SP
                2, 3 -> OP_ED
                else -> OTHER
            }
    }
}

/** 分集类型筛选 Chip 栏 */
@Composable
private fun EpisodeGroupFilterChips(
    availableGroups: List<EpisodeGroup>,
    groupedEpisodes: Map<EpisodeGroup, List<Episode>>,
    selectedGroup: EpisodeGroup,
    onGroupSelected: (EpisodeGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(items = availableGroups, key = { it.name }) { group ->
            val count = groupedEpisodes[group]?.size ?: 0
            val isSelected = group == selectedGroup
            FilterChip(
                selected = isSelected,
                onClick = { onGroupSelected(group) },
                label = { Text("${group.label} ($count)") },
                leadingIcon =
                    if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

/** 分集列表项（列表模式） */
@Composable
private fun EpisodeListItem(
    episode: Episode,
    isWatched: Boolean,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isWatched) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                    if (isWatched) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {
                val group = EpisodeGroup.fromType(episode.type)
                val epLabel =
                    if (episode.type == 0) {
                        "第 ${episode.ep.toEpisodeLabel()} 话"
                    } else {
                        "${group.label} ${episode.sort.toInt()}"
                    }
                Text(
                    text = epLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isWatched) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWatched) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleParts =
                    buildList {
                        if (episode.airdate.isNotBlank()) add("放送：${episode.airdate}")
                        if (episode.duration.isNotBlank()) add(episode.duration)
                    }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (episode.comment > 0) {
                        val isHot = episode.comment >= 50
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color =
                                if (isHot) {
                                    WishOrange.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Icon(
                                    imageVector = if (isHot) Icons.Filled.LocalFireDepartment else Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isHot) WishOrange else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "${episode.comment} 吐槽",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHot) WishOrange else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onToggleWatched,
                colors =
                    if (isWatched) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.Check else Icons.Outlined.Check,
                    contentDescription = if (isWatched) "已看过，点击取消打卡" else "未看，点击打卡",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 分集网格布局（网格模式） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    watchedCount: Int,
    onToggleWatched: (episode: Episode, isWatched: Boolean) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.chunked(6).forEach { rowEpisodes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEpisodes.forEach { episode ->
                    val isWatched = isEpisodeWatched(episode, watchedCount)
                    val label =
                        if (episode.type == 0) {
                            episode.ep.toEpisodeLabel()
                        } else {
                            val prefix =
                                when (episode.type) {
                                    1 -> "SP"
                                    2 -> "OP"
                                    3 -> "ED"
                                    else -> "E"
                                }
                            "$prefix${episode.sort.toInt()}"
                        }
                    val cellShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(cellShape)
                                .background(
                                    if (isWatched) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ).then(
                                    if (isWatched) {
                                        Modifier
                                    } else {
                                        Modifier.border(
                                            BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            cellShape,
                                        )
                                    },
                                ).combinedClickable(
                                    onClick = { onToggleWatched(episode, !isWatched) },
                                    onLongClick = { onEpisodeLongClick(episode) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (isWatched) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            if (isWatched) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }

                        if (episode.comment >= 100) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(WishOrange),
                            )
                        }
                    }
                }
                // 补齐末行空位保持对齐
                repeat(6 - rowEpisodes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 单集详情 BottomSheet */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EpisodeDetailBottomSheet(
    episode: Episode,
    isWatched: Boolean,
    comments: List<EpisodeComment>,
    isLoadingComments: Boolean,
    onLoadComments: () -> Unit,
    onDismiss: () -> Unit,
    onToggleWatched: (episode: Episode, isWatched: Boolean) -> Unit,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val group = EpisodeGroup.fromType(episode.type)
    val episodeNumberText =
        if (episode.type == 0) {
            "第 ${episode.ep.toEpisodeLabel()} 话"
        } else {
            "${group.label} ${episode.sort.toInt()}"
        }

    LaunchedEffect(episode.id) {
        onLoadComments()
    }

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
            // 1. 分集序号与标题 (中文名 & 原名)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = episodeNumberText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    if (episode.type != 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Text(
                    text = episode.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (episode.name.isNotBlank() && episode.name != episode.displayTitle) {
                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 2. 放送时间与时长 Chip 标签
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (episode.airdate.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "放送：${episode.airdate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (episode.duration.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "时长：${episode.duration}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 3. "已看过 / 未看" toggle button with instant check-in
            if (isWatched) {
                FilledTonalButton(
                    onClick = {
                        onToggleWatched(episode, false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "已看过 · 点击取消打卡")
                }
            } else {
                Button(
                    onClick = {
                        onToggleWatched(episode, true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "标记为看过 (打卡)")
                }
            }

            // 4. 剧情梗概 (Full desc)
            if (episode.desc.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "剧情梗概",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = episode.desc.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 5. 单集吐槽与讨论 (Episode Comments Section)
            EpisodeCommentsSection(
                episode = episode,
                comments = comments,
                isLoading = isLoadingComments,
                onUrlClick = onUrlClick,
            )
        }
    }
}

/** 单集吐槽与讨论板块 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeCommentsSection(
    episode: Episode,
    comments: List<EpisodeComment>,
    isLoading: Boolean,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "💬 本集吐槽与讨论",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (comments.isNotEmpty() || episode.comment > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${if (comments.isNotEmpty()) comments.size else episode.comment} 条",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (isLoading && comments.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "正在获取单集吐槽...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (comments.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "本集暂无吐槽",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                comments.forEach { comment ->
                    EpisodeCommentItem(
                        comment = comment,
                        onUrlClick = onUrlClick,
                    )
                }
            }
        }
    }
}

/** 单条吐槽卡片项（含头像、发布者、内容、点赞反应与楼中楼） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeCommentItem(
    comment: EpisodeComment,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsyncImage(
                        model =
                            comment.user
                                ?.avatar
                                ?.bestAvatar,
                        contentDescription = comment.user?.displayName,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Text(
                        text = comment.user?.displayName ?: "用户",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (comment.createdAt > 0) {
                    Text(
                        text = TimeUtils.formatEpochSecondsToDate(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            BgmBbCodeContent(
                content = comment.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                onUrlClick = onUrlClick,
            )

            // 点赞反应
            if (comment.reactions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    comment.reactions.forEach { reaction ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "❤️ ${reaction.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // 楼中楼回复
            if (comment.replies.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(8.dp),
                            ).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.replies.forEach { reply ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AsyncImage(
                                model =
                                    reply.user
                                        ?.avatar
                                        ?.bestAvatar
                                        .orEmpty(),
                                contentDescription = reply.user?.displayName,
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = reply.user?.displayName ?: "回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                BgmBbCodeContent(
                                    content = reply.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    onUrlClick = onUrlClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 社区标签页 / 讨论模块：原生展示短评精选与小组讨论帖子 */
@Composable
private fun SubjectCommunitySection(
    comments: List<SubjectComment>,
    commentTotal: Int,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    onLoadMoreComments: () -> Unit,
    topics: List<SubjectTopic>,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val displayComments = if (isExpanded) comments else comments.take(5)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 全网即时短评
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "全网短评吐槽",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (commentTotal > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "$commentTotal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (comments.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无短评，快去发表你的看法吧~",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        displayComments.forEachIndexed { index, comment ->
                            SubjectCommentItem(
                                comment = comment,
                                onUrlClick = onUrlClick,
                            )
                            if (index < displayComments.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }

                        if (!isExpanded && (comments.size > 5 || commentTotal > 5)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isExpanded = true }
                                        .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "查看更多短评 (已显示 5 / 共 $commentTotal 条)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else if (isExpanded) {
                            if (hasMoreComments) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = !isLoadingMoreComments) { onLoadMoreComments() }
                                            .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isLoadingMoreComments) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "正在加载更多短评...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text = "加载更多短评 (已显示 ${comments.size} / 共 $commentTotal 条)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else if (comments.size >= commentTotal && commentTotal > 5) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "已显示全部 $commentTotal 条短评",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isExpanded = false }
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "收起短评",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 讨论版话题
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "讨论版交流区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (topics.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "${topics.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (topics.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无相关讨论帖",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        topics.take(6).forEachIndexed { index, topic ->
                            SubjectTopicItem(
                                topic = topic,
                                onClick = { launchCustomTab(context, "$BGM_BASE_URL/subject/topic/${topic.id}") },
                            )
                            if (index < topics.take(6).lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单条短评卡片组件 */
@Composable
private fun SubjectCommentItem(
    comment: SubjectComment,
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = comment.user?.avatar?.bestAvatar,
                    contentDescription = comment.user?.displayName,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = comment.user?.displayName.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (comment.rate > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = RatingGold.copy(alpha = 0.15f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = RatingGold,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = "${comment.rate}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RatingGold,
                            )
                        }
                    }
                }
            }

            if (comment.updatedAt > 0) {
                Text(
                    text = TimeUtils.formatEpochSecondsToDate(comment.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        if (comment.comment.isNotBlank()) {
            BgmBbCodeContent(
                content = comment.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                onUrlClick = onUrlClick,
            )
        }
    }
}

/** 讨论帖条目组件 */
@Composable
private fun SubjectTopicItem(
    topic: SubjectTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = topic.creator?.displayName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (topic.createdAt > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = TimeUtils.formatEpochSecondsToDate(topic.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (topic.replyCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "${topic.replyCount} 回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 使用 Chrome Custom Tabs 在应用内优雅打开网页 */
private fun launchCustomTab(
    context: Context,
    url: String,
) {
    try {
        val customTabsIntent =
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
        customTabsIntent.launchUrl(context, url.toUri())
    } catch (e: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(fallbackIntent)
    }
}

/** 辅助方法：判断分集是否已看过 */
private fun isEpisodeWatched(
    episode: Episode,
    watchedCount: Int,
): Boolean {
    val epNumber = if (episode.ep > 0f) episode.ep.toInt() else episode.sort.toInt()
    return watchedCount >= epNumber && epNumber > 0
}

/** Bangumi 评分说明文案 */
private fun getScoreLabel(score: Int): String =
    when (score) {
        1 -> "不忍直视"
        2 -> "很差"
        3 -> "差"
        4 -> "较差"
        5 -> "不过不失"
        6 -> "还行"
        7 -> "推荐"
        8 -> "力荐"
        9 -> "神作"
        10 -> "极品"
        else -> "未评分"
    }

/** 格式化分集话数编号 */
private fun Float.toEpisodeLabel(): String {
    if (this <= 0f) return "1"
    val whole = toInt()
    return if (this == whole.toFloat()) whole.toString() else toString()
}
