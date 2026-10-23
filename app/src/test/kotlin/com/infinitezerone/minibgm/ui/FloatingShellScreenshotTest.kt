package com.infinitezerone.minibgm.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.component.BgmCircleActionButton
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingFrame
import com.infinitezerone.minibgm.core.designsystem.component.BgmFloatingPageScaffold
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopBarMode
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.AppUiStyle
import com.infinitezerone.minibgm.core.designsystem.theme.BgmSpacing
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.navigation.ScheduleRoute
import com.infinitezerone.minibgm.ui.component.BgmFloatingNavigationBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 悬浮外壳的真实组件预览：直接渲染 [BgmFloatingNavigationBar] 与 [BgmFloatingPageScaffold]，
 * 避免设计系统测试里用文字胶囊冒充底栏造成“预览与实机不符”。
 *
 * 内容只用于验证层级与材质分工：内容卡片走实体 [BgmFloatingFrame]，
 * 玻璃只出现在顶栏与悬浮导航。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi", application = Application::class)
class FloatingShellScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun floatingShell_light() {
        capture(darkTheme = false, path = "floating_shell_light.png")
    }

    @Test
    fun floatingShell_dark() {
        capture(darkTheme = true, path = "floating_shell_dark.png")
    }

    private fun capture(
        darkTheme: Boolean,
        path: String,
    ) {
        System.setProperty("roborazzi.test.record", "true")
        val target =
            File("app/src/test/snapshots/images/$path").takeIf { it.parentFile?.exists() == true }
                ?: File("src/test/snapshots/images/$path")
        target.parentFile?.mkdirs()
        composeTestRule.setContent {
            MiniBgmTheme(
                darkTheme = darkTheme,
                uiStyle = AppUiStyle.Luminous,
            ) {
                FloatingShellPreview()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(target.absolutePath)
    }
}

private data class PreviewScheduleItem(
    val time: String,
    val title: String,
    val episode: String,
    val platform: String,
    val posterMark: String,
)

private val previewScheduleItems =
    listOf(
        PreviewScheduleItem("19:30", "药屋少女的呢喃", "第 24 话 · 已更新", "bilibili", "药"),
        PreviewScheduleItem("20:00", "胆大党 第二季", "第 12 话 · 即将播出", "bilibili", "胆"),
        PreviewScheduleItem("21:00", "SPY×FAMILY 第三季", "第 5 话 · 已更新", "腾讯视频", "S"),
        PreviewScheduleItem("22:30", "葬送的芙莉莲", "第 28 话 · 已更新", "bilibili", "芙"),
        PreviewScheduleItem("23:00", "章鱼哔的原罪", "第 6 话 · 已更新", "bilibili", "章"),
        PreviewScheduleItem("23:30", "为丑女献上花束", "第 7 话 · 即将播出", "腾讯视频", "花"),
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FloatingShellPreview() {
    val totalTopChromeHeight = BgmSpacing.TopBar.height + 56.dp
    BgmFloatingPageScaffold(
        modifier = Modifier.size(width = 411.dp, height = 891.dp),
        topBarMode = BgmTopBarMode.Immersive,
        topScrimHeight = totalTopChromeHeight,
        topScrimSolidHeight = BgmSpacing.TopBar.height,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BgmTopAppBar(
                    mode = BgmTopBarMode.Immersive,
                    title = {
                        Text(
                            text = "时间表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            BgmCircleActionButton(
                                onClick = {},
                                icon = BgmIcons.Search,
                                contentDescription = "搜索",
                            )
                            BgmCircleActionButton(
                                onClick = {},
                                icon = BgmIcons.Assistant,
                                contentDescription = "助手",
                            )
                        }
                    },
                )
                PreviewDateStrip()
            }
        },
        floatingBottomBar = {
            BgmFloatingNavigationBar(
                currentDestination = ScheduleRoute,
                onDestinationSelected = {},
            )
        },
        floatingBottomBarSink = 12.dp,
    ) {
        PreviewTimeline(topPadding = totalTopChromeHeight + 4.dp)
    }
}

@Composable
private fun PreviewDateStrip() {
    val days =
        listOf(
            "周一" to "19",
            "周二" to "20",
            "周三" to "21",
            "周四" to "22",
            "周五" to "23",
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEachIndexed { index, (weekday, day) ->
            val selected = index == 1
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = weekday, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = day,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewFilterRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = "全部 42",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = "在看 8",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "按时间",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewSectionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "今晚更新",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "共 7 部",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewTimeline(topPadding: Dp = 108.dp) {
    val posterBrushes =
        listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.secondaryContainer,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "filter_row") {
            PreviewFilterRow()
        }
        item(key = "section_header") {
            PreviewSectionHeader()
        }
        item(key = "now") {
            PreviewNowLine(time = "20:14")
        }
        itemsIndexed(previewScheduleItems, key = { _, item -> item.title }) { index, item ->
            val posterColors = posterBrushes[index % posterBrushes.size]
            PreviewScheduleCard(
                item = item,
                posterBrush =
                    Brush.linearGradient(
                        listOf(
                            posterColors.first,
                            posterColors.second,
                        ),
                    ),
            )
        }
        item(key = "now_late") {
            PreviewNowLine(time = "21:48")
        }
    }
}

@Composable
private fun PreviewNowLine(time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        )
        Text(
            text = "现在",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PreviewScheduleCard(
    item: PreviewScheduleItem,
    posterBrush: Brush,
) {
    BgmFloatingFrame(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(width = 46.dp, height = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(posterBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.posterMark,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.episode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = item.platform,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "今晚",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
