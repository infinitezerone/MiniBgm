package com.infinitezerone.minibgm.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmLoginPromptDialog
import com.infinitezerone.minibgm.core.designsystem.component.BgmStatusState
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonBox
import com.infinitezerone.minibgm.core.designsystem.component.SkeletonState
import com.infinitezerone.minibgm.core.designsystem.component.rememberSkeletonState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.search.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/** 骨架屏加载状态 */
@Composable
fun SearchSkeletonLoading(
    modifier: Modifier = Modifier,
    skeletonState: SkeletonState = rememberSkeletonState(),
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBox(
                        modifier =
                            Modifier
                                .width(74.dp)
                                .height(104.dp),
                        shape = RoundedCornerShape(8.dp),
                        state = skeletonState,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(18.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                        SkeletonBox(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            state = skeletonState,
                        )
                    }
                }
            }
        }
    }
}

/** 无结果引导状态 */
@Composable
fun SearchNoResultsState(
    query: String,
    selectedType: Int,
    onResetCategory: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = BgmIcons.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.feature_search_no_results_title, query),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.feature_search_no_results_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedType != 0) {
                Button(onClick = onResetCategory) {
                    Text(stringResource(R.string.feature_search_action_reset_category))
                }
            }
            OutlinedButton(onClick = onClearQuery) {
                Text(stringResource(R.string.feature_search_action_clear_and_search))
            }
        }
    }
}

/** 错误重试状态 */
@Composable
fun SearchErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BgmStatusState(
        message = errorMessage,
        modifier = modifier.padding(28.dp),
        title = stringResource(R.string.feature_search_error_title),
        titleColor = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(DesignSystemR.string.core_designsystem_action_retry),
        onAction = onRetry,
    )
}

/** 登录提示引导弹窗 */
@Composable
fun SearchLoginDialog(
    onDismiss: () -> Unit,
    onConfirmLogin: () -> Unit,
) {
    BgmLoginPromptDialog(
        title = stringResource(R.string.feature_search_login_dialog_title),
        description = stringResource(R.string.feature_search_login_dialog_desc),
        onLogin = onConfirmLogin,
        onDismiss = onDismiss,
    )
}
