package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * 顶栏 chrome 的连续状态。
 *
 * [surfaceProgress] 控制顶栏容器从透明过渡到页面背景色；[titleProgress] 控制顶栏标题的
 * 出现进度。静态页面不需要传这个对象；需要滚动联动的页面传入同一个 progress 即可。
 */
@Immutable
data class BgmTopBarChrome(
    val surfaceProgress: Float = 0f,
    val titleProgress: Float = 1f,
) {
    fun normalized(): BgmTopBarChrome =
        BgmTopBarChrome(
            surfaceProgress = surfaceProgress.coerceIn(0f, 1f),
            titleProgress = titleProgress.coerceIn(0f, 1f),
        )

    companion object {
        /** 内容滚动时，顶栏背景与标题按同一进度逐渐出现。 */
        fun collapsing(progress: Float): BgmTopBarChrome {
            val normalized = progress.coerceIn(0f, 1f)
            return BgmTopBarChrome(
                surfaceProgress = normalized,
                titleProgress = normalized,
            )
        }
    }
}
