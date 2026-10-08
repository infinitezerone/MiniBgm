package com.infinitezerone.minibgm.feature.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.updateAll
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.CollectionRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * 桌面小组件快捷打卡回调：
 * 用户在桌面点击条目的「+1」按钮时，在后台将该集标记为已看过，并同步刷新小组件。
 */
class CheckInEpisodeCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val subjectId = parameters[SUBJECT_ID_KEY] ?: return
        val episode = parameters[EPISODE_KEY] ?: return
        val koin = GlobalContext.getOrNull() ?: return
        val collectionRepo = koin.getOrNull<CollectionRepository>() ?: return

        val result =
            withContext(NonCancellable) {
                collectionRepo.updateEpisodeStatus(
                    subjectId = subjectId,
                    episodeId = null,
                    isWatched = true,
                    epNumber = episode,
                )
            }

        when (result) {
            is AppResult.Success -> {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.widget_check_in_success, episode),
                        Toast.LENGTH_SHORT,
                    ).show()
                ScheduleWidget().updateAll(context)
            }
            is AppResult.Error -> {
                Toast
                    .makeText(
                        context,
                        result.message,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            else -> Unit
        }
    }

    companion object {
        val SUBJECT_ID_KEY = ActionParameters.Key<Long>("subject_id")
        val EPISODE_KEY = ActionParameters.Key<Int>("episode_number")

        fun createAction(
            subjectId: Long,
            episode: Int,
        ) = actionRunCallback<CheckInEpisodeCallback>(
            actionParametersOf(
                SUBJECT_ID_KEY to subjectId,
                EPISODE_KEY to episode,
            ),
        )
    }
}
