package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun unescapeHtmlEntities_decodesNamedEntities() {
        assertEquals("Let's Go 怪奇组", "Let&#39;s Go 怪奇组".unescapeHtmlEntities())
        assertEquals("2nd & 3rd STAGE", "2nd &amp; 3rd STAGE".unescapeHtmlEntities())
        assertEquals("<b>\"x\"</b>", "&lt;b&gt;&quot;x&quot;&lt;/b&gt;".unescapeHtmlEntities())
        assertEquals("a'b", "a&apos;b".unescapeHtmlEntities())
        assertEquals("a&b", "a&amp;b".unescapeHtmlEntities())
    }

    @Test
    fun unescapeHtmlEntities_decodesNumericEntities() {
        assertEquals("a'b", "a&#x27;b".unescapeHtmlEntities())
        assertEquals("A—B", "A&#8212;B".unescapeHtmlEntities())
        // 辅助平面（emoji）需要合成代理对
        assertEquals("\uD83D\uDE00", "&#128512;".unescapeHtmlEntities())
    }

    @Test
    fun unescapeHtmlEntities_leavesCleanTextAndUnknownEntitiesUntouched() {
        assertEquals("ふつうのタイトル", "ふつうのタイトル".unescapeHtmlEntities())
        assertEquals("100% & pure", "100% & pure".unescapeHtmlEntities())
        assertEquals("&unknown;", "&unknown;".unescapeHtmlEntities())
    }
}
