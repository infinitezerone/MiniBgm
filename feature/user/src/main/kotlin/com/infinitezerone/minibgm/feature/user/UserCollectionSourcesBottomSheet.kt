package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.infinitezerone.minibgm.core.model.UserCollection
import com.infinitezerone.minibgm.core.navigation.SubjectDetailRoute

/**
 * 个人中心追番条目播放与选源快捷面板：
 * - 针对当前在看条目与下一待播集数，提供就地跳转 B 站搜索、蜜柑计划下载、
 *   以及直达条目详情页的完整选源体验。
 */
@Composable
internal fun UserCollectionSourcesBottomSheet(
    collection: UserCollection,
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSubjectClick: (SubjectDetailRoute) -> Unit,
    modifier: Modifier = Modifier,
    isBinge: Boolean = false,
    onToggleBinge: (() -> Unit)? = null,
) {
    val subject = collection.subject
    val displayName =
        subject?.displayName ?: stringResource(R.string.feature_user_subject_fallback_title, collection.subjectId)
    val coverUrl = subject?.images?.bestImage.orEmpty()
    val eps = subject?.eps ?: 0
    val totalEps = subject?.totalEpisodes?.takeIf { it > 0 } ?: eps
    val epStatus = collection.epStatus
    val nextEp = epStatus + 1
    val isFinished = remember(collection) { collection.isFinished() }

    val searchKeyword =
        if (nextEp > 1 || (totalEps > 0 && nextEp <= totalEps)) {
            stringResource(R.string.feature_user_source_search_name_ep, displayName, nextEp)
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

    val subtitle =
        when {
            isFinished && totalEps > 0 && epStatus >= totalEps ->
                stringResource(R.string.feature_user_source_badge_all_watched, totalEps)
            isFinished && totalEps > 0 ->
                stringResource(
                    R.string.feature_user_source_badge_finished_to_ep,
                    nextEp,
                    totalEps,
                )
            totalEps > 0 && nextEp <= totalEps ->
                stringResource(R.string.feature_user_source_badge_resume_total, nextEp, totalEps)
            epStatus > 0 -> stringResource(R.string.feature_user_source_badge_resume, nextEp)
            else -> stringResource(R.string.feature_user_source_badge_start)
        }

    BgmSourcesBottomSheet(
        title = displayName,
        coverUrl = coverUrl,
        subtitle = subtitle,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) { runAfterDismiss ->
        BgmSourcesSectionLabel(stringResource(R.string.feature_user_source_quick_jump))

        // 选项 1：哔哩哔哩搜索
        BgmSourceActionCard(
            title = stringResource(R.string.feature_user_source_bilibili_search),
            iconVector = BgmIcons.Tv,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = {
                runAfterDismiss {
                    val targetUrl =
                        bilibiliTarget.deepLinkUri
                            ?: bilibiliTarget.webFallbackUrl
                    onOpenUrl(targetUrl)
                }
            },
        )

        // 选项 2：蜜柑计划
        BgmSourceActionCard(
            title = stringResource(R.string.feature_user_source_mikan),
            iconVector = BgmIcons.Download,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = {
                runAfterDismiss {
                    onOpenUrl(mikanUrl)
                }
            },
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (onToggleBinge != null && (collection.subjectType == 2 || collection.subjectType == 6) && !isFinished) {
            BgmSourcesSectionLabel(stringResource(R.string.feature_user_source_preference))

            BgmSourceActionCard(
                title =
                    if (isBinge) {
                        stringResource(R.string.feature_user_source_binge_on)
                    } else {
                        stringResource(R.string.feature_user_source_binge_off)
                    },
                iconVector = if (isBinge) BgmIcons.Inventory else BgmIcons.InventoryBorder,
                iconTint = MaterialTheme.colorScheme.primary,
                trailingContent = {
                    Switch(
                        checked = isBinge,
                        onCheckedChange = { onToggleBinge() },
                    )
                },
                onClick = onToggleBinge,
            )

            Spacer(modifier = Modifier.height(6.dp))
        }

        BgmSourcesSectionLabel(stringResource(R.string.feature_user_source_subject_detail))

        // 选项 3：前往条目详情
        BgmSourceActionCard(
            title = stringResource(R.string.feature_user_source_enter_detail),
            iconVector = BgmIcons.Info,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = {
                runAfterDismiss {
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
