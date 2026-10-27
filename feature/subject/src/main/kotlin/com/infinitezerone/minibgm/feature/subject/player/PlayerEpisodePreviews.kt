package com.infinitezerone.minibgm.feature.subject.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitezerone.minibgm.core.designsystem.theme.MiniBgmTheme
import com.infinitezerone.minibgm.core.designsystem.theme.ThemePreviews

@ThemePreviews
@Composable
fun PlayerEpisodesPreview() {
    val sampleEpisodes =
        listOf(
            PlayerEpisodeItem(id = 1, sort = 1f, nameCn = "第 1 话 开始的旅途", isWatched = true, airdate = "2024-10-01"),
            PlayerEpisodeItem(id = 2, sort = 2f, nameCn = "第 2 话 魔法使的徒弟", isWatched = true, airdate = "2024-10-08"),
            PlayerEpisodeItem(id = 3, sort = 3f, nameCn = "第 3 话 杀人的魔法", isWatched = true, airdate = "2024-10-15"),
            PlayerEpisodeItem(id = 4, sort = 4f, nameCn = "第 4 话 魂睡之地", isWatched = false, airdate = "2024-10-22"),
            PlayerEpisodeItem(id = 5, sort = 5f, nameCn = "第 5 话 幻影之鬼", isWatched = false, airdate = "2099-11-05"),
            PlayerEpisodeItem(id = 6, sort = 6f, nameCn = "第 6 话 村庄的英雄", isWatched = false, airdate = "2099-11-12"),
        )

    MiniBgmTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "横向选集栏（第4话播放中，第5-6话待播置灰）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            BiliEpisodesSection(
                episodes = sampleEpisodes,
                selectedEpisodeSort = 4f,
                selectedEpisodeId = 4L,
                onSelectEpisode = {},
                totalEpisodes = 12,
            )

            Text(
                text = "选集网格模式（已播 / 播放中 / 待播带日期）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleEpisodes.take(6).chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { ep ->
                            Box(modifier = Modifier.weight(1f)) {
                                EpisodeGridCard(
                                    episode = ep,
                                    isSelected = ep.id == 4L,
                                    onClick = {},
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "选集列表模式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleEpisodes.take(4).forEach { ep ->
                    EpisodeListCard(
                        episode = ep,
                        isSelected = ep.id == 4L,
                        onClick = {},
                    )
                }
                EpisodeListCard(
                    episode = sampleEpisodes[4],
                    isSelected = false,
                    onClick = {},
                )
            }
        }
    }
}
