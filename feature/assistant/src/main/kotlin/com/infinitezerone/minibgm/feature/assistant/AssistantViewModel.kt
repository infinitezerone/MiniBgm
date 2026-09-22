package com.infinitezerone.minibgm.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.ai.AiToolActivity
import com.infinitezerone.minibgm.core.ai.BgmAiAgentService
import com.infinitezerone.minibgm.core.ai.PendingActionParser
import com.infinitezerone.minibgm.core.ai.PlayableSourcesParser
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.AssistantRepository
import com.infinitezerone.minibgm.core.data.repository.SettingsRepository
import com.infinitezerone.minibgm.core.data.repository.WebViewResolveRepository
import com.infinitezerone.minibgm.core.model.ActionCardStatus
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import com.infinitezerone.minibgm.core.model.PendingActionCard
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 找源工具"无结果"回复的稳定前缀格式：从中提取条目 id 供 WebView 深度解析入口使用 */
private val DEEP_RESOLVE_SUBJECT_ID = Regex("""^No playable source found for subject ID (\d+)""")

/** 新会话的默认标题；首条用户消息发出后自动改为提问摘要 */
private const val NEW_SESSION_TITLE = "新会话"

/** 会话标题取首条用户消息的前 N 个字符 */
private const val SESSION_TITLE_MAX_LENGTH = 20

class AssistantViewModel(
    private val agentService: BgmAiAgentService,
    private val settingsRepository: SettingsRepository,
    private val failureStore: PlaybackFailureStore? = null,
    private val webviewResolveRepository: WebViewResolveRepository? = null,
    private val assistantRepository: AssistantRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _events = Channel<AssistantUiEvent>(Channel.BUFFERED)
    val events: Flow<AssistantUiEvent> = _events.receiveAsFlow()

    private var prefillConsumed = false

    /** 当前进行中的智能体运行；null 或已完成表示空闲。停止 = 取消这个 Job */
    private var runJob: Job? = null

    /** 当前激活的会话 id；消息流按它切换数据源 */
    private val activeSessionId = MutableStateFlow("")

    init {
        viewModelScope.launch {
            settingsRepository.aiConfig.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiConfigProfiles.collect { profiles ->
                _uiState.update { it.copy(aiProfiles = profiles) }
            }
        }
        viewModelScope.launch {
            settingsRepository.activeAiProfileId.collect { profileId ->
                _uiState.update { it.copy(activeProfileId = profileId) }
            }
        }
        viewModelScope.launch {
            AiToolActivity.events.collect { activityEvents ->
                _uiState.update { state ->
                    state.copy(
                        toolActivity = activityEvents.lastOrNull()?.text,
                        activityEvents = activityEvents.map { it.text },
                    )
                }
            }
        }
        failureStore?.recentFailures?.let { flow ->
            viewModelScope.launch {
                flow.collect { failures ->
                    _uiState.update { it.copy(failedSources = failures) }
                }
            }
        }
        assistantRepository?.let { repo ->
            viewModelScope.launch {
                // 会话列表驱动激活 id：空列表自动建首个会话；激活会话被删时自愈到最近一个
                repo.getSessions().collectLatest { sessions ->
                    _uiState.update { it.copy(sessions = sessions) }
                    val current = sessions.firstOrNull { it.id == activeSessionId.value }
                    if (current == null) {
                        val fallbackId =
                            sessions.firstOrNull()?.id
                                ?: repo.createSession(NEW_SESSION_TITLE)
                        activeSessionId.value = fallbackId
                    }
                    _uiState.update { it.copy(activeSessionId = activeSessionId.value) }
                }
            }
            viewModelScope.launch {
                activeSessionId.collectLatest { sessionId ->
                    if (sessionId.isBlank()) return@collectLatest
                    repo.getMessages(sessionId).collect { domainMessages ->
                        _uiState.update { state ->
                            state.copy(messages = domainMessages.map { it.toUiModel() })
                        }
                    }
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** 路由预填提问只消费一次，避免重组或返回该页时重复发起智能体调用 */
    fun sendPrefilledPrompt(prompt: String) {
        if (prefillConsumed) return
        if (prompt.isBlank()) return
        prefillConsumed = true
        sendMessage(prompt)
    }

    fun sendMessage(prompt: String = _uiState.value.inputText) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        val history =
            _uiState.value.messages
                .filter { !it.isError && it.content.isNotBlank() }
                .takeLast(6)
                .map { msg ->
                    val role = if (msg.role == MessageRole.USER) "user" else "assistant"
                    role to msg.content
                }

        val userMessage =
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = trimmed,
            )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                inputText = "",
                isLoading = true,
            )
        }
        viewModelScope.launch {
            val sessionId = ensureActiveSession() ?: return@launch
            // 会话首条用户消息自动命名（后续提问不再覆盖标题）。
            // sessions 可能尚未同步到 state（新建会话后的首次提问），此时同样命名——
            // isLoading 保证同一会话内不会并发发消息，不存在误改名竞态
            val session = _uiState.value.sessions.firstOrNull { it.id == sessionId }
            if (session == null || session.title.isBlank() || session.title == NEW_SESSION_TITLE) {
                assistantRepository?.renameSession(sessionId, trimmed.take(SESSION_TITLE_MAX_LENGTH))
            }
            assistantRepository?.saveMessage(sessionId, userMessage.toDomainModel())
        }

        runAgent(trimmed, history)
    }

    /** 激活会话 id；为空时以仓库现有最近会话兜底，仅在仓库也没有会话时才新建 */
    private suspend fun ensureActiveSession(): String? {
        val repo = assistantRepository ?: return null
        var id = activeSessionId.value
        if (id.isBlank()) {
            val sessions = repo.getSessions().first()
            id = sessions.firstOrNull()?.id ?: repo.createSession(NEW_SESSION_TITLE)
            activeSessionId.value = id
            _uiState.update { it.copy(activeSessionId = id) }
        }
        return id
    }

    /** 把消息持久化到当前激活会话 */
    private fun saveToActiveSession(message: AssistantMessage) {
        val sessionId = activeSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            assistantRepository?.saveMessage(sessionId, message.toDomainModel())
        }
    }

    /** 切换会话：运行中禁止切换（避免回复落到别的会话） */
    fun switchSession(sessionId: String) {
        if (_uiState.value.isLoading) return
        if (sessionId == activeSessionId.value) {
            _uiState.update { it.copy(showSessionSwitcher = false) }
            return
        }
        activeSessionId.value = sessionId
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                showSessionSwitcher = false,
                deepResolve = null,
            )
        }
    }

    /**
     * 新建会话：当前会话没有任何消息时不新建（避免堆积一堆空会话），只收起面板。
     */
    fun createNewSession() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(showSessionSwitcher = false) }
        if (_uiState.value.messages.isNotEmpty()) {
            viewModelScope.launch {
                val newId = assistantRepository?.createSession(NEW_SESSION_TITLE) ?: return@launch
                switchSession(newId)
            }
        }
    }

    /** 重命名会话：空标题忽略；改名后首条提问的自动命名不再覆盖（仅针对"新会话"默认名） */
    fun renameSession(
        sessionId: String,
        title: String,
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            assistantRepository?.renameSession(sessionId, trimmed)
        }
    }

    /** 删除会话及其消息；删的是激活会话时，会话收集器自愈到剩余最近会话（全空则新建） */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            assistantRepository?.deleteSession(sessionId)
            _uiState.update { it.copy(showSessionSwitcher = false) }
        }
    }

    fun toggleSessionSwitcher(show: Boolean) {
        _uiState.update { it.copy(showSessionSwitcher = show) }
    }

    /**
     * 停止当前进行中的智能体运行：取消执行任务、清空工具活动，并在会话里留一条已停止的说明。
     * 空闲时调用是 no-op。
     */
    fun stopGeneration() {
        val job = runJob?.takeIf { it.isActive } ?: return
        job.cancel()
        runJob = null
        AiToolActivity.clear()
        val stoppedMessage =
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "⏹ 已停止生成。",
            )
        _uiState.update { it.copy(messages = it.messages + stoppedMessage, isLoading = false) }
        saveToActiveSession(stoppedMessage)
    }

    /**
     * 对某条错误回复重试：取它之前最近一条用户输入原样重发。
     * 不追加新的用户气泡（那条输入已经在会话里），仅重新唤起智能体。
     */
    fun retryAfterError(errorId: String) {
        if (_uiState.value.isLoading) return
        val messages = _uiState.value.messages
        val errorIndex = messages.indexOfFirst { it.id == errorId }
        if (errorIndex <= 0) return
        if (!messages[errorIndex].isError) return
        val prompt =
            messages
                .take(errorIndex)
                .lastOrNull { it.role == MessageRole.USER }
                ?.content
                ?: return

        val history =
            messages
                .take(errorIndex)
                .filter { !it.isError && it.content.isNotBlank() }
                .takeLast(6)
                .map { msg ->
                    val role = if (msg.role == MessageRole.USER) "user" else "assistant"
                    role to msg.content
                }

        _uiState.update { it.copy(isLoading = true) }
        runAgent(prompt, history, retriedErrorId = errorId)
    }

    private fun runAgent(
        prompt: String,
        history: List<Pair<String, String>>,
        retriedErrorId: String? = null,
    ) {
        val sessionId = activeSessionId.value
        runJob =
            viewModelScope.launch {
                when (val result = agentService.execute(prompt, history)) {
                    is AppResult.Success -> {
                        // 重试成功：被重试的错误气泡已完成使命，从会话与持久化里一并移除
                        if (retriedErrorId != null) {
                            _uiState.update { state ->
                                state.copy(messages = state.messages.filterNot { it.id == retriedErrorId })
                            }
                            viewModelScope.launch {
                                if (sessionId.isNotBlank()) {
                                    assistantRepository?.deleteMessage(sessionId, retriedErrorId)
                                }
                            }
                        }
                        val rawContent = result.data
                        val storeActions = agentService.pendingActionStore?.popAll().orEmpty()
                        val parsedActions = PendingActionParser.extractPendingActions(rawContent)
                        val combinedActions = (storeActions + parsedActions).distinctBy { it.actionId }

                        val actionCardStates =
                            combinedActions.map {
                                PendingActionCardState(action = it, status = ActionStatus.PENDING)
                            }

                        val playableSources = PlayableSourcesParser.extract(rawContent)

                        val displayContent =
                            when {
                                playableSources != null -> {
                                    val source = playableSources.source
                                    val suffix = if (source.isBlank()) "" else "（来源：$source）"
                                    "为你找到 ${playableSources.episodes.size} 条可播放来源$suffix："
                                }
                                rawContent.trim().startsWith("{") && combinedActions.isNotEmpty() -> {
                                    "已为您生成待确认操作提案，请确认是否提交同步至 Bangumi："
                                }
                                else -> rawContent
                            }

                        val assistantMessage =
                            AssistantMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = displayContent,
                                pendingActions = actionCardStates,
                                playableSources = playableSources,
                            )

                        val deepResolve =
                            if (playableSources == null && webviewResolveRepository != null) {
                                deepResolveSubjectId(rawContent)?.let { DeepResolveState(subjectId = it) }
                            } else {
                                null
                            }
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + assistantMessage,
                                isLoading = false,
                                deepResolve = deepResolve,
                            )
                        }
                        viewModelScope.launch {
                            saveToActiveSession(assistantMessage)
                        }
                    }
                    is AppResult.Error -> {
                        val errorMsg =
                            result.message.ifBlank {
                                result.throwable.message ?: "智能体执行失败"
                            }
                        val assistantMessage =
                            AssistantMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "❌ 执行出错：$errorMsg",
                                isError = true,
                            )
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + assistantMessage,
                                isLoading = false,
                            )
                        }
                        viewModelScope.launch {
                            saveToActiveSession(assistantMessage)
                        }
                    }
                    is AppResult.Loading -> Unit
                }
            }
    }

    fun approveAction(actionId: String) {
        val currentCard =
            _uiState.value.messages
                .flatMap { it.pendingActions }
                .firstOrNull { it.action.actionId == actionId }
                ?: return

        // 仅允许对等待确认 (PENDING) 或执行失败 (FAILED) 状态的卡片执行提交/重试
        if (currentCard.status != ActionStatus.PENDING && currentCard.status != ActionStatus.FAILED) {
            return
        }

        val executor = agentService.pendingActionExecutor
        if (executor == null) {
            viewModelScope.launch {
                _events.send(AssistantUiEvent.ShowSnackbar("执行器未配置"))
            }
            return
        }

        val action = currentCard.action

        _uiState.update { state ->
            val updatedMessages =
                state.messages.map { msg ->
                    val updatedCards =
                        msg.pendingActions.map { card ->
                            if (card.action.actionId == actionId) {
                                card.copy(status = ActionStatus.EXECUTING, errorMessage = null)
                            } else {
                                card
                            }
                        }
                    msg.copy(pendingActions = updatedCards)
                }
            state.copy(messages = updatedMessages)
        }

        viewModelScope.launch {
            when (val result = executor.execute(action)) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        val updatedMessages =
                            state.messages.map { msg ->
                                val updatedCards =
                                    msg.pendingActions.map { card ->
                                        if (card.action.actionId == actionId) {
                                            card.copy(status = ActionStatus.SUCCESS)
                                        } else {
                                            card
                                        }
                                    }
                                msg.copy(pendingActions = updatedCards)
                            }
                        state.copy(messages = updatedMessages)
                    }
                    agentService.pendingActionStore?.remove(actionId)
                    _events.send(AssistantUiEvent.ShowSnackbar("操作已成功执行并同步至 Bangumi！"))
                    _uiState.value.messages
                        .firstOrNull { msg -> msg.pendingActions.any { it.action.actionId == actionId } }
                        ?.let { msg ->
                            viewModelScope.launch {
                                saveToActiveSession(msg)
                            }
                        }
                }
                is AppResult.Error -> {
                    val err = result.throwable.message ?: "执行操作失败"
                    _uiState.update { state ->
                        val updatedMessages =
                            state.messages.map { msg ->
                                val updatedCards =
                                    msg.pendingActions.map { card ->
                                        if (card.action.actionId == actionId) {
                                            card.copy(status = ActionStatus.FAILED, errorMessage = err)
                                        } else {
                                            card
                                        }
                                    }
                                msg.copy(pendingActions = updatedCards)
                            }
                        state.copy(messages = updatedMessages)
                    }
                    _events.send(AssistantUiEvent.ShowSnackbar("执行失败: $err"))
                    _uiState.value.messages
                        .firstOrNull { msg -> msg.pendingActions.any { it.action.actionId == actionId } }
                        ?.let { msg ->
                            viewModelScope.launch {
                                saveToActiveSession(msg)
                            }
                        }
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun rejectAction(actionId: String) {
        val currentCard =
            _uiState.value.messages
                .flatMap { it.pendingActions }
                .firstOrNull { it.action.actionId == actionId }
                ?: return

        // 仅允许取消等待确认或失败重试状态的提案，禁止反转已执行成功的操作
        if (currentCard.status != ActionStatus.PENDING && currentCard.status != ActionStatus.FAILED) {
            return
        }

        _uiState.update { state ->
            val updatedMessages =
                state.messages.map { msg ->
                    val updatedCards =
                        msg.pendingActions.map { card ->
                            if (card.action.actionId == actionId) {
                                card.copy(status = ActionStatus.REJECTED)
                            } else {
                                card
                            }
                        }
                    msg.copy(pendingActions = updatedCards)
                }
            state.copy(messages = updatedMessages)
        }
        agentService.pendingActionStore?.remove(actionId)
        _uiState.value.messages
            .firstOrNull { msg -> msg.pendingActions.any { it.action.actionId == actionId } }
            ?.let { msg ->
                viewModelScope.launch {
                    saveToActiveSession(msg)
                }
            }
        viewModelScope.launch {
            _events.send(AssistantUiEvent.ShowSnackbar("已取消操作提案"))
        }
    }

    fun clearConversation() {
        _uiState.update { it.copy(messages = emptyList()) }
        agentService.pendingActionStore?.clear()
        val sessionId = activeSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            assistantRepository?.clearMessages(sessionId)
        }
    }

    fun toggleConfigDialog(show: Boolean) {
        _uiState.update { it.copy(showConfigDialog = show) }
    }

    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch {
            settingsRepository.setAiConfig(config)
            _uiState.update { it.copy(showConfigDialog = false) }
            _events.send(AssistantUiEvent.ShowSnackbar("AI 配置已更新"))
        }
    }

    /** 保存当前表单为命名方案：profileId 非空 = 覆盖更新该方案，null = 新建 */
    fun saveAiProfile(
        profileId: String?,
        name: String,
        config: AiConfig,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            val id = profileId ?: UUID.randomUUID().toString()
            settingsRepository.saveAiConfigProfile(
                com.infinitezerone.minibgm.core.model.AiConfigProfile(
                    id = id,
                    name = trimmedName,
                    config = config,
                ),
            )
            settingsRepository.activateAiConfigProfile(id)
            _events.send(AssistantUiEvent.ShowSnackbar("方案「$trimmedName」已保存并启用"))
        }
    }

    /** 启用已保存方案：配置写回当前生效值，后续对话即用该端点/密钥 */
    fun activateAiProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepository.activateAiConfigProfile(profileId)
            val name =
                _uiState.value.aiProfiles
                    .firstOrNull { it.id == profileId }
                    ?.name
                    ?: return@launch
            _events.send(AssistantUiEvent.ShowSnackbar("已启用方案「$name」"))
        }
    }

    fun deleteAiProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepository.deleteAiConfigProfile(profileId)
            _events.send(AssistantUiEvent.ShowSnackbar("方案已删除"))
        }
    }

    suspend fun fetchAvailableModels(
        endpoint: String,
        apiKey: String,
        provider: String,
    ): AppResult<List<String>> = agentService.fetchAvailableModels(endpoint, apiKey, provider)

    /**
     * 用户显式触发的 WebView 深度解析（第 5 档）：对找源工具报告无结果的条目，
     * 用确定性 WebView 会话加载候选来源页并捕获媒体请求；结果以可播放清单卡片追加进会话。
     */
    fun runDeepResolve() {
        val state = _uiState.value.deepResolve ?: return
        if (state.isRunning) return
        _uiState.update { it.copy(deepResolve = state.copy(isRunning = true)) }
        viewModelScope.launch {
            val result = webviewResolveRepository?.deepResolve(state.subjectId)
            _uiState.update { it.copy(deepResolve = it.deepResolve?.copy(isRunning = false)) }
            when (result) {
                is AppResult.Success -> {
                    val message =
                        if (result.data.isEmpty()) {
                            AssistantMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "WebView 深度解析也没有捕获到可播放的媒体请求。",
                            )
                        } else {
                            AssistantMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "WebView 深度解析找到 ${result.data.size} 条可播放来源：",
                                playableSources =
                                    PlayableEpisodeList(
                                        subjectId = state.subjectId,
                                        source = "WebView 深度解析",
                                        episodes = result.data,
                                    ),
                            )
                        }
                    _uiState.update { it.copy(messages = it.messages + message) }
                    viewModelScope.launch {
                        saveToActiveSession(message)
                    }
                }
                is AppResult.Error -> {
                    val errorMessage =
                        AssistantMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = "❌ WebView 深度解析失败：${result.message}",
                            isError = true,
                        )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errorMessage,
                        )
                    }
                    viewModelScope.launch {
                        saveToActiveSession(errorMessage)
                    }
                }
                is AppResult.Loading -> Unit
                null -> Unit
            }
        }
    }

    private fun deepResolveSubjectId(rawContent: String): Long? {
        val match = DEEP_RESOLVE_SUBJECT_ID.find(rawContent.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()?.takeIf { it > 0L }
    }
}

private fun AssistantChatMessage.toUiModel(): AssistantMessage =
    AssistantMessage(
        id = id,
        role =
            when (role) {
                ChatMessageRole.USER -> MessageRole.USER
                ChatMessageRole.ASSISTANT -> MessageRole.ASSISTANT
            },
        content = content,
        timestamp = timestamp,
        pendingActions =
            pendingActions.map { card ->
                PendingActionCardState(
                    action = card.action,
                    status =
                        when (card.status) {
                            ActionCardStatus.PENDING -> ActionStatus.PENDING
                            ActionCardStatus.EXECUTING -> ActionStatus.EXECUTING
                            ActionCardStatus.SUCCESS -> ActionStatus.SUCCESS
                            ActionCardStatus.REJECTED -> ActionStatus.REJECTED
                            ActionCardStatus.FAILED -> ActionStatus.FAILED
                        },
                    errorMessage = card.errorMessage,
                )
            },
        playableSources = playableSources,
        isError = isError,
    )

private fun AssistantMessage.toDomainModel(): AssistantChatMessage =
    AssistantChatMessage(
        id = id,
        role =
            when (role) {
                MessageRole.USER -> ChatMessageRole.USER
                MessageRole.ASSISTANT -> ChatMessageRole.ASSISTANT
            },
        content = content,
        timestamp = timestamp,
        pendingActions =
            pendingActions.map { card ->
                PendingActionCard(
                    action = card.action,
                    status =
                        when (card.status) {
                            ActionStatus.PENDING -> ActionCardStatus.PENDING
                            ActionStatus.EXECUTING -> ActionCardStatus.EXECUTING
                            ActionStatus.SUCCESS -> ActionCardStatus.SUCCESS
                            ActionStatus.REJECTED -> ActionCardStatus.REJECTED
                            ActionStatus.FAILED -> ActionCardStatus.FAILED
                        },
                    errorMessage = card.errorMessage,
                )
            },
        playableSources = playableSources,
        isError = isError,
    )
