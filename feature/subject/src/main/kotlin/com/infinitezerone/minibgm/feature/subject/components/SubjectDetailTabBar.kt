package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.model.SubjectType
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.SubjectDetailTab
import kotlin.math.absoluteValue

/** 二级 Tab 栏高度常量。 */
internal val SubjectTabBarHeight = 48.dp

/** 获取 Tab 标题文案。 */
@Composable
internal fun getTabLabel(
    tab: SubjectDetailTab,
    subjectType: SubjectType,
): String =
    when (tab) {
        SubjectDetailTab.OVERVIEW -> stringResource(R.string.feature_subject_tab_overview)
        SubjectDetailTab.EPISODES ->
            when (subjectType) {
                SubjectType.BOOK -> stringResource(R.string.feature_subject_tab_volumes)
                SubjectType.MUSIC -> stringResource(R.string.feature_subject_tab_tracks)
                SubjectType.GAME -> stringResource(R.string.feature_subject_tab_chapters)
                SubjectType.ANIME, SubjectType.REAL -> stringResource(R.string.feature_subject_tab_episodes)
            }
        SubjectDetailTab.COMMUNITY -> stringResource(R.string.feature_subject_tab_community)
    }

/**
 * 吸顶二级 Tab 栏（概览/分集/讨论）：
 * 1. 背景随收合进度从全透渐变至 surface 实色，并在吸顶时显示微细分割线；
 * 2. 指示条胶囊严格以 `(maxWidth - 32.dp) / tabCount` + `16.dp` 居中对齐；
 * 3. 独立组件，禁止与滚动容器耦合。
 */
@Composable
internal fun SubjectDetailTabBar(
    selectedTab: SubjectDetailTab,
    pagerState: PagerState,
    collapseRatio: Float,
    subjectType: SubjectType,
    totalEpisodes: Int,
    currentEpisodeCount: Int,
    commentTotal: Int,
    onSelectTab: (SubjectDetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabBgAlpha = (collapseRatio * 1.25f).coerceIn(0f, 1f)
    val tabContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = tabBgAlpha)

    Surface(
        color = tabContainerColor,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SubjectTabBarHeight),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val tabCount = SubjectDetailTab.entries.size
            val contentWidth = maxWidth - 32.dp
            val tabWidth = contentWidth / tabCount
            val pageOffset =
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, (tabCount - 1).toFloat())
            val indicatorWidth = 28.dp
            val indicatorCenterX = 16.dp + tabWidth * (pageOffset + 0.5f)
            val indicatorLeft = indicatorCenterX - (indicatorWidth / 2f)

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectDetailTab.entries.forEachIndexed { index, tab ->
                    val tabLabel = getTabLabel(tab, subjectType)
                    val badgeCount =
                        when (tab) {
                            SubjectDetailTab.EPISODES ->
                                (if (totalEpisodes > 0) totalEpisodes else currentEpisodeCount).takeIf { it > 0 }
                            SubjectDetailTab.COMMUNITY -> commentTotal.takeIf { it > 0 }
                            else -> null
                        }
                    val tabDistance = (pageOffset - index).absoluteValue.coerceIn(0f, 1f)
                    val isSelected = tabDistance < 0.5f

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelectTab(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = tabLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    },
                            )
                            if (badgeCount != null) {
                                Text(
                                    text = if (badgeCount > 999) "999+" else badgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // 平滑滑动的指示胶囊（精确居中对齐文字）
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 6.dp)
                        .offset(x = indicatorLeft)
                        .width(indicatorWidth)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )

            // 吸顶时微细分割线，阻隔下方穿行内容
            if (collapseRatio > 0.05f) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * collapseRatio),
                )
            }
        }
    }
}
