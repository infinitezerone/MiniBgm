package com.infinitezerone.minibgm.feature.subject.player

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.delay
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
    bufferedPosition: StateFlow<Long>,
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
    onOpenSourcesPanel: (() -> Unit)? = null,
    playbackSpeed: Float,
    onCyclePlaybackSpeed: () -> Unit,
    showEpisodeQueue: Boolean,
    onOpenEpisodeQueue: () -> Unit,
    showPipButton: Boolean = true,
    currentQualityLabel: String? = null,
    onOpenTrackSelection: () -> Unit = {},
    isDanmakuEnabled: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    /** 弹幕分集匹配元信息；有候选时透出纠正入口 */
    danmakuMatch: DanmakuMatch? = null,
    onOpenDanmakuPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    hasNextEpisode: Boolean = false,
    onPlayNextEpisode: (() -> Unit)? = null,
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
                onOpenSourcesPanel = onOpenSourcesPanel,
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

            if (isLandscape) {
                PlayerSystemStatusIndicator()
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 顶部操作栏右侧（横竖屏一致）：画中画在左、"更多"固定右上角最右——
            // 汇聚清晰度/音画字幕/倍速/比例/弹幕/循环（对齐 B 站/YouTube 的右上角"更多"位）
            if (showPipButton) {
                IconButton(onClick = onEnterPip) {
                    Icon(
                        imageVector = BgmIcons.PictureInPicture,
                        contentDescription = stringResource(R.string.feature_subject_player_pip),
                        tint = Color.White,
                    )
                }
            }

            IconButton(onClick = onOpenTrackSelection) {
                Icon(
                    imageVector = BgmIcons.MoreVert,
                    contentDescription = stringResource(R.string.feature_subject_player_settings),
                    tint = Color.White,
                )
            }
        }

        // 底部控制栏：对齐 B站/腾讯视频现代单行内嵌布局（播放键 + 时间 + 进度条 + 全屏/扩展按键）
        val bottomBarModifier =
            if (isLandscape) {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ).displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ).padding(horizontal = 10.dp, vertical = 4.dp)
            }

        Box(modifier = bottomBarModifier) {
            // 位置单独订阅：只有底部栏（且仅在可见时才组合）随 500ms 位置跳重组
            val currentPosition by position.collectAsStateWithLifecycle()
            val currentBufferedPosition by bufferedPosition.collectAsStateWithLifecycle()
            val progress =
                if (isScrubbing) {
                    scrubProgress
                } else if (totalDuration > 0L) {
                    (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            val bufferedProgress =
                if (totalDuration > 0L) {
                    (currentBufferedPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

            if (isLandscape) {
                // 横屏成熟双行布局（Dual-Row Layout）：通栏全宽自适应进度条 + 下层控制行
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 1. 上层通栏自适应进度条（最大化有效行程，微调更跟手）
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerProgressBar(
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            isScrubbing = isScrubbing,
                            onScrubStart = onScrubStart,
                            onScrubbing = onScrubbing,
                            onScrubEnd = onScrubEnd,
                        )
                    }

                    // 2. 下层控制行（高度 44dp，左右双手集群分布）
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 播放/暂停
                        IconButton(
                            onClick = onPlayPauseToggle,
                            modifier = Modifier.size(40.dp),
                        ) {
                            val isPlaybackActive = isPlaying || isBuffering
                            val icon =
                                when {
                                    isEnded -> BgmIcons.Replay
                                    isPlaybackActive -> BgmIcons.Pause
                                    else -> BgmIcons.Play
                                }
                            Icon(
                                imageVector = icon,
                                contentDescription =
                                    if (isPlaybackActive) {
                                        stringResource(R.string.feature_subject_player_pause)
                                    } else {
                                        stringResource(R.string.feature_subject_player_play)
                                    },
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // 下一集按键（仅当有下一集且传入了回调时）
                        if (hasNextEpisode && onPlayNextEpisode != null) {
                            IconButton(
                                onClick = onPlayNextEpisode,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = BgmIcons.SkipNext,
                                    contentDescription = stringResource(R.string.feature_subject_ep_next),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 合并时间文本（01:23 / 24:12）
                        val displayPosition =
                            if (isScrubbing) (scrubProgress * totalDuration).toLong() else currentPosition
                        val hasHours = totalDuration >= 3600_000L
                        val timeTextStyle =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                            )
                        Text(
                            text =
                                "${formatDuration(
                                    displayPosition,
                                    forceHours = hasHours,
                                )} / ${formatDuration(totalDuration, forceHours = hasHours)}",
                            style = timeTextStyle,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.95f),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // 弹幕开关
                        ControlsBarIconButton(
                            icon = if (isDanmakuEnabled) BgmIcons.ChatBubble else BgmIcons.ChatBubbleOutline,
                            contentDescription =
                                stringResource(
                                    if (isDanmakuEnabled) {
                                        R.string.feature_subject_player_danmaku_on
                                    } else {
                                        R.string.feature_subject_player_danmaku_off
                                    },
                                ),
                            onClick = onToggleDanmaku,
                            tint = if (isDanmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        )

                        // 弹幕分集纠正入口（弹幕开启且有候选才出现；匹配处于回退兜底态时挂警示徽标）
                        DanmakuMatchEntryButton(
                            hasCandidates = isDanmakuEnabled && !danmakuMatch?.candidates.isNullOrEmpty(),
                            isFallback = danmakuMatch?.state == DanmakuMatchState.FALLBACK,
                            onClick = onOpenDanmakuPicker,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // 右侧动作区（文字胶囊与换源/选集/全屏）
                        if (currentQualityLabel != null) {
                            ControlsBarTextAction(
                                text = currentQualityLabel,
                                onClickLabel = stringResource(R.string.feature_subject_player_quality_group),
                                onClick = onOpenTrackSelection,
                            )
                        }
                        ControlsBarTextAction(
                            text = "${playbackSpeed}x",
                            onClickLabel = stringResource(R.string.feature_subject_player_speed_label),
                            onClick = onCyclePlaybackSpeed,
                        )
                        if (showEpisodeQueue) {
                            ControlsBarTextAction(
                                text = stringResource(R.string.feature_subject_player_episodes_pill),
                                onClickLabel = stringResource(R.string.feature_subject_player_episodes_pill),
                                onClick = onOpenEpisodeQueue,
                            )
                        }
                        if (onOpenSourcesPanel != null) {
                            ControlsBarTextAction(
                                text = stringResource(R.string.feature_subject_player_sources_pill),
                                onClickLabel = stringResource(R.string.feature_subject_player_sources_pill),
                                onClick = onOpenSourcesPanel,
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 退出全屏按键
                        ControlsBarIconButton(
                            icon = BgmIcons.FullscreenExit,
                            contentDescription = stringResource(R.string.feature_subject_player_exit_fullscreen),
                            onClick = onToggleFullscreen,
                        )
                    }
                }
            } else {
                // 纯粹的单行控制栏（Single-Row Inline Controls）
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. 左下角播放/暂停键：缓冲中只要处于意图播放态即显示暂停，允许用户打断缓冲
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.size(40.dp),
                    ) {
                        val isPlaybackActive = isPlaying || isBuffering
                        val icon =
                            when {
                                isEnded -> BgmIcons.Replay
                                isPlaybackActive -> BgmIcons.Pause
                                else -> BgmIcons.Play
                            }
                        Icon(
                            imageVector = icon,
                            contentDescription =
                                if (isPlaybackActive) {
                                    stringResource(R.string.feature_subject_player_pause)
                                } else {
                                    stringResource(R.string.feature_subject_player_play)
                                },
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 2. 当前时间（启用 tnum 等宽数字并固定最小占位，杜绝数字跳动挤压进度条产生肉眼可见的伸缩抖动）
                    val displayPosition =
                        if (isScrubbing) (scrubProgress * totalDuration).toLong() else currentPosition
                    val hasHours = totalDuration >= 3600_000L
                    val timeMinWidth = if (hasHours) 56.dp else 40.dp
                    val timeTextStyle =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        )
                    Text(
                        text = formatDuration(displayPosition, forceHours = hasHours),
                        style = timeTextStyle,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = timeMinWidth),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. 自适应撑满的单行进度条
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerProgressBar(
                            progress = progress,
                            bufferedProgress = bufferedProgress,
                            isScrubbing = isScrubbing,
                            onScrubStart = onScrubStart,
                            onScrubbing = onScrubbing,
                            onScrubEnd = onScrubEnd,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 4. 总时间
                    Text(
                        text = formatDuration(totalDuration, forceHours = hasHours),
                        style = timeTextStyle,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = timeMinWidth),
                    )

                    // 弹幕开关
                    ControlsBarIconButton(
                        icon = if (isDanmakuEnabled) BgmIcons.ChatBubble else BgmIcons.ChatBubbleOutline,
                        contentDescription =
                            stringResource(
                                if (isDanmakuEnabled) {
                                    R.string.feature_subject_player_danmaku_on
                                } else {
                                    R.string.feature_subject_player_danmaku_off
                                },
                            ),
                        onClick = onToggleDanmaku,
                        tint = if (isDanmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 6. 全屏切换键
                    ControlsBarIconButton(
                        icon = if (isLandscape) BgmIcons.FullscreenExit else BgmIcons.Fullscreen,
                        contentDescription =
                            if (isLandscape) {
                                stringResource(R.string.feature_subject_player_exit_fullscreen)
                            } else {
                                stringResource(R.string.feature_subject_player_enter_fullscreen)
                            },
                        onClick = onToggleFullscreen,
                    )
                }
            }
        }
    }
}

/** 控制栏分组竖线：把"播放控制"与"画面/线路动作"分开，比一排灰底药丸更安静 */
@Composable
private fun ControlsBarDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.3f)),
    )
}

/** 控制栏图标动作：统一 40dp 触控靶区，图标 22dp，与文字动作同一基线 */
@Composable
private fun ControlsBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 控制栏文字动作：只承载"当前值本身就是信息"的开关（倍速 / 清晰度）。
 * 无底色、40dp 高度，靠字重和间距区分层级，不再画成一排药丸。
 */
@Composable
private fun ControlsBarTextAction(
    text: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = onClickLabel, onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
        )
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
    onOpenSourcesPanel: (() -> Unit)? = null,
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
            if (onOpenSourcesPanel != null) {
                OutlinedButton(
                    onClick = onOpenSourcesPanel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.feature_subject_player_switch_source), color = Color.White)
                }
            }
            if (onNextSource != null) {
                OutlinedButton(
                    onClick = onNextSource,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.feature_subject_player_switch_next_source), color = Color.White)
                }
            }
            Button(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
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

/**
 * 横屏全屏系统状态指示（时间与电量）。
 * 消除系统状态栏隐藏后的全屏时间盲区。
 */
@Composable
private fun PlayerSystemStatusIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTime by remember {
        mutableStateOf(formatSystemTime())
    }
    var batteryPercent by remember {
        mutableIntStateOf(getBatteryLevel(context))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatSystemTime()
            batteryPercent = getBatteryLevel(context)
            delay(15_000L)
        }
    }

    Row(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = currentTime,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Medium,
        )
        if (batteryPercent in 0..100) {
            Text(
                text = "$batteryPercent%",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

private fun formatSystemTime(): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}

private fun getBatteryLevel(context: android.content.Context): Int =
    runCatching {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }.getOrDefault(-1)
