package com.infinitezerone.minibgm.core.testing.data

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserProfile

val sampleSubject =
    Subject(
        id = 1001L,
        name = "葬送のフリーレン",
        nameCn = "葬送的芙莉莲",
        summary = "打倒魔王之后的勇者一行的后日谈。",
        date = "2023-09-29",
        eps = 28,
        totalEpisodes = 28,
        images =
            SubjectImages(
                large = "https://lain.bgm.tv/pic/cover/l/sample_1001.jpg",
                common = "https://lain.bgm.tv/pic/cover/c/sample_1001.jpg",
            ),
        rating =
            Rating(
                score = 8.9,
                total = 15000,
                rank = 1,
                count =
                    mapOf(
                        "1" to 15,
                        "2" to 10,
                        "3" to 25,
                        "4" to 40,
                        "5" to 120,
                        "6" to 450,
                        "7" to 1800,
                        "8" to 5200,
                        "9" to 5800,
                        "10" to 1540,
                    ),
            ),
        collection =
            CollectionCount(
                wish = 1200,
                collect = 28000,
                doing = 4500,
                onHold = 300,
                dropped = 150,
            ),
        tags =
            listOf(
                Tag(name = "日常", count = 3500),
                Tag(name = "治愈", count = 2800),
                Tag(name = "奇幻", count = 2400),
                Tag(name = "MADHOUSE", count = 1800),
            ),
    )

val sampleEpisodeList =
    listOf(
        Episode(
            id = 2001L,
            sort = 1f,
            ep = 1f,
            name = "冒険の終わり",
            nameCn = "冒险的终结",
            duration = "24:00",
            airdate = "2023-09-29",
        ),
        Episode(
            id = 2002L,
            sort = 2f,
            ep = 2f,
            name = "別に魔法じゃなくたって…",
            nameCn = "就算不是魔法也…",
            duration = "24:00",
            airdate = "2023-09-29",
        ),
    )

val sampleAirScheduleList =
    listOf(
        AirSchedule(
            bgmId = 1001L,
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            coverUrl = "https://lain.bgm.tv/pic/cover/l/sample_1001.jpg",
            ratingScore = 8.9,
            beginUtc = "2023-09-29T14:00:00.000Z",
            weekday = 5,
            timeCst = "22:00",
            timeJst = "23:00",
            siteLinks =
                listOf(
                    SiteLink(siteName = "bilibili", displayName = "哔哩哔哩", playUrl = "https://www.bilibili.com"),
                    SiteLink(siteName = "bahamut", displayName = "巴哈姆特", playUrl = "https://ani.gamer.com.tw"),
                ),
            nextEpisodeNumber = 1,
            isAiring = true,
        ),
    )

val sampleUserProfile =
    UserProfile(
        id = 42L,
        username = "infinitezerone",
        nickname = "零一",
        avatar =
            UserAvatar(
                large = "https://lain.bgm.tv/pic/user/l/sample_user.jpg",
            ),
        sign = "Stay hungry, stay foolish.",
    )

val sampleUserProfileAlt =
    UserProfile(
        id = 999L,
        username = "alt_user",
        nickname = "马甲二号",
        avatar =
            UserAvatar(
                large = "https://lain.bgm.tv/pic/user/l/sample_alt.jpg",
            ),
        sign = "Alt account for Galgame.",
    )

val sampleUserCollection =
    com.infinitezerone.minibgm.core.model.UserCollection(
        userId = 42L,
        subjectId = 1001L,
        subjectType = 2,
        rate = 9,
        type = 3, // DOING (在看)
        comment = "神作无误！",
        epStatus = 12,
        volStatus = 0,
        updatedAt = "2026-08-31T09:00:00Z",
        subject = sampleSubject,
    )

val sampleCharacterList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectCharacter(
            id = 3001L,
            name = "フリーレン",
            roleName = "主角",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/crt/l/sample_frieren.jpg",
                    medium = "https://lain.bgm.tv/pic/crt/m/sample_frieren.jpg",
                ),
            actors =
                listOf(
                    com.infinitezerone.minibgm.core.model.SubjectPerson(
                        id = 4001L,
                        name = "種﨑敦美",
                        type = 1,
                        relation = "声优",
                        images =
                            SubjectImages(
                                large = "https://lain.bgm.tv/pic/crt/l/sample_tanezaki.jpg",
                            ),
                    ),
                ),
        ),
        com.infinitezerone.minibgm.core.model.SubjectCharacter(
            id = 3002L,
            name = "フェルン",
            roleName = "主角",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/crt/l/sample_fern.jpg",
                ),
            actors =
                listOf(
                    com.infinitezerone.minibgm.core.model.SubjectPerson(
                        id = 4002L,
                        name = "市ノ瀬加那",
                        type = 1,
                        relation = "声优",
                    ),
                ),
        ),
    )

val samplePersonList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5001L,
            name = "斎藤圭一郎",
            type = 1,
            relation = "导演",
        ),
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5002L,
            name = "Evan Call",
            type = 1,
            relation = "音乐",
        ),
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5003L,
            name = "マッドハウス",
            type = 2,
            relation = "动画制作",
        ),
    )

val sampleRelationList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectRelation(
            id = 6001L,
            type = 2,
            name = "葬送のフリーレン 第2期",
            nameCn = "葬送的芙莉莲 第二季",
            relation = "续作",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/cover/l/sample_frieren_s2.jpg",
                ),
            ratingScore = 9.0,
        ),
    )

/** 超过收起态上限（4 位）的角色夹具：覆盖角色纵向摘要列表的默认态。 */
val sampleLargeCharacterList =
    sampleCharacterList +
        listOf(
            testCharacter(id = 3003L, name = "ヒンメル", roleName = "主角", actorName = "小市眞琴", actorId = 4003L),
            testCharacter(id = 3004L, name = "ハイター", roleName = "配角", actorName = "東地宏樹", actorId = 4004L),
            testCharacter(id = 3005L, name = "シュタルク", roleName = "配角", actorName = "小林千晃", actorId = 4005L),
            testCharacter(id = 3006L, name = "ザイン", roleName = "配角", actorName = "上田燿司", actorId = 4006L),
        )

private fun testCharacter(
    id: Long,
    name: String,
    roleName: String,
    actorName: String,
    actorId: Long,
) = com.infinitezerone.minibgm.core.model.SubjectCharacter(
    id = id,
    name = name,
    roleName = roleName,
    images =
        SubjectImages(
            large = "https://lain.bgm.tv/pic/crt/l/sample_character_$id.jpg",
        ),
    actors =
        listOf(
            com.infinitezerone.minibgm.core.model.SubjectPerson(
                id = actorId,
                name = actorName,
                type = 1,
                relation = "声优",
            ),
        ),
)

/**
 * 混序关联夹具（6 条）：边角类别（画集/原声集/资料集/其他）排在主线（前传/续作）之前，
 * 用于锁定收起态摘要的主线优先排序——API 顺序不可信，收起态必须先给承接关系。
 */
val sampleMixedRelationList =
    listOf(
        testRelation(id = 7001L, name = "公式アートワークス", nameCn = "芙莉莲官方画集", relation = "画集", score = 8.1),
        testRelation(id = 7002L, name = "Original Soundtrack", nameCn = "芙莉莲原声集", relation = "原声集", score = 8.4),
        testRelation(id = 7003L, name = "続・葬送のフリーレン", nameCn = "续·葬送的芙莉莲", relation = "续作", score = 9.1),
        testRelation(id = 7004L, name = "葬送のフリーレン preview", nameCn = "葬送的芙莉莲 先行篇", relation = "前传", score = 8.8),
        testRelation(id = 7005L, name = "Magic of the Journey", nameCn = "魔法的旅程 短篇集", relation = "资料集", score = 7.5),
        testRelation(id = 7006L, name = "魔族考察手册", nameCn = "魔族考察手册", relation = "其他", score = 7.0),
    )

private fun testRelation(
    id: Long,
    name: String,
    nameCn: String,
    relation: String,
    score: Double,
) = com.infinitezerone.minibgm.core.model.SubjectRelation(
    id = id,
    type = 2,
    name = name,
    nameCn = nameCn,
    relation = relation,
    images =
        SubjectImages(
            large = "https://lain.bgm.tv/pic/cover/l/sample_relation_$id.jpg",
        ),
    ratingScore = score,
)
