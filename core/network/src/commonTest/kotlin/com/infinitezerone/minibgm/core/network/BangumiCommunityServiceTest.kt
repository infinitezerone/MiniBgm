package com.infinitezerone.minibgm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiCommunityServiceTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun getEpisodeComments_parsesCorrectly() =
        runTest {
            val responseJson =
                """
                [
                    {
                        "id": 2038019,
                        "mainID": 1348301,
                        "creatorID": 929189,
                        "createdAt": 1769436820,
                        "content": "这帧数太棒了",
                        "user": {
                            "id": 929189,
                            "username": "tester",
                            "nickname": "测试用户",
                            "avatar": {
                                "large": "https://lain.bgm.tv/pic/user/l/test.jpg"
                            }
                        },
                        "reactions": [
                            {
                                "value": 140,
                                "users": [{"id": 1, "username": "user1", "nickname": "u1"}]
                            }
                        ],
                        "replies": [
                            {
                                "id": 2038752,
                                "creatorID": 678850,
                                "createdAt": 1769522615,
                                "content": "确实非常写实",
                                "user": {
                                    "id": 678850,
                                    "username": "reply_user",
                                    "nickname": "回复用户"
                                }
                            }
                        ]
                    }
                ]
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    assertEquals("/p1/episodes/1348301/comments", request.url.encodedPath)
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(this@BangumiCommunityServiceTest.json) }
                }

            val service = BangumiCommunityServiceImpl(client, baseUrl = "https://next.bgm.tv")
            val comments = service.getEpisodeComments(1348301)

            assertEquals(1, comments.size)
            val comment = comments.first()
            assertEquals(2038019L, comment.id)
            assertEquals(1348301L, comment.mainId)
            assertEquals("这帧数太棒了", comment.content)
            assertEquals("测试用户", comment.user?.displayName)
            assertEquals(1, comment.reactions.size)
            assertEquals(1, comment.reactions.first().count)
            assertEquals(1, comment.replies.size)
            assertEquals("确实非常写实", comment.replies.first().content)
        }

    @Test
    fun getSubjectComments_parsesCorrectly() =
        runTest {
            val responseJson =
                """
                {
                    "total": 1,
                    "data": [
                        {
                            "id": 52691182,
                            "type": 2,
                            "rate": 8,
                            "comment": "制作一流的动画电影",
                            "updatedAt": 1787132298,
                            "user": {
                                "id": 641415,
                                "username": "critic",
                                "nickname": "影评人"
                            }
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    assertEquals("/p1/subjects/496135/comments", request.url.encodedPath)
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(this@BangumiCommunityServiceTest.json) }
                }

            val service = BangumiCommunityServiceImpl(client, baseUrl = "https://next.bgm.tv")
            val page = service.getSubjectComments(496135)

            assertEquals(1, page.total)
            assertEquals(1, page.data.size)
            val item = page.data.first()
            assertEquals(8, item.rate)
            assertEquals("制作一流的动画电影", item.comment)
            assertEquals("影评人", item.user?.displayName)
        }

    @Test
    fun getSubjectTopics_parsesCorrectly() =
        runTest {
            val responseJson =
                """
                {
                    "total": 1,
                    "data": [
                        {
                            "id": 38323,
                            "title": "中文字幕翻译讨论",
                            "creatorID": 63429,
                            "parentID": 496135,
                            "replyCount": 29,
                            "createdAt": 1767239305,
                            "updatedAt": 1768746815,
                            "creator": {
                                "id": 63429,
                                "username": "host",
                                "nickname": "楼主"
                            }
                        }
                    ]
                }
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    assertEquals("/p1/subjects/496135/topics", request.url.encodedPath)
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                    )
                }

            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(this@BangumiCommunityServiceTest.json) }
                }

            val service = BangumiCommunityServiceImpl(client, baseUrl = "https://next.bgm.tv")
            val page = service.getSubjectTopics(496135)

            assertEquals(1, page.total)
            assertEquals(1, page.data.size)
            val topic = page.data.first()
            assertEquals("中文字幕翻译讨论", topic.title)
            assertEquals(29, topic.replyCount)
            assertEquals("楼主", topic.creator?.displayName)
        }
}
