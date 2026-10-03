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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.designsystem.component.BgmSnackbarHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import com.infinitezerone.minibgm.feature.assistant.components.AssistantConfigDialog
import org.koin.androidx.compose.koinViewModel

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
        onStopGeneration = viewModel::stopGeneration,
        onApproveAction = viewModel::approveAction,
        onRejectAction = viewModel::rejectAction,
        onRetryMessage = viewModel::retryAfterError,
        onActivateProfile = viewModel::activateAiProfile,
        onSaveProfile = viewModel::saveAiProfile,
        onDeleteProfile = viewModel::deleteAiProfile,
        onToggleSessionSwitcher = viewModel::toggleSessionSwitcher,
        onSwitchSession = viewModel::switchSession,
        onCreateSession = viewModel::createNewSession,
        onDeleteSession = viewModel::deleteSession,
        onRenameSession = viewModel::renameSession,
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
    onStopGeneration: () -> Unit = {},
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onRetryMessage: (String) -> Unit = { },
    onActivateProfile: (String) -> Unit = {},
    onSaveProfile: (profileId: String?, name: String, config: AiConfig) -> Unit = { _, _, _ -> },
    onDeleteProfile: (String) -> Unit = {},
    onToggleSessionSwitcher: (Boolean) -> Unit = {},
    onSwitchSession: (String) -> Unit = {},
    onCreateSession: () -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
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

    var showDeleteCurrentSessionDialog by remember { mutableStateOf(false) }

    if (showDeleteCurrentSessionDialog) {
        val activeTitle =
            uiState.sessions
                .firstOrNull { it.id == uiState.activeSessionId }
                ?.title
                ?.ifBlank { "当前会话" } ?: "当前会话"
        AlertDialog(
            onDismissRequest = { showDeleteCurrentSessionDialog = false },
            title = { Text("删除会话") },
            text = { Text("确定要删除会话「$activeTitle」及其所有聊天记录吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCurrentSessionDialog = false
                        onDeleteSession(uiState.activeSessionId)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCurrentSessionDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            BgmTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = BgmIcons.Assistant,
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
                                imageVector = BgmIcons.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleSessionSwitcher(true) }) {
                        Icon(
                            imageVector = BgmIcons.ChatBubble,
                            contentDescription = "会话列表",
                        )
                    }
                    IconButton(onClick = { onToggleConfigDialog(true) }) {
                        Icon(
                            imageVector = BgmIcons.Settings,
                            contentDescription = "AI 设置",
                        )
                    }
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { showDeleteCurrentSessionDialog = true }) {
                            Icon(
                                imageVector = BgmIcons.DeleteBorder,
                                contentDescription = "删除当前会话",
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
                    shape = RoundedCornerShape(24.dp),
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

                        if (uiState.isLoading) {
                            // 运行中：发送位变成停止按钮——端点挂住时用户不必干等超时
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onStopGeneration()
                                },
                                modifier =
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.Stop,
                                    contentDescription = "停止生成",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            val canSend = uiState.inputText.isNotBlank()
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
                                    imageVector = BgmIcons.Send,
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
                    // 重试入口只挂在"最后一条且之后没有成功回复"的错误上：
                    // 已有成功回答的问题不值得重试，重试成功后错误气泡会被整体移除
                    val lastErrorId = uiState.messages.lastOrNull { it.isError }?.id
                    val successAfterLastError =
                        lastErrorId?.let { id ->
                            val errorIndex = uiState.messages.indexOfFirst { it.id == id }
                            uiState.messages
                                .drop(errorIndex + 1)
                                .any { it.role == MessageRole.ASSISTANT && !it.isError }
                        } ?: false
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            failedSources = uiState.failedSources,
                            showRetry = message.isError && message.id == lastErrorId && !successAfterLastError,
                            onApproveAction = onApproveAction,
                            onRejectAction = onRejectAction,
                            onSubjectClick = onSubjectClick,
                            onPlaySource = onPlaySource,
                            onRetryMessage = onRetryMessage,
                        )
                    }

                    if (uiState.isLoading) {
                        item(key = "loading_indicator") {
                            AssistantLoadingBubble(
                                toolActivity = uiState.toolActivity,
                                activityStepCount = uiState.activityEvents.size,
                            )
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
            aiProfiles = uiState.aiProfiles,
            activeProfileId = uiState.activeProfileId,
            onSaveConfig = onSaveConfig,
            onActivateProfile = onActivateProfile,
            onSaveProfile = onSaveProfile,
            onDeleteProfile = onDeleteProfile,
            onDismiss = { onToggleConfigDialog(false) },
            onFetchModels = onFetchModelsAsync,
        )
    }

    if (uiState.showSessionSwitcher) {
        SessionSwitcherSheet(
            sessions = uiState.sessions,
            activeSessionId = uiState.activeSessionId,
            onSwitch = onSwitchSession,
            onCreate = onCreateSession,
            onDelete = onDeleteSession,
            onRename = onRenameSession,
            onDismiss = { onToggleSessionSwitcher(false) },
        )
    }
}
