package com.infinitezerone.minibgm.feature.assistant.components

import androidx.annotation.StringRes
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.feature.assistant.R

/**
 * 常见服务商预设模型与官方推荐端点原型
 */
internal data class ProviderPreset(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val badgeRes: Int,
    val endpoint: String,
    val defaultModel: String,
    val provider: String,
    val isApiKeyRequired: Boolean,
    @StringRes val tipRes: Int,
    val popularModels: List<String> = emptyList(),
)

internal val PROVIDER_PRESETS: List<ProviderPreset> =
    listOf(
        ProviderPreset(
            id = "gemini",
            nameRes = R.string.feature_assistant_preset_gemini_name,
            badgeRes = R.string.feature_assistant_preset_gemini_badge,
            endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/",
            defaultModel = "gemini-2.5-flash",
            provider = AiConfig.PROVIDER_GEMINI,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_gemini_tip,
            popularModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.5-pro"),
        ),
        ProviderPreset(
            id = "deepseek",
            nameRes = R.string.feature_assistant_preset_deepseek_name,
            badgeRes = R.string.feature_assistant_preset_deepseek_badge,
            endpoint = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_deepseek_tip,
            popularModels = listOf("deepseek-chat", "deepseek-reasoner"),
        ),
        ProviderPreset(
            id = "siliconflow",
            nameRes = R.string.feature_assistant_preset_siliconflow_name,
            badgeRes = R.string.feature_assistant_preset_siliconflow_badge,
            endpoint = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_siliconflow_tip,
            popularModels = listOf("Qwen/Qwen2.5-7B-Instruct", "deepseek-ai/DeepSeek-V3", "THUDM/glm-4-9b-chat"),
        ),
        ProviderPreset(
            id = "zhipu",
            nameRes = R.string.feature_assistant_preset_zhipu_name,
            badgeRes = R.string.feature_assistant_preset_zhipu_badge,
            endpoint = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_zhipu_tip,
            popularModels = listOf("glm-4-flash", "glm-4-air", "glm-4-plus"),
        ),
        ProviderPreset(
            id = "dashscope",
            nameRes = R.string.feature_assistant_preset_dashscope_name,
            badgeRes = R.string.feature_assistant_preset_dashscope_badge,
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_dashscope_tip,
            popularModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        ),
        ProviderPreset(
            id = "moonshot",
            nameRes = R.string.feature_assistant_preset_moonshot_name,
            badgeRes = R.string.feature_assistant_preset_moonshot_badge,
            endpoint = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_moonshot_tip,
            popularModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        ),
        ProviderPreset(
            id = "ollama",
            nameRes = R.string.feature_assistant_preset_ollama_name,
            badgeRes = R.string.feature_assistant_preset_ollama_badge,
            endpoint = "http://10.0.2.2:11434/v1",
            defaultModel = "qwen2.5:7b",
            provider = AiConfig.PROVIDER_OLLAMA,
            isApiKeyRequired = false,
            tipRes = R.string.feature_assistant_preset_ollama_tip,
            popularModels = listOf("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:7b"),
        ),
        ProviderPreset(
            id = "openrouter",
            nameRes = R.string.feature_assistant_preset_openrouter_name,
            badgeRes = R.string.feature_assistant_preset_openrouter_badge,
            endpoint = "https://openrouter.ai/api/v1",
            defaultModel = "google/gemini-2.5-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_openrouter_tip,
            popularModels = listOf("google/gemini-2.5-flash", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct"),
        ),
        ProviderPreset(
            id = "openai",
            nameRes = R.string.feature_assistant_preset_openai_name,
            badgeRes = R.string.feature_assistant_preset_openai_badge,
            endpoint = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_openai_tip,
            popularModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
        ),
        ProviderPreset(
            id = "groq",
            nameRes = R.string.feature_assistant_preset_groq_name,
            badgeRes = R.string.feature_assistant_preset_groq_badge,
            endpoint = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_groq_tip,
            popularModels = listOf("llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b"),
        ),
        ProviderPreset(
            id = "custom",
            nameRes = R.string.feature_assistant_preset_custom_name,
            badgeRes = R.string.feature_assistant_preset_custom_badge,
            endpoint = "",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tipRes = R.string.feature_assistant_preset_custom_tip,
            popularModels = emptyList(),
        ),
    )

/** 根据输入的 API Key 格式特征智能匹配对应的服务商预设 */
internal fun detectProviderFromApiKey(apiKey: String): ProviderPreset? {
    val key = apiKey.trim()
    if (key.isBlank()) return null

    val targetId =
        when {
            // Google Gemini AI Studio (以 AIzaSy 开头，通常 39 位)
            key.startsWith("AIzaSy") -> "gemini"
            // OpenRouter (以 sk-or-v1- 开头)
            key.startsWith("sk-or-v1-") -> "openrouter"
            // OpenAI 官方 Project / Service / Admin Key (sk-proj-, sk-admin-, sk-svcacct-)
            key.startsWith("sk-proj-") || key.startsWith("sk-admin-") || key.startsWith("sk-svcacct-") -> "openai"
            // OpenAI 经典个人 Key (sk- 开头接 48 或 51 位字符)
            key.matches(Regex("""^sk-[A-Za-z0-9]{48,51}$""")) -> "openai"
            // Groq (以 gsk_ 开头)
            key.startsWith("gsk_") -> "groq"
            // 智谱开放平台 API Key (通常为 32位十六进制/字符加点再加16-32位字符)
            key.matches(Regex("""^[0-9a-zA-Z]{32}\.[0-9a-zA-Z]{16,32}$""")) -> "zhipu"
            // DeepSeek 官方 API Key (格式固定为 sk- 加上 32 位十六进制字符，总长 35 位)
            key.matches(Regex("^sk-[0-9a-fA-F]{32}$")) -> "deepseek"
            else -> null
        } ?: return null

    return PROVIDER_PRESETS.firstOrNull { it.id == targetId }
}

/**
 * 检查模型是否已知在官方端点暂不支持 Tool Calling（函数调用 / 智能体工具执行）。
 * 若已知不支持，UI 渲染轻量警示，避免用户误选后导致查时刻表或打卡报错。
 */
internal fun isModelKnownUnsupportedToolCall(modelName: String): Boolean {
    val lower = modelName.trim().lowercase()
    return lower == "deepseek-reasoner" ||
        lower == "o1-preview" ||
        lower == "o1-mini"
}

/** 根据输入的 Base URL 自动解析并匹配协议提供商 */
internal fun autoDetectProvider(endpoint: String): String {
    val lowered = endpoint.trim().lowercase()
    return when {
        "generativelanguage.googleapis.com" in lowered || "gemini" in lowered -> AiConfig.PROVIDER_GEMINI
        "11434" in lowered || "ollama" in lowered -> AiConfig.PROVIDER_OLLAMA
        else -> AiConfig.PROVIDER_CUSTOM
    }
}

/** 规范化 Base URL：自动补齐 scheme、剥离多余的 chat/models 路径并剔除尾部斜杠 */
internal fun normalizeEndpoint(rawEndpoint: String): String {
    val trimmed = rawEndpoint.trim()
    if (trimmed.isBlank()) return ""
    val cleaned =
        trimmed
            .removeSuffix("/chat/completions")
            .removeSuffix("/chat/completions/")
            .removeSuffix("/models")
            .removeSuffix("/models/")
            .trimEnd('/')

    val withScheme =
        if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            if (cleaned.startsWith("localhost", ignoreCase = true) ||
                cleaned.startsWith("127.0.0.1") ||
                cleaned.startsWith("10.0.2.2") ||
                cleaned.startsWith("192.168.")
            ) {
                "http://$cleaned"
            } else {
                "https://$cleaned"
            }
        } else {
            cleaned
        }
    return withScheme.trimEnd('/')
}

/** 模型与端点疑似错配的 UI 提示：文案资源 + 插值参数（模型 id 等） */
internal data class ModelMismatchWarning(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/** 校验模型名称与端点/服务商是否疑似错配 */
internal fun checkModelProviderMismatch(
    endpoint: String,
    model: String,
    provider: String,
): ModelMismatchWarning? {
    val ep = endpoint.trim().lowercase()
    val m = model.trim().lowercase()
    if (m.isBlank() || ep.isBlank()) return null

    // 端点是 DeepSeek，模型却填了 gemini 或 gpt
    if ("deepseek" in ep && ("gemini" in m || "gpt-" in m || "claude" in m)) {
        return ModelMismatchWarning(
            messageRes = R.string.feature_assistant_mismatch_deepseek,
            args = listOf("deepseek-chat", "deepseek-reasoner"),
        )
    }
    // 端点是 Google Gemini，模型却填了 deepseek / qwen / gpt
    if (("generativelanguage.googleapis.com" in ep || provider == AiConfig.PROVIDER_GEMINI) &&
        ("deepseek" in m || "qwen" in m || "gpt-" in m || "claude" in m)
    ) {
        return ModelMismatchWarning(
            messageRes = R.string.feature_assistant_mismatch_gemini,
            args = listOf("gemini-2.5-flash"),
        )
    }
    // 端点是 OpenAI 官方，模型填了 deepseek / gemini / qwen
    if ("api.openai.com" in ep && ("deepseek" in m || "gemini" in m || "qwen" in m)) {
        return ModelMismatchWarning(
            messageRes = R.string.feature_assistant_mismatch_openai,
            args = listOf("gpt-4o-mini"),
        )
    }
    // 端点是 智谱，模型填了 gemini / gpt / deepseek
    if ("bigmodel.cn" in ep && ("gemini" in m || "gpt-" in m || "deepseek" in m)) {
        return ModelMismatchWarning(
            messageRes = R.string.feature_assistant_mismatch_zhipu,
            args = listOf("glm-4-flash"),
        )
    }
    return null
}

internal fun defaultModelFor(provider: String): String =
    when (provider) {
        AiConfig.PROVIDER_GEMINI -> "gemini-2.5-flash"
        AiConfig.PROVIDER_OLLAMA -> "qwen2.5:7b"
        else -> "gpt-4o-mini"
    }

internal fun providerDisplayName(provider: String): String =
    when (provider) {
        AiConfig.PROVIDER_GEMINI -> "Google Gemini"
        AiConfig.PROVIDER_OLLAMA -> "Ollama (自建服务)"
        else -> "OpenAI 兼容协议"
    }

/** 方案默认名：服务商（自定义取端点主机名）+ 模型，用户可改。主机名解析不出时用 [customProviderLabel] 兜底 */
internal fun defaultProfileName(
    provider: String,
    model: String,
    endpoint: String,
    customProviderLabel: String,
): String {
    val base =
        when (provider) {
            AiConfig.PROVIDER_GEMINI -> "Gemini"
            AiConfig.PROVIDER_OLLAMA -> "Ollama"
            else -> {
                val host =
                    runCatching { java.net.URI(endpoint.trim()).host }
                        .getOrNull()
                        ?.removePrefix("www.")
                        .orEmpty()
                host.ifBlank { customProviderLabel }
            }
        }
    val modelPart = model.trim().ifBlank { defaultModelFor(provider) }
    return "$base · $modelPart"
}
