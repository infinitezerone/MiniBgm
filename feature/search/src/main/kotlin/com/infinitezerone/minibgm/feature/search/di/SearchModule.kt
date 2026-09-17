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
        viewModelOf(::ExploreViewModel)
        viewModel { (tag: String, initialType: Int) ->
            TagSubjectsViewModel(
                tag = tag,
                initialType = initialType,
                searchRepository = get(),
                collectionRepository = get(),
            )
        }
        viewModel { (initialYear: Int?, initialSeasonMonth: Int?) ->
            SeasonalGuideViewModel(
                searchRepository = get(),
                collectionRepository = get(),
                authRepository = get(),
                initialYear = initialYear ?: 0,
                initialSeasonMonth = initialSeasonMonth ?: 0,
            )
        }
    }
