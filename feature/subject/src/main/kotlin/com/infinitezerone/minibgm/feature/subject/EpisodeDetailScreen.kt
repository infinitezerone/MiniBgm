package com.infinitezerone.minibgm.feature.subject

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.BgmLink
import com.infinitezerone.minibgm.core.common.BgmUrlParser
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.launchStreamingUrl
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrderTabs
import com.infinitezerone.minibgm.feature.subject.components.EpisodeCommentItem
import com.infinitezerone.minibgm.feature.subject.components.SubjectSourcesBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 分集详情与讨论全屏三级页面（由 Navigation 3 栈式路由驱动）。
 * 拥有完整的全屏沉浸式阅读空间与独立的滚动状态，
 * 天然支持多层级深度跳转及系统预测性侧滑返回（Predictive Back）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EpisodeDetailScreen(
    subjectId: Long,
    episodeId: Long,
    initialEpNumberText: String = "",
    initialEpisodeTitle: String = "",
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onTopicClick: (Long, String) -> Unit = { _, _ -> },
    onPlayClick: ((PlayerRoute) -> Unit)? = null,
    onSourceSearch: ((String) -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    onLoginRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EpisodeDetailViewModel =
        koinViewModel(
            parameters = { parametersOf(subjectId, episodeId) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val handleCopyComment: (String) -> Unit =
        remember(clipboard, coroutineScope, snackbarHostState) {
            { text ->
                coroutineScope.launch {
                    val clipEntry = ClipEntry(ClipData.newPlainText("comment", text))
                    clipboard.setClipEntry(clipEntry)
                    snackbarHostState.showSnackbar("已复制评论内容")
                }
            }
        }
    // 吐槽表态结果与登录提示
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is EpisodeDetailUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            is EpisodeDetailUiEvent.OpenSourceSearch -> onSourceSearch?.invoke(event.prefillPrompt)
        }
    }
    val overlayHostState = rememberOverlayHostState()
    var showSourcesSheet by remember { mutableStateOf(false) }

    val handleAppNotInstalled: (String, String) -> Unit = { appName, webUrl ->
        coroutineScope.launch {
            val confirmed =
                overlayHostState.await(
                    ConfirmDialogAction(
                        title = "未安装 $appName 客户端",
                        message = "未检测到 $appName 客户端，是否在应用内使用浏览器打开该播放源？",
                        confirmText = "浏览器打开",
                    ),
                )
            if (confirmed) {
                context.launchWebUrl(webUrl)
            }
        }
    }

    val episode = uiState.episode
    val group = episode?.let { EpisodeGroup.fromType(it.type) } ?: EpisodeGroup.MAIN
    val episodeNumberText =
        if (episode != null) {
            episode.guideLabel
        } else {
            initialEpNumberText.ifBlank { "分集详情" }
        }
    val displayTitle = episode?.displayTitle ?: initialEpisodeTitle.ifBlank { episodeNumberText }

    val handleLinkClick: (String) -> Unit =
        remember(context, onSubjectClick, onEpisodeClick, onCharacterClick, onPersonClick, onTopicClick) {
            { url ->
                when (val link = BgmUrlParser.parse(url)) {
                    is BgmLink.Subject -> onSubjectClick(link.subjectId)
                    is BgmLink.Episode -> onEpisodeClick(link.episodeId)
                    is BgmLink.Character -> onCharacterClick(link.characterId)
                    is BgmLink.Person -> onPersonClick(link.personId)
                    is BgmLink.Topic -> onTopicClick(link.topicId, "")
                    is BgmLink.User -> context.launchWebUrl(url)
                    is BgmLink.External -> {
                        context.launchStreamingUrl(
                            url = url,
                            onAppNotInstalled = handleAppNotInstalled,
                        )
                    }
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            BgmTopAppBar(
                title = {
                    Text(
                        text = "$episodeNumberText $displayTitle",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = BgmIcons.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (episode != null) {
                        if (onPlayClick != null) {
                            IconButton(
                                onClick = { onPlayClick(viewModel.buildPlayerRoute(episode)) },
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Play,
                                    contentDescription = "播放本集",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.toggleWatched(episode, !uiState.isWatched)
                            },
                        ) {
                            Icon(
                                imageVector = if (uiState.isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                                contentDescription = if (uiState.isWatched) "已看过" else "未看过",
                                tint =
                                    if (uiState.isWatched) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                },
            )
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                            .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. 分集精炼头部：话数徽标、连贯切集胶囊、标题与放送元信息
                    item(key = "episode_header") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // 顶栏：话数标签 + 分集属性 (左) 与 上/下一集切换器 (右)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false),
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

                                    if (episode != null && episode.type != 0) {
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

                                    if (episode != null) {
                                        val metaParts =
                                            listOfNotNull(
                                                episode.airdate.takeIf { it.isNotBlank() }?.let { "放送：$it" },
                                                episode.duration.takeIf { it.isNotBlank() }?.let { "时长：$it" },
                                            )
                                        if (metaParts.isNotEmpty()) {
                                            Text(
                                                text = metaParts.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                // 连贯切集胶囊：‹ 当前集/总集数 ›
                                val allEps = uiState.allEpisodes
                                if (allEps.size > 1) {
                                    val currentIndex = allEps.indexOfFirst { it.id == episodeId }
                                    if (currentIndex >= 0) {
                                        val prevEp = if (currentIndex > 0) allEps.getOrNull(currentIndex - 1) else null
                                        val nextEp = if (currentIndex < allEps.size - 1) allEps.getOrNull(currentIndex + 1) else null

                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            ) {
                                                IconButton(
                                                    onClick = { prevEp?.let { onEpisodeClick(it.id) } },
                                                    enabled = prevEp != null,
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = BgmIcons.KeyboardArrowLeft,
                                                        contentDescription = "上一集",
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                Text(
                                                    text = "${currentIndex + 1}/${allEps.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                IconButton(
                                                    onClick = { nextEp?.let { onEpisodeClick(it.id) } },
                                                    enabled = nextEp != null,
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = BgmIcons.KeyboardArrowRight,
                                                        contentDescription = "下一集",
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 主标题
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // 副标题（日文原名，不同于中文译名时展示）
                            if (episode != null && episode.name.isNotBlank() && episode.name != displayTitle) {
                                Text(
                                    text = episode.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 2. 辅助工具胶囊条（播放源与看到本集；轻量不霸屏）
                    if (episode != null && (!uiState.isWatched && episode.isMain && episode.episodeInt > 1 || onPlayClick != null)) {
                        item(key = "episode_tools") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    onClick = { showSourcesSheet = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.CloudQueue,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "播放源",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (!uiState.isWatched && episode.isMain && episode.episodeInt > 1) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            viewModel.markWatchedUpTo(episode)
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                imageVector = BgmIcons.FormatListNumbered,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = "看到此集 (1~${episode.episodeInt})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. 剧情梗概（轻量折叠卡片）
                    if (episode != null && episode.desc.isNotBlank()) {
                        item(key = "episode_desc") {
                            var isDescExpanded by rememberSaveable(episode.id) { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { isDescExpanded = !isDescExpanded },
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "剧情梗概",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = if (isDescExpanded) "收起 ∧" else "展开 ∨",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = episode.desc.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // 5. 分割线
                    item(key = "comments_divider") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // 6. 吐槽标题行
                    item(key = "comments_header") {
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
                                    text = "本集吐槽与讨论",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                val totalCount = if (uiState.comments.isNotEmpty()) uiState.comments.size else (episode?.comment ?: 0)
                                if (totalCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = "$totalCount 条",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (uiState.comments.isNotEmpty()) {
                                CommentSortOrderTabs(
                                    currentOrder = uiState.commentSortOrder,
                                    onOrderSelected = { order ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setCommentSortOrder(order)
                                    },
                                )
                            }
                        }
                    }

                    // 7. 吐槽列表状态 (Loading / Error / Empty / Virtualized items)
                    if (uiState.isCommentsLoading && uiState.comments.isEmpty()) {
                        item(key = "comments_loading") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = "正在获取单集吐槽...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else if (uiState.commentsError != null && uiState.comments.isEmpty()) {
                        item(key = "comments_error") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = BgmIcons.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Text(
                                        text = "吐槽加载失败",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = uiState.commentsError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    FilledTonalButton(
                                        onClick = { viewModel.retryLoadComments() },
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = BgmIcons.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "重新加载")
                                    }
                                }
                            }
                        }
                    } else if (uiState.comments.isEmpty()) {
                        item(key = "comments_empty") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "本集暂无吐槽，快来做第一个讨论的人吧",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = uiState.comments,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> "episode_comment" },
                        ) { index, comment ->
                            EpisodeCommentItem(
                                comment = comment,
                                floorNumber = if (comment.floor > 0) comment.floor else (index + 1),
                                onUrlClick = handleLinkClick,
                                onUserClick = { username -> handleLinkClick("https://bgm.tv/user/$username") },
                                onCopyComment = handleCopyComment,
                                currentUserId = uiState.currentUserId,
                                onReactionClick = { reaction -> viewModel.toggleCommentReaction(comment, reaction) },
                                onAddReaction = { reactionValue -> viewModel.toggleCommentReaction(comment, reactionValue) },
                            )
                        }
                    }
                }
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }

    val currentEpisode = uiState.episode
    val currentSubject = uiState.subject
    if (showSourcesSheet && currentSubject != null && currentEpisode != null) {
        SubjectSourcesBottomSheet(
            subject = currentSubject,
            episode = currentEpisode,
            onDismissRequest = { showSourcesSheet = false },
            onOpenUrl = { url ->
                context.launchStreamingUrl(
                    url = url,
                    onAppNotInstalled = handleAppNotInstalled,
                )
            },
            onInternalPlayClick =
                onPlayClick?.let { play ->
                    { route ->
                        showSourcesSheet = false
                        play(route)
                    }
                },
            onAiSourceSearch =
                if (onSourceSearch != null) {
                    { viewModel.requestSourceSearch() }
                } else {
                    null
                },
            onManageRules =
                onManageRules?.let { manage ->
                    {
                        showSourcesSheet = false
                        manage()
                    }
                },
            playbackRules = uiState.playbackRules,
            playlists = uiState.playlists,
            failedSourceReasons = uiState.failedSourceReasons,
        )
    }

    if (uiState.showLoginPromptDialog) {
        BgmLoginPromptDialog(
            description = "分集打卡需要同步至您的 Bangumi 账号，登录后即可随手打卡并同步进度。",
            onLogin = {
                viewModel.dismissLoginPrompt()
                onLoginRequest()
            },
            onDismiss = viewModel::dismissLoginPrompt,
        )
    }
}
