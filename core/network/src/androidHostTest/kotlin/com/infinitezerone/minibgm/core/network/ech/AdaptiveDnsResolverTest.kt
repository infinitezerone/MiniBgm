package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.TimeUtils
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveDnsResolverTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("adaptive-dns-resolver-test").toFile()
        AdaptiveDnsResolver.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        AdaptiveDnsResolver.resetForTest()
        tempDir.deleteRecursively()
    }

    @Test
    fun init_loads_persisted_dynamic_hosts_from_disk() {
        val hostsFile = File(tempDir, "ech_dynamic_hosts.txt")
        hostsFile.writeText("example.com\n  Sub.Domain.Org  \n\n")

        AdaptiveDnsResolver.init(tempDir)

        assertTrue(AdaptiveDnsResolver.isEchEligible("example.com"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("sub.domain.org"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("api.sub.domain.org"))
        assertFalse(AdaptiveDnsResolver.isEchEligible("unrelated.org"))
    }

    @Test
    fun init_tolerates_missing_or_directory_hosts_file() {
        AdaptiveDnsResolver.init(tempDir)
        assertFalse(AdaptiveDnsResolver.isEchEligible("dynamic-unknown.com"))
    }

    @Test
    fun registerEchHost_persists_new_host_and_updates_eligibility() {
        AdaptiveDnsResolver.init(tempDir)
        assertFalse(AdaptiveDnsResolver.isEchEligible("mikanani.me"))

        AdaptiveDnsResolver.registerEchHost("MikanAni.me")
        assertTrue(AdaptiveDnsResolver.isEchEligible("mikanani.me"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("sub.mikanani.me"))

        // 校验磁盘文件已持久化
        val hostsFile = File(tempDir, "ech_dynamic_hosts.txt")
        assertTrue(hostsFile.exists())
        val lines = hostsFile.readLines().map { it.trim() }
        assertTrue(lines.contains("mikanani.me"))
    }

    @Test
    fun registerEchHost_ignores_blank_or_duplicate_hosts() {
        AdaptiveDnsResolver.init(tempDir)
        AdaptiveDnsResolver.registerEchHost("   ")
        AdaptiveDnsResolver.registerEchHost("foo.bar")
        AdaptiveDnsResolver.registerEchHost("foo.bar")

        val hostsFile = File(tempDir, "ech_dynamic_hosts.txt")
        val lines = hostsFile.readLines().map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(listOf("foo.bar"), lines)
    }

    @Test
    fun static_whitelist_hosts_are_ech_eligible() {
        assertTrue(AdaptiveDnsResolver.isEchEligible("bgm.tv"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("api.bgm.tv"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("bangumi.tv"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("chii.in"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("sda1.dev"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("kitsu.io"))
        assertTrue(AdaptiveDnsResolver.isEchEligible("cloudflare-dns.com"))

        assertFalse(AdaptiveDnsResolver.isEchEligible("google.com"))
    }

    @Test
    fun isCloudflareHosted_includes_ech_eligible_and_anilist() {
        assertTrue(AdaptiveDnsResolver.isCloudflareHosted("bgm.tv"))
        assertTrue(AdaptiveDnsResolver.isCloudflareHosted("anilist.co"))
        assertTrue(AdaptiveDnsResolver.isCloudflareHosted("graphql.anilist.co"))
        assertFalse(AdaptiveDnsResolver.isCloudflareHosted("example.com"))
    }

    @Test
    fun assembleCandidates_caps_to_request_budget_and_keeps_fresh_sources() {
        val pool = (1..8).map { "104.26.8." + it }
        val hostIps = listOf("172.67.73.67")
        val publicNameIps = listOf("104.18.10.118", "104.18.11.118")

        val picked = AdaptiveDnsResolver.assembleCandidates(pool, hostIps, publicNameIps)

        // 4 个候选 × Rust 单候选 2s = 8s < 业务 client 15s 预算；10 个候选必然 20s 超时
        assertEquals(4, picked.size, "候选必须裁到请求预算内")
        assertTrue(picked.contains(hostIps[0]), "刚解析到的真实 IP 必须占一个槽位")
        assertTrue(picked.contains(publicNameIps[0]), "ECH 入口 IP 必须占一个槽位")
        assertEquals(listOf(pool[0], pool[1]), picked.take(2), "池按健康分排序，取前两条")
    }

    @Test
    fun assembleCandidates_dedupes_and_fills_from_remaining_sources() {
        val picked =
            AdaptiveDnsResolver.assembleCandidates(
                poolIps = listOf("1.1.1.1", "1.1.1.1"),
                hostIps = listOf("1.1.1.1", "2.2.2.2"),
                publicNameIps = emptyList(),
            )

        assertEquals(listOf("1.1.1.1", "2.2.2.2"), picked)
    }

    @Test
    fun assembleCandidates_returns_empty_when_every_source_is_blank() {
        assertTrue(
            AdaptiveDnsResolver
                .assembleCandidates(emptyList(), listOf("   "), emptyList())
                .isEmpty(),
        )
    }

    @Test
    fun resolveTargetAddrs_uses_fresh_cached_entry() =
        runTest {
            val now = TimeUtils.nowEpochMillis()
            AdaptiveDnsResolver.injectCacheEntryForTest(
                host = "example.com",
                ips = listOf("93.184.216.34"),
                freshUntil = now + 600_000L,
                staleUntil = now + 86_400_000L,
            )

            val addrs = AdaptiveDnsResolver.resolveTargetAddrs("example.com", 443)
            assertEquals(listOf("93.184.216.34:443"), addrs?.toList())
        }

    @Test
    fun resolveTargetAddrs_returns_stale_cached_entry_under_stale_window() =
        runTest {
            val now = TimeUtils.nowEpochMillis()
            // 已过 10 分钟鲜活期，但在 24 小时陈旧可用期内
            AdaptiveDnsResolver.injectCacheEntryForTest(
                host = "example.com",
                ips = listOf("93.184.216.34"),
                freshUntil = now - 1_000L,
                staleUntil = now + 86_400_000L,
            )

            val addrs = AdaptiveDnsResolver.resolveTargetAddrs("example.com", 443)
            assertEquals(listOf("93.184.216.34:443"), addrs?.toList(), "RFC 8767: 处于陈旧可用期时立即返回旧地址")
        }

    @Test
    fun recordSuccess_does_not_clear_dnsCache() {
        val now = TimeUtils.nowEpochMillis()
        AdaptiveDnsResolver.injectCacheEntryForTest(
            host = "example.com",
            ips = listOf("93.184.216.34"),
            freshUntil = now + 600_000L,
            staleUntil = now + 86_400_000L,
        )

        AdaptiveDnsResolver.recordSuccess("example.com", "93.184.216.34:443")

        val entry = AdaptiveDnsResolver.getCacheEntryForTest("example.com")
        assertTrue(entry != null, "recordSuccess 绝不能抹除 dnsCache，否则下次请求会退化为阻塞冷启动解析")
        assertEquals(listOf("93.184.216.34"), entry.first)
    }

    @Test
    fun recordFailure_does_not_clear_dnsCache() {
        val now = TimeUtils.nowEpochMillis()
        AdaptiveDnsResolver.injectCacheEntryForTest(
            host = "example.com",
            ips = listOf("93.184.216.34"),
            freshUntil = now + 600_000L,
            staleUntil = now + 86_400_000L,
        )

        AdaptiveDnsResolver.recordFailure("example.com")

        val entry = AdaptiveDnsResolver.getCacheEntryForTest("example.com")
        assertTrue(entry != null, "recordFailure 绝不能抹除 dnsCache，RFC 8767 容灾依赖此缓存")
        assertEquals(listOf("93.184.216.34"), entry.first)
    }

    @Test
    fun resolveTargetAddrs_applies_backoff_to_freshUntil_when_background_refresh_fails() =
        runTest {
            val now = TimeUtils.nowEpochMillis()
            val host = "non-existent-domain-for-test.invalid"
            AdaptiveDnsResolver.injectCacheEntryForTest(
                host = host,
                ips = listOf("1.2.3.4"),
                freshUntil = now - 1000L,
                staleUntil = now + 86_400_000L,
            )

            val addrs = AdaptiveDnsResolver.resolveTargetAddrs(host, 443)
            assertEquals(listOf("1.2.3.4:443"), addrs?.toList())

            // 等待后台刷新完成并应用退避
            var backedOff = false
            for (i in 1..50) {
                Thread.sleep(50)
                val entry = AdaptiveDnsResolver.getCacheEntryForTest(host)
                if (entry != null && entry.second > now) {
                    backedOff = true
                    break
                }
            }
            assertTrue(backedOff, "后台解析失败后应将 freshUntil 推后以退避冷却")
        }
}
