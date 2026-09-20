package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import com.infinitezerone.minibgm.core.model.PendingAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * HITL 待确认操作执行器。
 * 当用户在 UI 界面显式同意/确认 [PendingAction] 提案后，
 * 由此执行器负责将变更同步至 [CollectionRepository]。
 * 严格遵循 AGENTS.md 准则：关键写操作必须在 NonCancellable 下执行。
 */
interface PendingActionExecutor {
    suspend fun execute(action: PendingAction): AppResult<Unit>
}

class DefaultPendingActionExecutor(
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: com.infinitezerone.minibgm.core.data.repository.SettingsRepository? = null,
) : PendingActionExecutor {
    override suspend fun execute(action: PendingAction): AppResult<Unit> =
        withContext(NonCancellable) {
            try {
                when (action) {
                    is PendingAction.UpdateCollection -> {
                        if (action.subjectId <= 0) {
                            return@withContext AppResult.Error(
                                IllegalArgumentException("Invalid subject ID: ${action.subjectId}"),
                            )
                        }
                        collectionRepository.updateCollectionStatus(
                            subjectId = action.subjectId,
                            type = action.collectionType,
                            rate = action.rating?.coerceIn(1, 10),
                            comment = action.comment,
                            private = action.isPrivate,
                        )
                    }
                    is PendingAction.UpdateEpisode -> {
                        if (action.subjectId <= 0) {
                            return@withContext AppResult.Error(
                                IllegalArgumentException("Invalid subject ID: ${action.subjectId}"),
                            )
                        }
                        if (action.episodeNumber <= 0) {
                            return@withContext AppResult.Error(
                                IllegalArgumentException("Invalid episode number: ${action.episodeNumber}"),
                            )
                        }
                        collectionRepository.updateEpisodeStatus(
                            subjectId = action.subjectId,
                            episodeId = null,
                            isWatched = action.isWatched,
                            epNumber = action.episodeNumber,
                        )
                    }
                    is PendingAction.ImportPlaybackRules -> {
                        if (action.rules.isEmpty()) {
                            return@withContext AppResult.Error(
                                IllegalArgumentException("No playback rules provided to import"),
                            )
                        }
                        settingsRepository?.importPlaybackRules(action.rules)
                        AppResult.Success(Unit)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Error(e)
            }
        }
}
