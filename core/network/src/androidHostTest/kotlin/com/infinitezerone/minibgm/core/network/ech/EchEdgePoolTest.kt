package com.infinitezerone.minibgm.core.network.ech

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 优选池的排序、衰减与落盘契约。
 *
 * 时间一律显式传入（`now` 参数），不用真实时钟——排序规则本来就是"相对时间"的函数，
 * 注入时间才能把"过期""连续失败"这类分支稳定地钉死。
 */
class EchEdgePoolTest {
    private val now = 1_800_000_000_000L
    private lateinit var cacheDir: File

    @BeforeTest
    fun setUp() {
        cacheDir = Files.createTempDirectory("ech-edge-pool-test").toFile()
        EchEdgePool.resetForTest()
        EchEdgePool.init(cacheDir)
    }

    @AfterTest
    fun tearDown() {
        EchEdgePool.resetForTest()
        cacheDir.deleteRecursively()
    }

    @Test
    fun absorb_registers_candidates_without_claiming_they_succeeded() {
        EchEdgePool.absorb(listOf("172.67.73.67", "104.26.9.23"), now)

        assertEquals(listOf("172.67.73.67", "104.26.9.23"), EchEdgePool.snapshot(now))
        // 只被观测过、从未握手成功：不能算新鲜节点，否则兜底探测永远不会启动
        assertFalse(EchEdgePool.hasFreshNodes(now))
    }

    @Test
    fun recordSuccess_promotes_the_node_ahead_of_untried_candidates() {
        EchEdgePool.absorb(listOf("104.16.0.1", "104.16.0.2", "104.16.0.3"), now)
        EchEdgePool.recordSuccess("104.16.0.3", now + 1)

        assertEquals("104.16.0.3", EchEdgePool.snapshot(now + 1).first())
        assertTrue(EchEdgePool.hasFreshNodes(now + 1))
    }

    @Test
    fun repeatedly_failing_node_yields_to_never_tried_candidates() {
        EchEdgePool.absorb(listOf("104.16.0.1", "104.16.0.2"), now)
        EchEdgePool.recordSuccess("104.16.0.1", now + 1)
        repeat(3) { EchEdgePool.recordFailure("104.16.0.1", now + 2 + it) }

        // 网段被黑洞的环境里，未试过的候选比"曾经能用但已连续失败"的更值得先试
        assertEquals("104.16.0.2", EchEdgePool.snapshot(now + 10).first())
    }

    @Test
    fun never_succeeded_and_failing_node_ranks_last() {
        EchEdgePool.absorb(listOf("104.16.0.1", "104.16.0.2"), now)
        EchEdgePool.recordFailure("104.16.0.1", now + 1)

        assertEquals(listOf("104.16.0.2", "104.16.0.1"), EchEdgePool.snapshot(now + 2))
    }

    @Test
    fun a_success_older_than_the_freshness_window_is_not_fresh() {
        EchEdgePool.recordSuccess("104.16.0.1", now)
        assertTrue(EchEdgePool.hasFreshNodes(now))

        val sevenHoursLater = now + 7 * 60 * 60 * 1000
        assertFalse(EchEdgePool.hasFreshNodes(sevenHoursLater))
        // 过期只是不再"新鲜"，节点本身必须留着——它很可能只是暂时不可用
        assertEquals(listOf("104.16.0.1"), EchEdgePool.snapshot(sevenHoursLater))
    }

    @Test
    fun pool_survives_process_death() {
        EchEdgePool.absorb(listOf("172.67.73.67"), now)
        EchEdgePool.recordSuccess("172.67.73.67", now + 1)
        EchEdgePool.flush()

        // 模拟进程重启：清空内存后重新挂载同一目录
        EchEdgePool.resetForTest()
        EchEdgePool.init(cacheDir)

        assertEquals(listOf("172.67.73.67"), EchEdgePool.snapshot(now + 2))
    }

    @Test
    fun a_corrupt_cache_file_falls_back_to_an_empty_pool() {
        File(cacheDir, POOL_FILE_NAME).writeText("this is not a pool file\n\u0000\u0001garbage")

        EchEdgePool.resetForTest()
        EchEdgePool.init(cacheDir)

        // 缓存损坏只允许降级，不允许影响主流程
        assertEquals(0, EchEdgePool.size())
    }

    @Test
    fun malformed_lines_are_skipped_without_dropping_valid_ones() {
        File(cacheDir, POOL_FILE_NAME).writeText(
            buildString {
                append("ech-edge-pool-v1\n")
                append("104.16.0.1|$now|$now|0\n")
                append("broken-line\n")
                append("104.16.0.2|not-a-number|$now|0\n")
                append("104.16.0.3|$now|$now|not-a-number\n")
            },
        )

        EchEdgePool.resetForTest()
        EchEdgePool.init(cacheDir)

        assertEquals(1, EchEdgePool.size())
        assertEquals(listOf("104.16.0.1"), EchEdgePool.snapshot(now))
    }

    @Test
    fun pool_size_stays_bounded() {
        EchEdgePool.absorb((1..40).map { "104.16.$it.1" }, now)
        assertEquals(MAX_POOL_NODES, EchEdgePool.size())
    }

    @Test
    fun only_cloudflare_range_addresses_are_admitted() {
        // 真机实测暴露过的形态：EchHttpClientEngine 对所有 host 都回传 connectedAddr，
        // App 访问的图片 CDN 在 Fastly 上，那个地址曾经被当成 CF 边缘收进池
        EchEdgePool.absorb(listOf("151.101.1.229", "104.16.0.1"), now)
        assertEquals(listOf("104.16.0.1"), EchEdgePool.snapshot(now))

        EchEdgePool.recordSuccess("151.101.1.229:443", now + 1)
        assertEquals(1, EchEdgePool.size())
        assertEquals("104.16.0.1", EchEdgePool.snapshot(now + 1).single())
    }

    @Test
    fun normalizeIp_strips_the_port_and_ipv6_brackets() {
        assertEquals("104.16.0.1", EchEdgePool.normalizeIp("104.16.0.1:443"))
        assertEquals("2606:4700::1", EchEdgePool.normalizeIp("[2606:4700::1]:443"))
        assertEquals("2606:4700::1", EchEdgePool.normalizeIp("2606:4700::1"))
        assertNull(EchEdgePool.normalizeIp("   "))
    }

    companion object {
        /** 与 EchEdgePool 的落盘/容量契约对齐，改动时这里必须一起改。 */
        private const val POOL_FILE_NAME = "ech_edge_pool.txt"
        private const val MAX_POOL_NODES = 32
    }
}
