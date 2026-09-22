package com.infinitezerone.minibgm.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackFailureStoreTest {
    @Test
    fun `同一源的连续失败累加，换源各记各的`() {
        val store = PlaybackFailureStore()

        store.markFailed("https://a.example.com/1.m3u8", "网络不可达", sourceId = "rule-a")
        store.markFailed("https://a.example.com/2.m3u8", "网络不可达", sourceId = "rule-a")
        store.markFailed("https://b.example.com/1.m3u8", "被拒绝", sourceId = "rule-b")

        assertEquals(2, store.sourceHealth.value["rule-a"]?.consecutiveFailures)
        assertEquals("网络不可达", store.sourceHealth.value["rule-a"]?.lastReason)
        assertEquals(1, store.sourceHealth.value["rule-b"]?.consecutiveFailures)
    }

    @Test
    fun `成功一次就把该源的连续计数清零`() {
        val store = PlaybackFailureStore()
        store.markFailed("https://a.example.com/1.m3u8", "网络不可达", sourceId = "rule-a")
        store.markFailed("https://a.example.com/1.m3u8", "网络不可达", sourceId = "rule-a")

        store.markPlayable("https://a.example.com/1.m3u8", sourceId = "rule-a")

        // 「连续」是关键：计数回答的是"这个源现在还灵不灵"，不是"历史上错过几次"
        assertNull(store.sourceHealth.value["rule-a"])
        assertFalse(store.recentFailures.value.containsKey("https://a.example.com/1.m3u8"))
    }

    @Test
    fun `清一个源不会动到别的源`() {
        val store = PlaybackFailureStore()
        store.markFailed("https://a.example.com/1.m3u8", "网络不可达", sourceId = "rule-a")
        store.markFailed("https://b.example.com/1.m3u8", "网络不可达", sourceId = "rule-b")

        store.markPlayable("https://a.example.com/1.m3u8", sourceId = "rule-a")

        assertNull(store.sourceHealth.value["rule-a"])
        assertEquals(1, store.sourceHealth.value["rule-b"]?.consecutiveFailures)
    }

    @Test
    fun `地址为空时仍记源级失败`() {
        val store = PlaybackFailureStore()

        store.markFailed(url = "", reason = "嗅探失败", sourceId = "rule-a")

        // 丢掉的应当是"哪条地址"，而不是"哪个源不灵"
        assertEquals(1, store.sourceHealth.value["rule-a"]?.consecutiveFailures)
        assertTrue(store.recentFailures.value.isEmpty())
    }

    @Test
    fun `认不出来的源只记地址级失败`() {
        val store = PlaybackFailureStore()

        store.markFailed("https://a.example.com/1.m3u8", "网络不可达")

        assertEquals("网络不可达", store.recentFailures.value["https://a.example.com/1.m3u8"])
        assertTrue(store.sourceHealth.value.isEmpty())
    }
}
