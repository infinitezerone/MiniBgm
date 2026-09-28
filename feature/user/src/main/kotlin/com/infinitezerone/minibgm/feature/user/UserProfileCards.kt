package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.data.repository.TrackingFootprint
import com.infinitezerone.minibgm.core.designsystem.ambient.ambientGlow
import com.infinitezerone.minibgm.core.designsystem.ambient.rememberAmbientDominantColorState
import com.infinitezerone.minibgm.core.designsystem.component.bounceClickable
import com.infinitezerone.minibgm.core.designsystem.component.rememberBounceOnClick
import com.infinitezerone.minibgm.core.model.UserProfile

/**
 * 个人页第一层：沉浸式身份头部。
 *
 * 不做卡片外壳——身份块、数字带、分区 Tab 应当读作同一个「个人页头部」，而不是三张并列的卡。
 * 背景保留头像主色的氛围光（[ambientGlow]），提取失败时自动退化为无光晕，绝不影响主体内容。
 * 账号管理入口统一收敛到顶栏与「N 个账号」胶囊，头部不再重复铺设按钮。
 */
@Composable
internal fun UserProfileHero(
    profile: UserProfile?,
    savedAccountsCount: Int,
    onManageAccountsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = profile?.sign.orEmpty()
    val username = profile?.username.orEmpty()
    val isAdmin = profile?.userGroup == 11
    val ambientGlowState = rememberAmbientDominantColorState()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .ambientGlow(dominantColor = ambientGlowState.dominantColor)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 头像：主色调内环。88dp —— 个人页头部的视觉锚点，比列表场景再大一档
            Surface(
                shape = CircleShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                val avatarUrl = profile?.avatar?.bestAvatar.orEmpty()
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Crop,
                        onSuccess = ambientGlowState::onImageSuccess,
                        onError = { ambientGlowState.onImageError() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.displayName?.ifBlank { "Bangumi 用户" } ?: "Bangumi 用户",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 身份元信息收敛为一行：@用户名 · UID
                val uid = profile?.id ?: 0L
                val metaLine =
                    buildString {
                        if (username.isNotBlank()) append("@$username")
                        if (uid > 0L) {
                            if (isNotEmpty()) append(" · ")
                            append("UID $uid")
                        }
                    }
                if (metaLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 入站年限：对齐主流个人页把注册时间作为次要元信息单独成行的做法，不占统计格
                profile?.registeredYear?.let { year ->
                    val years = TimeUtils.currentCstYearMonth().first - year
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (years >= 1) "$year 年加入 · 已 $years 年" else "$year 年加入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 会员 / 管理员徽章
                    Surface(
                        shape = RoundedCornerShape(50),
                        color =
                            if (isAdmin) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint =
                                    if (isAdmin) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isAdmin) "管理员" else "Bangumi 会员",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (isAdmin) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                            )
                        }
                    }

                    if (savedAccountsCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onManageAccountsClick),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "$savedAccountsCount 个账号",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 个性签名：左侧主题色细线引导，随文本高度自适应，替代灰底气泡
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = sign.ifBlank { "这个人很神秘，什么都没写~" },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (sign.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                fontStyle = if (sign.isBlank()) FontStyle.Italic else FontStyle.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 个人页第二层：通栏数字带。
 *
 * 形态纪律（见 `docs/PROFILE_HUB_REDESIGN.md` §3）：全页只有这一排同形数字，
 * 不再叠加第二排数字格。三条等分 + 上下细分隔线，读作一条「带」而不是一张卡。
 * 点击整条跳转到「在看」分区列表。
 */
@Composable
internal fun TrackingStatsRow(
    footprint: TrackingFootprint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounceState = rememberBounceOnClick(pressedScale = 0.98f)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .bounceClickable(state = bounceState, onClickLabel = "查看看番足迹") { onClick() },
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackingStatCell(
                label = "在看",
                value = footprint.watchingCount.toString(),
                modifier = Modifier.weight(1f),
            )
            TrackingStatCell(
                label = "累计追集",
                value = footprint.episodesWatched.toString(),
                modifier = Modifier.weight(1f),
            )
            TrackingStatCell(
                label = "本月打卡",
                value = footprint.monthActiveCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        // 最近打卡：装饰性信息，解析失败或缺失时整行不渲染（fail-open）
        formatLastActiveAt(footprint.lastActiveAtIso)?.let { lastActive ->
            Text(
                text = "最近打卡 · $lastActive",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun TrackingStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 最近打卡时刻 → 相对时间文案；无数据或解析失败返回 null */
private fun formatLastActiveAt(lastActiveAtIso: String?): String? {
    if (lastActiveAtIso.isNullOrBlank()) return null
    val days = TimeUtils.daysSinceIsoUtc(lastActiveAtIso) ?: return null
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        days < 30 -> "$days 天前"
        else -> TimeUtils.formatIsoToCstDate(lastActiveAtIso)
    }
}
