package com.infinitezerone.minibgm.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 导入合并语义：同一条来源无论走哪个入口、导入多少次，库里都只应有一份。
 *
 * 这层是最容易悄悄退化的地方——它没有 UI，出问题时只表现为"规则列表慢慢变长"，
 * 所以三种身份判定（id / 名称 / 模板）与"保留本地开关"都各自钉一条断言。
 */
class PlaybackRuleMergeTest {
    private fun rule(
        id: String,
        name: String,
        url: String,
        isEnabled: Boolean = true,
    ) = PlaybackSourceRule(id = id, name = name, urlTemplate = url, isEnabled = isEnabled)

    @Test
    fun mergeImportedRules_sameId_replacesInPlaceAndKeepsLocalSwitchState() {
        val existing = rule("rule-1", "旧名", "https://a.example.tv/{title}", isEnabled = false)
        val incoming = rule("rule-1", "新名", "https://a.example.tv/v2/{title}")

        val merged = mergeImportedRules(listOf(existing), listOf(incoming))

        assertEquals(1, merged.size)
        assertEquals("新名", merged.single().name)
        assertEquals("rule-1", merged.single().id)
        // 用户手动关掉的源不该因为一次刷新被偷偷打开
        assertFalse(merged.single().isEnabled)
    }

    @Test
    fun mergeImportedRules_differentIdButSameName_replacesInsteadOfAppending() {
        // 对话框导入曾经用随机 UUID，刷新用稳定 id：同一来源两条 id 不同，也必须落成一条
        val existing = rule("sub-abc", "量子资源", "https://a.example.tv/{title}")
        val incoming = rule("c0ffee-uuid", "量子资源", "https://a.example.tv/{title}")

        val merged = mergeImportedRules(listOf(existing), listOf(incoming))

        assertEquals(1, merged.size)
        assertEquals("sub-abc", merged.single().id)
    }

    @Test
    fun mergeImportedRules_differentIdAndNameButSameTemplate_replacesInsteadOfAppending() {
        val existing = rule("sub-abc", "量子资源", "https://a.example.tv/{title}")
        val incoming = rule("c0ffee-uuid", "量子资源（改名）", "https://a.example.tv/{title}")

        val merged = mergeImportedRules(listOf(existing), listOf(incoming))

        assertEquals(1, merged.size)
        assertEquals("量子资源（改名）", merged.single().name)
        assertEquals("sub-abc", merged.single().id)
    }

    @Test
    fun mergeImportedRules_distinctSources_allAppendedInOrder() {
        val current = listOf(rule("1", "源甲", "https://a.example.tv/{title}"))
        val incoming =
            listOf(
                rule("2", "源乙", "https://b.example.tv/{title}"),
                rule("3", "源丙", "https://c.example.tv/{title}"),
            )

        val merged = mergeImportedRules(current, incoming)

        assertEquals(listOf("源甲", "源乙", "源丙"), merged.map { it.name })
    }

    @Test
    fun mergeImportedRules_emptyIncoming_keepsCurrentUntouched() {
        val current = listOf(rule("1", "源甲", "https://a.example.tv/{title}"))

        assertEquals(current, mergeImportedRules(current, emptyList()))
    }

    @Test
    fun mergeImportedRules_sameListImportedTwice_isIdempotent() {
        val incoming = listOf(rule("1", "源甲", "https://a.example.tv/{title}"))

        val once = mergeImportedRules(emptyList(), incoming)
        val twice = mergeImportedRules(once, incoming)

        assertEquals(once, twice)
    }

    @Test
    fun isSameSource_matchesByIdNameOrTemplateIndependently() {
        val base = rule("1", "源甲", "https://a.example.tv/{title}")

        assertTrue(isSameSource(base, rule("1", "别的名字", "https://b.example.tv/{title}")))
        assertTrue(isSameSource(base, rule("2", "源甲", "https://b.example.tv/{title}")))
        assertTrue(isSameSource(base, rule("2", "别的名字", "https://a.example.tv/{title}")))
        assertFalse(isSameSource(base, rule("2", "源乙", "https://b.example.tv/{title}")))
    }
}
