package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.infinitezerone.minibgm.core.common.bgmLogger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private val logger = bgmLogger("Bgm/MpvView")

/**
 * mpv render API 承载视图：渲染上下文常驻，surface 重建只是换渲染目标，
 * 旋转/PiP/后台往返零拆卸——这是替代 wid 模式的根基。
 *
 * 帧驱动：mpv 更新回调（原生线程）→ requestRender，
 * GL 线程在 onDrawFrame 里把当前帧画进默认 FBO。
 *
 * 创建顺序陷阱：本视图通常比 mpv 句柄先就绪（句柄由第一次 `load()` 创建），
 * 故 [Renderer.onSurfaceCreated] 里的建上下文会失败；引擎初始化完成后经
 * [MpvNative.notifyMpvReady] → [requestRenderContext] 补建一次，两边收敛。
 */
class MpvRenderView(
    context: Context,
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(Renderer())
        renderMode = RENDERMODE_WHEN_DIRTY
        MpvNative.bindRenderView(this)
    }

    /** GL 线程上补建 render context 并立刻画一帧（mpv 句柄晚于视图就绪时的兜底） */
    internal fun requestRenderContext() {
        logger.d { "requestRenderContext 入队（GL 线程补建）" }
        queueEvent {
            val ok = MpvNative.renderContextCreate()
            logger.d { "补建 render context -> $ok" }
            requestRender()
        }
    }

    /** mpv 渲染线程要求出新帧；GLSurfaceView 内部已做线程安全，直接投递即可 */
    internal fun requestRenderSafely() {
        requestRender()
    }

    override fun onDetachedFromWindow() {
        // 先解绑，避免 mpv 更新回调继续打向将死的视图
        MpvNative.unbindRenderView(this)
        // GL 线程上释放 render context（早于引擎的 MpvNative.destroy）
        queueEvent { MpvNative.renderContextRelease() }
        super.onDetachedFromWindow()
    }

    private class Renderer : GLSurfaceView.Renderer {
        /** GL surface 当前尺寸（onSurfaceChanged 下发，onDrawFrame 消费） */
        @Volatile private var surfaceWidth = 0

        @Volatile private var surfaceHeight = 0

        /** 仅用于首帧日志计数，避免每帧刷屏 */
        private var drawnFrames = 0

        override fun onSurfaceCreated(
            gl: GL10,
            config: EGLConfig,
        ) {
            val ok = MpvNative.renderContextCreate()
            logger.d { "GL onSurfaceCreated, renderContextCreate -> $ok" }
        }

        override fun onSurfaceChanged(
            gl: GL10,
            width: Int,
            height: Int,
        ) {
            logger.d { "GL onSurfaceChanged ${width}x$height" }
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10) {
            // 尺寸未定义时不能提交 FBO 参数（0×0 会让 mpv 直接报错并丢弃该帧）
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return
            if (drawnFrames < 3) {
                drawnFrames++
                logger.d { "首帧渲染 #$drawnFrames ${surfaceWidth}x$surfaceHeight" }
            }
            MpvNative.renderContextRender(surfaceWidth, surfaceHeight)
            MpvNative.renderContextReportSwap()
        }
    }
}
