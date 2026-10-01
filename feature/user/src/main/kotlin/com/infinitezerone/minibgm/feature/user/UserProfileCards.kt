package com.infinitezerone.minibgm.feature.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.infinitezerone.minibgm.core.common.TimeUtils
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

        // 个性签名：轻柔微气泡底色包裹，随文本高度自适应。签名为空时整块不渲染。
        if (sign.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = sign,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

/** 数字尚未就绪时的占位符：宁可显示「—」，也不要让整条数字带凭空出现把下方顶下去 */
private const val STAT_PLACEHOLDER = "—"

/**
 * 个人页第二层：黄金三维统计岛屿（Stat Island）。
 *
 * 契合二次元追番社区的核心用户资产维度：
 * 1.「看过」—— 累计完结的动画资产（历史沉淀）；
 * 2.「在追」—— 当前活跃追番负荷（当下状态）；
 * 3.「想看」—— 待补新番心愿单储备（未来期待）。
 *
 * 零额外 API 开销：直接消费 [counts]（官方单接口 status 返回的真实汇总）。
 * 视觉形态：一体化圆角岛屿卡片，彻底消灭两条生硬贯穿全屏的表格分隔细线；
 * 交互联动：点击任一维度平滑联动切换下方对应 Tab 并滚动到位。
 */
@Composable
internal fun UserStatsIsland(
    counts: Map<com.infinitezerone.minibgm.core.model.CollectionType, Int>,
    onSelectType: (com.infinitezerone.minibgm.core.model.CollectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectCount = counts[com.infinitezerone.minibgm.core.model.CollectionType.COLLECT]?.toString() ?: STAT_PLACEHOLDER
    val doingCount = counts[com.infinitezerone.minibgm.core.model.CollectionType.DOING]?.toString() ?: STAT_PLACEHOLDER
    val wishCount = counts[com.infinitezerone.minibgm.core.model.CollectionType.WISH]?.toString() ?: STAT_PLACEHOLDER

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserStatCell(
                label = "看过",
                value = collectCount,
                onClick = { onSelectType(com.infinitezerone.minibgm.core.model.CollectionType.COLLECT) },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            )
            UserStatCell(
                label = "在追",
                value = doingCount,
                onClick = { onSelectType(com.infinitezerone.minibgm.core.model.CollectionType.DOING) },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            )
            UserStatCell(
                label = "想看",
                value = wishCount,
                onClick = { onSelectType(com.infinitezerone.minibgm.core.model.CollectionType.WISH) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UserStatCell(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounceState = rememberBounceOnClick(pressedScale = 0.95f)
    Column(
        modifier =
            modifier
                .bounceClickable(state = bounceState, onClickLabel = "查看$label") { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
