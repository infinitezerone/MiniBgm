package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1080dp")
class ButtonComponentsScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun buttonComponents_dark() {
        capture(darkTheme = true, path = "button_showcase_diffuse_dark.png")
    }

    @Test
    fun buttonComponents_light() {
        capture(darkTheme = false, path = "button_showcase_diffuse_light.png")
    }

    private fun capture(
        darkTheme: Boolean,
        path: String,
    ) {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = darkTheme) {
                ButtonComponentsShowcase()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/$path")
    }
}

@Composable
private fun ButtonComponentsShowcase() {
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.width(411.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "按钮系统走查规范",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ChatGPT / ColorOS 15 级高反差实体药丸与弥散光晕按键",
                    style = BgmContentTokens.Typography.subtleSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 1. Primary: 纯白/高反差主操作实体药丸键
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "1. Primary 高反差主操作 (Solid White Accent)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                // 尺寸阶梯：Large (46dp) -> Standard (40dp) -> Compact (30dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BgmPillButton(
                        text = "立即起播",
                        icon = BgmIcons.Play,
                        size = BgmPillSize.Large,
                        variant = BgmPillVariant.Primary,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "继续观看",
                        icon = BgmIcons.Play,
                        size = BgmPillSize.Standard,
                        variant = BgmPillVariant.Primary,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "播放",
                        icon = BgmIcons.Play,
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Primary,
                        onClick = {},
                    )
                }

                // 禁用态
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BgmPillButton(
                        text = "暂无源起播",
                        icon = BgmIcons.Play,
                        enabled = false,
                        variant = BgmPillVariant.Primary,
                        onClick = {},
                    )
                }
            }

            // 2. Secondary: 中性深色容器实色键
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "2. Secondary 中性次级操作 (Muted Surface Container)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BgmPillButton(
                        text = "已追番",
                        icon = BgmIcons.Bookmark,
                        size = BgmPillSize.Standard,
                        variant = BgmPillVariant.Secondary,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "全部 3 部",
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Secondary,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "不可用",
                        enabled = false,
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Secondary,
                        onClick = {},
                    )
                }
            }

            // 3. Tonal: 主题语义/强调操作键
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. Tonal 语义调色操作 (Tonal Accent Container)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BgmPillButton(
                        text = "纠偏 ±1",
                        icon = BgmIcons.Edit,
                        size = BgmPillSize.Standard,
                        variant = BgmPillVariant.Tonal,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "选集 04",
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Tonal,
                        onClick = {},
                    )
                }
            }

            // 4. Outlined: 极细发丝描边键
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "4. Outlined 极简发丝描边 (Hairline Outline)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BgmPillButton(
                        text = "查看详情",
                        size = BgmPillSize.Standard,
                        variant = BgmPillVariant.Outlined,
                        onClick = {},
                    )

                    BgmPillButton(
                        text = "切换排序",
                        icon = BgmIcons.Sort,
                        size = BgmPillSize.Compact,
                        variant = BgmPillVariant.Outlined,
                        onClick = {},
                    )
                }
            }

            // 5. Circle Action: 圆形微操作靶区（搭载 Aquamorphic 弥散晕染阴影）
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "5. Circle Action 圆形操作键 (Aquamorphic 弥散晕染光影)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )

                // 5.1 标准次级微操作
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BgmCircleActionButton(
                        icon = BgmIcons.Play,
                        contentDescription = "播放",
                        onClick = {},
                    )

                    BgmCircleActionButton(
                        icon = BgmIcons.Bookmark,
                        contentDescription = "收藏",
                        onClick = {},
                    )

                    BgmCircleActionButton(
                        icon = BgmIcons.MoreVert,
                        contentDescription = "更多",
                        onClick = {},
                    )

                    BgmCircleActionButton(
                        icon = BgmIcons.Close,
                        contentDescription = "关闭",
                        onClick = {},
                    )
                }

                // 5.2 ChatGPT 式高反差悬浮键 (Solid High-Contrast Floating Action)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 纯白实体主悬浮键（在深色下为纯白底黑标，亮色下为纯黑底白标）
                    BgmCircleActionButton(
                        icon = BgmIcons.Play,
                        contentDescription = "主浮动键",
                        containerColor = if (isDark) Color.White else Color.Black,
                        contentColor = if (isDark) Color.Black else Color.White,
                        size = 46.dp,
                        iconSize = 22.dp,
                        onClick = {},
                    )

                    // 浅色纯净悬浮键（模拟 ChatGPT 下翻/定位键）
                    BgmCircleActionButton(
                        icon = BgmIcons.KeyboardArrowDown,
                        contentDescription = "置底悬浮键",
                        containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White,
                        contentColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                        size = 42.dp,
                        iconSize = 24.dp,
                        onClick = {},
                    )
                }
            }
        }
    }
}
