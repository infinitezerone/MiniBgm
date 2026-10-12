package com.infinitezerone.minibgm.core.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseMirrorResolverTest {
    @Test
    fun resolveCandidateUrls_withGitHubUrl_returnsMirrorsAndRawFallback() {
        val raw = "https://github.com/infinitezerone/MiniBgm/releases/download/v0.3.0/app-release.apk"
        val candidates = ReleaseMirrorResolver.resolveCandidateUrls(raw)

        assertTrue(candidates.size >= 3, "至少应包含2个镜像和1个原始URL")
        assertEquals("https://gh-proxy.com/$raw", candidates[0])
        assertEquals("https://ghproxy.net/$raw", candidates[1])
        assertEquals(raw, candidates.last(), "最后一个应为原生兜底直链")
    }

    @Test
    fun resolveCandidateUrls_withCustomPrefix_prioritizesCustomPrefix() {
        val raw = "https://github.com/infinitezerone/MiniBgm/releases/download/v0.3.0/app-release.apk"
        val customPrefix = "https://my-proxy.workers.dev"
        val candidates = ReleaseMirrorResolver.resolveCandidateUrls(raw, customPrefix = customPrefix)

        assertEquals("https://my-proxy.workers.dev/$raw", candidates.first(), "自定义代理应排在第一位")
        assertTrue(candidates.contains(raw), "应包含原生直链兜底")
    }

    @Test
    fun resolveCandidateUrls_withNonGitHubUrl_returnsOriginalOnly() {
        val nonGitHub = "https://api.bgm.tv/v0/subjects/1"
        val candidates = ReleaseMirrorResolver.resolveCandidateUrls(nonGitHub)

        assertEquals(listOf(nonGitHub), candidates)
    }

    @Test
    fun resolveCandidateUrls_withBlankUrl_returnsEmptyList() {
        assertEquals(emptyList(), ReleaseMirrorResolver.resolveCandidateUrls(""))
        assertEquals(emptyList(), ReleaseMirrorResolver.resolveCandidateUrls("   "))
    }
}
