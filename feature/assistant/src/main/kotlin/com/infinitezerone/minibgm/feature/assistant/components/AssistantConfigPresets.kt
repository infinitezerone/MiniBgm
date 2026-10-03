package com.infinitezerone.minibgm.feature.assistant.components

import com.infinitezerone.minibgm.core.model.AiConfig

/**
 * 常见服务商预设模型与官方推荐端点原型
 */
internal data class ProviderPreset(
    val id: String,
    val name: String,
    val badge: String,
    val endpoint: String,
    val defaultModel: String,
    val provider: String,
    val isApiKeyRequired: Boolean,
    val tip: String,
    val popularModels: List<String> = emptyList(),
)

internal val PROVIDER_PRESETS: List<ProviderPreset> =
    listOf(
        ProviderPreset(
            id = "gemini",
            name = "Google Gemini",
            badge = "官方推荐",
            endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/",
            defaultModel = "gemini-2.5-flash",
            provider = AiConfig.PROVIDER_GEMINI,
            isApiKeyRequired = true,
            tip = "Google 官方提供免费调用额度，只需配置 API Key",
            popularModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.5-pro"),
        ),
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            badge = "高性价比",
            endpoint = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "深度推理与高智能对话，极低 Token 成本",
            popularModels = listOf("deepseek-chat", "deepseek-reasoner"),
        ),
        ProviderPreset(
            id = "siliconflow",
            name = "硅基流动",
            badge = "开源聚合",
            endpoint = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "国内稳定聚合服务，提供主流开源模型免费与优惠额度",
            popularModels = listOf("Qwen/Qwen2.5-7B-Instruct", "deepseek-ai/DeepSeek-V3", "THUDM/glm-4-9b-chat"),
        ),
        ProviderPreset(
            id = "zhipu",
            name = "智谱 GLM",
            badge = "国产标杆",
            endpoint = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "清华系自研基座大模型，glm-4-flash 高并发免费调用",
            popularModels = listOf("glm-4-flash", "glm-4-air", "glm-4-plus"),
        ),
        ProviderPreset(
            id = "dashscope",
            name = "阿里百炼",
            badge = "通义千问",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "阿里巴巴通义系列千亿级模型，兼容 OpenAI 规范",
            popularModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        ),
        ProviderPreset(
            id = "moonshot",
            name = "月之暗面 Kimi",
            badge = "长上下文",
            endpoint = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "超长上下文与深度文本理解能力",
            popularModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        ),
        ProviderPreset(
            id = "ollama",
            name = "Ollama 本地",
            badge = "私有化",
            endpoint = "http://10.0.2.2:11434/v1",
            defaultModel = "qwen2.5:7b",
            provider = AiConfig.PROVIDER_OLLAMA,
            isApiKeyRequired = false,
            tip = "本地或局域网 NAS 私有部署，免 API Key，隐私安全",
            popularModels = listOf("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:7b"),
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            badge = "全球聚合",
            endpoint = "https://openrouter.ai/api/v1",
            defaultModel = "google/gemini-2.5-flash",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "统一网关路由全球数百款闭源与开源模型",
            popularModels = listOf("google/gemini-2.5-flash", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct"),
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI",
            badge = "行业标杆",
            endpoint = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "OpenAI 官方 API 服务",
            popularModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
        ),
        ProviderPreset(
            id = "groq",
            name = "Groq",
            badge = "极速推理",
            endpoint = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "不向中国大陆及香港地区提供服务",
            popularModels = listOf("llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b"),
        ),
        ProviderPreset(
            id = "custom",
            name = "自定义端点",
            badge = "OpenAI 兼容",
            endpoint = "",
            defaultModel = "gpt-4o-mini",
            provider = AiConfig.PROVIDER_CUSTOM,
            isApiKeyRequired = true,
            tip = "可填自建网关或第三方中转服务",
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

/** 校验模型名称与端点/服务商是否疑似错配 */
internal fun checkModelProviderMismatch(
    endpoint: String,
    model: String,
    provider: String,
): String? {
    val ep = endpoint.trim().lowercase()
    val m = model.trim().lowercase()
    if (m.isBlank() || ep.isBlank()) return null

    // 端点是 DeepSeek，模型却填了 gemini 或 gpt
    if ("deepseek" in ep && ("gemini" in m || "gpt-" in m || "claude" in m)) {
        return "当前端点为 DeepSeek，模型疑似错配，建议切换为 deepseek-chat 或 deepseek-reasoner"
    }
    // 端点是 Google Gemini，模型却填了 deepseek / qwen / gpt
    if (("generativelanguage.googleapis.com" in ep || provider == AiConfig.PROVIDER_GEMINI) &&
        ("deepseek" in m || "qwen" in m || "gpt-" in m || "claude" in m)
    ) {
        return "当前端点为 Google Gemini，模型疑似错配，建议切换为 gemini-2.5-flash"
    }
    // 端点是 OpenAI 官方，模型填了 deepseek / gemini / qwen
    if ("api.openai.com" in ep && ("deepseek" in m || "gemini" in m || "qwen" in m)) {
        return "当前端点为 OpenAI 官方，模型疑似错配，建议切换为 gpt-4o-mini"
    }
    // 端点是 智谱，模型填了 gemini / gpt / deepseek
    if ("bigmodel.cn" in ep && ("gemini" in m || "gpt-" in m || "deepseek" in m)) {
        return "当前端点为智谱 GLM，模型疑似错配，建议切换为 glm-4-flash"
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

/** 方案默认名：服务商（自定义取端点主机名）+ 模型，用户可改 */
internal fun defaultProfileName(
    provider: String,
    model: String,
    endpoint: String,
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
                host.ifBlank { "自定义端点" }
            }
        }
    val modelPart = model.trim().ifBlank { defaultModelFor(provider) }
    return "$base · $modelPart"
}
