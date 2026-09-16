package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PendingAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 暂存 AI 智能体工具执行时生成的 HITL 待确认操作提案。
 * 保证线程安全并在 UI 交互时支持弹出消费或按 ID 检索。
 */
class PendingActionStore {
    private val _actions = MutableStateFlow<List<PendingAction>>(emptyList())
    val actions: StateFlow<List<PendingAction>> = _actions.asStateFlow()

    fun add(action: PendingAction) {
        _actions.update { current ->
            if (current.any { it.actionId == action.actionId }) current else current + action
        }
    }

    fun remove(actionId: String) {
        _actions.update { current -> current.filterNot { it.actionId == actionId } }
    }

    fun popAll(): List<PendingAction> {
        var popped: List<PendingAction> = emptyList()
        _actions.update { current ->
            popped = current
            emptyList()
        }
        return popped
    }

    fun clear() {
        _actions.update { emptyList() }
    }
}
