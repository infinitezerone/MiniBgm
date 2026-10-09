package com.infinitezerone.minibgm.feature.subject

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrderTabs
import com.infinitezerone.minibgm.feature.subject.components.TopicMainPostCard
import com.infinitezerone.minibgm.feature.subject.components.TopicReplyCard
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

private const val BGM_BASE_URL = "https://bgm.tv"

/**
 * 原生讨论帖详情界面（展示主楼、关联番剧、楼层回复与楼中楼子回复）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: Long,
    initialTitle: String = "",
    type: String = "subject",
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onTopicClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopicDetailViewModel =
        koinViewModel(
            parameters = { parametersOf(topicId, type) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val handleCopyContent: (String) -> Unit =
        remember(clipboard, coroutineScope, snackbarHostState) {
            { text ->
                coroutineScope.launch {
                    val clipEntry = ClipEntry(ClipData.newPlainText("topic_content", text))
                    clipboard.setClipEntry(clipEntry)
                    snackbarHostState.showSnackbar(context.getString(R.string.feature_subject_topic_copied))
                }
            }
        }

    // 表态结果与登录提示
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is TopicDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
        }
    }

    val topic = uiState.topicDetail
    val displayTitle = topic?.title ?: initialTitle.ifBlank { stringResource(R.string.feature_subject_topic_detail_title) }
    val opUserId = topic?.creator?.id
    val topicWebUrl =
        if (type == "group") {
            "$BGM_BASE_URL/group/topic/$topicId"
        } else {
            "$BGM_BASE_URL/subject/topic/$topicId"
        }

    val handleLinkClick: (String) -> Unit = { url ->
        when (val link = BgmUrlParser.parse(url)) {
            is BgmLink.Subject -> onSubjectClick(link.subjectId)
            is BgmLink.Topic -> onTopicClick(link.topicId, "")
            else -> context.launchWebUrl(url)
        }
    }

    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { context.launchWebUrl(topicWebUrl) }) {
                        Icon(
                            imageVector = BgmIcons.OpenInBrowser,
                            contentDescription = stringResource(R.string.feature_subject_action_open_in_browser),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showBackToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.feature_subject_topic_back_to_top),
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.feature_subject_topic_web_reply_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { context.launchWebUrl(topicWebUrl) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_topic_go_to_discuss),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh(isUserPullToRefresh = true) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                uiState.isLoading && topic == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && topic == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = uiState.error ?: stringResource(R.string.feature_subject_topic_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { viewModel.refresh(isUserPullToRefresh = false) }) {
                                Text(text = stringResource(DesignSystemR.string.core_designsystem_action_retry))
                            }
                        }
                    }
                }
                topic != null -> {
                    val sortedReplies = uiState.sortedFloorReplies
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 1 楼：楼主原帖卡片
                        item(key = "main_post_${topic.id}") {
                            TopicMainPostCard(
                                topic = topic,
                                onSubjectClick = onSubjectClick,
                                onUrlClick = handleLinkClick,
                                onUserClick = { username -> handleLinkClick("https://bgm.tv/user/$username") },
                                onCopyContent = handleCopyContent,
                                currentUserId = uiState.currentUserId,
                                onReactionClick = { reaction -> viewModel.toggleMainPostReaction(reaction) },
                                onAddReaction = { value -> viewModel.toggleMainPostReaction(value) },
                            )
                        }

                        // 回复标题行：回复计数与排序分段器
                        item(key = "replies_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text =
                                            if (topic.floorReplies.isNotEmpty()) {
                                                stringResource(R.string.feature_subject_topic_all_replies, topic.floorReplies.size)
                                            } else {
                                                stringResource(R.string.feature_subject_topic_no_replies)
                                            },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (topic.floorReplies.isNotEmpty()) {
                                        CommentSortOrderTabs(
                                            currentOrder = uiState.sortOrder,
                                            onOrderSelected = { order -> viewModel.setSortOrder(order) },
                                        )
                                    }
                                }
                            }
                        }

                        // 2 楼及后续楼层回帖列表
                        items(
                            items = sortedReplies,
                            key = { (_, reply) -> reply.id },
                        ) { (floorNumber, reply) ->
                            TopicReplyCard(
                                reply = reply,
                                floorNumber = floorNumber,
                                onUrlClick = handleLinkClick,
                                isOp = opUserId != null && reply.creator?.id == opUserId,
                                onUserClick = { username -> handleLinkClick("https://bgm.tv/user/$username") },
                                onCopyContent = handleCopyContent,
                                currentUserId = uiState.currentUserId,
                                onReactionClick = { reaction -> viewModel.toggleReaction(reply, reaction) },
                                onAddReaction = { reactionValue -> viewModel.toggleReaction(reply, reactionValue) },
                            )
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
