package com.infinitezerone.minibgm.core.webview.di

import com.infinitezerone.minibgm.core.data.repository.WebViewCaptureService
import com.infinitezerone.minibgm.core.data.repository.WebViewResolveRepository
import com.infinitezerone.minibgm.core.data.repository.WebViewResolveRepositoryImpl
import com.infinitezerone.minibgm.core.webview.WebViewCaptureServiceImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val webviewModule =
    module {
        single<WebViewCaptureService> { WebViewCaptureServiceImpl(context = androidContext()) }
        single<WebViewResolveRepository> { WebViewResolveRepositoryImpl(scheduleRepository = get(), captureService = get()) }
    }
