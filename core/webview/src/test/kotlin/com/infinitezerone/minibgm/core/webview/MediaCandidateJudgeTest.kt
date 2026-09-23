package com.infinitezerone.minibgm.core.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCandidateJudgeTest {
    @Test
    fun `媒体后缀是强信号`() {
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/v/1.m3u8?sign=abc", null))
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/v/1.M3U8", null))
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/v/1.mp4", null))
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/v/1.ts", null))
    }

    @Test
    fun `Range 头加非静态资源推断为媒体——后缀之外补上 JS 站无后缀拉流形态`() {
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/stream/12345", "bytes=0-"))
        assertTrue(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/stream/12345", "Bytes=0-1000"))
    }

    @Test
    fun `Range 推断对静态资源不生效`() {
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/app.js", "bytes=0-"))
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/style.css", "bytes=0-"))
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/page.html", "bytes=0-"))
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/data.json", "bytes=0-"))
    }

    @Test
    fun `广告统计端点即使带 Range 也拒绝`() {
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://www.google-analytics.com/collect", "bytes=0-"))
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://ad.example.com/ads/tracker", "bytes=0-"))
    }

    @Test
    fun `无后缀且无 Range 不是媒体`() {
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/stream/12345", null))
        assertFalse(MediaCandidateJudge.isMediaCandidate("https://cdn.example.com/api/list", "x-bytes=0-"))
    }

    @Test
    fun `rank 排序——清单优先分片沉底`() {
        assertEquals(0, MediaCandidateJudge.rank("https://cdn.example.com/v/index.m3u8?sign=abc"))
        assertEquals(1, MediaCandidateJudge.rank("https://cdn.example.com/v/full.mp4"))
        assertEquals(2, MediaCandidateJudge.rank("https://cdn.example.com/stream/12345"))
        assertEquals(3, MediaCandidateJudge.rank("https://cdn.example.com/v/seg-1.ts"))
    }
}
