package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.reflect.KClass

@DslMarker
annotation class BgmOverlayDsl

/**
 * 交互式挂起动作契约基类。
 *
 * 通过 1 对 1 的 [CancellableContinuation] 将挂起调用与 UI 渲染解耦，
 * 生命周期跟随当前作用域自动取消，彻底消除 ViewModel 中的临时 Boolean 状态与回调地狱。
 */
abstract class OverlayAction<R> {
    private var continuation: CancellableContinuation<R>? = null

    internal fun attachContinuation(cont: CancellableContinuation<R>) {
        this.continuation = cont
    }

    /** 提交结果并唤醒调用方挂起点 */
    fun complete(result: R) {
        val cont = continuation
        if (cont != null && cont.isActive) {
            cont.resumeWith(Result.success(result))
        }
    }

    /** 取消交互并向调用方抛出 [CancellationException] */
    fun cancel() {
        val cont = continuation
        if (cont != null && cont.isActive) {
            cont.cancel()
        }
    }
}

/**
 * 通用二值确认对话框动作契约。
 */
data class ConfirmDialogAction(
    val title: String,
    val message: String,
    val confirmText: String = "确定",
    val dismissText: String = "取消",
    val isDestructive: Boolean = false,
) : OverlayAction<Boolean>()

/**
 * 局部弹窗宿主状态持有者（限定在具体 Screen / NavEntry 树内）。
 */
@Stable
class OverlayHostState {
    var currentAction by mutableStateOf<OverlayAction<*>?>(null)
        private set

    /**
     * 挂起等待用户完成交互并返回强类型结果。
     * 若协程或页面被销毁，自动关闭弹窗并触发取消。
     */
    suspend fun <R> await(action: OverlayAction<R>): R =
        suspendCancellableCoroutine { continuation ->
            action.attachContinuation(continuation)
            currentAction = action
            continuation.invokeOnCancellation {
                currentAction = null
            }
        }.also {
            currentAction = null
        }

    /**
     * 挂起等待交互结果；若用户通过外部点击或手势取消，安全返回 null。
     */
    suspend fun <R> awaitOrNull(action: OverlayAction<R>): R? =
        try {
            await(action)
        } catch (_: CancellationException) {
            null
        }
}

@Composable
fun rememberOverlayHostState(): OverlayHostState = remember { OverlayHostState() }

/**
 * 类似 Navigation 3 EntryProviderScope 的强类型弹窗注册 DSL。
 */
@BgmOverlayDsl
class OverlayProviderScope internal constructor() {
    @PublishedApi
    internal val handlers = mutableMapOf<KClass<*>, @Composable (OverlayAction<*>) -> Unit>()

    inline fun <reified T : OverlayAction<*>> overlay(noinline content: @Composable (T) -> Unit) {
        handlers[T::class] = { action ->
            @Suppress("UNCHECKED_CAST")
            content(action as T)
        }
    }

    /** 预设的 Material 3 确认对话框扩展 */
    fun confirmDialog(icon: (@Composable () -> Unit)? = null) {
        overlay<ConfirmDialogAction> { action ->
            AlertDialog(
                onDismissRequest = { action.complete(false) },
                icon = icon,
                title = { Text(text = action.title) },
                text = { Text(text = action.message) },
                confirmButton = {
                    if (action.isDestructive) {
                        Button(
                            onClick = { action.complete(true) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(text = action.confirmText)
                        }
                    } else {
                        TextButton(onClick = { action.complete(true) }) {
                            Text(text = action.confirmText)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { action.complete(false) }) {
                        Text(text = action.dismissText)
                    }
                },
            )
        }
    }
}

/**
 * 局部弹窗渲染宿主。
 */
@Composable
fun BgmOverlayHost(
    hostState: OverlayHostState,
    builder: OverlayProviderScope.() -> Unit,
) {
    val scope = remember(builder) { OverlayProviderScope().apply(builder) }
    val current = hostState.currentAction

    if (current != null) {
        val renderer = scope.handlers[current::class]
        renderer?.invoke(current)
    }
}
