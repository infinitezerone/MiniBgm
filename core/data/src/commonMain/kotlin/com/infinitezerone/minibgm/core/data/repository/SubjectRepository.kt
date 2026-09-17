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

    suspend fun fetchSubjectDetail(id: Long): AppResult<Subject>

    fun getEpisodesStream(subjectId: Long): Flow<List<Episode>>

    suspend fun fetchEpisodes(subjectId: Long): AppResult<List<Episode>>

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
    private val subjectAccessOrder = mutableListOf<Long>()
    private val episodeAccessOrder = mutableListOf<Long>()

    override fun getSubjectStream(id: Long): Flow<Subject?> =
        subjectsState
            .map { it[id] }
            .distinctUntilChanged()

    override suspend fun fetchSubjectDetail(id: Long): AppResult<Subject> =
        try {
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

    override fun getEpisodesStream(subjectId: Long): Flow<List<Episode>> =
        episodesState
            .map { it[subjectId].orEmpty() }
            .distinctUntilChanged()

    override suspend fun fetchEpisodes(subjectId: Long): AppResult<List<Episode>> =
        try {
            val response = apiService.getEpisodes(subjectId, limit = 100)
            cacheMutex.withLock {
                episodeAccessOrder.remove(subjectId)
                episodeAccessOrder.add(subjectId)
                val newMap = episodesState.value.toMutableMap()
                newMap[subjectId] = response.data
                while (episodeAccessOrder.size > maxMemoryEntries) {
                    val evictedId = episodeAccessOrder.removeAt(0)
                    newMap.remove(evictedId)
                }
                episodesState.value = newMap
            }
            AppResult.Success(response.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e, e.toUserFriendlyMessage("获取剧集列表"))
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
