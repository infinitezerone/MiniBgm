package com.infinitezerone.minibgm.core.common

import kotlinx.coroutines.CancellationException

sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AppResult<T>

    data class Error(
        val throwable: Throwable,
        val message: String = throwable.message ?: "Unknown error",
    ) : AppResult<Nothing>

    data object Loading : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> this
        is AppResult.Loading -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (Throwable, String) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(throwable, message)
    return this
}

/**
 * Runs [block] and wraps the result in [AppResult.Success].
 * If [CancellationException] is thrown, it is re-thrown so that coroutine cancellation is never swallowed.
 * Any other [Throwable] is wrapped in [AppResult.Error].
 */
inline fun <T> asAppResult(
    errorMessage: (Throwable) -> String = { it.message ?: "Unknown error" },
    block: () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Error(e, errorMessage(e))
    }

/**
 * Coroutine-friendly variant of [kotlin.runCatching] that never catches [CancellationException].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
