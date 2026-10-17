package com.infinitezerone.minibgm.core.network.ech

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
}
