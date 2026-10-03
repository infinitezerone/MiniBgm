package com.infinitezerone.minibgm.feature.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** 分页取页信号：`Initial` 是进页 / 查询换挡时的自动首取，其余由用户意图触发 */
internal sealed interface PagingSignal {
    data object Initial : PagingSignal

    data object Retry : PagingSignal

    data object Refresh : PagingSignal

    data object More : PagingSignal
}

/** 三条用户意图触发流的公共载体：重试 / 下拉刷新 / 触底加载更多 */
internal class PagingTriggers {
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loadMoreTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 汇成信号流；收集方在进页 / 换挡后自动先收到一次 [PagingSignal.Initial] */
    fun signals(): Flow<PagingSignal> =
        merge(
            retryTrigger.map { PagingSignal.Retry },
            refreshTrigger.map { PagingSignal.Refresh },
            loadMoreTrigger.map { PagingSignal.More },
        ).onStart { emit(PagingSignal.Initial) }

    fun retry() {
        retryTrigger.tryEmit(Unit)
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun loadMore() {
        loadMoreTrigger.tryEmit(Unit)
    }
}
