package com.infinitezerone.minibgm.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews
import com.infinitezerone.minibgm.core.model.AiConfig
import com.infinitezerone.minibgm.core.model.SyncInterval
import com.infinitezerone.minibgm.core.model.ThemeMode
import com.infinitezerone.minibgm.core.model.UserProfile

private const val BGM_HOME_URL = "https://bgm.tv"

private const val BGM_WIKI_URL = "https://bgm.tv/wiki"

private const val PROJECT_GITHUB_URL = "https://github.com/infinitezerone/MiniBgm"

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
    onOpenSyncDialog: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    isClearingCache: Boolean = false,
    onOpenCrashLog: () -> Unit = {},
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    onOpenPlaybackRules: (() -> Unit)? = null,
    amoledDarkMode: Boolean = false,
    onToggleAmoledDarkMode: (Boolean) -> Unit = {},
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppearanceSettingsCard(
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            dynamicColor = dynamicColor,
            onToggleDynamicColor = onToggleDynamicColor,
            amoledDarkMode = amoledDarkMode,
            onToggleAmoledDarkMode = onToggleAmoledDarkMode,
        )

        if (onOpenPlaybackRules != null) {
            PlaybackSettingsCard(
                pipEnabled = pipEnabled,
                onTogglePipEnabled = onTogglePipEnabled,
                onOpenPlaybackRules = onOpenPlaybackRules,
            )
        }

        PreferenceSettingsCard(
            showRestrictedContent = showRestrictedContent,
            onToggleShowRestrictedContent = onToggleShowRestrictedContent,
        )

        SyncAndReminderSettingsCard(
            syncInterval = syncInterval,
            onOpenSyncDialog = onOpenSyncDialog,
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
        )

        AiAndStorageSettingsCard(
            aiConfig = aiConfig,
            onOpenAiSettingsDialog = onOpenAiSettingsDialog,
            onClearCache = onClearCache,
            isClearingCache = isClearingCache,
            onOpenCrashLog = onOpenCrashLog,
        )

        AboutAndSupportSettingsCard(
            isLoggedIn = isLoggedIn,
            activeProfile = activeProfile,
            savedAccountsCount = savedAccountsCount,
            onOpenWebUrl = onOpenWebUrl,
            onLogoutCurrentClick = onLogoutCurrentClick,
            onLogoutAllClick = onLogoutAllClick,
            isCheckingUpdate = isCheckingUpdate,
            onCheckForUpdate = onCheckForUpdate,
        )
    }
}

@Composable
internal fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    amoledDarkMode: Boolean,
    onToggleAmoledDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_appearance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            // 主题模式三选：跟随系统 / 亮色 / 深色
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onSelectThemeMode(mode) },
                        label = { Text(mode.displayName) },
                    )
                }
            }

            SettingsItemRow(
                icon = BgmIcons.Palette,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_dynamic_color_title),
                subtitle = stringResource(R.string.feature_user_settings_dynamic_color_desc),
                onClick = { onToggleDynamicColor(!dynamicColor) },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                    )
                },
            )

            SettingsItemRow(
                icon = BgmIcons.DarkMode,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_amoled_title),
                subtitle = stringResource(R.string.feature_user_settings_amoled_desc),
                onClick = { onToggleAmoledDarkMode(!amoledDarkMode) },
                trailing = {
                    Switch(
                        checked = amoledDarkMode,
                        onCheckedChange = onToggleAmoledDarkMode,
                    )
                },
            )
        }
    }
}

@Composable
internal fun PlaybackSettingsCard(
    pipEnabled: Boolean,
    onTogglePipEnabled: (Boolean) -> Unit,
    onOpenPlaybackRules: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_playback),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            if (onOpenPlaybackRules != null) {
                SettingsItemRow(
                    icon = BgmIcons.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.feature_user_title_playback_rules),
                    subtitle = stringResource(R.string.feature_user_settings_playback_manage_desc),
                    onClick = onOpenPlaybackRules,
                )
            }

            SettingsItemRow(
                icon = BgmIcons.PictureInPicture,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_pip_title),
                subtitle = stringResource(R.string.feature_user_settings_pip_desc),
                onClick = { onTogglePipEnabled(!pipEnabled) },
                trailing = {
                    Switch(
                        checked = pipEnabled,
                        onCheckedChange = onTogglePipEnabled,
                    )
                },
            )
        }
    }
}

@Composable
internal fun PreferenceSettingsCard(
    showRestrictedContent: Boolean,
    onToggleShowRestrictedContent: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_restricted),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            SettingsItemRow(
                icon = BgmIcons.VisibilityOff,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_restricted_content_title),
                subtitle = "",
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
internal fun SyncAndReminderSettingsCard(
    syncInterval: SyncInterval,
    onOpenSyncDialog: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_sync_reminder),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            SettingsItemRow(
                icon = BgmIcons.Sync,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_auto_sync_title),
                subtitle = stringResource(R.string.feature_user_settings_sync_period, syncInterval.displayName),
                onClick = onOpenSyncDialog,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            val isReminderActive = airingReminderEnabled && hasNotificationPermission
            val reminderSubtitle =
                if (!hasNotificationPermission) {
                    stringResource(R.string.feature_user_settings_reminder_perm_subtitle)
                } else {
                    stringResource(R.string.feature_user_settings_reminder_active_subtitle)
                }
            SettingsItemRow(
                icon = BgmIcons.NotificationsActive,
                iconTint = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    // 1. 每日追番更新汇总
                    SettingsItemRow(
                        icon = BgmIcons.Schedule,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.feature_user_settings_daily_summary_title),
                        subtitle =
                            if (airingDailySummaryEnabled) {
                                stringResource(
                                    R.string.feature_user_settings_daily_summary_subtitle,
                                    "%02d:00".format(airingReminderHour),
                                )
                            } else {
                                stringResource(R.string.feature_user_common_off)
                            },
                        onClick = if (airingDailySummaryEnabled) onOpenReminderHourDialog else null,
                        trailing = {
                            Switch(
                                checked = airingDailySummaryEnabled,
                                onCheckedChange = onToggleAiringDailySummary,
                            )
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    // 2. 新集开播提醒
                    val timingSubtitle =
                        when {
                            airingNotificationOffsetMinutes < 0 ->
                                stringResource(
                                    R.string.feature_user_timing_advance_minutes,
                                    -airingNotificationOffsetMinutes,
                                )
                            airingNotificationOffsetMinutes == 0 -> stringResource(R.string.feature_user_timing_on_time)
                            airingNotificationOffsetMinutes == 15 ->
                                stringResource(R.string.feature_user_timing_delay_15_platform)
                            else ->
                                stringResource(
                                    R.string.feature_user_timing_delay_minutes,
                                    airingNotificationOffsetMinutes,
                                )
                        }

                    SettingsItemRow(
                        icon = BgmIcons.PlayCircle,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.feature_user_settings_pre_air_title),
                        subtitle =
                            if (airingPreAirEnabled) {
                                stringResource(R.string.feature_user_settings_pre_air_desc)
                            } else {
                                stringResource(R.string.feature_user_common_off)
                            },
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
                            iconTint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            title = stringResource(R.string.feature_user_settings_reminder_time_title),
                            subtitle = timingSubtitle,
                            onClick = onOpenTimingBottomSheet,
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    // 3. 囤番完结提醒
                    SettingsItemRow(
                        icon = BgmIcons.Inventory,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.feature_user_settings_binge_finale_title),
                        subtitle =
                            if (airingBingeFinaleEnabled) {
                                stringResource(R.string.feature_user_settings_binge_finale_desc)
                            } else {
                                stringResource(R.string.feature_user_settings_binge_finale_off)
                            },
                        onClick = null,
                        trailing = {
                            Switch(
                                checked = airingBingeFinaleEnabled,
                                onCheckedChange = onToggleAiringBingeFinale,
                            )
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    // 4. 系统通知渠道设置入口
                    SettingsItemRow(
                        icon = BgmIcons.OpenInNew,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.feature_user_settings_system_notification_title),
                        subtitle = stringResource(R.string.feature_user_settings_system_notification_desc),
                        onClick = onOpenSystemNotificationSettings,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiAndStorageSettingsCard(
    aiConfig: AiConfig,
    onOpenAiSettingsDialog: (() -> Unit)?,
    onClearCache: () -> Unit,
    isClearingCache: Boolean,
    onOpenCrashLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_ai_storage),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            val providerDisplay =
                when (aiConfig.provider) {
                    AiConfig.PROVIDER_OLLAMA -> "Ollama / Local"
                    AiConfig.PROVIDER_GEMINI -> "Gemini"
                    else -> "Custom OpenAI"
                }
            val modelDisplay =
                aiConfig.model.ifBlank {
                    when (aiConfig.provider) {
                        AiConfig.PROVIDER_OLLAMA -> "qwen2.5:7b"
                        AiConfig.PROVIDER_GEMINI -> "gemini-2.5-flash"
                        else -> "gpt-4o-mini"
                    }
                }

            if (onOpenAiSettingsDialog != null) {
                SettingsItemRow(
                    icon = BgmIcons.Assistant,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.feature_user_settings_ai_config_title),
                    subtitle = "$providerDisplay · $modelDisplay",
                    onClick = onOpenAiSettingsDialog,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }

            SettingsItemRow(
                icon = BgmIcons.CleaningServices,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.feature_user_settings_clear_cache_title),
                subtitle =
                    if (isClearingCache) {
                        stringResource(R.string.feature_user_settings_clearing)
                    } else {
                        stringResource(R.string.feature_user_settings_clear_cache_desc)
                    },
                onClick = if (isClearingCache) null else onClearCache,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            SettingsItemRow(
                icon = BgmIcons.BugReport,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.feature_user_crash_log_title),
                subtitle = stringResource(R.string.feature_user_settings_crash_log_desc),
                onClick = onOpenCrashLog,
            )
        }
    }
}

@Composable
internal fun AboutAndSupportSettingsCard(
    isLoggedIn: Boolean,
    activeProfile: UserProfile?,
    savedAccountsCount: Int,
    onOpenWebUrl: (String) -> Unit,
    onLogoutCurrentClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCheckingUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val clientVersion =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull()
                .orEmpty()
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.feature_user_section_about),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            SettingsItemRow(
                icon = BgmIcons.BookmarkBorder,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_client_title),
                subtitle = stringResource(R.string.feature_user_settings_client_subtitle, clientVersion),
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_github),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpenWebUrl(PROJECT_GITHUB_URL) },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            SettingsItemRow(
                icon = BgmIcons.SystemUpdate,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.feature_user_settings_check_update_title),
                subtitle =
                    if (isCheckingUpdate) {
                        stringResource(R.string.feature_user_settings_checking_update)
                    } else {
                        stringResource(R.string.feature_user_settings_current_version, clientVersion)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = if (isCheckingUpdate) null else onCheckForUpdate,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            SettingsItemRow(
                icon = BgmIcons.Language,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = stringResource(R.string.feature_user_settings_bgm_site_title),
                subtitle = stringResource(R.string.feature_user_settings_bgm_site_desc),
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_web),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpenWebUrl(BGM_HOME_URL) },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            SettingsItemRow(
                icon = BgmIcons.Info,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.feature_user_settings_wiki_title),
                subtitle = stringResource(R.string.feature_user_settings_wiki_desc),
                trailing = {
                    Icon(
                        imageVector = BgmIcons.OpenInNew,
                        contentDescription = stringResource(R.string.feature_user_settings_cd_open_web),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpenWebUrl(BGM_WIKI_URL) },
            )

            if (isLoggedIn) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                val usernameText = activeProfile?.username.orEmpty().ifBlank { activeProfile?.id?.toString().orEmpty() }
                SettingsItemRow(
                    icon = BgmIcons.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.feature_user_logout_current_title),
                    subtitle = stringResource(R.string.feature_user_settings_logout_subtitle, usernameText),
                    onClick = onLogoutCurrentClick,
                )

                if (savedAccountsCount > 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    SettingsItemRow(
                        icon = BgmIcons.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.feature_user_settings_logout_all_title),
                        subtitle = stringResource(R.string.feature_user_settings_logout_all_desc),
                        onClick = onLogoutAllClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = BgmIcons.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            amoledDarkMode = true,
            onToggleAmoledDarkMode = {},
            pipEnabled = true,
            onTogglePipEnabled = {},
        )
    }
}
