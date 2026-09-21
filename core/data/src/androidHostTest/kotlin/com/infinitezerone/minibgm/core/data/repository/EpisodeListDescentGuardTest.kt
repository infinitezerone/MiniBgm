package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlaybackRuleKind
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 列表页下探的片名归属守卫。
 *
 * 搜索页搜不到片名时，站点常回落到"最新番剧"之类的默认列表，那里的 `[25]` 锚文本对
 * 第 25 话的话数规则同样命中——不做归属校验就会返回另一部番的直链并报告成功。
 */
class EpisodeListDescentGuardTest {
    private companion object {
        const val SIMPLIFIED_TITLE = "葬送的芙莉莲"
        const val TRADITIONAL_TITLE = "葬送的芙莉蓮"

        /** 简体检索无结果：只剩与本番无关的最新列表，锚文本带 [25] */
        val UNMATCHED_LIST_PAGE =
            """
            <html><head><title>「$SIMPLIFIED_TITLE」的搜索结果 - Example</title></head><body>
            <ul>
              <li><a href="https://search.example.tv/9001">海上的家 [25]</a></li>
              <li><a href="https://search.example.tv/9002">夜行列车 [24]</a></li>
            </ul>
            </body></html>
            """.trimIndent()

        /** 繁体检索命中：本番第 38 话的单集条目 */
        val MATCHED_LIST_PAGE =
            """
            <html><head><title>「$TRADITIONAL_TITLE」的搜索结果 - Example</title></head><body>
            <ul>
              <li><a href="https://search.example.tv/7038">$TRADITIONAL_TITLE 第二季 [38]</a></li>
            </ul>
            </body></html>
            """.trimIndent()

        val WRONG_SHOW_PAGE =
            """
            <html><head><title>海上的家 [25] - Example</title></head><body>
            <video src="https://cdn.example.tv/sea/25.mp4"></video>
            </body></html>
            """.trimIndent()

        val TARGET_EPISODE_PAGE =
            """
            <html><head><title>$TRADITIONAL_TITLE 第二季 [38] - Example</title></head><body>
            <video src="https://cdn.example.tv/media/38.mp4"></video>
            </body></html>
            """.trimIndent()

        val SEARCH_RULE =
            PlaybackSourceRule(
                id = "r-search",
                name = "示例搜索站",
                urlTemplate = "https://search.example.tv/?q={title}",
                kind = PlaybackRuleKind.SOURCE,
            )
    }

    /** 列表页按查询词繁简给不同结果，单集页按地址回放，模拟站点的真实分歧 */
    private class ReplayFetch : PageFetchService {
        val requested = mutableListOf<String>()

        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? {
            requested += url
            val html =
                when {
                    url.contains("q=") -> if (url.queryTitle().contains(TRADITIONAL_LOTUS)) MATCHED_LIST_PAGE else UNMATCHED_LIST_PAGE
                    url.endsWith("/9001") -> WRONG_SHOW_PAGE
                    url.endsWith("/7038") -> TARGET_EPISODE_PAGE
                    else -> null
                } ?: return null
            return FetchedPage(url = url, html = html)
        }

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null

        /** 站点只认繁体写法，查询词要解码后才知道请求的是哪一种 */
        private fun String.queryTitle(): String = java.net.URLDecoder.decode(substringAfter("q=", ""), "UTF-8")

        private companion object {
            const val TRADITIONAL_LOTUS = "蓮"
        }
    }

    @Test
    fun `搜不到片名时不把撞号的别的番剧当成结果`() =
        runTest {
            val fetch = ReplayFetch()
            val repo = PlaybackResolverRepositoryImpl(fetch)

            val sources =
                repo.resolveTemplate(
                    url = "https://search.example.tv/?q=$SIMPLIFIED_TITLE",
                    epNumber = 25f,
                    title = SIMPLIFIED_TITLE,
                )

            assertTrue(fetch.requested.any { it.endsWith("/9001") }, "下探本身仍应发生，只是结果被拒")
            assertEquals(emptyList(), sources, "拿到了《海上的家》第 25 话：跳错番还报告成功")
        }

    @Test
    fun `未给出片名时保持嗅探原样`() =
        runTest {
            val repo = PlaybackResolverRepositoryImpl(ReplayFetch())

            val sources =
                repo.resolveTemplate(
                    url = "https://search.example.tv/?q=$SIMPLIFIED_TITLE",
                    epNumber = 25f,
                )

            assertEquals("https://cdn.example.tv/sea/25.mp4", sources.single().url)
        }

    @Test
    fun `片名命中时下探并补全出可播直链与防盗链头`() =
        runTest {
            val repo = PlaybackResolverRepositoryImpl(ReplayFetch())

            val sources = repo.resolveRule(SEARCH_RULE, TRADITIONAL_TITLE, 38f)

            val stream = sources.single()
            assertEquals(PlaylistEntryKind.DIRECT, stream.kind)
            assertEquals("https://cdn.example.tv/media/38.mp4", stream.url)
            assertEquals("https://search.example.tv/", stream.headers["Referer"])
        }

    @Test
    fun `页面标题拿不到时不做归属校验`() {
        assertTrue(pageBelongsToTitle("<html><body>no title</body></html>", SIMPLIFIED_TITLE))
        assertTrue(pageBelongsToTitle("<title>任意播放器</title>", ""))
    }
}
