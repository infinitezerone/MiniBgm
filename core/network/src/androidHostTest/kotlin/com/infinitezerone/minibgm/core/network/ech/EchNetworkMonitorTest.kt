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
        EchNetworkMonitor.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        AdaptiveDnsResolver.resetForTest()
        CloudflareEdgeProbe.resetCooldown()
        EchNetworkMonitor.resetForTest()
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
        val now = TimeUtils.nowEpochMillis()
        AdaptiveDnsResolver.injectCacheEntryForTest(
            host = "example.com",
            ips = listOf("93.184.216.34"),
            freshUntil = now + 600_000L,
            staleUntil = now + 86_400_000L,
        )

        EchNetworkMonitor.onNetworkSwitched("test")

        assertEquals(
            0,
            AdaptiveDnsResolver.cachedHostCountForTest(),
            "换网后必须丢弃旧网络的 DNS 与候选缓存",
        )
    }

    @Test
    fun onLost_for_stale_network_is_ignored_when_new_network_is_active() {
        val wifiNetwork = "wifi-network-handle"
        val cellNetwork = "cellular-network-handle"

        // 1. Wi-Fi 连接
        assertTrue(EchNetworkMonitor.handleNetworkAvailable(wifiNetwork))

        // 2. 蜂窝网络接管（Wi-Fi 切换蜂窝）
        assertTrue(EchNetworkMonitor.handleNetworkAvailable(cellNetwork))

        // 3. 延迟到达的 Wi-Fi 断开事件（onLost）必须被忽略，严禁误清蜂窝的连接池
        val lostResult = EchNetworkMonitor.handleNetworkLost(wifiNetwork)
        assertFalse(lostResult, "旧网络断开事件必须被忽略")

        // 4. 当前蜂窝网络断开事件正常处理
        val cellLostResult = EchNetworkMonitor.handleNetworkLost(cellNetwork)
        assertTrue(cellLostResult, "当前活跃网络断开必须正常响应")
    }

    @Test
    fun onNetworkLost_does_not_wipe_dns_cache_preserving_swr_fallback() {
        val now = TimeUtils.nowEpochMillis()
        AdaptiveDnsResolver.injectCacheEntryForTest(
            host = "example.com",
            ips = listOf("93.184.216.34"),
            freshUntil = now + 600_000L,
            staleUntil = now + 86_400_000L,
        )

        EchNetworkMonitor.onNetworkLost("test")

        val entry = AdaptiveDnsResolver.getCacheEntryForTest("example.com")
        assertTrue(entry != null, "网络彻底断开时必须保留 SWR 缓存以供离线/恢复容灾")
    }
}
