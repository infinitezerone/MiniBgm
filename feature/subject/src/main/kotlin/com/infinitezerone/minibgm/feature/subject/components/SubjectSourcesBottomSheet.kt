package com.infinitezerone.minibgm.feature.subject.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.data.download.TorrentIntentDispatcher
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourceActionCard
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesSectionLabel
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.OverlayHostState
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.designsystem.theme.WishOrange
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.onStatusDoingContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusAiringContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusCollectContainerColor
import com.infinitezerone.minibgm.core.designsystem.theme.statusDoingContainerColor
import com.infinitezerone.minibgm.core.model.DownloadStorageUsage
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.model.TorrentStreamState
import com.infinitezerone.minibgm.core.model.TorrentTask
import com.infinitezerone.minibgm.core.model.formatTransferSpeed
import com.infinitezerone.minibgm.core.model.magnetInfoHash
import com.infinitezerone.minibgm.core.model.torrentEpisodeKey
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.StreamingAppLauncher
import com.infinitezerone.minibgm.core.navigation.launchExternalPlayer
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

private data class SourceEpisodeStatusDisplay(
    val text: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector?,
)

/**
 * 统一的「播放来源」BottomSheet，条目级与分集级共用同一组件（[episode] 为 null 即条目级）：
 * - 条目级：一体化播放器、AI 找源、播放源管理、条目级外部跳转；
 * - 分集级：逐条渲染规则源（应用内嗅探或外开）、
 *   原位切集导航、分集打卡微操作、分集化搜索关键词、外部播放器（mpv / VLC）与失败归因提示。
 */
@Composable
fun SubjectSourcesBottomSheet(
    subject: Subject,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    episode: Episode? = null,
    isWatched: Boolean = false,
    isNextToWatch: Boolean = false,
    onToggleWatched: (() -> Unit)? = null,
    onBatchMark: (() -> Unit)? = null,
    onSelectEpisodeForDetail: (() -> Unit)? = null,
    onNavigateToPrevious: (() -> Unit)? = null,
    onNavigateToNext: (() -> Unit)? = null,
    currentIndex: Int = -1,
    totalCount: Int = -1,
    mikanId: String? = null,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onAiSourceSearch: (() -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    playbackRules: List<PlaybackSourceRule> = emptyList(),
    failedSourceReasons: Map<String, String> = emptyMap(),
    torrentResources: List<TorrentMediaResource> = emptyList(),
    isTorrentLoading: Boolean = false,
    onRequestNotificationPermission: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val displayName = subject.displayName
    val sheetTitle = displayName
    val sheetSubtitle =
        remember(episode) {
            if (episode != null) {
                episode.guideLabel
            } else {
                null
            }
        }

    val enabledRules = remember(playbackRules) { playbackRules.filter { it.isEnabled } }
    val torrentRules = remember(enabledRules) { enabledRules.filter { it.kind == PlaybackRuleKind.TORRENT } }
    val onlineRules = remember(enabledRules) { enabledRules.filter { it.kind != PlaybackRuleKind.TORRENT } }

    val initialTab =
        remember(onlineRules, torrentRules) {
            if (onlineRules.isEmpty() && torrentRules.isNotEmpty()) SourceSheetTab.TORRENT else SourceSheetTab.ONLINE
        }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

    val firstHealthyRule =
        remember(onlineRules, failedSourceReasons) {
            onlineRules.firstOrNull { rule ->
                failedSourceReasons.none { (url, _) -> url.contains(rule.id) }
            } ?: onlineRules.firstOrNull()
        }

    val epLabel = remember(episode) { episode?.guideLabel.orEmpty() }

    val bilibiliTarget =
        remember(displayName, epLabel, episode) {
            if (episode != null) {
                StreamingIntentResolver.buildBilibiliSearchTarget("$displayName $epLabel")
            } else {
                StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
            }
        }

    // 外部播放器直链候选：解析为媒体直链的在线播放规则
    val externalPlayerStreamUrl =
        remember(onlineRules, displayName, episode, subject.id) {
            if (episode == null) return@remember null
            onlineRules.firstNotNullOfOrNull { rule ->
                if (rule.kind == PlaybackRuleKind.SOURCE) return@firstNotNullOfOrNull null
                val epStr = if (episode.isMain) episode.formattedNumber else episode.episodeInt.toString()
                val resolvedUrl =
                    rule.resolveUrl(
                        title = displayName,
                        ep = epStr,
                        subjectId = subject.id,
                        episodeId = episode.id,
                    )
                resolvedUrl.takeIf {
                    StreamingIntentResolver.isHttpStreamUrl(it) && isLikelyMediaStream(resolvedUrl, rule.description)
                }
            }
        }
    val externalPlayerTargets =
        remember(externalPlayerStreamUrl) {
            externalPlayerStreamUrl?.let { StreamingIntentResolver.buildExternalPlayerTargets(it) }.orEmpty()
        }
    val installedExternalPlayerPackages =
        remember(context, externalPlayerTargets) {
            externalPlayerTargets
                .filter { StreamingAppLauncher.isAppInstalled(context, it.packageName) }
                .mapTo(mutableSetOf()) { it.packageName }
        }

    val isFuture = remember(episode?.airdate) { episode?.let { isEpisodeFutureAir(it) } ?: false }

    BgmSourcesBottomSheet(
        title = sheetTitle,
        coverUrl = subject.images?.bestImage.orEmpty(),
        subtitle = sheetSubtitle ?: stringResource(R.string.feature_subject_source_choose_source),
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) { runAfterDismiss ->
        // 0. 🌟 分集原位信息与打卡微操作区（单层扁平化呈现，彻底消灭弹窗跳弹窗）
        if (episode != null) {
            // 原位切集导航
            if (onNavigateToPrevious != null || onNavigateToNext != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalButton(
                        onClick = { onNavigateToPrevious?.invoke() },
                        enabled = onNavigateToPrevious != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = BgmShapes.small,
                    ) {
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.feature_subject_ep_previous),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.feature_subject_ep_previous),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Text(
                        text =
                            if (currentIndex >= 0 && totalCount > 0) {
                                "${currentIndex + 1} / $totalCount"
                            } else {
                                epLabel
                            },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FilledTonalButton(
                        onClick = { onNavigateToNext?.invoke() },
                        enabled = onNavigateToNext != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = BgmShapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_ep_next),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.feature_subject_ep_next),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // 分集主标题与状态标签
            val primaryTitle = episode.nameCn.ifBlank { episode.name.ifBlank { epLabel } }
            val secondaryTitle = if (episode.name.isNotBlank() && episode.name != primaryTitle) episode.name else ""

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val fullHeading = if (primaryTitle != epLabel) "$epLabel · $primaryTitle" else epLabel
                    Text(
                        text = fullHeading,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val status =
                        when {
                            isWatched ->
                                SourceEpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_ep_status_watched),
                                    containerColor = statusCollectContainerColor(),
                                    contentColor = onStatusCollectContainerColor(),
                                    icon = BgmIcons.Check,
                                )
                            isFuture ->
                                SourceEpisodeStatusDisplay(
                                    text =
                                        if (episode.airdate.isNotBlank()) {
                                            stringResource(R.string.feature_subject_player_airdate, episode.airdate)
                                        } else {
                                            stringResource(R.string.feature_subject_episodes_status_to_watch)
                                        },
                                    containerColor = statusAiringContainerColor(),
                                    contentColor = onStatusAiringContainerColor(),
                                    icon = BgmIcons.Schedule,
                                )
                            isNextToWatch ->
                                SourceEpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_episodes_status_to_watch),
                                    containerColor = statusDoingContainerColor(),
                                    contentColor = onStatusDoingContainerColor(),
                                    icon = BgmIcons.Play,
                                )
                            else ->
                                SourceEpisodeStatusDisplay(
                                    text = stringResource(R.string.feature_subject_ep_status_unwatched),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    icon = null,
                                )
                        }

                    Surface(
                        shape = BgmShapes.small,
                        color = status.containerColor,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            if (status.icon != null) {
                                Icon(
                                    imageVector = status.icon,
                                    contentDescription = null,
                                    tint = status.contentColor,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Text(
                                text = status.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = status.contentColor,
                            )
                        }
                    }
                }

                if (secondaryTitle.isNotBlank()) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 行内元信息：放送时间 · 时长 · 吐槽讨论直达链接
                val metaItems =
                    buildList {
                        if (episode.airdate.isNotBlank()) add(episode.airdate)
                        if (episode.duration.isNotBlank()) add(episode.duration)
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (metaItems.isNotEmpty()) {
                        Text(
                            text = metaItems.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier =
                            Modifier
                                .clip(BgmShapes.small)
                                .clickable {
                                    runAfterDismiss {
                                        onSelectEpisodeForDetail?.invoke()
                                    }
                                }.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = if (episode.comment >= 50) BgmIcons.LocalFireDepartment else BgmIcons.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (episode.comment >= 50) WishOrange else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text =
                                if (episode.comment > 0) {
                                    stringResource(R.string.feature_subject_ep_comments_total, episode.comment) + " ›"
                                } else {
                                    stringResource(R.string.feature_subject_ep_quick_no_comments) + " ›"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (episode.comment >= 50) WishOrange else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 剧情梗概（折叠卡片）
            if (episode.desc.isNotBlank()) {
                var isDescExpanded by rememberSaveable(episode.id) { mutableStateOf(false) }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(BgmShapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { isDescExpanded = !isDescExpanded }
                            .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.feature_subject_ep_synopsis),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = if (isDescExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = episode.desc.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                }
            }

            // 打卡操作行（显式 contentPadding，彻底杜绝文字截断）
            if (onToggleWatched != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { onToggleWatched() },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = BgmShapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = if (isWatched) BgmIcons.Check else BgmIcons.CheckBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                if (isWatched) {
                                    stringResource(R.string.feature_subject_ep_unwatch)
                                } else {
                                    stringResource(R.string.feature_subject_ep_mark_watched)
                                },
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (!isWatched && onBatchMark != null && episode.isMain && episode.episodeInt > 1) {
                        OutlinedButton(
                            onClick = { onBatchMark() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = BgmShapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.FormatListNumbered,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.feature_subject_ep_watch_to_here),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        // 1. 🌟 Hero 主起播区 (一键即播) / 未播分集状态提示
        if (isFuture && episode != null) {
            Surface(
                shape = BgmShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = BgmIcons.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    val unAiredText =
                        if (episode.airdate.isNotBlank()) {
                            stringResource(
                                R.string.feature_subject_ep_not_aired_yet,
                                episode.formattedNumber,
                                episode.airdate,
                            )
                        } else {
                            stringResource(
                                R.string.feature_subject_ep_not_aired_no_date,
                                episode.formattedNumber,
                            )
                        }
                    Text(
                        text = unAiredText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else if (onInternalPlayClick != null) {
            HeroPlayCard(
                title =
                    if (episode != null) {
                        stringResource(R.string.feature_subject_source_hero_play_episode, epLabel)
                    } else {
                        stringResource(R.string.feature_subject_source_integrated_player)
                    },
                subtitle =
                    if (episode != null) {
                        if (firstHealthyRule != null) {
                            stringResource(R.string.feature_subject_source_hero_preferred_format, firstHealthyRule.name)
                        } else {
                            stringResource(R.string.feature_subject_source_play_immediately_desc)
                        }
                    } else {
                        stringResource(R.string.feature_subject_source_player_features)
                    },
                onClick = {
                    runAfterDismiss {
                        val route =
                            if (episode != null) {
                                PlayerRoute(
                                    subjectId = subject.id,
                                    episodeId = episode.id,
                                    streamUrl = "",
                                    episodeName = episode.nameCn.ifBlank { episode.name },
                                    subjectName = displayName,
                                    episodeSort = episode.episodeNumber,
                                    episodeType = episode.type,
                                )
                            } else {
                                PlayerRoute(
                                    subjectId = subject.id,
                                    episodeId = 0L,
                                    subjectName = displayName,
                                )
                            }
                        onInternalPlayClick(route)
                    }
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 2. 🗂️ 场景分段标签栏
        SourceTabsRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onlineCount = onlineRules.size,
            torrentCount = torrentResources.size,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 3. 📄 对应 Tab 下的纯净内容
        when (selectedTab) {
            SourceSheetTab.ONLINE -> {
                if (episode != null) {
                    if (onlineRules.isNotEmpty()) {
                        BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_online_routes))
                        for (rule in onlineRules) {
                            val epStr = if (episode.isMain) episode.formattedNumber else episode.episodeInt.toString()
                            val resolvedUrl =
                                remember(rule, displayName, epStr, subject.id, episode.id) {
                                    rule.resolveUrl(
                                        title = displayName,
                                        ep = epStr,
                                        subjectId = subject.id,
                                        episodeId = episode.id,
                                    )
                                }
                            val isMedia =
                                remember(resolvedUrl, rule.description) {
                                    isLikelyMediaStream(resolvedUrl, rule.description)
                                }
                            val ruleFailure = failedSourceReasons[resolvedUrl]
                            BgmSourceActionCard(
                                title = rule.name,
                                subtitle =
                                    playbackSourceSubtitle(
                                        rule.description.ifBlank {
                                            if (onInternalPlayClick != null || isMedia) {
                                                stringResource(R.string.feature_subject_source_internal_sniff)
                                            } else {
                                                stringResource(R.string.feature_subject_source_open_link)
                                            }
                                        },
                                        ruleFailure,
                                    ),
                                iconVector =
                                    if (onInternalPlayClick != null || isMedia) {
                                        BgmIcons.PlayCircle
                                    } else {
                                        BgmIcons.OpenInNew
                                    },
                                iconTint =
                                    when {
                                        ruleFailure != null -> MaterialTheme.colorScheme.error
                                        onInternalPlayClick != null || isMedia -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                trailingContent = {
                                    val trailingIcon =
                                        if (onInternalPlayClick != null || isMedia) {
                                            BgmIcons.KeyboardArrowRight
                                        } else {
                                            BgmIcons.OpenInNew
                                        }
                                    Icon(
                                        imageVector = trailingIcon,
                                        contentDescription =
                                            if (onInternalPlayClick != null || isMedia) {
                                                stringResource(R.string.feature_subject_source_action_play)
                                            } else {
                                                stringResource(R.string.feature_subject_source_action_open)
                                            },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    runAfterDismiss {
                                        if (onInternalPlayClick != null) {
                                            val route =
                                                PlayerRoute(
                                                    subjectId = subject.id,
                                                    episodeId = episode.id,
                                                    streamUrl = if (isMedia) resolvedUrl else "",
                                                    episodeName = episode.nameCn.ifBlank { episode.name },
                                                    subjectName = displayName,
                                                    episodeSort = episode.episodeNumber,
                                                    episodeType = episode.type,
                                                    initialRuleId = rule.id,
                                                )
                                            onInternalPlayClick(route)
                                        } else {
                                            onOpenUrl(resolvedUrl)
                                        }
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.feature_subject_source_no_online_routes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
                if (onManageRules != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BgmSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_manage),
                        subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                        iconVector = BgmIcons.Settings,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            runAfterDismiss {
                                onManageRules()
                            }
                        },
                    )
                }
                if (onAiSourceSearch != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BgmSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_ai_search_desc),
                        subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                        iconVector = BgmIcons.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { runAfterDismiss(onAiSourceSearch) },
                    )
                }
            }
            SourceSheetTab.TORRENT -> {
                TorrentMediaResourcesSection(
                    isTorrentLoading = isTorrentLoading,
                    torrentResources = torrentResources,
                    hasTorrentRules = torrentRules.isNotEmpty(),
                    context = context,
                    subject = subject,
                    episode = episode,
                    onInternalPlayClick = onInternalPlayClick,
                    onManageRules = onManageRules,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onDismissAndRun = runAfterDismiss,
                )
            }
            SourceSheetTab.EXTERNAL -> {
                BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_bilibili),
                    subtitle =
                        if (episode != null) {
                            stringResource(R.string.feature_subject_source_bilibili_ep_desc)
                        } else {
                            stringResource(R.string.feature_subject_source_bilibili_desc)
                        },
                    iconVector = BgmIcons.Tv,
                    onClick = {
                        runAfterDismiss {
                            onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                        }
                    },
                )
                if (episode != null) {
                    for (target in externalPlayerTargets) {
                        val installed = target.packageName in installedExternalPlayerPackages
                        Spacer(modifier = Modifier.height(4.dp))
                        BgmSourceActionCard(
                            title = stringResource(R.string.feature_subject_source_open_with_app, target.appName),
                            subtitle =
                                if (installed) {
                                    stringResource(R.string.feature_subject_source_open_with_app_hint, target.appName)
                                } else {
                                    stringResource(R.string.feature_subject_source_app_not_detected, target.appName)
                                },
                            iconVector = BgmIcons.PlayCircle,
                            iconTint =
                                if (installed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            onClick = {
                                runAfterDismiss {
                                    context.launchExternalPlayer(target)
                                }
                            },
                        )
                    }
                }
                if (onAiSourceSearch != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BgmSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_ai_search_desc),
                        subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                        iconVector = BgmIcons.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { runAfterDismiss(onAiSourceSearch) },
                    )
                }
                if (onManageRules != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BgmSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_manage),
                        subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                        iconVector = BgmIcons.Settings,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            runAfterDismiss {
                                onManageRules()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 来源抽屉分类标签 */
private enum class SourceSheetTab(
    val labelRes: Int,
) {
    ONLINE(R.string.feature_subject_source_tab_online),
    TORRENT(R.string.feature_subject_source_tab_torrent),
    EXTERNAL(R.string.feature_subject_source_tab_external),
}

/** 🌟 Hero 级主起播卡片：高对比度、大圆角、醒目播放箭头 */
@Composable
private fun HeroPlayCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = BgmShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = BgmIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                imageVector = BgmIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 🗂️ 场景分段标签栏：胶囊药丸、平滑切换与数量徽标 */
@Composable
private fun SourceTabsRow(
    selectedTab: SourceSheetTab,
    onTabSelected: (SourceSheetTab) -> Unit,
    onlineCount: Int,
    torrentCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceSheetTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val count =
                when (tab) {
                    SourceSheetTab.ONLINE -> onlineCount.takeIf { it > 0 }
                    SourceSheetTab.TORRENT -> torrentCount.takeIf { it > 0 }
                    SourceSheetTab.EXTERNAL -> null
                }
            Surface(
                onClick = { onTabSelected(tab) },
                shape = CircleShape,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                contentColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.height(34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (count != null) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ).padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 来源副标题：叠加最近一次播放失败归因 */
@Composable
internal fun playbackSourceSubtitle(
    base: String,
    failureReason: String?,
): String =
    if (failureReason == null) {
        base
    } else {
        stringResource(R.string.feature_subject_source_last_failed_format, base, failureReason)
    }

/**
 * 格式化分集播放源向导顶部标题：
 * 例如《葬送的芙莉莲》 第 5 话 · 死亡与安宁
 */
fun formatEpisodeGuideHeader(
    displayName: String,
    episode: Episode,
): String {
    val epLabel = episode.guideLabel
    val rawTitle = episode.nameCn.ifBlank { episode.name }.trim()
    val numLabel = episode.formattedNumber
    val isRedundant =
        rawTitle.isBlank() ||
            rawTitle.filterNot { it.isWhitespace() }.equals(epLabel.filterNot { it.isWhitespace() }, ignoreCase = true) ||
            (episode.isMain && (rawTitle == numLabel || rawTitle == "第${numLabel}集" || rawTitle == "第${numLabel}话"))

    val epTitle = if (!isRedundant) rawTitle else null
    val subjectPrefix = if (displayName.isNotBlank()) "《$displayName》 " else ""

    return if (epTitle != null) {
        "$subjectPrefix$epLabel · $epTitle"
    } else {
        "$subjectPrefix$epLabel"
    }
}

/**
 * 判定目标 URL 或描述是否属于音视频媒体直链。
 */
fun isLikelyMediaStream(
    url: String,
    description: String = "",
): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    if (clean.endsWith(".m3u8") ||
        clean.endsWith(".mp4") ||
        clean.endsWith(".mkv") ||
        clean.endsWith(".webm") ||
        clean.endsWith(".flv") ||
        clean.endsWith(".ts") ||
        clean.endsWith(".mpd")
    ) {
        return true
    }
    val full = url.lowercase()
    if (full.contains(".m3u8") || full.contains(".mp4") || full.contains(".flv")) {
        return true
    }
    val desc = description.lowercase()
    return desc.contains("直链") || desc.contains("m3u8") || desc.contains("stream") || desc.contains("视频流")
}

@Composable
fun TorrentMediaResourcesSection(
    isTorrentLoading: Boolean,
    torrentResources: List<TorrentMediaResource>,
    hasTorrentRules: Boolean,
    context: Context,
    subject: Subject,
    episode: Episode?,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onDismissAndRun: (() -> Unit) -> Unit,
) {
    val torrentRepository: TorrentRepository = koinInject()
    val torrentIntentDispatcher: TorrentIntentDispatcher = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val overlayHostState = rememberOverlayHostState()
    var expandedIndex by remember { mutableStateOf<Int?>(-1) }
    // 单资源进行中态：边下边播单次最长等待 60s，重复点击不得再起一条并发协程。
    // busyTaskId 由 onTaskQueued 在任务落库瞬间回填——「停止起播」必须靠它暂停底层任务，
    // 只取消等待协程会让任务继续占着单实例 P2P 队列，并在下载页留下幽灵条目。
    var busyResourceKey by remember { mutableStateOf<String?>(null) }
    var busyJob by remember { mutableStateOf<Job?>(null) }
    var busyTaskId by remember { mutableStateOf<String?>(null) }
    var showAllResources by remember { mutableStateOf(false) }
    val streamState by torrentRepository.streamState.collectAsState()
    val torrentTasks by torrentRepository.tasks.collectAsState()

    /**
     * 分集身份：优先当前分集上下文；条目级（未选分集）动作回退资源自身声明的集数，
     * 绝不硬编码「第 1 话」——那会把所有条目级资源塌缩成同一个 `sort:1.0` 身份键，
     * 并把假的集数提示（episodeHint="1"）喂给 Rust 侧的分集选片逻辑。
     */
    fun resourceEpisodeSort(resource: TorrentMediaResource): Float =
        episode?.let { if (it.sort > 0f) it.sort else it.episodeNumber } ?: resource.episodeSort ?: 0f

    fun resourceEpisodeName(resource: TorrentMediaResource): String = episode?.let { it.nameCn.ifBlank { it.name } } ?: resource.title

    fun resourceEpisodeKey(
        resource: TorrentMediaResource,
        episodeSort: Float,
    ): String =
        torrentEpisodeKey(
            episodeId = episode?.id ?: 0L,
            episodeSort = episodeSort,
            episodeName = resourceEpisodeName(resource),
        )

    /**
     * 该资源已落盘的既有任务（列表徽标与「点击即播」共用同一判据）。
     *
     * 分集身份键 + 源同一性双判据缺一不可：只比磁力会让合集包内第 N 集命中第 M 集，
     * 只比分集键又会让同一集的清晰度/压制组变体互串。
     */
    fun completedTaskFor(resource: TorrentMediaResource): TorrentTask? =
        torrentTasks.firstOrNull { task ->
            task.episodeKey == resourceEpisodeKey(resource, resourceEpisodeSort(resource)) &&
                task.isCompleted &&
                // 已完成但物理文件已被清理（`verifyTaskFiles` 标记）时必须当作"未落盘"：
                // 否则徽标会宣告「已下载」、按钮会指向一个不存在的 file:// 地址，
                // 同时 离线下载 被禁用，用户被卡在一条既播不了也重下不了的死路上。
                !task.isFileMissing &&
                task.savePath.isNotBlank() &&
                task.matchesResourceSource(resource)
        }

    suspend fun playTorrentResource(resource: TorrentMediaResource) {
        val effectiveEpSort = resourceEpisodeSort(resource)
        // 合集/BD 包内多集共用同一磁力：必须按分集身份键隔离，
        // 否则点第 N 集会直接复用第 M 集已落盘的文件，静默播错集。
        val completedTask = completedTaskFor(resource)
        if (completedTask != null) {
            withContext(Dispatchers.Main) {
                onDismissAndRun {
                    onInternalPlayClick?.invoke(
                        PlayerRoute(
                            subjectId = subject.id,
                            episodeId = episode?.id ?: 0L,
                            streamUrl = "file://${completedTask.savePath}",
                            episodeName = resourceEpisodeName(resource),
                            subjectName = subject.displayName,
                            episodeSort = effectiveEpSort,
                            episodeType = episode?.type ?: 0,
                        ),
                    )
                }
            }
            return
        }

        val installed =
            ensureTorrentPluginInstalled(
                context = context,
                overlayHostState = overlayHostState,
                torrentRepository = torrentRepository,
                purpose = TorrentPluginInstallPurpose.STREAM_PLAYBACK,
            )
        if (!installed) return

        val streamResult =
            torrentRepository.startStreamDownload(
                title = resource.title,
                magnetUrl = resource.magnetUrl,
                torrentUrl = resource.torrentUrl.ifBlank { null },
                totalBytes = resource.contentLength,
                subjectId = subject.id,
                subjectName = subject.displayName,
                subjectCoverUrl = subject.images?.bestImage.orEmpty(),
                episodeId = episode?.id ?: 0L,
                episodeSort = effectiveEpSort,
                episodeName = resourceEpisodeName(resource),
                onTaskQueued = { taskId -> busyTaskId = taskId },
            )
        streamResult
            .onSuccess { streamUrl ->
                withContext(Dispatchers.Main) {
                    onDismissAndRun {
                        onInternalPlayClick?.invoke(
                            PlayerRoute(
                                subjectId = subject.id,
                                episodeId = episode?.id ?: 0L,
                                streamUrl = streamUrl,
                                episodeName = resourceEpisodeName(resource),
                                subjectName = subject.displayName,
                                episodeSort = effectiveEpSort,
                                episodeType = episode?.type ?: 0,
                            ),
                        )
                    }
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            err.message ?: context.getString(R.string.feature_subject_source_torrent_stream_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
    }

    suspend fun downloadTorrentResource(resource: TorrentMediaResource) {
        val effectiveEpSort = resourceEpisodeSort(resource)
        val estimatedBytes = resource.contentLength
        val usage = runCatching { torrentRepository.getStorageUsage() }.getOrNull()
        // 后台大任务必须显式确认，并在确认前披露体积与剩余空间（严禁静默入队）
        val confirmed =
            overlayHostState.await(
                ConfirmDialogAction(
                    title = context.getString(R.string.feature_subject_source_torrent_download_confirm_title),
                    message =
                        context.getString(
                            R.string.feature_subject_source_torrent_download_confirm_message,
                            resource.formattedSize.ifBlank { DownloadStorageUsage.formatBytes(estimatedBytes) },
                            usage?.formattedAvailableSize
                                ?: context.getString(R.string.feature_subject_source_torrent_download_space_unknown),
                        ),
                    confirmText = context.getString(R.string.feature_subject_source_torrent_download_confirm_btn),
                    dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                    isPrimary = true,
                ),
            )
        if (!confirmed) return

        if (usage != null &&
            estimatedBytes > 0L &&
            usage.availableBytes > 0L &&
            estimatedBytes > usage.availableBytes
        ) {
            withContext(Dispatchers.Main) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.feature_subject_source_torrent_download_no_space),
                        Toast.LENGTH_LONG,
                    ).show()
            }
            return
        }

        val installed =
            ensureTorrentPluginInstalled(
                context = context,
                overlayHostState = overlayHostState,
                torrentRepository = torrentRepository,
                purpose = TorrentPluginInstallPurpose.OFFLINE_DOWNLOAD,
            )
        if (!installed) return

        withContext(Dispatchers.Main) {
            onRequestNotificationPermission?.invoke()
        }
        torrentRepository.startDownload(
            title = resource.title,
            magnetUrl = resource.magnetUrl,
            torrentUrl = resource.torrentUrl.ifBlank { null },
            totalBytes = estimatedBytes,
            subjectId = subject.id,
            subjectName = subject.displayName,
            subjectCoverUrl = subject.images?.bestImage.orEmpty(),
            episodeId = episode?.id ?: 0L,
            episodeSort = effectiveEpSort,
            episodeName = resourceEpisodeName(resource),
        )
        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.feature_subject_source_torrent_download_started),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    /**
     * 停止本次起播：先掐掉等待协程，再暂停底层任务。
     *
     * 只取消等待协程是历史缺陷——任务会继续占着单实例 P2P 队列，并在下载页留下一条
     * 用户从未主动创建过的条目（进度条的「停止」形同虚设）。这里选择**暂停**而非删除：
     * 该任务可能正是用户此前显式确认过的离线下载，删除会连半成品一起清掉。
     */
    fun stopBusyStream() {
        val taskIdToStop = busyTaskId
        busyJob?.cancel()
        busyResourceKey = null
        busyJob = null
        busyTaskId = null
        if (taskIdToStop != null) {
            coroutineScope.launch {
                runCatching { torrentRepository.pauseDownload(taskIdToStop) }
            }
            Toast
                .makeText(
                    context,
                    context.getString(R.string.feature_subject_source_torrent_stream_stopped),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_torrent_fansub_section))
    if (!hasTorrentRules) {
        Text(
            text = stringResource(R.string.feature_subject_source_torrent_no_rules),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        if (onManageRules != null) {
            Spacer(modifier = Modifier.height(4.dp))
            BgmSourceActionCard(
                title = stringResource(R.string.feature_subject_source_torrent_configure_rules),
                subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                iconVector = BgmIcons.Settings,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    onDismissAndRun {
                        onManageRules()
                    }
                },
            )
        }
    } else if (isTorrentLoading) {
        Text(
            text = stringResource(R.string.feature_subject_source_torrent_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    } else if (torrentResources.isEmpty()) {
        Text(
            text = stringResource(R.string.feature_subject_source_torrent_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visibleResources =
                if (showAllResources) {
                    torrentResources
                } else {
                    torrentResources.take(MAX_VISIBLE_TORRENT_RESOURCES)
                }
            visibleResources.forEachIndexed { index, resource ->
                val resourceKey = resource.dedupeKey
                val isBusy = busyResourceKey == resourceKey
                TorrentMediaResourceCard(
                    resource = resource,
                    isExpanded = expandedIndex == index,
                    isBusy = isBusy,
                    // 引擎是单实例 P2P 会话：任一起播进行中时所有资源动作一并禁用（可见的诚实禁用，
                    // 而不是按钮亮着却因忙态守卫点了没反应）
                    isAnyBusy = busyResourceKey != null,
                    isDownloaded = completedTaskFor(resource) != null,
                    streamState = if (isBusy) streamState else null,
                    onStopBusy = { stopBusyStream() },
                    onToggleExpand = {
                        expandedIndex = if (expandedIndex == index) null else index
                    },
                    onStreamPlay = {
                        // 进行中或没有可用地址（无磁力且无种子直链）时不得再拉起并发任务
                        if (busyResourceKey == null && resource.hasPlayableSource) {
                            busyResourceKey = resourceKey
                            busyJob =
                                coroutineScope.launch {
                                    try {
                                        playTorrentResource(resource)
                                    } finally {
                                        busyResourceKey = null
                                        busyJob = null
                                        busyTaskId = null
                                    }
                                }
                        }
                    },
                    onInAppDownload = {
                        if (busyResourceKey == null && resource.hasPlayableSource) {
                            busyResourceKey = resourceKey
                            busyJob =
                                coroutineScope.launch {
                                    try {
                                        downloadTorrentResource(resource)
                                    } finally {
                                        busyResourceKey = null
                                        busyJob = null
                                        busyTaskId = null
                                    }
                                }
                        }
                    },
                    onExternalLaunch = {
                        if (resource.magnetUrl.isBlank()) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.feature_subject_source_torrent_no_magnet),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            onDismissAndRun {
                                torrentIntentDispatcher.launchMagnet(resource.magnetUrl)
                            }
                        }
                    },
                    onDownloadTorrent = {
                        if (resource.torrentUrl.isBlank()) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.feature_subject_source_torrent_no_torrent),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            coroutineScope.launch {
                                val result = torrentRepository.downloadTorrentFile(resource.torrentUrl, resource.title)
                                withContext(Dispatchers.Main) {
                                    result
                                        .onSuccess { path ->
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.feature_subject_source_torrent_torrent_downloaded),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            torrentIntentDispatcher.launchTorrentFile(path)
                                        }.onFailure {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.feature_subject_source_torrent_torrent_failed),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                }
                            }
                        }
                    },
                    onCopyMagnet = {
                        if (resource.magnetUrl.isBlank()) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.feature_subject_source_torrent_no_magnet),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            torrentIntentDispatcher.copyMagnetToClipboard(resource.magnetUrl)
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.feature_subject_source_torrent_copied_magnet),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                )
            }
            if (!showAllResources && torrentResources.size > MAX_VISIBLE_TORRENT_RESOURCES) {
                Spacer(modifier = Modifier.height(4.dp))
                TorrentActionButton(
                    text =
                        stringResource(
                            R.string.feature_subject_source_torrent_show_more,
                            torrentResources.size - MAX_VISIBLE_TORRENT_RESOURCES,
                        ),
                    icon = BgmIcons.KeyboardArrowDown,
                    isPrimary = false,
                    onClick = { showAllResources = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }
}

@Composable
fun TorrentMediaResourceCard(
    resource: TorrentMediaResource,
    isExpanded: Boolean,
    isBusy: Boolean = false,
    isAnyBusy: Boolean = false,
    isDownloaded: Boolean = false,
    streamState: TorrentStreamState? = null,
    onStopBusy: () -> Unit = {},
    onToggleExpand: () -> Unit,
    onStreamPlay: () -> Unit,
    onInAppDownload: () -> Unit,
    onExternalLaunch: () -> Unit,
    onDownloadTorrent: () -> Unit,
    onCopyMagnet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupTitle =
        buildString {
            if (resource.fansubName.isNotBlank()) append("[${resource.fansubName}] ")
            if (resource.resolution.isNotBlank()) append("${resource.resolution} ")
            if (resource.formattedSize.isNotBlank()) append("· ${resource.formattedSize}")
        }.trim().ifBlank { resource.title }

    Surface(
        onClick = onToggleExpand,
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BgmIcons.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (resource.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resource.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 已落盘标记：用户必须在**点击之前**就能分辨哪条已经下载完，
                // 而不是点下去才发现走的是本地文件（或反过来白等一次起播）
                if (isDownloaded) {
                    Text(
                        text = stringResource(R.string.feature_subject_source_torrent_downloaded_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Icon(
                    imageVector = if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // 起播进度刻意留在折叠区之外：用户收起卡片是为了少占屏，不是要丢掉进度与「停止」入口
            if (isBusy) {
                Spacer(modifier = Modifier.height(10.dp))
                TorrentStreamProgress(state = streamState, onStop = onStopBusy)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 边下边播 (Hero 强调主操作)
                    TorrentActionButton(
                        text =
                            stringResource(
                                if (isDownloaded) {
                                    R.string.feature_subject_source_torrent_play_downloaded
                                } else {
                                    R.string.feature_subject_source_torrent_stream_play
                                },
                            ),
                        icon = BgmIcons.PlayCircle,
                        isPrimary = true,
                        enabled = !isAnyBusy,
                        onClick = onStreamPlay,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_in_app_download),
                            icon = BgmIcons.Download,
                            isPrimary = false,
                            // 已落盘资源没有可下载的内容：放行只会落进 startDownload 的
                            // 「既有完成任务直接返回 taskId」分支，随后弹「已加入下载队列，
                            // 可在通知栏查看进度」的假提示——用户盯着通知栏等一个永不出现的进度。
                            enabled = !isAnyBusy && !isDownloaded,
                            onClick = onInAppDownload,
                            modifier = Modifier.weight(1f),
                        )
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_external_app),
                            icon = BgmIcons.OpenInNew,
                            isPrimary = false,
                            enabled = !isAnyBusy,
                            onClick = onExternalLaunch,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_download_torrent),
                            icon = BgmIcons.FolderOpen,
                            isPrimary = false,
                            enabled = !isAnyBusy,
                            onClick = onDownloadTorrent,
                            modifier = Modifier.weight(1f),
                        )
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_copy_magnet),
                            icon = BgmIcons.ContentCopy,
                            isPrimary = false,
                            enabled = !isAnyBusy,
                            onClick = onCopyMagnet,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentActionButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = BgmShapes.small,
        color =
            if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color =
                    if (isPrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
            )
        }
    }
}

/** 组件按需安装的用途：决定确认弹窗文案（边下边播 vs 离线下载），避免复用同一套「立即安装并起播」措辞。 */
private enum class TorrentPluginInstallPurpose {
    STREAM_PLAYBACK,
    OFFLINE_DOWNLOAD,
}

private suspend fun ensureTorrentPluginInstalled(
    context: Context,
    overlayHostState: OverlayHostState,
    torrentRepository: TorrentRepository,
    purpose: TorrentPluginInstallPurpose = TorrentPluginInstallPurpose.STREAM_PLAYBACK,
): Boolean {
    if (torrentRepository.isPluginInstalled()) {
        return true
    }
    val messageRes =
        when (purpose) {
            TorrentPluginInstallPurpose.STREAM_PLAYBACK ->
                R.string.feature_subject_source_torrent_plugin_dialog_message
            TorrentPluginInstallPurpose.OFFLINE_DOWNLOAD ->
                R.string.feature_subject_source_torrent_plugin_dialog_message_download
        }
    val confirmRes =
        when (purpose) {
            TorrentPluginInstallPurpose.STREAM_PLAYBACK ->
                R.string.feature_subject_source_torrent_plugin_install_btn
            TorrentPluginInstallPurpose.OFFLINE_DOWNLOAD ->
                R.string.feature_subject_source_torrent_plugin_install_btn_download
        }
    val confirmed =
        overlayHostState.await(
            ConfirmDialogAction(
                title = context.getString(R.string.feature_subject_source_torrent_plugin_dialog_title),
                message = context.getString(messageRes),
                confirmText = context.getString(confirmRes),
                dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                isPrimary = true,
            ),
        )
    if (!confirmed) {
        return false
    }
    withContext(Dispatchers.Main) {
        Toast
            .makeText(
                context,
                context.getString(R.string.feature_subject_source_torrent_plugin_installing),
                Toast.LENGTH_SHORT,
            ).show()
    }
    val installResult = torrentRepository.installPlugin()
    if (installResult.isFailure) {
        // 严禁吞掉真实失败原因（如设备 ABI 不受支持 / 镜像 404）：否则用户只能看到泛化文案
        val reason = installResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
        val text =
            if (reason != null) {
                context.getString(R.string.feature_subject_source_torrent_plugin_install_failed_reason, reason)
            } else {
                context.getString(R.string.feature_subject_source_torrent_plugin_install_failed)
            }
        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    text,
                    Toast.LENGTH_LONG,
                ).show()
        }
        return false
    }
    return true
}

/** 流式/安装进度可视化：安装组件 → 解析元数据 → 缓冲首部数据，全程可见并可停止。 */
@Composable
private fun TorrentStreamProgress(
    state: TorrentStreamState?,
    onStop: () -> Unit,
) {
    val label =
        when (state) {
            is TorrentStreamState.InstallingPlugin ->
                stringResource(R.string.feature_subject_source_torrent_progress_installing, (state.progress * 100).toInt())
            is TorrentStreamState.Resolving -> stringResource(R.string.feature_subject_source_torrent_progress_resolving)
            is TorrentStreamState.Buffering ->
                stringResource(
                    R.string.feature_subject_source_torrent_progress_buffering,
                    (state.progress * 100).toInt(),
                    formatTransferSpeed(state.speedBytesPerSec),
                    state.connectedPeers,
                )
            is TorrentStreamState.Error -> stringResource(R.string.feature_subject_source_torrent_progress_failed, state.message)
            else -> stringResource(R.string.feature_subject_source_torrent_stream_buffering)
        }
    val fraction =
        when (state) {
            is TorrentStreamState.InstallingPlugin -> state.progress
            is TorrentStreamState.Buffering -> state.progress
            else -> null
        }

    Surface(
        shape = BgmShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fraction == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (state is TorrentStreamState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.feature_subject_source_torrent_progress_stop),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .clickable(onClick = onStop)
                            .padding(start = 8.dp),
                )
            }
            if (fraction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                )
            }
        }
    }
}

/** 磁力资源列表首屏渲染上限：超出部分折叠到「展开更多」，杜绝静默截断丢条目。 */
private const val MAX_VISIBLE_TORRENT_RESOURCES = 8

/**
 * 源同一性判据：infoHash 优先，其次磁力链接原文，最后种子直链。
 *
 * 严禁只比 `magnetUrl`：种子直链条目（Feed 只给 `.torrent` 直链）两侧的 `magnetUrl` 都是空串，
 * `"" == ""` 会让两条互不相干的资源被判成同一来源，直接复用对方的流地址或 `file://` 落盘路径，
 * 静默播错内容且全程无提示。`TorrentTask` 本就持久化了 `torrentUrl`，直链必须参与比对。
 */
private fun TorrentTask.matchesResourceSource(resource: TorrentMediaResource): Boolean {
    val resourceHash = magnetInfoHash(resource.magnetUrl)
    val taskHash = magnetInfoHash(magnetUrl)
    return when {
        resourceHash.isNotBlank() && taskHash.isNotBlank() -> resourceHash.equals(taskHash, ignoreCase = true)
        resource.magnetUrl.isNotBlank() -> magnetUrl == resource.magnetUrl
        !resource.torrentUrl.isNullOrBlank() -> torrentUrl == resource.torrentUrl
        else -> false
    }
}
