package com.infinitezerone.minibgm.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BgmUrlParserTest {
    @Test
    fun parse_subjectUrls_returnsSubjectLink() {
        val urls =
            listOf(
                "https://bgm.tv/subject/328793",
                "http://bangumi.tv/subject/328793",
                "https://chii.in/subject/328793",
                "https://m.bgm.tv/subject/328793",
                "//bgm.tv/subject/328793",
                "/subject/328793",
                "bgm.tv/subject/328793",
                "https://bgm.tv/subject/328793?from=search#comments",
            )

        for (url in urls) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.Subject>(result, "Expected Subject for $url")
            assertEquals(328793L, result.subjectId)
        }
    }

    @Test
    fun parse_characterUrls_returnsCharacterLink() {
        val urls =
            listOf(
                "https://bgm.tv/character/12345",
                "https://bangumi.tv/crt/12345",
                "https://chii.in/character/12345",
                "/character/12345",
                "/crt/12345",
                "bgm.tv/crt/12345",
            )

        for (url in urls) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.Character>(result, "Expected Character for $url")
            assertEquals(12345L, result.characterId)
        }
    }

    @Test
    fun parse_personUrls_returnsPersonLink() {
        val urls =
            listOf(
                "https://bgm.tv/person/6789",
                "https://bangumi.tv/prsn/6789",
                "/person/6789",
                "/prsn/6789",
                "https://chii.in/prsn/6789?ref=work",
            )

        for (url in urls) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.Person>(result, "Expected Person for $url")
            assertEquals(6789L, result.personId)
        }
    }

    @Test
    fun parse_episodeUrls_returnsEpisodeLink() {
        val urls =
            listOf(
                "https://bgm.tv/ep/10086",
                "https://bangumi.tv/ep/10086",
                "/ep/10086",
            )

        for (url in urls) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.Episode>(result, "Expected Episode for $url")
            assertEquals(10086L, result.episodeId)
        }
    }

    @Test
    fun parse_topicUrls_returnsTopicLink() {
        val subjectTopic = BgmUrlParser.parse("https://bgm.tv/subject/topic/54321")
        assertIs<BgmLink.Topic>(subjectTopic)
        assertEquals(54321L, subjectTopic.topicId)
        assertEquals("subject", subjectTopic.type)

        val groupTopic = BgmUrlParser.parse("https://bangumi.tv/group/topic/123")
        assertIs<BgmLink.Topic>(groupTopic)
        assertEquals(123L, groupTopic.topicId)
        assertEquals("group", groupTopic.type)
    }

    @Test
    fun parse_userUrls_returnsUserLink() {
        val userLink = BgmUrlParser.parse("https://bgm.tv/user/sai")
        assertIs<BgmLink.User>(userLink)
        assertEquals("sai", userLink.username)

        val numericUser = BgmUrlParser.parse("https://bgm.tv/user/12345")
        assertIs<BgmLink.User>(numericUser)
        assertEquals("12345", numericUser.username)
    }

    @Test
    fun parse_externalUrls_returnsExternalLink() {
        val external =
            listOf(
                "https://bilibili.com/video/BV1xx411c7mD",
                "https://github.com/infinitezerone/MiniBgm",
                "https://google.com",
                "https://other-bgm.tv/subject/123",
                "other-bgm.tv/subject/123",
                "fakebgm.tv/ep/100",
                "plain text without url",
                "",
                "   ",
            )

        for (url in external) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.External>(result, "Expected External for $url")
        }
    }

    @Test
    fun parse_nonPositiveOrInvalidIds_returnsExternalLink() {
        val invalidIdUrls =
            listOf(
                "https://bgm.tv/subject/0",
                "/subject/0",
                "https://bgm.tv/character/0",
                "/crt/0",
                "https://bgm.tv/person/0",
                "/prsn/0",
                "https://bgm.tv/ep/0",
                "/ep/0",
                "https://bgm.tv/subject/topic/0",
            )

        for (url in invalidIdUrls) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.External>(result, "Expected External for invalid ID: $url")
        }
    }

    @Test
    fun formatDisplayLabel_generatesHumanReadableLabels() {
        assertEquals("条目 #328793", BgmUrlParser.formatDisplayLabel(BgmLink.Subject(328793L)))
        assertEquals("角色 #12345", BgmUrlParser.formatDisplayLabel(BgmLink.Character(12345L)))
        assertEquals("人物 #6789", BgmUrlParser.formatDisplayLabel(BgmLink.Person(6789L)))
        assertEquals("单集 #100", BgmUrlParser.formatDisplayLabel(BgmLink.Episode(100L)))
        assertEquals("讨论 #555", BgmUrlParser.formatDisplayLabel(BgmLink.Topic(555L)))
        assertEquals("@sai", BgmUrlParser.formatDisplayLabel(BgmLink.User("sai")))
        assertEquals("https://bilibili.com", BgmUrlParser.formatDisplayLabel(BgmLink.External("https://bilibili.com")))
    }

    // ---- 域名识别边界（安全行为钉子：伪造 host 绝不能路由进应用内跳转） ----

    @Test
    fun parse_userinfoSpoofing_isRejectedAsExternal() {
        // bgm.tv 出现在 userinfo 位置时真实 host 是 evil.com，必须拒绝
        val result = BgmUrlParser.parse("https://bgm.tv@evil.com/subject/123")
        assertIs<BgmLink.External>(result)
    }

    @Test
    fun parse_lookalikeDomains_areRejectedAsExternal() {
        val lookalikes =
            listOf(
                "https://evilbgm.tv/subject/123",
                "https://bgm.tv.evil.com/subject/123",
                "https://bgm.tv.evil.com.evil/subject/123",
                "evilbgm.tv/subject/123",
            )
        for (url in lookalikes) {
            val result = BgmUrlParser.parse(url)
            assertIs<BgmLink.External>(result, "Expected External for lookalike: $url")
        }
    }

    @Test
    fun parse_protocolRelativeBgmUrl_isRecognized() {
        val result = BgmUrlParser.parse("//bgm.tv/subject/123")
        assertEquals(BgmLink.Subject(123L), result)
    }

    @Test
    fun parse_bareDomainWithoutScheme_isRecognized() {
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("bgm.tv/subject/123"))
        assertEquals(BgmLink.Episode(456L), BgmUrlParser.parse("bangumi.tv/ep/456"))
    }

    @Test
    fun parse_uppercaseSchemeAndHost_isRecognized() {
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("HTTPS://BGM.TV/subject/123"))
    }

    @Test
    fun parse_subdomainsOfBgmDomain_areRecognized() {
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://www.bgm.tv/subject/123"))
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://next.bgm.tv/subject/123"))
    }

    @Test
    fun parse_portAndQueryAndFragment_areStripped() {
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://bgm.tv/subject/123?page=1"))
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://bgm.tv/subject/123#comment_456"))
    }

    @Test
    fun parse_trailingSlashAndDeepPaths_areTolerated() {
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://bgm.tv/subject/123/"))
        assertEquals(BgmLink.Subject(123L), BgmUrlParser.parse("https://bgm.tv/subject/123/comments"))
    }
}
