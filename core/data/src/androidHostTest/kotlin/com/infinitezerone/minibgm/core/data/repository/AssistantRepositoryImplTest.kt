package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAssistantMessageDao : AssistantMessageDao {
    private val messages = MutableStateFlow<List<AssistantMessageEntity>>(emptyList())

    override fun getMessagesBySession(sessionId: String): Flow<List<AssistantMessageEntity>> =
        messages.map { list -> list.filter { it.sessionId == sessionId } }

    override suspend fun insertMessage(message: AssistantMessageEntity) {
        messages.value = messages.value.filterNot { it.id == message.id } + message
    }

    override suspend fun insertMessages(messages: List<AssistantMessageEntity>) {
        this.messages.value = this.messages.value + messages
    }

    override suspend fun deleteMessage(id: String) {
        messages.value = messages.value.filterNot { it.id == id }
    }

    override suspend fun clearSession(sessionId: String) {
        messages.value = messages.value.filterNot { it.sessionId == sessionId }
    }

    override suspend fun clearAll() {
        messages.value = emptyList()
    }
}

private class FakeAssistantSessionDao : AssistantSessionDao {
    private val sessions = MutableStateFlow<List<AssistantSessionEntity>>(emptyList())

    override fun observeSessions(): Flow<List<AssistantSessionEntity>> = sessions

    override suspend fun getSession(sessionId: String): AssistantSessionEntity? = sessions.value.firstOrNull { it.id == sessionId }

    override suspend fun insertSession(session: AssistantSessionEntity) {
        sessions.value = sessions.value.filterNot { it.id == session.id } + session
    }

    override suspend fun renameSession(
        sessionId: String,
        title: String,
    ) {
        sessions.value = sessions.value.map { if (it.id == sessionId) it.copy(title = title) else it }
    }

    override suspend fun touchSession(
        sessionId: String,
        updatedAt: Long,
    ) {
        sessions.value = sessions.value.map { if (it.id == sessionId) it.copy(updatedAt = updatedAt) else it }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessions.value = sessions.value.filterNot { it.id == sessionId }
    }
}

class AssistantRepositoryImplTest {
    @Test
    fun `saveMessage 写入实体并被 getMessages 流响应式发出`() =
        runTest {
            val dao = FakeAssistantMessageDao()
            val repository = AssistantRepositoryImpl(dao, FakeAssistantSessionDao())

            val msg =
                AssistantChatMessage(
                    id = "msg-1",
                    role = ChatMessageRole.USER,
                    content = "测试持久化",
                    timestamp = 1000L,
                )

            repository.saveMessage("s1", msg)

            val emitted = repository.getMessages("s1").first()
            assertEquals(1, emitted.size)
            assertEquals("msg-1", emitted[0].id)
            assertEquals(ChatMessageRole.USER, emitted[0].role)
            assertEquals("测试持久化", emitted[0].content)
        }

    @Test
    fun `clearMessages 清空数据库表`() =
        runTest {
            val dao = FakeAssistantMessageDao()
            val repository = AssistantRepositoryImpl(dao, FakeAssistantSessionDao())

            repository.saveMessage(
                "s1",
                AssistantChatMessage(
                    id = "msg-1",
                    role = ChatMessageRole.USER,
                    content = "待清空",
                    timestamp = 1000L,
                ),
            )
            repository.clearMessages("s1")

            val emitted = repository.getMessages("s1").first()
            assertTrue(emitted.isEmpty())
        }
}
