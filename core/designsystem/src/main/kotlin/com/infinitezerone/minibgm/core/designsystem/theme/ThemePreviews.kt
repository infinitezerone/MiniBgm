package com.infinitezerone.minibgm.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * 多主题组合预览注解：一键在 Android Studio 预览器中同时渲染浅色模式与深色模式。
 */
@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class ThemePreviews

/**
 * 多设备屏幕组合预览注解：在常用手机、折叠屏与平板屏幕规格下验证布局自适应能力。
 */
@Preview(name = "Phone Portrait", device = "spec:width=411dp,height=891dp")
@Preview(name = "Phone Landscape", device = "spec:width=891dp,height=411dp")
@Preview(name = "Foldable", device = "spec:width=673dp,height=841dp")
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240")
annotation class DevicePreviews
