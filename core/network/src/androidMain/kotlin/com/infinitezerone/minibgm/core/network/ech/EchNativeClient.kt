package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Rust 原生层 ECH HTTP 响应承载对象（与 libminibgm_ech.so 严格对应）
 */
class EchNativeResponse(
    val statusCode: Int,
    val headerKeys: Array<String>,
    val headerValues: Array<String>,
    val body: ByteArray,
    val echAccepted: Boolean,
    val errorMessage: String?,
    val connectedAddr: String?,
    val updatedEchConfig: String?,
)

data class EchNativeStartedResponse(
    val requestId: Long,
    val response: EchNativeResponse,
)

/**
 * 封装对 Rust libminibgm_ech.so 的 JNI 调用
 */
object EchNativeClient {
    private val logger = bgmLogger("Bgm/EchNative")
    private var isLoaded = false
    private val requestExecutor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "MiniBgm-EchNative").apply { isDaemon = true }
        }

    init {
        try {
            System.loadLibrary("minibgm_ech")
            isLoaded = true
            logger.i { "libminibgm_ech.so loaded successfully" }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to load libminibgm_ech.so" }
        }
    }

    fun isAvailable(): Boolean = isLoaded

    suspend fun nativeFetchCancellable(
        url: String,
        method: String,
        headerKeys: Array<String>,
        headerValues: Array<String>,
        body: ByteArray?,
        timeoutMs: Long,
        targetAddrs: Array<String>?,
        enableEch: Boolean,
        requireEch: Boolean,
        echConfig: String?,
    ): EchNativeStartedResponse {
        val requestId =
            nativeStart(
                url,
                method,
                headerKeys,
                headerValues,
                body,
                timeoutMs,
                targetAddrs,
                enableEch,
                requireEch,
                echConfig,
            )
        if (requestId <= 0L) {
            // nativeStart 用负数区分失败原因：一律抛同一句话会把"为什么起不来"丢掉，排障只能靠猜
            throw IllegalStateException(
                when (requestId) {
                    0L -> "Failed to start native ECH request"
                    -1L -> "Native ECH request rejected: invalid argument"
                    -2L -> "Native ECH client initialization failed"
                    -3L -> "Native ECH runtime unavailable"
                    -4L -> "Native ECH request limit reached (too many in-flight requests)"
                    else -> "Native ECH request failed with code $requestId"
                },
            )
        }

        val response =
            suspendCancellableCoroutine<EchNativeResponse> { continuation ->
                continuation.invokeOnCancellation { nativeCancel(requestId) }
                requestExecutor.execute {
                    try {
                        val response = nativeAwait(requestId)
                        if (response == null) {
                            continuation.resumeWithException(IllegalStateException("Native ECH request was cancelled"))
                        } else {
                            continuation.resume(response)
                        }
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        return EchNativeStartedResponse(requestId, response)
    }

    suspend fun nativeReadBodyChunkCancellable(requestId: Long): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { nativeCancel(requestId) }
            requestExecutor.execute {
                try {
                    val chunk = nativeReadBodyChunk(requestId)
                    continuation.resume(chunk)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }

    external fun nativeStart(
        url: String,
        method: String,
        headerKeys: Array<String>,
        headerValues: Array<String>,
        body: ByteArray?,
        timeoutMs: Long,
        targetAddrs: Array<String>?,
        enableEch: Boolean,
        requireEch: Boolean,
        echConfig: String?,
    ): Long

    external fun nativeAwait(requestId: Long): EchNativeResponse?

    external fun nativeReadBodyChunk(requestId: Long): ByteArray?

    external fun nativeCancel(requestId: Long)
}
