package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `titleAliases 同时读取字符串与列表形态的 infobox 值`() {
        // 两种 value 形状在真实响应（/v0/subjects/400602）里同时存在
        val subject =
            json.decodeFromString<Subject>(
                """
                {"id":400602,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲",
                 "infobox":[
                   {"key":"中文名","value":"葬送的芙莉莲"},
                   {"key":"别名","value":[
                     {"v":"Frieren: Beyond Journey's End"},
                     {"v":"Sousou no Frieren"},
                     {"v":"葬送的芙莉蓮"}
                   ]}
                 ]}
                """.trimIndent(),
            )

        assertEquals(
            listOf("葬送的芙莉莲", "Frieren: Beyond Journey's End", "Sousou no Frieren", "葬送的芙莉蓮"),
            subject.titleAliases,
        )
    }

    @Test
    fun `titleAliases 忽略无关条目并丢掉空白与超长值`() {
        val subject =
            json.decodeFromString<Subject>(
                """
                {"id":1,"name":"x",
                 "infobox":[
                   {"key":"中文名","value":"测试番剧"},
                   {"key":"别名","value":[{"v":""},{"v":"${"长".repeat(61)}"}]},
                   {"key":"作曲","value":"某人"},
                   {"key":"话数","value":"12"}
                 ]}
                """.trimIndent(),
            )

        assertEquals(listOf("测试番剧"), subject.titleAliases)
    }

    @Test
    fun `titleAliases 没有 infobox 时为空`() {
        val subject = json.decodeFromString<Subject>("""{"id":1,"name":"x"}""")

        assertTrue(subject.titleAliases.isEmpty())
    }

    @Test
    fun `broadcastStation 取 infobox 播放电视台的首个值`() {
        // 真实响应里该字段同时存在字符串与列表两种形状（/v0/search/subjects 与 /v0/subjects/{id}）
        val subject =
            json.decodeFromString<Subject>(
                """
                {"id":1,"name":"x","name_cn":"无职转生 第三季",
                 "infobox":[
                   {"key":"放送星期","value":"星期日"},
                   {"key":"播放电视台","value":"TOKYO MX / BS11"},
                   {"key":"其他电视台","value":"BS11"}
                 ]}
                """.trimIndent(),
            )

        assertEquals("TOKYO MX / BS11", subject.broadcastStation)
    }

    @Test
    fun `broadcastStation 在列表形态与缺失时分别取首项与空串`() {
        val listed =
            json.decodeFromString<Subject>(
                """
                {"id":1,"name":"x",
                 "infobox":[{"key":"播放电视台","value":[{"v":"AT-X"},{"v":"TOKYO MX"}]}]}
                """.trimIndent(),
            )
        val missing = json.decodeFromString<Subject>("""{"id":1,"name":"x"}""")
        val blank = json.decodeFromString<Subject>("""{"id":1,"name":"x","infobox":[{"key":"播放电视台","value":" "}]}""")

        assertEquals("AT-X", listed.broadcastStation)
        assertEquals("", missing.broadcastStation)
        assertEquals("", blank.broadcastStation)
    }

    @Test
    fun `metaTags 与 platform 从响应里解析出来`() {
        // 这两个字段此前在反序列化时被静默丢弃，导视只能拿用户标签（tags）加标题关键词去猜放送形式
        val subject =
            json.decodeFromString<Subject>(
                """
                {"id":501963,"name":"無職転生Ⅲ","platform":"TV",
                 "meta_tags":["后宫","TV","日本","奇幻","冒险","小说改"]}
                """.trimIndent(),
            )

        assertEquals(listOf("后宫", "TV", "日本", "奇幻", "冒险", "小说改"), subject.metaTags)
        assertEquals("TV", subject.platform)
        assertFalse(subject.isShortForm)
    }

    @Test
    fun `isShortForm 同时识别 platform 其他与片段类元标签`() {
        // 两种形态在真实响应里都出现过：MV 条目的 platform 普遍是「其他」，
        // 但也有 platform 标成 WEB 却带「短片」标签的
        val viaPlatform = json.decodeFromString<Subject>("""{"id":1,"name":"x","platform":"其他","meta_tags":["MV"]}""")
        val viaTag = json.decodeFromString<Subject>("""{"id":2,"name":"x","platform":"WEB","meta_tags":["短片"]}""")
        val regular = json.decodeFromString<Subject>("""{"id":3,"name":"x","platform":"WEB","meta_tags":["WEB","日本"]}""")
        val bare = json.decodeFromString<Subject>("""{"id":4,"name":"x"}""")

        assertTrue(viaPlatform.isShortForm)
        assertTrue(viaTag.isShortForm)
        // 两个字段都缺失的条目（如「乐高航海王」）是真番，不能因为没标签就当成片段
        assertFalse(regular.isShortForm)
        assertFalse(bare.isShortForm)
    }
}
