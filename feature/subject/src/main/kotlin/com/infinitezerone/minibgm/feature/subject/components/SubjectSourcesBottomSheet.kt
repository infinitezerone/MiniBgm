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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.forSubject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectSourcesBottomSheet(
    subject: Subject,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    episode: Episode? = null,
    mikanId: String? = null,
    onAiSourceSearch: () -> Unit = {},
    onManageRules: (() -> Unit)? = null,
    playbackRules: List<PlaybackSourceRule> = emptyList(),
    playlists: List<PlaybackPlaylist> = emptyList(),
    failedSourceReasons: Map<String, String> = emptyMap(),
) {
    if (episode != null) {
        EpisodeSourceGuideBottomSheet(
            subject = subject,
            episode = episode,
            onDismissRequest = onDismissRequest,
            onOpenUrl = onOpenUrl,
            modifier = modifier,
            mikanId = mikanId,
            onAiSourceSearch = onAiSourceSearch,
            onManageRules = onManageRules,
            playbackRules = playbackRules,
            playlists = playlists,
            failedSourceReasons = failedSourceReasons,
        )
        return
    }

    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = subject.displayName
    val boundPlaylists = remember(playlists, subject.id) { playlists.forSubject(subject.id) }

    val bilibiliTarget =
        remember(displayName) {
            StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
        }

    val mikanUrl =
        remember(mikanId, displayName) {
            StreamingIntentResolver.buildMikanUrl(mikanId = mikanId, keyword = displayName)
        }

    val runAfterDismiss: (() -> Unit) -> Unit = { action ->
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismissRequest()
            action()
        }
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
            // 头部条目信息
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
                    Text(
                        text = displayName,
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
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
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

            // 滚动内容区
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
                    Text(
                        text = "自备片单",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    for (playlist in boundPlaylists) {
                        val hasFailure =
                            playlist.entries.any { it.url in failedSourceReasons }
                        EpisodeSourceActionCard(
                            title = playlist.name,
                            subtitle =
                                playbackSourceSubtitle(
                                    "${playlist.entries.size} 条 · 在具体分集的播放入口中选择",
                                    if (hasFailure) "存在上次播放失败的条目" else null,
                                ),
                            iconVector = Icons.Filled.PlayCircleOutline,
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

                // 分组 1：AI 找源（条目级无具体分集，交由助手会话解析可播放清单）
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

                if (onManageRules != null) {
                    EpisodeSourceActionCard(
                        title = "播放源管理",
                        subtitle = "导入自备片单 / 维护解析规则",
                        iconVector = Icons.Filled.Settings,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onManageRules() },
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
                    subtitle = "打开 B 站客户端/网页搜索",
                    iconVector = Icons.Filled.Tv,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            onOpenUrl(bilibiliTarget.deepLinkUri ?: bilibiliTarget.webFallbackUrl)
                        }
                    },
                )

                EpisodeSourceActionCard(
                    title = "蜜柑计划",
                    subtitle = "在蜜柑计划中查看 BT 资源与字幕组",
                    iconVector = Icons.Filled.Download,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            onOpenUrl(mikanUrl)
                        }
                    },
                )
            }
        }
    }
}
