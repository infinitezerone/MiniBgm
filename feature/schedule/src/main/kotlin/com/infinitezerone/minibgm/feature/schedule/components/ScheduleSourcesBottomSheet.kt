package com.infinitezerone.minibgm.feature.schedule.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.CoverImage
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.SiteLink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSourcesBottomSheet(
    schedule: AirSchedule,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val displayName = schedule.titleCn.ifBlank { schedule.title }
    val sortedLinks =
        remember(schedule.siteLinks) {
            val priorityOrder =
                listOf(
                    "bilibili",
                    "gamer",
                    "gamer_hk",
                    "bahamut",
                    "iqiyi",
                    "qq",
                    "youku",
                    "mikan",
                    "muse_tw",
                    "muse_hk",
                    "ani_one",
                    "ani_one_asia",
                    "netflix",
                    "disneyplus",
                    "crunchyroll",
                    "abema",
                    "danime",
                    "unext",
                    "prime",
                    "nicovideo",
                )
            schedule.siteLinks.distinctBy { it.displayName }.sortedBy { link ->
                val index = priorityOrder.indexOf(link.siteName.lowercase())
                if (index >= 0) index else 100
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp)),
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "共 ${sortedLinks.size} 个播放渠道与资源链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(12.dp))

            // 播放源列表：使用带高度边界约束的纵向滚动 Column 代替 LazyColumn，彻底杜绝嵌套滑动导致的持续弹跳抖动
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortedLinks.forEach { siteLink ->
                    SourceListItem(
                        siteLink = siteLink,
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

@Composable
private fun SourceListItem(
    siteLink: SiteLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle =
        when (siteLink.siteName.lowercase()) {
            "bilibili" -> "中国大陆正版放送"
            "gamer", "gamer_hk", "bahamut" -> "中国港澳台正版 · 巴哈姆特动画疯"
            "muse_tw", "muse_hk" -> "木棉花官方频道"
            "ani_one", "ani_one_asia" -> "羚邦官方频道"
            "iqiyi" -> "爱奇艺动漫"
            "qq" -> "腾讯视频动漫"
            "youku" -> "优酷动漫"
            "mikan" -> "蜜柑计划 · BT 资源与字幕组"
            "netflix" -> "Netflix 全球流媒体"
            "disneyplus" -> "Disney+ 流媒体"
            "crunchyroll" -> "Crunchyroll 欧美流媒体"
            "nicovideo" -> "NicoNico 动画（日本地区）"
            "abema" -> "ABEMA TV（日本地区）"
            "danime" -> "d动画商城（日本地区）"
            "unext" -> "U-NEXT（日本地区）"
            "prime" -> "Amazon Prime Video"
            else -> "外部正版流媒体平台"
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = siteLink.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
