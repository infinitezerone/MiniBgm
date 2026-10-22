package com.infinitezerone.minibgm.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.expressivePress
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.BgmContentTokens
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.DanmakuConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile

private const val BGM_HOME_URL = "https://bgm.tv"

private const val BGM_WIKI_URL = "https://bgm.tv/wiki"

internal const val PROJECT_GITHUB_URL = "https://github.com/infinitezerone/MiniBgm"

@Composable
internal fun SettingsSection(
    isLoggedIn: Boolean,
    activeProfile: UserProfile?,
    savedAccountsCount: Int,
    syncInterval: SyncInterval,
    airingReminderEnabled: Boolean,
    onToggleAiringReminder: (Boolean) -> Unit,
    airingDailySummaryEnabled: Boolean = true,
    onToggleAiringDailySummary: (Boolean) -> Unit = {},
    airingPreAirEnabled: Boolean = true,
    onToggleAiringPreAir: (Boolean) -> Unit = {},
    airingBingeFinaleEnabled: Boolean = true,
    onToggleAiringBingeFinale: (Boolean) -> Unit = {},
    airingReminderHour: Int,
    hasNotificationPermission: Boolean = true,
    aiConfig: AiConfig = AiConfig(),
    onOpenAiSettingsDialog: (() -> Unit)? = null,
    onOpenReminderHourDialog: () -> Unit,
    airingNotificationOffsetMinutes: Int = -15,
    onOpenTimingBottomSheet: () -> Unit = {},
    onOpenSystemNotificationSettings: () -> Unit = {},
    isIgnoringBatteryOptimizations: Boolean = false,
    onOpenKeepAliveSettings: () -> Unit = {},
    onOpenSyncDialog: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    isClearingCache: Boolean = false,
    onOpenCrashLog: () -> Unit = {},
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    onOpenPlaybackRules: (() -> Unit)? = null,
    danmakuConfig: DanmakuConfig = DanmakuConfig(),
    onOpenDanmakuSettings: (() -> Unit)? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    dynamicColor: Boolean = false,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    pipEnabled: Boolean = true,
    onTogglePipEnabled: (Boolean) -> Unit = {},
    showRestrictedContent: Boolean = false,
    onToggleShowRestrictedContent: (Boolean) -> Unit = {},
    isCheckingUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 版本号取自 PackageManager，与 BuildConfig 保持一致；预览环境下取不到则留空
    val context = LocalContext.current
    val clientVersion =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull()
                .orEmpty()
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppearanceSettingsCard(
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            dynamicColor = dynamicColor,
            onToggleDynamicColor = onToggleDynamicColor,
        )

        PlaybackAndContentSettingsCard(
            pipEnabled = pipEnabled,
            onTogglePipEnabled = onTogglePipEnabled,
            showRestrictedContent = showRestrictedContent,
            onToggleShowRestrictedContent = onToggleShowRestrictedContent,
            syncInterval = syncInterval,
            onOpenSyncDialog = onOpenSyncDialog,
            onOpenPlaybackRules = onOpenPlaybackRules,
            onOpenDanmakuSettings = onOpenDanmakuSettings,
            danmakuSummary =
                if (danmakuConfig.endpoint.isNotBlank()) {
                    danmakuConfig.endpoint
                } else if (danmakuConfig.appId.isNotBlank()) {
                    stringResource(R.string.feature_user_settings_danmaku_desc_configured)
                } else {
                    null
                },
        )

        ReminderSettingsCard(
            airingReminderEnabled = airingReminderEnabled,
            onToggleAiringReminder = onToggleAiringReminder,
            hasNotificationPermission = hasNotificationPermission,
            airingDailySummaryEnabled = airingDailySummaryEnabled,
            onToggleAiringDailySummary = onToggleAiringDailySummary,
            airingReminderHour = airingReminderHour,
            onOpenReminderHourDialog = onOpenReminderHourDialog,
            airingPreAirEnabled = airingPreAirEnabled,
            onToggleAiringPreAir = onToggleAiringPreAir,
            airingNotificationOffsetMinutes = airingNotificationOffsetMinutes,
            onOpenTimingBottomSheet = onOpenTimingBottomSheet,
            airingBingeFinaleEnabled = airingBingeFinaleEnabled,
            onToggleAiringBingeFinale = onToggleAiringBingeFinale,
            onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            onOpenKeepAliveSettings = onOpenKeepAliveSettings,
        )

        DataAndStorageSettingsCard(
            aiConfig = aiConfig,
            onOpenAiSettingsDialog = onOpenAiSettingsDialog,
            onClearCache = onClearCache,
            isClearingCache = isClearingCache,
            onOpenDownloads = onOpenDownloads,
        )

        AboutSettingsCard(
            onOpenWebUrl = onOpenWebUrl,
            isCheckingUpdate = isCheckingUpdate,
            onCheckForUpdate = onCheckForUpdate,
        )

        if (isLoggedIn) {
            AccountSettingsCard(
                activeProfile = activeProfile,
                savedAccountsCount = savedAccountsCount,
                onLogoutCurrentClick = onLogoutCurrentClick,
                onLogoutAllClick = onLogoutAllClick,
            )
        }
    }
}

@Composable
internal fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_appearance))
        SettingsCardContainer {
            // 主题模式三选：跟随系统 / 亮色 / 深色
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = themeMode == mode
                        val interactionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    Color.Transparent
                                },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .expressivePress(
                                        interactionSource = interactionSource,
                                        pressedScale = 0.96f,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { onSelectThemeMode(mode) },
                                    ),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.Palette,
                title = stringResource(R.string.feature_user_settings_dynamic_color_title),
                subtitle = null,
                onClick = { onToggleDynamicColor(!dynamicColor) },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                    )
                },
            )
        }
    }
}

@Composable
internal fun PlaybackAndContentSettingsCard(
    pipEnabled: Boolean,
    onTogglePipEnabled: (Boolean) -> Unit,
    showRestrictedContent: Boolean,
    onToggleShowRestrictedContent: (Boolean) -> Unit,
    syncInterval: SyncInterval,
    onOpenSyncDialog: () -> Unit,
    onOpenPlaybackRules: (() -> Unit)? = null,
    onOpenDanmakuSettings: (() -> Unit)? = null,
    danmakuSummary: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_playback_content))
        SettingsCardContainer {
            if (onOpenPlaybackRules != null) {
                SettingsItemRow(
                    icon = BgmIcons.PlayCircle,
                    title = stringResource(R.string.feature_user_title_playback_rules),
                    subtitle = null,
                    onClick = onOpenPlaybackRules,
                )
                SettingsDivider()
            }

            SettingsItemRow(
                icon = BgmIcons.Sync,
                title = stringResource(R.string.feature_user_settings_auto_sync_title),
                subtitle = syncInterval.displayName,
                onClick = onOpenSyncDialog,
            )

            if (onOpenDanmakuSettings != null) {
                SettingsDivider()
                SettingsItemRow(
                    icon = BgmIcons.ChatBubble,
                    title = stringResource(R.string.feature_user_settings_danmaku_title),
                    subtitle = danmakuSummary,
                    onClick = onOpenDanmakuSettings,
                )
            }

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.PictureInPicture,
                title = stringResource(R.string.feature_user_settings_pip_title),
                subtitle = null,
                onClick = { onTogglePipEnabled(!pipEnabled) },
                trailing = {
                    Switch(
                        checked = pipEnabled,
                        onCheckedChange = onTogglePipEnabled,
                    )
                },
            )

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.VisibilityOff,
                title = stringResource(R.string.feature_user_settings_restricted_content_title),
                subtitle = null,
                onClick = { onToggleShowRestrictedContent(!showRestrictedContent) },
                trailing = {
                    Switch(
                        checked = showRestrictedContent,
                        onCheckedChange = onToggleShowRestrictedContent,
                    )
                },
            )
        }
    }
}

@Composable
internal fun ReminderSettingsCard(
    airingReminderEnabled: Boolean,
    onToggleAiringReminder: (Boolean) -> Unit,
    hasNotificationPermission: Boolean,
    airingDailySummaryEnabled: Boolean,
    onToggleAiringDailySummary: (Boolean) -> Unit,
    airingReminderHour: Int,
    onOpenReminderHourDialog: () -> Unit,
    airingPreAirEnabled: Boolean,
    onToggleAiringPreAir: (Boolean) -> Unit,
    airingNotificationOffsetMinutes: Int,
    onOpenTimingBottomSheet: () -> Unit,
    airingBingeFinaleEnabled: Boolean,
    onToggleAiringBingeFinale: (Boolean) -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    isIgnoringBatteryOptimizations: Boolean = false,
    onOpenKeepAliveSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isReminderActive = airingReminderEnabled && hasNotificationPermission
    val reminderSubtitle =
        if (!hasNotificationPermission) {
            stringResource(R.string.feature_user_settings_reminder_perm_subtitle)
        } else {
            null
        }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_sync_reminder))
        SettingsCardContainer {
            SettingsItemRow(
                icon = BgmIcons.NotificationsActive,
                iconTint =
                    if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                iconContainerColor =
                    if (hasNotificationPermission) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    },
                title = stringResource(R.string.feature_user_settings_airing_reminder_title),
                subtitle = reminderSubtitle,
                onClick = {
                    onToggleAiringReminder(!isReminderActive)
                },
                trailing = {
                    Switch(
                        checked = isReminderActive,
                        onCheckedChange = onToggleAiringReminder,
                    )
                },
            )

            AnimatedVisibility(
                visible = isReminderActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    SettingsDivider()

                    // 1. 每日更新汇总
                    SettingsItemRow(
                        icon = BgmIcons.Schedule,
                        title = stringResource(R.string.feature_user_settings_daily_summary_title),
                        subtitle = if (airingDailySummaryEnabled) "%02d:00".format(airingReminderHour) else null,
                        onClick = if (airingDailySummaryEnabled) onOpenReminderHourDialog else null,
                        trailing = {
                            Switch(
                                checked = airingDailySummaryEnabled,
                                onCheckedChange = onToggleAiringDailySummary,
                            )
                        },
                    )

                    SettingsDivider()

                    // 2. 新集开播提醒
                    val timingSubtitle =
                        when {
                            airingNotificationOffsetMinutes < 0 ->
                                stringResource(
                                    R.string.feature_user_timing_advance_minutes,
                                    -airingNotificationOffsetMinutes,
                                )
                            airingNotificationOffsetMinutes == 0 -> stringResource(R.string.feature_user_timing_on_time)
                            else ->
                                stringResource(
                                    R.string.feature_user_timing_delay_minutes,
                                    airingNotificationOffsetMinutes,
                                )
                        }

                    SettingsItemRow(
                        icon = BgmIcons.PlayCircle,
                        title = stringResource(R.string.feature_user_settings_pre_air_title),
                        subtitle = null,
                        onClick = null,
                        trailing = {
                            Switch(
                                checked = airingPreAirEnabled,
                                onCheckedChange = onToggleAiringPreAir,
                            )
                        },
                    )

                    if (airingPreAirEnabled) {
                        SettingsItemRow(
                            icon = BgmIcons.Schedule,
                            title = stringResource(R.string.feature_user_settings_reminder_time_title),
                            subtitle = timingSubtitle,
                            onClick = onOpenTimingBottomSheet,
                        )
                    }

                    SettingsDivider()

                    // 3. 囤番完结提醒
                    SettingsItemRow(
                        icon = BgmIcons.Inventory,
                        title = stringResource(R.string.feature_user_settings_binge_finale_title),
                        subtitle = null,
                        onClick = null,
                        trailing = {
                            Switch(
                                checked = airingBingeFinaleEnabled,
                                onCheckedChange = onToggleAiringBingeFinale,
                            )
                        },
                    )

                    SettingsDivider()

                    // 4. 系统通知
                    SettingsItemRow(
                        icon = BgmIcons.OpenInNew,
                        title = stringResource(R.string.feature_user_settings_system_notification_title),
                        subtitle = null,
                        onClick = onOpenSystemNotificationSettings,
                    )

                    SettingsDivider()

                    // 5. 后台保活
                    SettingsItemRow(
                        icon = BgmIcons.Bolt,
                        title = stringResource(R.string.feature_user_settings_keep_alive_title),
                        subtitle =
                            if (isIgnoringBatteryOptimizations) {
                                stringResource(R.string.feature_user_settings_keep_alive_desc_active)
                            } else {
                                stringResource(R.string.feature_user_settings_keep_alive_desc_inactive)
                            },
                        onClick = onOpenKeepAliveSettings,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DataAndStorageSettingsCard(
    aiConfig: AiConfig,
    onOpenAiSettingsDialog: (() -> Unit)?,
    onClearCache: () -> Unit,
    isClearingCache: Boolean,
    onOpenDownloads: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val providerDisplay =
        when (aiConfig.provider) {
            AiConfig.PROVIDER_OLLAMA -> "Local"
            AiConfig.PROVIDER_GEMINI -> "Gemini"
            else -> "Custom"
        }
    val modelDisplay =
        aiConfig.model.ifBlank {
            when (aiConfig.provider) {
                AiConfig.PROVIDER_OLLAMA -> "qwen2.5:7b"
                AiConfig.PROVIDER_GEMINI -> "gemini-2.5-flash"
                else -> "gpt-4o-mini"
            }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_data_storage))
        SettingsCardContainer {
            if (onOpenDownloads != null) {
                SettingsItemRow(
                    icon = BgmIcons.Download,
                    title = stringResource(R.string.feature_user_downloads_title),
                    subtitle = null,
                    onClick = onOpenDownloads,
                )
                SettingsDivider()
            }

            if (onOpenAiSettingsDialog != null) {
                SettingsItemRow(
                    icon = BgmIcons.Assistant,
                    title = stringResource(R.string.feature_user_settings_ai_config_title),
                    subtitle = "$providerDisplay · $modelDisplay",
                    onClick = onOpenAiSettingsDialog,
                )
                SettingsDivider()
            }

            SettingsItemRow(
                icon = BgmIcons.CleaningServices,
                title = stringResource(R.string.feature_user_settings_clear_cache_title),
                subtitle =
                    if (isClearingCache) {
                        stringResource(R.string.feature_user_settings_clearing)
                    } else {
                        null
                    },
                onClick = if (isClearingCache) null else onClearCache,
            )
        }
    }
}

/**
 * 现代开发者选项统一入口卡片（已激活开发者模式后在设置页展现）。
 */
@Composable
internal fun DeveloperEntrySettingsCard(
    onOpenDeveloperOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_developer))
        SettingsCardContainer {
            SettingsItemRow(
                icon = BgmIcons.BugReport,
                title = stringResource(R.string.feature_user_dev_options_title),
                subtitle = null,
                onClick = onOpenDeveloperOptions,
            )
        }
    }
}

@Composable
internal fun AboutSettingsCard(
    onOpenWebUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    isCheckingUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onVersionClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val clientVersion =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull()
                .orEmpty()
        }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_about))
        SettingsCardContainer {
            SettingsItemRow(
                icon = BgmIcons.BookmarkBorder,
                title = stringResource(R.string.feature_user_settings_client_title),
                subtitle = stringResource(R.string.feature_user_settings_client_subtitle, clientVersion),
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_github),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                onClick = onVersionClick,
            )

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.SystemUpdate,
                title = stringResource(R.string.feature_user_settings_check_update_title),
                subtitle =
                    if (isCheckingUpdate) {
                        stringResource(R.string.feature_user_settings_checking_update)
                    } else {
                        null
                    },
                trailing = {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = BgmIcons.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.feature_user_settings_check_update_title),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                },
                onClick = if (isCheckingUpdate) null else onCheckForUpdate,
            )

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.Language,
                title = stringResource(R.string.feature_user_settings_bgm_site_title),
                subtitle = null,
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_web),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                onClick = { onOpenWebUrl(BGM_HOME_URL) },
            )

            SettingsDivider()

            SettingsItemRow(
                icon = BgmIcons.Info,
                title = stringResource(R.string.feature_user_settings_wiki_title),
                subtitle = null,
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_web),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                onClick = { onOpenWebUrl(BGM_WIKI_URL) },
            )
        }
    }
}

@Composable
internal fun AccountSettingsCard(
    activeProfile: UserProfile?,
    savedAccountsCount: Int,
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usernameText = activeProfile?.username.orEmpty().ifBlank { activeProfile?.id?.toString().orEmpty() }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.feature_user_section_account))
        SettingsCardContainer {
            SettingsItemRow(
                icon = BgmIcons.Logout,
                iconTint = MaterialTheme.colorScheme.error,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                title = stringResource(R.string.feature_user_logout_current_title),
                subtitle = if (usernameText.isNotBlank()) "@$usernameText" else null,
                onClick = onLogoutCurrentClick,
            )

            if (savedAccountsCount > 1) {
                SettingsDivider()

                SettingsItemRow(
                    icon = BgmIcons.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    title = stringResource(R.string.feature_user_settings_logout_all_title),
                    subtitle = null,
                    onClick = onLogoutAllClick,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, bottom = 6.dp, top = 4.dp),
    )
}

@Composable
internal fun SettingsCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = 64.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
    )
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .expressivePress(
                                interactionSource = interactionSource,
                                pressedScale = 0.985f,
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = iconContainerColor,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = BgmIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsSectionPreview() {
    MiniBgmTheme {
        SettingsSection(
            isLoggedIn = true,
            activeProfile = null,
            savedAccountsCount = 1,
            syncInterval = SyncInterval.DAILY,
            airingReminderEnabled = true,
            onToggleAiringReminder = {},
            airingReminderHour = 8,
            onOpenReminderHourDialog = {},
            airingNotificationOffsetMinutes = -15,
            onOpenTimingBottomSheet = {},
            onOpenSyncDialog = {},
            onOpenWebUrl = {},
            onClearCache = {},
            onLogoutCurrentClick = {},
            onLogoutAllClick = {},
            pipEnabled = true,
            onTogglePipEnabled = {},
        )
    }
}
