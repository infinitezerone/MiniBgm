package com.infinitezerone.minibgm.core.testing.datastore

import androidx.datastore.core.DataStore
import com.infinitezerone.minibgm.core.datastore.UserPreferences
import com.infinitezerone.minibgm.core.datastore.UserPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

class InMemoryDataStore<T>(
    initialValue: T,
) : DataStore<T> {
    private val flow = MutableStateFlow(initialValue)
    override val data: Flow<T> = flow.asStateFlow()

    override suspend fun updateData(transform: suspend (t: T) -> T): T = flow.updateAndGet { transform(it) }
}

fun createTestUserPreferencesDataSource(initialPreferences: UserPreferences = UserPreferences()): UserPreferencesDataSource {
    val inMemoryDataStore = InMemoryDataStore(initialPreferences)
    return UserPreferencesDataSource(inMemoryDataStore)
}
