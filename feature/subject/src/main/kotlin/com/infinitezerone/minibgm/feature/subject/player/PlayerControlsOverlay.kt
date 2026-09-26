package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

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
    position: StateFlow<Long>,
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
    onNextSource: (() -> Unit)? = null,
    onRequestOpenSources: (() -> Unit)? = null,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    showEpisodeQueue: Boolean,
    onOpenEpisodeQueue: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (isLocked) Color.Transparent else Color.Black.copy(alpha = 0.32f)),
    ) {
        // 播放错误态：只保留返回 + 错误文案 + 动作，隐藏所有控制台
        // （进度条/播放键/比例/倍速/PiP/全屏），避免画面被多种控件挤成一团。
        if (errorMessage != null) {
            PlayerErrorState(
                errorMessage = errorMessage,
                isLandscape = isLandscape,
                onBackClick = onBackClick,
                onRetry = onRetry,
                onNextSource = onNextSource,
                onRequestOpenSources = onRequestOpenSources,
            )
            return@Box
        }

        // 横屏锁屏切换按钮（浮动于屏幕左侧中央边缘）
        if (isLandscape) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .displayCutoutPadding()
                        .padding(start = 20.dp),
            ) {
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isLocked) "解锁屏幕" else "锁定屏幕",
                        tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 锁屏状态下隐藏所有其它控制元素，防止误触
        if (isLocked) {
            return@Box
        }

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
                    ).displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                val title = if (episodeName.isNotBlank()) "$epLabel · $episodeName" else epLabel.ifBlank { subjectName }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLandscape && subjectName.isNotBlank() && title != subjectName) {
                    Text(
                        text = subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    IconButton(
                        onClick = onRewind10,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "快退 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    ) {
                        IconButton(
                            onClick = onPlayPauseToggle,
                            modifier = Modifier.size(56.dp),
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
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = onForward10,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "快进 10 秒",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        // 底部控制栏（集成微交互自适应进度条与紧凑单行控件）
        val bottomBarModifier =
            if (isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    ).displayCutoutPadding()
                    .navigationBarsPadding()
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
            }

        Column(modifier = bottomBarModifier) {
            // 位置单独订阅：只有底部栏（且仅在可见时才组合）随 500ms 位置跳重组
            val currentPosition by position.collectAsStateWithLifecycle()
            val progress =
                if (isScrubbing) {
                    scrubProgress
                } else if (totalDuration > 0L) {
                    (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

            // 1. 控制行（位于进度条上方）
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isLandscape) 16.dp else 12.dp,
                            end = if (isLandscape) 16.dp else 12.dp,
                            top = if (isLandscape) 8.dp else 6.dp,
                            bottom = 2.dp,
                        ).height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 播放/暂停
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 当前时间 / 总时间
                val displayPosition =
                    if (isScrubbing) (scrubProgress * totalDuration).toLong() else currentPosition
                Text(
                    text = "${formatDuration(displayPosition)} / ${formatDuration(totalDuration)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                )

                Spacer(modifier = Modifier.weight(1f))

                // 画面比例微标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.clickable(onClick = onCycleResizeMode),
                ) {
                    Text(
                        text = resizeMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 倍速药丸（1.0x → 1.25x → 1.5x → 2.0x 循环）
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.clickable(onClick = onCyclePlaybackSpeed),
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                if (showEpisodeQueue) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenEpisodeQueue,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "选集",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(32.dp),
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
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // 2. 最底部进度条（紧贴视频最底边）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (isScrubbing && totalDuration > 0L) {
                    val scrubMs = (scrubProgress * totalDuration).toLong()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier.offset(y = (-32).dp),
                    ) {
                        Text(
                            text = "${formatDuration(scrubMs)} / ${formatDuration(totalDuration)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                PlayerProgressBar(
                    progress = progress,
                    isScrubbing = isScrubbing,
                    onScrubStart = onScrubStart,
                    onScrubbing = onScrubbing,
                    onScrubEnd = onScrubEnd,
                )
            }
        }
    }
}

/**
 * 纯错误态：只保留返回、错误文案与动作按钮。
 * 播放/解析失败时不叠进度条与控制台，避免画面被多种控件挤成一团。
 */
@Composable
private fun BoxScope.PlayerErrorState(
    errorMessage: String,
    isLandscape: Boolean,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onNextSource: (() -> Unit)?,
    onRequestOpenSources: (() -> Unit)?,
) {
    IconButton(
        onClick = onBackClick,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .displayCutoutPadding()
                .padding(if (isLandscape) 16.dp else 4.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color.White,
        )
    }

    Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onNextSource != null) {
                OutlinedButton(onClick = onNextSource) {
                    Text("换下一个源", color = Color.White)
                }
            }
            Button(onClick = onRetry) {
                Text("重试播放")
            }
        }
        if (onRequestOpenSources != null) {
            TextButton(onClick = onRequestOpenSources) {
                Text("管理播放源", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
