package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.sortedBySitePriority
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSourcesBottomSheet(
    schedule: AirSchedule,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAiSourceSearch: () -> Unit = {},
    onInternalPlayClick: ((PlayerRoute) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = schedule.titleCn.ifBlank { schedule.title }

    val bilibiliTarget =
        remember(displayName) {
            StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
        }
    val bilibiliOfficialLink =
        remember(schedule.siteLinks) {
            schedule.siteLinks.firstOrNull { it.siteName.equals("bilibili", ignoreCase = true) }
        }

    val mikanLink =
        remember(schedule.siteLinks) {
            schedule.siteLinks.firstOrNull { it.siteName.equals("mikan", ignoreCase = true) }
        }
    val mikanUrl =
        remember(mikanLink, displayName) {
            mikanLink?.playUrl ?: StreamingIntentResolver.buildMikanUrl(keyword = displayName)
        }

    val otherLinks =
        remember(schedule.siteLinks) {
            schedule.siteLinks
                .filterNot {
                    it.siteName.equals("bilibili", ignoreCase = true) ||
                        it.siteName.equals("mikan", ignoreCase = true)
                }.sortedBySitePriority()
        }

    var isOtherExpanded by remember { mutableStateOf(false) }

    // 一体化播放器路由：episodeId=0 时由播放器按规则源自主嗅探（与条目页「一体化播放器」同语义）
    val internalPlayRoute =
        remember(schedule.bgmId, displayName) {
            PlayerRoute(subjectId = schedule.bgmId, episodeId = 0L, subjectName = displayName)
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
            // 头部番剧信息
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
                        url = schedule.coverUrl,
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

            // 外部跳转列表
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onInternalPlayClick != null) {
                    Text(
                        text = "应用内播放",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )

                    ScheduleSourceCard(
                        title = "用内置播放器播放",
                        subtitle = "按播放规则自动嗅探可播地址并连播",
                        iconVector = Icons.Filled.PlayCircleOutline,
                        onClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismissRequest()
                                onInternalPlayClick(internalPlayRoute)
                            }
                        },
                    )
                }

                Text(
                    text = "AI 找源",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                ScheduleSourceCard(
                    title = "让 AI 助手找源",
                    subtitle = "解析可播放地址与集数，结果在助手会话中展示",
                    iconVector = Icons.Filled.AutoAwesome,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            onAiSourceSearch()
                        }
                    },
                )

                Text(
                    text = "外部跳转",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                // 源 1：哔哩哔哩 (Bilibili)
                ScheduleSourceCard(
                    title = "哔哩哔哩",
                    subtitle =
                        if (bilibiliOfficialLink != null) {
                            "打开 B 站观看正版番剧"
                        } else {
                            "打开 B 站客户端/网页搜索"
                        },
                    iconVector = Icons.Filled.Tv,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            val targetUrl =
                                bilibiliOfficialLink?.playUrl
                                    ?: bilibiliTarget.deepLinkUri
                                    ?: bilibiliTarget.webFallbackUrl
                            onOpenUrl(targetUrl)
                        }
                    },
                )

                // 源 2：蜜柑计划 (Mikan)
                ScheduleSourceCard(
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

                // 其他播放渠道（默认收纳）
                if (otherLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        onClick = { isOtherExpanded = !isOtherExpanded },
                        shape = BgmShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "更多外部源 (${otherLinks.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Icon(
                                imageVector =
                                    if (isOtherExpanded) {
                                        Icons.Filled.KeyboardArrowUp
                                    } else {
                                        Icons.Filled.KeyboardArrowDown
                                    },
                                contentDescription = if (isOtherExpanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isOtherExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            otherLinks.forEach { siteLink ->
                                ScheduleSourceCard(
                                    title = siteLink.displayName,
                                    subtitle = "打开外部播放渠道",
                                    iconVector = Icons.Filled.PlayCircleOutline,
                                    onClick = {
                                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                            onDismissRequest()
                                            onOpenUrl(siteLink.playUrl)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSourceCard(
    title: String,
    subtitle: String,
    iconVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "打开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
