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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.Subject
import kotlinx.coroutines.delay
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
    isSniffing: Boolean = false,
    onAiSniff: (() -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    playbackRules: List<PlaybackSourceRule> = emptyList(),
) {
    if (episode != null) {
        EpisodeSourceGuideBottomSheet(
            subject = subject,
            episode = episode,
            onDismissRequest = onDismissRequest,
            onOpenUrl = onOpenUrl,
            modifier = modifier,
            mikanId = mikanId,
            isSniffing = isSniffing,
            onAiSniff = { onAiSniff?.invoke() },
            onManageRules = onManageRules,
            playbackRules = playbackRules,
        )
        return
    }

    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = subject.displayName

    val bilibiliTarget =
        remember(displayName) {
            StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
        }

    val mikanUrl =
        remember(mikanId, displayName) {
            StreamingIntentResolver.buildMikanUrl(mikanId = mikanId, keyword = displayName)
        }

    var localScanning by rememberSaveable { mutableStateOf(false) }
    val scanning = isSniffing || localScanning

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
                // 分组 1：内部播放
                Text(
                    text = "内部播放",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                EpisodeSourceActionCard(
                    title = "应用内播放",
                    subtitle =
                        if (scanning) {
                            "正在检索可用播放直链..."
                        } else {
                            "尝试在应用内解析并播放该番剧"
                        },
                    iconVector = Icons.Filled.PlayCircleOutline,
                    iconTint = MaterialTheme.colorScheme.primary,
                    trailingContent = {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "开始播放",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        if (!scanning) {
                            if (onAiSniff != null) {
                                onAiSniff()
                            } else {
                                localScanning = true
                                coroutineScope.launch {
                                    delay(1500)
                                    localScanning = false
                                }
                            }
                        }
                    },
                )

                if (onManageRules != null) {
                    EpisodeSourceActionCard(
                        title = "自定义播放规则",
                        subtitle = "导入与管理第三方解析规则",
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
