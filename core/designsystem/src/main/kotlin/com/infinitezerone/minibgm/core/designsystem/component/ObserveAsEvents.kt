package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 现代生命周期感知的一次性事件收集器（与 Navigation 3 多栈与单屏生命周期强对齐）。
 *
 * 1. 只有当前 NavEntry / Activity 处于 [minActiveState]（默认 [Lifecycle.State.STARTED]，即可见状态）时才消费事件；
 * 2. 页面进入后台或被上层新页面完全覆盖（STOPPED）时，自动挂起/暂停消费，事件安全留在 Channel.BUFFERED 队列中；
 * 3. 页面重新切回前台（STARTED）时，自动恢复收集并处理堆积事件，杜绝后台静默吃事件；
 * 4. [onEvent] 在 Dispatchers.Main.immediate 上下文中执行，天然适合直接更新 UI、调用 SnackbarHostState 或触发跳转。
 */
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    key1: Any? = Unit,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner, key1) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            withContext(Dispatchers.Main.immediate) {
                flow.collect { event ->
                    onEvent(event)
                }
            }
        }
    }
}
