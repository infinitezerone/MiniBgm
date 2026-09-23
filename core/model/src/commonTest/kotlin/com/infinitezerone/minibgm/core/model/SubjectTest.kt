package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
