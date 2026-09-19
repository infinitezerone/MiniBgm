package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.AiConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelCatalogTest {
    @Test
    fun buildModelsUrl_keepsVersionPrefixAndAppendsModels() {
        // OpenAI 兼容端点：保留 /v1 前缀（实测 …/api/v1/models 可用）
        assertEquals("https://api.example.com/v1/models", buildModelsUrl("https://api.example.com/v1", "custom"))
        assertEquals("https://api.example.com/models", buildModelsUrl("https://api.example.com", "custom"))
        assertEquals(
            "https://api.example.com/v1/models",
            buildModelsUrl("https://api.example.com/v1/chat/completions", "custom"),
        )
        // Ollama：原生 /api/tags
        assertEquals("http://10.0.2.2:11434/api/tags", buildModelsUrl("http://10.0.2.2:11434", AiConfig.PROVIDER_OLLAMA))
        assertEquals("http://localhost:11434/api/tags", buildModelsUrl("", AiConfig.PROVIDER_OLLAMA))
    }

    @Test
    fun parseModelsBody_supportsOpenAiShape() {
        val body = """{"data":[{"id":"DeepSeek-V4.1-Flash"},{"id":"DeepSeek-V4-Flash"},{"id":""}]}"""
        assertEquals(listOf("DeepSeek-V4.1-Flash", "DeepSeek-V4-Flash"), parseModelsBody(body))
    }

    @Test
    fun parseModelsBody_supportsOllamaShape() {
        val body = """{"models":[{"name":"qwen2.5:7b"},{"name":"llama3.1"}]}"""
        assertEquals(listOf("qwen2.5:7b", "llama3.1"), parseModelsBody(body))
    }

    @Test
    fun parseModelsBody_invalidJsonReturnsNull() {
        assertNull(parseModelsBody("<html>not json</html>"))
        assertNull(parseModelsBody(""))
    }
}
