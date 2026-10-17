package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourceActionCard
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesSheetHeader
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.SourceEmptyStateCard

/**
 * 统一播放线路选择面板（竖屏底部弹层）。
 *
 * 容器直接复用设计系统播放源底栏 [BgmSourcesBottomSheet]（与详情页统一抽屉同构）：
 * 收起动效、导航栏 padding、头部（标题/条数/关闭）与滚动容器都由设计系统提供，
 * feature 侧只负责「线路卡片 + 管理规则 + AI 找源」这段内容本身。
 */
@Composable
internal fun PlayerSourcesBottomSheet(
    sources: List<PlayerSourceTab>,
    selectedIndex: Int,
    /** 在播源标识：与 [selectedIndex] 分离，用于区分「正在播放」与「已选中待生效」 */
    playingSourceId: String?,
    sourceFailureCounts: Map<String, Int>,
    isResolvingSource: Boolean,
    resolveAttempt: Int,
    resolveAttemptTotal: Int,
    matchedVodName: String?,
    onSelectSource: (Int) -> Unit,
    onManageRules: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestOpenSources: (() -> Unit)? = null,
) {
    BgmSourcesBottomSheet(
        title = stringResource(R.string.feature_subject_player_sources),
        subtitle = playerSourcesSummary(sources),
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) { runAfterDismiss ->
        PlayerSourcesListContent(
            sources = sources,
            selectedIndex = selectedIndex,
            playingSourceId = playingSourceId,
            sourceFailureCounts = sourceFailureCounts,
            isResolvingSource = isResolvingSource,
            resolveAttempt = resolveAttempt,
            resolveAttemptTotal = resolveAttemptTotal,
            matchedVodName = matchedVodName,
            // 切源/管理/找源都先播完收起动效再落地，避免面板半截闪断与「关掉又立刻弹别的」的割裂
            onSelectSource = { index -> runAfterDismiss { onSelectSource(index) } },
            onManageRules = onManageRules?.let { manage -> { runAfterDismiss(manage) } },
            onRequestOpenSources = onRequestOpenSources?.let { open -> { runAfterDismiss(open) } },
        )
    }
}

/**
 * 横屏右侧全高选源抽屉：容器形态（主题色 + 左侧圆角 + elevation）与选集抽屉同源，
 * 头部复用设计系统 [BgmSourcesSheetHeader]，内容与竖屏弹层共用 [PlayerSourcesListContent]。
 */
@Composable
internal fun PlayerSourcesDrawer(
    sources: List<PlayerSourceTab>,
    selectedIndex: Int,
    /** 在播源标识：与 [selectedIndex] 分离，用于区分「正在播放」与「已选中待生效」 */
    playingSourceId: String?,
    sourceFailureCounts: Map<String, Int>,
    isResolvingSource: Boolean,
    resolveAttempt: Int,
    resolveAttemptTotal: Int,
    matchedVodName: String?,
    onSelectSource: (Int) -> Unit,
    onManageRules: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestOpenSources: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.width(360.dp).fillMaxHeight(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            BgmSourcesSheetHeader(
                title = stringResource(R.string.feature_subject_player_sources),
                subtitle = playerSourcesSummary(sources),
                onClose = onClose,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Spacer(modifier = Modifier.height(14.dp))

            PlayerSourcesListContent(
                sources = sources,
                selectedIndex = selectedIndex,
                playingSourceId = playingSourceId,
                sourceFailureCounts = sourceFailureCounts,
                isResolvingSource = isResolvingSource,
                resolveAttempt = resolveAttempt,
                resolveAttemptTotal = resolveAttemptTotal,
                matchedVodName = matchedVodName,
                onSelectSource = onSelectSource,
                onManageRules = onManageRules,
                onRequestOpenSources = onRequestOpenSources,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * 面板副标题：有线路时报条数，空列表时留空（空态本身由列表内的统一空态卡片表达）。
 */
@Composable
private fun playerSourcesSummary(sources: List<PlayerSourceTab>): String =
    if (sources.isEmpty()) {
        ""
    } else {
        stringResource(R.string.feature_subject_player_sources_count_format, sources.size)
    }

/**
 * 面板主体：线路卡片列表 + 「播放源管理」/「AI 找源」统一动作卡片。
 *
 * 竖屏弹层与横屏抽屉共用这一份内容，任何一侧都不再手搓专属样式；
 * 见底栏空态规范，配置引导与次级入口一律走 [BgmSourceActionCard]，空态走 [SourceEmptyStateCard]。
 */
@Composable
internal fun PlayerSourcesListContent(
    sources: List<PlayerSourceTab>,
    selectedIndex: Int,
    /** 在播源标识：与 [selectedIndex] 分离，用于区分「正在播放」与「已选中待生效」 */
    playingSourceId: String?,
    sourceFailureCounts: Map<String, Int>,
    isResolvingSource: Boolean,
    resolveAttempt: Int,
    resolveAttemptTotal: Int,
    matchedVodName: String?,
    onSelectSource: (Int) -> Unit,
    onManageRules: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRequestOpenSources: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isEmpty()) {
            // 只配了 BT 规则（或没配任何规则）时线路列表本来就是空的：
            // 必须给出可达的下一步，而不是留一个空面板让用户以为功能坏了
            SourceEmptyStateCard(
                title = stringResource(R.string.feature_subject_source_no_online_routes),
                subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                iconVector = BgmIcons.Settings,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.feature_subject_player_sources_sheet_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sources.forEachIndexed { index, source ->
                PlayerSourceActionRow(
                    source = source,
                    index = index,
                    isSelected = index == selectedIndex,
                    // 「正在播放」只认在播源：切源解析窗口内选中的是 B、在播的仍是 A，
                    // 此时把 B 标成「正在使用当前线路」就是在骗用户（解析失败时尤其明显）
                    isPlaying = source.id == playingSourceId,
                    failureCount = sourceFailureCounts[source.id] ?: 0,
                    isResolvingSource = isResolvingSource,
                    resolveAttempt = resolveAttempt,
                    resolveAttemptTotal = resolveAttemptTotal,
                    matchedVodName = matchedVodName,
                    onClick = { onSelectSource(index) },
                )
            }
        }

        if (onManageRules != null) {
            BgmSourceActionCard(
                title = stringResource(R.string.feature_subject_player_manage_sources),
                subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                iconVector = BgmIcons.Settings,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onManageRules,
            )
        }

        if (onRequestOpenSources != null) {
            BgmSourceActionCard(
                title = stringResource(R.string.feature_subject_source_ai_search_desc),
                subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                iconVector = BgmIcons.AutoAwesome,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onRequestOpenSources,
            )
        }
    }
}

/**
 * 单条线路卡片：选中态、在播态、解析进度与屡试屡败提示全部并入副标题，
 * 不再另起一套私有的徽标/图标行。
 */
@Composable
private fun PlayerSourceActionRow(
    source: PlayerSourceTab,
    index: Int,
    isSelected: Boolean,
    isPlaying: Boolean,
    failureCount: Int,
    isResolvingSource: Boolean,
    resolveAttempt: Int,
    resolveAttemptTotal: Int,
    matchedVodName: String?,
    onClick: () -> Unit,
) {
    val rawName = source.nameRes?.let { stringResource(it) } ?: source.name
    val displayLabel = formatSourceDisplayName(rawName, index, source.isDirect)
    val status =
        when {
            isSelected && isResolvingSource ->
                if (resolveAttemptTotal > 0) {
                    stringResource(
                        R.string.feature_subject_player_source_resolving_format,
                        resolveAttempt,
                        resolveAttemptTotal,
                    )
                } else {
                    stringResource(R.string.feature_subject_player_unresolved_stream)
                }
            isPlaying && !matchedVodName.isNullOrBlank() ->
                stringResource(R.string.feature_subject_player_source_matched_format, matchedVodName)
            isPlaying -> stringResource(R.string.feature_subject_player_source_in_use)
            isSelected -> stringResource(R.string.feature_subject_player_source_selected_pending)
            // 无状态可报时退回规则自身的说明（如「MacCMS · 直连」），比留空更有用
            else -> source.rule?.description?.takeIf { it.isNotBlank() }
        }
    val subtitle =
        listOfNotNull(
            status,
            if (failureCount > 0) {
                stringResource(R.string.feature_subject_player_failures_count, failureCount)
            } else {
                null
            },
        ).joinToString(" · ")

    BgmSourceActionCard(
        title = displayLabel,
        subtitle = subtitle,
        iconVector = sourceIcon(source),
        iconTint =
            when {
                isSelected -> MaterialTheme.colorScheme.primary
                failureCount > 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        trailingContent = {
            Icon(
                imageVector = if (isSelected) BgmIcons.Check else BgmIcons.KeyboardArrowRight,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}
