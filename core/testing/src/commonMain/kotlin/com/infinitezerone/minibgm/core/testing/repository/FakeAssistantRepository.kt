package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.repository.AssistantRepository
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.AssistantSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** 内存版多会话仓库：sessions + 按 sessionId 归组的消息，语义与 Room 实现一致 */
class FakeAssistantRepository : AssistantRepository {
    private val sessionsState =
        MutableStateFlow<List<AssistantSession>>(
            listOf(
                AssistantSession(id = DEFAULT_SESSION_ID, title = "新会话", createdAt = 0L, updatedAt = 0L),
            ),
        )
    private val messagesBySession = MutableStateFlow<Map<String, List<AssistantChatMessage>>>(emptyMap())

    override fun getSessions(): Flow<List<AssistantSession>> =
        sessionsState.asStateFlow().map { sessions -> sessions.sortedByDescending { it.updatedAt } }

    override suspend fun createSession(title: String): String {
        val id = "fake-session-${currentTimeMs++}"
        val now = currentTimeMs++
        sessionsState.value = sessionsState.value + AssistantSession(id = id, title = title, createdAt = now, updatedAt = now)
        return id
    }

    override suspend fun renameSession(
        sessionId: String,
        title: String,
    ) {
        if (title.isBlank()) return
        sessionsState.value =
            sessionsState.value.map {
                if (it.id == sessionId) it.copy(title = title.trim()) else it
            }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionsState.value = sessionsState.value.filterNot { it.id == sessionId }
        messagesBySession.value = messagesBySession.value - sessionId
    }

    override fun getMessages(sessionId: String): Flow<List<AssistantChatMessage>> =
        messagesBySession.asStateFlow().map { bySession -> bySession[sessionId].orEmpty() }

    override suspend fun saveMessage(
        sessionId: String,
        message: AssistantChatMessage,
    ) {
        val current = messagesBySession.value[sessionId].orEmpty()
        val index = current.indexOfFirst { it.id == message.id }
        val updated =
            if (index >= 0) {
                current.toMutableList().also { it[index] = message }
            } else {
                current + message
            }
        messagesBySession.value = messagesBySession.value + (sessionId to updated)
        sessionsState.value =
            sessionsState.value.map {
                if (it.id == sessionId) it.copy(updatedAt = currentTimeMs++) else it
            }
    }

    override suspend fun deleteMessage(
        sessionId: String,
        id: String,
    ) {
        val current = messagesBySession.value[sessionId].orEmpty()
        messagesBySession.value = messagesBySession.value + (sessionId to current.filterNot { it.id == id })
    }

    override suspend fun clearMessages(sessionId: String) {
        messagesBySession.value = messagesBySession.value + (sessionId to emptyList())
    }

    /** 测试辅助：直接注入某会话的消息列表 */
    fun setMessages(
        messages: List<AssistantChatMessage>,
        sessionId: String = DEFAULT_SESSION_ID,
    ) {
        messagesBySession.value = messagesBySession.value + (sessionId to messages)
    }

    var currentTimeMs: Long = 1L

    companion object {
        const val DEFAULT_SESSION_ID = "fake-default"
    }
}
