package com.infinitezerone.minibgm.feature.assistant

import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.feature.assistant.components.autoDetectProvider
import com.infinitezerone.minibgm.feature.assistant.components.defaultModelFor
import com.infinitezerone.minibgm.feature.assistant.components.defaultProfileName
import com.infinitezerone.minibgm.feature.assistant.components.providerDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantConfigDialogTest {
    @Test
    fun autoDetectProvider_recognizes_gemini() {
        assertEquals(
            AiConfig.PROVIDER_GEMINI,
            autoDetectProvider("https://generativelanguage.googleapis.com/v1beta/openai/"),
        )
        assertEquals(
            AiConfig.PROVIDER_GEMINI,
            autoDetectProvider("https://my-gemini-proxy.com/v1"),
        )
    }

    @Test
    fun autoDetectProvider_recognizes_ollama() {
        assertEquals(
            AiConfig.PROVIDER_OLLAMA,
            autoDetectProvider("http://10.0.2.2:11434"),
        )
        assertEquals(
            AiConfig.PROVIDER_OLLAMA,
            autoDetectProvider("http://192.168.1.100:11434/api"),
        )
        assertEquals(
            AiConfig.PROVIDER_OLLAMA,
            autoDetectProvider("https://ollama.myhome.org"),
        )
    }

    @Test
    fun autoDetectProvider_defaults_to_custom_openai_compatible() {
        assertEquals(
            AiConfig.PROVIDER_CUSTOM,
            autoDetectProvider("https://api.openai.com/v1"),
        )
        assertEquals(
            AiConfig.PROVIDER_CUSTOM,
            autoDetectProvider("https://api.deepseek.com/v1"),
        )
        assertEquals(
            AiConfig.PROVIDER_CUSTOM,
            autoDetectProvider("https://api.groq.com/openai/v1"),
        )
    }

    @Test
    fun defaultModelFor_returns_appropriate_defaults() {
        assertEquals("gemini-2.5-flash", defaultModelFor(AiConfig.PROVIDER_GEMINI))
        assertEquals("qwen2.5:7b", defaultModelFor(AiConfig.PROVIDER_OLLAMA))
        assertEquals("gpt-4o-mini", defaultModelFor(AiConfig.PROVIDER_CUSTOM))
    }

    @Test
    fun providerDisplayName_formats_clean_labels() {
        assertEquals("Google Gemini", providerDisplayName(AiConfig.PROVIDER_GEMINI))
        assertEquals("Ollama (自建服务)", providerDisplayName(AiConfig.PROVIDER_OLLAMA))
        assertEquals("OpenAI 兼容协议", providerDisplayName(AiConfig.PROVIDER_CUSTOM))
    }

    @Test
    fun defaultProfileName_usesProviderLabelAndModel() {
        assertEquals(
            "Gemini · gemini-2.5-flash",
            defaultProfileName(AiConfig.PROVIDER_GEMINI, "gemini-2.5-flash", "https://generativelanguage.googleapis.com/v1beta/openai/"),
        )
        assertEquals(
            "Ollama · qwen2.5:7b",
            defaultProfileName(AiConfig.PROVIDER_OLLAMA, "qwen2.5:7b", "http://10.0.2.2:11434"),
        )
    }

    @Test
    fun defaultProfileName_customProviderFallsBackToHost() {
        assertEquals(
            "api.groq.com · gpt-4o-mini",
            defaultProfileName(AiConfig.PROVIDER_CUSTOM, "gpt-4o-mini", "https://api.groq.com/openai/v1"),
        )
        // 端点解析不出主机名（如空串）时兜底，不抛异常
        assertEquals(
            "自定义端点 · gpt-4o-mini",
            defaultProfileName(AiConfig.PROVIDER_CUSTOM, "gpt-4o-mini", ""),
        )
    }
}
