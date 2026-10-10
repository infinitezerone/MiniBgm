package com.infinitezerone.minibgm.feature.subject.player

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器画面缩放模式
 */
enum class PlayerResizeMode(
    val label: String,
    @StringRes val labelRes: Int,
) {
    FIT("适应", R.string.feature_subject_player_fit),
    ZOOM("裁剪", R.string.feature_subject_player_crop),
    FILL("拉伸", R.string.feature_subject_player_stretch),
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
    showPipButton: Boolean = true,
    hasSelectableTracks: Boolean = false,
    isSubtitlesActive: Boolean = false,
    currentQualityLabel: String? = null,
    onOpenTrackSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    onSingleTap: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (isLocked) Color.Transparent else Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSingleTap,
                ),
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
                        imageVector = if (isLocked) BgmIcons.Lock else BgmIcons.LockOpen,
                        contentDescription =
                            if (isLocked) {
                                stringResource(R.string.feature_subject_player_unlock_screen)
                            } else {
                                stringResource(R.string.feature_subject_player_lock_screen)
                            },
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
                    imageVector = BgmIcons.ArrowBack,
                    contentDescription = stringResource(R.string.feature_subject_cd_back),
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
            if (showPipButton) {
                IconButton(onClick = onEnterPip) {
                    Icon(
                        imageVector = BgmIcons.PictureInPicture,
                        contentDescription = stringResource(R.string.feature_subject_player_pip),
                        tint = Color.White,
                    )
                }
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
                    if (isLandscape) {
                        IconButton(
                            onClick = onRewind10,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Replay10,
                                contentDescription = stringResource(R.string.feature_subject_player_rewind_10s),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
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
                                    isEnded -> BgmIcons.Replay
                                    isPlaying -> BgmIcons.Pause
                                    else -> BgmIcons.Play
                                }
                            Icon(
                                imageVector = icon,
                                contentDescription =
                                    if (isPlaying) {
                                        stringResource(R.string.feature_subject_player_pause)
                                    } else {
                                        stringResource(R.string.feature_subject_player_play)
                                    },
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }

                    if (isLandscape) {
                        IconButton(
                            onClick = onForward10,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = BgmIcons.Forward10,
                                contentDescription = stringResource(R.string.feature_subject_player_forward_10s),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
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
                        imageVector = if (isPlaying) BgmIcons.Pause else BgmIcons.Play,
                        contentDescription =
                            if (isPlaying) {
                                stringResource(R.string.feature_subject_player_pause)
                            } else {
                                stringResource(R.string.feature_subject_player_play)
                            },
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

                // 画面比例微标签（仅全屏横屏保留，竖屏 16:9 视口无需切换比例）
                if (isLandscape) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable(onClick = onCycleResizeMode),
                    ) {
                        Text(
                            text = stringResource(resizeMode.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 倍速药丸（1.0x → 1.25x → 1.5x → 2.0x 循环）
                Surface(
                    shape = RoundedCornerShape(8.dp),
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

                if (currentQualityLabel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable(onClick = onOpenTrackSelection),
                    ) {
                        Text(
                            text = currentQualityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                if (showEpisodeQueue) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenEpisodeQueue,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.List,
                            contentDescription = stringResource(R.string.feature_subject_player_episodes),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (hasSelectableTracks) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onOpenTrackSelection,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = BgmIcons.Subtitles,
                            contentDescription = stringResource(R.string.feature_subject_player_tracks_btn),
                            tint = if (isSubtitlesActive) MaterialTheme.colorScheme.primary else Color.White,
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
                                BgmIcons.FullscreenExit
                            } else {
                                BgmIcons.Fullscreen
                            },
                        contentDescription =
                            if (isLandscape) {
                                stringResource(R.string.feature_subject_player_exit_fullscreen)
                            } else {
                                stringResource(R.string.feature_subject_player_enter_fullscreen)
                            },
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
                        shape = RoundedCornerShape(8.dp),
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
            imageVector = BgmIcons.ArrowBack,
            contentDescription = stringResource(R.string.feature_subject_cd_back),
            tint = Color.White,
        )
    }

    Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = BgmIcons.Warning,
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
                    Text(stringResource(R.string.feature_subject_player_switch_next_source), color = Color.White)
                }
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.feature_subject_player_retry_play))
            }
        }
        if (onRequestOpenSources != null) {
            TextButton(onClick = onRequestOpenSources) {
                Text(stringResource(R.string.feature_subject_source_ai_search), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
