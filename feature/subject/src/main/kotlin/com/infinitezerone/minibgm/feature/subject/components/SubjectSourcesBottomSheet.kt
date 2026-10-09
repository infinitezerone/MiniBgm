package com.infinitezerone.minibgm.feature.subject.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.hideThenDismiss
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.PlaylistEntryMatch
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.forSubject
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.StreamingAppLauncher
import com.infinitezerone.minibgm.core.navigation.launchExternalPlayer
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 统一的「播放来源」BottomSheet，条目级与分集级共用同一组件（[episode] 为 null 即条目级）：
 * - 条目级：自备片单概览、一体化播放器、AI 找源、播放源管理、条目级外部跳转；
 * - 分集级：按话数命中的自备片单、逐条渲染规则源（应用内嗅探或外开）、
 *   分集化搜索关键词、外部播放器（mpv / VLC）与失败归因提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    playlists: List<PlaybackPlaylist> = emptyList(),
    failedSourceReasons: Map<String, String> = emptyMap(),
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = subject.displayName
    val runAfterDismiss: (() -> Unit) -> Unit = { action ->
        coroutineScope.hideThenDismiss(sheetState) {
            onDismissRequest()
            action()
        }
    }
    val closeAction: () -> Unit = {
        coroutineScope.hideThenDismiss(sheetState, onDismissRequest)
    }

    if (episode == null) {
        // ==================== 条目级 ====================
        val boundPlaylists = remember(playlists, subject.id) { playlists.forSubject(subject.id) }
        val bilibiliTarget =
            remember(displayName) {
                StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
            }
        val mikanUrl =
            remember(mikanId, displayName) {
                StreamingIntentResolver.buildMikanUrl(mikanId = mikanId, keyword = displayName)
            }

        BgmModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
            ) {
                SourcesSheetHeader(
                    coverUrl = subject.images?.bestImage.orEmpty(),
                    title = displayName,
                    onClose = closeAction,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 分组 0：自备片单概览（条目级只看总量，逐话选择在分集入口完成）
                    if (onManageRules != null && boundPlaylists.isNotEmpty()) {
                        SourcesSectionLabel(stringResource(R.string.feature_subject_source_user_playlist))
                        for (playlist in boundPlaylists) {
                            val hasFailure = playlist.entries.any { it.url in failedSourceReasons }
                            EpisodeSourceActionCard(
                                title = playlist.name,
                                subtitle =
                                    playbackSourceSubtitle(
                                        stringResource(R.string.feature_subject_source_user_playlist_hint, playlist.entries.size),
                                        if (hasFailure) stringResource(R.string.feature_subject_source_has_failed_entries) else null,
                                    ),
                                iconVector = BgmIcons.PlayCircle,
                                iconTint =
                                    if (hasFailure) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                onClick = { onManageRules() },
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (onInternalPlayClick != null) {
                        SourcesSectionLabel(stringResource(R.string.feature_subject_source_internal_player))
                        EpisodeSourceActionCard(
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
                        SourcesSectionLabel(stringResource(R.string.feature_subject_source_ai_search))
                        EpisodeSourceActionCard(
                            title = stringResource(R.string.feature_subject_source_ai_search_desc),
                            subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                            iconVector = BgmIcons.AutoAwesome,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { runAfterDismiss(onAiSourceSearch) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (onManageRules != null) {
                        EpisodeSourceActionCard(
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

                    SourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))
                    EpisodeSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_bilibili),
                        subtitle = stringResource(R.string.feature_subject_source_bilibili_desc),
                        iconVector = BgmIcons.Tv,
                        onClick = {
                            runAfterDismiss {
                                onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                            }
                        },
                    )

                    EpisodeSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_mikan),
                        subtitle = stringResource(R.string.feature_subject_source_mikan_desc),
                        iconVector = BgmIcons.Download,
                        onClick = {
                            runAfterDismiss {
                                onOpenUrl(mikanUrl)
                            }
                        },
                    )
                }
            }
        }
    } else {
        // ==================== 分集级 ====================
        val enabledRules = remember(playbackRules) { playbackRules.filter { it.isEnabled } }
        val playlistMatches =
            remember(playlists, subject.id, episode) {
                playlists.matchesForEpisode(subject.id, episode.episodeNumber)
            }
        val epLabel = remember(episode) { episode.guideLabel }

        val bilibiliTarget =
            remember(displayName, epLabel) {
                StreamingIntentResolver.buildBilibiliSearchTarget("$displayName $epLabel")
            }
        val mikanUrl =
            remember(mikanId, displayName, epLabel, episode) {
                val keyword =
                    if (episode.isMain) {
                        "$displayName ${episode.formattedNumber}".trim()
                    } else {
                        "$displayName $epLabel".trim()
                    }
                StreamingIntentResolver.buildMikanUrl(mikanId = mikanId, keyword = keyword)
            }

        val context = LocalContext.current

        // 外部播放器直链候选：仅对流媒体直链（http/https 的 DIRECT 地址）提供，
        // 片单 DIRECT 直链优先，其次为解析为媒体直链的播放规则
        val externalPlayerStreamUrl =
            remember(playlistMatches, enabledRules, displayName, episode, subject.id) {
                // 外部播放器无法携带 Referer/Cookie 等自定义请求头，带请求头的直链不提供；
                // kind = SOURCE 的规则地址是取源接口而非流地址，同样排除
                playlistMatches
                    .map { it.entry }
                    .firstOrNull {
                        it.kind == PlaylistEntryKind.DIRECT &&
                            it.headers.isEmpty() &&
                            StreamingIntentResolver.isHttpStreamUrl(it.url)
                    }?.url
                    ?: enabledRules.firstNotNullOfOrNull { rule ->
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

        BgmModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
            ) {
                SourcesSheetHeader(
                    coverUrl = subject.images?.bestImage.orEmpty(),
                    title = formatEpisodeGuideHeader(displayName, episode),
                    onClose = closeAction,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 分组 0：用户自备片单（JSON 导入），按话数命中后排在本集最前面
                    PlaylistSourceSection(
                        matches = playlistMatches,
                        subject = subject,
                        episode = episode,
                        displayName = displayName,
                        failedSourceReasons = failedSourceReasons,
                        onOpenUrl = onOpenUrl,
                        onInternalPlayClick = onInternalPlayClick,
                        runAfterDismiss = runAfterDismiss,
                    )

                    // 分组 1：内部播放
                    SourcesSectionLabel(stringResource(R.string.feature_subject_source_internal_playback))

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
                            EpisodeSourceActionCard(
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
                        EpisodeSourceActionCard(
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
                        EpisodeSourceActionCard(
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
                        SourcesSectionLabel(stringResource(R.string.feature_subject_source_ai_search))
                        EpisodeSourceActionCard(
                            title = stringResource(R.string.feature_subject_source_ai_search_desc),
                            subtitle = stringResource(R.string.feature_subject_source_ai_search_hint),
                            iconVector = BgmIcons.AutoAwesome,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { runAfterDismiss(onAiSourceSearch) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 分组 2：外部跳转
                    SourcesSectionLabel(stringResource(R.string.feature_subject_source_external_nav))
                    EpisodeSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_bilibili),
                        subtitle = stringResource(R.string.feature_subject_source_bilibili_ep_desc),
                        iconVector = BgmIcons.Tv,
                        onClick = {
                            runAfterDismiss {
                                onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                            }
                        },
                    )

                    EpisodeSourceActionCard(
                        title = stringResource(R.string.feature_subject_source_mikan),
                        subtitle = stringResource(R.string.feature_subject_source_mikan_desc),
                        iconVector = BgmIcons.Download,
                        onClick = {
                            runAfterDismiss {
                                onOpenUrl(mikanUrl)
                            }
                        },
                    )

                    // 外部播放器（mpv / VLC）：仅流媒体直链可交给系统 ACTION_VIEW 唤起，
                    // 外部播放器无法携带 Referer 等自定义请求头，未安装时在副标题给出降级提示
                    for (target in externalPlayerTargets) {
                        val installed = target.packageName in installedExternalPlayerPackages
                        EpisodeSourceActionCard(
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
            }
        }
    }
}

/** 来源分组标题 */
@Composable
private fun SourcesSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** sheet 头部：封面 + 标题（条目名或「条目名 + 第 N 话」）+ 关闭按钮 */
@Composable
private fun SourcesSheetHeader(
    coverUrl: String,
    title: String,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(BgmShapes.small),
        ) {
            CoverImage(
                url = coverUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.feature_subject_source_choose_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = BgmIcons.Close,
                contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 自备片单分组：按话数命中的条目优先；条目 kind 决定应用内播放还是外部打开页面。
 */
@Composable
internal fun PlaylistSourceSection(
    matches: List<PlaylistEntryMatch>,
    subject: Subject,
    episode: Episode,
    displayName: String,
    failedSourceReasons: Map<String, String>,
    onOpenUrl: (String) -> Unit,
    onInternalPlayClick: ((PlayerRoute) -> Unit)?,
    runAfterDismiss: (() -> Unit) -> Unit,
) {
    if (matches.isEmpty()) return
    val grouped = remember(matches) { matches.groupBy { it.playlist } }

    SourcesSectionLabel(stringResource(R.string.feature_subject_source_user_playlist))

    for ((playlist, items) in grouped) {
        val header =
            if (items.all { it.matched }) {
                playlist.name
            } else {
                stringResource(R.string.feature_subject_source_playlist_unmatched_hint, playlist.name, items.size)
            }
        Text(
            text = header,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        for (item in items) {
            val entry = item.entry
            val failure = failedSourceReasons[entry.url]
            val playableInApp = entry.kind == PlaylistEntryKind.DIRECT && onInternalPlayClick != null
            EpisodeSourceActionCard(
                title =
                    if (entry.title.isBlank()) {
                        entry.label
                    } else {
                        "${entry.label} · ${entry.title}"
                    },
                subtitle =
                    playbackSourceSubtitle(
                        entry.siteName.ifBlank {
                            if (entry.kind == PlaylistEntryKind.DIRECT) {
                                stringResource(R.string.feature_subject_source_play_playlist_direct)
                            } else {
                                stringResource(R.string.feature_subject_source_open_playlist_page)
                            }
                        },
                        failure,
                    ),
                iconVector =
                    if (entry.kind == PlaylistEntryKind.DIRECT) {
                        BgmIcons.PlayCircle
                    } else {
                        BgmIcons.OpenInNew
                    },
                iconTint =
                    when {
                        failure != null -> MaterialTheme.colorScheme.error
                        playableInApp -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                onClick = {
                    runAfterDismiss {
                        if (playableInApp) {
                            // 同片单的可播条目按用户书写顺序入队：播放器内支持连播与选集抽屉
                            val queueEntries = item.playlist.entries.filter { it.kind == PlaylistEntryKind.DIRECT }
                            onInternalPlayClick(
                                PlayerRoute(
                                    subjectId = subject.id,
                                    episodeId = episode.id,
                                    streamUrl = entry.url,
                                    episodeName = episode.nameCn.ifBlank { episode.name },
                                    subjectName = displayName,
                                    episodeSort = episode.episodeNumber,
                                    episodeType = episode.type,
                                    requestHeaders = entry.headers,
                                    queue =
                                        queueEntries.map { matched ->
                                            PlayerQueueEntry(
                                                streamUrl = matched.url,
                                                label = matched.label,
                                                episodeName = matched.title,
                                                episodeSort =
                                                    matched.label.toFloatOrNull()
                                                        ?: episode.episodeNumber,
                                                episodeType = episode.type,
                                                episodeId = episode.id,
                                                requestHeaders = matched.headers,
                                            )
                                        },
                                    startIndex = queueEntries.indexOf(entry).coerceAtLeast(0),
                                ),
                            )
                        } else {
                            onOpenUrl(entry.url)
                        }
                    }
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
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

@Composable
internal fun EpisodeSourceActionCard(
    title: String,
    subtitle: String,
    iconVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContent: @Composable () -> Unit = {
        Icon(
            imageVector = BgmIcons.OpenInNew,
            contentDescription = stringResource(R.string.feature_subject_source_action_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    },
) {
    Surface(
        onClick = onClick,
        shape = BgmShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = BgmShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            trailingContent()
        }
    }
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
