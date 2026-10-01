package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.asAppResult
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.aggregateBySubject
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SubjectRepository {
    fun getSubjectStream(id: Long): Flow<Subject?>

    /** 同步获取内存中已缓存的条目详情（未命中返回 null） */
    fun getCachedSubject(id: Long): Subject? = null

    suspend fun fetchSubjectDetail(id: Long): AppResult<Subject>

    fun getEpisodesStream(subjectId: Long): Flow<List<Episode>>

    /** 同步获取内存中已缓存的分集列表（未命中返回 null） */
    fun getCachedEpisodes(subjectId: Long): List<Episode>? = null

    /** 载入分集首屏（[descending] 决定从“最早”还是“最新”一端开始），重置该条目的累积缓存 */
    suspend fun loadEpisodes(
        subjectId: Long,
        descending: Boolean = false,
    ): AppResult<List<Episode>>

    /** 按当前方向续拉下一屏，累积进缓存；返回是否拿到了新数据 */
    suspend fun loadMoreEpisodes(
        subjectId: Long,
        descending: Boolean = false,
    ): AppResult<Boolean>

    /** 该条目是否还有未加载的分集（响应式） */
    fun hasMoreEpisodesStream(subjectId: Long): Flow<Boolean>

    /** 兼容旧调用：载入分集首屏（升序）并返回列表 */
    suspend fun fetchEpisodes(subjectId: Long): AppResult<List<Episode>>

    /** 分集分页窗口大小 */
    companion object {
        const val EPISODE_PAGE_SIZE = 100
    }

    suspend fun fetchCharacters(subjectId: Long): AppResult<List<SubjectCharacter>>

    suspend fun fetchCharacterDetail(id: Long): AppResult<CharacterDetail>

    suspend fun fetchCharacterSubjects(id: Long): AppResult<List<RelatedWork>>

    suspend fun fetchPersons(subjectId: Long): AppResult<List<SubjectPerson>>

    suspend fun fetchPersonDetail(id: Long): AppResult<PersonDetail>

    suspend fun fetchPersonSubjects(id: Long): AppResult<List<RelatedWork>>

    suspend fun fetchRelations(subjectId: Long): AppResult<List<SubjectRelation>>
}

class SubjectRepositoryImpl(
    private val apiService: BangumiApiService,
    private val maxMemoryEntries: Int = DEFAULT_MAX_ENTRIES,
) : SubjectRepository {
    private val cacheMutex = Mutex()
    private val subjectsState = MutableStateFlow<Map<Long, Subject>>(emptyMap())
    private val episodesState = MutableStateFlow<Map<Long, List<Episode>>>(emptyMap())
    private val episodesHasMoreState = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val episodeCursors = mutableMapOf<Long, EpisodeCursor>()
    private val subjectAccessOrder = mutableListOf<Long>()
    private val episodeAccessOrder = mutableListOf<Long>()

    private data class EpisodeCursor(
        val total: Int,
        /** 升序：下一个待拉 offset；降序：本屏起始 offset */
        val nextOffset: Int,
        val descending: Boolean,
    )

    private fun hasMore(cursor: EpisodeCursor): Boolean = if (cursor.descending) cursor.nextOffset > 0 else cursor.nextOffset < cursor.total

    override fun getSubjectStream(id: Long): Flow<Subject?> =
        subjectsState
            .map { it[id] }
            .distinctUntilChanged()

    override suspend fun fetchSubjectDetail(id: Long): AppResult<Subject> {
        getCachedSubject(id)?.let { return AppResult.Success(it) }
        return try {
            val subject = apiService.getSubject(id)
            cacheMutex.withLock {
                subjectAccessOrder.remove(id)
                subjectAccessOrder.add(id)
                val newMap = subjectsState.value.toMutableMap()
                newMap[id] = subject
                while (subjectAccessOrder.size > maxMemoryEntries) {
                    val evictedId = subjectAccessOrder.removeAt(0)
                    newMap.remove(evictedId)
                }
                subjectsState.value = newMap
            }
            AppResult.Success(subject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("获取条目详情"))
        }
    }

    override fun getCachedSubject(id: Long): Subject? = subjectsState.value[id]

    override fun getCachedEpisodes(subjectId: Long): List<Episode>? = episodesState.value[subjectId]

    override fun getEpisodesStream(subjectId: Long): Flow<List<Episode>> =
        episodesState
            .map { it[subjectId].orEmpty() }
            .distinctUntilChanged()

    override fun hasMoreEpisodesStream(subjectId: Long): Flow<Boolean> =
        episodesHasMoreState
            .map { it[subjectId] ?: false }
            .distinctUntilChanged()

    override suspend fun fetchEpisodes(subjectId: Long): AppResult<List<Episode>> =
        getCachedEpisodes(subjectId)?.takeIf { it.isNotEmpty() }?.let { AppResult.Success(it) }
            ?: loadEpisodes(subjectId, descending = false)

    override suspend fun loadEpisodes(
        subjectId: Long,
        descending: Boolean,
    ): AppResult<List<Episode>> =
        try {
            val limit = SubjectRepository.EPISODE_PAGE_SIZE
            // 降序需要先知道 total 才能从末页开始（只取 1 条探 total，不污染缓存）
            val total =
                if (descending) {
                    apiService.getEpisodes(subjectId, limit = 1, offset = 0).total
                } else {
                    -1
                }
            val offset = if (descending) maxOf(0, total - limit) else 0
            val page = apiService.getEpisodes(subjectId, limit = limit, offset = offset)
            val resolvedTotal = if (descending) total else page.total
            val cursor =
                EpisodeCursor(
                    total = resolvedTotal,
                    nextOffset = if (descending) offset else page.data.size,
                    descending = descending,
                )
            cacheEpisodePage(subjectId, page.data, cursor, reset = true)
            AppResult.Success(page.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("获取剧集列表"))
        }

    override suspend fun loadMoreEpisodes(
        subjectId: Long,
        descending: Boolean,
    ): AppResult<Boolean> =
        try {
            val cursor = cacheMutex.withLock { episodeCursors[subjectId] }
            if (cursor == null || !hasMore(cursor)) {
                AppResult.Success(false)
            } else {
                val limit = SubjectRepository.EPISODE_PAGE_SIZE
                val offset = if (descending) maxOf(0, cursor.nextOffset - limit) else cursor.nextOffset
                val page = apiService.getEpisodes(subjectId, limit = limit, offset = offset)
                val newCursor =
                    EpisodeCursor(
                        total = cursor.total,
                        nextOffset = if (descending) offset else cursor.nextOffset + page.data.size,
                        descending = descending,
                    )
                cacheEpisodePage(subjectId, page.data, newCursor, reset = false)
                AppResult.Success(page.data.isNotEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("获取剧集列表"))
        }

    private suspend fun cacheEpisodePage(
        subjectId: Long,
        page: List<Episode>,
        cursor: EpisodeCursor,
        reset: Boolean,
    ) {
        cacheMutex.withLock {
            episodeAccessOrder.remove(subjectId)
            episodeAccessOrder.add(subjectId)
            val newMap = episodesState.value.toMutableMap()
            val existing = if (reset) emptyList() else newMap[subjectId].orEmpty()
            newMap[subjectId] = (existing + page).distinctBy { it.id }.sortedBy { it.sort }
            val newHasMore = episodesHasMoreState.value.toMutableMap()
            newHasMore[subjectId] = hasMore(cursor)
            episodeCursors[subjectId] = cursor
            while (episodeAccessOrder.size > maxMemoryEntries) {
                val evictedId = episodeAccessOrder.removeAt(0)
                newMap.remove(evictedId)
                newHasMore.remove(evictedId)
                episodeCursors.remove(evictedId)
            }
            episodesState.value = newMap
            episodesHasMoreState.value = newHasMore
        }
    }

    override suspend fun fetchCharacters(subjectId: Long): AppResult<List<SubjectCharacter>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取角色列表") }) {
            apiService.getSubjectCharacters(subjectId)
        }

    override suspend fun fetchCharacterDetail(id: Long): AppResult<CharacterDetail> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取角色详情") }) {
            apiService.getCharacter(id)
        }

    override suspend fun fetchCharacterSubjects(id: Long): AppResult<List<RelatedWork>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取角色参演作品") }) {
            apiService.getCharacterSubjects(id).aggregateBySubject()
        }

    override suspend fun fetchPersons(subjectId: Long): AppResult<List<SubjectPerson>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取演职员列表") }) {
            apiService.getSubjectPersons(subjectId)
        }

    override suspend fun fetchPersonDetail(id: Long): AppResult<PersonDetail> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取演职员详情") }) {
            apiService.getPerson(id)
        }

    override suspend fun fetchPersonSubjects(id: Long): AppResult<List<RelatedWork>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取演职员作品") }) {
            apiService.getPersonSubjects(id).aggregateBySubject()
        }

    override suspend fun fetchRelations(subjectId: Long): AppResult<List<SubjectRelation>> =
        asAppResult(errorMessage = { it.toUserFriendlyMessage("获取关联作品") }) {
            apiService.getSubjectRelations(subjectId)
        }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 30
    }
}
