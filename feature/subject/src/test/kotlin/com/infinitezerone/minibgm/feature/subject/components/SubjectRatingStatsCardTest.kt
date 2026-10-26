package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.Rating
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h850dp")
class SubjectRatingStatsCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sparse_distribution_displays_accurate_score_details_on_click() {
        val sparseRating =
            Rating(
                score = 9.0,
                total = 100,
                rank = 1,
                count = mapOf("9" to 100),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                RatingDistributionSection(rating = sparseRating)
            }
        }

        // 默认状态：展示总体评分、投票人数与好评率
        composeTestRule.onNodeWithText("9.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("(100人评分)").assertIsDisplayed()
        composeTestRule.onNodeWithText("好评率 100%").assertIsDisplayed()

        // 交互验证：点击 9 分（众数项），左侧状态切换为具体分段详情
        composeTestRule.onNodeWithText("9").performClick()
        composeTestRule.onNodeWithText("9分 · 100人 (100.0%)").assertIsDisplayed()

        // 交互验证：点击 1 分（0 票分段），客观显示 0 票，绝不伪造
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("1分 · 0人 (0.0%)").assertIsDisplayed()

        // 再次点击 1 分，取消选中，恢复总评态
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("好评率 100%").assertIsDisplayed()
    }

    @Test
    fun controversy_bimodal_distribution_calculates_correct_high_score_ratio() {
        // 粉黑大战双峰分布：50人打10分，50人打1分
        val bimodalRating =
            Rating(
                score = 5.5,
                total = 100,
                count = mapOf("1" to 50, "10" to 50),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                RatingDistributionSection(rating = bimodalRating)
            }
        }

        composeTestRule.onNodeWithText("5.5").assertIsDisplayed()
        // 好评率（>=8分）应为 50%
        composeTestRule.onNodeWithText("好评率 50%").assertIsDisplayed()

        // 点击 10 分
        composeTestRule.onNodeWithText("10").performClick()
        composeTestRule.onNodeWithText("10分 · 50人 (50.0%)").assertIsDisplayed()
    }
}
