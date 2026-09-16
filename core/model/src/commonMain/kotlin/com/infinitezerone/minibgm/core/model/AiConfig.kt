package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * AI 服务配置：支持本地（如 Ollama）或云端 OpenAI 兼容端点。
 * 遵循 AGENTS.md 凭据隔离规范：API 访问密钥与 Bangumi OAuth Token 严格隔离。
 */
@Serializable
data class AiConfig(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val provider: String = PROVIDER_OLLAMA,
) {
    companion object {
        const val PROVIDER_OLLAMA = "ollama"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_CUSTOM = "custom"
    }
}
