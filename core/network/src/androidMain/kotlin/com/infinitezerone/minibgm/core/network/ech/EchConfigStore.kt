package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端 ECH 配置的内存缓存与本地持久化存储。
 */
object EchConfigStore {
    private val logger = bgmLogger("Bgm/EchConfigStore")

    // 默认 Cloudflare ECH 配置种子
    const val DEFAULT_CLOUDFLARE_ECH_CONFIG =
        "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA="

    private val cachedConfigs = ConcurrentHashMap<String, String>()
    private var cacheFile: File? = null

    fun init(filesDir: File) {
        cacheFile = filesDir
        loadConfigFile(File(filesDir, "ech_active_config.txt"), "*")
        filesDir
            .listFiles { file -> file.name.startsWith("ech_active_config_") && file.name.endsWith(".txt") }
            ?.forEach { file -> loadConfigFile(file, file.name.removePrefix("ech_active_config_").removeSuffix(".txt")) }
    }

    fun getActiveConfig(host: String): String = cachedConfigs[host] ?: cachedConfigs["*"] ?: DEFAULT_CLOUDFLARE_ECH_CONFIG

    fun updateConfig(
        host: String,
        newConfig: String,
    ) {
        val trimmed = newConfig.trim()
        if (trimmed.isNotBlank() && trimmed != cachedConfigs[host]) {
            cachedConfigs[host] = trimmed
            logger.i { "Updated active ECH config (${trimmed.take(16)}...)" }
            val directory = cacheFile
            if (directory != null) {
                runCatching {
                    val file = File(directory, "ech_active_config_${safeHost(host)}.txt")
                    val temporary = File(directory, "${file.name}.tmp")
                    temporary.writeText(trimmed)
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                    logger.d { "Persisted updated ECH config to disk cache" }
                }.onFailure { t ->
                    logger.w(t) { "Failed to persist updated ECH config to disk" }
                }
            }
        }
    }

    private fun loadConfigFile(
        file: File,
        host: String,
    ) {
        if (!file.exists()) return
        runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            cachedConfigs[host] = it
            logger.i { "Loaded persisted ECH config for $host (${it.take(16)}...)" }
        }
    }

    private fun safeHost(host: String): String = host.replace(Regex("[^A-Za-z0-9.-]"), "_")
}
