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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PlaybackPlaylist
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.PlaylistEntryMatch
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.matchesForEpisode
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.StreamingAppLauncher
import com.infinitezerone.minibgm.core.navigation.launchExternalPlayer
import kotlinx.coroutines.launch

/**
 * 分集播放源向导 BottomSheet：
 * 当用户在条目详情页（:feature:subject）分集列表点击单集播放按钮且暂无可播直链时呼出。
 *
 * 界面采用简洁克制的现代 Material 3 Expressive 风格，去除突兀描边与花哨徽章，清晰划分为三大区域：
 * 1. 自备片单：用户导入的 JSON 片源（[PlaybackPlaylist]），按话数匹配后直接给出；
 * 2. 内部播放 / AI 找源：应用内解析播放、管理第三方播放规则，或把找源请求交给 AI 助手会话；
 * 3. 外部跳转：外部 App / 网页直达（哔哩哔哩分集搜索、蜜柑计划资源页、mpv / VLC 外部播放器直链）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSourceGuideBottomSheet(
    subject: Subject,
    episode: Episode,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    mikanId: String? = null,
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    onAiSourceSearch: () -> Unit = {},
    onManageRules: (() -> Unit)? = null,
    playbackRules: List<PlaybackSourceRule> = emptyList(),
    playlists: List<PlaybackPlaylist> = emptyList(),
    failedSourceReasons: Map<String, String> = emptyMap(),
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = subject.displayName
    val enabledRules = remember(playbackRules) { playbackRules.filter { it.isEnabled } }
    val playlistMatches =
        remember(playlists, subject.id, episode) {
            playlists.matchesForEpisode(subject.id, if (episode.ep > 0f) episode.ep else episode.sort)
        }
    val runAfterDismiss: (() -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            sheetState.hide()
            onDismissRequest()
            action()
        }
    }

    val epLabel = remember(episode) { episodeGuideLabel(episode) }

    val bilibiliKeyword =
        remember(displayName, epLabel) {
            "$displayName $epLabel"
        }

    val bilibiliTarget =
        remember(bilibiliKeyword) {
            StreamingIntentResolver.buildBilibiliSearchTarget(bilibiliKeyword)
        }

    val mikanKeyword =
        remember(displayName, epLabel, episode) {
            if (episode.type == 0) {
                val num = if (episode.ep > 0f) episode.ep else episode.sort
                "$displayName ${num.toEpisodeLabel()}".trim()
            } else {
                "$displayName $epLabel".trim()
            }
        }

    val mikanUrl =
        remember(mikanId, mikanKeyword) {
            StreamingIntentResolver.buildMikanUrl(mikanId = mikanId, keyword = mikanKeyword)
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
                    val rawEpSort = if (episode.ep > 0f) episode.ep else episode.sort
                    val epStr = if (episode.type == 0) rawEpSort.toEpisodeLabel() else rawEpSort.toInt().toString()
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
            // 顶部信息：封面、条目名与单集标题，去除冗余胶囊
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
                        url = subject.images?.bestImage.orEmpty(),
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    val fullEpisodeHeader =
                        remember(displayName, episode) {
                            formatEpisodeGuideHeader(displayName, episode)
                        }
                    Text(
                        text = fullEpisodeHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "选择播放或跳转来源",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Spacer(modifier = Modifier.height(14.dp))

            // 滚动内容区：清晰划分为“在线解析”与“外部跳转”
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
                Text(
                    text = "内部播放",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                if (enabledRules.isNotEmpty()) {
                    // 渲染已启用的自定义播放规则
                    for (rule in enabledRules) {
                        val rawEpSort = if (episode.ep > 0f) episode.ep else episode.sort
                        val epStr = if (episode.type == 0) rawEpSort.toEpisodeLabel() else rawEpSort.toInt().toString()
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
                                        if (onInternalPlayClick != null || isMedia) "应用内嗅探与播放" else "打开解析链接"
                                    },
                                    ruleFailure,
                                ),
                            iconVector =
                                if (onInternalPlayClick != null || isMedia) {
                                    Icons.Filled.PlayCircleOutline
                                } else {
                                    Icons.AutoMirrored.Filled.OpenInNew
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
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    } else {
                                        Icons.AutoMirrored.Filled.OpenInNew
                                    }
                                Icon(
                                    imageVector = trailingIcon,
                                    contentDescription = if (onInternalPlayClick != null || isMedia) "播放" else "打开",
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
                                                episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
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
                        title = "应用内播放",
                        subtitle = "尝试在应用内解析并播放该分集",
                        iconVector = Icons.Filled.PlayCircleOutline,
                        iconTint = MaterialTheme.colorScheme.primary,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "开始播放",
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
                                        episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
                                        episodeType = episode.type,
                                    )
                                onInternalPlayClick(route)
                            }
                        },
                    )
                }

                if (onManageRules != null) {
                    EpisodeSourceActionCard(
                        title = "播放源管理",
                        subtitle = "导入自备片单 / 维护解析规则",
                        iconVector = Icons.Filled.Settings,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            runAfterDismiss {
                                onManageRules()
                            }
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "管理规则",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 分组 1.5：AI 找源——解析可播放地址，检索在助手会话中显式触发
                Text(
                    text = "AI 找源",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                EpisodeSourceActionCard(
                    title = "让 AI 助手找源",
                    subtitle = "解析可播放地址与集数，结果在助手会话中展示",
                    iconVector = Icons.Filled.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { runAfterDismiss(onAiSourceSearch) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 分组 2：外部跳转
                Text(
                    text = "外部跳转",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                EpisodeSourceActionCard(
                    title = "哔哩哔哩",
                    subtitle = "在 B 站中搜索当前分集",
                    iconVector = Icons.Filled.Tv,
                    onClick = {
                        runAfterDismiss {
                            onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                        }
                    },
                )

                EpisodeSourceActionCard(
                    title = "蜜柑计划",
                    subtitle = "在蜜柑计划中查看 BT 资源与字幕组",
                    iconVector = Icons.Filled.Download,
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
                        title = "用 ${target.appName} 打开",
                        subtitle =
                            if (installed) {
                                "用 ${target.appName} 播放当前直链 · 外部播放无法携带 Referer 等请求头"
                            } else {
                                "未检测到 ${target.appName}，安装后可用 · 外部播放无法携带 Referer 等请求头"
                            },
                        iconVector = Icons.Filled.PlayCircleOutline,
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

    Text(
        text = "自备片单",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )

    for ((playlist, items) in grouped) {
        val header =
            if (items.all { it.matched }) {
                playlist.name
            } else {
                "${playlist.name} · 未按话数匹配，展示前 ${items.size} 条"
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
                            if (entry.kind == PlaylistEntryKind.DIRECT) "应用内播放片单直链" else "外部打开片单页面"
                        },
                        failure,
                    ),
                iconVector =
                    if (entry.kind == PlaylistEntryKind.DIRECT) {
                        Icons.Filled.PlayCircleOutline
                    } else {
                        Icons.AutoMirrored.Filled.OpenInNew
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
                                    episodeSort = if (episode.ep > 0f) episode.ep else episode.sort,
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
                                                        ?: (if (episode.ep > 0f) episode.ep else episode.sort),
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
internal fun playbackSourceSubtitle(
    base: String,
    failureReason: String?,
): String = if (failureReason == null) base else "$base · 上次播放失败：$failureReason"

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
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "打开",
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

/** 分集口语化编号：正片为「第 N 话」，其他类型为「SP 1」等分组前缀加序号 */
fun episodeGuideLabel(episode: Episode): String =
    if (episode.type == 0) {
        val num = if (episode.ep > 0f) episode.ep else episode.sort
        "第 ${num.toEpisodeLabel()} 话"
    } else {
        "${EpisodeGroup.fromType(episode.type).label} ${episode.sort.toInt()}"
    }

/**
 * 格式化分集播放源向导顶部标题：
 * 例如《葬送的芙莉莲》 第 5 话 · 死亡与安宁
 */
fun formatEpisodeGuideHeader(
    displayName: String,
    episode: Episode,
): String {
    val epLabel = episodeGuideLabel(episode)
    val rawTitle = episode.nameCn.ifBlank { episode.name }.trim()
    val numLabel = (if (episode.ep > 0f) episode.ep else episode.sort).toEpisodeLabel()
    val isRedundant =
        rawTitle.isBlank() ||
            rawTitle.filterNot { it.isWhitespace() }.equals(epLabel.filterNot { it.isWhitespace() }, ignoreCase = true) ||
            (episode.type == 0 && (rawTitle == numLabel || rawTitle == "第${numLabel}集" || rawTitle == "第${numLabel}话"))

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
