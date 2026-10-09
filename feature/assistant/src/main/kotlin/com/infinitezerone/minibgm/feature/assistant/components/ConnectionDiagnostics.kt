package com.infinitezerone.minibgm.feature.assistant.components

import androidx.annotation.StringRes
import com.infinitezerone.minibgm.feature.assistant.R

/** 连通测试诊断状态机 */
internal sealed interface ConnectionDiagnosticState {
    data object Idle : ConnectionDiagnosticState

    data object Testing : ConnectionDiagnosticState

    data class Success(
        val latencyMs: Long,
        val models: List<String>,
    ) : ConnectionDiagnosticState

    data class Failure(
        val summary: String,
        val detail: String,
    ) : ConnectionDiagnosticState
}

/**
 * 模型特征能力标签
 */
internal enum class ModelCapability(
    @StringRes val labelRes: Int,
) {
    REASONING(R.string.feature_assistant_capability_reasoning),
    VISION(R.string.feature_assistant_capability_vision),
    LIGHTWEIGHT(R.string.feature_assistant_capability_lightweight),
}

/**
 * 依据模型标识符自动提取其主要能力特征
 */
internal fun detectModelCapabilities(modelName: String): List<ModelCapability> {
    val name = modelName.lowercase()
    val caps = mutableListOf<ModelCapability>()
    if (name.contains("reasoner") || name.contains("r1") || name.contains("o1") || name.contains("o3") || name.contains("thinking")) {
        caps.add(ModelCapability.REASONING)
    }
    if (name.contains("vision") || name.contains("vl") || name.contains("omni") || name.contains("4o")) {
        caps.add(ModelCapability.VISION)
    }
    if (name.contains("flash") ||
        name.contains("mini") ||
        name.contains("nano") ||
        name.contains("lite") ||
        name.contains("small") ||
        name.contains("7b") ||
        name.contains("8b")
    ) {
        caps.add(ModelCapability.LIGHTWEIGHT)
    }
    return caps
}
