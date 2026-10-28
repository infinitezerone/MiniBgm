package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import com.infinitezerone.minibgm.core.common.bgmLogger

private val logger = bgmLogger("Bgm/MpvNative")

/**
 * libmpv 原生桥（minibgm-mpv.so）：控制面 + render API + 事件泵。
 *
 * 线程契约：
 * - 控制调用（create/load/property）主线程发起即可；
 * - onEvent / onProperty* 来自原生事件线程，消费方必须自行收口主线程
 *   （MpvPlayerEngine.notify 承担）；
 * - onRenderUpdate 来自 mpv 渲染线程，MpvRenderView 响应 requestRender。
 *
 * 生命周期：进程级单句柄——[create] 前原生层自动 terminate 残留旧句柄，
 * 任何时序下都不会触发双句柄 assert 自杀。
 */
object MpvNative {
    init {
        System.loadLibrary("minibgm_mpv")
    }

    interface EventListener {
        fun onEvent(eventId: Int)

        fun onProperty(name: String)

        fun onPropertyLong(
            name: String,
            value: Long,
        )

        fun onPropertyDouble(
            name: String,
            value: Double,
        )

        fun onPropertyFlag(
            name: String,
            value: Boolean,
        )

        fun onPropertyString(
            name: String,
            value: String,
        )
    }

    @Volatile
    var eventListener: EventListener? = null

    @Volatile
    var renderUpdateListener: (() -> Unit)? = null

    /**
     * 当前绑定中的渲染视图。
     *
     * 时序陷阱：Compose 进入播放页时会**先**组合出 [MpvRenderView]，而 mpv 句柄
     * 要等到第一次 [create]/[init]（由 `load()` 触发）才存在。视图的
     * `onSurfaceCreated` 早于句柄就绪时，原生侧会因 `mpv_handle()` 为空而静默放弃创建
     * render context——表现就是黑屏 + 无限缓冲（时间停在 00:00）。
     * 因此引擎初始化完成后必须回头通知该视图补建，见 [notifyMpvReady]。
     */
    @Volatile
    private var renderViewRef: java.lang.ref.WeakReference<MpvRenderView>? = null

    fun bindRenderView(view: MpvRenderView) {
        renderViewRef = java.lang.ref.WeakReference(view)
        renderUpdateListener = { view.requestRenderSafely() }
    }

    fun unbindRenderView(view: MpvRenderView) {
        if (renderViewRef?.get() === view) {
            renderViewRef = null
            renderUpdateListener = null
        }
    }

    /** mpv create/init 完成后调用：让已存在的渲染视图补建 render context（幂等） */
    fun notifyMpvReady() {
        renderViewRef?.get()?.requestRenderContext()
    }

    // ---------- 控制面 ----------

    external fun create(appctx: Context)

    external fun init()

    external fun destroy()

    external fun command(cmd: Array<out String>)

    external fun setOptionString(
        name: String,
        value: String,
    ): Int

    // ---------- 属性（get 失败返回哨兵值，包装层映射 null） ----------

    private external fun getPropertyInt(name: String): Long

    private external fun getPropertyDouble(name: String): Double

    private external fun getPropertyBoolean(name: String): Byte

    external fun getPropertyString(name: String): String?

    fun getDouble(name: String): Double? = getPropertyDouble(name).takeUnless { it.isNaN() }

    fun getInt(name: String): Int? = getPropertyInt(name).takeUnless { it == Long.MIN_VALUE }?.toInt()

    fun getBoolean(name: String): Boolean? = getPropertyBoolean(name).takeIf { it.toInt() != 2 }?.toInt() != 0

    external fun setPropertyInt(
        name: String,
        value: Long,
    )

    external fun setPropertyDouble(
        name: String,
        value: Double,
    )

    external fun setPropertyBoolean(
        name: String,
        value: Boolean,
    )

    external fun setPropertyString(
        name: String,
        value: String,
    )

    external fun observeProperty(
        name: String,
        format: Int,
    )

    // ---------- render API ----------

    /** 须在 EGL 上下文 current 的线程调用（GLSurfaceView onSurfaceCreated）；false 表示未建成 */
    external fun renderContextCreate(): Boolean

    external fun renderContextRender(
        width: Int,
        height: Int,
    )

    external fun renderContextReportSwap()

    external fun renderContextRelease()

    // ---------- 原生回调入口（勿手动调用） ----------

    @JvmStatic
    fun onEvent(eventId: Int) {
        eventListener?.onEvent(eventId)
    }

    @JvmStatic
    fun onPropertyNone(name: String) {
        logger.d { "property none: $name" }
        eventListener?.onProperty(name)
    }

    @JvmStatic
    fun onPropertyLong(
        name: String,
        value: Long,
    ) {
        eventListener?.onPropertyLong(name, value)
    }

    @JvmStatic
    fun onPropertyDouble(
        name: String,
        value: Double,
    ) {
        eventListener?.onPropertyDouble(name, value)
    }

    @JvmStatic
    fun onPropertyFlag(
        name: String,
        value: Boolean,
    ) {
        eventListener?.onPropertyFlag(name, value)
    }

    @JvmStatic
    fun onPropertyString(
        name: String,
        value: String,
    ) {
        eventListener?.onPropertyString(name, value)
    }

    @JvmStatic
    fun onRenderUpdate() {
        renderUpdateListener?.invoke()
    }

    // ---------- mpv 格式常量 ----------

    object MpvFormat {
        const val MPV_FORMAT_NONE: Int = 0
        const val MPV_FORMAT_STRING: Int = 1
        const val MPV_FORMAT_FLAG: Int = 3
        const val MPV_FORMAT_INT64: Int = 4
        const val MPV_FORMAT_DOUBLE: Int = 5
    }

    object MpvEvent {
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
    }
}
