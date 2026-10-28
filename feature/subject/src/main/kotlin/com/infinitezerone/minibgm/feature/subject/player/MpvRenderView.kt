package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * mpv render API 承载视图：渲染上下文常驻，surface 重建只是换渲染目标，
 * 旋转/PiP/后台往返零拆卸——这是替代 wid 模式的根基。
 *
 * 帧驱动：mpv 更新回调（原生线程）→ requestRender，
 * GL 线程在 onDrawFrame 里把当前帧画进默认 FBO。
 */
class MpvRenderView(
    context: Context,
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(Renderer)
        renderMode = RENDERMODE_WHEN_DIRTY

        MpvNative.renderUpdateListener = { this@MpvRenderView.requestRender() }
    }

    override fun onDetachedFromWindow() {
        // GL 线程上释放 render context（早于引擎的 MpvNative.destroy）
        queueEvent { MpvNative.renderContextRelease() }
        super.onDetachedFromWindow()
    }

    private object Renderer : GLSurfaceView.Renderer {
        /** GL surface 当前尺寸（onSurfaceChanged 下发，onDrawFrame 消费） */
        @Volatile private var surfaceWidth = 0

        @Volatile private var surfaceHeight = 0

        override fun onSurfaceCreated(
            gl: GL10,
            config: EGLConfig,
        ) {
            // 幂等：GL 上下文重建时旧 render context 一并重建
            MpvNative.renderContextCreate()
        }

        override fun onSurfaceChanged(
            gl: GL10,
            width: Int,
            height: Int,
        ) {
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10) {
            MpvNative.renderContextRender(surfaceWidth, surfaceHeight)
            MpvNative.renderContextReportSwap()
        }
    }
}
