package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.common.intent.StreamingIntentResolver
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourceActionCard
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.BgmSourcesSectionLabel
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.feature.schedule.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

@Composable
fun ScheduleSourcesBottomSheet(
    schedule: AirSchedule,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAiSourceSearch: (() -> Unit)? = null,
    /** 应用内播放：由调用方按追番进度定位下一集并组装播放器路由 */
    onInternalPlayClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val displayName = schedule.displayName

    val bilibiliTarget =
        remember(displayName) {
            StreamingIntentResolver.buildBilibiliSearchTarget(displayName)
        }

    val mikanLink =
        remember(schedule.siteLinks) {
            schedule.siteLinks.firstOrNull { it.siteName.equals("mikan", ignoreCase = true) }
        }
    val mikanUrl =
        remember(mikanLink, displayName) {
            mikanLink?.playUrl ?: StreamingIntentResolver.buildMikanUrl(keyword = displayName)
        }

    BgmSourcesBottomSheet(
        title = displayName,
        coverUrl = schedule.coverUrl,
        subtitle = stringResource(R.string.feature_schedule_source_sheet_subtitle),
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) { runAfterDismiss ->
        if (onInternalPlayClick != null) {
            BgmSourcesSectionLabel(stringResource(R.string.feature_schedule_source_internal_player_section))

            BgmSourceActionCard(
                title = stringResource(R.string.feature_schedule_source_internal_player_title),
                subtitle = stringResource(DesignSystemR.string.core_designsystem_sources_internal_player_subtitle),
                iconVector = BgmIcons.PlayCircle,
                trailingContent = {
                    Icon(
                        imageVector = BgmIcons.KeyboardArrowRight,
                        contentDescription = stringResource(DesignSystemR.string.core_designsystem_sources_action_play),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = { runAfterDismiss(onInternalPlayClick) },
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        if (onAiSourceSearch != null) {
            BgmSourcesSectionLabel(stringResource(R.string.feature_schedule_source_ai_section))

            BgmSourceActionCard(
                title = stringResource(DesignSystemR.string.core_designsystem_sources_ai_search_title),
                subtitle = stringResource(DesignSystemR.string.core_designsystem_sources_ai_search_subtitle),
                iconVector = BgmIcons.AutoAwesome,
                onClick = { runAfterDismiss(onAiSourceSearch) },
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        BgmSourcesSectionLabel(stringResource(R.string.feature_schedule_source_external_section))

        // 源 1：哔哩哔哩 (Bilibili)
        BgmSourceActionCard(
            title = stringResource(R.string.feature_schedule_source_bilibili_title),
            subtitle = stringResource(DesignSystemR.string.core_designsystem_sources_bilibili_subtitle),
            iconVector = BgmIcons.Tv,
            onClick = {
                runAfterDismiss {
                    val targetUrl =
                        bilibiliTarget.deepLinkUri
                            ?: bilibiliTarget.webFallbackUrl
                    onOpenUrl(targetUrl)
                }
            },
        )

        // 源 2：蜜柑计划 (Mikan 社区磁力)
        BgmSourceActionCard(
            title = stringResource(R.string.feature_schedule_source_mikan_title),
            subtitle = stringResource(DesignSystemR.string.core_designsystem_sources_mikan_subtitle),
            iconVector = BgmIcons.Download,
            onClick = { runAfterDismiss { onOpenUrl(mikanUrl) } },
        )
    }
}
