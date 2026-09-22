package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/** 追番助手会话（多会话管理）：元数据行，消息正文按 [id] 归组存放 */
@Serializable
data class AssistantSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
