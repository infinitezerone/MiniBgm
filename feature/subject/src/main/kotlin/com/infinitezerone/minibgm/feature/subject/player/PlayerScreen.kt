package com.infinitezerone.minibgm.feature.subject.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/**
 * 格式化播放时间（毫秒 -> mm:ss 或 hh:mm:ss）
 */
internal fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * 把 Media3 播放异常归类为可归因的失败原因（网络不可达 / 被来源拒绝 / 非媒体内容），
 * 供来源列表展示"打不开"标注。
 */
private fun classifyPlaybackError(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> "网络不可达"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> "被来源拒绝访问"

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "资源不存在"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "非媒体内容或格式不支持"

        else -> error.localizedMessage ?: "视频播放失败"
    }

/**
 * MiniBgm 应用内播放界面：
 * 采用最新 Media3 ExoPlayer 驱动，提供沉浸式全屏播放体验、极简 Material 3 控制遮罩、
 * 智能常亮管理、横竖屏自适应切换以及播放达到 85% 时的自动打卡标记。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    route: PlayerRoute,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestOpenSources: (() -> Unit)? = null,
    viewModel: PlayerViewModel =
        koinViewModel(
            parameters = { parametersOf(route.subjectId, route.episodeId, route.streamUrl) },
        ),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var isPlaybackEnded by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }

    val epLabel =
        remember(route.episodeSort, route.episodeType) {
            if (route.episodeType == 0) {
                "第 ${route.episodeSort.toEpisodeLabel()} 话"
            } else {
                "${EpisodeGroup.fromType(route.episodeType).label} ${route.episodeSort.toInt()}"
            }
        }

    // 观察 ViewModel 一次性单发事件
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is PlayerUiEvent.MarkedWatched -> {
                    // 已打卡完成
                }
            }
        }
    }

    // 构造 ExoPlayer 实例并托管生命周期；
    // 用户自备列表的条目可携带必要请求头（Referer/Cookie 等），经 HttpDataSource 随媒体请求发送
    val exoPlayer =
        remember(context, route.requestHeaders) {
            val httpDataSourceFactory =
                DefaultHttpDataSource.Factory().apply {
                    setAllowCrossProtocolRedirects(true)
                    if (route.requestHeaders.isNotEmpty()) {
                        setDefaultRequestProperties(route.requestHeaders)
                    }
                }
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
                .build()
                .apply {
                    playWhenReady = true
                }
        }

    // 设置数据源
    LaunchedEffect(uiState.streamUrl) {
        if (uiState.streamUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(uiState.streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    // 播放器状态监听器
    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            isBuffering = true
                            isPlaybackEnded = false
                        }
                        Player.STATE_READY -> {
                            isBuffering = false
                            isPlaybackEnded = false
                            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                            viewModel.onPlaybackReady()
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
                            isPlaybackEnded = true
                            // 播放完成：自动触发已看打卡
                            viewModel.markWatched(route.episodeSort.toInt())
                        }
                        Player.STATE_IDLE -> {
                            isBuffering = false
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    viewModel.onPlaybackError(classifyPlaybackError(error))
                }
            }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 周期性更新播放进度及 85% 自动打卡检测
    LaunchedEffect(isPlaying, isPlaybackEnded) {
        while (isPlaying && !isPlaybackEnded) {
            if (!isScrubbing) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    totalDuration = dur
                    // 当播放进度达到 85% 自动打卡
                    if (currentPosition.toFloat() / dur.toFloat() >= 0.85f) {
                        viewModel.markWatched(route.episodeSort.toInt())
                    }
                }
            }
            delay(500)
        }
    }

    // 控制栏自动隐藏（3.5 秒无交互自动淡出）
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // 保持屏幕常亮
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 全屏与沉浸式沉浸栏控制
    fun toggleFullscreen(landscape: Boolean) {
        isLandscape = landscape
        if (activity != null) {
            activity.requestedOrientation =
                if (landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (landscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 页面退出时恢复竖屏
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity.window
                WindowCompat
                    .getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 物理返回键：如果在全屏横屏下，先恢复竖屏
    BackHandler {
        if (isLandscape) {
            toggleFullscreen(false)
        } else {
            onBackClick()
        }
    }

    // 生命周期联动：离开前台时自动暂停
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    exoPlayer.pause()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        areControlsVisible = !areControlsVisible
                    },
        ) {
            // 播放器底图渲染层
            if (uiState.streamUrl.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 空数据源友好提示
                PlayerEmptyView(
                    subjectName = route.subjectName,
                    epLabel = epLabel,
                    onBackClick = onBackClick,
                    onRequestOpenSources = onRequestOpenSources,
                )
            }

            // 控制器覆盖遮罩
            AnimatedVisibility(
                visible = areControlsVisible || !isPlaying || isBuffering || isPlaybackEnded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerControlsOverlay(
                    subjectName = route.subjectName,
                    epLabel = epLabel,
                    episodeName = route.episodeName,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isEnded = isPlaybackEnded,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    isScrubbing = isScrubbing,
                    scrubProgress = scrubProgress,
                    isLandscape = isLandscape,
                    errorMessage = uiState.error,
                    onBackClick = {
                        if (isLandscape) toggleFullscreen(false) else onBackClick()
                    },
                    onPlayPauseToggle = {
                        if (isPlaybackEnded) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        } else if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onRewind10 = {
                        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(newPos)
                        currentPosition = newPos
                    },
                    onForward10 = {
                        val dur = exoPlayer.duration
                        val maxPos = if (dur > 0L) dur else Long.MAX_VALUE
                        val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                        exoPlayer.seekTo(newPos)
                        currentPosition = newPos
                    },
                    onScrubStart = {
                        isScrubbing = true
                    },
                    onScrubbing = { progress ->
                        scrubProgress = progress
                    },
                    onScrubEnd = { progress ->
                        isScrubbing = false
                        if (totalDuration > 0L) {
                            val newPosition = (progress * totalDuration).toLong()
                            exoPlayer.seekTo(newPosition)
                            currentPosition = newPosition
                        }
                    },
                    onToggleFullscreen = {
                        toggleFullscreen(!isLandscape)
                    },
                    onRetry = {
                        viewModel.updateStreamUrl(route.streamUrl)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                )
            }
        }
    }
}

/**
 * 极简现代播放器控制遮罩
 */
@Composable
private fun PlayerControlsOverlay(
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
    errorMessage: String?,
    onBackClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubbing: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        // 顶部操作栏
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                        ),
                    ).statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    ).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val progress =
                if (isScrubbing) {
                    scrubProgress
                } else if (totalDuration > 0L) {
                    (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val displayPosition =
                    if (isScrubbing) (scrubProgress * totalDuration).toLong() else currentPosition
                Text(
                    text = "${formatDuration(displayPosition)} / ${formatDuration(totalDuration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )

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

/**
 * 暂无直链时的友好占位与向导入口
 */
@Composable
private fun PlayerEmptyView(
    subjectName: String,
    epLabel: String,
    onBackClick: () -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "暂无在线可播直链",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = "$subjectName · $epLabel\n该分集尚未匹配到应用内直链，可通过播放向导使用哔哩哔哩或蜜柑计划获取",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text("返回", color = Color.White)
                }
                if (onRequestOpenSources != null) {
                    Button(onClick = onRequestOpenSources) {
                        Text("打开播放向导")
                    }
                }
            }
        }
    }
}
