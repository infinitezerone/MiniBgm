package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.infinitezerone.minibgm.core.common.bgmLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val logger = bgmLogger("Bgm/MpvSurface")

/**
 * mpv render API 的渲染宿主（会话级，**不随 View 重建而销毁**）。
 *
 * 关键设计：EGLContext + 渲染线程都挂在这里，而不是挂 [MpvSurfaceView]。
 * Compose 在旋转 / 全屏 / PiP 往返时会重建 View 实例；如果 EGL 跟着 View 走，
 * 每次重建都会让 mpv 的 render context 失效（只能重建 mpv，黑屏 + 中断）。
 *
 * 职责拆分：
 * - View 只负责把 `SurfaceHolder.surface` 递进来 / 收回去；
 * - 本对象持有常驻 EGLContext 与渲染线程，surface 变了只换 [EGLSurface]；
 * - mpv 的 render context 因此跨 View 存活，旋转/PiP 退化成一次尺寸变化。
 */
object MpvRenderer {
    private val renderThread = HandlerThread("mpv-render").apply { start() }
    private val renderHandler = Handler(renderThread.looper)

    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private var renderContextCreated = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var surfaceReady = false
    private var renderScheduled = false

    /** 尺寸/表面变化后必须强制画一帧，不能等 mpv 的帧标志 */
    private var forceNextFrame = true

    private var skippedFrames = 0L
    private var frameWindowStartMs = 0L
    private var frameWindowCount = 0L

    /**
     * 当前 EGLSurface 的归属 surface。
     * Compose 换 View 时可能"新 View 先绑、旧 View 后解绑"，若不校验归属，
     * 旧 View 的解绑会把刚建好的 EGLSurface 拆掉 → 画面定格（实测）。
     */
    private var ownerSurface: Surface? = null

    // ---------- View 侧入口 ----------

    fun setSurface(surface: Surface) {
        renderHandler.post {
            ownerSurface = surface
            if (!ensureEgl()) return@post
            if (!createEglSurface(surface)) return@post
            surfaceReady = true
            ensureRenderContext()
            requestRenderLocked()
            logger.d { "surface 绑定完成（renderContext=$renderContextCreated）" }
        }
    }

    /** 只有当前归属的 surface 才允许解绑（防止旧 View 误拆新 EGLSurface） */
    fun clearSurface(surface: Surface?) {
        renderHandler.post {
            if (surface != null && ownerSurface !== surface) return@post
            ownerSurface = null
            surfaceReady = false
            destroyEglSurface()
            // 只销毁 EGLSurface：EGLContext 与 mpv render context 原样保留，
            // 下一个 View 的 Surface 来了直接复用
            logger.d { "surface 解绑：上下文常驻" }
        }
    }

    fun setViewport(
        width: Int,
        height: Int,
    ) {
        viewportWidth = width
        viewportHeight = height
        forceNextFrame = true
        requestRender()
    }

    fun requestRender() {
        renderHandler.post { requestRenderLocked() }
    }

    private fun requestRenderLocked() {
        if (renderScheduled) return
        renderScheduled = true
        renderHandler.post(renderFrame)
    }

    /**
     * mpv 句柄被重建（新一轮会话）后复位：旧 render context 已随旧句柄作废。
     * 不复位就会出现"宿主以为已建过、新句柄却没有上下文"的黑屏（只剩声音）。
     */
    fun resetContextState() {
        renderHandler.post {
            renderContextCreated = false
            forceNextFrame = true
            requestRenderLocked()
        }
    }

    /** mpv 句柄就绪后由引擎触发补建（表面可能早已就绪） */
    fun ensureContextAsync() {
        renderHandler.post {
            if (!surfaceReady) return@post
            ensureRenderContext()
            requestRenderLocked()
        }
    }

    /** 引擎销毁句柄前调用：在渲染线程同步释放上下文 */
    fun releaseRenderContextBlocking(timeoutMs: Long = 300L) {
        if (!renderContextCreated) return
        val latch = CountDownLatch(1)
        renderHandler.post {
            surfaceReady = false
            if (renderContextCreated) {
                MpvNative.renderContextRelease()
                renderContextCreated = false
            }
            MpvNative.markRenderContextReleased()
            latch.countDown()
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** 会话彻底结束（引擎 release）时回收 EGL */
    fun releaseAll() {
        releaseRenderContextBlocking()
        renderHandler.post {
            destroyEglSurface()
            releaseEgl()
        }
    }

    // ---------- 渲染 ----------

    private val renderFrame =
        Runnable {
            renderScheduled = false
            if (!surfaceReady) return@Runnable
            val display = eglDisplay ?: return@Runnable
            val surface = eglSurface ?: return@Runnable
            if (viewportWidth <= 0 || viewportHeight <= 0) return@Runnable
            ensureRenderContext()
            if (!renderContextCreated) return@Runnable
            // 只在 mpv 真说有新帧时重绘：update 回调也会被 seek/切轨/属性变化触发，
            // 无条件重绘会画出空帧，白耗 GPU 与 CPU
            if (!forceNextFrame && !MpvNative.renderContextUpdate()) {
                skippedFrames++
                return@Runnable
            }
            forceNextFrame = false
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
                logger.w { "eglMakeCurrent 失败，丢弃本帧" }
                return@Runnable
            }
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            MpvNative.renderContextRender(viewportWidth, viewportHeight)
            MpvNative.renderContextReportSwap()
            if (!EGL14.eglSwapBuffers(display, surface)) {
                logger.w { "eglSwapBuffers 失败" }
            } else {
                logFrameRate()
            }
        }

    /** 每秒输出一次实际绘制帧率（与片源 fps 对比即可判断有没有多画） */
    private fun logFrameRate() {
        frameWindowCount++
        val now = SystemClock.elapsedRealtime()
        if (frameWindowStartMs == 0L) {
            frameWindowStartMs = now
            return
        }
        val elapsed = now - frameWindowStartMs
        if (elapsed < 1000L) return
        val fps = frameWindowCount * 1000f / elapsed
        logger.d { "render fps=%.1f skipped=%d".format(fps, skippedFrames) }
        frameWindowStartMs = now
        frameWindowCount = 0
        skippedFrames = 0
    }

    /**
     * 建 mpv render context。必须在渲染线程且已 makeCurrent——mpv 建上下文时会调
     * GL 取设备信息，没有 current context 会直接失败
     * （libEGL: "call to OpenGL ES API with no current context"）。
     */
    private fun ensureRenderContext() {
        if (renderContextCreated) return
        val display = eglDisplay ?: return
        val surface = eglSurface ?: return
        if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return
        if (MpvNative.renderContextCreate()) {
            renderContextCreated = true
            MpvNative.markRenderContextReady()
            logger.d { "render context 已创建" }
        }
    }

    // ---------- EGL ----------

    private fun ensureEgl(): Boolean {
        eglDisplay?.let { return true }
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            logger.e { "eglGetDisplay 失败" }
            return false
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            logger.e { "eglInitialize 失败" }
            return false
        }
        val configAttrs =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            logger.e { "eglChooseConfig 失败" }
            return false
        }
        val config = configs[0] ?: return false
        val context =
            EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            logger.e { "eglCreateContext 失败" }
            return false
        }
        eglDisplay = display
        eglConfig = config
        eglContext = context
        return true
    }

    private fun createEglSurface(surface: Surface): Boolean {
        val display = eglDisplay ?: return false
        val config = eglConfig ?: return false
        destroyEglSurface()
        if (!surface.isValid) {
            logger.w { "surface 无效" }
            return false
        }
        val created =
            EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (created == null || created == EGL14.EGL_NO_SURFACE) {
            logger.e { "eglCreateWindowSurface 失败" }
            return false
        }
        eglSurface = created
        return true
    }

    private fun destroyEglSurface() {
        val display = eglDisplay
        val surface = eglSurface
        eglSurface = null
        if (display != null && surface != null) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
        }
    }

    private fun releaseEgl() {
        val display = eglDisplay ?: return
        val context = eglContext
        if (context != null) {
            EGL14.eglDestroyContext(display, context)
        }
        EGL14.eglTerminate(display)
        eglContext = null
        eglConfig = null
        eglDisplay = null
    }
}

/**
 * 只做一件事：把 [SurfaceView] 的 surface 递给会话级 [MpvRenderer]。
 *
 * 本类可以被 Compose 反复重建（旋转 / 全屏 / PiP），EGL 与 mpv 上下文都在
 * [MpvRenderer] 里常驻，因此重建不会中断播放。
 */
class MpvSurfaceView(
    context: Context,
) : SurfaceView(context),
    SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
        MpvNative.bindRenderView(this)
    }

    /**
     * 每次附着都重新注册"新帧"回调。
     * Compose 旋转/全屏/PiP 会 detach→attach（同一实例跨布局复用），
     * detach 时已解绑；不在这里绑回来，mpv 的每帧唤醒就无处可去，
     * 表现就是"只有一帧、之后定格/黑屏"。
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MpvNative.bindRenderView(this)
    }

    fun requestRenderSafely() = MpvRenderer.requestRender()

    fun requestRenderContext() = MpvRenderer.ensureContextAsync()

    override fun surfaceCreated(holder: SurfaceHolder) {
        MpvRenderer.setSurface(holder.surface)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        MpvRenderer.setViewport(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MpvRenderer.clearSurface(holder.surface)
    }

    override fun onDetachedFromWindow() {
        // 只解绑回调；surface 的解绑由 surfaceDestroyed 负责，且带归属校验，
        // 避免"新 View 先绑、旧 View 后拆"把新画面掐掉
        MpvNative.unbindRenderView(this)
        super.onDetachedFromWindow()
    }
}
