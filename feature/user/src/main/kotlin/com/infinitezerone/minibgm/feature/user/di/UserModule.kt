package com.infinitezerone.minibgm.feature.user.di

import com.infinitezerone.minibgm.feature.user.UserCollectionsViewModel
import com.infinitezerone.minibgm.feature.user.UserViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val userModule =
    module {
        viewModelOf(::UserViewModel)
        viewModelOf(::UserCollectionsViewModel)
    }
