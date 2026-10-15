package com.infinitezerone.minibgm.feature.user

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.RuleParserType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SAMPLE_SUB_URL =
    "https://example.com/subscriptions/minibgm_rules.json"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaybackRulesScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleRules =
        listOf(
            PlaybackSourceRule(
                id = "rule-1",
                name = "量子资源 (TVBox/MacCMS)",
                urlTemplate = "https://cj.lziapi.com/api.php/provide/vod?ac=detail&wd={title}",
                isEnabled = true,
                description = "TVBox 主力切片采集源，支持绝大部分主流新番与国漫，秒播直出",
                kind = PlaybackRuleKind.SOURCE,
                parserType = RuleParserType.MACCMS,
            ),
            PlaybackSourceRule(
                id = "rule-2",
                name = "蜜柑计划 (Mikan RSS)",
                urlTemplate = "https://mikanani.me/RSS/Search?searchstr={title}",
                isEnabled = true,
                description = "新番字幕组 BT 种子发布站，按分集智能归类",
                kind = PlaybackRuleKind.TORRENT,
                parserType = RuleParserType.RSS_TORRENT,
            ),
            PlaybackSourceRule(
                id = "rule-3",
                name = "Nyaa (全球动漫 BT 枢纽)",
                urlTemplate = "https://nyaa.si/?page=rss&q={title}",
                isEnabled = false,
                description = "全球最大的动漫/日系影视 BT 发布枢纽，做种 Peer 最多",
                kind = PlaybackRuleKind.TORRENT,
                parserType = RuleParserType.RSS_TORRENT,
            ),
        )

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h900dp")
    fun playbackRulesScreen_light() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                RulesTabContent(
                    rules = sampleRules,
                    subscriptionUrl = SAMPLE_SUB_URL,
                    isRefreshingSubscription = false,
                    onRefreshSubscription = {},
                    onClearSubscription = {},
                    onOpenSubscriptionImport = {},
                    onOpenSiteProbe = {},
                    onToggleRule = { _, _ -> },
                    onDeleteRule = {},
                    overlayHostState = OverlayHostState(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/playback_rules_screen_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h900dp")
    fun playbackRulesScreen_dark() {
        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = true) {
                RulesTabContent(
                    rules = sampleRules,
                    subscriptionUrl = SAMPLE_SUB_URL,
                    isRefreshingSubscription = false,
                    onRefreshSubscription = {},
                    onClearSubscription = {},
                    onOpenSubscriptionImport = {},
                    onOpenSiteProbe = {},
                    onToggleRule = { _, _ -> },
                    onDeleteRule = {},
                    overlayHostState = OverlayHostState(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/images/playback_rules_screen_dark.png")
    }
}
