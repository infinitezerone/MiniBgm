package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.database.dao.AssistantMessageDao
import com.infinitezerone.minibgm.core.database.dao.AssistantSessionDao
import com.infinitezerone.minibgm.core.database.entity.AssistantMessageEntity
import com.infinitezerone.minibgm.core.database.entity.AssistantSessionEntity
import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.AssistantSession
import com.infinitezerone.minibgm.core.model.ChatMessageRole
import com.infinitezerone.minibgm.core.model.PendingActionCard
import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import com.infinitezerone.minibgm.core.network.BgmHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.util.UUID

class AssistantRepositoryImpl(
    private val assistantMessageDao: AssistantMessageDao,
    private val assistantSessionDao: AssistantSessionDao,
) : AssistantRepository {
    override fun getSessions(): Flow<List<AssistantSession>> =
        assistantSessionDao.observeSessions().map { entities ->
            entities.map { it.toModel() }
        }

    override suspend fun createSession(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        assistantSessionDao.insertSession(
            AssistantSessionEntity(id = id, title = title, createdAt = now, updatedAt = now),
        )
        return id
    }

    override suspend fun renameSession(
        sessionId: String,
        title: String,
    ) {
        if (title.isBlank()) return
        assistantSessionDao.renameSession(sessionId, title.trim())
    }

    override suspend fun deleteSession(sessionId: String) {
        assistantMessageDao.clearSession(sessionId)
        assistantSessionDao.deleteSession(sessionId)
    }

    override fun getMessages(sessionId: String): Flow<List<AssistantChatMessage>> =
        assistantMessageDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { it.toModel() }
        }

    override suspend fun saveMessage(
        sessionId: String,
        message: AssistantChatMessage,
    ) {
        assistantMessageDao.insertMessage(message.toEntity(sessionId))
        assistantSessionDao.touchSession(sessionId, System.currentTimeMillis())
    }

    override suspend fun deleteMessage(
        sessionId: String,
        id: String,
    ) {
        assistantMessageDao.deleteMessage(id)
    }

    override suspend fun clearMessages(sessionId: String) {
        assistantMessageDao.clearSession(sessionId)
    }
}

internal fun AssistantSessionEntity.toModel(): AssistantSession =
    AssistantSession(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)

internal fun AssistantChatMessage.toEntity(sessionId: String): AssistantMessageEntity {
    val actionsJson =
        if (pendingActions.isNotEmpty()) {
            runCatching { BgmHttpClient.jsonConfig.encodeToString(pendingActions) }.getOrNull()
        } else {
            null
        }
    val sourcesJson =
        playableSources?.let {
            runCatching { BgmHttpClient.jsonConfig.encodeToString(it) }.getOrNull()
        }
    return AssistantMessageEntity(
        id = id,
        role = role.name,
        content = content,
        timestamp = timestamp,
        pendingActionsJson = actionsJson,
        playableSourcesJson = sourcesJson,
        isError = isError,
        sessionId = sessionId,
    )
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
                runCatching { BgmHttpClient.jsonConfig.decodeFromString<List<PendingActionCard>>(raw) }.getOrNull()
            }.orEmpty()
    val sources =
        playableSourcesJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { BgmHttpClient.jsonConfig.decodeFromString<PlayableEpisodeList>(raw) }.getOrNull()
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
