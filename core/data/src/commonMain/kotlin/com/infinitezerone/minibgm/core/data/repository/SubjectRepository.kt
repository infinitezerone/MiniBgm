package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.common.asAppResult
import com.infinitezerone.minibgm.core.database.dao.EpisodeDao
import com.infinitezerone.minibgm.core.database.dao.SubjectDao
import com.infinitezerone.minibgm.core.database.entity.EpisodeEntity
import com.infinitezerone.minibgm.core.database.entity.SubjectEntity
import com.infinitezerone.minibgm.core.model.CharacterDetail
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.PersonDetail
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.RelatedWork
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectCharacter
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.SubjectPerson
import com.infinitezerone.minibgm.core.model.SubjectRelation
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.network.BangumiApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

private val repositoryJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

class SubjectRepositoryImpl(
    private val apiService: BangumiApiService,
    private val subjectDao: SubjectDao,
    private val episodeDao: EpisodeDao,
) : SubjectRepository {
    override fun getSubjectStream(id: Long): Flow<Subject?> =
        subjectDao.getSubjectById(id).map { entity ->
            entity?.let {
                val ratingCount =
                    if (it.ratingCountJson.isNotBlank()) {
                        try {
                            repositoryJson.decodeFromString<Map<String, Int>>(it.ratingCountJson)
                        } catch (e: Exception) {
                            emptyMap()
                        }
                    } else {
                        emptyMap()
                    }
                val tagsList =
                    if (it.tagsJson.isNotBlank()) {
                        try {
                            repositoryJson.decodeFromString<List<Tag>>(it.tagsJson)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                val collectionCount =
                    if (it.collectionWish > 0 ||
                        it.collectionCollect > 0 ||
                        it.collectionDoing > 0 ||
                        it.collectionOnHold > 0 ||
                        it.collectionDropped > 0
                    ) {
                        CollectionCount(
                            wish = it.collectionWish,
                            collect = it.collectionCollect,
                            doing = it.collectionDoing,
                            onHold = it.collectionOnHold,
                            dropped = it.collectionDropped,
                        )
                    } else {
                        null
                    }
                Subject(
                    id = it.id,
                    type = it.type,
                    name = it.name,
                    nameCn = it.nameCn,
                    summary = it.summary,
                    date = it.date,
                    eps = it.eps,
                    totalEpisodes = it.totalEpisodes,
                    images = SubjectImages(large = it.coverUrl),
                    rating =
                        Rating(
                            score = it.ratingScore,
                            rank = it.ratingRank,
                            total = it.ratingTotal,
                            count = ratingCount,
                        ),
                    collection = collectionCount,
                    tags = tagsList,
                )
            }
        }

    override suspend fun fetchSubjectDetail(id: Long): AppResult<Subject> =
        try {
            val subject = apiService.getSubject(id)
            val ratingCountJson =
                if (!subject.rating?.count.isNullOrEmpty()) {
                    try {
                        repositoryJson.encodeToString(subject.rating!!.count)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
            val tagsJson =
                if (subject.tags.isNotEmpty()) {
                    try {
                        repositoryJson.encodeToString(subject.tags)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
            val entity =
                SubjectEntity(
                    id = subject.id,
                    type = subject.type,
                    name = subject.name,
                    nameCn = subject.nameCn,
                    summary = subject.summary,
                    date = subject.date,
                    eps = subject.eps,
                    totalEpisodes = subject.totalEpisodes,
                    coverUrl = subject.images?.bestImage ?: "",
                    ratingScore = subject.rating?.score ?: 0.0,
                    ratingRank = subject.rating?.rank ?: 0,
                    ratingTotal = subject.rating?.total ?: 0,
                    ratingCountJson = ratingCountJson,
                    collectionWish = subject.collection?.wish ?: 0,
                    collectionCollect = subject.collection?.collect ?: 0,
                    collectionDoing = subject.collection?.doing ?: 0,
                    collectionOnHold = subject.collection?.onHold ?: 0,
                    collectionDropped = subject.collection?.dropped ?: 0,
                    tagsJson = tagsJson,
                )
            subjectDao.insertSubject(entity)
            AppResult.Success(subject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e)
        }

    override fun getEpisodesStream(subjectId: Long): Flow<List<Episode>> =
        episodeDao.getEpisodesBySubjectId(subjectId).map { entities ->
            entities.map {
                Episode(
                    id = it.id,
                    sort = it.sort,
                    ep = it.ep,
                    name = it.name,
                    nameCn = it.nameCn,
                    duration = it.duration,
                    airdate = it.airdate,
                    type = it.type,
                    desc = it.desc,
                    comment = it.comment,
                )
            }
        }

    override suspend fun fetchEpisodes(subjectId: Long): AppResult<List<Episode>> =
        try {
            val response = apiService.getEpisodes(subjectId, limit = 100)
            val entities =
                response.data.map {
                    EpisodeEntity(
                        id = it.id,
                        subjectId = subjectId,
                        sort = it.sort,
                        ep = it.ep,
                        name = it.name,
                        nameCn = it.nameCn,
                        duration = it.duration,
                        airdate = it.airdate,
                        type = it.type,
                        desc = it.desc,
                        comment = it.comment,
                    )
                }
            episodeDao.insertEpisodes(entities)
            AppResult.Success(response.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Error(e)
        }

    override suspend fun fetchCharacters(subjectId: Long): AppResult<List<SubjectCharacter>> =
        asAppResult { apiService.getSubjectCharacters(subjectId) }

    override suspend fun fetchCharacterDetail(id: Long): AppResult<CharacterDetail> = asAppResult { apiService.getCharacter(id) }

    override suspend fun fetchCharacterSubjects(id: Long): AppResult<List<RelatedWork>> =
        asAppResult { apiService.getCharacterSubjects(id) }

    override suspend fun fetchPersons(subjectId: Long): AppResult<List<SubjectPerson>> =
        asAppResult { apiService.getSubjectPersons(subjectId) }

    override suspend fun fetchPersonDetail(id: Long): AppResult<PersonDetail> = asAppResult { apiService.getPerson(id) }

    override suspend fun fetchPersonSubjects(id: Long): AppResult<List<RelatedWork>> = asAppResult { apiService.getPersonSubjects(id) }

    override suspend fun fetchRelations(subjectId: Long): AppResult<List<SubjectRelation>> =
        asAppResult { apiService.getSubjectRelations(subjectId) }
}
