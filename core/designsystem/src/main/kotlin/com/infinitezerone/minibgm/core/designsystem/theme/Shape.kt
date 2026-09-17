package com.infinitezerone.minibgm.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MiniBgm 全局统一形状系统 (Material 3 Shapes)
 *
 * 规范应用中的圆角阶梯：
 * - extraSmall (4.dp): 小型状态徽章、评分标签、微型胶囊
 * - small (8.dp): 条目封面、内置操作按钮、次级容器
 * - medium (12.dp): 列表条目、操作卡片、弹窗与提示块
 * - large (16.dp): 核心业务卡片（无边框 FilledCard）、头部信息块
 * - extraLarge (24.dp): 用户总览大卡、底部抽屉、悬浮导航胶囊
 */
val BgmShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
