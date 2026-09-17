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

    @Test
    fun getSubjectTopicDetail_withRealPayload_parsesCorrectly() =
        runTest {
            val responseJson =
                """
                {
                    "id": 40498,
                    "title": "谁懂这撑伞",
                    "creatorID": 734183,
                    "parentID": 637124,
                    "replyCount": 1,
                    "createdAt": 1784787912,
                    "updatedAt": 1784787912,
                    "state": 0,
                    "display": 1,
                    "subject": {
                        "id": 637124,
                        "name": "花ざかりの君たちへ 第2期",
                        "nameCN": "花样少年少女 第二季",
                        "type": 2,
                        "info": "13话",
                        "metaTags": ["校园", "TV"],
                        "rating": {
                            "rank": 0,
                            "count": [1, 0, 2, 3, 11, 27, 11, 1, 1, 1],
                            "score": 5.86,
                            "total": 58
                        },
                        "locked": false,
                        "nsfw": false,
                        "images": {
                            "large": "https://lain.bgm.tv/pic/cover/l/20/0f/637124_6DDda.jpg",
                            "common": "https://lain.bgm.tv/r/400/pic/cover/l/20/0f/637124_6DDda.jpg",
                            "medium": "https://lain.bgm.tv/r/200/pic/cover/l/20/0f/637124_6DDda.jpg",
                            "small": "https://lain.bgm.tv/r/100/pic/cover/l/20/0f/637124_6DDda.jpg",
                            "grid": "https://lain.bgm.tv/r/100x100/pic/cover/l/20/0f/637124_6DDda.jpg"
                        }
                    },
                    "replies": [
                        {
                            "id": 406011,
                            "creatorID": 734183,
                            "createdAt": 1784787912,
                            "content": "楼主主贴内容",
                            "state": 0,
                            "replies": [],
                            "creator": {
                                "id": 734183,
                                "username": "sawarin",
                                "nickname": "Sawarin"
                            },
                            "reactions": []
                        }
                    ],
                    "creator": {
                        "id": 734183,
                        "username": "sawarin",
                        "nickname": "Sawarin"
                    }
                }
                """.trimIndent()

            val engine =
                MockEngine { request ->
                    assertEquals("/p1/subjects/-/topics/40498", request.url.encodedPath)
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
            val detail = service.getSubjectTopicDetail(40498)

            assertEquals(40498, detail.id)
            assertEquals("谁懂这撑伞", detail.title)
            assertEquals("花样少年少女 第二季", detail.subject?.displayName)
            assertEquals(5.86, detail.subject?.rating?.score)
            assertEquals("楼主主贴内容", detail.mainPost?.content)
        }
}
