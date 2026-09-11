package com.infinitezerone.minibgm.feature.subject

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.CommentUser
import com.infinitezerone.minibgm.core.model.EpisodeComment
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.SubjectComment
import com.infinitezerone.minibgm.core.model.SubjectCommentPage
import com.infinitezerone.minibgm.core.model.SubjectTopic
import com.infinitezerone.minibgm.core.testing.data.sampleCharacterList
import com.infinitezerone.minibgm.core.testing.data.sampleEpisodeList
import com.infinitezerone.minibgm.core.testing.data.samplePersonList
import com.infinitezerone.minibgm.core.testing.data.sampleRelationList
import com.infinitezerone.minibgm.core.testing.data.sampleSubject
import com.infinitezerone.minibgm.core.testing.data.sampleUserCollection
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeCommunityRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import com.infinitezerone.minibgm.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubjectDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fetchSuccess_subjectAndEpisodesEnterUiState() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    sendSubject(sampleSubject)
                    sendEpisodes(sampleSubject.id, sampleEpisodeList)
                    sendCharacters(sampleSubject.id, sampleCharacterList)
                    sendPersons(sampleSubject.id, samplePersonList)
                    sendRelations(sampleSubject.id, sampleRelationList)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            val state = viewModel.uiState.value

            assertFalse(state.isLoading)
            assertEquals(sampleSubject, state.subject)
            assertEquals(sampleEpisodeList, state.episodes)
            // 首屏按需懒加载：初次加载不请求演职员数据
            assertTrue(state.characters.isEmpty())
            assertTrue(state.persons.isEmpty())
            assertTrue(state.relations.isEmpty())
            assertNull(state.error)

            // 切换至资料与演职员 Tab 时按需触发加载
            viewModel.loadDetailsTabIfNeeded()
            val loadedState = viewModel.uiState.value
            assertEquals(sampleCharacterList, loadedState.characters)
            assertEquals(samplePersonList, loadedState.persons)
            assertEquals(sampleRelationList, loadedState.relations)
        }

    @Test
    fun subjectId_isForwardedToAllRepositoryCalls() =
        runTest {
            var requestedDetailId: Long? = null
            var requestedEpisodesId: Long? = null
            var requestedCharactersId: Long? = null
            var requestedPersonsId: Long? = null
            var requestedRelationsId: Long? = null
            val repository =
                FakeSubjectRepository().apply {
                    fetchSubjectDetailResult = { id ->
                        requestedDetailId = id
                        AppResult.Success(sampleSubject.copy(id = id))
                    }
                    fetchEpisodesResult = { id ->
                        requestedEpisodesId = id
                        AppResult.Success(emptyList())
                    }
                    fetchCharactersResult = { id ->
                        requestedCharactersId = id
                        AppResult.Success(emptyList())
                    }
                    fetchPersonsResult = { id ->
                        requestedPersonsId = id
                        AppResult.Success(emptyList())
                    }
                    fetchRelationsResult = { id ->
                        requestedRelationsId = id
                        AppResult.Success(emptyList())
                    }
                }

            val viewModel = SubjectDetailViewModel(repository, 7777L, FakeCollectionRepository(), FakeCommunityRepository())

            assertEquals(7777L, requestedDetailId)
            assertEquals(7777L, requestedEpisodesId)
            // 初次初始化不调用演职员接口
            assertNull(requestedCharactersId)
            assertNull(requestedPersonsId)
            assertNull(requestedRelationsId)

            viewModel.loadDetailsTabIfNeeded()
            assertEquals(7777L, requestedCharactersId)
            assertEquals(7777L, requestedPersonsId)
            assertEquals(7777L, requestedRelationsId)
        }

    @Test
    fun fetchError_setsErrorAndKeepsUiStateIntact() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    fetchSubjectDetailResult = { AppResult.Error(IllegalStateException("条目请求失败")) }
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            val state = viewModel.uiState.value

            assertFalse(state.isLoading)
            assertEquals("条目请求失败", state.error)
            assertNull(state.subject)
            assertTrue(state.episodes.isEmpty())
        }

    @Test
    fun streamUpdates_mergeIntoUiStateAfterFetchFailure() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    // 两次 fetch 均失败：数据只能经本地库流到达
                    fetchSubjectDetailResult = { AppResult.Error(IllegalStateException("离线")) }
                    fetchEpisodesResult = { AppResult.Error(IllegalStateException("离线")) }
                }
            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())

            repository.sendSubject(sampleSubject)
            repository.sendEpisodes(sampleSubject.id, sampleEpisodeList)

            val state = viewModel.uiState.value
            assertEquals(sampleSubject, state.subject)
            assertEquals(sampleEpisodeList, state.episodes)
            assertEquals("离线", state.error)
            assertFalse(state.isLoading)
        }

    @Test
    fun collectionStream_mergesIntoUiState() =
        runTest {
            val subjectRepo =
                FakeSubjectRepository().apply {
                    sendSubject(sampleSubject)
                    sendEpisodes(sampleSubject.id, sampleEpisodeList)
                }
            val collectionRepo =
                FakeCollectionRepository().apply {
                    sendCollection(sampleUserCollection)
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            val state = viewModel.uiState.value
            assertEquals(sampleUserCollection, state.collection)
            assertEquals(3, state.collection?.type)
        }

    @Test
    fun refresh_whenRemoteCollectionIsNull_clearsCollectionInUiState() =
        runTest {
            val subjectRepo =
                FakeSubjectRepository().apply {
                    sendSubject(sampleSubject)
                    sendEpisodes(sampleSubject.id, sampleEpisodeList)
                }
            val collectionRepo =
                FakeCollectionRepository().apply {
                    sendCollection(sampleUserCollection)
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            assertEquals(sampleUserCollection, viewModel.uiState.value.collection)

            // 当远端查到未收藏时（返回 Success(null)），刷新应正确将状态更新为 null
            collectionRepo.clearAllUserData()
            viewModel.refresh()
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.collection)
        }

    @Test
    fun updateCollectionStatus_callsRepository() =
        runTest {
            val subjectRepo = FakeSubjectRepository()
            val collectionRepo = FakeCollectionRepository()

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            viewModel.updateCollectionStatus(
                type = CollectionType.COLLECT,
                rate = 9,
                comment = "好看！",
                private = true,
            )

            assertEquals(1, collectionRepo.updateCollectionCallCount)
            val updatedState = viewModel.uiState.value
            assertEquals(CollectionType.COLLECT.value, updatedState.collection?.type)
            assertEquals(9, updatedState.collection?.rate)
            assertEquals("好看！", updatedState.collection?.comment)
        }

    @Test
    fun updateCollectionStatus_rollsBackOnFailure() =
        runTest {
            val subjectRepo = FakeSubjectRepository()
            val collectionRepo = FakeCollectionRepository()
            collectionRepo.updateCollectionResult = AppResult.Error(IllegalStateException("网络异常"))

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            viewModel.updateCollectionStatus(
                type = CollectionType.DOING,
            )

            // 失败后回滚为 null 并记录错误
            assertEquals(null, viewModel.uiState.value.collection)
            assertEquals("网络异常", viewModel.uiState.value.error)
        }

    @Test
    fun toggleWatching_quickTogglesCollectionStatus() =
        runTest {
            val subjectRepo = FakeSubjectRepository()
            val collectionRepo = FakeCollectionRepository()

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            // 1. 初始为 null，快捷追番即刻变为在看
            viewModel.toggleWatching()
            assertEquals(
                CollectionType.DOING.value,
                viewModel.uiState.value.collection
                    ?.type,
            )
            assertEquals(1, collectionRepo.updateCollectionCallCount)

            // 2. 再次点击，变为移出在看（DROPPED）
            viewModel.toggleWatching()
            assertEquals(
                CollectionType.DROPPED.value,
                viewModel.uiState.value.collection
                    ?.type,
            )
            assertEquals(2, collectionRepo.updateCollectionCallCount)
        }

    @Test
    fun toggleEpisodeWatched_callsRepository() =
        runTest {
            val subjectRepo = FakeSubjectRepository()
            val collectionRepo = FakeCollectionRepository()

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepo,
                    communityRepository = FakeCommunityRepository(),
                )

            viewModel.toggleEpisodeWatched(2001L, isWatched = true)
            assertEquals(1, collectionRepo.updateEpisodeCallCount)

            viewModel.toggleEpisodeWatched(2001L, isWatched = false)
            assertEquals(2, collectionRepo.updateEpisodeCallCount)
        }

    @Test
    fun refresh_refetchesSubjectDetailEpisodesCharactersPersonsRelations() =
        runTest {
            var detailCount = 0
            var charactersCount = 0
            var personsCount = 0
            var relationsCount = 0

            val repository =
                FakeSubjectRepository().apply {
                    fetchSubjectDetailResult = {
                        detailCount++
                        AppResult.Success(sampleSubject)
                    }
                    fetchCharactersResult = {
                        charactersCount++
                        AppResult.Success(sampleCharacterList)
                    }
                    fetchPersonsResult = {
                        personsCount++
                        AppResult.Success(samplePersonList)
                    }
                    fetchRelationsResult = {
                        relationsCount++
                        AppResult.Success(sampleRelationList)
                    }
                    sendSubject(sampleSubject)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())

            val initialDetail = detailCount
            assertEquals(0, charactersCount)
            assertEquals(0, personsCount)
            assertEquals(0, relationsCount)

            // 下拉刷新核心首屏数据（不产生次要接口请求）
            viewModel.refresh()
            assertEquals(initialDetail + 1, detailCount)
            assertEquals(0, charactersCount)
            assertEquals(0, personsCount)
            assertEquals(0, relationsCount)

            // 切入并强制刷新资料 Tab
            viewModel.loadDetailsTabIfNeeded(force = true)
            assertEquals(1, charactersCount)
            assertEquals(1, personsCount)
            assertEquals(1, relationsCount)
            assertEquals(sampleCharacterList, viewModel.uiState.value.characters)
            assertEquals(samplePersonList, viewModel.uiState.value.persons)
            assertEquals(sampleRelationList, viewModel.uiState.value.relations)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun refresh_cancelsOngoingRefreshJob_preventsConcurrentRace() =
        runTest {
            var fetchCount = 0
            val repository =
                FakeSubjectRepository().apply {
                    fetchSubjectDetailResult = {
                        fetchCount++
                        AppResult.Success(sampleSubject)
                    }
                    sendSubject(sampleSubject)
                }
            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            testScheduler.advanceUntilIdle()

            // 连续快速触发两次 refresh，旧任务被取消，最终正常完成
            viewModel.refresh()
            viewModel.refresh()
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(null, viewModel.uiState.value.error)
        }

    @Test
    fun updateCollectionStatus_preservesActualSubjectTypeInOptimisticCreation() =
        runTest {
            val bookSubject = sampleSubject.copy(id = 555L, type = 1) // 1 = BOOK
            val repository =
                FakeSubjectRepository().apply {
                    sendSubject(bookSubject)
                }
            val collectionRepository = FakeCollectionRepository()

            val viewModel = SubjectDetailViewModel(repository, bookSubject.id, collectionRepository, FakeCommunityRepository())
            testScheduler.advanceUntilIdle()

            viewModel.updateCollectionStatus(CollectionType.DOING)

            val optimistic = viewModel.uiState.value.collection
            assertEquals(1, optimistic?.subjectType)
            assertEquals(CollectionType.DOING.value, optimistic?.type)
        }

    @Test
    fun communityRepository_loadsCommentsAndTopicsIntoUiState() =
        runTest {
            val sampleComments =
                listOf(
                    SubjectComment(
                        id = 101L,
                        user = CommentUser(id = 1L, username = "testuser", nickname = "测试用户"),
                        rate = 8,
                        comment = "很棒的作品！",
                    ),
                )
            val sampleTopics =
                listOf(
                    SubjectTopic(
                        id = 201L,
                        title = "关于大结局的深度探讨",
                        creator = CommentUser(id = 2L, username = "analyst", nickname = "考据党"),
                        replyCount = 15,
                    ),
                )
            val communityRepo =
                FakeCommunityRepository().apply {
                    setSubjectComments(
                        sampleSubject.id,
                        SubjectCommentPage(total = 42, data = sampleComments),
                    )
                    setSubjectTopics(sampleSubject.id, sampleTopics)
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )
            testScheduler.advanceUntilIdle()

            // 首屏按需懒加载：初次不加载社区短评与讨论
            assertTrue(
                viewModel.uiState.value.subjectComments
                    .isEmpty(),
            )
            assertTrue(
                viewModel.uiState.value.subjectTopics
                    .isEmpty(),
            )

            // 切换至社区吐槽 Tab 后按需懒加载
            viewModel.loadCommunityTabIfNeeded()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sampleComments, state.subjectComments)
            assertEquals(42, state.subjectCommentTotal)
            assertEquals(sampleTopics, state.subjectTopics)
        }

    @Test
    fun loadEpisodeComments_populatesEpisodeCommentsMap() =
        runTest {
            val epId = 9999L
            val comments =
                listOf(
                    EpisodeComment(
                        id = 1L,
                        user = CommentUser(id = 1L, username = "animefan", nickname = "漫迷"),
                        content = "这一集神展开！",
                    ),
                )
            val communityRepo =
                FakeCommunityRepository().apply {
                    setEpisodeComments(epId, comments)
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )

            viewModel.loadEpisodeComments(epId)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(comments, state.episodeComments[epId])
            assertFalse(state.isEpisodeCommentsLoading)
        }

    @Test
    fun loadCommunityTabIfNeeded_onAllNetworkErrors_doesNotLockLoadedStateAndAllowsRetry() =
        runTest {
            val communityRepo =
                FakeCommunityRepository().apply {
                    getSubjectCommentsResult = AppResult.Error(IllegalStateException("网络连接超时"))
                    getSubjectTopicsResult = AppResult.Error(IllegalStateException("网络连接超时"))
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )

            // 初次切 Tab，全部网络失败
            viewModel.loadCommunityTabIfNeeded()
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isCommunityLoading)
            assertEquals(1, communityRepo.getSubjectCommentsCallCount)
            assertEquals(1, communityRepo.getSubjectTopicsCallCount)

            // 再次切回 Tab，不应被锁定，应能发起二次重试
            viewModel.loadCommunityTabIfNeeded()
            testScheduler.advanceUntilIdle()

            assertEquals(2, communityRepo.getSubjectCommentsCallCount)
            assertEquals(2, communityRepo.getSubjectTopicsCallCount)
        }

    @Test
    fun loadEpisodeComments_onNetworkError_doesNotCacheEmptyListAndAllowsRetry() =
        runTest {
            val epId = 777L
            val comments =
                listOf(
                    EpisodeComment(
                        id = 1L,
                        user = CommentUser(id = 1L, username = "otaku", nickname = "宅友"),
                        content = "精彩绝伦！",
                    ),
                )
            val communityRepo =
                FakeCommunityRepository().apply {
                    getEpisodeCommentsResult = AppResult.Error(IllegalStateException("网络异常"))
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )

            // 首次请求失败
            viewModel.loadEpisodeComments(epId)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isEpisodeCommentsLoading)
            assertFalse(
                viewModel.uiState.value.episodeComments
                    .containsKey(epId),
            )
            assertEquals(1, communityRepo.getEpisodeCommentsCallCount)

            // 网络恢复，二次点击应重新发起请求并成功写入缓存
            communityRepo.getEpisodeCommentsResult = AppResult.Success(comments)
            viewModel.loadEpisodeComments(epId)
            testScheduler.advanceUntilIdle()

            assertEquals(2, communityRepo.getEpisodeCommentsCallCount)
            assertEquals(comments, viewModel.uiState.value.episodeComments[epId])
        }

    @Test
    fun loadMoreSubjectComments_appendsNewCommentsAndUpdatesPaginationState() =
        runTest {
            val initialComment = SubjectComment(id = 1L, comment = "第一条短评")
            val nextComment = SubjectComment(id = 2L, comment = "第二条短评")
            val communityRepo =
                FakeCommunityRepository().apply {
                    setSubjectComments(
                        subjectId = sampleSubject.id,
                        page = SubjectCommentPage(total = 2, data = listOf(initialComment)),
                    )
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )
            viewModel.loadCommunityTabIfNeeded()
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(initialComment), viewModel.uiState.value.subjectComments)
            assertTrue(viewModel.uiState.value.hasMoreComments)

            // 模拟第二页数据就绪并加载
            communityRepo.setSubjectComments(
                subjectId = sampleSubject.id,
                page = SubjectCommentPage(total = 2, data = listOf(nextComment)),
            )

            viewModel.loadMoreSubjectComments()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.subjectComments.size)
            assertEquals(listOf(initialComment, nextComment), state.subjectComments)
            assertFalse(state.isLoadingMoreComments)
            assertFalse(state.hasMoreComments)
        }

    @Test
    fun loadCharacterDetail_populatesCharacterAndWorksState() =
        runTest {
            val charId = 123L
            val charDetail = CharacterDetail(id = charId, name = "フリーレン", summary = "千年精灵魔法使")
            val works = listOf(RelatedWork(id = 100L, name = "葬送のフリーレン", staff = "主角"))
            val repository =
                FakeSubjectRepository().apply {
                    sendCharacterDetail(charDetail)
                    sendCharacterSubjects(charId, works)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            viewModel.loadCharacterDetail(charId)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(charDetail, state.selectedCharacterDetail)
            assertEquals(works, state.selectedCharacterWorks)
            assertFalse(state.isLoadingEntityDetail)

            viewModel.clearEntityDetail()
            val clearedState = viewModel.uiState.value
            assertNull(clearedState.selectedCharacterDetail)
            assertTrue(clearedState.selectedCharacterWorks.isEmpty())
        }

    @Test
    fun loadCharacterDetail_clearsPreviousPersonAndCharacterState() =
        runTest {
            val charId = 123L
            val charDetail = CharacterDetail(id = charId, name = "角色1")
            val charWorks = listOf(RelatedWork(id = 10L, name = "作品1", staff = "主角"))
            val personId = 456L
            val personDetail = PersonDetail(id = personId, name = "種﨑敦美", career = listOf("seiyu"))
            val personWorks = listOf(RelatedWork(id = 100L, name = "作品2", staff = "声优"))
            val repository =
                FakeSubjectRepository().apply {
                    sendCharacterDetail(charDetail)
                    sendCharacterSubjects(charId, charWorks)
                    sendPersonDetail(personDetail)
                    sendPersonSubjects(personId, personWorks)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())

            // 1. 先加载人物详情
            viewModel.loadPersonDetail(personId)
            testScheduler.advanceUntilIdle()
            assertEquals(personDetail, viewModel.uiState.value.selectedPersonDetail)
            assertEquals(personWorks, viewModel.uiState.value.selectedPersonWorks)

            // 2. 切换加载角色详情，人物状态和作品必须被清空
            viewModel.loadCharacterDetail(charId)
            testScheduler.advanceUntilIdle()
            assertEquals(charDetail, viewModel.uiState.value.selectedCharacterDetail)
            assertEquals(charWorks, viewModel.uiState.value.selectedCharacterWorks)
            assertNull(viewModel.uiState.value.selectedPersonDetail)
            assertTrue(
                viewModel.uiState.value.selectedPersonWorks
                    .isEmpty(),
            )

            // 3. 再次切回人物详情，角色状态和作品必须被清空
            viewModel.loadPersonDetail(personId)
            testScheduler.advanceUntilIdle()
            assertEquals(personDetail, viewModel.uiState.value.selectedPersonDetail)
            assertEquals(personWorks, viewModel.uiState.value.selectedPersonWorks)
            assertNull(viewModel.uiState.value.selectedCharacterDetail)
            assertTrue(
                viewModel.uiState.value.selectedCharacterWorks
                    .isEmpty(),
            )
        }

    @Test
    fun loadPersonDetail_populatesPersonAndWorksState() =
        runTest {
            val personId = 456L
            val personDetail = PersonDetail(id = personId, name = "種﨑敦美", career = listOf("seiyu"))
            val works = listOf(RelatedWork(id = 100L, name = "葬送のフリーレン", staff = "声优"))
            val repository =
                FakeSubjectRepository().apply {
                    sendPersonDetail(personDetail)
                    sendPersonSubjects(personId, works)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            viewModel.loadPersonDetail(personId)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(personDetail, state.selectedPersonDetail)
            assertEquals(works, state.selectedPersonWorks)
            assertFalse(state.isLoadingEntityDetail)

            viewModel.clearEntityDetail()
            val clearedState = viewModel.uiState.value
            assertNull(clearedState.selectedPersonDetail)
            assertTrue(clearedState.selectedPersonWorks.isEmpty())
        }

    @Test
    fun loadPersonDetail_aggregatesDuplicateSubjectsAndCombinesStaff() =
        runTest {
            val personId = 3083L
            val personDetail = PersonDetail(id = personId, name = "吉浦康裕")
            val rawWorks =
                listOf(
                    RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "原作"),
                    RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "导演"),
                    RelatedWork(id = 29414L, name = "サカサマのパテマ", staff = "脚本"),
                    RelatedWork(id = 1001L, name = "イヴの時間", staff = "导演"),
                )
            val repository =
                FakeSubjectRepository().apply {
                    sendPersonDetail(personDetail)
                    sendPersonSubjects(personId, rawWorks)
                }

            val viewModel = SubjectDetailViewModel(repository, sampleSubject.id, FakeCollectionRepository(), FakeCommunityRepository())
            viewModel.loadPersonDetail(personId)
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.selectedPersonWorks.size)
            assertEquals(29414L, state.selectedPersonWorks[0].id)
            assertEquals("原作 / 导演 / 脚本", state.selectedPersonWorks[0].staff)
            assertEquals(1001L, state.selectedPersonWorks[1].id)
            assertEquals("导演", state.selectedPersonWorks[1].staff)
        }

    @Test
    fun initialLoad_onlyFetchesCoreData_andSkipsDetailsAndCommunity() =
        runTest {
            var detailCalls = 0
            var episodeCalls = 0
            var charCalls = 0
            var communityCalls = 0

            val subjectRepo =
                FakeSubjectRepository().apply {
                    fetchSubjectDetailResult = {
                        detailCalls++
                        AppResult.Success(sampleSubject)
                    }
                    fetchEpisodesResult = {
                        episodeCalls++
                        AppResult.Success(sampleEpisodeList)
                    }
                    fetchCharactersResult = {
                        charCalls++
                        AppResult.Success(sampleCharacterList)
                    }
                }
            val communityRepo =
                FakeCommunityRepository().apply {
                    // set comments
                    setSubjectComments(sampleSubject.id, SubjectCommentPage(total = 0, data = emptyList()))
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = communityRepo,
                )

            // 初次初始化：仅拉取条目和章节
            assertEquals(1, detailCalls)
            assertEquals(1, episodeCalls)
            assertEquals(0, charCalls)
            assertTrue(
                viewModel.uiState.value.characters
                    .isEmpty(),
            )
            assertTrue(
                viewModel.uiState.value.subjectComments
                    .isEmpty(),
            )
        }

    @Test
    fun lazyLoading_preventsDuplicateFetches() =
        runTest {
            var charCalls = 0
            val subjectRepo =
                FakeSubjectRepository().apply {
                    fetchCharactersResult = {
                        charCalls++
                        AppResult.Success(sampleCharacterList)
                    }
                }

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = subjectRepo,
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = FakeCommunityRepository(),
                )

            assertEquals(0, charCalls)

            // 第一次加载 Tab
            viewModel.loadDetailsTabIfNeeded()
            assertEquals(1, charCalls)

            // 重复调用不触发网络请求
            viewModel.loadDetailsTabIfNeeded()
            viewModel.loadDetailsTabIfNeeded()
            assertEquals(1, charCalls)

            // 强制刷新时才重新拉取
            viewModel.loadDetailsTabIfNeeded(force = true)
            assertEquals(2, charCalls)
        }

    @Test
    fun markWatchedUpTo_optimisticallyUpdatesProgressAndCallsRepository() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    sendSubject(sampleSubject)
                    sendEpisodes(sampleSubject.id, sampleEpisodeList)
                }
            val collectionRepository = FakeCollectionRepository()

            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = repository,
                    subjectId = sampleSubject.id,
                    collectionRepository = collectionRepository,
                    communityRepository = FakeCommunityRepository(),
                )
            testScheduler.advanceUntilIdle()

            // 针对第 2 话触发「看到本集」
            val targetEp = sampleEpisodeList.first { it.ep.toInt() == 2 }
            viewModel.markWatchedUpTo(targetEp)

            // 验证乐观更新状态
            val state = viewModel.uiState.value
            assertEquals(2, state.collection?.epStatus)
            assertEquals(CollectionType.DOING.value, state.collection?.type)

            testScheduler.advanceUntilIdle()
            assertEquals(1, collectionRepository.markEpisodesWatchedUpToCallCount)
        }

    @Test
    fun selectTab_switchesTabAndLoadsDetailsIfNeeded() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    sendSubject(sampleSubject)
                    sendEpisodes(sampleSubject.id, sampleEpisodeList)
                    sendCharacters(sampleSubject.id, sampleCharacterList)
                    sendPersons(sampleSubject.id, samplePersonList)
                    sendRelations(sampleSubject.id, sampleRelationList)
                }
            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = repository,
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = FakeCommunityRepository(),
                )

            assertEquals(SubjectDetailTab.EPISODES, viewModel.uiState.value.selectedTab)
            assertTrue(
                viewModel.uiState.value.characters
                    .isEmpty(),
            )

            viewModel.selectTab(SubjectDetailTab.DETAILS)
            assertEquals(SubjectDetailTab.DETAILS, viewModel.uiState.value.selectedTab)
            assertEquals(sampleCharacterList, viewModel.uiState.value.characters)
            assertEquals(samplePersonList, viewModel.uiState.value.persons)
            assertEquals(sampleRelationList, viewModel.uiState.value.relations)
        }

    @Test
    fun stateHoisting_controlsGridAndSheetVisibility() =
        runTest {
            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = FakeCommunityRepository(),
                )

            assertTrue(viewModel.uiState.value.isEpisodeGridView)
            assertFalse(viewModel.uiState.value.showCollectionSheet)

            viewModel.setEpisodeGridView(false)
            assertFalse(viewModel.uiState.value.isEpisodeGridView)

            viewModel.setCollectionSheetVisible(true)
            assertTrue(viewModel.uiState.value.showCollectionSheet)

            viewModel.setCollectionSheetVisible(false)
            assertFalse(viewModel.uiState.value.showCollectionSheet)
        }

    @Test
    fun stateHoisting_openAndDismissEpisodeDetail() =
        runTest {
            val targetEpisode = sampleEpisodeList.first()
            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = FakeSubjectRepository(),
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = FakeCommunityRepository(),
                )

            assertNull(viewModel.uiState.value.selectedEpisodeForDetail)

            viewModel.openEpisodeDetail(targetEpisode)
            assertEquals(targetEpisode, viewModel.uiState.value.selectedEpisodeForDetail)

            viewModel.dismissEpisodeDetail()
            assertNull(viewModel.uiState.value.selectedEpisodeForDetail)
        }

    @Test
    fun stateHoisting_openAndDismissEntityDetail() =
        runTest {
            val repository =
                FakeSubjectRepository().apply {
                    sendCharacters(sampleSubject.id, sampleCharacterList)
                    sendPersons(sampleSubject.id, samplePersonList)
                    fetchCharacterDetailResult = {
                        AppResult.Success(
                            CharacterDetail(
                                id = 101L,
                                name = "Test Character",
                                summary = "Character Summary",
                            ),
                        )
                    }
                }
            val viewModel =
                SubjectDetailViewModel(
                    subjectRepository = repository,
                    subjectId = sampleSubject.id,
                    collectionRepository = FakeCollectionRepository(),
                    communityRepository = FakeCommunityRepository(),
                )

            viewModel.loadDetailsTabIfNeeded()
            viewModel.openCharacterDetail(101L)

            assertEquals(
                101L,
                viewModel.uiState.value.activeCharacter
                    ?.id,
            )
            assertNull(viewModel.uiState.value.activePerson)

            testScheduler.advanceUntilIdle()
            assertEquals(
                "Test Character",
                viewModel.uiState.value.selectedCharacterDetail
                    ?.name,
            )

            // 切换打开 Person，应清空 Character
            viewModel.openPersonDetail(201L)
            assertEquals(
                201L,
                viewModel.uiState.value.activePerson
                    ?.id,
            )
            assertNull(viewModel.uiState.value.activeCharacter)
            assertNull(viewModel.uiState.value.selectedCharacterDetail)

            // 关闭 entity detail
            viewModel.dismissEntityDetail()
            assertNull(viewModel.uiState.value.activeCharacter)
            assertNull(viewModel.uiState.value.activePerson)
            assertNull(viewModel.uiState.value.selectedPersonDetail)
        }
}
