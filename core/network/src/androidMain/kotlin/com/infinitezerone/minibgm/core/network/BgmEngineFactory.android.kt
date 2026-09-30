package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.ech.EchHttpClientEngine
import com.infinitezerone.minibgm.core.network.ech.EchNativeClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun createPlatformHttpClientEngine(): HttpClientEngine {
    val logger = bgmLogger("Bgm/NetworkEngine")
    return if (EchNativeClient.isAvailable()) {
        logger.i { "Using EchHttpClientEngine (Rust ECH direct connect)" }
        EchHttpClientEngine()
    } else {
        logger.i { "EchNativeClient unavailable, fallback to CIO engine" }
        CIO.create()
    }
}
