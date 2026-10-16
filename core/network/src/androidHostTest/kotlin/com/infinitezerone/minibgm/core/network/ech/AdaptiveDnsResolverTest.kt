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
}
