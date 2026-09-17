package com.infinitezerone.minibgm.feature.subject

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.components.TopicMainPostCard
import com.infinitezerone.minibgm.feature.subject.components.TopicReplyCard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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

    val topic = uiState.topicDetail
    val displayTitle = topic?.title ?: initialTitle.ifBlank { "讨论详情" }
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

    Scaffold(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { context.launchWebUrl(topicWebUrl) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = "在浏览器中打开",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
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
                        text = "发表回帖请前往 Bangumi 网页版",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { context.launchWebUrl(topicWebUrl) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(text = "前往讨论", style = MaterialTheme.typography.labelMedium)
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
                                text = uiState.error ?: "加载讨论帖失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { viewModel.refresh(isUserPullToRefresh = false) }) {
                                Text(text = "重新加载")
                            }
                        }
                    }
                }
                topic != null -> {
                    val floorReplies = topic.floorReplies
                    LazyColumn(
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
                            )
                        }

                        // 回复分割线与回复计数
                        item(key = "replies_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                                Text(
                                    text = if (floorReplies.isNotEmpty()) "全部回复 (${floorReplies.size})" else "暂无回帖",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // 2 楼及后续楼层回帖列表
                        itemsIndexed(
                            items = floorReplies,
                            key = { _, reply -> reply.id },
                        ) { index, reply ->
                            TopicReplyCard(
                                reply = reply,
                                floorNumber = index + 2,
                                onUrlClick = handleLinkClick,
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
