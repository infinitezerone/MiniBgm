package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.StatusDoing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1000dp")
class ContentTokensScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contentTokens_dark() {
        capture(darkTheme = true, path = "timeless_obsidian_showcase_dark.png")
    }

    @Test
    fun contentTokens_light() {
        capture(darkTheme = false, path = "timeless_obsidian_showcase_light.png")
    }

    private fun capture(
        darkTheme: Boolean,
        path: String,
    ) {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = darkTheme) {
                ContentTokensShowcase()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/$path")
    }
}

@Composable
private fun ContentTokensShowcase() {
    Surface(
        modifier = Modifier.width(411.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Section 1: 接着看焦点卡 (Hero Focus Card - 遵循 screen1_home_timeless.png)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "接着看",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "全部 3 部",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BgmFocusCard(
                    title = "葬送的芙莉莲",
                    subtitle = "第 04 话 · 旅途的终点",
                    statusLabel = "2小时前更新",
                    progress = 0.76f,
                    progressLabel = "18:24 / 24:10",
                    onClick = {},
                    cover = {
                        // 模拟电影级宽屏海报底图（深邃蓝夜与微光光斑）
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF1E2838),
                                                Color(0xFF141A24),
                                                Color(0xFF0C1017),
                                            ),
                                        ),
                                    ),
                        ) {
                            // 模拟海报中央的人物立绘/氛围元素
                            Text(
                                text = "✨ 芙莉莲星空草地 · 电影级海报",
                                style = BgmContentTokens.Typography.subtleSubtitle,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    },
                )
            }

            // Section 2: 今日更新列表行 (Flat Rows)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "今日更新 · 周二",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "21:45 JST",
                        style = BgmContentTokens.Typography.tabularCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BgmFlatRow(
                    title = "胆大党",
                    subtitle = "EP 02 · 幽灵还是外星人",
                    onClick = {},
                    leading = {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(BgmContentTokens.FlatRow.thumbShape)
                                    .background(Color(0xFF283747)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "封面",
                                style = BgmContentTokens.Typography.microLabel,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "23:00 待播",
                                    style = BgmContentTokens.Typography.tabularCaption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Icon(
                                imageVector = BgmIcons.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                BgmFlatRow(
                    title = "Re:从零开始的异世界生活 第三季",
                    subtitle = "EP 03 · 绚烂的波纹",
                    onClick = {},
                    leading = {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(BgmContentTokens.FlatRow.thumbShape)
                                    .background(Color(0xFF1B2631)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "封面",
                                style = BgmContentTokens.Typography.microLabel,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StatusDoing.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "已更新",
                                    style = BgmContentTokens.Typography.tabularCaption,
                                    color = StatusDoing,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Icon(
                                imageVector = BgmIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        }
    }
}
