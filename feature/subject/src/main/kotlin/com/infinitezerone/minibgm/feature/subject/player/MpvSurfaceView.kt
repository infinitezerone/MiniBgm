package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * mpv render API 的渲染宿主入口：只负责把 [SurfaceView] 的 surface 递给原生渲染循环。
 *
 * EGL 上下文、渲染线程、帧路径（update → render → report_swap → 换缓冲）全部在
 * native `mpv-gl` 线程内完成，帧路径零 JNI 穿透。surface 的归属校验（Compose 重建
 * View 时「新 surface 先绑、旧 surface 后拆」的迟到回调）也在原生侧完成——
 * 因此本类可以被 Compose 反复重建（旋转 / 全屏 / PiP），播放不中断。
 *
 * 时序说明：本视图在组合阶段创建，早于 mpv 句柄（首次 `load()` 才创建）。
 * surfaceCreated 早于句柄就绪时，原生侧会跳过 render context 创建，等引擎
 * 初始化完成后调用 [MpvNative.retryRenderContext] 补建。
 */
class MpvSurfaceView(
    context: Context,
) : SurfaceView(context),
    SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MpvNative.setSurface(holder.surface)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        // 输出尺寸由 native 侧每帧 eglQuerySurface 读取，永远与缓冲一致，
        // 不依赖任何 View 回调（此前本设备 surfaceChanged 实测不可靠）
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MpvNative.clearSurface(holder.surface)
    }
}
