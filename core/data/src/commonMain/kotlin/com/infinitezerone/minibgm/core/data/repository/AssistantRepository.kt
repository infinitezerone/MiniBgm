package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import kotlinx.coroutines.flow.Flow

interface AssistantRepository {
    fun getMessages(): Flow<List<AssistantChatMessage>>

    suspend fun saveMessage(message: AssistantChatMessage)

    suspend fun deleteMessage(id: String)

    suspend fun clearMessages()
}
