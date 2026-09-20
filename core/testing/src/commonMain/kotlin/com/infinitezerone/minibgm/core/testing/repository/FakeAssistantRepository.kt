package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.data.repository.AssistantRepository
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAssistantRepository : AssistantRepository {
    private val _messages = MutableStateFlow<List<AssistantChatMessage>>(emptyList())

    override fun getMessages(): Flow<List<AssistantChatMessage>> = _messages.asStateFlow()

    override suspend fun saveMessage(message: AssistantChatMessage) {
        val current = _messages.value
        val index = current.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            val updated = current.toMutableList()
            updated[index] = message
            _messages.value = updated
        } else {
            _messages.value = current + message
        }
    }

    override suspend fun deleteMessage(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    override suspend fun clearMessages() {
        _messages.value = emptyList()
    }

    fun setMessages(messages: List<AssistantChatMessage>) {
        _messages.value = messages
    }
}
