package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.TimeUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 网络切换（Android 平台契约）的机械判据。
 *
 * 换网后必须做两件事：① 允许立刻重新探测边缘；② 丢弃旧网络的解析缓存。
 * 不做的话，Wi-Fi ↔ 蜂窝切换后要等满 10 分钟冷却才会重新发现节点，
 * 表现为「明明有网却一直超时」。
 */
class EchNetworkMonitorTest {
    @BeforeTest
    fun setUp() {
        AdaptiveDnsResolver.resetForTest()
        CloudflareEdgeProbe.resetCooldown()
    }

    @AfterTest
    fun tearDown() {
        AdaptiveDnsResolver.resetForTest()
        CloudflareEdgeProbe.resetCooldown()
    }

    @Test
    fun probe_is_cooling_down_only_inside_the_window() {
        val now = 1_000_000L

        assertFalse(
            CloudflareEdgeProbe.shouldSkipProbe(now, lastProbeAtMillis = 0L),
            "从未探测过不该被冷却",
        )
        assertTrue(
            CloudflareEdgeProbe.shouldSkipProbe(now, lastProbeAtMillis = now - 1_000L),
            "窗口内必须跳过，避免探测风暴",
        )
        assertFalse(
            CloudflareEdgeProbe.shouldSkipProbe(now, lastProbeAtMillis = now - 11L * 60 * 1000),
            "窗口外应允许探测",
        )
    }

    @Test
    fun network_switch_resets_cooldown_so_next_probe_can_run() {
        CloudflareEdgeProbe.markProbeAttemptForTest(TimeUtils.nowEpochMillis())
        assertTrue(CloudflareEdgeProbe.lastProbeAtForTest() != 0L)

        EchNetworkMonitor.onNetworkSwitched("test")

        assertEquals(
            0L,
            CloudflareEdgeProbe.lastProbeAtForTest(),
            "换网后必须立刻允许一次探测（旧网络的冷却对新网络没有意义）",
        )
    }

    @Test
    fun network_switch_drops_cached_resolution() {
        EchNetworkMonitor.onNetworkSwitched("test")

        assertEquals(
            0,
            AdaptiveDnsResolver.cachedHostCountForTest(),
            "换网后必须丢弃旧网络的 DNS 与候选缓存",
        )
    }
}
