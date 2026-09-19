package com.infinitezerone.minibgm.core.testing.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.WebViewResolveRepository
import com.infinitezerone.minibgm.core.model.PlayableSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWebViewResolveRepository : WebViewResolveRepository {
    private val results = MutableStateFlow<Map<Long, List<PlayableSource>>>(emptyMap())

    var deepResolveResult: AppResult<List<PlayableSource>>? = null
    val deepResolveCalls = mutableListOf<Long>()

    fun setPlayableSources(
        subjectId: Long,
        sources: List<PlayableSource>,
    ) {
        results.value = results.value + (subjectId to sources)
    }

    override suspend fun deepResolve(subjectId: Long): AppResult<List<PlayableSource>> {
        deepResolveCalls += subjectId
        deepResolveResult?.let { return it }
        return AppResult.Success(results.value[subjectId].orEmpty())
    }
}
