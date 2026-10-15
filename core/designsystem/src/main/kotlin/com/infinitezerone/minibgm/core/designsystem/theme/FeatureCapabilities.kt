package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.infinitezerone.minibgm.core.model.FeatureCapabilities

/**
 * 全局特性能力环境 CompositionLocal（UI 树中的环境单一真源）。
 *
 * 任意叶子组件可直接通过 `LocalFeatureCapabilities.current` 获取当前功能是否可用，
 * 严禁通过 4~5 层深度的函数签名层层透传可空 lambda 或未受控布尔值作为开关。
 */
val LocalFeatureCapabilities = compositionLocalOf { FeatureCapabilities() }

/**
 * 特性门禁容器组件：
 * 仅当满足指定能力策略 [capability] 时才渲染 [content]，未满足时渲染 [fallback]（默认为空）。
 *
 * 示例：
 * ```kotlin
 * FeatureGate(capability = { it.isAiAssistantEnabled }) {
 *     IconButton(onClick = onAiSearch) { ... }
 * }
 * ```
 */
@Composable
fun FeatureGate(
    capability: (FeatureCapabilities) -> Boolean,
    modifier: Modifier = Modifier,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val capabilities = LocalFeatureCapabilities.current
    if (capability(capabilities)) {
        content()
    } else {
        fallback?.invoke()
    }
}
