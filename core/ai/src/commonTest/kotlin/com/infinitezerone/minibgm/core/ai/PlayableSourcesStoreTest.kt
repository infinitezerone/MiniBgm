package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.PlayableEpisodeList
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlayableSourcesStoreTest {
    @Test
    fun pop_returns_value_and_resets_to_null() {
        val store = PlayableSourcesStore()
        assertNull(store.pop(), "空 store 弹出应返回 null")

        val list = PlayableEpisodeList(subjectId = 1L, title = "葬送的芙莉莲", episodes = emptyList())
        store.set(list)
        assertSame(list, store.pop())
        assertNull(store.pop(), "pop 必须消费掉数据，二次弹出为空")
    }

    @Test
    fun clear_resets_state() {
        val store = PlayableSourcesStore()
        store.set(PlayableEpisodeList(subjectId = 2L, episodes = emptyList()))
        store.clear()
        assertNull(store.pop())
    }
}
