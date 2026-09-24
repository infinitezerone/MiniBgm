package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 播放器画面缩放模式
 */
enum class PlayerResizeMode(
    val label: String,
) {
    FIT("适应"),
    ZOOM("裁剪"),
    FILL("拉伸"),
}

/**
 * 极简现代播放器控制遮罩
 */
@Composable
internal fun PlayerControlsOverlay(
    subjectName: String,
    epLabel: String,
    episodeName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isEnded: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isScrubbing: Boolean,
    scrubProgress: Float,
    isLandscape: Boolean,
    resizeMode: PlayerResizeMode,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubbing: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onEnterPip: () -> Unit,
    onRetry: () -> Unit,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    showEpisodeQueue: Boolean,
    onOpenEpisodeQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        // 顶部操作栏
        val topBarModifier =
            if (isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                        ),
                    ).statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                        ),
                    ).padding(horizontal = 8.dp, vertical = 4.dp)
            }

        Row(
            modifier = topBarModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subText = if (episodeName.isNotBlank()) "$epLabel · $episodeName" else epLabel
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 比例缩放模式切换
            IconButton(onClick = onCycleResizeMode) {
                Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = "画面比例: ${resizeMode.label}",
                    tint = Color.White,
                )
            }

            // 画中画模式
            IconButton(onClick = onEnterPip) {
                Icon(
                    imageVector = Icons.Filled.PictureInPictureAlt,
                    contentDescription = "画中画",
                    tint = Color.White,
                )
            }
        }

        // 中央播放控制与缓冲状态
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Button(onClick = onRetry) {
                        Text("重试播放")
                    }
                }
            } else if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    IconButton(
                        onClick = onRewind10,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "快退 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier =
                            Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.2f), shape = MaterialTheme.shapes.extraLarge),
                    ) {
                        val icon =
                            when {
                                isEnded -> Icons.Filled.Replay
                                isPlaying -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            }
                        Icon(
                            imageVector = icon,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    IconButton(
                        onClick = onForward10,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "快进 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // 底部控制栏（进度条、时长、全屏切换）
        val bottomBarModifier =
            if (isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    ).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    ).padding(horizontal = 16.dp, vertical = 4.dp)
            }

        Column(modifier = bottomBarModifier) {
            val progress =
                if (isScrubbing) {
                    scrubProgress
                } else if (totalDuration > 0L) {
                    (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

            // 拖动时浮动显示目标时间气泡
            if (isScrubbing && totalDuration > 0L) {
                val scrubMs = (scrubProgress * totalDuration).toLong()
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = "${formatDuration(scrubMs)} / ${formatDuration(totalDuration)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    onScrubStart()
                    onScrubbing(newProgress)
                },
                onValueChangeFinished = {
                    onScrubEnd(progress)
                },
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val displayPosition =
                    if (isScrubbing) (scrubProgress * totalDuration).toLong() else currentPosition
                Text(
                    text = "${formatDuration(displayPosition)} / ${formatDuration(totalDuration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                )

                // 比例提示小标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable(onClick = onCycleResizeMode),
                ) {
                    Text(
                        text = resizeMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 倍速：1.0x → 1.25x → 1.5x → 2.0x 循环
                Text(
                    text = "${playbackSpeed}x",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onCyclePlaybackSpeed),
                )

                if (showEpisodeQueue) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenEpisodeQueue,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "选集",
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector =
                            if (isLandscape) {
                                Icons.Filled.FullscreenExit
                            } else {
                                Icons.Filled.Fullscreen
                            },
                        contentDescription = if (isLandscape) "退出全屏" else "全屏播放",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
