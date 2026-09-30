package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File

/**
 * 负责客户端 ECH 配置的运行时内存管理与本地持久化
 *
 * 实现了机制与配置的完全解耦：
 * 1. 底层 Rust 引擎保持纯粹与零硬编码；
 * 2. 默认种子密钥作为上层配置存在，用于初次冷启动；
 * 3. 当底层通过 TLS retry_configs 动态协商出自愈的新公钥时，实时落盘持久化；
 * 4. 之后无论是热启动还是杀死进程冷启动，均优先使用已落盘的最新密钥。
 */
object EchConfigStore {
    private val logger = bgmLogger("Bgm/EchConfigStore")

    // Cloudflare 全局通用的当前活跃 ECH 配置种子（仅作为上层默认配置，不在底层 .so 硬编码）
    const val DEFAULT_CLOUDFLARE_ECH_CONFIG =
        "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA="

    @Volatile
    private var cachedConfig: String? = null
    private var cacheFile: File? = null

    fun init(filesDir: File) {
        val file = File(filesDir, "ech_active_config.txt")
        cacheFile = file
        if (file.exists()) {
            val content = runCatching { file.readText().trim() }.getOrNull()
            if (!content.isNullOrBlank()) {
                cachedConfig = content
                logger.i { "Loaded persisted ECH config from disk cache (${content.take(16)}...)" }
            }
        }
    }

    fun getActiveConfig(): String = cachedConfig ?: DEFAULT_CLOUDFLARE_ECH_CONFIG

    fun updateConfig(newConfig: String) {
        val trimmed = newConfig.trim()
        if (trimmed.isNotBlank() && trimmed != cachedConfig) {
            cachedConfig = trimmed
            logger.i { "Updated active ECH config (${trimmed.take(16)}...)" }
            val file = cacheFile
            if (file != null) {
                runCatching {
                    file.writeText(trimmed)
                    logger.d { "Persisted updated ECH config to disk cache" }
                }.onFailure { t ->
                    logger.w(t) { "Failed to persist updated ECH config to disk" }
                }
            }
        }
    }
}
