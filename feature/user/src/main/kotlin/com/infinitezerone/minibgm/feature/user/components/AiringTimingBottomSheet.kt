package com.infinitezerone.minibgm.feature.user.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.rememberBgmBottomSheetState
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.feature.user.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.infinitezerone.minibgm.core.designsystem.R as DesignSystemR

/**
 * 快捷预设定义
 */
private data class TimingPreset(
    @StringRes val labelRes: Int,
    val offsetMinutes: Int,
)

private val TIMING_PRESETS =
    listOf(
        TimingPreset(R.string.feature_user_timing_preset_advance_30, -30),
        TimingPreset(R.string.feature_user_timing_preset_advance_15, -15),
        TimingPreset(R.string.feature_user_timing_preset_advance_5, -5),
        TimingPreset(R.string.feature_user_timing_preset_on_time, 0),
        TimingPreset(R.string.feature_user_timing_preset_delay_15, 15),
        TimingPreset(R.string.feature_user_timing_preset_delay_30, 30),
        TimingPreset(R.string.feature_user_timing_preset_delay_60, 60),
    )

/**
 * 相机式弧形刻度盘 + 底部抽屉的开播提醒时间设置组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiringTimingBottomSheet(
    initialOffsetMinutes: Int,
    onConfirmOffset: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    minOffsetMinutes: Int = -60,
    maxOffsetMinutes: Int = 120,
) {
    val sheetState = rememberBgmBottomSheetState(skipPartiallyExpanded = true)
    var selectedOffset by remember(initialOffsetMinutes) {
        mutableIntStateOf(initialOffsetMinutes.coerceIn(minOffsetMinutes, maxOffsetMinutes))
    }
    var showDirectInputDialog by remember { mutableStateOf(false) }

    BgmModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 标题与说明
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.feature_user_timing_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.feature_user_timing_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = BgmIcons.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 2. 焦点卡片（Hero Card）—— 展示当前换算白话文与示例
            TimingHeroCard(
                offsetMinutes = selectedOffset,
                onEditClick = { showDirectInputDialog = true },
            )

            // 3. 常用快捷预设胶囊
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(TIMING_PRESETS, key = { it.offsetMinutes }) { preset ->
                    val isSelected = selectedOffset == preset.offsetMinutes
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedOffset = preset.offsetMinutes },
                        label = {
                            Text(
                                text = stringResource(preset.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }

            // 4. 相机弧形刻度圆盘（Camera Arc Dial）
            CameraArcDial(
                offsetMinutes = selectedOffset,
                onOffsetChange = { selectedOffset = it },
                minOffset = minOffsetMinutes,
                maxOffset = maxOffsetMinutes,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
            )

            // 5. 1 分钟精细步进器与自定义辅助
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        if (selectedOffset > minOffsetMinutes) {
                            selectedOffset--
                        }
                    },
                    enabled = selectedOffset > minOffsetMinutes,
                ) {
                    Icon(
                        imageVector = BgmIcons.Remove,
                        contentDescription = stringResource(R.string.feature_user_timing_cd_minus),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = stringResource(R.string.feature_user_timing_fine_tune),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IconButton(
                    onClick = {
                        if (selectedOffset < maxOffsetMinutes) {
                            selectedOffset++
                        }
                    },
                    enabled = selectedOffset < maxOffsetMinutes,
                ) {
                    Icon(
                        imageVector = BgmIcons.Add,
                        contentDescription = stringResource(R.string.feature_user_timing_cd_plus),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 6. 确认应用按钮
            Button(
                onClick = {
                    onConfirmOffset(selectedOffset)
                    onDismiss()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_user_timing_confirm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (showDirectInputDialog) {
        DirectNumberInputDialog(
            initialOffset = selectedOffset,
            minOffset = minOffsetMinutes,
            maxOffset = maxOffsetMinutes,
            onConfirm = {
                selectedOffset = it
                showDirectInputDialog = false
            },
            onDismiss = { showDirectInputDialog = false },
        )
    }
}

/**
 * 焦点卡片：展示当前选择的语义文本与真实时间换算示例
 */
@Composable
private fun TimingHeroCard(
    offsetMinutes: Int,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleText =
        when {
            offsetMinutes < 0 -> stringResource(R.string.feature_user_timing_advance_minutes, -offsetMinutes)
            offsetMinutes == 0 -> stringResource(R.string.feature_user_timing_on_time)
            else -> stringResource(R.string.feature_user_timing_delay_minutes, offsetMinutes)
        }

    val examplePushTime =
        run {
            val baseHour = 23
            val baseMinute = 0
            val totalMinutes = (baseHour * 60 + baseMinute + offsetMinutes).coerceIn(0, 24 * 60 - 1)
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            "%02d:%02d".format(h, m)
        }

    val subtitleText =
        when {
            offsetMinutes == 0 -> stringResource(R.string.feature_user_timing_hero_immediate)
            offsetMinutes == 15 ->
                stringResource(R.string.feature_user_timing_hero_example_delay15, examplePushTime)
            else -> stringResource(R.string.feature_user_timing_hero_example, examplePushTime)
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = titleText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "hero_title",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = BgmIcons.Edit,
                    contentDescription = stringResource(R.string.feature_user_timing_cd_manual_input),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 相机式弧形刻度盘（Camera Arc Dial）
 *
 * 核心数学与手势：
 * - 拱形朝上，以圆心在下方的弧线排列刻度线；
 * - 中心上方设有金色游标指示器；
 * - 水平拖拽换算为刻度旋转，带 1 分钟精度与 5 分钟触感阻尼反馈。
 */
@Composable
private fun CameraArcDial(
    offsetMinutes: Int,
    onOffsetChange: (Int) -> Unit,
    minOffset: Int,
    maxOffset: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    var offsetFloat by remember { mutableFloatStateOf(offsetMinutes.toFloat()) }
    LaunchedEffect(offsetMinutes) {
        if (offsetFloat.roundToInt() != offsetMinutes) {
            offsetFloat = offsetMinutes.toFloat()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier =
            modifier
                .pointerInput(minOffset, maxOffset) {
                    // 提升手势传动比（放大拖拽位移）：约 3.6.dp 对应 1 分钟，手指小幅拨动即可轻松带动圆盘大范围旋转
                    val pxPerMinute = with(density) { 3.6.dp.toPx() }
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val rounded = offsetFloat.roundToInt()
                            val nearest5 = (offsetFloat / 5f).roundToInt() * 5
                            // 磁吸效应：若释放时靠近 5 的倍数（0.85m 容忍度内），平滑吸附到整 5 分钟
                            val snapped =
                                if (kotlin.math.abs(offsetFloat - nearest5) <= 0.85f) {
                                    nearest5
                                } else {
                                    rounded
                                }.coerceIn(minOffset, maxOffset)
                            offsetFloat = snapped.toFloat()
                            onOffsetChange(snapped)
                        },
                        onDragCancel = {
                            val target = offsetFloat.roundToInt().coerceIn(minOffset, maxOffset)
                            offsetFloat = target.toFloat()
                            onOffsetChange(target)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMinutes = -dragAmount / pxPerMinute
                            val prevInt = offsetFloat.roundToInt()
                            val nextFloat = (offsetFloat + deltaMinutes).coerceIn(minOffset.toFloat(), maxOffset.toFloat())
                            offsetFloat = nextFloat
                            val nextInt = nextFloat.roundToInt()
                            if (nextInt != prevInt) {
                                onOffsetChange(nextInt)
                                if (nextInt % 15 == 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            // 几何定义：圆心在画布下方，弧顶在 y = 24.dp 处
            val arcTopY = 24.dp.toPx()
            val radius = width * 0.95f
            val centerX = width / 2f
            val centerY = arcTopY + radius

            // 弧长刻度密度：每 1 分钟对应的旋转弧度
            val radPerMinute = (1.5f * PI / 180f).toFloat()

            // 1. 绘制弧形轨道底线
            val visibleSpanMinutes = 35
            val minVisibleMinute = (offsetFloat - visibleSpanMinutes).toInt().coerceAtLeast(minOffset)
            val maxVisibleMinute = (offsetFloat + visibleSpanMinutes).toInt().coerceAtMost(maxOffset)

            val startAngleRad = (minVisibleMinute - offsetFloat) * radPerMinute
            val endAngleRad = (maxVisibleMinute - offsetFloat) * radPerMinute

            val trackStartAngleDeg = ((-PI / 2 + startAngleRad) * 180f / PI).toFloat()
            val trackSweepAngleDeg = ((endAngleRad - startAngleRad) * 180f / PI).toFloat()

            drawArc(
                color = trackColor,
                startAngle = trackStartAngleDeg,
                sweepAngle = trackSweepAngleDeg,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size =
                    androidx.compose.ui.geometry
                        .Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // 2. 绘制每个分钟的刻度线与数字
            val majorTickLen = 14.dp.toPx()
            val minorTickLen = 7.dp.toPx()

            for (m in minVisibleMinute..maxVisibleMinute) {
                val delta = m - offsetFloat
                val phi = delta * radPerMinute

                val baseX = centerX + radius * sin(phi)
                val baseY = centerY - radius * cos(phi)

                val isMajor5 = (m % 5 == 0)
                val isMajor15 = (m % 15 == 0 || m == 0)
                val tickLen = if (isMajor5) majorTickLen else minorTickLen
                val tickColor =
                    when {
                        m == 0 -> primaryColor
                        isMajor5 -> onSurfaceColor.copy(alpha = 0.85f)
                        else -> onSurfaceVariantColor.copy(alpha = 0.35f)
                    }
                val strokeWidth = if (isMajor5) 2.dp.toPx() else 1.dp.toPx()

                // 沿法线向内绘制刻度线
                val innerX = centerX + (radius - tickLen) * sin(phi)
                val innerY = centerY - (radius - tickLen) * cos(phi)

                drawLine(
                    color = tickColor,
                    start = Offset(baseX, baseY),
                    end = Offset(innerX, innerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )

                // 针对 15 的倍数及 0 绘制文字标尺
                if (isMajor15) {
                    val labelText =
                        when {
                            m > 0 -> "+$m"
                            else -> "$m"
                        }
                    val textLayout =
                        textMeasurer.measure(
                            text = labelText,
                            style =
                                TextStyle(
                                    fontSize = 10.sp,
                                    color = if (m == 0) primaryColor else onSurfaceVariantColor,
                                    fontWeight = if (m == 0) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                ),
                        )

                    val textRadius = radius - majorTickLen - 12.dp.toPx()
                    val textX = centerX + textRadius * sin(phi) - textLayout.size.width / 2f
                    val textY = centerY - textRadius * cos(phi) - textLayout.size.height / 2f

                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(textX, textY),
                    )
                }
            }

            // 3. 绘制中心固定金色游标（指向弧顶）
            val pointerPath =
                Path().apply {
                    moveTo(centerX - 6.dp.toPx(), arcTopY - 14.dp.toPx())
                    lineTo(centerX + 6.dp.toPx(), arcTopY - 14.dp.toPx())
                    lineTo(centerX, arcTopY - 3.dp.toPx())
                    close()
                }
            drawPath(path = pointerPath, color = primaryColor)
        }
    }
}

/**
 * 辅助弹窗：允许极客用户直接键盘键入精确分钟数与方向
 */
@Composable
private fun DirectNumberInputDialog(
    initialOffset: Int,
    minOffset: Int,
    maxOffset: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var isEarly by remember { mutableStateOf(initialOffset <= 0) }
    var inputString by remember {
        mutableStateOf(if (initialOffset == 0) "0" else kotlin.math.abs(initialOffset).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_user_timing_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.feature_user_timing_custom_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = isEarly,
                        onClick = { isEarly = true },
                        label = { Text(stringResource(R.string.feature_user_timing_direction_advance)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = !isEarly,
                        onClick = { isEarly = false },
                        label = { Text(stringResource(R.string.feature_user_timing_direction_delay)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = inputString,
                    onValueChange = { str ->
                        if (str.all { it.isDigit() } && str.length <= 4) {
                            inputString = str
                        }
                    },
                    label = { Text(stringResource(R.string.feature_user_timing_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawMinutes = inputString.toIntOrNull() ?: 0
                    val finalOffset =
                        if (isEarly) {
                            (-rawMinutes).coerceIn(minOffset, maxOffset)
                        } else {
                            rawMinutes.coerceIn(minOffset, maxOffset)
                        }
                    onConfirm(finalOffset)
                },
            ) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignSystemR.string.core_designsystem_action_cancel))
            }
        },
    )
}
