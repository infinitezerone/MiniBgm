package com.infinitezerone.minibgm.feature.subject.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.compose.ContentFrame
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.feature.subject.components.EpisodeGroup
import com.infinitezerone.minibgm.feature.subject.components.toEpisodeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val CONTROLS_AUTO_HIDE_MS = 3_500L

private const val EPISODE_GRID_CHUNK_SIZE = 30

/**
 * Kazumi 风格一体化流媒体播放页。
 *
 * 播放器所有权：本页只建一个 [ExoPlayerEngine] 与其上的 [PlayerController]（单一状态源），
 * 渲染交给官方 Compose 节点 `PlayerSurface`，系统集成（蓝牙/媒体键、Android 12+ PiP 键）
 * 交给随页面生命周期的轻量 `MediaSession`。所有播放器状态来自 [PlayerController.state]，
 * 不再散落为多个 `remember { mutableStateOf }`，也不再有 UI 层轮询。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    route: PlayerRoute,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestOpenSources: (() -> Unit)? = null,
    onManageRules: (() -> Unit)? = null,
    viewModel: PlayerViewModel =
        koinViewModel(
            parameters = { parametersOf(route) },
        ),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 单个播放引擎 + 控制器：换源只更新请求头，绝不重建播放器
    val engine = remember(context) { ExoPlayerEngine(context) }
    val controller = remember(engine, coroutineScope) { PlayerController(engine, coroutineScope) }
    val playback by controller.state.collectAsStateWithLifecycle()
    val position = controller.positionMs
    val player = controller.player

    // 纯 UI 局部状态（与播放器无关）
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var resumedForUrl by remember { mutableStateOf("") }
    var playerReady by remember { mutableStateOf(false) }

    // 视频真实宽高比：监听播放器尺寸事件，限制在 Android PiP 合法比例 (1/2.39 ~ 2.39)
    var videoAspectRatio by remember { mutableStateOf<Rational?>(null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        if (ratio in 0.41841f..2.39f) {
                            videoAspectRatio = Rational(videoSize.width, videoSize.height)
                        }
                    }
                }
            }
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            val ratio = initialSize.width.toFloat() / initialSize.height.toFloat()
            if (ratio in 0.41841f..2.39f) {
                videoAspectRatio = Rational(initialSize.width, initialSize.height)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val targetAspectRatio = videoAspectRatio ?: Rational(16, 9)

    // 视频区域在窗口中的矩形：剔除黑边后作为 PiP 的 sourceRectHint，让进出 PiP 从画面本身无缝过渡
    var rawStageBounds by remember { mutableStateOf<Rect?>(null) }
    val videoSourceRect =
        remember(rawStageBounds, targetAspectRatio, resizeMode) {
            rawStageBounds?.let {
                calculateVideoSourceRect(
                    containerBounds = it,
                    aspectRatio = targetAspectRatio,
                    resizeMode = resizeMode,
                )
            }
        }

    val isSystemLandscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isInPipMode = rememberIsInPipMode()

    val epLabel =
        remember(uiState.episodeSort, uiState.episodeType) {
            if (uiState.episodeType == 0) {
                "第 ${uiState.episodeSort.toEpisodeLabel()} 话"
            } else {
                "${EpisodeGroup.fromType(uiState.episodeType).label} ${uiState.episodeSort.toInt()}"
            }
        }

    suspend fun resumeFromSavedPositionIfNeeded() {
        if (resumedForUrl == uiState.streamUrl) return
        resumedForUrl = uiState.streamUrl
        val resume = uiState.resumePositionMs
        if (resume < MIN_RESUME_POSITION_MS) return
        val duration = playback.durationMs
        if (duration > 0L && resume >= duration * 0.95) return
        controller.seekTo(resume)
        val result =
            snackbarHostState.showSnackbar(
                message = "已恢复到上次位置 ${formatDuration(resume)}",
                actionLabel = "从头看",
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            controller.seekTo(0L)
        }
    }

    fun toggleFullscreen(landscape: Boolean) {
        if (!landscape) {
            isEpisodeDrawerOpen = false
        }
        isLandscape = landscape
        val act = activity ?: return
        act.requestedOrientation =
            if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        val window = act.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (landscape) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun buildPipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams
            .Builder()
            .setAspectRatio(targetAspectRatio)
            .setAutoEnterEnabled(autoEnter)
            // 无缝缩放：进出 PiP 时画面平滑过渡而非瞬间跳变（API 31+）
            .setSeamlessResizeEnabled(true)
            // 以实际视频画面区域为过渡起点，保证宽高比与 targetAspectRatio 一致
            .apply { videoSourceRect?.let { setSourceRectHint(it) } }
            .build()

    fun enterPictureInPicture() {
        if (!uiState.pipEnabled) return
        val act = activity ?: return
        if (act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        act.enterPictureInPictureMode(buildPipParams(autoEnter = true))
    }

    fun cycleResizeMode() {
        resizeMode =
            when (resizeMode) {
                PlayerResizeMode.FIT -> PlayerResizeMode.ZOOM
                PlayerResizeMode.ZOOM -> PlayerResizeMode.FILL
                PlayerResizeMode.FILL -> PlayerResizeMode.FIT
            }
    }

    // 物理传感器旋转联动：跟随系统横竖屏自动切入/切出全屏（PiP 模式下忽略小窗配置方向）
    LaunchedEffect(isSystemLandscape, isInPipMode) {
        if (isInPipMode) return@LaunchedEffect
        if (isSystemLandscape != isLandscape) {
            isLandscape = isSystemLandscape
            val act = activity
            if (act != null) {
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (isSystemLandscape) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    isEpisodeDrawerOpen = false
                    isScreenLocked = false
                }
            }
        }
    }

    // 进入 PiP 后释放方向锁定并关闭抽屉，使得后续点击小窗恢复时根据用户当前手机物理朝向自适应展开且不遮挡画面
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            isEpisodeDrawerOpen = false
            val act = activity
            if (act != null && act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // 轻量 MediaSession：随页面创建/释放；只为系统媒体键与 Android 12+ PiP 播放键，
    // 不做后台播放（那需要 MediaSessionService，属于后续立项）。
    DisposableEffect(controller) {
        val mediaSession = MediaSession.Builder(context, player).build()
        onDispose {
            mediaSession.release()
            controller.release()
        }
    }

    // ViewModel 一次性事件 → 提示
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is PlayerUiEvent.MarkedWatched -> Unit
            }
        }
    }

    // 播放器事件 → ViewModel（打卡、失败归因、断点恢复）
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                PlayerEngineEvent.Ready -> {
                    playerReady = true
                    viewModel.onPlaybackReady()
                    coroutineScope.launch { resumeFromSavedPositionIfNeeded() }
                }

                PlayerEngineEvent.Ended -> {
                    playerReady = false
                    viewModel.onPlaybackEnded()
                }

                is PlayerEngineEvent.Error -> viewModel.onPlaybackError(event.message)
            }
        }
    }

    // 状态对齐：URL / 请求头变化 → 装载媒体
    LaunchedEffect(uiState.streamUrl, uiState.requestHeaders) {
        if (uiState.streamUrl != playback.mediaUrl) {
            playerReady = false
            isScrubbing = false
        }
        controller.setMedia(uiState.streamUrl, uiState.requestHeaders)
    }

    // 播放位置 → ViewModel 节流落盘（StateFlow 自身已去重，无需 distinctUntilChanged）
    LaunchedEffect(controller) {
        controller.positionMs.collect { viewModel.onProgressChanged(it) }
    }

    // 断点位置迟到时补发
    LaunchedEffect(uiState.resumePositionMs, uiState.streamUrl, playerReady) {
        if (playerReady && uiState.resumePositionMs >= MIN_RESUME_POSITION_MS) {
            coroutineScope.launch { resumeFromSavedPositionIfNeeded() }
        }
    }

    LaunchedEffect(playbackSpeed, controller) {
        controller.setPlaybackSpeed(playbackSpeed)
    }

    // 控制栏自动隐藏
    LaunchedEffect(areControlsVisible, playback.isPlaying, isScrubbing) {
        if (areControlsVisible && playback.isPlaying && !isScrubbing) {
            delay(CONTROLS_AUTO_HIDE_MS)
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

    // 页面退出时恢复竖屏与系统默认亮度
    DisposableEffect(activity) {
        onDispose {
            val act = activity ?: return@onDispose
            if (act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val window = act.window
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

    BackHandler {
        if (isEpisodeDrawerOpen) {
            isEpisodeDrawerOpen = false
        } else if (isLandscape) {
            toggleFullscreen(false)
        } else {
            onBackClick()
        }
    }

    // Android 12+：播放中且画中画开关开启时「划回桌面」自动进入 PiP；暂停/结束/开关关闭时关闭自动进入。
    // 离开播放页时在 onDispose 中彻底重置 autoEnterEnabled，防止退回详情页/首页后误入画中画。
    DisposableEffect(activity, playback.isPlaying, videoSourceRect, targetAspectRatio, uiState.pipEnabled) {
        activity?.setPictureInPictureParams(buildPipParams(autoEnter = playback.isPlaying && uiState.pipEnabled))
        onDispose {
            activity?.setPictureInPictureParams(
                PictureInPictureParams
                    .Builder()
                    .setAutoEnterEnabled(false)
                    .build(),
            )
        }
    }

    // 生命周期联动：ON_PAUSE 落盘续播点；ON_STOP 才暂停。
    // PiP 中 Activity 只到 Paused、不会 Stop，因此用 ON_STOP 判定“真正离开前台”，
    // 避免进 PiP 瞬间 onPause 早于 isInPictureInPictureMode 置位而误暂停音频。
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> viewModel.flushPlaybackPosition()
                    Lifecycle.Event.ON_STOP -> controller.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 单实例渲染节点：利用 movableContentOf 在常规树与 PiP 极简树之间无损转移 Surface，
    // 彻底杜绝销毁重建引发的黑闪与解码器 Surface 重附着开销。
    val videoSurface =
        remember(player) {
            movableContentOf {
                PlayerVideoSurface(
                    player = player,
                    resizeMode = resizeMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

    // 画中画模式下只显示纯净视频视口
    if (isInPipMode) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            videoSurface()
        }
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
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerVideoStage(
                    videoContent = videoSurface,
                    playback = playback,
                    position = position,
                    streamUrl = uiState.streamUrl,
                    isResolvingSource = uiState.isResolvingSource,
                    resolveAttempt = uiState.resolveAttempt,
                    resolveAttemptTotal = uiState.resolveAttemptTotal,
                    resolvingSourceName = uiState.currentSource?.name ?: "播放源",
                    subjectName = uiState.subjectName.ifBlank { route.subjectName },
                    epLabel = epLabel,
                    episodeName = uiState.episodeName,
                    errorMessage = uiState.error,
                    isLandscape = true,
                    isLocked = isScreenLocked,
                    controlsVisible = areControlsVisible,
                    isScrubbing = isScrubbing,
                    scrubProgress = scrubProgress,
                    playbackSpeed = playbackSpeed,
                    resizeMode = resizeMode,
                    showEpisodeQueue = uiState.episodes.size > 1 || uiState.queue.size > 1,
                    onSingleTap = { areControlsVisible = !areControlsVisible },
                    onDoubleTapSeek = controller::seekTo,
                    onDoubleTapPlayPause = controller::togglePlayPause,
                    onSeekConfirm = controller::seekTo,
                    onFastForwardStart = { controller.setPlaybackSpeed(2f) },
                    onFastForwardEnd = { controller.setPlaybackSpeed(playbackSpeed) },
                    onPlayPauseToggle = controller::togglePlayPause,
                    onRewind10 = { controller.seekTo((position.value - 10_000L).coerceAtLeast(0L)) },
                    onForward10 = {
                        val maxPos = if (playback.durationMs > 0L) playback.durationMs else Long.MAX_VALUE
                        controller.seekTo((position.value + 10_000L).coerceAtMost(maxPos))
                    },
                    onScrubStart = { isScrubbing = true },
                    onScrubbing = { scrubProgress = it },
                    onScrubEnd = { progress ->
                        isScrubbing = false
                        if (playback.durationMs > 0L) {
                            controller.seekTo((progress * playback.durationMs).toLong())
                        }
                    },
                    onBackClick = { toggleFullscreen(false) },
                    onToggleFullscreen = { toggleFullscreen(false) },
                    onCycleResizeMode = ::cycleResizeMode,
                    onEnterPip = ::enterPictureInPicture,
                    onRetry = {
                        viewModel.retry()
                        controller.retry()
                    },
                    onNextSource = viewModel::selectNextSource,
                    onCyclePlaybackSpeed = {
                        playbackSpeed =
                            when (playbackSpeed) {
                                1f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2f
                                else -> 1f
                            }
                    },
                    onOpenEpisodeQueue = { isEpisodeDrawerOpen = true },
                    onToggleLock = { isScreenLocked = !isScreenLocked },
                    onRequestOpenSources = onRequestOpenSources,
                    showPipButton = uiState.pipEnabled,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                rawStageBounds = coords.boundsInWindow().toAndroidRect()
                            },
                )

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
                        onSelectSource = viewModel::selectSource,
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
            // 竖屏常规态：顶部 16:9 播放窗口 + 播放源切换 + 底部选集网格
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                ) {
                    PlayerVideoStage(
                        videoContent = videoSurface,
                        playback = playback,
                        position = position,
                        streamUrl = uiState.streamUrl,
                        isResolvingSource = uiState.isResolvingSource,
                        resolveAttempt = uiState.resolveAttempt,
                        resolveAttemptTotal = uiState.resolveAttemptTotal,
                        resolvingSourceName = uiState.currentSource?.name ?: "播放源",
                        subjectName = uiState.subjectName.ifBlank { route.subjectName },
                        epLabel = epLabel,
                        episodeName = uiState.episodeName,
                        errorMessage = uiState.error,
                        isLandscape = false,
                        isLocked = false,
                        controlsVisible = areControlsVisible,
                        isScrubbing = isScrubbing,
                        scrubProgress = scrubProgress,
                        playbackSpeed = playbackSpeed,
                        resizeMode = resizeMode,
                        showEpisodeQueue = false,
                        onSingleTap = { areControlsVisible = !areControlsVisible },
                        onDoubleTapSeek = controller::seekTo,
                        onDoubleTapPlayPause = controller::togglePlayPause,
                        onSeekConfirm = controller::seekTo,
                        onFastForwardStart = { controller.setPlaybackSpeed(2f) },
                        onFastForwardEnd = { controller.setPlaybackSpeed(playbackSpeed) },
                        onPlayPauseToggle = controller::togglePlayPause,
                        onRewind10 = { controller.seekTo((position.value - 10_000L).coerceAtLeast(0L)) },
                        onForward10 = {
                            val maxPos = if (playback.durationMs > 0L) playback.durationMs else Long.MAX_VALUE
                            controller.seekTo((position.value + 10_000L).coerceAtMost(maxPos))
                        },
                        onScrubStart = { isScrubbing = true },
                        onScrubbing = { scrubProgress = it },
                        onScrubEnd = { progress ->
                            isScrubbing = false
                            if (playback.durationMs > 0L) {
                                controller.seekTo((progress * playback.durationMs).toLong())
                            }
                        },
                        onBackClick = onBackClick,
                        onToggleFullscreen = { toggleFullscreen(true) },
                        onCycleResizeMode = ::cycleResizeMode,
                        onEnterPip = ::enterPictureInPicture,
                        onRetry = {
                            viewModel.retry()
                            controller.retry()
                        },
                        onNextSource = viewModel::selectNextSource,
                        onCyclePlaybackSpeed = {
                            playbackSpeed =
                                when (playbackSpeed) {
                                    1f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                        },
                        onOpenEpisodeQueue = {},
                        onToggleLock = {},
                        onRequestOpenSources = onRequestOpenSources,
                        showPipButton = uiState.pipEnabled,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords ->
                                    rawStageBounds = coords.boundsInWindow().toAndroidRect()
                                },
                    )
                }

                // 下半部：剧集信息、播放源切换栏、选集方块网格
                val chunks =
                    remember(uiState.episodes) {
                        if (uiState.episodes.size > EPISODE_GRID_CHUNK_SIZE) {
                            uiState.episodes.chunked(EPISODE_GRID_CHUNK_SIZE)
                        } else {
                            emptyList()
                        }
                    }

                val initialChunkIndex =
                    remember(uiState.episodes, uiState.episodeSort) {
                        val idx = uiState.episodes.indexOfFirst { it.sort == uiState.episodeSort }
                        if (idx >= 0 && chunks.isNotEmpty()) idx / EPISODE_GRID_CHUNK_SIZE else 0
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
                        chunks.map { list -> "${list.first().sort.toInt()}-${list.last().sort.toInt()}" }
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
                                onManageRules = onManageRules,
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

/**
 * 共享的视频舞台：手势层 + 渲染面 + 底边进度线 + 控制层。
 * 横竖屏差异（返回行为、是否显示选集抽屉入口）由参数注入，避免两套重复布局。
 */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerVideoStage(
    videoContent: @Composable () -> Unit,
    playback: PlaybackState,
    position: StateFlow<Long>,
    streamUrl: String,
    isResolvingSource: Boolean,
    resolveAttempt: Int,
    resolveAttemptTotal: Int,
    resolvingSourceName: String,
    subjectName: String,
    epLabel: String,
    episodeName: String,
    errorMessage: String?,
    isLandscape: Boolean,
    isLocked: Boolean,
    controlsVisible: Boolean,
    isScrubbing: Boolean,
    scrubProgress: Float,
    playbackSpeed: Float,
    resizeMode: PlayerResizeMode,
    showEpisodeQueue: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onSeekConfirm: (Long) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubbing: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onBackClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onEnterPip: () -> Unit,
    onRetry: () -> Unit,
    onNextSource: (() -> Unit)?,
    onCyclePlaybackSpeed: () -> Unit,
    onOpenEpisodeQueue: () -> Unit,
    onToggleLock: () -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    showPipButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        PlayerGestureDetector(
            isPlaying = playback.isPlaying,
            currentPositionMs = { position.value },
            totalDurationMs = { playback.durationMs },
            onSingleTap = onSingleTap,
            onDoubleTapSeek = onDoubleTapSeek,
            onDoubleTapPlayPause = onDoubleTapPlayPause,
            onSeekConfirm = onSeekConfirm,
            onFastForwardStart = onFastForwardStart,
            onFastForwardEnd = onFastForwardEnd,
            isLocked = isLocked,
        ) {
            when {
                streamUrl.isNotBlank() -> videoContent()

                isResolvingSource ->
                    PlayerResolvingView(
                        sourceName = resolvingSourceName,
                        attempt = resolveAttempt,
                        attemptTotal = resolveAttemptTotal,
                    )

                // 错误态由控制层统一渲染，这里不再重复一块错误占位
                errorMessage != null -> Unit

                else ->
                    PlayerEmptyView(
                        subjectName = subjectName,
                        epLabel = epLabel,
                        errorMessage = null,
                        onBackClick = onBackClick,
                        onRetry = onRetry,
                        onNextSource = onNextSource,
                        onRequestOpenSources = onRequestOpenSources,
                    )
            }
        }

        // 切源/切集时叠半透明遮罩（旧画面仍在播），而不是整块黑
        if (streamUrl.isNotBlank() && isResolvingSource) {
            PlayerResolvingOverlay(
                sourceName = resolvingSourceName,
                attempt = resolveAttempt,
                attemptTotal = resolveAttemptTotal,
            )
        }

        // 控制栏收起且正常播放时常驻在视频最底边的极简进度线
        AnimatedVisibility(
            visible =
                !controlsVisible &&
                    playback.isPlaying &&
                    !playback.isBuffering &&
                    !playback.isEnded &&
                    errorMessage == null &&
                    playback.durationMs > 0L,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            // 位置单独订阅：只有底边进度线随 500ms 位置跳重组
            val currentPosition by position.collectAsStateWithLifecycle()
            val progressFraction =
                if (playback.durationMs > 0L) {
                    (currentPosition.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            PlayerBottomEdgeProgressBar(progress = progressFraction)
        }

        AnimatedVisibility(
            visible =
                controlsVisible ||
                    !playback.isPlaying ||
                    playback.isBuffering ||
                    playback.isEnded ||
                    errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControlsOverlay(
                subjectName = subjectName,
                epLabel = epLabel,
                episodeName = episodeName,
                isPlaying = playback.isPlaying,
                isBuffering = playback.isBuffering,
                isEnded = playback.isEnded,
                position = position,
                totalDuration = playback.durationMs,
                isScrubbing = isScrubbing,
                scrubProgress = scrubProgress,
                isLandscape = isLandscape,
                resizeMode = resizeMode,
                errorMessage = errorMessage,
                onBackClick = onBackClick,
                onPlayPauseToggle = onPlayPauseToggle,
                onRewind10 = onRewind10,
                onForward10 = onForward10,
                onScrubStart = onScrubStart,
                onScrubbing = onScrubbing,
                onScrubEnd = onScrubEnd,
                onToggleFullscreen = onToggleFullscreen,
                onCycleResizeMode = onCycleResizeMode,
                onEnterPip = onEnterPip,
                onRetry = onRetry,
                onNextSource = onNextSource,
                onRequestOpenSources = onRequestOpenSources,
                playbackSpeed = playbackSpeed,
                onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                showEpisodeQueue = showEpisodeQueue,
                onOpenEpisodeQueue = onOpenEpisodeQueue,
                showPipButton = showPipButton,
                isLocked = isLocked,
                onToggleLock = onToggleLock,
            )
        }
    }
}

/**
 * 播放画面渲染面。用 media3 官方的 Compose `ContentFrame`：它 = `PlayerSurface` + `resizeWithContentScale`
 * + 未渲染首帧时的黑色 shutter，**自带 contentScale**（正是 `PlayerSurface` 缺少的那层），
 * 于是 FIT/ZOOM/FILL 三种比例模式得以保留。引擎是单实例，不再有旧 `AndroidView(PlayerView)` 的
 * 重绑定黑屏问题。
 */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerVideoSurface(
    player: Player,
    resizeMode: PlayerResizeMode,
    modifier: Modifier = Modifier,
) {
    ContentFrame(
        player = player,
        modifier = modifier,
        contentScale = resizeMode.toContentScale(),
        // 进/出 PiP 与轨道变化时保留上一帧，避免黑色 shutter 盖住画面造成瞬间黑闪
        keepContentOnReset = true,
        shutter = {},
    )
}

/** 播放器画面比例模式 → Compose ContentScale。 */
private fun PlayerResizeMode.toContentScale(): ContentScale =
    when (this) {
        PlayerResizeMode.FIT -> ContentScale.Fit
        PlayerResizeMode.ZOOM -> ContentScale.Crop
        PlayerResizeMode.FILL -> ContentScale.FillBounds
    }

/** Compose 几何矩形 → Android `Rect`（供 PiP `sourceRectHint` 使用）。 */
private fun androidx.compose.ui.geometry.Rect.toAndroidRect(): Rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/**
 * 计算实际视频画面在窗口中的物理矩形，供 PiP `sourceRectHint` 使用。
 * 针对 FIT 模式自动剔除黑边（letterbox/pillarbox），保证 sourceRectHint 宽高比与 PiP 宽高比一致。
 */
private fun calculateVideoSourceRect(
    containerBounds: Rect,
    aspectRatio: Rational,
    resizeMode: PlayerResizeMode,
): Rect {
    if (resizeMode != PlayerResizeMode.FIT) return containerBounds
    val containerWidth = containerBounds.width().toFloat()
    val containerHeight = containerBounds.height().toFloat()
    if (containerWidth <= 0 || containerHeight <= 0) return containerBounds

    val videoRatio = aspectRatio.numerator.toFloat() / aspectRatio.denominator.toFloat()
    val containerRatio = containerWidth / containerHeight

    return if (containerRatio > videoRatio) {
        // 容器比视频更宽（如横屏 20:9 播 16:9），左右有黑边（pillarbox）
        val actualWidth = containerHeight * videoRatio
        val horizontalPadding = (containerWidth - actualWidth) / 2f
        Rect(
            (containerBounds.left + horizontalPadding).toInt(),
            containerBounds.top,
            (containerBounds.right - horizontalPadding).toInt(),
            containerBounds.bottom,
        )
    } else {
        // 容器比视频更高，上下有黑边（letterbox）
        val actualHeight = containerWidth / videoRatio
        val verticalPadding = (containerHeight - actualHeight) / 2f
        Rect(
            containerBounds.left,
            (containerBounds.top + verticalPadding).toInt(),
            containerBounds.right,
            (containerBounds.bottom - verticalPadding).toInt(),
        )
    }
}

/** 递归解包 ContextWrapper，安全提取 ComponentActivity。 */
private tailrec fun Context.findActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * 响应式监听当前 Activity 是否处于画中画（PiP）模式。
 */
@Composable
private fun rememberIsInPipMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return false

    var isInPipMode by remember(activity) {
        mutableStateOf(activity.isInPictureInPictureMode)
    }

    DisposableEffect(activity) {
        val listener =
            Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPipMode = info.isInPictureInPictureMode
            }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    return isInPipMode
}
