package com.infinitezerone.minibgm.feature.subject.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private fun classifyPlaybackError(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "网络连接超时，请检查网络"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "播放地址已失效或返回错误"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> "视频流格式无法解析"
        else -> error.localizedMessage ?: "播放出现未知异常"
    }

/**
 * Kazumi 风格一体化流媒体播放页
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
    val configuration = LocalConfiguration.current

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
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var userInteractionTrigger by remember { mutableIntStateOf(0) }
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }

    // 物理传感器旋转联动：跟随系统横竖屏自动切入/切出全屏
    val isSystemLandscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isSystemLandscape) {
        if (isSystemLandscape != isLandscape) {
            isLandscape = isSystemLandscape
            if (activity != null) {
                val window = activity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (isSystemLandscape) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    isEpisodeDrawerOpen = false
                }
            }
        }
    }

    // 检测画中画状态
    val isInPipMode = activity?.isInPictureInPictureMode == true

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
                is PlayerUiEvent.MarkedWatched -> {}
            }
        }
    }

    // 构造 ExoPlayer 实例并托管生命周期（配置音频焦点管理、拔出耳机自动暂停与网络请求头）
    val exoPlayer =
        remember(context, uiState.requestHeaders) {
            val httpDataSourceFactory =
                DefaultHttpDataSource.Factory().apply {
                    setAllowCrossProtocolRedirects(true)
                    if (uiState.requestHeaders.isNotEmpty()) {
                        setDefaultRequestProperties(uiState.requestHeaders)
                    }
                }

            val audioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()

            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    playWhenReady = true
                }
        }

    // 倍速：分集切换重建播放器实例后同样要重新套用
    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // 断点续播
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

    // 设置媒体数据源：重置播放临时进度以避免切集进度条瞬时抖动
    LaunchedEffect(uiState.streamUrl) {
        if (uiState.streamUrl.isNotBlank()) {
            isBuffering = true
            isPlaybackEnded = false
            currentPosition = 0L
            totalDuration = 0L
            val mediaItem = MediaItem.fromUri(Uri.parse(uiState.streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } else {
            isBuffering = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // 断点续播补发：防止磁盘 I/O 较慢时状态就绪后才载入断点位置
    LaunchedEffect(uiState.resumePositionMs, uiState.streamUrl) {
        if (uiState.resumePositionMs >= MIN_RESUME_POSITION_MS && exoPlayer.playbackState == Player.STATE_READY) {
            resumeFromSavedPositionIfNeeded()
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
                            coroutineScope.launch { resumeFromSavedPositionIfNeeded() }
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
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
                viewModel.onProgressChanged(currentPosition)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    totalDuration = dur
                    if (currentPosition.toFloat() / dur.toFloat() >= 0.85f) {
                        viewModel.onWatchThresholdReached()
                    }
                }
            }
            delay(500)
        }
    }

    // 控制栏自动隐藏（3.5 秒无交互自动淡出）
    LaunchedEffect(areControlsVisible, isPlaying, userInteractionTrigger) {
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
        if (!landscape) {
            isEpisodeDrawerOpen = false
        }
        isLandscape = landscape
        if (activity != null) {
            activity.requestedOrientation =
                if (landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

    // 画中画模式
    fun enterPictureInPicture() {
        if (activity != null) {
            val params =
                PictureInPictureParams
                    .Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    // 比例模式循环切换
    fun cycleResizeMode() {
        resizeMode =
            when (resizeMode) {
                PlayerResizeMode.FIT -> PlayerResizeMode.ZOOM
                PlayerResizeMode.ZOOM -> PlayerResizeMode.FILL
                PlayerResizeMode.FILL -> PlayerResizeMode.FIT
            }
    }

    // 页面退出时恢复竖屏与系统默认亮度
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                val window = activity.window
                val lp = window.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
                WindowCompat
                    .getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 物理返回键处理
    BackHandler {
        if (isEpisodeDrawerOpen) {
            isEpisodeDrawerOpen = false
        } else if (isLandscape) {
            toggleFullscreen(false)
        } else {
            onBackClick()
        }
    }

    // 生命周期联动：离开前台时自动暂停并立即落盘
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    if (activity?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
                    }
                    viewModel.flushPlaybackPosition()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 画中画模式下只显示纯净视频视口
    if (isInPipMode) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode =
                        when (resizeMode) {
                            PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        containerColor = if (isLandscape) Color.Black else MaterialTheme.colorScheme.background,
    ) { _ ->
        if (isLandscape) {
            // 全屏横屏态：纯黑背景沉浸式播放 + 手势检测与 HUD
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerGestureDetector(
                    isPlaying = isPlaying,
                    currentPositionMs = currentPosition,
                    totalDurationMs = totalDuration,
                    onSingleTap = {
                        areControlsVisible = !areControlsVisible
                        userInteractionTrigger++
                    },
                    onDoubleTapSeek = { target ->
                        exoPlayer.seekTo(target)
                        currentPosition = target
                        userInteractionTrigger++
                    },
                    onDoubleTapPlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        userInteractionTrigger++
                    },
                    onSeekConfirm = { target ->
                        exoPlayer.seekTo(target)
                        currentPosition = target
                        userInteractionTrigger++
                    },
                    onFastForwardStart = {
                        exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                    },
                    onFastForwardEnd = {
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    },
                ) {
                    if (uiState.streamUrl.isNotBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    this.resizeMode =
                                        when (resizeMode) {
                                            PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        }
                                    layoutParams =
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                }
                            },
                            update = { view ->
                                view.resizeMode =
                                    when (resizeMode) {
                                        PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
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
                        resizeMode = resizeMode,
                        errorMessage = uiState.error,
                        onBackClick = { toggleFullscreen(false) },
                        onPlayPauseToggle = {
                            userInteractionTrigger++
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
                            userInteractionTrigger++
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        onForward10 = {
                            userInteractionTrigger++
                            val dur = exoPlayer.duration
                            val maxPos = if (dur > 0L) dur else Long.MAX_VALUE
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        onScrubStart = {
                            userInteractionTrigger++
                            isScrubbing = true
                        },
                        onScrubbing = { progress -> scrubProgress = progress },
                        onScrubEnd = { progress ->
                            userInteractionTrigger++
                            isScrubbing = false
                            if (totalDuration > 0L) {
                                val newPosition = (progress * totalDuration).toLong()
                                exoPlayer.seekTo(newPosition)
                                currentPosition = newPosition
                            }
                        },
                        onToggleFullscreen = { toggleFullscreen(false) },
                        onCycleResizeMode = ::cycleResizeMode,
                        onEnterPip = ::enterPictureInPicture,
                        onRetry = {
                            viewModel.retry()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        playbackSpeed = playbackSpeed,
                        onCyclePlaybackSpeed = {
                            userInteractionTrigger++
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
                            isEpisodeDrawerOpen = true
                        },
                    )
                }

                // 全屏内右侧选集抽屉背景遮罩
                AnimatedVisibility(
                    visible = isEpisodeDrawerOpen,
                    enter = fadeIn(tween(durationMillis = 200)),
                    exit = fadeOut(tween(durationMillis = 200)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { isEpisodeDrawerOpen = false },
                                ),
                    )
                }

                // 全屏内右侧选集抽屉面板（自右向左滑入）
                AnimatedVisibility(
                    visible = isEpisodeDrawerOpen,
                    enter =
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 250),
                        ),
                    exit =
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 200),
                        ),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                ) {
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
                            isEpisodeDrawerOpen = false
                        },
                        onSelectQueueIndex = { index ->
                            viewModel.switchTo(index)
                            isEpisodeDrawerOpen = false
                        },
                        onClose = { isEpisodeDrawerOpen = false },
                    )
                }
            }
        } else {
            // 竖屏常规态：Kazumi 风格一体化播放页（顶部 16:9 播放窗口 + 中间源选择 + 底部选集网格）
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                // 1. 顶部 16:9 播放窗口
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                ) {
                    PlayerGestureDetector(
                        isPlaying = isPlaying,
                        currentPositionMs = currentPosition,
                        totalDurationMs = totalDuration,
                        onSingleTap = {
                            areControlsVisible = !areControlsVisible
                            userInteractionTrigger++
                        },
                        onDoubleTapSeek = { target ->
                            exoPlayer.seekTo(target)
                            currentPosition = target
                            userInteractionTrigger++
                        },
                        onDoubleTapPlayPause = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            userInteractionTrigger++
                        },
                        onSeekConfirm = { target ->
                            exoPlayer.seekTo(target)
                            currentPosition = target
                            userInteractionTrigger++
                        },
                        onFastForwardStart = {
                            exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                        },
                        onFastForwardEnd = {
                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                        },
                    ) {
                        if (uiState.streamUrl.isNotBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        this.resizeMode =
                                            when (resizeMode) {
                                                PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            }
                                        layoutParams =
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                            )
                                    }
                                },
                                update = { view ->
                                    view.resizeMode =
                                        when (resizeMode) {
                                            PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
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
                            resizeMode = resizeMode,
                            errorMessage = uiState.error,
                            onBackClick = onBackClick,
                            onPlayPauseToggle = {
                                userInteractionTrigger++
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
                                userInteractionTrigger++
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            onForward10 = {
                                userInteractionTrigger++
                                val dur = exoPlayer.duration
                                val maxPos = if (dur > 0L) dur else Long.MAX_VALUE
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxPos)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            onScrubStart = {
                                userInteractionTrigger++
                                isScrubbing = true
                            },
                            onScrubbing = { progress -> scrubProgress = progress },
                            onScrubEnd = { progress ->
                                userInteractionTrigger++
                                isScrubbing = false
                                if (totalDuration > 0L) {
                                    val newPosition = (progress * totalDuration).toLong()
                                    exoPlayer.seekTo(newPosition)
                                    currentPosition = newPosition
                                }
                            },
                            onToggleFullscreen = { toggleFullscreen(true) },
                            onCycleResizeMode = ::cycleResizeMode,
                            onEnterPip = ::enterPictureInPicture,
                            onRetry = {
                                viewModel.retry()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            playbackSpeed = playbackSpeed,
                            onCyclePlaybackSpeed = {
                                userInteractionTrigger++
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

                // 2. 下半部：剧集信息、播放源切换栏、选集方块网格（带长篇分页分段）
                val chunkSize = 30
                val chunks =
                    remember(uiState.episodes) {
                        if (uiState.episodes.size > chunkSize) {
                            uiState.episodes.chunked(chunkSize)
                        } else {
                            emptyList()
                        }
                    }

                val initialChunkIndex =
                    remember(uiState.episodes, uiState.episodeSort) {
                        val idx = uiState.episodes.indexOfFirst { it.sort == uiState.episodeSort }
                        if (idx >= 0 && chunks.isNotEmpty()) idx / chunkSize else 0
                    }

                var selectedChunkIndex by remember(chunks) { mutableIntStateOf(initialChunkIndex) }
                val displayEpisodes =
                    if (chunks.isNotEmpty()) {
                        chunks.getOrElse(selectedChunkIndex) { uiState.episodes }
                    } else {
                        uiState.episodes
                    }

                val paginationLabels =
                    remember(chunks) {
                        chunks.map { list ->
                            "${list.first().sort.toInt()}-${list.last().sort.toInt()}"
                        }
                    }

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
                            paginationChunks = paginationLabels,
                            selectedChunkIndex = selectedChunkIndex,
                            onSelectChunk = { selectedChunkIndex = it },
                        )
                    }

                    items(displayEpisodes, key = { "${it.type}_${it.id}_${it.sort}" }) { ep ->
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
