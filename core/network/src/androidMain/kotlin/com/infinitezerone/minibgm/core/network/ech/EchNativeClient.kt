package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger

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
)

/**
 * 封装对 Rust libminibgm_ech.so 的 JNI 调用
 */
object EchNativeClient {
    private val logger = bgmLogger("Bgm/EchNative")
    private var isLoaded = false

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

    external fun nativeFetch(
        url: String,
        method: String,
        headerKeys: Array<String>,
        headerValues: Array<String>,
        body: ByteArray?,
        timeoutMs: Long,
        targetAddrs: Array<String>?,
        enableEch: Boolean,
    ): EchNativeResponse
}
