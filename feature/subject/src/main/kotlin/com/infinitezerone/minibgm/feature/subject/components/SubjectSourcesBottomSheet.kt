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
import com.infinitezerone.minibgm.core.data.download.TorrentStreamEngine
import com.infinitezerone.minibgm.core.data.repository.TorrentRepository
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourceActionCard
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesSectionLabel
import com.infinitezerone.minibgm.core.designsystem.component.ConfirmDialogAction
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.MikanTorrentResource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
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

            MikanResourcesSection(
                isMikanLoading = isMikanLoading,
                mikanResources = mikanResources,
                context = context,
                subject = subject,
                episode = null,
                onInternalPlayClick = onInternalPlayClick,
                onDismissAndRun = runAfterDismiss,
            )
        } else {
            // ==================== 分集级 ====================
            val enabledRules = remember(playbackRules) { playbackRules.filter { it.isEnabled } }
            val epLabel = remember(episode) { episode.guideLabel }

            val bilibiliTarget =
                remember(displayName, epLabel) {
                    StreamingIntentResolver.buildBilibiliSearchTarget("$displayName $epLabel")
                }

            // 外部播放器直链候选：解析为媒体直链的播放规则
            val externalPlayerStreamUrl =
                remember(enabledRules, displayName, episode, subject.id) {
                    // 外部播放器无法携带 Referer/Cookie 等自定义请求头，带请求头的直链不提供；
                    // kind = SOURCE 的规则地址是取源接口而非流地址，同样排除
                    enabledRules.firstNotNullOfOrNull { rule ->
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

            // 内部播放
            BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_internal_playback))

            if (enabledRules.isNotEmpty()) {
                // 渲染已启用的自定义播放规则
                for (rule in enabledRules) {
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
            } else if (onInternalPlayClick != null) {
                // 无已启用自定义规则时的默认内置播放入口
                BgmSourceActionCard(
                    title = stringResource(R.string.feature_subject_source_internal_player),
                    subtitle = stringResource(R.string.feature_subject_source_try_internal_play),
                    iconVector = BgmIcons.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    trailingContent = {
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.feature_subject_source_start_play),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
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

            Spacer(modifier = Modifier.height(4.dp))

            // 分组 1.5：AI 找源——解析可播放地址，检索在助手会话中显式触发
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

            // 分组 2：外部跳转
            BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))
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

            // 外部播放器（mpv / VLC）：仅流媒体直链可交给系统 ACTION_VIEW 唤起，
            // 外部播放器无法携带 Referer 等自定义请求头，未安装时在副标题给出降级提示
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

            MikanResourcesSection(
                isMikanLoading = isMikanLoading,
                mikanResources = mikanResources,
                context = context,
                subject = subject,
                episode = episode,
                onInternalPlayClick = onInternalPlayClick,
                onDismissAndRun = runAfterDismiss,
            )
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
private fun MikanResourcesSection(
    isMikanLoading: Boolean,
    mikanResources: List<MikanTorrentResource>,
    context: Context,
    subject: Subject,
    episode: Episode?,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onDismissAndRun: (() -> Unit) -> Unit,
) {
    val torrentRepository: TorrentRepository = koinInject()
    val torrentIntentDispatcher: TorrentIntentDispatcher = koinInject()
    val torrentStreamEngine: TorrentStreamEngine = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val overlayHostState = rememberOverlayHostState()
    var expandedIndex by remember { mutableStateOf<Int?>(-1) }

    Spacer(modifier = Modifier.height(4.dp))
    BgmSourcesSectionLabel(stringResource(R.string.feature_subject_source_mikan_fansub_section))
    if (isMikanLoading) {
        Text(
            text = stringResource(R.string.feature_subject_source_mikan_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    } else if (mikanResources.isEmpty()) {
        Text(
            text = stringResource(R.string.feature_subject_source_mikan_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mikanResources.take(15).forEachIndexed { index, resource ->
                MikanTorrentCard(
                    resource = resource,
                    isExpanded = expandedIndex == index,
                    onToggleExpand = {
                        expandedIndex = if (expandedIndex == index) null else index
                    },
                    onStreamPlay = {
                        coroutineScope.launch {
                            if (!torrentStreamEngine.isPluginInstalled()) {
                                val confirmed =
                                    overlayHostState.await(
                                        ConfirmDialogAction(
                                            title = context.getString(R.string.feature_subject_source_mikan_plugin_dialog_title),
                                            message = context.getString(R.string.feature_subject_source_mikan_plugin_dialog_message),
                                            confirmText = context.getString(R.string.feature_subject_source_mikan_plugin_install_btn),
                                            dismissText = context.getString(DesignSystemR.string.core_designsystem_action_cancel),
                                            isPrimary = true,
                                        ),
                                    )
                                if (!confirmed) {
                                    return@launch
                                }
                                withContext(Dispatchers.Main) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.feature_subject_source_mikan_plugin_installing),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                                val installResult = torrentStreamEngine.installPlugin()
                                if (installResult.isFailure) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "组件安装失败，请检查网络", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.feature_subject_source_mikan_stream_buffering),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                            val streamResult =
                                torrentStreamEngine.startStream(
                                    title = resource.title,
                                    magnetUrl = resource.magnetUrl,
                                    torrentUrl = resource.torrentUrl.ifBlank { null },
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
                                                        episodeSort = episode?.episodeNumber ?: 1f,
                                                        episodeType = episode?.type ?: 0,
                                                    )
                                                onInternalPlayClick(route)
                                            }
                                        }
                                    }
                                }.onFailure { err ->
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, err.message ?: "起播失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    },
                    onInAppDownload = {
                        coroutineScope.launch {
                            torrentRepository.startDownload(
                                title = resource.title,
                                magnetUrl = resource.magnetUrl,
                                torrentUrl = resource.torrentUrl.ifBlank { null },
                                totalBytes = resource.contentLength,
                            )
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.feature_subject_source_mikan_download_started),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                        onDismissAndRun {}
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
                                                    context.getString(R.string.feature_subject_source_mikan_torrent_downloaded),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            torrentIntentDispatcher.launchTorrentFile(path)
                                        }.onFailure {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.feature_subject_source_mikan_torrent_failed),
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
                                context.getString(R.string.feature_subject_source_mikan_copied_magnet),
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

@Composable
private fun MikanTorrentCard(
    resource: MikanTorrentResource,
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
                    MikanActionButton(
                        text = stringResource(R.string.feature_subject_source_mikan_stream_play),
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
                        MikanActionButton(
                            text = stringResource(R.string.feature_subject_source_mikan_in_app_download),
                            icon = BgmIcons.Download,
                            isPrimary = false,
                            onClick = onInAppDownload,
                            modifier = Modifier.weight(1f),
                        )
                        MikanActionButton(
                            text = stringResource(R.string.feature_subject_source_mikan_external_app),
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
                        MikanActionButton(
                            text = stringResource(R.string.feature_subject_source_mikan_download_torrent),
                            icon = BgmIcons.FolderOpen,
                            isPrimary = false,
                            onClick = onDownloadTorrent,
                            modifier = Modifier.weight(1f),
                        )
                        MikanActionButton(
                            text = stringResource(R.string.feature_subject_source_mikan_copy_magnet),
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
private fun MikanActionButton(
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
