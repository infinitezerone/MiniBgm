package com.infinitezerone.minibgm.core.designsystem.theme

/**
 * 透明度令牌：按语义而不是按数值散落在组件里。
 *
 * 这些 alpha 只用于前景/分隔/装饰层；材质透明度仍由 [LuminousTokens] 的玻璃配置负责。
 */
object BgmOpacity {
    /** 骨架屏与低对比底纹。 */
    val skeleton: Float = 0.12f

    /** 图标背后的浅色染色底。 */
    val tintWash: Float = 0.12f

    /** 分隔线。 */
    val divider: Float = 0.35f

    /** 占位图标、弱提示。 */
    val placeholder: Float = 0.4f

    /** 内容框描边。 */
    val border: Float = 0.45f

    /** 次要图标与辅助控件。 */
    val muted: Float = 0.6f

    /** 图片/内容上的压暗遮罩。 */
    val scrim: Float = 0.65f

    /** 高对比叠层，例如黑色评分底。 */
    val overlay: Float = 0.75f

    /** 强调正文或图标。 */
    val emphasis: Float = 0.85f
}
