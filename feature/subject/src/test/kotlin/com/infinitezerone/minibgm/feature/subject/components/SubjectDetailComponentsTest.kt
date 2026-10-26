package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.core.testing.data.sampleCharacterList
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.feature.subject.SubjectDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w392dp-h850dp")
class SubjectDetailComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun character_tile_clicking_character_vs_cv_independent_dispatch() {
        var clickedCharacterId: Long? = null
        var clickedActorId: Long? = null

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface {
                    CharactersSection(
                        characters = sampleCharacterList,
                        onCharacterClick = { clickedCharacterId = it },
                        onActorClick = { clickedActorId = it },
                        onPreviewCharacter = {},
                    )
                }
            }
        }

        // 验证角色本体点击
        composeTestRule.onNodeWithText("フリーレン").assertIsDisplayed().performClick()
        assertEquals(3001L, clickedCharacterId)

        // 验证 CV 独立热区点击
        composeTestRule.onNodeWithText("CV: 種﨑敦美").assertIsDisplayed().performClick()
        assertEquals(4001L, clickedActorId)
    }

    @Test
    fun staff_section_multi_member_supports_individual_click_and_dialog_selection() {
        val multiStaff =
            listOf(
                SubjectPerson(
                    id = 7001L,
                    name = "大河内一楼",
                    type = 1,
                    relation = "脚本",
                ),
                SubjectPerson(
                    id = 7002L,
                    name = "花田十辉",
                    type = 1,
                    relation = "脚本",
                ),
                SubjectPerson(
                    id = 7003L,
                    name = "斎藤圭一郎",
                    type = 1,
                    relation = "导演",
                ),
            )

        var clickedPersonId: Long? = null

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface {
                    StaffSection(
                        persons = multiStaff,
                        onPersonClick = { clickedPersonId = it },
                    )
                }
            }
        }

        // 1. 卡片内分离的直接点击热区
        composeTestRule.onNodeWithText("大河内一楼").assertIsDisplayed().performClick()
        assertEquals(7001L, clickedPersonId)

        // 2. 多人提示标识与弹窗选择态
        composeTestRule.onNodeWithText("2人").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("脚本 (2人)").assertIsDisplayed()

        // 3. 在弹窗内点击第二位主创直达其主页
        composeTestRule.onAllNodesWithText("花田十辉")[1].assertIsDisplayed().performClick()
        assertEquals(7002L, clickedPersonId)
    }

    @Test
    fun subject_summary_handles_long_text_without_double_newlines_and_toggles_expansion() {
        // 无 \n\n 双换行的连续长文本
        val singleBlockLongSummary =
            "这是没有双换行的超长单段剧情简介，专门测试简介平滑折叠组件在面对各种排版格式时的健壮性。" +
                "不论文本中是否包含空行切块，只要内容行数或长度超出折叠阈值，折叠机制都能基于行数平滑截断，" +
                "并提供展开与收起切换入口，避免脆弱的正则切块引发文本展示缺失或无法展开的异常。" +
                "即使是整段文字一气呵成、没有任何空行分隔的超长日式轻小说简介或者剧场版长段落剧情提要，" +
                "折叠组件也能够稳定展现四行预览，并在用户点击展开后完整展开全文。"

        val testSubject = sampleSubject.copy(summary = singleBlockLongSummary)
        val uiState = SubjectDetailUiState(isLoading = false)

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                OverviewTabPage(
                    subject = testSubject,
                    displaySubject = testSubject,
                    uiState = uiState,
                    onTagClick = {},
                    onCharacterClick = {},
                    onActorClick = {},
                    onPreviewCharacter = {},
                    onPersonClick = {},
                    onSubjectClick = {},
                )
            }
        }

        // 初始状态下展示展开入口（在 LazyColumn 中滚动至可见）
        val expandNode = composeTestRule.onNodeWithText("展开完整简介")
        expandNode.performScrollTo().assertIsDisplayed().performClick()

        // 展开后切换为收起入口
        val collapseNode = composeTestRule.onNodeWithText("收起简介")
        collapseNode.performScrollTo().assertIsDisplayed().performClick()

        // 再次折叠回初始态
        composeTestRule.onNodeWithText("展开完整简介").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun subject_header_card_removes_episode_rail_and_exposes_clean_actions() {
        var openEpisodePickerCalled = false
        var decrementWatchedCalled = false
        var incrementWatchedCalled = false

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    SubjectHeaderCard(
                        subject = sampleSubject,
                        subjectType = SubjectType.ANIME,
                        totalEpisodes = 28,
                        collection = sampleUserCollection.copy(epStatus = 5),
                        isLoggedIn = true,
                        episodes = sampleEpisodeList,
                        onOpenEpisodePicker = { openEpisodePickerCalled = true },
                        onDecrementWatched = { decrementWatchedCalled = true },
                        onIncrementWatched = { incrementWatchedCalled = true },
                        onPlayNext = {},
                        onShare = {},
                    )
                }
            }
        }

        // 1. 验证横向分集快轨已从头部彻底移除（不会渲染 EP.1 等快轨卡片）
        composeTestRule.onNodeWithText("EP.1").assertDoesNotExist()

        // 2. 验证操作栏选集圆盘独立且可用
        composeTestRule.onNodeWithContentDescription("选集").assertIsDisplayed().performClick()
        assertTrue(openEpisodePickerCalled)

        // 3. 验证 -1 和 +1 纯净打卡微操作圆盘正常响应
        composeTestRule.onNodeWithText("-1").assertIsDisplayed().performClick()
        assertTrue(decrementWatchedCalled)

        composeTestRule.onNodeWithContentDescription("打卡下一集").assertIsDisplayed().performClick()
        assertTrue(incrementWatchedCalled)
    }

    @Test
    fun tags_section_unifies_section_toggle_and_removes_end_fake_chip() {
        val testTags =
            (1..15).map {
                com.infinitezerone.minibgm.core.model
                    .Tag(name = "标签$it", count = it * 10)
            }

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface {
                    TagsSection(
                        tags = testTags,
                        onTagClick = {},
                    )
                }
            }
        }

        // 1. 验证标题栏右侧存在统一的小节展开操作全部 (15)
        composeTestRule.onNodeWithText("全部 (15)").assertIsDisplayed().performClick()

        // 2. 展开后收起操作显示，且第 15 个标签可见
        composeTestRule.onNodeWithText("收起").assertIsDisplayed()
        composeTestRule.onNodeWithText("#标签15").assertIsDisplayed()

        // 3. 再次点击收起
        composeTestRule.onNodeWithText("收起").performClick()
        composeTestRule.onNodeWithText("全部 (15)").assertIsDisplayed()
    }

    @Test
    fun characters_section_supports_role_category_filtering_and_priority_sorting() {
        val testCharacters =
            listOf(
                com.infinitezerone.minibgm.core.model
                    .SubjectCharacter(id = 1L, name = "配角前置", relation = "配角"),
                com.infinitezerone.minibgm.core.model
                    .SubjectCharacter(id = 2L, name = "主角A", relation = "主角"),
                com.infinitezerone.minibgm.core.model
                    .SubjectCharacter(id = 3L, name = "客串C", relation = "闲角"),
                com.infinitezerone.minibgm.core.model
                    .SubjectCharacter(id = 4L, name = "主角B", roleName = "主角"),
                com.infinitezerone.minibgm.core.model
                    .SubjectCharacter(id = 5L, name = "配角后置", roleName = "配角"),
            )

        composeTestRule.setContent {
            MiniBgmTheme(darkTheme = false) {
                Surface {
                    CharactersSection(
                        characters = testCharacters,
                        onCharacterClick = {},
                        onActorClick = {},
                        onPreviewCharacter = {},
                    )
                }
            }
        }

        // 1. 首屏收起态断言：核心主角必须优先被排序展示（主角A 和 主角B），即使原数据流中配角排在前面
        composeTestRule.onNodeWithText("主角A").assertIsDisplayed()
        composeTestRule.onNodeWithText("主角B").assertIsDisplayed()

        // 2. 点击统一展开全部控制器
        composeTestRule.onNodeWithText("全部 5 位").performClick()

        // 3. 展开后应显示各角色类型分类 FilterChip
        composeTestRule.onNodeWithText("主角 (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("配角 (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("客串 (1)").assertIsDisplayed()

        // 4. 切换至主角分类
        composeTestRule.onNodeWithText("主角 (2)").performClick()
        composeTestRule.onNodeWithText("主角A").assertIsDisplayed()
        composeTestRule.onNodeWithText("主角B").assertIsDisplayed()
        composeTestRule.onNodeWithText("客串C").assertDoesNotExist()

        // 5. 切换至客串分类
        composeTestRule.onNodeWithText("客串 (1)").performClick()
        composeTestRule.onNodeWithText("客串C").assertIsDisplayed()
        composeTestRule.onNodeWithText("主角A").assertDoesNotExist()
    }
}
