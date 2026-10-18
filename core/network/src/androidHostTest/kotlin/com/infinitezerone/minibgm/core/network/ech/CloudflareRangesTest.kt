package com.infinitezerone.minibgm.core.network.ech

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudflareRangesTest {
    @Test
    fun accepts_the_edge_addresses_that_used_to_be_hardcoded() {
        VALID_EDGE_ADDRESSES.forEach { ip ->
            assertTrue(CloudflareRanges.contains(ip), "$ip should fall inside a Cloudflare range")
        }
    }

    @Test
    fun rejects_private_poisoned_and_unrelated_addresses() {
        listOf(
            "0.0.0.0",
            "127.0.0.1",
            "10.0.0.1",
            "192.168.1.1",
            "169.254.1.1",
            "198.18.0.1",
            "8.8.8.8",
            "31.13.1.1",
        ).forEach { ip ->
            assertFalse(CloudflareRanges.contains(ip), "$ip must not be treated as Cloudflare")
        }
    }

    @Test
    fun accepts_cloudflare_ipv6_addresses() {
        listOf(
            "2606:4700::1",
            "2606:4700:50::1",
            "2400:cb00:2048:1::c629:d7a2",
            "2a06:98c0:3600::103",
            "[2606:4700::1]",
        ).forEach { ip ->
            assertTrue(CloudflareRanges.contains(ip), "$ip should fall inside a Cloudflare IPv6 range")
        }
    }

    @Test
    fun rejects_non_cloudflare_ipv6_and_invalid_literals() {
        listOf(
            "2001:4860:4860::8888",
            "2400:3200::1",
            "::1",
            "fe80::1",
            "example.com",
            "104.16.0",
            "104.16.0.256",
            "2606:4700:::1",
        ).forEach { ip ->
            assertFalse(CloudflareRanges.contains(ip), "$ip must not be treated as Cloudflare")
        }
    }

    @Test
    fun accepts_cloudflare_ipv4_mapped_ipv6_addresses() {
        listOf(
            "::ffff:104.18.10.118",
            "::ffff:172.67.73.67",
            "[::ffff:104.26.9.23]",
            "0:0:0:0:0:ffff:104.26.8.23",
            "::ffff:0:104.26.14.71",
        ).forEach { ip ->
            assertTrue(CloudflareRanges.contains(ip), "$ip should fall inside a Cloudflare range")
        }
    }

    @Test
    fun rejects_non_cloudflare_ipv4_mapped_ipv6_addresses() {
        listOf(
            "::ffff:1.1.1.1",
            "::ffff:192.168.1.1",
            "::ffff:8.8.8.8",
            "0:0:0:0:0:ffff:127.0.0.1",
            "::ffff:0:10.0.0.1",
        ).forEach { ip ->
            assertFalse(CloudflareRanges.contains(ip), "$ip must not be treated as Cloudflare")
        }
    }

    @Test
    fun sampling_stays_inside_the_ranges_and_honours_exclusions() {
        val excluded = setOf("104.16.0.0")
        val sampled = CloudflareRanges.sampleCandidates(count = 64, random = Random(42), exclude = excluded)

        assertEquals(64, sampled.size)
        assertEquals(sampled.size, sampled.toSet().size, "sampled candidates must be unique")
        sampled.forEach { assertTrue(CloudflareRanges.contains(it), "$it fell outside every range") }
        assertTrue(sampled.none { it in excluded })
    }

    @Test
    fun sampling_covers_both_high_confidence_blocks() {
        val sampled = CloudflareRanges.sampleCandidates(count = 512, random = Random(7))
        assertTrue(sampled.any { it.startsWith("104.") }, "expected samples from 104.16.0.0/12")
        assertTrue(sampled.any { it.startsWith("172.") }, "expected samples from 172.64.0.0/13")
    }

    @Test
    fun sampling_returns_nothing_for_a_non_positive_count() {
        assertTrue(CloudflareRanges.sampleCandidates(count = 0, random = Random(1)).isEmpty())
        assertTrue(CloudflareRanges.sampleCandidates(count = -5, random = Random(1)).isEmpty())
    }

    companion object {
        /** 改造前那份硬编码池里的全部地址——它们必须仍然被白名单接受。 */
        private val VALID_EDGE_ADDRESSES =
            listOf(
                "172.67.73.67",
                "104.26.9.23",
                "104.26.8.23",
                "172.67.71.232",
                "104.26.14.71",
                "104.26.15.71",
            )
    }
}
