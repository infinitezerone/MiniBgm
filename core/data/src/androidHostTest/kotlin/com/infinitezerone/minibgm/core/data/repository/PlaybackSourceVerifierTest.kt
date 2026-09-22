package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.network.FetchedPage
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.StreamProbe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSourceVerifierTest {
    @Test
    fun `首包状态正常且类型是媒体时判定可播`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Responded(206, "video/mp4")))

            assertEquals(StreamVerification.Playable, verifier.verify(source()))
        }

    @Test
    fun `CDN 通用的 octet-stream 也按可播处理`() =
        runTest {
            val verifier =
                PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Responded(200, "application/octet-stream")))

            assertEquals(StreamVerification.Playable, verifier.verify(source()))
        }

    @Test
    fun `Content-Type 带 charset 时按主类型判断`() =
        runTest {
            val verifier =
                PlaybackSourceVerifierImpl(
                    ProbeFetch(StreamProbe.Responded(200, "application/vnd.apple.mpegurl; charset=utf-8")),
                )

            assertEquals(StreamVerification.Playable, verifier.verify(source()))
        }

    @Test
    fun `首包返回网页时判定不可播并说明原因`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Responded(200, "text/html")))

            val result = verifier.verify(source())

            assertTrue(result is StreamVerification.NotPlayable)
            assertTrue(result.reason.contains("文本页"), "原因要说清拿到的是网页，否则模型不知道改哪儿")
        }

    @Test
    fun `首包非 2xx 时判定不可播`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Responded(403, "video/mp4")))

            val result = verifier.verify(source())

            assertTrue(result is StreamVerification.NotPlayable)
            assertTrue(result.reason.contains("403"))
        }

    @Test
    fun `缺少 Content-Type 时不认定可播`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Responded(200, null)))

            assertTrue(verifier.verify(source()) is StreamVerification.NotPlayable)
        }

    @Test
    fun `探测请求失败时判定不可播`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Failed("connect timeout")))

            val result = verifier.verify(source())

            assertTrue(result is StreamVerification.NotPlayable)
            assertTrue(result.reason.contains("connect timeout"))
        }

    @Test
    fun `实现不支持探测时不下结论`() =
        runTest {
            val verifier = PlaybackSourceVerifierImpl(ProbeFetch(StreamProbe.Unsupported))

            assertEquals(StreamVerification.Unverified, verifier.verify(source()))
        }

    private fun source() = PlayableSource(url = "https://cdn.example.tv/hls/1.m3u8")

    /** 只回固定探测结论；页面抓取路径不参与本测试 */
    private class ProbeFetch(
        private val probe: StreamProbe,
    ) : PageFetchService {
        override suspend fun fetchHtml(
            url: String,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null

        override suspend fun postForm(
            url: String,
            formData: Map<String, String>,
            requestHeaders: Map<String, String>,
        ): FetchedPage? = null

        override suspend fun probeStream(
            url: String,
            requestHeaders: Map<String, String>,
        ): StreamProbe = probe
    }
}
