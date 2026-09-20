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
        assertEquals(
            "https://api.example.com/v1/models",
            buildModelsUrl("https://api.example.com/v1/models", "custom"),
        )
        // Gemini 端点
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/models",
            buildModelsUrl("", AiConfig.PROVIDER_GEMINI),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/models",
            buildModelsUrl("https://generativelanguage.googleapis.com/v1beta/openai/", AiConfig.PROVIDER_GEMINI),
        )
        // Ollama：原生 /api/tags
        assertEquals("http://10.0.2.2:11434/api/tags", buildModelsUrl("http://10.0.2.2:11434", AiConfig.PROVIDER_OLLAMA))
        assertEquals("http://10.0.2.2:11434/api/tags", buildModelsUrl("", AiConfig.PROVIDER_OLLAMA))
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
    fun parseModelsBody_supportsGeminiNativePrefixAndStringArray() {
        val geminiBody = """{"models":[{"name":"models/gemini-2.0-flash"},{"name":"models/gemini-1.5-pro"}]}"""
        assertEquals(listOf("gemini-2.0-flash", "gemini-1.5-pro"), parseModelsBody(geminiBody))

        val stringArrayBody = """{"models":["qwen2.5:7b","deepseek-coder"]}"""
        assertEquals(listOf("qwen2.5:7b", "deepseek-coder"), parseModelsBody(stringArrayBody))
    }

    @Test
    fun parseModelsBody_supportsRootArrays() {
        val objArray = """[{"id":"gpt-4o"},{"name":"claude-3-5-sonnet"}]"""
        assertEquals(listOf("gpt-4o", "claude-3-5-sonnet"), parseModelsBody(objArray))

        val strArray = """["model-a","model-b"]"""
        assertEquals(listOf("model-a", "model-b"), parseModelsBody(strArray))
    }

    @Test
    fun parseModelsBody_invalidJsonReturnsNull() {
        assertNull(parseModelsBody("<html>not json</html>"))
        assertNull(parseModelsBody(""))
        assertNull(parseModelsBody("{}"))
    }
}
