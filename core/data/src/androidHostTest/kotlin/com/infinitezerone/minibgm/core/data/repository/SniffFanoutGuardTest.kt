package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 下探扇出与跳转域约束 */
class SniffFanoutGuardTest {
    private class CountingFetch(
        private val pages: Map<String, String>,
    ) : PageFetchService {
        val requested = mutableListOf<String>()

        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            requested += url
            val html = pages.entries.firstOrNull { url.startsWith(it.key) }?.value ?: return null
            return FetchedPage(url = url, html = html)
        }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null
    }

    private fun listPage(index: Int) =
        "<html><head><title>目录 $index</title></head><body>" +
            "<a href=\"https://v.example.tv/p$index-a\">[1]</a>" +
            "<a href=\"https://v.example.tv/p$index-b\">第 1 话</a>" +
            "</body></html>"

    private fun episodePage(index: Int) =
        "<html><head><title>某番 第 1 话</title></head><body>" +
            "<iframe src=\"https://v.example.tv/embed$index\"></iframe>" +
            "</body></html>"

    private val embedPage =
        "<html><head><title>在线播放</title></head><body>" +
            "<video src=\"https://cdn.example.tv/m1.mp4\"></video>" +
            "</body></html>"

    @Test
    fun `一次解析的抓取总数有上限`() =
        runTest {
            // 5 个目录页 × 每页 2 条分集链接 × 每条再进一层 iframe，放开上限会是 25 次请求
            val pages =
                (1..5).flatMap { i ->
                    listOf(
                        "https://list.example.tv/dir$i" to listPage(i),
                        "https://v.example.tv/p$i-a" to episodePage(i),
                        "https://v.example.tv/p$i-b" to episodePage(i),
                    )
                } + listOf("https://v.example.tv/embed" to embedPage)
            val fetch = CountingFetch(pages.toMap())
            val repo = PlaybackResolverRepositoryImpl(fetch)

            repo.resolvePages((1..5).map { "https://list.example.tv/dir$it" }, epNumber = 1f)

            assertTrue(
                fetch.requested.size <= MAX_FETCHES_PER_RESOLUTION,
                "抓取了 ${fetch.requested.size} 次，超过单次解析上限 $MAX_FETCHES_PER_RESOLUTION",
            )
        }

    @Test
    fun `iframe 跟进不受注册域限制，列表挑链接才受`() =
        runTest {
            // 内嵌播放器托管在另一个注册域是常态，拦它等于把正常站点打死
            val crossDomainEmbed =
                mapOf(
                    "https://list.example.tv/cross" to
                        "<html><head><title>播放页</title></head><body>" +
                        "<iframe src=\"https://player.other.site/embed/9\"></iframe>" +
                        "</body></html>",
                    "https://player.other.site/embed/9" to embedPage,
                )
            val repo = PlaybackResolverRepositoryImpl(CountingFetch(crossDomainEmbed))
            val sources = repo.resolvePages(listOf("https://list.example.tv/cross"), 1f)
            assertEquals("https://cdn.example.tv/m1.mp4", sources.single().url)

            // 列表页下探：同注册域的子站放行，跨注册域的社交链接挡住
            val listPages =
                mapOf(
                    "https://list.example.tv/mixed" to
                        "<html><head><title>目录</title></head><body>" +
                        "<a href=\"https://v.example.tv/ep/1\">[1]</a>" +
                        "<a href=\"https://twitter.com/share\">[1]</a>" +
                        "</body></html>",
                    "https://v.example.tv/ep/1" to
                        "<html><head><title>第 1 话</title></head><body>" +
                        "<video src=\"https://cdn.example.tv/e1.mp4\"></video>" +
                        "</body></html>",
                )
            val mixed = CountingFetch(listPages)
            val mixedSources = PlaybackResolverRepositoryImpl(mixed).resolvePages(listOf("https://list.example.tv/mixed"), 1f)

            assertEquals("https://cdn.example.tv/e1.mp4", mixedSources.single().url)
            assertFalse(mixed.requested.any { it.contains("twitter.com") }, "社交外链不该被下探")
        }

    @Test
    fun `两段式公共后缀按注册域比较`() {
        assertTrue(sameRegistrableDomain("https://a.example.com.cn/x", "https://b.example.com.cn/y"))
        assertFalse(sameRegistrableDomain("https://a.example.com.cn/x", "https://b.other.com.cn/y"))
        assertTrue(sameRegistrableDomain("https://v.example.tv/a", "https://cdn.example.tv/b"))
        assertFalse(sameRegistrableDomain("https://example.tv/a", "https://example.org/b"))
        // 拿不出域名（畸形地址）时放行，这一版只拦明确的跨站跳转
        assertTrue(sameRegistrableDomain("not a url", "https://example.tv/a"))
    }
}
