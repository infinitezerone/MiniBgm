package com.infinitezerone.minibgm.feature.subject.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.MikanTorrentResource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.TorrentMediaResource
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.StreamingAppLauncher
import com.infinitezerone.minibgm.core.navigation.launchExternalPlayer
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 统一的「播放来源」BottomSheet，条目级与分集级共用同一组件（[episode] 为 null 即条目级）：
 * - 条目级：一体化播放器、AI 找源、播放源管理、条目级外部跳转；
 * - 分集级：逐条渲染规则源（应用内嗅探或外开）、
 *   分集化搜索关键词、外部播放器（mpv / VLC）与失败归因提示。
 */
@Composable
fun SubjectSourcesBottomSheet(
    subject: Subject,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    episode: Episode? = null,
    mikanId: String? = null,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onAiSourceSearch: (() -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    playbackRules: List<PlaybackSourceRule> = emptyList(),
    failedSourceReasons: Map<String, String> = emptyMap(),
    torrentResources: List<TorrentMediaResource> = emptyList(),
    isTorrentLoading: Boolean = false,
    mikanResources: List<MikanTorrentResource> = emptyList(),
    isMikanLoading: Boolean = false,
) {
    val context = LocalContext.current
    val displayName = subject.displayName
    val sheetTitle =
        remember(displayName, episode) {
            if (episode != null) {
                formatEpisodeGuideHeader(displayName, episode)
            } else {
                displayName
            }
        }

    val effectiveTorrentResources = remember(torrentResources, mikanResources) {
        torrentResources.ifEmpty { mikanResources }
    }
    val effectiveIsTorrentLoading = isTorrentLoading || isMikanLoading
    val enabledRules = remember(playbackRules) { playbackRules.filter { it.isEnabled } }
    val torrentRules = remember(enabledRules) { enabledRules.filter { it.kind == PlaybackRuleKind.TORRENT } }
    val onlineRules = remember(enabledRules) { enabledRules.filter { it.kind != PlaybackRuleKind.TORRENT } }

    BgmSourcesBottomSheet(
        title = sheetTitle,
        coverUrl = subject.images?.bestImage.orEmpty(),
        subtitle = stringResource(R.string.feature_subject_source_choose_source),
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) { runAfterDismiss ->
        if (episode == null) {
            // ==================== 条目级 ====================
            val bilibiliTarget =
                remember(displayName) {
                    StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
                }

            if (onInternalPlayClick != null) {
                BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_internal_player))
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_integrated_player),
                    subtitle = stringResource(R.string.feature_subject_source_player_features),
                    iconVector = BgmIcons.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        runAfterDismiss {
                            val route =
                                PlayerRoute(
                                    subjectId = subject.id,
                                    episodeId = 0L,
                                    subjectName = displayName,
                                )
                            onInternalPlayClick(route)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // P2P 种子与字幕组资源（仅在存在启用规则或已加载出资源时渲染）
            val shouldShowTorrentSection = torrentRules.isNotEmpty() || effectiveTorrentResources.isNotEmpty() || effectiveIsTorrentLoading
            if (shouldShowTorrentSection) {
                TorrentMediaResourcesSection(
                    isTorrentLoading = effectiveIsTorrentLoading,
                    torrentResources = effectiveTorrentResources,
                    context = context,
                    subject = subject,
                    episode = null,
                    onInternalPlayClick = onInternalPlayClick,
                    onDismissAndRun = runAfterDismiss,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (onAiSourceSearch != null) {
                BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_ai_search))
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_ai_search_desc),
                    subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                    iconVector = BgmIcons.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { runAfterDismiss(onAiSourceSearch) },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (onManageRules != null) {
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_manage),
                    subtitle = stringResource(R.string.feature_subject_source_manage_desc),
                    iconVector = BgmIcons.Settings,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onManageRules() },
                    trailingContent = {
                        Icon(
                            imageVector = BgmIcons.OpenInNew,
                            contentDescription = stringResource(R.string.feature_subject_source_manage_rules),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))
            BgmSourceActionCard(
                title = stringResource(R.string.feature_subject_source_bilibili),
                subtitle = stringResource(R.string.feature_subject_source_bilibili_desc),
                iconVector = BgmIcons.Tv,
                onClick = {
                    runAfterDismiss {
                        onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                    }
                },
            )
        } else {
            // ==================== 分集级 ====================
            val epLabel = remember(episode) { episode.guideLabel }

            val bilibiliTarget =
                remember(displayName, epLabel) {
                    StreamingIntentResolver.buildBilibiliSearchTarget("$displayName $epLabel")
                }

            // 外部播放器直链候选：解析为媒体直链的在线播放规则
            val externalPlayerStreamUrl =
                remember(onlineRules, displayName, episode, subject.id) {
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

            // 1. 核心操作：立即起播
            if (onInternalPlayClick != null) {
                BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_internal_playback))
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_play_immediately),
                    subtitle = stringResource(R.string.feature_subject_source_play_immediately_desc),
                    iconVector = BgmIcons.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    trailingContent = {
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.feature_subject_source_start_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    onClick = {
                        runAfterDismiss {
                            val route =
                                PlayerRoute(
                                    subjectId = subject.id,
                                    episodeId = episode.id,
                                    streamUrl = "",
                                    episodeName = episode.nameCn.ifBlank { episode.name },
                                    subjectName = displayName,
                                    episodeSort = episode.episodeNumber,
                                    episodeType = episode.type,
                                )
                            onInternalPlayClick(route)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 2. 在线播放线路
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
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 3. P2P 种子与字幕组资源（仅在存在启用规则或已加载出资源时渲染）
            val shouldShowTorrentSection = torrentRules.isNotEmpty() || effectiveTorrentResources.isNotEmpty() || effectiveIsTorrentLoading
            if (shouldShowTorrentSection) {
                TorrentMediaResourcesSection(
                    isTorrentLoading = effectiveIsTorrentLoading,
                    torrentResources = effectiveTorrentResources,
                    context = context,
                    subject = subject,
                    episode = episode,
                    onInternalPlayClick = onInternalPlayClick,
                    onDismissAndRun = runAfterDismiss,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. 辅助工具与外部跳转
            BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))

            if (onAiSourceSearch != null) {
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_ai_search_desc),
                    subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                    iconVector = BgmIcons.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { runAfterDismiss(onAiSourceSearch) },
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            BgmSourceActionCard(
                title = stringResource(R.string.feature_subject_source_bilibili),
                subtitle = stringResource(R.string.feature_subject_source_bilibili_ep_desc),
                iconVector = BgmIcons.Tv,
                onClick = {
                    runAfterDismiss {
                        onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                    }
                },
            )

            for (target in externalPlayerTargets) {
                val installed = target.packageName in installedExternalPlayerPackages
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

            if (onManageRules != null) {
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
                    trailingContent = {
                        Icon(
                            imageVector = BgmIcons.OpenInNew,
                            contentDescription = stringResource(R.string.feature_subject_source_manage_rules),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
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
    context: Context,
    subject: Subject,
    episode: Episode?,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onDismissAndRun: (() -> Unit) -> Unit,
) {
    val torrentRepository: TorrentRepository = koinInject()
    val torrentIntentDispatcher: TorrentIntentDispatcher = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val overlayHostState = rememberOverlayHostState()
    var expandedIndex by remember { mutableStateOf<Int?>(-1) }

    Spacer(modifier = Modifier.height(4.dp))
    BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_torrent_fansub_section))
    if (isTorrentLoading) {
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
            torrentResources.take(15).forEachIndexed { index, resource ->
                TorrentMediaResourceCard(
                    resource = resource,
                    isExpanded = expandedIndex == index,
                    onToggleExpand = {
                        expandedIndex = if (expandedIndex == index) null else index
                    },
                    onStreamPlay = {
                        coroutineScope.launch {
                            val resourceHash =
                                resource.magnetUrl
                                    .substringAfter("xt=urn:btih:", "")
                                    .substringBefore("&")
                            val completedTask =
                                torrentRepository.tasks.value.firstOrNull { task ->
                                    task.isCompleted &&
                                        task.savePath.isNotBlank() &&
                                        (
                                            task.magnetUrl == resource.magnetUrl ||
                                                (
                                                    resourceHash.isNotBlank() &&
                                                        task.magnetUrl
                                                            .substringAfter("xt=urn:btih:", "")
                                                            .substringBefore("&")
                                                            .equals(resourceHash, ignoreCase = true)
                                                )
                                        )
                                }
                            val effectiveEpSort = episode?.let { if (it.sort > 0f) it.sort else it.episodeNumber } ?: 1f
                            if (completedTask != null) {
                                withContext(Dispatchers.Main) {
                                    onDismissAndRun {
                                        if (onInternalPlayClick != null) {
                                            val route =
                                                PlayerRoute(
                                                    subjectId = subject.id,
                                                    episodeId = episode?.id ?: 0L,
                                                    streamUrl = "file://${completedTask.savePath}",
                                                    episodeName = episode?.let { it.nameCn.ifBlank { it.name } } ?: resource.title,
                                                    subjectName = subject.displayName,
                                                    episodeSort = effectiveEpSort,
                                                    episodeType = episode?.type ?: 0,
                                                )
                                            onInternalPlayClick(route)
                                        }
                                    }
                                }
                                return@launch
                            }

                            if (!ensureTorrentPluginInstalled(context, overlayHostState, torrentRepository)) {
                                return@launch
                            }
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.feature_subject_source_torrent_stream_buffering),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
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
                                    episodeName = episode?.let { it.nameCn.ifBlank { it.name } } ?: resource.title,
                                )
                            streamResult
                                .onSuccess { streamUrl ->
                                    withContext(Dispatchers.Main) {
                                        onDismissAndRun {
                                            if (onInternalPlayClick != null) {
                                                val route =
                                                    PlayerRoute(
                                                        subjectId = subject.id,
                                                        episodeId = episode?.id ?: 0L,
                                                        streamUrl = streamUrl,
                                                        episodeName = episode?.let { it.nameCn.ifBlank { it.name } } ?: resource.title,
                                                        subjectName = subject.displayName,
                                                        episodeSort = effectiveEpSort,
                                                        episodeType = episode?.type ?: 0,
                                                    )
                                                onInternalPlayClick(route)
                                            }
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
                    },
                    onInAppDownload = {
                        coroutineScope.launch {
                            val effectiveEpSort = episode?.let { if (it.sort > 0f) it.sort else it.episodeNumber } ?: 1f
                            if (!ensureTorrentPluginInstalled(context, overlayHostState, torrentRepository)) {
                                return@launch
                            }
                            torrentRepository.startDownload(
                                title = resource.title,
                                magnetUrl = resource.magnetUrl,
                                torrentUrl = resource.torrentUrl.ifBlank { null },
                                totalBytes = resource.contentLength,
                                subjectId = subject.id,
                                subjectName = subject.displayName,
                                subjectCoverUrl = subject.images?.bestImage.orEmpty(),
                                episodeId = episode?.id ?: 0L,
                                episodeSort = effectiveEpSort,
                                episodeName = episode?.let { it.nameCn.ifBlank { it.name } } ?: resource.title,
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
                    },
                    onExternalLaunch = {
                        onDismissAndRun {
                            torrentIntentDispatcher.launchMagnet(resource.magnetUrl)
                        }
                    },
                    onDownloadTorrent = {
                        if (resource.torrentUrl.isNotBlank()) {
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
                        } else {
                            Toast.makeText(context, "当前资源未提供种子直链", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyMagnet = {
                        torrentIntentDispatcher.copyMagnetToClipboard(resource.magnetUrl)
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.feature_subject_source_torrent_copied_magnet),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                )
            }
        }
    }

    BgmOverlayHost(hostState = overlayHostState) {
        confirmDialog()
    }
}

/** 兼容旧调用的包装函数 */
@Composable
private fun MikanResourcesSection(
    isMikanLoading: Boolean,
    mikanResources: List<MikanTorrentResource>,
    context: Context,
    subject: Subject,
    episode: Episode?,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onDismissAndRun: (() -> Unit) -> Unit,
) {
    TorrentMediaResourcesSection(
        isTorrentLoading = isMikanLoading,
        torrentResources = mikanResources,
        context = context,
        subject = subject,
        episode = episode,
        onInternalPlayClick = onInternalPlayClick,
        onDismissAndRun = onDismissAndRun,
    )
}

@Composable
fun TorrentMediaResourceCard(
    resource: TorrentMediaResource,
    isExpanded: Boolean,
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

                Icon(
                    imageVector = if (isExpanded) BgmIcons.KeyboardArrowUp else BgmIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
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
                        text = stringResource(R.string.feature_subject_source_torrent_stream_play),
                        icon = BgmIcons.PlayCircle,
                        isPrimary = true,
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
                            onClick = onInAppDownload,
                            modifier = Modifier.weight(1f),
                        )
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_external_app),
                            icon = BgmIcons.OpenInNew,
                            isPrimary = false,
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
                            onClick = onDownloadTorrent,
                            modifier = Modifier.weight(1f),
                        )
                        TorrentActionButton(
                            text = stringResource(R.string.feature_subject_source_torrent_copy_magnet),
                            icon = BgmIcons.ContentCopy,
                            isPrimary = false,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = BgmShapes.small,
        color =
            if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        modifier = modifier,
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

private suspend fun ensureTorrentPluginInstalled(
    context: Context,
    overlayHostState: OverlayHostState,
    torrentRepository: TorrentRepository,
): Boolean {
    if (torrentRepository.isPluginInstalled()) {
        return true
    }
    val confirmed =
        overlayHostState.await(
            ConfirmDialogAction(
                title = context.getString(R.string.feature_subject_source_torrent_plugin_dialog_title),
                message = context.getString(R.string.feature_subject_source_torrent_plugin_dialog_message),
                confirmText = context.getString(R.string.feature_subject_source_torrent_plugin_install_btn),
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
        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.feature_subject_source_torrent_plugin_install_failed),
                    Toast.LENGTH_SHORT,
                ).show()
        }
        return false
    }
    return true
}

