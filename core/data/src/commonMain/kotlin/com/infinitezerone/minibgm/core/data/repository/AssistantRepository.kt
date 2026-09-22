package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.AssistantChatMessage
import com.infinitezerone.minibgm.core.model.AssistantSession
import kotlinx.coroutines.flow.Flow

/** 追番助手多会话存储：会话元数据 + 按 sessionId 归组的消息 */
interface AssistantRepository {
    /** 会话列表，按最近更新时间降序 */
    fun getSessions(): Flow<List<AssistantSession>>

    /** 新建会话并返回 id */
    suspend fun createSession(title: String): String

    suspend fun renameSession(
        sessionId: String,
        title: String,
    )

    /** 删除会话及其全部消息 */
    suspend fun deleteSession(sessionId: String)

    fun getMessages(sessionId: String): Flow<List<AssistantChatMessage>>

    /** 保存消息并顺带 touch 会话（排序按最近更新） */
    suspend fun saveMessage(
        sessionId: String,
        message: AssistantChatMessage,
    )

    suspend fun deleteMessage(
        sessionId: String,
        id: String,
    )

    suspend fun clearMessages(sessionId: String)
}
