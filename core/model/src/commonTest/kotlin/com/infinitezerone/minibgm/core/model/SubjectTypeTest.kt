package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectTypeTest {
    @Test
    fun fromValueResolvesCorrectSubjectType() {
        assertEquals(SubjectType.BOOK, SubjectType.fromValue(1))
        assertEquals(SubjectType.ANIME, SubjectType.fromValue(2))
        assertEquals(SubjectType.MUSIC, SubjectType.fromValue(3))
        assertEquals(SubjectType.GAME, SubjectType.fromValue(4))
        assertEquals(SubjectType.REAL, SubjectType.fromValue(6))
        assertEquals(SubjectType.ANIME, SubjectType.fromValue(99)) // default
    }

    @Test
    fun collectionTypeVerbsMatchSubjectType() {
        // 动画
        assertEquals("想看", CollectionType.WISH.getVerb(SubjectType.ANIME))
        assertEquals("在看", CollectionType.DOING.getVerb(SubjectType.ANIME))
        assertEquals("看过", CollectionType.COLLECT.getVerb(SubjectType.ANIME))

        // 书籍
        assertEquals("想读", CollectionType.WISH.getVerb(SubjectType.BOOK))
        assertEquals("在读", CollectionType.DOING.getVerb(SubjectType.BOOK))
        assertEquals("读过", CollectionType.COLLECT.getVerb(SubjectType.BOOK))

        // 音乐
        assertEquals("想听", CollectionType.WISH.getVerb(SubjectType.MUSIC))
        assertEquals("在听", CollectionType.DOING.getVerb(SubjectType.MUSIC))
        assertEquals("听过", CollectionType.COLLECT.getVerb(SubjectType.MUSIC))

        // 游戏
        assertEquals("想玩", CollectionType.WISH.getVerb(SubjectType.GAME))
        assertEquals("在玩", CollectionType.DOING.getVerb(SubjectType.GAME))
        assertEquals("玩过", CollectionType.COLLECT.getVerb(SubjectType.GAME))
    }
}
