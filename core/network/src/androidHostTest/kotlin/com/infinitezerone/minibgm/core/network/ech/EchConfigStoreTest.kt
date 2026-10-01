package com.infinitezerone.minibgm.core.network.ech

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ECH 配置来源与**合规保证**。
 *
 * `legacy_*_are_purged` 这组用例锁定的是 RFC 9849 §6.1.6：规范禁止把 retry_configs 用于本次重试之外的
 * 连接（会引入 pinning 与跨会话追踪向量），因此任何历史落盘的服务端下发配置都必须在启动时清除，
 * 且绝不进入生效路径。
 */
class EchConfigStoreTest {
    private lateinit var cacheDir: File

    @BeforeTest
    fun setUp() {
        cacheDir = Files.createTempDirectory("ech-config-store-test").toFile()
        EchConfigStore.init(cacheDir)
    }

    @AfterTest
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun serves_the_built_in_seed() {
        assertEquals(EchConfigStore.DEFAULT_CLOUDFLARE_ECH_CONFIG, EchConfigStore.getActiveConfig())
        assertEquals("cloudflare-ech.com", EchConfigStore.activePublicName)
    }

    @Test
    fun legacy_retry_derived_configs_are_purged() {
        val legacy = File(cacheDir, "ech_active_config_$HOST.txt")
        legacy.writeText("AEX+DQBBSwAgACCSnk0m7tsUp6JZ0lQeHADjq8jqHICYYFZRDF9ff8PXVgAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=")

        EchConfigStore.init(cacheDir)

        assertFalse(legacy.exists(), "retry-derived config must be removed from disk")
        // 且其内容绝不进入生效路径
        assertEquals(EchConfigStore.DEFAULT_CLOUDFLARE_ECH_CONFIG, EchConfigStore.getActiveConfig())
    }

    @Test
    fun the_retired_https_rr_cache_is_also_purged() {
        val retired = File(cacheDir, "ech_dns_config_$HOST.txt")
        retired.writeText("ech-dns-v1\n1790829513336\nAEX+DQBBSwAgACCSnk0m7tsU=\n")

        EchConfigStore.init(cacheDir)

        assertFalse(retired.exists(), "the retired HTTPS-RR cache must not survive the rollback")
        assertEquals(EchConfigStore.DEFAULT_CLOUDFLARE_ECH_CONFIG, EchConfigStore.getActiveConfig())
    }

    @Test
    fun unrelated_files_are_left_alone() {
        val poolFile = File(cacheDir, "ech_edge_pool.txt").apply { writeText("ech-edge-pool-v1\n") }

        EchConfigStore.init(cacheDir)

        assertTrue(poolFile.exists(), "purging ECH configs must not touch the edge pool")
    }

    private companion object {
        const val HOST = "api.bgm.tv"
    }
}
