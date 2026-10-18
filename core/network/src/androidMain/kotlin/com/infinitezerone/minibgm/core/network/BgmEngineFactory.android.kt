package com.infinitezerone.minibgm.core.network

import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.ech.EchHttpClientEngine
import com.infinitezerone.minibgm.core.network.ech.EchNativeClient
import com.infinitezerone.minibgm.core.network.transport.RoutingEngine
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun createPlatformHttpClientEngine(): HttpClientEngine {
    val logger = bgmLogger("Bgm/NetworkEngine")
    val plainEngine = CIO.create()
    if (!EchNativeClient.isAvailable()) {
        logger.i { "EchNativeClient unavailable, fallback to CIO engine" }
        return plainEngine
    }
    logger.i { "Using RoutingEngine (ECH for https, CIO for http/loopback)" }
    return RoutingEngine(
        echEngine = EchHttpClientEngine(),
        plainEngine = plainEngine,
    )
}
