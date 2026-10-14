package com.infinitezerone.minibgm.feature.subject.player

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.DanmakuRepository
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.data.repository.SubjectRepository
import com.infinitezerone.minibgm.core.model.DanmakuEpisodeCandidate
import com.infinitezerone.minibgm.core.model.DanmakuItem
import com.infinitezerone.minibgm.core.model.DanmakuResult
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.model.UserProfile
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeTorrentStreamEngine
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import com.infinitezerone.minibgm.feature.subject.R
import com.infinitezerone.minibgm.feature.subject.components.CommentSortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    ) = PlayerRoute(
        subjectId = subjectId,
        episodeId = episodeId,
        streamUrl = streamUrl,
        queue = queue,
        startIndex = startIndex,
        initialRuleId = initialRuleId,
    )

    private fun viewModel(
        route: PlayerRoute = route(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialLoggedIn = true),
        failureStore: PlaybackFailureStore? = null,
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        subjectRepository: SubjectRepository? = null,
        communityRepository: FakeCommunityRepository? = null,
    ): PlayerViewModel =
        PlayerViewModel(
            route = route,
            collectionRepository = FakeCollectionRepository(),
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            failureStore = failureStore,
            subjectRepository = subjectRepository,
            communityRepository = communityRepository,
        )

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
    fun queuePlayback_switchToOutOfRange_isIgnored() {
        val viewModel = viewModel(route = route(queue = queueOf("https://example.com/ep1.m3u8")))

        viewModel.switchTo(5)

        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun resumePosition_exposedFromSettingsForCurrentEpisode() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackPositions(mapOf(initialStreamUrl to 62_000L))

            val viewModel = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            assertEquals(62_000L, viewModel.uiState.value.resumePositionMs)
        }

    @Test
    fun switchTo_flushesPreviousEpisodePositionBeforeLeaving() =
        runTest {
            val settings = FakeSettingsRepository()
            val viewModel =
                viewModel(
                    route = route(queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8")),
                    settingsRepository = settings,
                )

            viewModel.onProgressChanged(30_000L)
            viewModel.switchTo(1)
            advanceUntilIdle()

            assertEquals(30_000L, settings.playbackPositions.first()["https://example.com/ep1.m3u8"])
            // 新分集没有已存位置，不恢复
            assertEquals(0L, viewModel.uiState.value.resumePositionMs)
        }

    @Test
    fun selectEpisode_doesNotCarryPreviousEpisodeResumePosition() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackPositions(
                mapOf(
                    "https://example.com/ep1.m3u8" to 300_000L,
                    "https://example.com/ep2.m3u8" to 90_000L,
                ),
            )
            val viewModel =
                viewModel(
                    route =
                        route(
                            queue = queueOf("https://example.com/ep1.m3u8", "https://example.com/ep2.m3u8"),
                        ),
                    settingsRepository = settings,
                )
            advanceUntilIdle()
            assertEquals(300_000L, viewModel.uiState.value.resumePositionMs)

            viewModel.selectEpisode(PlayerEpisodeItem(id = 0L, sort = 2f, name = "第二话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://example.com/ep2.m3u8", state.streamUrl)
            assertEquals("换集必须下发新分集自己的续播点，不得沿用上一集进度", 90_000L, state.resumePositionMs)
        }

    @Test
    fun selectEpisode_onRuleSource_appliesNewEpisodeResumePositionAfterSniffing() =
        runTest {
            val settings = FakeSettingsRepository()
            // 新分集此前看过一部分：换集后必须恢复到它自己的位置
            settings.setPlaybackPositions(mapOf("https://cdn.example.com/ep2.m3u8" to 60_000L))
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
            assertEquals("https://cdn.example.com/ep1.m3u8", viewModel.uiState.value.streamUrl)

            viewModel.selectEpisode(PlayerEpisodeItem(id = 0L, sort = 2f, name = "第二话"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("https://cdn.example.com/ep2.m3u8", state.streamUrl)
            assertEquals("规则源换集要在嗅探出地址后用新分集自己的续播点", 60_000L, state.resumePositionMs)
        }

    @Test
    fun onPlaybackEnded_clearsSavedResumePosition() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setPlaybackPositions(mapOf(initialStreamUrl to 62_000L))
            val viewModel = viewModel(settingsRepository = settings)
            advanceUntilIdle()

            viewModel.onPlaybackEnded()
            advanceUntilIdle()

            assertFalse(settings.playbackPositions.first().containsKey(initialStreamUrl))
        }

    @Test
    fun periodicFlush_persistsLatestProgress() =
        runTest {
            val settings = FakeSettingsRepository()
            val viewModel = viewModel(settingsRepository = settings)

            // 进度回调约 500ms 一跳，POSITION_SAVE_TICKS 跳后节流落盘
            repeat(POSITION_SAVE_TICKS) { viewModel.onProgressChanged(45_000L) }
            advanceUntilIdle()

            assertEquals(45_000L, settings.playbackPositions.first()[initialStreamUrl])
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
    fun onPlaybackReady_recordsLastWorkingSource() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.importPlaybackRules(
                listOf(
                    PlaybackSourceRule(id = "r1", name = "R1", urlTemplate = "https://a/?t={title}", isEnabled = true),
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

            vm.onPlaybackReady()
            advanceUntilIdle()

            assertEquals("r1", settings.lastPlaybackSourceId.first())
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

            val vm =
                PlayerViewModel(
                    route = route(streamUrl = ""),
                    collectionRepository = FakeCollectionRepository(),
                    authRepository = FakeAuthRepository(initialLoggedIn = true),
                    settingsRepository = settings,
                    playbackResolverRepository = fakeResolver,
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.error?.contains("未在【EmptyRule】中解析到") == true)
            assertFalse(state.isResolvingSource)
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
}
