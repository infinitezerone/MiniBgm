package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import com.infinitezerone.minibgm.core.model.SearchFilter
import com.infinitezerone.minibgm.core.model.SearchResult
import com.infinitezerone.minibgm.core.model.SearchSubjectsRequest
import com.infinitezerone.minibgm.core.network.BangumiApiService
import com.infinitezerone.minibgm.core.network.BgmNetworkException
import com.infinitezerone.minibgm.core.network.toUserFriendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

    /**
     * 高级多维搜索与探索条目 (POST /v0/search/subjects)。
     *
     * 服务端单页**硬上限 20 条**：请求 limit > 20 不会报错，只会静默按 20 截断，
     * 因此翻页判定必须用响应里的 `total`（[SearchResult.total]）而不是"返回条数是否等于 limit"，
     * 否则一旦 limit 传大于 20 就会永远判定为"没有下一页"。
     */
    suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int = 20,
        offset: Int = 0,
    ): AppResult<SearchResult>

    /** 观察本地搜索历史列表（按最近使用降序） */
    fun getSearchHistory(): Flow<List<String>>

    /** 添加/更新一条搜索历史 */
    suspend fun addSearchHistory(query: String)

    /** 移除单条搜索历史 */
    suspend fun removeSearchHistory(query: String)

    /** 清空所有搜索历史 */
    suspend fun clearSearchHistory()

    /** 获取探索流排序偏好（heat / score / rank，默认 rank） */
    suspend fun getExploreSortPreference(): String

    /** 设置探索流排序偏好 */
    suspend fun setExploreSortPreference(sortKey: String)

    /** 观察题材标签排除黑名单 */
    fun getBlockedSubjectTags(): Flow<List<String>>

    /** 更新题材标签排除黑名单 */
    suspend fun setBlockedSubjectTags(tags: List<String>)

    /** 观察自定义常用筛选标签列表（有序） */
    fun getCustomFilterTags(): Flow<List<String>>

    /** 添加自定义常用筛选标签 */
    suspend fun addCustomFilterTag(tag: String)

    /** 移除自定义常用筛选标签 */
    suspend fun removeCustomFilterTag(tag: String)
}

internal class SearchRepositoryImpl(
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

    override suspend fun getExploreSortPreference(): String = userPreferences.userPreferences.firstOrNull()?.exploreSortPreference ?: "rank"

    override suspend fun setExploreSortPreference(sortKey: String) {
        withContext(NonCancellable) {
            userPreferences.setExploreSortPreference(sortKey)
        }
    }

    override fun getBlockedSubjectTags(): Flow<List<String>> = userPreferences.userPreferences.map { it.blockedSubjectTags }

    override suspend fun setBlockedSubjectTags(tags: List<String>) {
        withContext(NonCancellable) {
            userPreferences.setBlockedSubjectTags(tags)
        }
    }

    override fun getCustomFilterTags(): Flow<List<String>> = userPreferences.userPreferences.map { it.customFilterTags }

    override suspend fun addCustomFilterTag(tag: String) {
        withContext(NonCancellable) {
            userPreferences.addCustomFilterTag(tag)
        }
    }

    override suspend fun removeCustomFilterTag(tag: String) {
        withContext(NonCancellable) {
            userPreferences.removeCustomFilterTag(tag)
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
                            filter = (if (type > 0) SearchFilter(type = listOf(type)) else SearchFilter()).copy(nsfw = resolveNsfwFilter()),
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
                AppResult.Error(fallbackEx, fallbackEx.toUserFriendlyMessage("搜索"))
            }
        } catch (e: Exception) {
            AppResult.Error(e, e.toUserFriendlyMessage("搜索"))
        }
    }

    override suspend fun searchSubjectsAdvanced(
        request: SearchSubjectsRequest,
        limit: Int,
        offset: Int,
    ): AppResult<SearchResult> =
        try {
            val response =
                apiService.searchSubjectsAdvanced(
                    request = withNsfwPolicy(request),
                    limit = limit,
                    offset = offset,
                )
            AppResult.Success(SearchResult(total = response.total, list = response.data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 前缀是**面向用户**的措辞，别写成「高级搜索」这种实现术语——它会原样进 Snackbar。
            AppResult.Error(e, e.toUserFriendlyMessage("搜索"))
        }

    private suspend fun resolveNsfwFilter(request: SearchSubjectsRequest? = null): Boolean? {
        val explicitlyRequestsNsfw = request?.filter?.tag?.any { it in setOf("里番", "R18", "18禁") } == true
        if (explicitlyRequestsNsfw) return null
        return if (userPreferences.userPreferences.firstOrNull()?.showRestrictedContent == true) null else false
    }

    private suspend fun withNsfwPolicy(request: SearchSubjectsRequest): SearchSubjectsRequest =
        request.copy(filter = (request.filter ?: SearchFilter()).copy(nsfw = resolveNsfwFilter(request)))
}
