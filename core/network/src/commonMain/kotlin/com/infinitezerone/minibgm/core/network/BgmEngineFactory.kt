package com.infinitezerone.minibgm.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * 平台相关的网络引擎创建工厂
 *
 * Android 端：当 libminibgm_ech.so 存在且可用时，优先创建 EchHttpClientEngine；否则回退 CIO。
 */
expect fun createPlatformHttpClientEngine(): HttpClientEngine
