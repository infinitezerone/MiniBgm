package com.infinitezerone.minibgm.feature.schedule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmStatusState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.schedule.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

@Composable
fun ScheduleDayEmptyNote(
    onlyWatching: Boolean,
    onSwitchToAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    if (onlyWatching) {
                        stringResource(R.string.feature_schedule_empty_watchlist)
                    } else {
                        stringResource(R.string.feature_schedule_empty_day)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            if (onlyWatching && onSwitchToAll != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSwitchToAll,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.feature_schedule_btn_view_all_broadcast))
                }
            }
        }
    }
}

@Composable
fun OfflineCacheBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.feature_schedule_offline_cache_tip),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = BgmIcons.Refresh,
                    contentDescription = stringResource(DesignSystemR.string.core_designsystem_action_retry),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun ScheduleErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BgmStatusState(
        message = errorMessage,
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        title = stringResource(R.string.feature_schedule_load_failed_title),
        icon = BgmIcons.CloudOff,
        iconTint = MaterialTheme.colorScheme.error,
        actionLabel = stringResource(DesignSystemR.string.core_designsystem_action_retry),
        onAction = onRetry,
    )
}
