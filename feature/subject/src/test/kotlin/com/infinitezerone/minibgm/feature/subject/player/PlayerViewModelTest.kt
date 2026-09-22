package com.infinitezerone.minibgm.feature.subject.player

import com.infinitezerone.minibgm.core.data.playback.PlaybackFailureStore
import com.infinitezerone.minibgm.core.data.repository.PlaybackResolverRepository
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.navigation.PlayerQueueEntry
import com.infinitezerone.minibgm.core.navigation.PlayerRoute
import com.infinitezerone.minibgm.core.testing.repository.FakeAuthRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSettingsRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
    ): PlayerViewModel =
        PlayerViewModel(
            route = route,
            collectionRepository = FakeCollectionRepository(),
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            failureStore = failureStore,
        )

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
            assertEquals("葬送的芙莉蓮 01", capturedTitles.first())
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
}
