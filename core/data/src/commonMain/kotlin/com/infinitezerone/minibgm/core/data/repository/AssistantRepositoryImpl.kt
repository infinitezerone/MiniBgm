package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import com.infinitezerone.minibgm.core.model.PendingActionCard
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val assistantJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

class AssistantRepositoryImpl(
    private val assistantMessageDao: AssistantMessageDao,
) : AssistantRepository {
    override fun getMessages(): Flow<List<AssistantChatMessage>> =
        assistantMessageDao.getAllMessages().map { entities ->
            entities.map { it.toModel() }
        }

    override suspend fun saveMessage(message: AssistantChatMessage) {
        assistantMessageDao.insertMessage(message.toEntity())
    }

    override suspend fun deleteMessage(id: String) {
        assistantMessageDao.deleteMessage(id)
    }

    override suspend fun clearMessages() {
        assistantMessageDao.clearAll()
    }
}

internal fun AssistantMessageEntity.toModel(): AssistantChatMessage {
    val roleEnum =
        if (role.equals("USER", ignoreCase = true)) {
            ChatMessageRole.USER
        } else {
            ChatMessageRole.ASSISTANT
        }
    val actions =
        pendingActionsJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { assistantJson.decodeFromString<List<PendingActionCard>>(raw) }.getOrNull()
            }.orEmpty()
    val sources =
        playableSourcesJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { assistantJson.decodeFromString<PlayableEpisodeList>(raw) }.getOrNull()
        }
    return AssistantChatMessage(
        id = id,
        role = roleEnum,
        content = content,
        timestamp = timestamp,
        pendingActions = actions,
        playableSources = sources,
        isError = isError,
    )
}

internal fun AssistantChatMessage.toEntity(): AssistantMessageEntity {
    val actionsJson =
        if (pendingActions.isNotEmpty()) {
            runCatching { assistantJson.encodeToString(pendingActions) }.getOrNull()
        } else {
            null
        }
    val sourcesJson =
        playableSources?.let {
            runCatching { assistantJson.encodeToString(it) }.getOrNull()
        }
    return AssistantMessageEntity(
        id = id,
        role = role.name,
        content = content,
        timestamp = timestamp,
        pendingActionsJson = actionsJson,
        playableSourcesJson = sourcesJson,
        isError = isError,
    )
}
