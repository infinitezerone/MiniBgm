package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 统一播放线路选择抽屉：
 * 展示全部可用线路、当前选中状态、解析状态与失败提示，
 * 右上角提供独立的「管理规则」操作入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
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
        )
    }
}

/**
 * 横屏右侧全高选源抽屉（与选集抽屉对齐风格，避免横屏底部向上遮挡画面）。
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
        modifier = modifier.width(360.dp).fillMaxHeight(),
        color = Color.Black.copy(alpha = 0.92f),
        contentColor = Color.White,
    ) {
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
            onClose = onClose,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

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
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题栏：左侧标题与线路数，右侧独立的管理规则操作入口 (+ 可选关闭按钮)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_sources),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sources.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_sources_count_format, sources.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (onManageRules != null) {
                TextButton(
                    onClick = onManageRules,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.feature_subject_player_manage_sources),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = BgmIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // 说明文案
        Text(
            text = stringResource(R.string.feature_subject_player_sources_sheet_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (sources.isEmpty()) {
            // 只配了 BT 规则（或没配任何规则）时线路列表本来就是空的：
            // 必须给出可达的下一步，而不是留一个空抽屉让用户以为功能坏了
            Text(
                text = stringResource(R.string.feature_subject_player_sources_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 线路垂直列表
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources, key = { _, s -> s.id }) { index, source ->
                val isSelected = index == selectedIndex
                // 「正在播放」只认在播源：切源解析窗口内选中的是 B、在播的仍是 A，
                // 此时把 B 标成「正在使用当前线路」就是在骗用户（解析失败时尤其明显）
                val isPlaying = source.id == playingSourceId
                val failureCount = sourceFailureCounts[source.id] ?: 0
                val isFailing = failureCount > 0 && !isSelected
                val rawName = source.nameRes?.let { stringResource(it) } ?: source.name
                val displayLabel = formatSourceDisplayName(rawName, index, source.isDirect)

                Surface(
                    onClick = { onSelectSource(index) },
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    contentColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (isFailing) 0.6f else 1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = sourceIcon(source),
                            contentDescription = null,
                            tint =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(20.dp),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // 类型徽标（自动防叠词：若名称已有对应词则不重复贴徽标）
                                val badge = sourceBadgeText(source)
                                if (badge != null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    },
                                                ).padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    }
                                }

                                SourceFailureHint(failureCount)
                            }

                            // 状态副标题（解析进度或匹配条目）
                            if (isSelected && isResolvingSource) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text =
                                            if (resolveAttemptTotal > 0) {
                                                stringResource(
                                                    R.string.feature_subject_player_source_resolving_format,
                                                    resolveAttempt,
                                                    resolveAttemptTotal,
                                                )
                                            } else {
                                                stringResource(R.string.feature_subject_player_unresolved_stream)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else if (isPlaying && !matchedVodName.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.feature_subject_player_source_matched_format, matchedVodName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (isPlaying) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.feature_subject_player_source_in_use),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else if (isSelected) {
                                // 选中但尚未在播（理论上只出现在解析窗口的边界态）：如实说明，不冒充"正在使用"
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.feature_subject_player_source_selected_pending),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = BgmIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (onRequestOpenSources != null) {
                item {
                    Surface(
                        onClick = onRequestOpenSources,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.feature_subject_source_ai_search),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.feature_subject_source_ai_search_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = BgmIcons.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
