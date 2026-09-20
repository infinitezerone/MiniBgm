package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAssistantMessageDao : AssistantMessageDao {
    private val messages = MutableStateFlow<List<AssistantMessageEntity>>(emptyList())

    override fun getAllMessages(): Flow<List<AssistantMessageEntity>> = messages

    override suspend fun insertMessage(message: AssistantMessageEntity) {
        messages.value = messages.value.filterNot { it.id == message.id } + message
    }

    override suspend fun insertMessages(messages: List<AssistantMessageEntity>) {
        this.messages.value = this.messages.value + messages
    }

    override suspend fun deleteMessage(id: String) {
        messages.value = messages.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        messages.value = emptyList()
    }
}

class AssistantRepositoryImplTest {
    @Test
    fun `saveMessage 写入实体并被 getMessages 流响应式发出`() =
        runTest {
            val dao = FakeAssistantMessageDao()
            val repository = AssistantRepositoryImpl(dao)

            val msg =
                AssistantChatMessage(
                    id = "msg-1",
                    role = ChatMessageRole.USER,
                    content = "测试持久化",
                    timestamp = 1000L,
                )

            repository.saveMessage(msg)

            val emitted = repository.getMessages().first()
            assertEquals(1, emitted.size)
            assertEquals("msg-1", emitted[0].id)
            assertEquals(ChatMessageRole.USER, emitted[0].role)
            assertEquals("测试持久化", emitted[0].content)
        }

    @Test
    fun `clearMessages 清空数据库表`() =
        runTest {
            val dao = FakeAssistantMessageDao()
            val repository = AssistantRepositoryImpl(dao)

            repository.saveMessage(
                AssistantChatMessage(
                    id = "msg-1",
                    role = ChatMessageRole.USER,
                    content = "待清空",
                    timestamp = 1000L,
                ),
            )
            repository.clearMessages()

            val emitted = repository.getMessages().first()
            assertTrue(emitted.isEmpty())
        }
}
