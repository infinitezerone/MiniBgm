package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmGhostIconButton
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.delay

/**
 * 播放器内置续播提示浮层（从系统 Scaffold Snackbar 迁入视频舞台内）：
 * 位于视频画面左下角，呈现「已从上次播放位置继续播放 hh:mm:ss [从头播放] ✕」，
 * 5 秒后自动淡出，点击 [从头播放] 或 ✕ 立即响应并收起。
 */
@Composable
internal fun PlayerResumeOverlay(
    positionMs: Long,
    onSeekToBeginning: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(positionMs) {
        delay(5000L)
        onDismiss()
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_resume_position, formatDuration(positionMs)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = onSeekToBeginning,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_player_start_from_beginning),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BgmGhostIconButton(
                onClick = onDismiss,
                icon = BgmIcons.Close,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                iconSize = 14.dp,
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
