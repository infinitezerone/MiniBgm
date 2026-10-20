package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmGlassLevel
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LuminousChromeScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun luminousChrome_light() {
        capture(darkTheme = false, amoledDark = false, path = "luminous_chrome_light.png")
    }

    @Test
    fun luminousChrome_dark() {
        capture(darkTheme = true, amoledDark = false, path = "luminous_chrome_dark.png")
    }

    @Test
    fun luminousChrome_amoled() {
        capture(darkTheme = true, amoledDark = true, path = "luminous_chrome_amoled.png")
    }

    @Test
    fun luminousChrome_largeFont() {
        capture(darkTheme = false, amoledDark = false, path = "luminous_chrome_large_font.png", fontScale = 1.4f)
    }

    private fun capture(
        darkTheme: Boolean,
        amoledDark: Boolean,
        path: String,
        fontScale: Float = 1f,
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                MiniBgmTheme(
                    darkTheme = darkTheme,
                    amoledDark = amoledDark,
                    uiStyle = AppUiStyle.Luminous,
                ) {
                    LuminousChromePreview()
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/$path")
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LuminousChromePreview() {
    BgmFloatingPageScaffold(
        modifier = Modifier.size(width = 411.dp, height = 891.dp),
        topBar = {
            BgmTopAppBar(
                title = { Text("时间表") },
                actions = {
                    Text(
                        text = "本周",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                },
            )
        },
        floatingBottomBar = {
            BgmGlassSurface(
                shape = CircleShape,
                level = BgmGlassLevel.Prominent,
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("时间表", "搜索", "我的").forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (index == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        },
        floatingBottomBarSink = 28.dp,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = BgmSpacing.TopBar.height + 24.dp,
                    bottom = 24.dp,
                ),
        ) {
            Text(
                text = "今晚更新",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            BgmFloatingFrame(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column {
                    Text(
                        text = "药屋少女的呢喃",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "第 24 话 · 20:00 更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
