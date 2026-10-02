package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute
import kotlinx.coroutines.launch

/**
 * 个人中心追番条目播放与选源快捷面板：
 * - 针对当前在看条目与下一待播集数，提供就地跳转 B 站搜索、蜜柑计划下载、
 *   以及直达条目详情页的完整选源体验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserCollectionSourcesBottomSheet(
    collection: UserCollection,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val subject = collection.subject
    val displayName = subject?.displayName ?: "条目 #${collection.subjectId}"
    val coverUrl = subject?.images?.bestImage.orEmpty()
    val eps = subject?.eps ?: 0
    val totalEps = subject?.totalEpisodes?.takeIf { it > 0 } ?: eps
    val epStatus = collection.epStatus
    val nextEp = epStatus + 1

    val searchKeyword =
        if (nextEp > 1 || (totalEps > 0 && nextEp <= totalEps)) {
            "$displayName 第 $nextEp 话"
        } else {
            displayName
        }

    val bilibiliTarget =
        remember(searchKeyword) {
            StreamingIntentResolver.buildBilibiliSearchTarget(searchKeyword)
        }

    val mikanUrl =
        remember(searchKeyword) {
            StreamingIntentResolver.buildMikanUrl(keyword = searchKeyword)
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
            // 头部：番剧封面、标题与续播集数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 46.dp, height = 62.dp)
                            .clip(BgmShapes.small),
                ) {
                    CoverImage(
                        url = coverUrl,
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text =
                            when {
                                totalEps > 0 && nextEp <= totalEps -> "续看第 $nextEp 话（全 $totalEps 话）"
                                epStatus > 0 -> "续看第 $nextEp 话"
                                else -> "开始观看第 1 话"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
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

            // 外部播放与选源选项
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "快速跳转与搜索",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                // 选项 1：哔哩哔哩搜索
                UserSourceOptionCard(
                    title = "哔哩哔哩搜索",
                    iconVector = Icons.Filled.Tv,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            val targetUrl =
                                bilibiliTarget.deepLinkUri
                                    ?: bilibiliTarget.webFallbackUrl
                            onOpenUrl(targetUrl)
                        }
                    },
                )

                // 选项 2：蜜柑计划
                UserSourceOptionCard(
                    title = "蜜柑计划",
                    iconVector = Icons.Filled.Download,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            onOpenUrl(mikanUrl)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "条目详情",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                // 选项 3：前往条目详情
                UserSourceOptionCard(
                    title = "进入条目详情",
                    iconVector = Icons.Filled.Info,
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismissRequest()
                            onSubjectClick(
                                SubjectDetailRoute(
                                    subjectId = collection.subjectId,
                                    initialName = displayName,
                                    initialCoverUrl = coverUrl,
                                    initialScore = subject?.rating?.score ?: 0.0,
                                    source = "user_sources",
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UserSourceOptionCard(
    title: String,
    iconVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BgmShapes.medium)
                .clickable(onClick = onClick),
        shape = BgmShapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = BgmShapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
