package com.infinitezerone.minibgm.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.assistant.components.AssistantConfigDialog
import com.infinitezerone.minibgm.feature.assistant.components.PendingActionCard
import com.infinitezerone.minibgm.feature.assistant.components.PlayableSourcesCard
import org.koin.androidx.compose.koinViewModel

private val PROMPT_SUGGESTIONS =
    listOf(
        "📅 今天有哪些动画更新？",
        "📺 查看我正在追看的番剧",
        "🌟 推荐一部本季高分动画",
        "✅ 把《葬送的芙莉莲》第12集标记为已看",
    )

@Composable
fun AssistantScreen(
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    onBackClick: (() -> Unit)? = null,
    prefillPrompt: String = "",
    onPlaySource: (PlayerRoute) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(prefillPrompt) {
        viewModel.sendPrefilledPrompt(prefillPrompt)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is AssistantUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AssistantUiEvent.NavigateToSubject -> {
                    onSubjectClick(SubjectDetailRoute(event.subjectId))
                }
            }
        }
    }

    AssistantScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onInputChanged = viewModel::onInputChanged,
        onSendMessage = { viewModel.sendMessage() },
        onSendPrompt = { prompt -> viewModel.sendMessage(prompt) },
        onApproveAction = viewModel::approveAction,
        onRejectAction = viewModel::rejectAction,
        onSubjectClick = { subjectId -> onSubjectClick(SubjectDetailRoute(subjectId)) },
        onPlaySource = onPlaySource,
        deepResolve = uiState.deepResolve,
        onRunDeepResolve = viewModel::runDeepResolve,
        onClearConversation = viewModel::clearConversation,
        onToggleConfigDialog = viewModel::toggleConfigDialog,
        onSaveConfig = viewModel::saveAiConfig,
        onFetchModelsAsync = viewModel::fetchAvailableModels,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreenContent(
    uiState: AssistantUiState,
    snackbarHostState: SnackbarHostState,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onClearConversation: () -> Unit,
    onToggleConfigDialog: (Boolean) -> Unit,
    onSaveConfig: (AiConfig) -> Unit,
    onBackClick: (() -> Unit)?,
    onPlaySource: (PlayerRoute) -> Unit = {},
    deepResolve: DeepResolveState? = null,
    onRunDeepResolve: () -> Unit = {},
    onFetchModelsAsync: suspend (String, String, String) -> AppResult<List<String>> = { _, _, _ -> AppResult.Success(emptyList()) },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 新消息到达时自动滚动到底部
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        val totalCount = uiState.messages.size + if (uiState.isLoading) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI 追番助手",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleConfigDialog(true) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "AI 设置",
                        )
                    }
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = onClearConversation) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "清空对话",
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                // 底部快捷预设 Prompts 滚动条（仅在有消息时展示辅助操作）
                if (uiState.messages.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(PROMPT_SUGGESTIONS) { prompt ->
                            SuggestionChip(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onSendPrompt(prompt)
                                },
                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
                                enabled = !uiState.isLoading,
                            )
                        }
                    }
                }

                // 悬浮式胶囊输入框（无边框，发送按钮内嵌）
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                focusRequester.requestFocus()
                            },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = uiState.inputText,
                            onValueChange = onInputChanged,
                            placeholder = {
                                Text(
                                    text = "问问 AI 追番助手...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = false,
                            maxLines = 4,
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Send,
                                    capitalization = KeyboardCapitalization.Sentences,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSend = {
                                        val canSend = uiState.inputText.isNotBlank() && !uiState.isLoading
                                        if (canSend) {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onSendMessage()
                                        }
                                    },
                                ),
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                        )

                        val canSend = uiState.inputText.isNotBlank() && !uiState.isLoading
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSendMessage()
                            },
                            enabled = canSend,
                            modifier =
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        },
                                    ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint =
                                    if (canSend) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { BgmSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
        ) {
            if (uiState.messages.isEmpty()) {
                // 空状态引导卡片
                EmptyAssistantGuide(
                    onSelectPrompt = { prompt ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onSendPrompt(prompt)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            failedSources = uiState.failedSources,
                            onApproveAction = onApproveAction,
                            onRejectAction = onRejectAction,
                            onSubjectClick = onSubjectClick,
                            onPlaySource = onPlaySource,
                        )
                    }

                    if (uiState.isLoading) {
                        item(key = "loading_indicator") {
                            AssistantLoadingBubble(toolActivity = uiState.toolActivity)
                        }
                    }

                    if (deepResolve != null) {
                        item(key = "deep_resolve") {
                            DeepResolveEntry(
                                isRunning = deepResolve.isRunning,
                                onClick = onRunDeepResolve,
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showConfigDialog) {
        AssistantConfigDialog(
            currentConfig = uiState.aiConfig,
            onSaveConfig = onSaveConfig,
            onDismiss = { onToggleConfigDialog(false) },
            onFetchModels = onFetchModelsAsync,
        )
    }
}

@Composable
private fun EmptyAssistantGuide(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MiniBgm AI 智能助手",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "支持自然语言查询时刻表、追番进度，打卡分集与管理收藏需经确认后同步，安全无忧。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "可以尝试提问：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PROMPT_SUGGESTIONS.forEach { prompt ->
                SuggestionChip(
                    onClick = { onSelectPrompt(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: AssistantMessage,
    failedSources: Map<String, String>,
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onPlaySource: (PlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape =
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
            color =
                if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            contentColor =
                if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (message.isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                LinkifiedMessageText(
                    content = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // 若该消息携带 HITL 操作提案，在消息气泡下方渲染提案交互卡片
        if (message.pendingActions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(0.95f),
            ) {
                message.pendingActions.forEach { cardState ->
                    PendingActionCard(
                        cardState = cardState,
                        onApprove = onApproveAction,
                        onReject = onRejectAction,
                        onSubjectClick = onSubjectClick,
                    )
                }
            }
        }

        // 找源结果：可播放清单卡片，DIRECT 条目点击即进播放器
        message.playableSources?.let { sources ->
            Spacer(modifier = Modifier.height(8.dp))
            PlayableSourcesCard(
                sources = sources,
                failedReasons = failedSources,
                onPlaySource = onPlaySource,
                modifier = Modifier.fillMaxWidth(0.95f),
            )
        }
    }
}

/** WebView 深度解析入口：仅在找源工具报告无结果时展示，用户显式触发 */
@Composable
private fun DeepResolveEntry(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "常规找源没有找到可播放来源",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (isRunning) "正在真实加载来源页并捕获媒体请求…" else "可用 WebView 深度解析尝试真实加载来源页（约 30 秒）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onClick) { Text("深度解析") }
            }
        }
    }
}

@Composable
private fun AssistantLoadingBubble(
    toolActivity: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = toolActivity ?: "AI 正在思考并检索...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 助手回答里的裸 URL（找源结果就是链接本身，必须可点） */
private val URL_PATTERN = Regex("https?://\\S+")

private val TRAILING_PUNCTUATION = charArrayOf('，', '。', '、', '）', ')', '】', '」', '’', '"', '.', ',')

/** 助手消息按纯文本渲染，但把其中的 URL 变成可点击链接（系统提示词要求一行一个链接） */
@Composable
private fun LinkifiedMessageText(
    content: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated =
        buildAnnotatedString {
            var cursor = 0
            URL_PATTERN.findAll(content).forEach { match ->
                append(content, cursor, match.range.first)
                val url = match.value.trimEnd { it in TRAILING_PUNCTUATION }
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles =
                            TextLinkStyles(
                                style =
                                    SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                            ),
                        linkInteractionListener = { uriHandler.openUri(url) },
                    ),
                ) {
                    append(url)
                }
                cursor = match.range.first + match.value.length
            }
            append(content, cursor, content.length)
        }
    Text(text = annotated, style = style, modifier = modifier)
}
