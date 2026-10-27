package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.delay

/**
 * 播放器内置续播提示浮层（从系统 Scaffold Snackbar 迁入视频舞台内）：
 * 位于视频画面左下角，呈现极简胶囊「已定位到 hh:mm:ss [从头播放]」，
 * 3 秒后自动淡出，点击 [从头播放] 立即响应并收起。
 */
@Composable
internal fun PlayerResumeOverlay(
    positionMs: Long,
    onSeekToBeginning: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(positionMs) {
        delay(3000L)
        onDismiss()
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = 0.55f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_subject_player_resume_position, formatDuration(positionMs)),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                    ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(2.dp))
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
        }
    }
}
