package com.infinitezerone.minibgm.feature.subject.player

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.DanmakuRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.data.repository.UserSettings
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuResult
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackHistory
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakePlaybackHistoryRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val subjectId = 1001L
    private val episodeId = 2002L
    private val initialStreamUrl = "https://example.com/stream.m3u8"

    private fun queueOf(vararg urls: String): List<PlayerQueueEntry> =
        urls.mapIndexed { index, url ->
            PlayerQueueEntry(
                streamUrl = url,
                label = "${index + 1}",
                episodeSort = (index + 1).toFloat(),
            )
        }

    private fun route(
        streamUrl: String = initialStreamUrl,
        queue: List<PlayerQueueEntry> = emptyList(),
        startIndex: Int = 0,
        initialRuleId: String = "",
        initialSourceName: String = "",
        initialSourceId: String = "",
        coverUrl: String = "",
        episodeId: Long = this.episodeId,
    ) = PlayerRoute(
        subjectId = subjectId,
        episodeId = episodeId,
        streamUrl = streamUrl,
        queue = queue,
        startIndex = startIndex,
        initialRuleId = initialRuleId,
        initialSourceName = initialSourceName,
        initialSourceId = initialSourceId,
        coverUrl = coverUrl,
    )

    private fun viewModel(
        route: PlayerRoute = route(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
        failureStore: PlaybackFailureStore? = null,
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        subjectRepository: SubjectRepository? = null,
        playbackResolverRepository: PlaybackResolverRepository? = null,
        communityRepository: FakeCommunityRepository? = null,
        playbackHistoryRepository: FakePlaybackHistoryRepository? = null,
    ): PlayerViewModel =
        PlayerViewModel(
            route = route,
            collectionRepository = FakeCollectionRepository(),
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            failureStore = failureStore,
            subjectRepository = subjectRepository,
            playbackResolverRepository = playbackResolverRepository,
            communityRepository = communityRepository,
            playbackHistoryRepository = playbackHistoryRepository,
        )

    /**
     * 立即返回固定直链的取源替身；不传 url 时恒为空结果（模拟"试完所有关键词都没命中"）。
     *
     * 选源后能否拿到地址决定了「在播源」归属：解析成功才把 playingSourceId 换成新源。
     */
    private fun resolverReturning(vararg urls: String): PlaybackResolverRepository =
        object : PlaybackResolverRepository {
            override suspend fun resolvePages(
                pageUrls: List<String>,
                epNumber: Float,
                siteName: String,
                title: String,
            ): List<PlayableSource> = emptyList()

            override suspend fun resolveTemplate(
                url: String,
                headers: Map<String, String>,
                epNumber: Float,
                siteName: String,
                title: String,
            ): List<PlayableSource> = emptyList()

            override suspend fun resolveRule(
                rule: PlaybackSourceRule,
                title: String,
                epNumber: Float,
                subjectId: Long,
                episodeId: Long,
            ): List<PlayableSource> = urls.filter { it.isNotBlank() }.map { PlayableSource(url = it, kind = PlaylistEntryKind.DIRECT) }
        }

    private fun rulesOf(vararg ids: String): List<PlaybackSourceRule> =
        ids.map { id ->
            PlaybackSourceRule(
                id = id,
                name = id.uppercase(),
                urlTemplate = "https://$id/?t={title}",
                kind = PlaybackRuleKind.SOURCE,
                isEnabled = true,
            )
        }

    @Test
    fun onQualitySelected_persistsPreference() =
        runTest {
            val settingsRepo = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settingsRepo)

            viewModel.onQualitySelected(1080)
            advanceUntilIdle()
            assertEquals(1080, settingsRepo.settings.first().preferredQualityHeight)

            viewModel.onQualitySelected(0)
            advanceUntilIdle()
            assertEquals(0, settingsRepo.settings.first().preferredQualityHeight)
        }

    @Test
    fun initialState_matchesProvidedArguments() {
        val state = viewModel().uiState.value
        assertEquals(subjectId, state.subjectId)
        assertEquals(episodeId, state.episodeId)
        assertEquals(initialStreamUrl, state.streamUrl)
        assertFalse(state.isWatched)
        assertFalse(state.autoMarked)
        // 单集启动：空队列退化为长度 1 的队列，无连播
        assertEquals(1, state.queue.size)
        assertFalse(state.hasNext)
        assertTrue(state.pipEnabled)
    }

    @Test
    fun pipEnabled_updatesWhenSettingsChange() =
        runTest {
            val settingsRepo = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settingsRepo)
            runCurrent()

            assertTrue(viewModel.uiState.value.pipEnabled)

            settingsRepo.setPipEnabled(false)
            runCurrent()

            assertFalse(viewModel.uiState.value.pipEnabled)
        }

    @Test
    fun authenticated_markWatched_updatesStateAndEmitsEvent() =
        runTest {
            val viewModel = viewModel()

            viewModel.markWatched(epNumber = 5)

            val state = viewModel.uiState.value
            assertTrue(state.isWatched)
            assertTrue(state.autoMarked)

            val firstEvent = viewModel.events.first()
            assertTrue(firstEvent is PlayerUiEvent.MarkedWatched)
            assertEquals(5, (firstEvent as PlayerUiEvent.MarkedWatched).epNumber)
        }

    @Test
    fun unauthenticated_markWatched_doesNotTriggerWatchUpdate() =
        runTest {
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialLoggedIn = false))

            viewModel.markWatched(epNumber = 5)

            val state = viewModel.uiState.value
            assertFalse(state.isWatched)
            assertFalse(state.autoMarked)
        }

    @Test
    fun markWatched_isIdempotent() =
        runTest {
            val viewModel = viewModel()

            viewModel.markWatched(epNumber = 1)
            viewModel.markWatched(epNumber = 2)

            val state = viewModel.uiState.value
            assertTrue(state.isWatched)
            assertTrue(state.autoMarked)
        }

    @Test
    fun onPlaybackError_setsErrorMessage() {
        val viewModel = viewModel()

        viewModel.onPlaybackError("Network timeout")
        assertEquals("Network timeout", viewModel.uiState.value.error)
    }

    @Test
    fun retry_clearsError() {
        val viewModel = viewModel()

        viewModel.onPlaybackError("网络不可达")
        viewModel.retry()

        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun playbackFailure_and_recovery_areAttributedToCurrentStreamUrl() {
        val failureStore = PlaybackFailureStore()
        val viewModel = viewModel(failureStore = failureStore)

        viewModel.onPlaybackError("被来源拒绝访问")
        assertEquals("被来源拒绝访问", failureStore.recentFailures.value[initialStreamUrl])

        viewModel.onPlaybackReady()
        assertFalse(failureStore.recentFailures.value.containsKey(initialStreamUrl))
    }

    @Test
    fun playbackFailure_isCountedAgainstTheSelectedSource() =
        runTest {
            val failureStore = PlaybackFailureStore()
            val viewModel = viewModel(failureStore = failureStore)

            viewModel.onPlaybackError("网络不可达")
            viewModel.onPlaybackError("网络不可达")

            // 初始只有一个源（默认直链），失败按源累计，选源界面据此弱化它
            val counts = viewModel.uiState.first { it.sourceFailureCounts.isNotEmpty() }.sourceFailureCounts
            assertEquals(2, counts["direct"])
            assertEquals(2, failureStore.sourceHealth.value["direct"]?.consecutiveFailures)

            // 一旦播成功就归零：否则一个已经修好的源会一直背着"打不开"的标签
            viewModel.onPlaybackReady()
            assertFalse(
                viewModel.uiState
                    .first { it.sourceFailureCounts.isEmpty() }
                    .sourceFailureCounts
                    .containsKey("direct"),
            )
        }

    @Test
    fun queuePlayback_onPlaybackEnded_advancesToNextEpisodeAndResetsWatchState() {
        val viewModel =
            viewModel(
                route =
                    route(
                        streamUrl = initialStreamUrl,
                        queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"),
                    ),
            )

        // 当前集先出现失败归因，切集后归因对象应跟着换
        viewModel.onPlaybackError("网络不可达")
        val advanced = viewModel.onPlaybackEnded()

        assertTrue(advanced)
        val state = viewModel.uiState.value
        assertEquals("https://example.com/ep2.m3u8", state.streamUrl)
        assertEquals(1, state.currentIndex)
        assertFalse(state.hasNext)
        assertTrue(state.hasPrevious)
        assertFalse(state.isWatched)
        assertFalse(state.error != null)
        // 下一集的 85% 阈值打卡应该可以重新触发
        viewModel.onWatchThresholdReached()
        assertTrue(viewModel.uiState.value.isWatched)
    }

    @Test
    fun queuePlayback_lastEpisodeEnded_staysAndShowsReplay() {
        val viewModel =
            viewModel(
                route = route(queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"), startIndex = 1),
            )

        val advanced = viewModel.onPlaybackEnded()

        assertFalse(advanced)
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun queuePlayback_autoNextDisabled_doesNotAdvance() {
        val viewModel =
            viewModel(route = route(queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8")))

        viewModel.toggleAutoNext()
        assertFalse(viewModel.uiState.value.autoNextEnabled)

        val advanced = viewModel.onPlaybackEnded()

        assertFalse(advanced)
        assertEquals("https://example.com/ep1.m3u8", viewModel.uiState.value.streamUrl)
    }

    @Test
    fun onPlaybackEnded_doesNotAdvanceWhenStreamUrlIsBlank() {
        val viewModel = viewModel(route = route(streamUrl = ""))

        val advanced = viewModel.onPlaybackEnded()

        assertFalse(advanced)
        assertFalse(viewModel.uiState.value.isWatched)
    }

    @Test
    fun onPlaybackEnded_doesNotAdvanceWhenNextEpisodeIsDifferentType() =
        runTest {
            val episodes =
                listOf(
                    Episode(id = episodeId, sort = 1f, type = 0, name = "Ep 1", nameCn = ""),
                    Episode(id = 2003L, sort = 1f, type = 1, name = "SP 1", nameCn = ""),
                )
            val subjectRepo = FakeSubjectRepository()
            subjectRepo.sendEpisodes(subjectId, episodes)

            val viewModel =
                viewModel(
                    route = route(queue = queueOf("https://example.com/ep1.m3u8")),
                    subjectRepository = subjectRepo,
                )
            advanceUntilIdle()

            val advanced = viewModel.onPlaybackEnded()
            assertFalse(advanced)
        }

    @Test
    fun onPlaybackEnded_loadsMoreEpisodesWhenReachingEndAndHasMore() =
        runTest {
            val initialEpisodes =
                listOf(
                    Episode(id = episodeId, sort = 1f, type = 0, name = "Ep 1", nameCn = ""),
                )
            val moreEpisodes =
                listOf(
                    Episode(id = episodeId, sort = 1f, type = 0, name = "Ep 1", nameCn = ""),
                    Episode(id = 2003L, sort = 2f, type = 0, name = "Ep 2", nameCn = ""),
                )
            val subjectRepo = FakeSubjectRepository()
            subjectRepo.sendEpisodes(subjectId, initialEpisodes)
            subjectRepo.setHasMoreEpisodes(subjectId, true)
            subjectRepo.loadMoreEpisodesResult = { _, _ ->
                subjectRepo.sendEpisodes(subjectId, moreEpisodes)
                subjectRepo.setHasMoreEpisodes(subjectId, false)
                AppResult.Success(false)
            }

            val viewModel =
                viewModel(
                    route = route(queue = queueOf("https://example.com/ep1.m3u8")),
                    subjectRepository = subjectRepo,
                )
            advanceUntilIdle()

            val advanced = viewModel.onPlaybackEnded()
            assertTrue(advanced)
            advanceUntilIdle()

            assertEquals(2f, viewModel.uiState.value.episodeSort)
        }

    @Test
    fun queuePlayback_switchTo_jumpsToSelectedEpisode() {
        val viewModel =
            viewModel(
                route =
                    route(
                        queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8", "https://example.com/ep3.m3u8"),
                    ),
            )

        viewModel.switchTo(2)

        val state = viewModel.uiState.value
        assertEquals(2, state.currentIndex)
        assertEquals("https://example.com/ep3.m3u8", state.streamUrl)
        assertEquals(3f, state.episodeSort)
    }

    @Test
    fun queuePlayback_playNextEpisode_switchesToNextQueueItem() {
        val viewModel =
            viewModel(
                route =
                    route(
                        queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"),
                    ),
            )
        assertTrue(viewModel.uiState.value.hasNext)
        val switched = viewModel.playNextEpisode()
        assertTrue(switched)
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("https://example.com/ep2.m3u8", viewModel.uiState.value.streamUrl)
        assertFalse(viewModel.uiState.value.hasNext)
        assertFalse(viewModel.playNextEpisode())
    }

    @Test
    fun queuePlayback_switchToOutOfRange_isIgnored() {
        val viewModel = viewModel(route = route(queue = queueOf("https://example.com/ep1.m3u8")))

        viewModel.switchTo(5)

        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun resumePosition_exposedFromHistoryForCurrentEpisode() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(historyEntry(positionMs = 62_000L, durationMs = 240_000L))

            val viewModel = viewModel(playbackHistoryRepository = historyRepo)
            advanceUntilIdle()

            assertEquals(62_000L, viewModel.uiState.value.resumePositionMs)
        }

    @Test
    fun switchTo_flushesPreviousEpisodePositionBeforeLeaving() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            val viewModel =
                viewModel(
                    route =
                        route(
                            episodeId = 101L,
                            queue =
                                listOf(
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep1.m3u8", episodeId = 101L, episodeSort = 1f),
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep2.m3u8", episodeId = 102L, episodeSort = 2f),
                                ),
                        ),
                    playbackHistoryRepository = historyRepo,
                )

            viewModel.onProgressChanged(30_000L)
            viewModel.switchTo(1)
            advanceUntilIdle()

            assertEquals(30_000L, historyRepo.getHistoryByEpisode(101L)?.positionMs)
            // 新分集没有已存位置，不恢复
            assertEquals(0L, viewModel.uiState.value.resumePositionMs)
        }

    @Test
    fun flushPlaybackPosition_writesHistoryWithEngineMeasuredDuration() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            val viewModel = viewModel(playbackHistoryRepository = historyRepo)

            // 时长只能来自引擎实测；条目元数据里的字符串时长不能用来算进度
            viewModel.onDurationChanged(240_000L)
            viewModel.onProgressChanged(30_000L)
            viewModel.flushPlaybackPosition()
            advanceUntilIdle()

            val entry = requireNotNull(historyRepo.getHistoryByEpisode(episodeId))
            assertEquals(episodeId, entry.episodeId)
            assertEquals(subjectId, entry.subjectId)
            assertEquals(initialStreamUrl, entry.streamUrl)
            // 伪源也要落库：历史面板据此区分「直链回放」与「规则重新解析」
            assertEquals("direct", entry.sourceId)
            assertEquals("", entry.sourceName)
            assertEquals(30_000L, entry.positionMs)
            assertEquals(240_000L, entry.durationMs)
            assertEquals(0.125f, entry.progress)
            assertEquals(false, entry.isCompleted)
        }

    @Test
    fun flushPlaybackPosition_recordsRuleSourceIdentityForHistory() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val viewModel =
                viewModel(
                    route = route(streamUrl = ""),
                    settingsRepository = settings,
                    playbackHistoryRepository = historyRepo,
                    playbackResolverRepository = resolverReturning("https://example.com/r1.m3u8"),
                )
            advanceUntilIdle()

            viewModel.onDurationChanged(240_000L)
            viewModel.onProgressChanged(30_000L)
            viewModel.flushPlaybackPosition()
            advanceUntilIdle()

            val entry = requireNotNull(historyRepo.getHistoryByEpisode(episodeId))
            assertEquals("r1", entry.sourceId)
            assertEquals("R1", entry.sourceName)
        }

    /**
     * 从观看记录回放时播的是历史直链（playingSourceId = direct）：
     * 落断点必须保留原始来源，否则第二次从历史进入就退化成「默认直链」。
     */
    @Test
    fun flushPlaybackPosition_keepsOriginWhenReplayingHistoryDirectLink() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            val viewModel =
                viewModel(
                    route = route(initialSourceId = "r1", initialSourceName = "R1"),
                    playbackHistoryRepository = historyRepo,
                )

            viewModel.onDurationChanged(240_000L)
            viewModel.onProgressChanged(30_000L)
            viewModel.flushPlaybackPosition()
            advanceUntilIdle()

            val entry = requireNotNull(historyRepo.getHistoryByEpisode(episodeId))
            assertEquals("r1", entry.sourceId)
            assertEquals("R1", entry.sourceName)
        }

    @Test
    fun initialCoverUrl_setsSubjectCoverUrl() {
        val testCover = "https://lain.bgm.tv/pic/cover/l/test.jpg"
        val state = viewModel(route = route(coverUrl = testCover)).uiState.value
        assertEquals(testCover, state.subjectCoverUrl)
    }

    /**
     * 从历史记录回放单集直链后切下一集：
     * 直链队列只有该集时（directMissing），必须无缝平滑切至规则源并嗅探下一集，不得报死锁错误。
     */
    @Test
    fun selectEpisode_onDirectMissing_fallsBackToRuleSourceAndResolves() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val viewModel =
                viewModel(
                    route =
                        route(
                            episodeId = 2001L,
                            streamUrl = "https://example.com/ep1_direct.m3u8",
                            initialSourceId = "r1",
                            initialSourceName = "R1",
                        ),
                    settingsRepository = settings,
                    playbackResolverRepository = resolverReturning("https://example.com/ep2_resolved.m3u8"),
                )
            advanceUntilIdle()

            // 初始在直链源上
            assertTrue(
                viewModel.uiState.value.currentSource
                    ?.isDirect == true,
            )
            assertEquals("https://example.com/ep1_direct.m3u8", viewModel.uiState.value.streamUrl)

            // 切换到第 2 话（直链队列没有第 2 话）
            viewModel.selectEpisode(PlayerEpisodeItem(id = 2002L, sort = 2f, name = "第 2 话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull("平滑降级到规则源后不得报错", state.error)
            assertEquals("应成功通过规则源解析出第 2 话流地址", "https://example.com/ep2_resolved.m3u8", state.streamUrl)
            assertEquals("当前播放源应已切换为规则源 r1", "r1", state.playingSourceId)
        }

    @Test
    fun initialSourceName_labelsTheDirectTabWithoutChangingItsIdentity() {
        val state = viewModel(route = route(initialSourceName = "AGE 动漫")).uiState.value

        val direct = requireNotNull(state.sources.firstOrNull { it.isDirect })
        assertEquals("AGE 动漫", direct.name)
        assertEquals(null, direct.nameRes)
        assertEquals("direct", direct.id)
    }

    @Test
    fun unfinishedHistory_isRestoredAsResumePositionOnInit() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(historyEntry(positionMs = 62_000L, durationMs = 240_000L))

            val viewModel = viewModel(playbackHistoryRepository = historyRepo)
            advanceUntilIdle()

            assertEquals(62_000L, viewModel.uiState.value.resumePositionMs)
        }

    @Test
    fun completedHistory_doesNotRestoreResumePosition() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            // 进度 99%，按「已看完」处理：再进来应从头发车，而不是跳到片尾
            historyRepo.saveHistory(historyEntry(positionMs = 238_000L, durationMs = 240_000L))

            val viewModel = viewModel(playbackHistoryRepository = historyRepo)
            advanceUntilIdle()

            assertEquals(0L, viewModel.uiState.value.resumePositionMs)
        }

    private fun historyEntry(
        positionMs: Long,
        durationMs: Long,
    ) = PlaybackHistory(
        episodeId = episodeId,
        subjectId = subjectId,
        subjectName = "葬送的芙莉莲",
        episodeName = "第 1 话",
        episodeSort = 1f,
        coverUrl = "https://example.com/cover.jpg",
        streamUrl = initialStreamUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 1_000L,
    )

    @Test
    fun selectEpisode_doesNotCarryPreviousEpisodeResumePosition() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = 101L,
                    subjectId = subjectId,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "第一话",
                    positionMs = 300_000L,
                    durationMs =
                        24 * 60_000L,
                ),
            )
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = 102L,
                    subjectId = subjectId,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "第二话",
                    positionMs = 90_000L,
                    durationMs =
                        24 * 60_000L,
                ),
            )
            val viewModel =
                viewModel(
                    route =
                        route(
                            episodeId = 101L,
                            queue =
                                listOf(
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep1.m3u8", episodeId = 101L, episodeSort = 1f),
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep2.m3u8", episodeId = 102L, episodeSort = 2f),
                                ),
                        ),
                    playbackHistoryRepository = historyRepo,
                )
            advanceUntilIdle()
            assertEquals(300_000L, viewModel.uiState.value.resumePositionMs)

            viewModel.selectEpisode(PlayerEpisodeItem(id = 102L, sort = 2f, name = "第二话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://example.com/ep2.m3u8", state.streamUrl)
            assertEquals("换集必须下发新分集自己的续播点，不得沿用上一集进度", 90_000L, state.resumePositionMs)
        }

    @Test
    fun selectEpisode_onRuleSource_appliesNewEpisodeResumePosition() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            // 新分集此前看过一部分：换集后必须恢复到它自己的位置
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = 2002L,
                    subjectId = subjectId,
                    subjectName = "Test",
                    episodeName = "第二话",
                    episodeSort = 2f,
                    positionMs = 60_000L,
                    durationMs = 24 * 60_000L,
                ),
            )
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "TestRule",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/ep${epNumber.toInt()}.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val viewModel =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                    playbackHistoryRepository = historyRepo,
                )
            advanceUntilIdle()
            assertEquals("https://cdn.example.com/ep1.m3u8", viewModel.uiState.value.streamUrl)

            viewModel.selectEpisode(PlayerEpisodeItem(id = 2002L, sort = 2f, name = "第二话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://cdn.example.com/ep2.m3u8", state.streamUrl)
            assertEquals("规则源换集按分集自己的续播点恢复", 60_000L, state.resumePositionMs)
        }

    @Test
    fun selectEpisode_onRuleSource_unwatchedEpisode_resumePositionMsMustStayZero() =
        runTest {
            val settings = FakeSettingsRepository()
            // ep2 没有历史记录（从未看过）
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "TestRule",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/ep${epNumber.toInt()}.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val viewModel =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            // 正在播放第 1 集至 120 秒
            viewModel.onProgressChanged(120_000L)

            // 切换到第 2 集（从未看过）
            viewModel.selectEpisode(PlayerEpisodeItem(id = 0L, sort = 2f, name = "第二话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://cdn.example.com/ep2.m3u8", state.streamUrl)
            assertEquals("未看过的分集续播点必须为 0，严禁继承上一集的播放进度", 0L, state.resumePositionMs)
        }

    @Test
    fun playingFromBeginning_flushPlaybackPosition_doesNotMutateResumePositionMsMidPlayback() =
        runTest {
            val settings = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settings)
            advanceUntilIdle()
            assertEquals("初始未看过的视频续播点应为 0", 0L, viewModel.uiState.value.resumePositionMs)

            // 播放中途回报进度并落盘
            viewModel.onProgressChanged(10_000L)
            viewModel.flushPlaybackPosition()
            advanceUntilIdle()

            assertEquals(
                "播放中途周期性自动落盘严禁反向冲刷回入场续播点 resumePositionMs",
                0L,
                viewModel.uiState.value.resumePositionMs,
            )
        }

    @Test
    fun onPlaybackEnded_clearsSavedResumePosition() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = episodeId,
                    subjectId = subjectId,
                    subjectName = "测试动画",
                    episodeName = "第 1 话",
                    episodeSort = 1f,
                    coverUrl = "",
                    streamUrl = initialStreamUrl,
                    positionMs = 62_000L,
                    durationMs = 120_000L,
                    updatedAt = 0L,
                ),
            )
            val viewModel = viewModel(playbackHistoryRepository = historyRepo)
            advanceUntilIdle()

            viewModel.onPlaybackEnded()
            advanceUntilIdle()

            assertNull(historyRepo.getHistoryByEpisode(episodeId))
        }

    @Test
    fun periodicFlush_persistsLatestProgress() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            val viewModel = viewModel(playbackHistoryRepository = historyRepo)

            // 进度回调约 500ms 一跳，POSITION_SAVE_TICKS 跳后节流落盘
            repeat(POSITION_SAVE_TICKS) { viewModel.onProgressChanged(45_000L) }
            advanceUntilIdle()

            assertEquals(45_000L, historyRepo.getHistoryByEpisode(episodeId)?.positionMs)
        }

    @Test
    fun sources_populatedFromSettingsRules() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(
                        id = "rule1",
                        name = "示例站",
                        urlTemplate = "https://example.tv/?s={title}",
                        isEnabled = true,
                    ),
                ),
            )
            val vm = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            val sources = vm.uiState.value.sources
            assertEquals(2, sources.size)
            assertTrue(sources[0].isDirect)
            assertEquals("示例站", sources[1].name)
        }

    @Test
    fun selectSource_triggersStreamSniffing() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "TestRule",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/resolved_ep${epNumber.toInt()}.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                                label = "第 ${epNumber.toInt()} 话",
                                headers = mapOf("Referer" to "https://example.com"),
                                matchedVodName = "测试番剧 第一季",
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            // 切换到外部源 (因 streamUrl 为空，rule1 位于 index 0)
            vm.selectSource(0)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("https://cdn.example.com/resolved_ep1.m3u8", state.streamUrl)
            assertEquals("https://example.com", state.requestHeaders["Referer"])
            assertEquals("测试番剧 第一季", state.matchedVodName)
            assertFalse(state.isResolvingSource)
            assertEquals(null, state.error)
        }

    @Test
    fun selectEpisode_switchesEpisodeAndSniffsStream() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "TestRule",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/resolved_ep${epNumber.toInt()}.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            // 选中第 3 话
            vm.selectEpisode(PlayerEpisodeItem(id = 3003L, sort = 3f, name = "第三话"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(3f, state.episodeSort)
            assertEquals("https://cdn.example.com/resolved_ep3.m3u8", state.streamUrl)
        }

    @Test
    fun selectEpisode_onRuleSource_keepsCurrentStreamWhileResolving() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "TestRule",
                    urlTemplate = "https://example.com/watch?t={title}&ep={ep}",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> {
                        // 模拟网络耗时，以便观察到“解析中保留旧画面”的中间态
                        delay(1_000L)
                        return listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/ep${epNumber.toInt()}.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )
                    }

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()
            assertEquals("https://cdn.example.com/ep1.m3u8", vm.uiState.value.streamUrl)

            vm.selectEpisode(PlayerEpisodeItem(id = 3002L, sort = 2f, name = "第二话"))
            runCurrent()

            val mid = vm.uiState.value
            assertTrue("切换分集时应处于嗅探中", mid.isResolvingSource)
            assertEquals("解析中必须保留旧画面，不得清空导致黑屏", "https://cdn.example.com/ep1.m3u8", mid.streamUrl)

            advanceUntilIdle()
            assertEquals("https://cdn.example.com/ep2.m3u8", vm.uiState.value.streamUrl)
            assertFalse(vm.uiState.value.isResolvingSource)
        }

    @Test
    fun resolveDirectSource_missingEpisode_clearsInsteadOfPlayingOtherQueueEntry() =
        runTest {
            val vm =
                viewModel(
                    route =
                        route(
                            streamUrl = "https://example.com/ep1.m3u8",
                            queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"),
                        ),
                )
            advanceUntilIdle()

            // 选中不在直链列表中的第 5 话
            vm.selectEpisode(PlayerEpisodeItem(id = 9005L, sort = 5f, name = "第五话"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("不得回退到队列里的其它集（串集）", "", state.streamUrl)
            assertFalse(state.isResolvingSource)
            assertTrue("应如实给出无片源提示", state.error != null)
        }

    @Test
    fun selectNextSource_movesToNextAndWraps() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(id = "r1", name = "R1", urlTemplate = "https://a/?t={title}", isEnabled = true),
                    PlaybackSourceRule(id = "r2", name = "R2", urlTemplate = "https://b/?t={title}", isEnabled = true),
                ),
            )
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                )
            advanceUntilIdle()
            assertEquals(0, vm.uiState.value.selectedSourceIndex)

            vm.selectNextSource()
            assertEquals(1, vm.uiState.value.selectedSourceIndex)

            vm.selectNextSource()
            assertEquals("应环绕回第一个源", 0, vm.uiState.value.selectedSourceIndex)
        }

    @Test
    fun initialSource_prefersLastWorkingSourceId() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(id = "r1", name = "R1", urlTemplate = "https://a/?t={title}", isEnabled = true),
                    PlaybackSourceRule(id = "r2", name = "R2", urlTemplate = "https://b/?t={title}", isEnabled = true),
                ),
            )
            settings.setLastPlaybackSourceId("r2")
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                )

            advanceUntilIdle()

            assertEquals("应优先选中上次成功起播的源（r2 在第 1 下标）", 1, vm.uiState.value.selectedSourceIndex)
        }

    @Test
    fun selectNextSource_skipsFailingSourceAndPicksHealthyOne() =
        runTest {
            val failureStore = PlaybackFailureStore()
            failureStore.markFailed(url = "https://b/bad.m3u8", reason = "HTTP 404", sourceId = "r2")

            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(id = "r1", name = "R1", urlTemplate = "https://a/?t={title}", isEnabled = true),
                    PlaybackSourceRule(id = "r2", name = "R2", urlTemplate = "https://b/?t={title}", isEnabled = true),
                    PlaybackSourceRule(id = "r3", name = "R3", urlTemplate = "https://c/?t={title}", isEnabled = true),
                ),
            )
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    failureStore = failureStore,
                )
            advanceUntilIdle()
            assertEquals(0, vm.uiState.value.selectedSourceIndex)

            // 下一切源应跳过有连续失败的 r2，优先挑选健康源 r3 (index 2)
            vm.selectNextSource()
            assertEquals(2, vm.uiState.value.selectedSourceIndex)
        }

    @Test
    fun initialSource_skipsRememberedSourceIfItHasFailuresAndPicksHealthyOne() =
        runTest {
            val failureStore = PlaybackFailureStore()
            failureStore.markFailed(url = "https://a/bad.m3u8", reason = "HTTP 404", sourceId = "r1")

            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(id = "r1", name = "R1", urlTemplate = "https://a/?t={title}", isEnabled = true),
                    PlaybackSourceRule(id = "r2", name = "R2", urlTemplate = "https://b/?t={title}", isEnabled = true),
                ),
            )
            settings.setLastPlaybackSourceId("r1")
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    failureStore = failureStore,
                )
            advanceUntilIdle()

            // r1 虽然被记忆，但已有失败记录；应优先选中第一个健康源 r2 (index 1)
            assertEquals("记忆源连续失败时应退回第一个健康源", 1, vm.uiState.value.selectedSourceIndex)
        }

    @Test
    fun onPlaybackReady_withoutAnInPlaySource_recordsNothing() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                )
            advanceUntilIdle()
            assertNull("规则源未解析出地址时没有在播源", vm.uiState.value.playingSourceId)

            vm.onPlaybackReady()
            advanceUntilIdle()

            // 引擎回调必须按"真正在播的源"归因：没有在播源就不得凭空记一条"上次可用源"
            assertEquals("", settings.lastPlaybackSourceId.first())
        }

    @Test
    fun sourceRule_probesPlainTitlesBeforeEpisodeSuffixedKeywords() =
        runTest {
            // 取源接口形态：wd= 是标题模糊搜索，一次就返回整部片子（含全部分集），
            // 集号由解析器在结果里本地匹配，把集号写进关键词反而匹配不上
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "接口站",
                    urlTemplate = "https://api.example.tv/provide/vod/?ac=detail&wd={title}",
                    kind = PlaybackRuleKind.SOURCE,
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val capturedTitles = mutableListOf<String>()
            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveRule(
                        rule: PlaybackSourceRule,
                        title: String,
                        epNumber: Float,
                        subjectId: Long,
                        episodeId: Long,
                    ): List<PlayableSource> {
                        capturedTitles += title
                        return emptyList()
                    }
                }

            PlayerViewModel(
                route =
                    PlayerRoute(
                        subjectId = subjectId,
                        episodeId = episodeId,
                        streamUrl = "",
                        subjectName = "葬送的芙莉莲",
                        episodeSort = 1f,
                        initialRuleId = "rule1",
                    ),
                collectionRepository = FakeCollectionRepository(),
                authRepository = FakeAuthRepository(initialLoggedIn = true),
                settingsRepository = settings,
                playbackResolverRepository = fakeResolver,
            )
            advanceUntilIdle()

            assertTrue("应发出关键词尝试：$capturedTitles", capturedTitles.isNotEmpty())
            // 原样（简体）必须排第一：实测 3 站 × 4 番，简体 11/12、繁体 5/12、日文原名 2/12
            assertEquals("葬送的芙莉莲", capturedTitles.first())
            val leadingPlain = capturedTitles.takeWhile { !it.contains(" 0") && !it.endsWith(" 1") }
            assertTrue("纯标题应排在带集号关键词之前：$capturedTitles", leadingPlain.size >= 2)
            assertTrue("带集号关键词应作为回退保留：$capturedTitles", capturedTitles.any { it.endsWith(" 01") })
        }

    @Test
    fun pageRule_keepsEpisodeSuffixedKeywordFirst() =
        runTest {
            // 网页搜索页的文章以「标题 集号」命名，带集号能直接命中单集页，故仍集号优先
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "页面站",
                    urlTemplate = "https://example.tv/?s={title}",
                    kind = PlaybackRuleKind.PAGE,
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val capturedTitles = mutableListOf<String>()
            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> {
                        capturedTitles += title
                        return emptyList()
                    }

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            PlayerViewModel(
                route =
                    PlayerRoute(
                        subjectId = subjectId,
                        episodeId = episodeId,
                        streamUrl = "",
                        subjectName = "葬送的芙莉莲",
                        episodeSort = 1f,
                        initialRuleId = "rule1",
                    ),
                collectionRepository = FakeCollectionRepository(),
                authRepository = FakeAuthRepository(initialLoggedIn = true),
                settingsRepository = settings,
                playbackResolverRepository = fakeResolver,
            )
            advanceUntilIdle()

            assertTrue("应发出关键词尝试：$capturedTitles", capturedTitles.isNotEmpty())
            assertEquals("葬送的芙莉莲 01", capturedTitles.first())
        }

    @Test
    fun resolveRule_givesUpAfterTotalTimeoutAndSaysSo() =
        runTest {
            // 串行试探必须有个总闸：单次请求卡住时也要能收手并如实告知，
            // 而不是让用户对着"嗅探中"干等
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "慢站",
                    urlTemplate = "https://slow.example.tv/provide/vod/?ac=detail&wd={title}",
                    kind = PlaybackRuleKind.SOURCE,
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveRule(
                        rule: PlaybackSourceRule,
                        title: String,
                        epNumber: Float,
                        subjectId: Long,
                        episodeId: Long,
                    ): List<PlayableSource> {
                        // 挂着不返回：只有真正会打断请求的超时才能收手
                        delay(60_000)
                        return emptyList()
                    }
                }

            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            initialRuleId = "rule1",
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isResolvingSource)
            assertTrue("超时文案应说明已尝试次数：${state.error}", state.error?.contains("超时") == true)
            assertEquals(0, state.resolveAttempt)
        }

    @Test
    fun episodeSniffing_noStreamFound_setsError() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "EmptyRule",
                    urlTemplate = "https://example.com/empty",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val failureStore = PlaybackFailureStore()
            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                    failureStore = failureStore,
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.error?.contains("未在【EmptyRule】中解析到") == true)
            assertFalse(state.isResolvingSource)
            assertEquals(1, failureStore.sourceHealth.value["rule1"]?.consecutiveFailures)
        }

    @Test
    fun initialRuleId_automaticallySelectsAndSniffsTargetRule() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule1 =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "Rule 1",
                    urlTemplate = "https://example.com/1",
                    isEnabled = true,
                )
            val rule2 =
                PlaybackSourceRule(
                    id = "rule2",
                    name = "Rule 2",
                    urlTemplate = "https://example.com/2",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule1, rule2))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://cdn.example.com/rule2_stream.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = "", initialRuleId = "rule2"),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("rule2", state.currentSource?.id)
            assertEquals("https://cdn.example.com/rule2_stream.m3u8", state.streamUrl)
            assertFalse(state.isResolvingSource)
        }

    @Test
    fun manualToggleWatched_whenNotWatched_marksWatched() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val vm =
                PlayerViewModel(
                    route = route(),
                    collectionRepository = collectionRepo,
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                )

            assertFalse(vm.uiState.value.isWatched)

            vm.manualToggleWatched()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isWatched)
            assertEquals(1, collectionRepo.updateEpisodeCallCount)

            val event = vm.events.first()
            assertTrue(event is PlayerUiEvent.ShowSnackbar)
        }

    @Test
    fun manualToggleWatched_whenAlreadyWatched_unmarksWatched() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val vm =
                PlayerViewModel(
                    route = route(),
                    collectionRepository = collectionRepo,
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                )

            // 先标记为已看
            vm.manualToggleWatched()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isWatched)

            // 再次调用切换为未看
            vm.manualToggleWatched()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isWatched)
            assertEquals(2, collectionRepo.updateEpisodeCallCount)

            val event = vm.events.first()
            assertTrue(event is PlayerUiEvent.ShowSnackbar)
        }

    @Test
    fun manualToggleWatched_whenNotLoggedIn_doesNothing() =
        runTest {
            val collectionRepo = FakeCollectionRepository()
            val vm =
                PlayerViewModel(
                    route = route(),
                    collectionRepository = collectionRepo,
                    authRepository = FakeAuthRepository(initialLoggedIn = false),
                    settingsRepository = FakeSettingsRepository(),
                )

            vm.manualToggleWatched()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isWatched)
            assertEquals(0, collectionRepo.updateEpisodeCallCount)
        }

    @Test
    fun loadComments_success_populatesCommentsInUiState() =
        runTest {
            val communityRepo = FakeCommunityRepository()
            communityRepo.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(
                        EpisodeComment(id = 101L, content = "第一条吐槽"),
                        EpisodeComment(id = 102L, content = "第二条吐槽"),
                    ),
                )

            val vm = viewModel(communityRepository = communityRepo)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(2, state.comments.size)
            assertEquals("第一条吐槽", state.comments[0].content)
            assertEquals(1, state.comments[0].floor)
            assertEquals("第二条吐槽", state.comments[1].content)
            assertEquals(2, state.comments[1].floor)
            assertEquals(2, state.currentEpisodeCommentCount)
            assertFalse(state.isCommentsLoading)
        }

    @Test
    fun selectEpisode_resetsAndReloadsCommentsForNewEpisode() =
        runTest {
            val communityRepo = FakeCommunityRepository()
            communityRepo.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(EpisodeComment(id = 201L, content = "新分集吐槽")),
                )

            val vm = viewModel(communityRepository = communityRepo)
            advanceUntilIdle()

            vm.selectEpisode(PlayerEpisodeItem(id = 3003L, sort = 2f, name = "第2话"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(3003L, state.episodeId)
            assertEquals(1, state.comments.size)
            assertEquals("新分集吐槽", state.comments.single().content)
        }

    @Test
    fun setCommentSortOrder_resortsComments() =
        runTest {
            val communityRepo = FakeCommunityRepository()
            communityRepo.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(
                        EpisodeComment(id = 101L, content = "第一条", floor = 1),
                        EpisodeComment(id = 102L, content = "第二条", floor = 2),
                    ),
                )

            val vm = viewModel(communityRepository = communityRepo)
            advanceUntilIdle()

            assertEquals(
                listOf(101L, 102L),
                vm.uiState.value.comments
                    .map { it.id },
            )

            vm.setCommentSortOrder(CommentSortOrder.DESCENDING)
            advanceUntilIdle()

            assertEquals(
                listOf(102L, 101L),
                vm.uiState.value.comments
                    .map { it.id },
            )
            assertEquals(CommentSortOrder.DESCENDING, vm.uiState.value.commentSortOrder)
        }

    @Test
    fun setCommentSortOrder_writesTheSharedDevicePreference() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            // 从未设置过（空串）即默认热门；这条默认值必须与分集详情页同源
            assertEquals(CommentSortOrder.HOT, vm.uiState.value.commentSortOrder)

            vm.setCommentSortOrder(CommentSortOrder.ASCENDING)
            advanceUntilIdle()

            assertEquals(1, settings.setCommentSortOrderCallCount)
            assertEquals("asc", settings.settings.first().commentSortOrder)
            assertEquals(CommentSortOrder.ASCENDING, vm.uiState.value.commentSortOrder)
        }

    @Test
    fun commentSortOrder_readsPreferenceWrittenByTheOtherPage() =
        runTest {
            // 分集详情页写入的那条设备侧偏好，播放页必须原样读到（同一组件两处共用一条偏好）
            val settings = FakeSettingsRepository(UserSettings(commentSortOrder = "desc"))
            val vm = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            assertEquals(CommentSortOrder.DESCENDING, vm.uiState.value.commentSortOrder)
            // 只读不写：投影回读不得反过来把偏好改写成自己的默认值
            assertEquals(0, settings.setCommentSortOrderCallCount)
        }

    @Test
    fun toggleCommentReaction_optimisticUpdate() =
        runTest {
            val communityRepo = FakeCommunityRepository()
            communityRepo.getEpisodeCommentsResult =
                AppResult.Success(
                    listOf(
                        EpisodeComment(id = 101L, content = "第一条", floor = 1, reactions = emptyList()),
                    ),
                )

            val authRepo =
                FakeAuthRepository(
                    initialLoggedIn = true,
                    initialProfile = UserProfile(id = 12345L),
                )
            val vm = viewModel(communityRepository = communityRepo, authRepository = authRepo)
            advanceUntilIdle()

            val comment =
                vm.uiState.value.comments
                    .single()
            vm.toggleCommentReaction(comment, reactionValue = 48)
            advanceUntilIdle()

            val updatedComment =
                vm.uiState.value.comments
                    .single()
            val reaction = updatedComment.reactions.firstOrNull { it.value == 48 }
            assertTrue(reaction != null)
            assertEquals(1, reaction?.count)
        }

    @Test
    fun longSeries_updatesTotalEpisodesAndLoadsMoreEpisodes() =
        runTest {
            val fakeSubjectRepo = FakeSubjectRepository()
            val initialEpisodes =
                (1..100).map { i ->
                    com.infinitezerone.minibgm.core.model.Episode(
                        id = i.toLong(),
                        name = "第 $i 话",
                        sort = i.toFloat(),
                        ep = i.toFloat(),
                        type = 0,
                    )
                }
            fakeSubjectRepo.sendEpisodes(subjectId, initialEpisodes)
            fakeSubjectRepo.setTotalEpisodes(subjectId, 1490)
            fakeSubjectRepo.setHasMoreEpisodes(subjectId, true)

            val vm = viewModel(subjectRepository = fakeSubjectRepo)
            advanceUntilIdle()

            assertEquals(1490, vm.uiState.value.totalEpisodes)
            assertTrue(
                vm.uiState.value.episodes
                    .isNotEmpty(),
            )
            assertTrue(vm.uiState.value.hasMoreEpisodes)

            vm.loadMoreEpisodes()
            advanceUntilIdle()
            assertEquals(1, fakeSubjectRepo.loadMoreEpisodesCallCount)
        }

    @Test
    fun danmaku_toggleAndLoad_updatesUiState() =
        runTest {
            val sampleDanmakus =
                listOf(
                    DanmakuItem(timeMs = 1000L, text = "弹幕测试", color = 0xFFFFFF),
                )
            val fakeDanmaku =
                object : DanmakuRepository {
                    var queryCount = 0

                    override suspend fun getDanmaku(
                        bangumiEpisodeId: Long,
                        subjectId: Long,
                        title: String,
                        episodeSort: Float,
                        episodeName: String?,
                        secondaryTitle: String?,
                        appId: String?,
                        appSecret: String?,
                        apiEndpoint: String?,
                    ): DanmakuResult {
                        queryCount++
                        return DanmakuResult(sampleDanmakus, null)
                    }

                    override suspend fun saveDanmakuOverride(
                        bangumiEpisodeId: Long,
                        subjectId: Long,
                        candidate: DanmakuEpisodeCandidate,
                    ) = Unit

                    override suspend fun clearDanmakuOverride(bangumiEpisodeId: Long) = Unit
                }

            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            streamUrl = initialStreamUrl,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                    danmakuRepository = fakeDanmaku,
                )
            advanceUntilIdle()

            // 默认开启
            assertTrue(vm.uiState.value.isDanmakuEnabled)

            // 切换开关
            vm.toggleDanmaku()
            assertFalse(vm.uiState.value.isDanmakuEnabled)

            vm.toggleDanmaku()
            assertTrue(vm.uiState.value.isDanmakuEnabled)

            // 验证弹幕加载成功
            assertEquals(sampleDanmakus, vm.uiState.value.danmakus)
            assertTrue(fakeDanmaku.queryCount >= 1)
        }

    @Test
    fun danmaku_whenEmpty_toggleEmitsSnackbarEvent() =
        runTest {
            val emptyDanmakuRepo =
                object : DanmakuRepository {
                    override suspend fun getDanmaku(
                        bangumiEpisodeId: Long,
                        subjectId: Long,
                        title: String,
                        episodeSort: Float,
                        episodeName: String?,
                        secondaryTitle: String?,
                        appId: String?,
                        appSecret: String?,
                        apiEndpoint: String?,
                    ): DanmakuResult = DanmakuResult(emptyList(), null)

                    override suspend fun saveDanmakuOverride(
                        bangumiEpisodeId: Long,
                        subjectId: Long,
                        candidate: DanmakuEpisodeCandidate,
                    ) = Unit

                    override suspend fun clearDanmakuOverride(bangumiEpisodeId: Long) = Unit
                }

            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            streamUrl = initialStreamUrl,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                    danmakuRepository = emptyDanmakuRepo,
                )
            advanceUntilIdle()

            // 当前弹幕为空，关 -> 开 时触发轻量提示
            vm.toggleDanmaku() // 默认开 -> 关
            vm.toggleDanmaku() // 关 -> 开

            val event = vm.events.first()
            assertTrue(event is PlayerUiEvent.ShowSnackbar && event.messageRes == R.string.feature_subject_player_danmaku_empty_hint)
        }

    /** 固定返回同一份弹幕的替身（记录调用次数，用于验证「关闭弹幕时零请求」） */
    private class RecordingDanmakuRepository(
        private val items: List<DanmakuItem>,
    ) : DanmakuRepository {
        var queryCount = 0
            private set

        /** 分集覆盖写入次数：关闭弹幕期间一次都不该发生 */
        var overrideCount = 0
            private set

        override suspend fun getDanmaku(
            bangumiEpisodeId: Long,
            subjectId: Long,
            title: String,
            episodeSort: Float,
            episodeName: String?,
            secondaryTitle: String?,
            appId: String?,
            appSecret: String?,
            apiEndpoint: String?,
        ): DanmakuResult {
            queryCount++
            return DanmakuResult(items, null)
        }

        override suspend fun saveDanmakuOverride(
            bangumiEpisodeId: Long,
            subjectId: Long,
            candidate: DanmakuEpisodeCandidate,
        ) {
            overrideCount++
        }

        override suspend fun clearDanmakuOverride(bangumiEpisodeId: Long) = Unit
    }

    private fun danmakuRoute(): PlayerRoute = route().copy(subjectName = "葬送的芙莉莲")

    private fun danmakuViewModel(
        settings: FakeSettingsRepository,
        danmakuRepository: DanmakuRepository,
    ): PlayerViewModel =
        PlayerViewModel(
            route = danmakuRoute(),
            collectionRepository = FakeCollectionRepository(),
            authRepository = FakeAuthRepository(initialLoggedIn = true),
            settingsRepository = settings,
            danmakuRepository = danmakuRepository,
        )

    @Test
    fun danmakuToggle_isPersisted_andSurvivesViewModelRecreation() =
        runTest {
            val settings = FakeSettingsRepository()
            val firstRepo = RecordingDanmakuRepository(emptyList())
            val first = danmakuViewModel(settings, firstRepo)
            advanceUntilIdle()
            assertTrue("默认应开启弹幕", first.uiState.value.isDanmakuEnabled)

            first.toggleDanmaku()
            advanceUntilIdle()

            assertEquals("关闭动作必须落到设置仓库", 1, settings.setDanmakuEnabledCallCount)
            assertFalse(first.uiState.value.isDanmakuEnabled)

            // 重建播放页（等价于换条目/重进页面）：开关必须保持关闭，且关闭期间一次弹幕请求都不发
            val secondRepo = RecordingDanmakuRepository(emptyList())
            val second = danmakuViewModel(settings, secondRepo)
            advanceUntilIdle()

            assertFalse("弹幕开关必须跨播放页重建保持", second.uiState.value.isDanmakuEnabled)
            assertEquals("关闭弹幕期间必须零网络请求", 0, secondRepo.queryCount)
        }

    @Test
    fun danmakuDisabledByPreference_skipsNetworkUntilReEnabled() =
        runTest {
            val sampleDanmakus = listOf(DanmakuItem(timeMs = 1000L, text = "弹幕测试", color = 0xFFFFFF))
            val settings = FakeSettingsRepository(UserSettings(danmakuEnabled = false))
            val repo = RecordingDanmakuRepository(sampleDanmakus)
            val viewModel = danmakuViewModel(settings, repo)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDanmakuEnabled)
            assertEquals("持久化关闭的弹幕不得在进页时拉取", 0, repo.queryCount)

            viewModel.toggleDanmaku()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDanmakuEnabled)
            assertEquals("重开弹幕必须补拉且只拉一次", 1, repo.queryCount)
            assertEquals(sampleDanmakus, viewModel.uiState.value.danmakus)
        }

    @Test
    fun danmakuReEnabled_withEmptyResult_hintsOnlyAfterRealFetch() =
        runTest {
            val settings = FakeSettingsRepository(UserSettings(danmakuEnabled = false))
            val repo = RecordingDanmakuRepository(emptyList())
            val viewModel = danmakuViewModel(settings, repo)
            advanceUntilIdle()

            viewModel.toggleDanmaku()
            advanceUntilIdle()

            // 拨开关的瞬间不预判为空：提示必须由这次补拉的真实空结果给出
            assertEquals(1, repo.queryCount)
            val event = viewModel.events.first()
            assertTrue(event is PlayerUiEvent.ShowSnackbar && event.messageRes == R.string.feature_subject_player_danmaku_empty_hint)
        }

    @Test
    fun subtitlesDisabled_isPersisted_andRestoredOnReentry() =
        runTest {
            val settings = FakeSettingsRepository()
            val first = viewModel(settingsRepository = settings)
            advanceUntilIdle()
            assertFalse("默认不关闭字幕", first.uiState.value.subtitlesDisabled)

            first.setSubtitlesDisabled(true)
            advanceUntilIdle()

            assertEquals("关闭字幕必须落到设置仓库", 1, settings.setSubtitlesDisabledCallCount)
            assertTrue(first.uiState.value.subtitlesDisabled)

            // 重建播放页（等价于换条目/重进页面）：立场必须保持，UI 才能据此把引擎字幕轨关掉
            val second = viewModel(settingsRepository = settings)
            advanceUntilIdle()
            assertTrue("关闭字幕必须跨播放页重建保持", second.uiState.value.subtitlesDisabled)

            // 选轨/自动选轨 = 用户不再要求关闭
            second.setSubtitlesDisabled(false)
            advanceUntilIdle()
            assertFalse(second.uiState.value.subtitlesDisabled)
            assertFalse(settings.settings.first().subtitlesDisabled)
        }

    @Test
    fun selectDanmakuCandidate_whileDanmakuDisabled_isIgnored() =
        runTest {
            // 弹幕关掉后两个入口都会收起（控制栏按钮 / 设置面板行），但关掉开关的那一帧里旧入口
            // 仍可能被点到：放行会给「已关闭的功能」写持久化覆盖并立刻拉一次弹幕，与「关闭即零请求」冲突
            val settings = FakeSettingsRepository(UserSettings(danmakuEnabled = false))
            val repo = RecordingDanmakuRepository(emptyList())
            val viewModel = danmakuViewModel(settings, repo)
            advanceUntilIdle()

            viewModel.selectDanmakuCandidate(
                DanmakuEpisodeCandidate(
                    animeId = 1L,
                    animeTitle = "葬送的芙莉莲",
                    episodeId = 2L,
                    episodeTitle = "第2话",
                ),
            )
            advanceUntilIdle()

            assertEquals("关闭弹幕期间不得写分集覆盖", 0, repo.overrideCount)
            assertEquals("关闭弹幕期间不得拉取弹幕", 0, repo.queryCount)
        }

    @Test
    fun selectDanmakuCandidate_whileEnabled_savesOverrideAndReloads() =
        runTest {
            val sampleDanmakus = listOf(DanmakuItem(timeMs = 500L, text = "弹幕"))
            val settings = FakeSettingsRepository()
            val repo = RecordingDanmakuRepository(sampleDanmakus)
            val viewModel = danmakuViewModel(settings, repo)
            advanceUntilIdle()
            val baselineQueries = repo.queryCount

            viewModel.selectDanmakuCandidate(
                DanmakuEpisodeCandidate(
                    animeId = 1L,
                    animeTitle = "葬送的芙莉莲",
                    episodeId = 2L,
                    episodeTitle = "第2话",
                ),
            )
            advanceUntilIdle()

            assertEquals("选中候选必须写入分集覆盖", 1, repo.overrideCount)
            assertEquals("选中候选必须重载一次弹幕", baselineQueries + 1, repo.queryCount)
            assertEquals(sampleDanmakus, viewModel.uiState.value.danmakus)
        }

    @Test
    fun autoNextToggle_isPersisted_andSurvivesViewModelRecreation() =
        runTest {
            val settings = FakeSettingsRepository()
            val queueRoute = route(queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"))

            val first = viewModel(route = queueRoute, settingsRepository = settings)
            advanceUntilIdle()
            assertTrue("默认应开启自动连播", first.uiState.value.autoNextEnabled)

            first.toggleAutoNext()
            advanceUntilIdle()

            assertEquals("关闭动作必须落到设置仓库", 1, settings.setAutoNextEnabledCallCount)
            assertFalse(first.uiState.value.autoNextEnabled)

            // 重建播放页：自动连播必须保持关闭，且播完真的不再前进
            val second = viewModel(route = queueRoute, settingsRepository = settings)
            advanceUntilIdle()

            assertFalse("自动连播必须跨播放页重建保持", second.uiState.value.autoNextEnabled)
            assertFalse(second.onPlaybackEnded())
            assertEquals(0, second.uiState.value.currentIndex)
        }

    @Test
    fun streamUrl_dynamicallyUpdatesTorrentPlayerSessionActive() =
        runTest {
            val fakeTorrentEngine = FakeTorrentStreamEngine()
            val queue =
                listOf(
                    PlayerQueueEntry(streamUrl = "http://127.0.0.1:8080/ep1", episodeSort = 1f, episodeId = 101L),
                    PlayerQueueEntry(streamUrl = "https://cdn.example.com/ep2.m3u8", episodeSort = 2f, episodeId = 102L),
                    PlayerQueueEntry(streamUrl = "http://127.0.0.1:8080/ep3", episodeSort = 3f, episodeId = 103L),
                )
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = 101L,
                            streamUrl = "http://127.0.0.1:8080/ep1",
                            queue = queue,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                    torrentStreamEngine = fakeTorrentEngine,
                )
            advanceUntilIdle()

            // 1. 初始为 127.0.0.1 流，引擎应激活播放器会话
            assertTrue("初始 127.0.0.1 流应激活 playerSessionActive", fakeTorrentEngine.isPlayerSessionActive())

            // 2. 切到普通网络流（ep2），引擎应释放活跃标记
            vm.switchTo(1)
            advanceUntilIdle()
            assertFalse("切到在线 CDN 流应释放 playerSessionActive", fakeTorrentEngine.isPlayerSessionActive())

            // 3. 切回 127.0.0.1 流（ep3），引擎应重新激活
            vm.switchTo(2)
            advanceUntilIdle()
            assertTrue("切回 127.0.0.1 流应重新激活 playerSessionActive", fakeTorrentEngine.isPlayerSessionActive())
        }

    @Test
    fun buildSources_filtersOutTorrentRules() =
        runTest {
            val settings = FakeSettingsRepository()
            val onlineRule =
                PlaybackSourceRule(
                    id = "online1",
                    name = "Online Source",
                    urlTemplate = "https://example.com/play?t={title}",
                    isEnabled = true,
                    kind = PlaybackRuleKind.SOURCE,
                )
            val torrentRule =
                PlaybackSourceRule(
                    id = "torrent1",
                    name = "Torrent Feed",
                    urlTemplate = "https://example.com/rss?q={title}",
                    isEnabled = true,
                    kind = PlaybackRuleKind.TORRENT,
                )
            settings.importPlaybackRules(listOf(onlineRule, torrentRule))

            val viewModel = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            val sourceIds =
                viewModel.uiState.value.sources
                    .map { it.id }
            assertTrue("在线规则应在播放器线路中", sourceIds.contains("online1"))
            assertFalse("TORRENT 种子规则不得进入播放器在线线路列表", sourceIds.contains("torrent1"))
        }

    @Test
    fun selectEpisode_matchingEpisodeZero_matchesSuccessfully() =
        runTest {
            val queue =
                listOf(
                    PlayerQueueEntry(
                        streamUrl = "https://example.com/ep0.m3u8",
                        label = "0",
                        episodeSort = 0f,
                        episodeId = 0L,
                    ),
                    PlayerQueueEntry(
                        streamUrl = "https://example.com/ep1.m3u8",
                        label = "1",
                        episodeSort = 1f,
                        episodeId = 0L,
                    ),
                )
            val vm =
                viewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = 0L,
                            streamUrl = "https://example.com/ep1.m3u8",
                            queue = queue,
                            startIndex = 1,
                        ),
                )
            advanceUntilIdle()
            assertEquals("https://example.com/ep1.m3u8", vm.uiState.value.streamUrl)

            // 选择第 0 话（序章）
            vm.selectEpisode(PlayerEpisodeItem(id = 0L, sort = 0f, name = "第0话 序章"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("https://example.com/ep0.m3u8", state.streamUrl)
            assertEquals(0, state.currentIndex)
            assertEquals(null, state.error)
        }

    @Test
    fun selectEpisode_clearsStaleDanmakusAndRecoversResumePositionFromHistory() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = 3003L,
                    subjectId = subjectId,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "第 2 话",
                    episodeSort = 2f,
                    coverUrl = "",
                    streamUrl = "https://example.com/dynamic_token_123.m3u8",
                    positionMs = 120_000L,
                    durationMs = 24 * 60_000L,
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            streamUrl = initialStreamUrl,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                    playbackHistoryRepository = historyRepo,
                )
            advanceUntilIdle()

            // 切换至第 2 话
            vm.selectEpisode(PlayerEpisodeItem(id = 3003L, sort = 2f, name = "第 2 话"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(3003L, state.episodeId)
            assertEquals(120_000L, state.resumePositionMs)
            assertTrue("换集后旧弹幕应立即清空", state.danmakus.isEmpty())
        }

    @Test
    fun switchTo_withKnownEpisode_delegatesToSelectEpisodeAndRecoversResumePosition() =
        runTest {
            val historyRepo = FakePlaybackHistoryRepository()
            historyRepo.saveHistory(
                PlaybackHistory(
                    episodeId = 3003L,
                    subjectId = subjectId,
                    subjectName = "葬送的芙莉莲",
                    episodeName = "第 2 话",
                    episodeSort = 2f,
                    coverUrl = "",
                    streamUrl = "https://example.com/ep2.m3u8",
                    positionMs = 99_000L,
                    durationMs = 24 * 60_000L,
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            streamUrl = "https://example.com/ep1.m3u8",
                            queue =
                                listOf(
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep1.m3u8", episodeSort = 1f, episodeId = episodeId),
                                    PlayerQueueEntry(streamUrl = "https://example.com/ep2.m3u8", episodeSort = 2f, episodeId = 3003L),
                                ),
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = FakeSettingsRepository(),
                    playbackHistoryRepository = historyRepo,
                )
            advanceUntilIdle()

            // 通过选集抽屉 switchTo(1) 切换到第 2 话
            vm.switchTo(1)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, state.currentIndex)
            assertEquals("https://example.com/ep2.m3u8", state.streamUrl)
            assertEquals(2f, state.episodeSort)
            assertEquals(99_000L, state.resumePositionMs)
            assertTrue("换集后旧弹幕应立即清空", state.danmakus.isEmpty())
        }

    @Test
    fun selectSource_withinSameEpisode_preservesLatestPlaybackPosition() =
        runTest {
            val settings = FakeSettingsRepository()
            val rule =
                PlaybackSourceRule(
                    id = "rule1",
                    name = "Rule 1",
                    urlTemplate = "https://example.com/rule1",
                    isEnabled = true,
                )
            settings.importPlaybackRules(listOf(rule))

            val fakeResolver =
                object : PlaybackResolverRepository {
                    override suspend fun resolvePages(
                        pageUrls: List<String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> =
                        listOf(
                            PlayableSource(
                                url = "https://example.com/rule1_ep1.m3u8",
                                kind = PlaylistEntryKind.DIRECT,
                            ),
                        )

                    override suspend fun resolveTemplate(
                        url: String,
                        headers: Map<String, String>,
                        epNumber: Float,
                        siteName: String,
                        title: String,
                    ): List<PlayableSource> = emptyList()
                }

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = "https://example.com/direct_ep1.m3u8"),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            // 观看至 120 秒
            vm.onProgressChanged(120_000L)

            // 切换至规则源 (index 1)
            vm.selectSource(1)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("https://example.com/rule1_ep1.m3u8", state.streamUrl)
            assertEquals("同分集内切换播放线路应无缝继承当前最新进度", 120_000L, state.resumePositionMs)
        }

    @Test
    fun selectSource_resolveFailure_keepsPlayingSourceAndDoesNotRaiseErrorState() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "https://example.com/direct_ep1.m3u8",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = resolverReturning(),
                )
            advanceUntilIdle()
            assertEquals("direct", vm.uiState.value.playingSourceId)

            // 切到解析不出地址的规则源：旧画面仍在播，不得被全屏错误态盖掉，也不得谎称新源"正在使用"
            vm.selectSource(1)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("切源失败后选中项必须回退到仍在播的线路", 0, state.selectedSourceIndex)
            assertEquals("direct", state.playingSourceId)
            assertNull("仍在播放时禁止落全屏错误态", state.error)
            val event = vm.events.first()
            assertTrue("失败原因必须以轻提示透出", event is PlayerUiEvent.ShowSnackbar)
            val snackbar = event as PlayerUiEvent.ShowSnackbar
            assertEquals(
                "换线失败的提示必须带「切换线路」动作：面板收起前提下把重试从 2 击降到 1 击",
                PlayerUiEvent.PlayerSnackbarAction.OPEN_SOURCES,
                snackbar.action,
            )
            assertEquals(R.string.feature_subject_player_switch_source, snackbar.actionRes)
        }

    @Test
    fun selectSource_resolveFailure_marksFailureOnTheSourceThatFailed() =
        runTest {
            val failureStore = PlaybackFailureStore()
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "https://example.com/direct_ep1.m3u8",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    failureStore = failureStore,
                    playbackResolverRepository = resolverReturning(),
                )
            advanceUntilIdle()

            vm.selectSource(1)
            advanceUntilIdle()

            // 归因给「用户选了但没解析出地址」的那条规则源，而不是仍在播的直链
            assertEquals(1, failureStore.sourceHealth.value["r1"]?.consecutiveFailures)
            assertNull(failureStore.sourceHealth.value["direct"])
        }

    @Test
    fun onPlaybackReady_duringSourceSwitch_staysAttributedToThePlayingSource() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "https://example.com/direct_ep1.m3u8",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository =
                        object : PlaybackResolverRepository {
                            override suspend fun resolvePages(
                                pageUrls: List<String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ): List<PlayableSource> = emptyList()

                            override suspend fun resolveTemplate(
                                url: String,
                                headers: Map<String, String>,
                                epNumber: Float,
                                siteName: String,
                                title: String,
                            ): List<PlayableSource> = emptyList()

                            override suspend fun resolveRule(
                                rule: PlaybackSourceRule,
                                title: String,
                                epNumber: Float,
                                subjectId: Long,
                                episodeId: Long,
                            ): List<PlayableSource> {
                                gate.await()
                                return listOf(PlayableSource(url = "https://example.com/r1_ep1.m3u8", kind = PlaylistEntryKind.DIRECT))
                            }
                        },
                )
            advanceUntilIdle()

            vm.selectSource(1)
            runCurrent()
            assertEquals("解析窗口内选中项已切到新源", 1, vm.uiState.value.selectedSourceIndex)
            assertEquals("但仍在播的仍是旧线路", "direct", vm.uiState.value.playingSourceId)
            assertTrue("此刻解析仍在途", vm.uiState.value.isResolvingSource)

            // 旧流此刻才回 READY：它属于 direct，绝不能把「上次可用源」记到正在解析的 r1 头上。
            // 注意用 runCurrent 而非 advanceUntilIdle——后者会把虚拟时间推到解析总超时，
            // 在途嗅探会被 TimeoutCancellation 掐掉，"解析窗口"就观察不到了
            vm.onPlaybackReady()
            runCurrent()
            assertEquals("direct", settings.lastPlaybackSourceId.first())

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("https://example.com/r1_ep1.m3u8", vm.uiState.value.streamUrl)
            assertEquals("r1", vm.uiState.value.playingSourceId)

            vm.onPlaybackReady()
            advanceUntilIdle()
            assertEquals("解析成功后归属才切换到新源", "r1", settings.lastPlaybackSourceId.first())
        }

    @Test
    fun ruleListChange_keepsSelectionAndPlayingSourceByIdentity() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1", "r2"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = resolverReturning("https://example.com/r_ep1.m3u8"),
                )
            advanceUntilIdle()
            vm.selectSource(1)
            advanceUntilIdle()
            assertEquals("r2", vm.uiState.value.playingSourceId)

            // 在最前面插入一条新规则：所有下标后移，选中项与在播源都必须按标识找回
            settings.setPlaybackRules(rulesOf("r0", "r1", "r2"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("下标漂移不得把选中线路串到别的规则上", "r2", state.sources[state.selectedSourceIndex].id)
            assertEquals("r2", state.playingSourceId)
        }

    @Test
    fun initialRuleId_onlyAppliesOnFirstBuild_manualSelectionIsNotYankedBack() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1", "r2"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                            initialRuleId = "r1",
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = resolverReturning("https://example.com/r_ep1.m3u8"),
                )
            advanceUntilIdle()
            assertEquals(
                "入口规则首次建表应生效",
                "r1",
                vm.uiState.value.sources[vm.uiState.value.selectedSourceIndex]
                    .id,
            )

            vm.selectSource(1)
            advanceUntilIdle()
            assertEquals("r2", vm.uiState.value.playingSourceId)

            // 列表再次变化时不得把用户手动选的线路拽回入口规则
            settings.setPlaybackRules(rulesOf("r1", "r2", "r3"))
            advanceUntilIdle()

            assertEquals(
                "r2",
                vm.uiState.value.sources[vm.uiState.value.selectedSourceIndex]
                    .id,
            )
            assertEquals("r2", vm.uiState.value.playingSourceId)
        }

    @Test
    fun onPlaybackReady_recordsLastWorkingSource() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackRules(rulesOf("r1"))
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = resolverReturning("https://example.com/r1_ep1.m3u8"),
                )
            advanceUntilIdle()
            assertEquals("r1", vm.uiState.value.playingSourceId)

            vm.onPlaybackReady()
            advanceUntilIdle()

            assertEquals("r1", settings.lastPlaybackSourceId.first())
        }

    @Test
    fun danmakuSettings_persistedAndReflectedInUiState() =
        runTest {
            val settings = FakeSettingsRepository()
            val vm =
                PlayerViewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = episodeId,
                            streamUrl = "https://example.com/ep1.m3u8",
                            subjectName = "葬送的芙莉莲",
                            episodeSort = 1f,
                        ),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                )
            advanceUntilIdle()

            assertEquals(1.0f, vm.uiState.value.danmakuAlpha)
            assertEquals(8500L, vm.uiState.value.danmakuDurationMs)

            vm.setDanmakuAlpha(0.6f)
            vm.setDanmakuDurationMs(5000L)
            advanceUntilIdle()

            assertEquals(0.6f, settings.settings.first().danmakuAlpha)
            assertEquals(5000L, settings.settings.first().danmakuDurationMs)
            assertEquals(0.6f, vm.uiState.value.danmakuAlpha)
            assertEquals(5000L, vm.uiState.value.danmakuDurationMs)
        }

    @Test
    fun formatShortAirdate_formatsIsoAndDateStrings() {
        assertEquals("11-05", formatShortAirdate("2026-11-05"))
        assertEquals("11-05", formatShortAirdate("2026-11-05T12:00:00Z"))
        assertEquals("11-05", formatShortAirdate("2026-11-05 08:30:00"))
        assertEquals("", formatShortAirdate(""))
    }

    @Test
    fun playerEpisodeItem_isNotAired_identifiesFutureEpisodes() {
        val futureEp = PlayerEpisodeItem(id = 1L, airdate = "2099-01-01")
        val pastEp = PlayerEpisodeItem(id = 2L, airdate = "2000-01-01")
        val blankEp = PlayerEpisodeItem(id = 3L, airdate = "")
        assertTrue(futureEp.isNotAired)
        assertFalse(pastEp.isNotAired)
        assertFalse(blankEp.isNotAired)
    }

    @Test
    fun playNextEpisode_whenNextEpisodeIsNotAired_doesNotAutoPlayNext() =
        runTest {
            val episodes =
                listOf(
                    Episode(id = 1001L, sort = 1f, type = 0, name = "Ep 1", airdate = "2020-01-01"),
                    Episode(id = 1002L, sort = 2f, type = 0, name = "Ep 2", airdate = "2099-01-01"),
                )
            val subjectRepo = FakeSubjectRepository()
            subjectRepo.sendEpisodes(subjectId, episodes)

            val viewModel =
                viewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = 1001L,
                            streamUrl = "https://example.com/ep1.m3u8",
                            episodeSort = 1f,
                            queue = emptyList(),
                        ),
                    subjectRepository = subjectRepo,
                )
            advanceUntilIdle()

            val advanced = viewModel.playNextEpisode()
            assertFalse(advanced)
            assertEquals(1001L, viewModel.uiState.value.episodeId)
            assertEquals("https://example.com/ep1.m3u8", viewModel.uiState.value.streamUrl)
        }

    @Test
    fun selectEpisode_whenEpisodeIsNotAired_interceptsAndShowsSnackbar() =
        runTest {
            val viewModel =
                viewModel(
                    route =
                        PlayerRoute(
                            subjectId = subjectId,
                            episodeId = 1001L,
                            streamUrl = "https://example.com/ep1.m3u8",
                            episodeSort = 1f,
                        ),
                )
            advanceUntilIdle()

            val futureEp = PlayerEpisodeItem(id = 1002L, sort = 2f, airdate = "2099-01-01")
            viewModel.selectEpisode(futureEp)

            assertEquals(1001L, viewModel.uiState.value.episodeId)
            assertEquals("https://example.com/ep1.m3u8", viewModel.uiState.value.streamUrl)

            val event = viewModel.events.first()
            assertTrue(event is PlayerUiEvent.ShowSnackbar)
            assertEquals(R.string.feature_subject_player_episode_not_aired, (event as PlayerUiEvent.ShowSnackbar).messageRes)
        }
}
