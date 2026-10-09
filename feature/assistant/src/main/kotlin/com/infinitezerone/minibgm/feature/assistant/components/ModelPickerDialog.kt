package com.infinitezerone.minibgm.feature.assistant.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.assistant.R
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 智能模型聚合卡片组件
 * 紧凑展示当前选中的模型、状态/来源，并提供原地探测同步能力
 */
@Composable
internal fun ModelSelectorCard(
    model: String,
    selectedProvider: String,
    selectedPreset: ProviderPreset?,
    remoteModelsCount: Int,
    isTesting: Boolean,
    onOpenPicker: () -> Unit,
    onSyncRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentModelDisplay = model.ifBlank { defaultModelFor(selectedProvider) }
    val isRemoteSynced = remoteModelsCount > 0
    val subtitle =
        if (isRemoteSynced) {
            stringResource(R.string.feature_assistant_model_subtitle_synced, remoteModelsCount)
        } else if (selectedPreset != null) {
            stringResource(
                R.string.feature_assistant_model_subtitle_preset,
                stringResource(selectedPreset.nameRes),
            )
        } else {
            stringResource(R.string.feature_assistant_model_subtitle_hint)
        }

    Surface(
        onClick = onOpenPicker,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BgmIcons.AssistantBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feature_assistant_model_field_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentModelDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isModelKnownUnsupportedToolCall(currentModelDisplay)) {
                    Text(
                        text = stringResource(R.string.feature_assistant_model_no_tool_call),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRemoteSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 原地同步远端模型按钮
                IconButton(
                    onClick = onSyncRemote,
                    enabled = !isTesting,
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = BgmIcons.Refresh,
                            contentDescription = stringResource(R.string.feature_assistant_model_cd_sync),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // 展开指示图标
                Icon(
                    imageVector = BgmIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 完整模型选择面板弹窗：支持搜索、属性特征标签识别、远端/预设分组与直接使用输入词
 */
@Composable
internal fun ModelPickerDialog(
    currentModel: String,
    remoteModels: List<String>,
    presetModels: List<String>,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCapabilityFilter by remember { mutableStateOf<ModelCapability?>(null) }
    val allUniqueModels =
        remember(remoteModels, presetModels) {
            (remoteModels + presetModels).distinct()
        }

    val capabilityFilteredModels =
        remember(allUniqueModels, selectedCapabilityFilter) {
            if (selectedCapabilityFilter == null) {
                allUniqueModels
            } else {
                allUniqueModels.filter { detectModelCapabilities(it).contains(selectedCapabilityFilter) }
            }
        }

    val trimmedQuery = searchQuery.trim()
    val isSearching = trimmedQuery.isNotBlank()

    // 过滤候选列表
    val filteredModels =
        remember(capabilityFilteredModels, trimmedQuery) {
            if (trimmedQuery.isBlank()) {
                capabilityFilteredModels
            } else {
                capabilityFilteredModels.filter { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }

    val hasExactMatch =
        remember(allUniqueModels, trimmedQuery) {
            allUniqueModels.any { it.equals(trimmedQuery, ignoreCase = true) }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.feature_assistant_model_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        if (remoteModels.isNotEmpty()) {
                            stringResource(
                                R.string.feature_assistant_model_picker_subtitle_remote,
                                remoteModels.size,
                            )
                        } else {
                            stringResource(R.string.feature_assistant_model_picker_subtitle_preset)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.feature_assistant_model_picker_search_placeholder)) },
                    leadingIcon = {
                        Icon(BgmIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    BgmIcons.Clear,
                                    contentDescription = stringResource(R.string.feature_assistant_model_picker_cd_clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedCapabilityFilter == null,
                        onClick = { selectedCapabilityFilter = null },
                        label = {
                            Text(
                                stringResource(R.string.feature_assistant_model_filter_all),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    ModelCapability.entries.forEach { cap ->
                        val isSelected = selectedCapabilityFilter == cap
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCapabilityFilter = if (isSelected) null else cap
                            },
                            label = {
                                Text(stringResource(cap.labelRes), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 如果搜索词无精确匹配，提供直接使用当前搜索词作为自定义模型的通道
                if (isSearching && !hasExactMatch) {
                    Surface(
                        onClick = {
                            onSelectModel(trimmedQuery)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = BgmIcons.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.feature_assistant_model_use_query,
                                            trimmedQuery,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.feature_assistant_model_use_query_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (filteredModels.isEmpty() && (!isSearching || hasExactMatch)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.feature_assistant_model_picker_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        // 1. 如果无搜索且无能力筛选，且有远端模型：展示分组
                        if (!isSearching && selectedCapabilityFilter == null && remoteModels.isNotEmpty()) {
                            item {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.feature_assistant_model_picker_group_remote,
                                            remoteModels.size,
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                            items(remoteModels) { item ->
                                ModelPickerItem(
                                    name = item,
                                    isSelected = item == currentModel,
                                    onSelect = {
                                        onSelectModel(item)
                                        onDismiss()
                                    },
                                )
                            }
                            val presetOnly = presetModels.filterNot { it in remoteModels }
                            if (presetOnly.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.feature_assistant_model_picker_group_preset),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                items(presetOnly) { item ->
                                    ModelPickerItem(
                                        name = item,
                                        isSelected = item == currentModel,
                                        onSelect = {
                                            onSelectModel(item)
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        } else {
                            // 搜索结果列表或筛选结果
                            items(filteredModels) { item ->
                                ModelPickerItem(
                                    name = item,
                                    isSelected = item == currentModel,
                                    onSelect = {
                                        onSelectModel(item)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_close))
            }
        },
    )
}

@Composable
internal fun ModelPickerItem(
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val capabilities = remember(name) { detectModelCapabilities(name) }
    val isUnsupportedTool = remember(name) { isModelKnownUnsupportedToolCall(name) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (capabilities.isNotEmpty() || isUnsupportedTool) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isUnsupportedTool) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = stringResource(R.string.feature_assistant_model_unsupported_tool),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        capabilities.forEach { cap ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color =
                                    when (cap) {
                                        ModelCapability.REASONING -> MaterialTheme.colorScheme.tertiaryContainer
                                        ModelCapability.VISION -> MaterialTheme.colorScheme.secondaryContainer
                                        ModelCapability.LIGHTWEIGHT -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                            ) {
                                Text(
                                    text = stringResource(cap.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        when (cap) {
                                            ModelCapability.REASONING -> MaterialTheme.colorScheme.onTertiaryContainer
                                            ModelCapability.VISION -> MaterialTheme.colorScheme.onSecondaryContainer
                                            ModelCapability.LIGHTWEIGHT -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = BgmIcons.Check,
                    contentDescription = stringResource(R.string.feature_assistant_model_cd_current),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }
}
