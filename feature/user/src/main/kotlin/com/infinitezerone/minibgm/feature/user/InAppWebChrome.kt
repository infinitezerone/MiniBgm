package com.infinitezerone.minibgm.feature.user

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmTopAppBar

/**
 * 把 WebView 当前的环回地址转回上游公开地址（如把 `http://127.0.0.1:port/topic/1` 转成 `https://bgm.tv/topic/1`）。
 * 若当前地址已是外部标准链接，则保持原样。
 */
internal fun resolveUpstreamUrl(
    currentUrl: String?,
    upstreamHost: String = "bgm.tv",
): String {
    if (currentUrl.isNullOrBlank()) return "https://$upstreamHost"
    val uri = runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return currentUrl
    if (uri.host == "127.0.0.1" || uri.host == "localhost") {
        val path = uri.encodedPath.orEmpty()
        val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.encodedFragment?.let { "#$it" }.orEmpty()
        return "https://$upstreamHost$path$query$fragment"
    }
    return currentUrl
}

/**
 * 提取用于在顶栏显示的简洁 Host 域名（如 `bgm.tv`）。
 */
internal fun extractDisplayHost(
    currentUrl: String?,
    defaultHost: String = "bgm.tv",
): String {
    if (currentUrl.isNullOrBlank()) return defaultHost
    val uri = runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return defaultHost
    if (uri.host == "127.0.0.1" || uri.host == "localhost") return defaultHost
    return uri.host ?: defaultHost
}

/**
 * 应用内网页页面的通用外壳（登录接管页与浏览页共用）：
 * 现代紧凑顶栏（契合 BgmTopAppBar）+ 动态真实加载进度条 + 内容槽；
 * 顶栏整合了 ECH 安全连接指示、刷新、复制链接以及「用系统浏览器打开」降级入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InAppWebScaffold(
    title: String,
    subtitle: String? = null,
    progress: Int = 0,
    isBusy: Boolean = false,
    canGoBack: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    onClose: () -> Unit,
    onOpenInBrowser: (() -> Unit)? = null,
    onCopyUrl: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    closeEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BgmTopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = subtitle ?: "bgm.tv • ECH 安全加密",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (canGoBack && onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回上一页",
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onClose,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                            )
                        }
                    }
                },
                actions = {
                    if (canGoBack && onNavigateBack != null) {
                        IconButton(
                            onClick = onClose,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                            )
                        }
                    }
                    if (onReload != null) {
                        IconButton(
                            onClick = onReload,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重新加载",
                            )
                        }
                    }
                    if (onCopyUrl != null) {
                        IconButton(
                            onClick = onCopyUrl,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "复制链接",
                            )
                        }
                    }
                    if (onOpenInBrowser != null) {
                        IconButton(
                            onClick = onOpenInBrowser,
                            enabled = closeEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "用系统浏览器打开",
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                height = 54.dp,
            )

            val animatedProgress by animateFloatAsState(
                targetValue = (progress / 100f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "web_progress",
            )

            AnimatedVisibility(
                visible = isBusy && animatedProgress < 1f,
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                if (progress > 0) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
            )

            content()
        }
    }
}

/**
 * 页面初次加载或代理会话连接中的占位过渡层：
 * 避免 WebView 在网络建连与首绘期间呈现全黑/全白死屏感。
 */
@Composable
internal fun InAppWebLoadingPlaceholder(
    visible: Boolean,
    title: String = "正在建立 ECH 安全连接…",
    subtitle: String = "bgm.tv • 端到端防封锁通道",
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 页面加载/登录过程中的操作阻断遮罩。 */
@Composable
internal fun InAppWebBusyOverlay(
    text: String,
    showSpinner: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(24.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = if (showSpinner) 16.dp else 0.dp),
                )
            }
        }
    }
}

/** 环回代理不可用时的降级页：给出唯一可行的下一步，而不是留在空白页。 */
@Composable
internal fun InAppWebUnavailable(
    reason: String,
    onOpenInBrowser: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (onOpenInBrowser != null) {
            Button(onClick = onOpenInBrowser) {
                Text("用系统浏览器打开")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextButton(onClick = onClose) {
            Text("返回")
        }
    }
}
