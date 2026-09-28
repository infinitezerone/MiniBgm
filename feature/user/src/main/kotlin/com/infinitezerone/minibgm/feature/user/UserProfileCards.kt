package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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
 *
 * 文案纪律（2026-09-28，「一行一义」）：头部只保留有区分度的身份信息。
 * - **用户名与 UID 去重**：Bangumi 未设置用户名时 `username` 会退回数字 UID，二者同义，
 *   原实现渲染为 `@1209850 · UID 1209850` 属纯重复
 * - **入站年份并入身份元信息行**，不再单独占一行
 * - **「Bangumi 会员」徽章不再展示**：注册用户人人皆是，零区分度；仅管理员保留标记，
 *   且内联在昵称之后，不另起一行
 * - **签名为空时整块不渲染**，不再用占位文案填充高度
 */
@Composable
internal fun UserProfileHero(
    profile: UserProfile?,
    savedAccountsCount: Int,
    onManageAccountsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = profile?.sign.orEmpty().trim()
    val username = profile?.username.orEmpty().trim()
    val isAdmin = profile?.userGroup == 11
    val uid = profile?.id ?: 0L
    val ambientGlowState = rememberAmbientDominantColorState()

    // 身份元信息合并为一行：@用户名 · UID · 入站年份。任一片段缺失即自动省略，不用占位符顶格。
    val metaText =
        buildString {
            // 纯数字 username 与 UID 同义（Bangumi 未设置用户名时的回退值），此处去重
            if (username.isNotBlank() && !username.all(Char::isDigit)) append("@$username")
            if (uid > 0L) {
                if (isNotEmpty()) append(" · ")
                append("UID $uid")
            }
            profile?.registeredYear?.let { year ->
                if (isNotEmpty()) append(" · ")
                val years = TimeUtils.currentCstYearMonth().first - year
                append(if (years >= 1) "$year 年加入 · 已 $years 年" else "$year 年加入")
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .ambientGlow(dominantColor = ambientGlowState.dominantColor)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile?.displayName?.ifBlank { "Bangumi 用户" } ?: "Bangumi 用户",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill = false：昵称按内容宽度收窄，管理员标记紧随其后，不被推到行尾
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AdminMark()
                    }
                }

                if (metaText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 多账号时提供就地切换入口（顶栏图标同名动作，此处直白写出「N 个账号」）
                if (savedAccountsCount > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountSwitchChip(
                        count = savedAccountsCount,
                        onClick = onManageAccountsClick,
                    )
                }
            }
        }

        // 个性签名：左侧主题色细线引导，随文本高度自适应。签名为空时整块不渲染。
        if (sign.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
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
                    text = sign,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 管理员标记：内联在昵称右侧的小尺寸 chip，普通注册用户不显示任何身份徽章 */
@Composable
private fun AdminMark(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "管理员",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** 账号切换胶囊：中性色 + 细描边，不与身份信息争夺注意力 */
@Composable
private fun AccountSwitchChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 9.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                text = "$count 个账号",
                style = MaterialTheme.typography.labelSmall,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 个人页第二层：通栏数字带。
 *
 * 形态纪律（见 `docs/PROFILE_HUB_REDESIGN.md` §3）：全页只有这一排同形数字。
 *
 * **本带不含「在看」**：该指标已由下方吸顶 Tab 承担，而 Tab 计数来自远端 legacy 统计、
 * 本带若也放一份则来自 Room 本地聚合——两者不同源，同步滞后时会并排出现同一指标的
 * 两个数字。因此本带只放 Tab 无法表达的累计量（累计追集 / 本月打卡），
 * 与 Tab 的分区计数互不重叠。两条细分隔线夹一条「带」，不读作卡片。
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
                .bounceClickable(state = bounceState, onClickLabel = "查看收藏明细") { onClick() },
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
