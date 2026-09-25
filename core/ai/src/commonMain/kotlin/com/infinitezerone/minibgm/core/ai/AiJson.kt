package com.infinitezerone.minibgm.core.ai

import kotlinx.serialization.json.Json

/**
 * `:core:ai` 模块内部共享的 JSON 序列化单例。
 * 统一收敛解析配置，避免在各个智能体工具、解析器与 Wire 协议层中重复手写 `Json { ... }` 实例。
 */
internal val aiJson: Json =
    Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
