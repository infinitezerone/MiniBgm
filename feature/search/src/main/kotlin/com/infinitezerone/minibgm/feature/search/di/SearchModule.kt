package com.infinitezerone.minibgm.feature.search.di

import com.infinitezerone.minibgm.feature.search.ExploreViewModel
import com.infinitezerone.minibgm.feature.search.SearchViewModel
import com.infinitezerone.minibgm.feature.search.SeasonalGuideViewModel
import com.infinitezerone.minibgm.feature.search.TagSubjectsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchModule =
    module {
        viewModelOf(::SearchViewModel)
        viewModel { ExploreViewModel(get(), get(), get(), getOrNull(), autoStart = false) }
        viewModel { params ->
            val tag = runCatching { params.get<String>(0) }.getOrDefault("")
            val initialType = runCatching { params.get<Int>(1) }.getOrDefault(0)
            TagSubjectsViewModel(
                tag = tag,
                initialType = initialType,
                searchRepository = get(),
                collectionRepository = get(),
            )
        }
        viewModel { params ->
            val initialYear = runCatching { params.get<Int>(0) }.getOrDefault(0)
            val initialSeasonMonth = runCatching { params.get<Int>(1) }.getOrDefault(0)
            SeasonalGuideViewModel(
                searchRepository = get(),
                collectionRepository = get(),
                authRepository = get(),
                scheduleRepository = get(),
                settingsRepository = get(),
                initialYear = initialYear,
                initialSeasonMonth = initialSeasonMonth,
            )
        }
    }
