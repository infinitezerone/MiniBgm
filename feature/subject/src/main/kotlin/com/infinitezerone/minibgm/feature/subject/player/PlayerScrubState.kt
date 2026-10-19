package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 拖动进度的不可变快照。
 *
 * [active] 与 [fraction] 必须同帧一致：拆成两个平行变量会造出「未在拖动，但进度还停在旧拖动点」
 * 的非法中间态（时间文本与进度条会各自读到不同帧的值），因此合并为一个不可变对象、一次写成。
 */
internal data class PlayerScrubSnapshot(
    val active: Boolean = false,
    val fraction: Float = 0f,
)

/**
 * 播放器进度的**唯一**拖动状态持有者。
 *
 * 屏幕横向手势与底部进度条（横屏通栏 / 竖屏单行）三处入口都只经它读写，
 * 不再由界面层各自维护 `isScrubbing` + `scrubProgress` 两个变量、两套回调。
 *
 * 单个 [state] 一次赋值 = 原子更新（对齐仓库「多字段原子更新」的约束）；对外只暴露只读状态，
 * 写入口收口为 [begin] / [update] / [end] / [cancel]。
 *
 * 取消语义的差异：手势层能区分「取消」与「松手」，进度条用的
 * `drag(pointerId, onDrag): Boolean` 无法区分（轻点进度条落点也是 false），
 * 故进度条只走 [end]，把落点当作一次点击定位。
 */
@Stable
internal class PlayerScrubState {
    var state by mutableStateOf(PlayerScrubSnapshot())
        private set

    val isActive: Boolean
        get() = state.active

    /** 进入拖动：按下点即初值，避免首帧落到 0 再跳回。 */
    fun begin(fraction: Float) {
        state = PlayerScrubSnapshot(active = true, fraction = fraction.coerceIn(0f, 1f))
    }

    /** 拖动中的实时进度；未处于拖动（取消后才到的迟到帧）直接忽略。 */
    fun update(fraction: Float) {
        if (!state.active) return
        state = state.copy(fraction = fraction.coerceIn(0f, 1f))
    }

    /** 结束拖动并落定最终进度（松手提交 seek 用）。 */
    fun end(fraction: Float) {
        state = PlayerScrubSnapshot(active = false, fraction = fraction.coerceIn(0f, 1f))
    }

    /** 取消拖动：不提交 seek，保持最后进度归零由调用方决定（这里只退出拖动态）。 */
    fun cancel() {
        state = PlayerScrubSnapshot(active = false, fraction = 0f)
    }
}
