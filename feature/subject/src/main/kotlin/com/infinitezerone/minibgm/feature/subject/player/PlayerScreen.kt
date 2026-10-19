package com.infinitezerone.minibgm.feature.subject.player

import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Rational
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import com.infinitezerone.minibgm.core.designsystem.component.BgmOverlayHost
import com.infinitezerone.minibgm.core.designsystem.component.ObserveAsEvents
import com.infinitezerone.minibgm.core.designsystem.component.rememberOverlayHostState
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuMatch
import com.infinitezerone.minibgm.core.model.EpisodeGroup
import com.infinitezerone.minibgm.core.model.toEpisodeLabel
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.navigation.launchWebUrl
import com.infinitezerone.minibgm.feature.subject.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    onSubjectClick: ((Long) -> Unit)? = null,
    onEpisodeDetailClick: ((subjectId: Long, episodeId: Long) -> Unit)? = null,
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
    val overlayHostState = rememberOverlayHostState()
    val configuration = LocalConfiguration.current
    val clipboardManager = LocalClipboard.current

    val handleCopyComment: (String) -> Unit = { text ->
        coroutineScope.launch {
            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("Comment", text)))
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.feature_subject_ep_copied_content),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 单个播放引擎 + 控制器：换源只更新请求头，绝不重建播放器
    val engine = remember(context) { ExoPlayerEngine(context) }
    val controller = remember(engine, coroutineScope) { PlayerController(engine, coroutineScope) }
    val playback by controller.state.collectAsStateWithLifecycle()
    val position = controller.positionMs
    val bufferedPosition = controller.bufferedPositionMs
    val player = controller.player

    val openSettingsPanel: (PlayerSettingsTab) -> Unit = { tab ->
        controller.refreshTracksSnapshot()
        coroutineScope.launch { overlayHostState.await(PlayerSettingsRequest(tab)) }
    }

    // 弹幕分集纠正：统一直接拉起设置抽屉的弹幕 Tab（内聚分集匹配卡片与候选切换器）
    val openDanmakuPicker: () -> Unit = {
        openSettingsPanel(PlayerSettingsTab.DANMAKU)
    }
    val openEpisodeQueuePanel: () -> Unit = {
        coroutineScope.launch { overlayHostState.await(PlayerEpisodeQueueRequest) }
    }
    val openEpisodesPanel: () -> Unit = {
        coroutineScope.launch { overlayHostState.await(PlayerEpisodesRequest) }
    }
    val openSourcesPanel: () -> Unit = {
        controller.syncPosition()
        coroutineScope.launch { overlayHostState.await(PlayerSourcesRequest) }
    }

    // 纯 UI 局部状态（与播放器无关）
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    // 轨道快照与视频宽高比由 PlayerController 从引擎事件播报（含轨道选择参数变化），UI 不再直挂 Player.Listener
    val tracksSnapshot by controller.tracksSnapshot.collectAsStateWithLifecycle()
    val videoAspectRatio by controller.videoAspectRatio.collectAsStateWithLifecycle()
    var isScreenLocked by remember { mutableStateOf(false) }
    // 拖动进度唯一真源：屏幕横向手势与底部进度条共用同一份状态（不再各自维护 isScrubbing + scrubProgress）
    val scrub = remember { PlayerScrubState() }
    var lastResumedEpisodeId by remember { mutableStateOf<Long?>(null) }
    var resumeOverlayPosition by remember { mutableStateOf<Long?>(null) }
    var playerReady by remember { mutableStateOf(false) }
    var selectedPortraitTab by rememberSaveable { mutableStateOf(PlayerPortraitTab.INTRO) }

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
        if (uiState.episodeType == 0) {
            stringResource(R.string.feature_subject_episode_format, uiState.episodeSort.toEpisodeLabel())
        } else {
            stringResource(
                R.string.feature_subject_episode_custom_format,
                EpisodeGroup.fromType(uiState.episodeType).label,
                uiState.episodeSort.toInt(),
            )
        }

    fun toggleFullscreen(landscape: Boolean) {
        // 面板类浮层随形态切换收起（横竖两套布局不同，保留面板意义不大）
        overlayHostState.cancelActive()
        isLandscape = landscape
        if (!landscape) {
            isScreenLocked = false
        }
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
        // 手动入口不受「自动画中画」开关影响：那个开关只决定返回桌面时是否自动小窗
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

    // 物理传感器旋转联动：跟随系统横竖屏自动切入/切出全屏（PiP 模式与屏幕锁定下忽略传感器方向）
    LaunchedEffect(isSystemLandscape, isInPipMode, isScreenLocked) {
        if (isInPipMode || isScreenLocked) return@LaunchedEffect
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
                    overlayHostState.cancelActive()
                    isScreenLocked = false
                }
            }
        }
    }

    // 进入 PiP 后释放方向锁定、解除屏幕锁定并关闭抽屉，使得后续点击小窗恢复时根据用户当前手机物理朝向自适应展开且不遮挡画面
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            overlayHostState.cancelActive()
            isScreenLocked = false
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
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PlayerUiEvent.ShowSnackbar -> {
                val text =
                    event.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(event.messageRes, *event.formatArgs.toTypedArray())
                // 动作按钮文案以 @StringRes 下发，非 Composable 回调内必须经 resources 解析（配置变更才刷新）
                val actionLabel =
                    event.action
                        ?.takeIf { event.actionRes != 0 }
                        ?.let { context.resources.getString(event.actionRes) }
                val result =
                    snackbarHostState.showSnackbar(
                        message = text,
                        actionLabel = actionLabel,
                        duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                // 换线失败的「切换线路」直接把用户送回统一线路面板，省掉「先找入口再点线路」那一次点击
                if (result == SnackbarResult.ActionPerformed &&
                    event.action == PlayerUiEvent.PlayerSnackbarAction.OPEN_SOURCES
                ) {
                    openSourcesPanel()
                }
            }
            is PlayerUiEvent.MarkedWatched -> Unit
        }
    }

    // 播放器事件 → ViewModel（打卡、失败归因、断点恢复）
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                PlayerEngineEvent.Ready -> {
                    playerReady = true
                    viewModel.onPlaybackReady()
                }

                PlayerEngineEvent.Ended -> {
                    val wasReady = playerReady
                    playerReady = false
                    if (wasReady) {
                        viewModel.onPlaybackEnded()
                    }
                }

                is PlayerEngineEvent.Error -> viewModel.onPlaybackError(event.message)
            }
        }
    }

    // 状态对齐：URL / 请求头变化 → 装载媒体
    LaunchedEffect(uiState.streamUrl, uiState.requestHeaders) {
        if (uiState.streamUrl != playback.mediaUrl) {
            playerReady = false
            scrub.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        val initialPosition = uiState.resumePositionMs.takeIf { it >= MIN_RESUME_POSITION_MS } ?: 0L
        controller.setMedia(uiState.streamUrl, uiState.requestHeaders, initialPosition)
    }

    // 播放位置 → ViewModel 节流落盘（StateFlow 自身已去重，无需 distinctUntilChanged）
    LaunchedEffect(controller) {
        controller.positionMs.collect { viewModel.onProgressChanged(it) }
    }

    // 媒体总时长 → ViewModel：观看记录的进度分母，只能来自引擎实测
    LaunchedEffect(playback.durationMs) {
        viewModel.onDurationChanged(playback.durationMs)
    }

    // 换集时重置续播提示状态
    LaunchedEffect(uiState.episodeId) {
        resumeOverlayPosition = null
    }

    // 续播断点恢复与内置浮层触发：仅对新分集且有此前记录时触发一次（同分集换线无缝继承进度，不重复提示）
    LaunchedEffect(uiState.episodeId, uiState.streamUrl, playerReady, uiState.resumePositionMs) {
        if (playerReady && lastResumedEpisodeId != uiState.episodeId) {
            val resume = uiState.resumePositionMs
            if (resume >= MIN_RESUME_POSITION_MS) {
                lastResumedEpisodeId = uiState.episodeId
                val duration = playback.durationMs
                if (duration <= 0L || resume < duration * 0.95) {
                    if (position.value < MIN_RESUME_POSITION_MS) {
                        controller.seekTo(resume)
                    }
                    resumeOverlayPosition = resume
                }
            } else if (position.value >= MIN_RESUME_POSITION_MS) {
                // 当前分集已经从头播放超过了阈值，确认本集是从头播放的，直接锁定，后续决不弹窗
                lastResumedEpisodeId = uiState.episodeId
            }
        }
    }

    LaunchedEffect(playbackSpeed, controller) {
        controller.setPlaybackSpeed(playbackSpeed)
    }

    // 「关闭字幕」是设备侧长期偏好（引擎参数只表达本次播放，重启即丢）：
    // 偏好投影一到位就把引擎字幕轨按它关掉。只处理「关闭」方向——偏好为「未关闭」时
    // 绝不反向强制打开并清轨道选择（那会抹掉用户刚选的轨道），交由引擎按系统语言自动选轨。
    LaunchedEffect(uiState.subtitlesDisabled, controller) {
        controller.applySubtitlesDisabledPreference(uiState.subtitlesDisabled)
    }

    // 控制栏自动隐藏：拖动期间不计时。
    // 用 snapshotFlow 观察拖动态而不是把它读进组合作用域，否则拖动每帧都会重组整页
    LaunchedEffect(areControlsVisible, playback.isPlaying, scrub) {
        if (!areControlsVisible || !playback.isPlaying) return@LaunchedEffect
        snapshotFlow { scrub.state.active }.collectLatest { isScrubbingNow ->
            if (!isScrubbingNow) {
                delay(CONTROLS_AUTO_HIDE_MS)
                areControlsVisible = false
            }
        }
    }

    // 播放出错时收起任何活跃面板（设置/选集/弹幕候选），让错误态完整露出——
    // 面板内操作对已死的流无反馈，且遮罩会挡住"重试/换源"按钮
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            overlayHostState.cancelActive()
        }
    }

    // 保持屏幕常亮：仅在播放中常亮，暂停/结束/错误时交还系统自动息屏超时（省电与防烧屏）
    DisposableEffect(activity, playback.isPlaying) {
        if (playback.isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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

    // 面板类浮层各自注册 BackHandler（OverlayHost 单槽，同一时刻只有最上层面板拦截返回键）；
    // 这里只剩页面级回退：横屏屏幕锁定拦截并唤出解锁提示，未锁定时退全屏；竖屏交给导航返回
    BackHandler {
        if (isLandscape) {
            if (isScreenLocked) {
                areControlsVisible = true
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_subject_player_screen_locked),
                        duration = SnackbarDuration.Short,
                    )
                }
                return@BackHandler
            }
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
                    Lifecycle.Event.ON_PAUSE -> {
                        controller.syncPosition()
                        viewModel.flushPlaybackPosition()
                    }
                    Lifecycle.Event.ON_STOP -> controller.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.syncPosition()
            viewModel.flushPlaybackPosition()
        }
    }

    // 清晰度选择记忆：起播（mediaUrl 变化）后轨道就绪时，自动匹配用户偏好高度最接近的档位；
    // 每个媒体地址只自动应用一次，之后的手动切换不受干扰（用户手动选择经 onQualitySelected 更新偏好）
    val preferredQualityHeight by viewModel.preferredQualityHeight.collectAsStateWithLifecycle()
    var qualityAppliedKey by remember { mutableStateOf("") }
    LaunchedEffect(playback.mediaUrl, tracksSnapshot, preferredQualityHeight) {
        val target = preferredQualityHeight
        if (target <= 0 || playback.mediaUrl.isBlank() || qualityAppliedKey == playback.mediaUrl) {
            return@LaunchedEffect
        }
        val tracks = tracksSnapshot.qualityTracks
        if (tracks.isEmpty()) return@LaunchedEffect
        val match = tracks.minByOrNull { kotlin.math.abs(it.height - target) }
        if (match != null && match.group != null) {
            controller.selectVideoQuality(match)
        }
        qualityAppliedKey = playback.mediaUrl
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

    // 横竖屏共用同一段 PlayerVideoStage 装配（约 50 参），仅 PlayerStageConfig 声明的 8 项随形态不同——
    // 消灭两处 95 行调用点 85% 的重复，防止后续加参数时两处漂移
    @Composable
    fun PlayerStageSection(config: PlayerStageConfig) {
        PlayerVideoStage(
            videoContent = videoSurface,
            playback = playback,
            position = position,
            bufferedPosition = bufferedPosition,
            streamUrl = uiState.streamUrl,
            isResolvingSource = uiState.isResolvingSource,
            resolveAttempt = uiState.resolveAttempt,
            resolveAttemptTotal = uiState.resolveAttemptTotal,
            resolvingSourceName = uiState.currentSource?.name ?: stringResource(R.string.feature_subject_player_sources),
            subjectName = uiState.subjectName.ifBlank { route.subjectName },
            epLabel = epLabel,
            episodeName = uiState.episodeName,
            errorMessage = uiState.error,
            isLandscape = config.isLandscape,
            isLocked = config.isLocked,
            controlsVisible = areControlsVisible,
            scrub = scrub,
            playbackSpeed = playbackSpeed,
            resizeMode = resizeMode,
            showEpisodeQueue = config.showEpisodeQueue,
            onSingleTap = { areControlsVisible = !areControlsVisible },
            onDoubleTapSeek = controller::seekTo,
            onDoubleTapPlayPause = controller::togglePlayPause,
            onFastForwardStart = { controller.setPlaybackSpeed(2f) },
            onFastForwardEnd = { controller.setPlaybackSpeed(playbackSpeed) },
            onPlayPauseToggle = controller::togglePlayPause,
            onRewind10 = { controller.seekTo((position.value - 10_000L).coerceAtLeast(0L)) },
            onForward10 = {
                val maxPos = if (playback.durationMs > 0L) playback.durationMs else Long.MAX_VALUE
                controller.seekTo((position.value + 10_000L).coerceAtMost(maxPos))
            },
            onScrubStart = scrub::begin,
            onScrubbing = scrub::update,
            // 唯一的提交点：进度的落库/跳转只在这里发生，手势与进度条共用同一条路径
            onScrubEnd = { fraction ->
                scrub.end(fraction)
                if (playback.durationMs > 0L) {
                    controller.seekTo((fraction * playback.durationMs).toLong())
                }
            },
            onScrubCancel = scrub::cancel,
            onBackClick = config.onBackClick,
            onToggleFullscreen = config.onToggleFullscreen,
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
            onOpenEpisodeQueue = config.onOpenEpisodeQueue,
            onOpenSourcesPanel = openSourcesPanel,
            onToggleLock = config.onToggleLock,
            onRequestOpenSources = onRequestOpenSources,
            // 手动画中画入口常驻：关闭「自动画中画」不等于用户不想要小窗
            showPipButton = true,
            currentQualityLabel = tracksSnapshot.currentQualityDisplayLabel,
            onOpenTrackSelection = { openSettingsPanel(PlayerSettingsTab.PLAYBACK) },
            danmakus = uiState.danmakus,
            isDanmakuEnabled = uiState.isDanmakuEnabled,
            onToggleDanmaku = viewModel::toggleDanmaku,
            danmakuMatch = uiState.danmakuMatch,
            onOpenDanmakuPicker = openDanmakuPicker,
            danmakuAlpha = uiState.danmakuAlpha,
            danmakuDurationMs = uiState.danmakuDurationMs,
            danmakuTimeOffsetMs = uiState.danmakuTimeOffsetMs,
            hasNextEpisode = uiState.hasNext,
            onPlayNextEpisode = viewModel::playNextEpisode,
            resumeOverlayPosition = resumeOverlayPosition,
            onResumeSeekToBeginning = {
                controller.seekTo(0L)
                resumeOverlayPosition = null
            },
            onResumeDismiss = { resumeOverlayPosition = null },
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        rawStageBounds = coords.boundsInWindow().toAndroidRect()
                    },
        )
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
                PlayerStageSection(
                    PlayerStageConfig(
                        isLandscape = true,
                        isLocked = isScreenLocked,
                        showEpisodeQueue = uiState.episodes.size > 1 || uiState.queue.size > 1,
                        onBackClick = { toggleFullscreen(false) },
                        onToggleFullscreen = { toggleFullscreen(false) },
                        onOpenEpisodeQueue = openEpisodeQueuePanel,
                        onToggleLock = {
                            overlayHostState.cancelActive()
                            isScreenLocked = !isScreenLocked
                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message =
                                        context.getString(
                                            if (isScreenLocked) {
                                                R.string.feature_subject_player_screen_locked
                                            } else {
                                                R.string.feature_subject_player_screen_unlocked
                                            },
                                        ),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    ),
                )
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
                    PlayerStageSection(
                        PlayerStageConfig(
                            isLandscape = false,
                            isLocked = false,
                            showEpisodeQueue = false,
                            onBackClick = onBackClick,
                            onToggleFullscreen = { toggleFullscreen(true) },
                            onOpenEpisodeQueue = {},
                            onToggleLock = {},
                        ),
                    )
                }

                // B站风格双 Tab 分栏：[简介] 与 [讨论 (数量)]
                PrimaryTabRow(
                    selectedTabIndex = selectedPortraitTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = selectedPortraitTab == PlayerPortraitTab.INTRO,
                        onClick = { selectedPortraitTab = PlayerPortraitTab.INTRO },
                        text = {
                            Text(
                                text = stringResource(R.string.feature_subject_player_tab_intro),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedPortraitTab == PlayerPortraitTab.INTRO) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                    Tab(
                        selected = selectedPortraitTab == PlayerPortraitTab.DISCUSSION,
                        onClick = { selectedPortraitTab = PlayerPortraitTab.DISCUSSION },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_subject_player_tab_discussion),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight =
                                        if (selectedPortraitTab ==
                                            PlayerPortraitTab.DISCUSSION
                                        ) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                )
                                if (uiState.currentEpisodeCommentCount > 0) {
                                    Badge(
                                        containerColor =
                                            if (selectedPortraitTab == PlayerPortraitTab.DISCUSSION) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                        contentColor =
                                            if (selectedPortraitTab == PlayerPortraitTab.DISCUSSION) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ) {
                                        Text(
                                            text = "${uiState.currentEpisodeCommentCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                // 自动拉取当前分集的吐槽列表
                LaunchedEffect(uiState.episodeId) {
                    if (uiState.episodeId > 0L) {
                        viewModel.loadComments()
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    when (selectedPortraitTab) {
                        PlayerPortraitTab.INTRO -> {
                            PlayerIntroContent(
                                uiState = uiState,
                                epLabel = epLabel,
                                onToggleWatched = viewModel::manualToggleWatched,
                                onToggleFollow = viewModel::toggleFollowSubject,
                                onSelectEpisode = viewModel::selectEpisode,
                                onOpenAllEpisodes = openEpisodesPanel,
                                onOpenSourcesPanel = openSourcesPanel,
                                onRequestOpenSources = onRequestOpenSources,
                                onSubjectClick = {
                                    if (uiState.subjectId > 0 && onSubjectClick != null) {
                                        onSubjectClick(uiState.subjectId)
                                    }
                                },
                                onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                            )
                        }
                        PlayerPortraitTab.DISCUSSION -> {
                            PlayerDiscussionContent(
                                uiState = uiState,
                                onSortChange = viewModel::setCommentSortOrder,
                                onRetry = { viewModel.loadComments(force = true) },
                                onReactionClick = viewModel::toggleCommentReaction,
                                onAddReaction = viewModel::toggleCommentReaction,
                                onCopyComment = handleCopyComment,
                                onUserClick = { username -> context.launchWebUrl("https://bgm.tv/user/$username") },
                                onUrlClick = { url -> context.launchWebUrl(url) },
                            )
                        }
                    }
                }
            }
        }

        // 面板类浮层统一经 BgmOverlayHost 承接（单槽互斥 + 各自的返回键拦截）；
        // 无请求时不渲染任何内容
        BgmOverlayHost(hostState = overlayHostState) {
            danmakuCandidatePicker()
            playerSettingsPanel(
                tracksSnapshot = { tracksSnapshot },
                playbackSpeed = { playbackSpeed },
                onSelectSpeed = { playbackSpeed = it },
                resizeMode = { resizeMode },
                onSelectResizeMode = { resizeMode = it },
                isDanmakuEnabled = { uiState.isDanmakuEnabled },
                onToggleDanmaku = viewModel::toggleDanmaku,
                onOpenDanmakuPicker =
                    openDanmakuPicker.takeIf {
                        uiState.isDanmakuEnabled && !uiState.danmakuMatch?.candidates.isNullOrEmpty()
                    },
                danmakuMatch = { uiState.danmakuMatch },
                onSelectDanmakuCandidate = viewModel::selectDanmakuCandidate,
                danmakuAlpha = { uiState.danmakuAlpha },
                onSelectDanmakuAlpha = viewModel::setDanmakuAlpha,
                danmakuDurationMs = { uiState.danmakuDurationMs },
                onSelectDanmakuDurationMs = viewModel::setDanmakuDurationMs,
                danmakuTimeOffsetMs = { uiState.danmakuTimeOffsetMs },
                onSelectDanmakuTimeOffset = viewModel::setDanmakuTimeOffsetMs,
                isLooping = { playback.isLooping },
                onToggleLoop = { controller.setLooping(!playback.isLooping) },
                onSelectQuality = {
                    controller.selectVideoQuality(it)
                    viewModel.onQualitySelected(it.height)
                },
                onAutoQuality = {
                    controller.selectAutoVideoQuality()
                    viewModel.onQualitySelected(0)
                },
                // 字幕：引擎动作当场生效 + 立场落库（与清晰度同一形态），进页由偏好投影恢复
                onSelectSubtitle = {
                    controller.selectSubtitleTrack(it)
                    viewModel.setSubtitlesDisabled(false)
                },
                onDisableSubtitles = {
                    controller.disableSubtitles()
                    viewModel.setSubtitlesDisabled(true)
                },
                onAutoSubtitles = {
                    controller.autoSelectSubtitles()
                    viewModel.setSubtitlesDisabled(false)
                },
                onSelectAudio = controller::selectAudioTrack,
                isLandscape = { isLandscape },
            )
            playerEpisodeQueuePanel(
                uiState = { uiState },
                onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                onToggleAutoNext = viewModel::toggleAutoNext,
                onSelectEpisode = viewModel::selectEpisode,
                onSelectQueueIndex = viewModel::switchTo,
            )
            playerEpisodesPanel(
                uiState = { uiState },
                onLoadMoreEpisodes = viewModel::loadMoreEpisodes,
                onSelectEpisode = viewModel::selectEpisode,
            )
            playerSourcesPanel(
                uiState = { uiState },
                isLandscape = { isLandscape },
                onSelectSource = viewModel::selectSource,
                onManageRules = onManageRules,
                onRequestOpenSources = onRequestOpenSources,
            )
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
    bufferedPosition: StateFlow<Long>,
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
    scrub: PlayerScrubState,
    playbackSpeed: Float,
    resizeMode: PlayerResizeMode,
    showEpisodeQueue: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (Long) -> Unit,
    onDoubleTapPlayPause: () -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onScrubStart: (Float) -> Unit,
    onScrubbing: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onScrubCancel: () -> Unit,
    onBackClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onEnterPip: () -> Unit,
    onRetry: () -> Unit,
    onNextSource: (() -> Unit)?,
    onCyclePlaybackSpeed: () -> Unit,
    onOpenEpisodeQueue: () -> Unit,
    onOpenSourcesPanel: (() -> Unit)? = null,
    onToggleLock: () -> Unit,
    onRequestOpenSources: (() -> Unit)?,
    showPipButton: Boolean = true,
    currentQualityLabel: String? = null,
    onOpenTrackSelection: () -> Unit = {},
    danmakus: List<DanmakuItem> = emptyList(),
    isDanmakuEnabled: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    danmakuMatch: DanmakuMatch? = null,
    onOpenDanmakuPicker: () -> Unit = {},
    danmakuAlpha: Float = 1.0f,
    danmakuDurationMs: Long = 8500L,
    danmakuTimeOffsetMs: Long = 0L,
    hasNextEpisode: Boolean = false,
    onPlayNextEpisode: (() -> Unit)? = null,
    resumeOverlayPosition: Long? = null,
    onResumeSeekToBeginning: () -> Unit = {},
    onResumeDismiss: () -> Unit = {},
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
            onFastForwardStart = onFastForwardStart,
            onFastForwardEnd = onFastForwardEnd,
            isLocked = isLocked,
            onScrubStart = onScrubStart,
            onScrubbing = onScrubbing,
            onScrubEnd = onScrubEnd,
            onScrubCancel = onScrubCancel,
        ) {
            when {
                streamUrl.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        videoContent()
                        val currentMs by position.collectAsStateWithLifecycle()
                        DanmakuView(
                            danmakus = danmakus,
                            currentPositionMs = currentMs,
                            isEnabled = isDanmakuEnabled,
                            isPlaying = playback.isPlaying,
                            playbackSpeed = playbackSpeed,
                            timeOffsetMs = danmakuTimeOffsetMs,
                            durationMs = danmakuDurationMs,
                            alpha = danmakuAlpha,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

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
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            // 位置单独订阅：只有底边进度线随 500ms 位置跳重组
            val currentPosition by position.collectAsStateWithLifecycle()
            val currentBufferedPosition by bufferedPosition.collectAsStateWithLifecycle()
            // 拖动中读 PlayerScrubState：本节点是独立的重组作用域，不会波及整页
            val scrubState = scrub.state
            val progressFraction =
                when {
                    scrubState.active -> scrubState.fraction
                    playback.durationMs > 0L -> (currentPosition.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                    else -> 0f
                }
            val bufferedFraction =
                if (playback.durationMs > 0L) {
                    (currentBufferedPosition.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            PlayerBottomEdgeProgressBar(
                progress = progressFraction,
                bufferedProgress = bufferedFraction,
            )
        }

        // 独立缓冲加载指示器（500ms 防抖）：微缓冲（<500ms）静止帧等待不打扰观影；
        // 持续卡顿（>500ms）时仅在画面中央淡入极简转圈，绝不唤起控制层与暗色渐变遮罩。
        val showBufferingSpinner by
            produceState(initialValue = false, playback.isBuffering) {
                if (playback.isBuffering) {
                    delay(500L)
                    value = true
                } else {
                    value = false
                }
            }

        AnimatedVisibility(
            visible = showBufferingSpinner && !playback.isEnded && errorMessage == null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp),
            )
        }

        AnimatedVisibility(
            visible =
                controlsVisible ||
                    playback.isEnded ||
                    errorMessage != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(100)),
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
                bufferedPosition = bufferedPosition,
                totalDuration = playback.durationMs,
                scrub = scrub,
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
                onOpenSourcesPanel = onOpenSourcesPanel,
                playbackSpeed = playbackSpeed,
                onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                showEpisodeQueue = showEpisodeQueue,
                onOpenEpisodeQueue = onOpenEpisodeQueue,
                showPipButton = showPipButton,
                currentQualityLabel = currentQualityLabel,
                onOpenTrackSelection = onOpenTrackSelection,
                isDanmakuEnabled = isDanmakuEnabled,
                onToggleDanmaku = onToggleDanmaku,
                danmakuMatch = danmakuMatch,
                onOpenDanmakuPicker = onOpenDanmakuPicker,
                isLocked = isLocked,
                onToggleLock = onToggleLock,
                onSingleTap = onSingleTap,
                hasNextEpisode = hasNextEpisode,
                onPlayNextEpisode = onPlayNextEpisode,
            )
        }

        val resumeOverlayBottomPadding by animateDpAsState(
            targetValue =
                if (controlsVisible) {
                    if (isLandscape) 80.dp else 56.dp
                } else {
                    if (isLandscape) 28.dp else 16.dp
                },
            animationSpec = tween(100),
            label = "resumeOverlayBottomPadding",
        )

        AnimatedVisibility(
            visible = resumeOverlayPosition != null && !isLocked,
            enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { it / 2 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { it / 2 },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .then(if (isLandscape) Modifier.displayCutoutPadding() else Modifier)
                    .padding(
                        start = if (isLandscape) 24.dp else 16.dp,
                        bottom = resumeOverlayBottomPadding,
                    ),
        ) {
            resumeOverlayPosition?.let { resumeMs ->
                PlayerResumeOverlay(
                    positionMs = resumeMs,
                    onSeekToBeginning = onResumeSeekToBeginning,
                    onDismiss = onResumeDismiss,
                )
            }
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            contentScale = resizeMode.toContentScale(),
            // 进/出 PiP 与轨道变化时保留上一帧，避免黑色 shutter 盖住画面造成瞬间黑闪
            keepContentOnReset = true,
            shutter = {},
        )
        PlayerSubtitleOverlay(
            player = player,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 与 [Player] 直连的软字幕呈现视图。
 *
 * 避坑说明：
 * 绝不能经由 Compose [mutableStateOf] / [AndroidView.update] 中转高频字幕事件。
 * 在 Compose 节点转移（如横竖屏与 PiP 的 [movableContentOf] 切换）期间，
 * AndroidViewHolder 的快照观察器容易丢失或吞没更新，导致字幕永远卡死在某一帧、
 * 且在控制面板点击「关闭字幕」也无法清空。
 *
 * 本控件将 [Player.Listener] 绑定到自身 [View.OnAttachStateChangeListener]：
 * 1. 附着到窗口时立即挂载监听，脱离窗口时注销并置空，避免跨生命周期泄漏与幽灵展示；
 * 2. 直连分发 [Player.Listener.onCues]，零中转零延迟；
 * 3. 拦截 [onTrackSelectionParametersChanged]、[onTracksChanged] 与 [onPositionDiscontinuity]：
 *    当字幕被用户显式关闭时立即调用 [setCues(emptyList())] 强制清屏，切轨或快进快退时同步最新字幕。
 */
@OptIn(UnstableApi::class)
internal class PlayerBoundSubtitleView(
    context: Context,
    private val player: Player,
) : android.widget.FrameLayout(context),
    View.OnAttachStateChangeListener {
    private val subtitleView =
        SubtitleView(context).apply {
            setStyle(
                CaptionStyleCompat(
                    // foregroundColor =
                    android.graphics.Color.WHITE,
                    // backgroundColor =
                    android.graphics.Color.TRANSPARENT,
                    // windowColor =
                    android.graphics.Color.TRANSPARENT,
                    // edgeType =
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    // edgeColor =
                    android.graphics.Color.BLACK,
                    // typeface =
                    null,
                ),
            )
            setApplyEmbeddedStyles(true)
            setApplyEmbeddedFontSizes(true)
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.05f)
            setBottomPaddingFraction(0.06f)
            isClickable = false
            isFocusable = false
        }

    private val playerListener =
        object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                syncCues(cueGroup.cues)
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                    syncCues(emptyList())
                } else {
                    syncCues(player.currentCues.cues)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                syncCues(player.currentCues.cues)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                syncCues(player.currentCues.cues)
            }
        }

    init {
        addView(
            subtitleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addOnAttachStateChangeListener(this)
        if (isAttachedToWindow) {
            player.addListener(playerListener)
            syncCues(player.currentCues.cues)
        }
    }

    var renderedCues: List<Cue> = emptyList()
        private set

    fun syncCues(cues: List<Cue> = player.currentCues.cues) {
        val target =
            if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                emptyList()
            } else {
                cues
            }
        renderedCues = target
        subtitleView.setCues(target)
    }

    override fun onViewAttachedToWindow(v: View) {
        player.removeListener(playerListener)
        player.addListener(playerListener)
        syncCues(player.currentCues.cues)
    }

    override fun onViewDetachedFromWindow(v: View) {
        player.removeListener(playerListener)
        renderedCues = emptyList()
        subtitleView.setCues(emptyList())
    }

    fun release() {
        removeOnAttachStateChangeListener(this)
        player.removeListener(playerListener)
        renderedCues = emptyList()
        subtitleView.setCues(emptyList())
    }
}

/**
 * 软字幕图层：使用 [PlayerBoundSubtitleView] 渲染内嵌或外挂字幕（ASS/SSA 特效字幕、SRT 等）。
 * [ContentFrame] 仅承载视频图形图层，通过在此处叠加并直连 [Player.Listener] 补齐字幕呈现。
 */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerSubtitleOverlay(
    player: Player,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerBoundSubtitleView(ctx, player)
        },
        update = { view ->
            view.syncCues()
        },
        onReset = { view ->
            view.syncCues(emptyList())
        },
        onRelease = { view ->
            view.release()
        },
        modifier = modifier,
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

/**
 * 横竖屏共用同一段 [PlayerVideoStage] 装配时，仅随形态不同的差异项。
 * 其余约 45 个参数（播放状态、手势回调、弹幕、轨道入口等）在 PlayerStageSection 中固定，
 * 新增能力只改一处，不再出现两份调用点各自漂移。
 */
private data class PlayerStageConfig(
    val isLandscape: Boolean,
    val isLocked: Boolean,
    val showEpisodeQueue: Boolean,
    val onBackClick: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onOpenEpisodeQueue: () -> Unit,
    val onToggleLock: () -> Unit,
)
