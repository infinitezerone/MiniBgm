package com.infinitezerone.minibgm.feature.subject

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
                    // 1. 分集主控卡片：话数徽标、标题、放送元信息、大尺寸播放与打卡主控
                    item(key = "episode_hero_card") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // 顶栏：话数标签 + 分集属性 + 放送与时长元信息
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                        }
                                    }
                                }

                                // 标题区：中文大标题 + 日文原名副标题
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    if (episode != null && episode.name.isNotBlank() && episode.name != displayTitle) {
                                        Text(
                                            text = episode.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )

                                // 双大主控按钮：[ ▶ 播放本集 ] 与 [ ✓ 标记已看 / 已看过 ]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (onPlayClick != null && episode != null) {
                                        Button(
                                            onClick = { onPlayClick(viewModel.buildPlayerRoute(episode)) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                imageVector = BgmIcons.Play,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "播放本集",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }

                                    if (episode != null) {
                                        FilledTonalButton(
                                            onClick = { viewModel.toggleWatched(episode, !uiState.isWatched) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            colors =
                                                if (uiState.isWatched) {
                                                    ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                } else {
                                                    ButtonDefaults.filledTonalButtonColors()
                                                },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (uiState.isWatched) "已看过" else "标记已看",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }

                                // 辅助快捷入口：播放源切换与一键批量标记到此集
                                if (episode != null &&
                                    (onPlayClick != null || (!uiState.isWatched && episode.isMain && episode.episodeInt > 1))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            onClick = { showSourcesSheet = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = BgmIcons.CloudQueue,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    text = "选择播放源",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        if (!uiState.isWatched && episode.isMain && episode.episodeInt > 1) {
                                            Surface(
                                                onClick = { viewModel.markWatchedUpTo(episode) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = BgmIcons.FormatListNumbered,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text(
                                                        text = "看到此集 (1~${episode.episodeInt})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. 舒展大触控双向切集控制条
                    val allEps = uiState.allEpisodes
                    if (allEps.size > 1) {
                        val currentIndex = allEps.indexOfFirst { it.id == episodeId }
                        if (currentIndex >= 0) {
                            val prevEp = if (currentIndex > 0) allEps.getOrNull(currentIndex - 1) else null
                            val nextEp = if (currentIndex < allEps.size - 1) allEps.getOrNull(currentIndex + 1) else null

                            item(key = "episode_switcher_bar") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (prevEp != null) {
                                        Surface(
                                            onClick = { onEpisodeClick(prevEp.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                            modifier = Modifier.weight(1f).height(46.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                            ) {
                                                Icon(
                                                    imageVector = BgmIcons.KeyboardArrowLeft,
                                                    contentDescription = "上一集",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "上一集",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = "第 ${prevEp.formattedNumber} 话",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    if (nextEp != null) {
                                        Surface(
                                            onClick = { onEpisodeClick(nextEp.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                            modifier = Modifier.weight(1f).height(46.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.End,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(
                                                        text = "下一集",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = "第 ${nextEp.formattedNumber} 话",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = BgmIcons.KeyboardArrowRight,
                                                    contentDescription = "下一集",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // 3. 剧情梗概（自适应折叠卡片：短梗概完整展示，长梗概优雅折叠）
                    if (episode != null && episode.desc.isNotBlank()) {
                        item(key = "episode_desc") {
                            val descText = remember(episode.desc) { episode.desc.trim() }
                            val isLongDesc =
                                remember(descText) {
                                    descText.length > 100 || descText.count { it == '\n' } >= 3
                                }
                            var isDescExpanded by rememberSaveable(episode.id) { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isLongDesc) { isDescExpanded = !isDescExpanded },
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(3.dp, 12.dp)
                                                        .clip(RoundedCornerShape(1.5.dp))
                                                        .background(MaterialTheme.colorScheme.primary),
                                            )
                                            Text(
                                                text = "剧情梗概",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        if (isLongDesc) {
                                            val arrowRotation by animateFloatAsState(
                                                targetValue = if (isDescExpanded) 180f else 0f,
                                                label = "desc_expand_arrow",
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                ) {
                                                    Text(
                                                        text = if (isDescExpanded) "收起" else "展开",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Icon(
                                                        imageVector = BgmIcons.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier =
                                                            Modifier
                                                                .size(14.dp)
                                                                .graphicsLayer(rotationZ = arrowRotation),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = descText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (isDescExpanded || !isLongDesc) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.animateContentSize(),
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
