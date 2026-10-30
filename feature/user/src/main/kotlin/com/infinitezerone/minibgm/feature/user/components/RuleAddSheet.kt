package com.infinitezerone.minibgm.feature.user.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmFlatRow
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.hideThenDismiss
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmShapes
import com.infinitezerone.minibgm.feature.user.R

/**
 * 「添加播放源」的四个入口。
 *
 * 文案分两层：标题说**是什么**，副标题说**什么时候用它**——四条路径的差别（有没有订阅链接、
 * 只有一个网址、要自己填模板、手上是一段 JSON）对用户不是自明的，只给标题等于让人猜。
 */
internal enum class RuleAddOption(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
) {
    SUBSCRIPTION(
        titleRes = R.string.feature_user_rule_choice_subscription,
        descRes = R.string.feature_user_rule_choice_subscription_desc,
        icon = BgmIcons.Sync,
    ),
    SITE_PROBE(
        titleRes = R.string.feature_user_rule_choice_site_probe,
        descRes = R.string.feature_user_rule_choice_site_probe_desc,
        icon = BgmIcons.Explore,
    ),
    ADD_TORRENT(
        titleRes = R.string.feature_user_rule_choice_add_torrent,
        descRes = R.string.feature_user_rule_choice_add_torrent_desc,
        icon = BgmIcons.Download,
    ),
    IMPORT_JSON(
        titleRes = R.string.feature_user_rule_choice_import_json,
        descRes = R.string.feature_user_rule_choice_import_json_desc,
        icon = BgmIcons.ContentPaste,
    ),
}

/**
 * 添加播放源的入口菜单（底栏）。
 *
 * 之前这层是「FAB → 单选弹窗 → 再弹对话框」：单选按钮的语义是"选完再确认"，实际点一下就跳走，
 * 用户还得先猜四个名字的区别。底栏把图标、标题、适用场景一次铺开，点哪儿去哪儿。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleAddSheet(
    onSelect: (RuleAddOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.feature_user_rule_add_choice_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            RuleAddOption.entries.forEach { option ->
                BgmFlatRow(
                    title = stringResource(option.titleRes),
                    subtitle = stringResource(option.descRes),
                    onClick = { scope.hideThenDismiss(sheetState) { onSelect(option) } },
                    leading = { RuleAddOptionIcon(option.icon) },
                )
            }
        }
    }
}

@Composable
private fun RuleAddOptionIcon(icon: ImageVector) {
    Surface(
        shape = BgmShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
