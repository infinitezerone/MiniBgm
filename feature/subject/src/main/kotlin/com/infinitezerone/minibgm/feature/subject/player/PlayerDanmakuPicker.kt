package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.component.BgmModalBottomSheet
import com.infinitezerone.minibgm.core.designsystem.component.OverlayProviderScope
import com.infinitezerone.minibgm.core.designsystem.component.OverlayRequest
import com.infinitezerone.minibgm.core.designsystem.icon.BgmIcons
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuMatchState
import com.infinitezerone.minibgm.feature.subject.R

/**
 * 弹幕分集候选挑选请求：把 dandanplay 搜索返回的全部候选交给用户确认，
 * 返回被选中的候选（取消 / 点击遮罩返回 null 表示放弃纠正）。
 */
data class DanmakuCandidateRequest(
    val candidates: List<DanmakuEpisodeCandidate>,
    val current: DanmakuEpisodeCandidate?,
    val currentState: DanmakuMatchState?,
) : OverlayRequest<DanmakuEpisodeCandidate?>

/**
 * 弹幕分集候选挑选浮层（BgmOverlayHost 注册渲染器）。
 *
 * 弹幕匹配本质是标题字符串猜测，同名番 / 剧场版 / 季度混排永远存在残余错误率；
 * 与其追求自动匹配 100% 准确，不如把残余错误收敛为一次用户确认——
 * 确认结果经 [PlayerViewModel.selectDanmakuCandidate] 落库为覆盖，后续取数直接命中。
 */
@OptIn(ExperimentalMaterial3Api::class)
fun OverlayProviderScope.danmakuCandidatePicker() {
    overlay<DanmakuCandidateRequest, DanmakuEpisodeCandidate?> { request, onRespond ->
        BgmModalBottomSheet(
            onDismissRequest = { onRespond(null) },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_subject_player_danmaku_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.feature_subject_player_danmaku_pick_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (request.currentState == DanmakuMatchState.FALLBACK) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(text = stringResource(R.string.feature_subject_player_danmaku_pick_state_fallback))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(request.candidates, key = { it.episodeId }) { candidate ->
                    val selected = candidate.episodeId == request.current?.episodeId
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onRespond(candidate) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onRespond(candidate) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.animeTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = candidate.episodeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 弹幕分集挑选入口按钮（弹幕开关旁的控制栏图标）：
 * 仅在有候选可选时展示，匹配处于回退兜底态时挂警示徽标提醒用户纠正。
 */
@Composable
internal fun DanmakuMatchEntryButton(
    hasCandidates: Boolean,
    isFallback: Boolean,
    onClick: () -> Unit,
) {
    if (!hasCandidates) return
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        BadgedBox(
            badge = {
                if (isFallback) {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Icon(
                imageVector = BgmIcons.FilterList,
                contentDescription = stringResource(R.string.feature_subject_player_danmaku_pick_entry),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
