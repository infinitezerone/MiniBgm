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
 * 强类型交互请求标记接口。
 *
 * 任意纯数据类（data class / data object）均可实现本接口，
 * 携带交互参数并声明其期望的强类型返回值 [R]。
 *
 * 示例：
 * ```kotlin
 * data class PickEpisodeRequest(val episodes: List<Episode>) : OverlayRequest<Episode?>
 * ```
 */
interface OverlayRequest<out R>

/**
 * 通用二值确认对话框请求契约。
 */
data class ConfirmDialogAction(
    val title: String,
    val message: String,
    val confirmText: String = "确定",
    val dismissText: String = "取消",
    val isDestructive: Boolean = false,
) : OverlayRequest<Boolean>

/**
 * 局部弹窗宿主状态持有者（限定在具体 Screen / NavEntry 树内）。
 */
@Stable
class OverlayHostState {
    @PublishedApi
    internal class ActiveEntry<R>(
        val request: OverlayRequest<R>,
        private val continuation: CancellableContinuation<R>,
    ) {
        fun respond(result: R) {
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(result))
            }
        }

        fun cancel() {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }

    internal var currentEntry by mutableStateOf<ActiveEntry<*>?>(null)
        private set

    /** 当前正在展示的交互请求数据对象 */
    val currentRequest: OverlayRequest<*>?
        get() = currentEntry?.request

    /**
     * 发起交互请求并挂起等待结果。
     * 若调用协程或承载页面被取消，自动撤销交互并清理。
     */
    suspend fun <R> request(request: OverlayRequest<R>): R =
        suspendCancellableCoroutine { continuation ->
            currentEntry = ActiveEntry(request, continuation)
            continuation.invokeOnCancellation {
                currentEntry = null
            }
        }.also {
            currentEntry = null
        }

    /**
     * 发起交互请求并安全等待结果；若用户通过外部点击或手势取消，返回 null。
     */
    suspend fun <R> requestOrNull(request: OverlayRequest<R>): R? =
        try {
            request(request)
        } catch (_: CancellationException) {
            null
        }

    /** 别名兼容，等价于 [request] */
    suspend fun <R> await(request: OverlayRequest<R>): R = request(request)

    /** 别名兼容，等价于 [requestOrNull] */
    suspend fun <R> awaitOrNull(request: OverlayRequest<R>): R? = requestOrNull(request)
}

@Composable
fun rememberOverlayHostState(): OverlayHostState = remember { OverlayHostState() }

/**
 * 类似 Navigation 3 EntryProviderScope 的强类型弹窗注册 DSL。
 */
@BgmOverlayDsl
class OverlayProviderScope internal constructor() {
    @PublishedApi
    internal val handlers = mutableMapOf<KClass<*>, @Composable (OverlayRequest<*>, (Any?) -> Unit) -> Unit>()

    /**
     * 注册特定请求数据类对应的 Composable 交互视图。
     *
     * @param content 接受纯数据请求对象 [request] 和完成交互的响应回调 [onRespond]
     */
    inline fun <reified T : OverlayRequest<R>, R> overlay(noinline content: @Composable (request: T, onRespond: (R) -> Unit) -> Unit) {
        handlers[T::class] = { request, onRespond ->
            @Suppress("UNCHECKED_CAST")
            content(request as T, onRespond as (R) -> Unit)
        }
    }

    /** 预设的 Material 3 确认对话框扩展 */
    fun confirmDialog(icon: (@Composable () -> Unit)? = null) {
        overlay<ConfirmDialogAction, Boolean> { action, onRespond ->
            AlertDialog(
                onDismissRequest = { onRespond(false) },
                icon = icon,
                title = { Text(text = action.title) },
                text = { Text(text = action.message) },
                confirmButton = {
                    if (action.isDestructive) {
                        Button(
                            onClick = { onRespond(true) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(text = action.confirmText)
                        }
                    } else {
                        TextButton(onClick = { onRespond(true) }) {
                            Text(text = action.confirmText)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onRespond(false) }) {
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
    val current = hostState.currentEntry

    if (current != null) {
        val renderer =
            scope.handlers[current.request::class]
                ?: error(
                    "未注册的 Overlay 渲染器: [${current.request::class.qualifiedName}]！" +
                        "请在当前页面的 BgmOverlayHost { ... } 中使用 overlay<${current.request::class.simpleName}, ...> { ... } 进行声明。",
                )
        renderer.invoke(current.request) { result ->
            @Suppress("UNCHECKED_CAST")
            (current as OverlayHostState.ActiveEntry<Any?>).respond(result)
        }
    }
}
