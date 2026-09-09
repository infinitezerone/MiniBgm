package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SearchRepository {
    /** 搜索条目（动画、书籍、音乐、游戏、三次元，支持服务端全量维度排序） */
    suspend fun searchSubjects(
        query: String,
        type: Int = 0,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): AppResult<SearchResult>

    /** 高级多维搜索与探索条目 (POST /v0/search/subjects，API 限制单页最大 20 条) */
    suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int = 20,
        offset: Int = 0,
    ): AppResult<List<Subject>>

    /** 观察本地搜索历史列表（按最近使用降序） */
    fun getSearchHistory(): Flow<List<String>>

    /** 添加/更新一条搜索历史 */
    suspend fun addSearchHistory(query: String)

    /** 移除单条搜索历史 */
    suspend fun removeSearchHistory(query: String)

    /** 清空所有搜索历史 */
    suspend fun clearSearchHistory()
}

class SearchRepositoryImpl(
    private val apiService: BangumiApiService,
    private val userPreferences: UserPreferencesDataSource,
) : SearchRepository {
    override fun getSearchHistory(): Flow<List<String>> = userPreferences.userPreferences.map { it.searchHistory }

    override suspend fun addSearchHistory(query: String) {
        withContext(NonCancellable) {
            userPreferences.addSearchHistory(query)
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        withContext(NonCancellable) {
            userPreferences.removeSearchHistory(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(NonCancellable) {
            userPreferences.clearSearchHistory()
        }
    }

    override suspend fun searchSubjects(
        query: String,
        type: Int,
        sort: String?,
        limit: Int,
        offset: Int,
    ): AppResult<SearchResult> {
        if (query.isBlank()) return AppResult.Success(SearchResult())
        return try {
            val response =
                apiService.searchSubjectsAdvanced(
                    request =
                        SearchSubjectsRequest(
                            keyword = query,
                            sort = sort?.ifBlank { null },
                            filter = if (type > 0) SearchFilter(type = listOf(type)) else null,
                        ),
                    limit = limit,
                    offset = offset,
                )
            AppResult.Success(SearchResult(total = response.total, list = response.data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            // 若高级搜索异常，降级回退至旧版搜索接口
            try {
                val legacy =
                    apiService.searchSubjects(
                        keyword = query,
                        type = type,
                        limit = limit,
                        offset = offset,
                    )
                AppResult.Success(SearchResult(total = legacy.results, list = legacy.list))
            } catch (fallbackCe: CancellationException) {
                throw fallbackCe
            } catch (fallbackEx: Exception) {
                AppResult.Error(e, "搜索失败：${e.message}")
            }
        } catch (e: Exception) {
            AppResult.Error(e, "搜索异常：${e.message}")
        }
    }

    override suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int,
        offset: Int,
    ): AppResult<List<Subject>> =
        try {
            val response =
                apiService.searchSubjectsAdvanced(
                    request = request,
                    limit = limit,
                    offset = offset,
                )
            AppResult.Success(response.data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BgmNetworkException) {
            AppResult.Error(e, "高级搜索失败：${e.message}")
        } catch (e: Exception) {
            AppResult.Error(e, "高级搜索异常：${e.message}")
        }
}
