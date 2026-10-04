package com.infinitezerone.minibgm.feature.assistant

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.OverlayRequest
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.AssistantSession
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.assistant.components.PendingActionCard
import com.infinitezerone.minibgm.feature.assistant.components.PlayableSourcesCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val PROMPT_SUGGESTIONS =
    listOf(
        "今天有哪些动画更新？",
        "查看我正在追看的番剧",
        "推荐一部本季高分动画",
        "把《葬送的芙莉莲》第12集标记为已看",
    )

/** 会话切换底部面板：列出全部会话（最近更新在前），支持切换、新建、重命名与删除 */
private data class RenameSessionRequest(
    val currentTitle: String,
) : OverlayRequest<String?>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionSwitcherSheet(
    sessions: List<AssistantSession>,
    activeSessionId: String,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val overlayHostState = rememberOverlayHostState()
    val coroutineScope = rememberCoroutineScope()
    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "会话记录",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCreate, enabled = true) {
                    Icon(
                        imageVector = BgmIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新会话")
                }
            }
            HorizontalDivider()
            if (sessions.isEmpty()) {
                Text(
                    text = "暂无历史会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            sessions.forEach { session ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSwitch(session.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (session.id == activeSessionId) BgmIcons.ChatBubble else BgmIcons.ChatBubbleBorder,
                        contentDescription = null,
                        tint =
                            if (session.id == activeSessionId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (session.id == activeSessionId) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = TimeUtils.formatShortDateTimeEpochMillis(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val newTitle = overlayHostState.await(RenameSessionRequest(session.title))
                            if (!newTitle.isNullOrBlank()) {
                                onRename(session.id, newTitle)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = BgmIcons.EditBorder,
                            contentDescription = "重命名会话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val confirmed =
                                overlayHostState.await(
                                    ConfirmDialogAction(
                                        title = "删除会话",
                                        message = "确定要删除会话「${session.title}」及其所有记录吗？删除后无法恢复。",
                                        confirmText = "删除",
                                        isDestructive = true,
                                    ),
                                )
                            if (confirmed) {
                                onDelete(session.id)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = BgmIcons.DeleteBorder,
                            contentDescription = "删除会话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        BgmOverlayHost(hostState = overlayHostState) {
            confirmDialog()
            overlay<RenameSessionRequest, String?> { request, onRespond ->
                var text by remember { mutableStateOf(request.currentTitle) }
                AlertDialog(
                    onDismissRequest = { onRespond(null) },
                    title = { Text("重命名会话") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { onRespond(text.trim()) },
                            enabled = text.trim().isNotBlank(),
                        ) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onRespond(null) }) {
                            Text("取消")
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun EmptyAssistantGuide(
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
                imageVector = BgmIcons.AssistantBorder,
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
internal fun ChatMessageItem(
    message: AssistantMessage,
    failedSources: Map<String, String>,
    showRetry: Boolean,
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onPlaySource: (PlayerRoute) -> Unit,
    onRetryMessage: (String) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER
    val parsedContent = remember(message.content) { parseThinkingProcess(message.content) }
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // 推理模型思考过程可折叠卡片
        if (!isUser && parsedContent.thinking != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier =
                    Modifier
                        .widthIn(max = 320.dp)
                        .padding(bottom = 6.dp)
                        .clickable { isThinkingExpanded = !isThinkingExpanded },
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "思考过程",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isThinkingExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = if (isThinkingExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    AnimatedVisibility(visible = isThinkingExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = parsedContent.thinking,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }

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
                val displayContent =
                    if (parsedContent.thinking != null && parsedContent.mainContent.isBlank()) {
                        "（已完成思考，请展开上方查看）"
                    } else {
                        parsedContent.mainContent
                    }
                LinkifiedMessageText(
                    content = displayContent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // 助手消息辅助操作（一键复制）与错误回复一键重试
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (!isUser && !message.isError && message.content.isNotBlank()) {
                val clipboard = LocalClipboard.current
                val coroutineScope = rememberCoroutineScope()
                var copied by remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        val textToCopy = parsedContent.mainContent.ifBlank { message.content }
                        coroutineScope.launch {
                            val clipEntry = ClipEntry(ClipData.newPlainText("assistant_message", textToCopy))
                            clipboard.setClipEntry(clipEntry)
                        }
                        copied = true
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (copied) BgmIcons.Check else BgmIcons.ContentCopy,
                        contentDescription = "复制回答",
                        tint =
                            if (copied) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (showRetry) {
                TextButton(
                    onClick = { onRetryMessage(message.id) },
                ) {
                    Text("重试", style = MaterialTheme.typography.labelLarge)
                }
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
internal fun DeepResolveEntry(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
internal fun AssistantLoadingBubble(
    toolActivity: String? = null,
    activityStepCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    // 本次运行已等待的秒数：多轮工具调用（每轮 12~30s）期间给用户可感知的时间进度
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

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
                Column {
                    AnimatedContent(
                        targetState = toolActivity ?: "AI 正在思考并检索...",
                        label = "ToolActivityTransition",
                    ) { targetText ->
                        Text(
                            text = targetText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text =
                            buildString {
                                append("已思考 ${elapsedSeconds.coerceAtLeast(0)} 秒")
                                if (activityStepCount > 0) {
                                    append(" · 第 $activityStepCount 步")
                                }
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** 助手消息支持完整 Markdown 富文本渲染与可点击超链接 */
@Composable
internal fun LinkifiedMessageText(
    content: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    com.infinitezerone.minibgm.feature.assistant.components.AssistantMarkdownText(
        content = content,
        style = style,
        modifier = modifier,
    )
}
