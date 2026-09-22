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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * 采用最新 Media3 ExoPlayer 驱动，提供 Kazumi 风格一体化播放页（顶部 16:9 原生播放窗口、
 * 中间播放源切换栏、底部选集方块网格），并支持全屏横竖屏旋转、智能常亮以及播放达到 85% 时的自动打卡标记。
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
            parameters = { parametersOf(route) },
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
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val epLabel =
        remember(uiState.episodeSort, uiState.episodeType) {
            if (uiState.episodeType == 0) {
                "第 ${uiState.episodeSort.toEpisodeLabel()} 话"
            } else {
                "${EpisodeGroup.fromType(uiState.episodeType).label} ${uiState.episodeSort.toInt()}"
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
    // 用户自备列表的条目可携带必要请求头（Referer/Cookie 等），经 HttpDataSource 随媒体请求发送；
    // 队列内不同分集的请求头可能不同，请求头变化时重建播放器实例
    val exoPlayer =
        remember(context, uiState.requestHeaders) {
            val httpDataSourceFactory =
                DefaultHttpDataSource.Factory().apply {
                    setAllowCrossProtocolRedirects(true)
                    if (uiState.requestHeaders.isNotEmpty()) {
                        setDefaultRequestProperties(uiState.requestHeaders)
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

    // 倍速：分集切换重建播放器实例后同样要重新套用
    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // 断点续播：每个地址只恢复一次（超过总时长 95% 的记录视为看完，不再恢复）
    var resumedForUrl by remember { mutableStateOf("") }

    suspend fun resumeFromSavedPositionIfNeeded() {
        val state = viewModel.uiState.value
        if (resumedForUrl == state.streamUrl) return
        resumedForUrl = state.streamUrl
        val resume = state.resumePositionMs
        if (resume < MIN_RESUME_POSITION_MS) return
        val duration = exoPlayer.duration
        if (duration > 0L && resume >= duration * 0.95) return
        exoPlayer.seekTo(resume)
        val result =
            snackbarHostState.showSnackbar(
                message = "已恢复到上次位置 ${formatDuration(resume)}",
                actionLabel = "从头看",
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            exoPlayer.seekTo(0)
        }
    }

    // 设置数据源
    LaunchedEffect(uiState.streamUrl) {
        if (uiState.streamUrl.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(uiState.streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
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
                            // 就绪后跳到上次观看位置（断点续播）
                            coroutineScope.launch { resumeFromSavedPositionIfNeeded() }
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
                            // 播放完成：打卡当前分集，并在开启连播且有下一集时自动切换；
                            // 没切走才展示重播按钮
                            isPlaybackEnded = !viewModel.onPlaybackEnded()
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
                // 断点续播：进度回报给 ViewModel（内存暂存，节流落盘）
                viewModel.onProgressChanged(currentPosition)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    totalDuration = dur
                    // 当播放进度达到 85% 自动打卡
                    if (currentPosition.toFloat() / dur.toFloat() >= 0.85f) {
                        viewModel.onWatchThresholdReached()
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

    // 生命周期联动：离开前台时自动暂停，并把当前观看位置立即落盘
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    exoPlayer.pause()
                    viewModel.flushPlaybackPosition()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isLandscape && (uiState.episodes.size > 1 || uiState.queue.size > 1),
        drawerContent = {
            PlayerEpisodeQueueDrawer(
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                episodes = uiState.episodes,
                selectedEpisodeSort = uiState.episodeSort,
                sources = uiState.sources,
                sourceFailureCounts = uiState.sourceFailureCounts,
                selectedSourceIndex = uiState.selectedSourceIndex,
                autoNextEnabled = uiState.autoNextEnabled,
                onToggleAutoNext = viewModel::toggleAutoNext,
                onSelectSource = { index ->
                    viewModel.selectSource(index)
                },
                onSelectEpisode = { ep ->
                    viewModel.selectEpisode(ep)
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectQueueIndex = { index ->
                    viewModel.switchTo(index)
                    coroutineScope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding(),
                )
            },
            containerColor = if (isLandscape) Color.Black else MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            if (isLandscape) {
                // 全屏横屏态：纯黑背景沉浸式播放
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
                    } else if (uiState.isResolvingSource) {
                        PlayerResolvingView(
                            sourceName = uiState.currentSource?.name ?: "播放源",
                            attempt = uiState.resolveAttempt,
                            attemptTotal = uiState.resolveAttemptTotal,
                        )
                    } else {
                        PlayerEmptyView(
                            subjectName = uiState.subjectName.ifBlank { route.subjectName },
                            epLabel = epLabel,
                            errorMessage = uiState.error,
                            onBackClick = { toggleFullscreen(false) },
                            onRetry = { viewModel.retry() },
                            onRequestOpenSources = onRequestOpenSources,
                        )
                    }

                    AnimatedVisibility(
                        visible = areControlsVisible || !isPlaying || isBuffering || isPlaybackEnded || uiState.error != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PlayerControlsOverlay(
                            subjectName = uiState.subjectName.ifBlank { route.subjectName },
                            epLabel = epLabel,
                            episodeName = uiState.episodeName,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            isEnded = isPlaybackEnded,
                            currentPosition = currentPosition,
                            totalDuration = totalDuration,
                            isScrubbing = isScrubbing,
                            scrubProgress = scrubProgress,
                            isLandscape = true,
                            errorMessage = uiState.error,
                            onBackClick = { toggleFullscreen(false) },
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
                            onScrubStart = { isScrubbing = true },
                            onScrubbing = { progress -> scrubProgress = progress },
                            onScrubEnd = { progress ->
                                isScrubbing = false
                                if (totalDuration > 0L) {
                                    val newPosition = (progress * totalDuration).toLong()
                                    exoPlayer.seekTo(newPosition)
                                    currentPosition = newPosition
                                }
                            },
                            onToggleFullscreen = { toggleFullscreen(false) },
                            onRetry = {
                                viewModel.retry()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            playbackSpeed = playbackSpeed,
                            onCyclePlaybackSpeed = {
                                playbackSpeed =
                                    when (playbackSpeed) {
                                        1f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2f
                                        else -> 1f
                                    }
                            },
                            showEpisodeQueue = uiState.episodes.size > 1 || uiState.queue.size > 1,
                            onOpenEpisodeQueue = {
                                coroutineScope.launch { drawerState.open() }
                            },
                        )
                    }
                }
            } else {
                // 竖屏常规态：Kazumi 风格一体化播放页（顶部 16:9 播放窗口 + 中间源选择 + 底部选集网格）
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                ) {
                    // 1. 顶部 16:9 播放窗口
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    areControlsVisible = !areControlsVisible
                                },
                    ) {
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
                        } else if (uiState.isResolvingSource) {
                            PlayerResolvingView(
                                sourceName = uiState.currentSource?.name ?: "播放源",
                                attempt = uiState.resolveAttempt,
                                attemptTotal = uiState.resolveAttemptTotal,
                            )
                        } else {
                            PlayerEmptyView(
                                subjectName = uiState.subjectName.ifBlank { route.subjectName },
                                epLabel = epLabel,
                                errorMessage = uiState.error,
                                onBackClick = onBackClick,
                                onRetry = { viewModel.retry() },
                                onRequestOpenSources = onRequestOpenSources,
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = areControlsVisible || !isPlaying || isBuffering || isPlaybackEnded || uiState.error != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            PlayerControlsOverlay(
                                subjectName = uiState.subjectName.ifBlank { route.subjectName },
                                epLabel = epLabel,
                                episodeName = uiState.episodeName,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                isEnded = isPlaybackEnded,
                                currentPosition = currentPosition,
                                totalDuration = totalDuration,
                                isScrubbing = isScrubbing,
                                scrubProgress = scrubProgress,
                                isLandscape = false,
                                errorMessage = uiState.error,
                                onBackClick = onBackClick,
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
                                onScrubStart = { isScrubbing = true },
                                onScrubbing = { progress -> scrubProgress = progress },
                                onScrubEnd = { progress ->
                                    isScrubbing = false
                                    if (totalDuration > 0L) {
                                        val newPosition = (progress * totalDuration).toLong()
                                        exoPlayer.seekTo(newPosition)
                                        currentPosition = newPosition
                                    }
                                },
                                onToggleFullscreen = { toggleFullscreen(true) },
                                onRetry = {
                                    viewModel.retry()
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                playbackSpeed = playbackSpeed,
                                onCyclePlaybackSpeed = {
                                    playbackSpeed =
                                        when (playbackSpeed) {
                                            1f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2f
                                            else -> 1f
                                        }
                                },
                                showEpisodeQueue = false,
                                onOpenEpisodeQueue = {},
                            )
                        }
                    }

                    // 2. 下半部：剧集信息、播放源切换栏、选集方块网格
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 56.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PlayerHeaderInfo(
                                subjectName = uiState.subjectName.ifBlank { route.subjectName },
                                episodeTitle = epLabel + if (uiState.episodeName.isNotBlank()) " · ${uiState.episodeName}" else "",
                                isWatched = uiState.isWatched,
                            )
                        }

                        if (uiState.sources.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                PlayerSourceSelector(
                                    sources = uiState.sources,
                                    sourceFailureCounts = uiState.sourceFailureCounts,
                                    selectedIndex = uiState.selectedSourceIndex,
                                    onSelectSource = viewModel::selectSource,
                                    onRequestOpenSources = onRequestOpenSources,
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EpisodeSectionHeader(
                                episodeCount = uiState.episodes.size,
                                autoNextEnabled = uiState.autoNextEnabled,
                                onToggleAutoNext = viewModel::toggleAutoNext,
                            )
                        }

                        items(uiState.episodes, key = { "${it.type}_${it.id}_${it.sort}" }) { ep ->
                            val isSelected = ep.sort == uiState.episodeSort && (ep.id == 0L || ep.id == uiState.episodeId)
                            EpisodeGridCard(
                                episode = ep,
                                isSelected = isSelected,
                                onClick = { viewModel.selectEpisode(ep) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 嗅探加载中视图
 */
@Composable
private fun PlayerResolvingView(
    sourceName: String,
    attempt: Int = 0,
    attemptTotal: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp),
            )
            Text(
                // 关键词是逐个串行试探的，把进度亮出来——否则用户只看到转圈，不知道在等第几步
                text =
                    if (attemptTotal > 0) {
                        "正在从【$sourceName】嗅探视频直链...（第 $attempt/$attemptTotal 次尝试）"
                    } else {
                        "正在从【$sourceName】嗅探视频直链..."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

/**
 * 剧集标题与看过标记
 */
@Composable
private fun PlayerHeaderInfo(
    subjectName: String,
    episodeTitle: String,
    isWatched: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = subjectName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isWatched) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = "已看",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 屡试屡败的源提示。
 *
 * 只做标记与弱化，**不动列表顺序**：选中项按位置判定，重排会让选中错位。
 * 计数是本次会话内的**连续**失败次数，任何一次成功即清零——它说的是"这个源现在不灵"，
 * 而不是"它历史上错过几次"。
 */
@Composable
private fun SourceFailureHint(failureCount: Int) {
    if (failureCount <= 0) return
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = "$failureCount 次打不开",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * 播放源切换选择器
 */
@Composable
private fun PlayerSourceSelector(
    sources: List<PlayerSourceTab>,
    sourceFailureCounts: Map<String, Int>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "播放源",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (onRequestOpenSources != null) {
                TextButton(
                    onClick = onRequestOpenSources,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "播放源管理",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources) { index, source ->
                val isSelected = index == selectedIndex
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectSource(index) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            SourceFailureHint(sourceFailureCounts[source.id] ?: 0)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (source.isDirect) Icons.Filled.CloudQueue else Icons.Filled.TravelExplore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }
    }
}

/**
 * 选集网格标题行
 */
@Composable
private fun EpisodeSectionHeader(
    episodeCount: Int,
    autoNextEnabled: Boolean,
    onToggleAutoNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "选集",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (episodeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "共 $episodeCount 话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onToggleAutoNext),
        ) {
            Text(
                text = "自动连播",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = autoNextEnabled,
                onCheckedChange = { onToggleAutoNext() },
            )
        }
    }
}

/**
 * 分集卡片
 */
@Composable
private fun EpisodeGridCard(
    episode: PlayerEpisodeItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortLabel =
        if (episode.type == 0) {
            if (episode.sort == episode.sort.toInt().toFloat()) {
                episode.sort.toInt().toString()
            } else {
                episode.sort.toString()
            }
        } else {
            "SP${episode.sort.toInt()}"
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (isSelected) {
                    Text(
                        text = "播放中",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

/**
 * 全屏内选集抽屉：分集列表 + 播放源切换 + 自动连播开关。
 */
@Composable
private fun PlayerEpisodeQueueDrawer(
    queue: List<PlayerQueueEntry>,
    currentIndex: Int,
    episodes: List<PlayerEpisodeItem>,
    selectedEpisodeSort: Float,
    sources: List<PlayerSourceTab>,
    sourceFailureCounts: Map<String, Int>,
    selectedSourceIndex: Int,
    autoNextEnabled: Boolean,
    onToggleAutoNext: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val totalCount = if (episodes.isNotEmpty()) episodes.size else queue.size
                Text(
                    text = "选集 · $totalCount 话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "自动连播",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(checked = autoNextEnabled, onCheckedChange = { onToggleAutoNext() })
            }

            if (sources.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(sources) { index, source ->
                        val isSelected = index == selectedSourceIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSource(index) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(source.name)
                                    SourceFailureHint(sourceFailureCounts[source.id] ?: 0)
                                }
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (episodes.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(episodes) { _, ep ->
                        val selected = ep.sort == selectedEpisodeSort
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectEpisode(ep) }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = ep.sort.toInt().toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = ep.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(queue) { index, entry ->
                        val selected = index == currentIndex
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectQueueIndex(index) }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = entry.label.ifBlank { entry.episodeName.ifBlank { "第 ${index + 1} 条" } },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
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

                // 倍速：1.0x → 1.25x → 1.5x → 2.0x 循环
                Text(
                    text = "${playbackSpeed}x",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onCyclePlaybackSpeed),
                )

                if (showEpisodeQueue) {
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
    errorMessage: String? = null,
    onBackClick: () -> Unit,
    onRetry: () -> Unit = {},
    onRequestOpenSources: (() -> Unit)? = null,
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
                text = if (errorMessage != null) "播放源解析未完成" else "暂无在线可播直链",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text =
                    if (errorMessage != null) {
                        errorMessage
                    } else {
                        "$subjectName · $epLabel\n该分集尚未匹配到应用内直链，可通过下方播放源切换其他来源，或在播放源管理中配置"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text("返回", color = Color.White)
                }
                if (errorMessage != null) {
                    Button(onClick = onRetry) {
                        Text("重试嗅探")
                    }
                } else if (onRequestOpenSources != null) {
                    Button(onClick = onRequestOpenSources) {
                        Text("管理播放源")
                    }
                }
            }
        }
    }
}
